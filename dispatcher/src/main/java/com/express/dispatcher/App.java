/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/16/15.
 */

package com.express.dispatcher;

import java.util.Set;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import scala.concurrent.Future;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnSuccess;
import akka.pattern.Patterns;

public class App {

    public static void main(String[] args) {
        new App().run();
    }

    public void run() {
        JedisPool pool = new JedisPool("127.0.0.1", 6379);
        WorkerLoader.init(pool);

        ActorSystem system = ActorSystem.create("Diver-Dispatcher");
        ActorRef actor = system.actorOf(Props.create(Router.class, pool), "router");

        WorkerLoader workerLoader = WorkerLoader.getWorkerLoader();
        Set<String> order = workerLoader.load();
        if (null != order) {
            for (String orderId : order) {
                actor.tell(new Cmd.Dispatch(orderId), null);
            }
        }

        try (Jedis redis = pool.getResource()) {
            final AkkaDispatcher dispatcher = new AkkaDispatcher(pool, actor);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    redis.subscribe(dispatcher, "dispatch");
                }
            }).start();

            System.out.println("Server started...");
            while (true) {
                try {
                    Thread.sleep(60000);
                    Future<Object> monitor = Patterns.ask(actor, new Cmd.Monitor(), 5000);
                    monitor.onSuccess(new OnSuccess<Object>() {
                        @Override
                        public final void onSuccess(Object o) {
                            int count = (int) o;
                            System.out.printf("dispatching order count[%d]\n", count);
                        }
                    }, system.dispatcher());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // private static void reload() {
    // OfflineLoader offlineLoader = OfflineLoader.getOfflineLoader();
    // Set<String> order = offlineLoader.load();
    // }
}
