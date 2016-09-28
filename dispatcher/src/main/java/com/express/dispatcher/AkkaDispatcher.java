/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/16/15.
 */

package com.express.dispatcher;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import akka.actor.ActorRef;

public class AkkaDispatcher extends JedisPubSub {

    //private final JedisPool pool;
    private final ActorRef actor;

    public AkkaDispatcher(JedisPool redisPool, ActorRef router) {
        //pool = redisPool;
        actor = router;
    }

    public void onMessage(String channel, String message) {
        log("onMessage");
        System.out.println(String.format(">>>>> channel:[%s], message:[%s]", channel, message));

        String[] args = message.split("#");
        if (args.length != 2) {
            System.out.printf("invalid message[%s]\n", message);
            return;
        }

        String cmd = args[0];
        String orderId = args[1];
        if (cmd.equals(Cmd.Dispatch.CMD)) {
            System.out.printf("\tdispatch order:%s\n", args[1]);
            actor.tell(new Cmd.Dispatch(orderId), null);
            WorkerLoader.getWorkerLoader().save(orderId);
        } else if (cmd.equals(Cmd.Cancel.CMD)) {
            System.out.printf("\tcancel order:%s\n dispatch", args[1]);
            actor.tell(new Cmd.Cancel(orderId), null);
        } else {
            System.out.printf("\tunsupported cmd[%s]\n", args[0]);
        }

    }

    public void onPMessage(String pattern, String channel, String message) {
        log("onPMessage");
        System.out.println(String.format(">>>>> pattern[%s], channel:[%s], message:[%s]", pattern, channel, message));
    }

    public void onSubscribe(String channel, int subscribedChannels) {
        log("onSubscribe");
        System.out.println(String.format("channel[%s], channel_id[%d]", channel, subscribedChannels));
    }

    public void onUnsubscribe(String channel, int subscribedChannels) {
        log("onUnsubscribe");
        System.out.println("INFO: onUnsubscribe");
        System.out.println(String.format("channel[%s], channel_id[%d]", channel, subscribedChannels));
    }

    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        log("onPUnsubscribe");
        System.out.println(String.format("pattern[%s], channel_id[%d]", pattern, subscribedChannels));
    }

    public void onPSubscribe(String pattern, int subscribedChannels) {
        log("onPSubscribe");
        System.out.println(String.format("pattern[%s], channel_id[%d]", pattern, subscribedChannels));
    }

    private void log(String funcName) {
        System.out.println(String.format("INFO: %s", funcName));
    }
}
