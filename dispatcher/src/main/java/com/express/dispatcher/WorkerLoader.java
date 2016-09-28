package com.express.dispatcher;

import java.util.Set;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class WorkerLoader {
    private final JedisPool pool;

    private static final String ORDER_PATTERN = "*";

    private static final String VALUE = "0";

    private static WorkerLoader workerLoader = null;

    private int dbIndex = 10;

    private WorkerLoader(JedisPool pool) {
        this.pool = pool;
    }

    public Set<String> load() {
        try (Jedis redis = pool.getResource()) {
            redis.select(getDBIndex());
            return redis.keys(ORDER_PATTERN);
        }
    }

    public void save(String orderId) {
        try (Jedis redis = pool.getResource()) {
            redis.select(getDBIndex());
            redis.set(orderId, VALUE);
        }
    }

    public void remove(String orderId) {
        try (Jedis redis = pool.getResource()) {
            redis.select(getDBIndex());
            redis.del(orderId);
        }
    }

    public static void init(JedisPool pool) {
        if (null == workerLoader) {
            workerLoader = new WorkerLoader(pool);
        }
    }

    public static WorkerLoader getWorkerLoader() {
        if (null == workerLoader) {
            throw new RuntimeException("init the loader first!");
        }
        return workerLoader;
    }

    private int getDBIndex() {
        return dbIndex;
    }

}
