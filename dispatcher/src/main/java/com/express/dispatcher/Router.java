/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/16/15.
 */

package com.express.dispatcher;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import redis.clients.jedis.JedisPool;
import scala.Option;

public class Router extends UntypedActor {

    private final JedisPool pool;

    private int count;

    public Router(JedisPool redisPool) {
        pool = redisPool;
        count = 0;
    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof Cmd.Dispatch) {
            Cmd.Dispatch cmd = (Cmd.Dispatch) message;

            Option<ActorRef> ref = getContext().child(cmd.getId());
            if (!ref.isEmpty()) {
                System.out.printf("duplicated dispatch for order[%s]\n",
                        cmd.getId());
                return;
            }

            getContext().actorOf(Props.create(Worker.class, cmd.getId(), pool),
                    cmd.getId());
            ++count;
            System.out.printf("new dispatch worker for order[%s]\n",
                    cmd.getId());
        } else if (message instanceof Cmd.Cancel) {
            Cmd.Cancel cmd = (Cmd.Cancel) message;

            Option<ActorRef> ref = getContext().child(cmd.getId());
            if (ref.isEmpty()) {
                System.out.printf("duplicated cancel for order[%s]\n",
                        cmd.getId());
                return;
            }

            ref.get().tell(PoisonPill.getInstance(), self());
            --count;
            System.out.printf("stop dispatch worker for order[%s]\n", cmd.getId());
        } else if (message instanceof Cmd.Monitor) {
            getSender().tell(count, getSender());
        } else {
            unhandled(message);
        }
    }
}
