/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/16/15.
 */

package com.express.dispatcher;

import java.util.concurrent.TimeUnit;

import redis.clients.jedis.JedisPool;
import scala.concurrent.duration.Duration;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;

public class Worker extends UntypedActor {

    private final String id;
    private final JedisPool pool;
    private final Cancellable tick = getContext()
            .system()
            .scheduler()
            .schedule(Duration.Zero(), Duration.create(30, TimeUnit.SECONDS), getSelf(), "tick",
                    getContext().dispatcher(), null);

    private int count;

    public Worker(String id, JedisPool pool) {
        this.id = id;
        this.pool = pool;
        count = 0;
    }

    @Override
    public void postStop() {
        tick.cancel();
        WorkerLoader.getWorkerLoader().remove(id);
        System.out.printf("worker for order[%s] will stop\n", id);
    }

    @Override
    public void onReceive(Object message) {
        if (message.equals("tick")) {
            count++;

            dispatch(id);

            System.out.printf("dispatch order[%s] count[%d]\n", id, count);
        } else {
            System.out.println("deal with unhandled message");
            unhandled(message);
        }
    }

    private void dispatch(String id) {
        // check timeout
        // check pay timeout
        // get dispatch strategy
        // find new drivers
        // dispatch order to new driver
    }
}
