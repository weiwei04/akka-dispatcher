/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

import com.express.cache_value.Driver;
import com.express.cache_value.Order;
import com.google.protobuf.InvalidProtocolBufferException;


public class Cache {

    private final JedisPool pool;

    private int ttl;

    private static final Logger logger = LogManager.getLogger(Cache.class);

    private static final int ORDER_DB_INDEX = 0;
    private static final int DRIVER_DB_INDEX = 1;
    private static final int HALL_DB_INDEX = 2;
    private static final int DRIVER_TODO_DB_INDEX = 3;
    private static final int ORDER_PUSH_DB_INDEX = 4;
    private static final int SIMPLE_CACHE_DB_INDEX = 5;
    private static final int DRIVER_SERVICE_STATUS_DB_INDEX = 6;

    public Cache(JedisPool redisPool) {
        pool = redisPool;
        ttl = 7 * 24 * 3600;
    }

    public Status publish(String channel, String message) {
        try (Jedis redis = pool.getResource()) {
            return publish(redis, channel, message);
        }
    }

    public Status publish(Jedis redis, String channel, String message) {
        long clientCount = redis.publish(channel, message);
        if (clientCount == 0) {
            logger.error("publish message[{}] to channel[{}] failed");
            return new Status(Status.FAILED, "publish failed");
        } else if (clientCount > 1) {
            logger.warn("expected publish 1 client, actual {}", clientCount);
        }

        logger.trace("ch[{}] <- message[{}] success", channel, message);

        return new Status();
    }

    public Status dispatch(String id) {
        return publish(DISPATCH_CHANNEL, dispatchCmd(id));
    }

    public Status cancelDispatch(String id) {
        return publish(DISPATCH_CHANNEL, cancelCmd(id));
    }

    private static final String DISPATCH_CHANNEL = "dispatch";
    private static final String DISPATCH_CMD = "dispatch#";
    private static final String CANCEL_CMD = "cancel#";

    private String cancelCmd(String orderId) {
        return CANCEL_CMD + orderId;
    }

    private String dispatchCmd(String orderId) {
        return DISPATCH_CMD + orderId;
    }

    public void insert(Order order, int ttl) {
        try (Jedis redis = pool.getResource()) {
            redis.select(ORDER_DB_INDEX);
            String id = order.getId();
            if (ttl > 0) {
                redis.setex(id, ttl, new String(order.toByteArray()));
            } else {
                redis.setex(id, this.ttl, new String(order.toByteArray()));
            }
        }
    }

    /**
     * 缓存一条order记录
     * 
     * @param order
     * @return
     */
    public void insert(Order order) {
        insert(order, 0);
    }

    /**
     * 缓存一条driver记录
     * 
     * @param driver
     * @return
     */
    public void insert(Driver driver) {
        try (Jedis redis = pool.getResource()) {
            redis.select(DRIVER_DB_INDEX);
            String id = driver.getId();
            redis.set(id, new String(driver.toByteArray()));
        }
    }

    /**
     * 从缓存中获取driver记录
     * 
     * @param id
     * @return
     */
    public Driver getDriver(String id) {
        try (Jedis redis = pool.getResource()) {
            redis.select(DRIVER_DB_INDEX);
            String data = redis.get(id);
            if (data == null) {
                return null;
            }

            return Driver.parseFrom(data.getBytes());
        } catch (InvalidProtocolBufferException e) {
            logger.error("deserialize driver[{}] failed, error[{}]", id, e.getMessage());
            return null;
        }
    }

    /**
     * 如果记录存在且满足cond中限定的条件, 以原子更新的方式将patch增加到原有记录中
     * 
     * @param id
     * @param patch
     * @param cond
     * @return
     */
    public Status patchOrder(String id, Order patch, Condition cond) {
        try (Jedis redis = pool.getResource()) {
            return patchOrder(redis, id, patch, cond);
        }
    }

    private Status patchOrder(Jedis redis, String id, Order patch, Condition cond) {
        redis.select(ORDER_DB_INDEX);
        redis.watch(id);
        Order order = getOrder(redis, id);
        if (order == null) {
            redis.unwatch();
            return new Status(Status.NOT_FOUND, "订单不存在");
        }

        Status s = cond.meet(order);
        if (!s.ok()) {
            redis.unwatch();
            s.setOldObject(order);
            return s;
        }
        s.setOldObject(order);

        // TODO: 确认这样玩可以
        // TODO: 确保patch中未设置的字段不会覆盖order中已经设置的字段
        int version = order.getVersion() + 1;
        Order newOrder = Order.newBuilder().mergeFrom(order).mergeFrom(patch).setVersion(version).build();
        s.setNewObject(newOrder);
        Transaction t = redis.multi();
        t.set(id, new String(newOrder.toByteArray()));
        List<Object> res = t.exec();
        if (res == null) {
            s.setCode(Status.INTERRUPT, "请稍后重试");
            return s;
        }

        return s;
    }

    /**
     * 从缓存中获取一条order记录
     * 
     * @param id
     * @return
     */
    public Order getOrder(String id) {
        try (Jedis redis = pool.getResource()) {
            return getOrder(redis, id);
        }
    }

    /**
     * 司机抢单后将这个订单放入司机的todo list
     * 
     * @param driverId
     * @param orderId
     * @return
     */
    public Status takeOrder(String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.TAKEN).setDriverId(driverId).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (order.getStatus() == OrderStatus.CREATE) {
                        s.setCode(Status.OK);
                        return s;
                    }
                    // TODO avoid chinese char
                    s.setCode(Status.FAILED, "订单已被抢");
                    return s;
                }
            };

            Status s = patchOrder(redis, orderId, patch, cond);
            if (!s.ok()) {
                return s;
            }

            logger.trace("driver[{}] take order[{}] success", driverId, orderId);

            long pickupTime = ((Order) s.getNewObject()).getPickupTime();
            // TODO replace cond with prepacked transaction
            Transaction t = redis.multi();
            t.select(DRIVER_TODO_DB_INDEX);
            t.zadd(driverId, pickupTime, orderId);
            t.select(HALL_DB_INDEX);
            t.del(orderId);
            t.select(ORDER_PUSH_DB_INDEX);
            t.del(orderId);
            // TODO check result
            t.exec();

            return s;
        }
    }

    public Status customerCancelOrder(final String customerId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.CUSTOMER_CANCEL).setCause(Reason.CUSTOMER_CANCEL)
                    .build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (!customerId.equals(order.getId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICING) {
                        s.setCode(Status.FAILED, "无法取消服务中的订单");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "已被系统取消");
                    }
                    return s;
                }
            };

            Status s = patchOrder(redis, orderId, patch, cond);
            if (!s.ok()) {
                return s;
            }

            logger.trace("customer[{}] cancel order[{}] success", customerId, orderId);

            return s;
        }
    }

    public Status driverCancelOrder(final String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.CREATE).clearDriverId().build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (!driverId.equals(order.getId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICING) {
                        s.setCode(Status.FAILED, "无法取消服务中的订单");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "客户已取消该订单");
                        return s;
                    }
                    return s;
                }
            };

            Status s = patchOrder(redis, orderId, patch, cond);
            if (!s.ok()) {
                return s;
            }

            logger.trace("driver[{}] cancel order[{}] success", driverId, orderId);

            return s;
        }
    }

    public Status cancelUnpaidOrder(String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.SERVICE_OVER).setCause(Reason.PAY_TIMEOUT).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if ((order.getStatus() == OrderStatus.TAKEN) && (order.getPayStatus() == PayStatus.FINISH_PAY)) {
                        s.setCode(Status.FAILED, "already payed");
                    }
                    return s;
                }
            };

            Status s = patchOrder(redis, orderId, patch, cond);
            if (!s.ok()) {
                logger.warn("failed to cancel unpaid order[{}], error[{}]", orderId, s.getCode());
            }

            logger.trace("cancel unpaid order[{}], status[{}]", orderId, s.getCode());

            return s;
        }
    }

    public Status cancelTimeoutOrder(String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.SERVICE_OVER).setCause(Reason.NO_DRIVER_TAKE)
                    .build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (order.getStatus() == OrderStatus.COMING) {
                        return s;
                    }
                    s.setCode(Status.FAILED, "已有司机抢单");
                    return s;
                }
            };

            Status s = patchOrder(redis, orderId, patch, cond);
            if (!s.ok()) {
                logger.warn("failed to cancel timeout order[{}], error[{}]", orderId, s.getCode());
            }

            logger.trace("cancel timeout order[{}], code[{}]", orderId, s.getCode());

            return s;
        }
    }

    public Status createCharge(String customerId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder()
            // .setStatus(OrderStatus.)
                    .setPayStatus(PayStatus.TO_PAY).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "超时未支付,订单已取消");
                        return s;
                    } else if (order.getPayStatus() == PayStatus.FINISH_PAY) {
                        s.setCode(Status.FAILED, "订单已完成支付");
                        return s;
                    }
                    return s;
                }
            };

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    public Status confirmCharge(String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setPayStatus(PayStatus.FINISH_PAY).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    return new Status();
                }
            };

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    /**
     * 为新创建的订单添加合适的司机 同时将订单加入到司机的预约大厅中
     * 
     * @param orderId
     * @param driverIds
     * @return
     */
    public void addQualifiedDrivers(String orderId, Collection<String> driverIds) {
        try (Jedis redis = pool.getResource()) {
            // 记录这个订单已经推送的司机id
            redis.select(ORDER_PUSH_DB_INDEX);
            Pipeline p = redis.pipelined();
            for (String driverId : driverIds) {
                p.lpush(orderId, driverId);
            }
            p.sync();

            // 将这个订单增加到司机的预约大厅中
            redis.select(HALL_DB_INDEX);
            p = redis.pipelined();
            for (String driverId : driverIds) {
                // 将这个订单增加到司机的预约大厅的第一条
                p.lpush(driverId, orderId);
            }
            p.sync();
        }
    }

    /**
     * 获取订单已经推送给的司机列表
     * 
     * @param orderId
     * @return
     */
    public List<String> getQualifiedDrivers(String orderId) {
        try (Jedis redis = pool.getResource()) {
            redis.select(ORDER_PUSH_DB_INDEX);
            return redis.lrange(orderId, 0, -1);
        }
    }

    /**
     * 司机的预约大厅
     * 
     * @param id
     * @return
     */
    public List<Order> getAvailableOrders(String id) {
        try (Jedis redis = pool.getResource()) {
            // 获取司机预约大厅可见的订单列表
            redis.select(HALL_DB_INDEX);
            List<String> orderIds = redis.lrange(id, 0, -1);

            // 根据订单列表获取具体的订单信息
            redis.select(ORDER_DB_INDEX);
            Pipeline p = redis.pipelined();
            for (String orderId : orderIds) {
                p.get(orderId);
            }
            List<Object> orderObjs = p.syncAndReturnAll();

            return lazyUpdateAvailableOrders(redis, id, orderObjs);
        }
    }

    private List<Order> lazyUpdateAvailableOrders(Jedis redis, String driverId, List<Object> objs) {
        List<Order> list = new ArrayList<>();
        List<String> availableOrderIds = new ArrayList<>();
        for (Object obj : objs) {
            if (obj == null) {
                continue;
            }
            try {
                Order order = Order.parseFrom(((String) obj).getBytes());
                if (order.getStatus() == OrderStatus.CREATE) {
                    list.add(order);
                    availableOrderIds.add(order.getId());
                }
            } catch (InvalidProtocolBufferException e) {
                logger.error("deserialize order for driver[{}] failed, error[{}]", driverId, e.getMessage());
            }
        }

        if (objs.size() > availableOrderIds.size()) {
            redis.select(HALL_DB_INDEX);
            redis.del(driverId);
            Pipeline p = redis.pipelined();
            for (String orderId : availableOrderIds) {
                p.lpush(driverId, orderId);
            }
            p.sync();
        }

        return list;
    }

    public int removeOrder(String id) {
        try (Jedis redis = pool.getResource()) {
            redis.select(ORDER_DB_INDEX);
            redis.del(id);
        }
        return 0;
    }

    public int removeDriver(String id) {
        try (Jedis redis = pool.getResource()) {
            redis.select(DRIVER_DB_INDEX);
            redis.del(id);
        }
        return 0;
    }

    public Order getCurrentOrder(String id) {
        try (Jedis redis = pool.getResource()) {
            redis.select(DRIVER_TODO_DB_INDEX);
            Set<String> orderIds = redis.zrange(id, 0, 0);
            if (orderIds.isEmpty()) {
                logger.trace("driver[{}] todo list is empty", id);
                return null;
            }
            String orderId = (String) orderIds.toArray()[0];
            return getOrder(redis, orderId);
        }
    }

    public List<Order> getTakenOrders(String id) {
        try (Jedis redis = pool.getResource()) {
            redis.select(DRIVER_TODO_DB_INDEX);
            Set<String> orderIds = redis.zrange(id, 0, -1);

            List<Order> list = new ArrayList<>();
            if (orderIds.isEmpty()) {
                logger.trace("driver[{}] todo list is empty", id);
                return list;
            }

            redis.select(ORDER_DB_INDEX);
            Pipeline p = redis.pipelined();
            for (String orderId : orderIds) {
                p.get(orderId);
            }
            List<Object> orderObjs = p.syncAndReturnAll();

            for (Object obj : orderObjs) {
                if (obj == null) {
                    logger.warn("order not found");
                    continue;
                }
                try {
                    list.add(Order.parseFrom(((String) obj).getBytes()));
                } catch (InvalidProtocolBufferException e) {
                    logger.error("deserialize order failed, error[{}]", e.getMessage());
                }
            }
            return list;
        }
    }

    private Order getOrder(Jedis redis, String id) {
        redis.select(ORDER_DB_INDEX);
        String data = redis.get(id);
        if (data == null) {
            return null;
        }
        try {
            return Order.parseFrom(data.getBytes());
        } catch (InvalidProtocolBufferException e) {
            logger.error("deserialize order[{}] failed, error[{}]", id, e.getMessage());
            return null;
        }
    }

    public Status gotoPickupLocation(final String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.COMING).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = new Status();
                    if (!driverId.equals(order.getDriverId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "订单已被取消");
                        return s;
                    } else if (order.getPayStatus() != PayStatus.FINISH_PAY) {
                        s.setCode(Status.FAILED, "客户还未支付");
                        return s;
                    }
                    return s;
                }
            };

            // TODO: update driver service status

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    public Status arrivePickupLocation(final String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.ARRIVED).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    // TODO: 设计模式,多个Condition层层嵌套即可
                    // TODO: 无需多个if else...
                    Status s = new Status();
                    if (!driverId.equals(order.getDriverId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "订单已被取消");
                    } else if (order.getStatus() != OrderStatus.COMING || order.getStatus() != OrderStatus.ARRIVED) {
                        s.setCode(Status.FAILED, "服务状态异常");
                        return s;
                    }
                    return s;
                }
            };

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    public Status startService(final String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.SERVICING).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = null;
                    if (!driverId.equals(order.getDriverId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "订单已被取消");
                        return s;
                    } else if (order.getStatus() != OrderStatus.COMING || order.getStatus() != OrderStatus.ARRIVED) {
                        s.setCode(Status.FAILED, "服务状态异常");
                        return s;
                    }
                    return s;
                }
            };

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    public Status finishService(final String driverId, String orderId) {
        try (Jedis redis = pool.getResource()) {
            Order patch = Order.newBuilder().setStatus(OrderStatus.SERVICE_OVER).setCause(Reason.OK).build();

            Condition cond = new Condition() {
                @Override
                public Status meet(Order order) {
                    Status s = null;
                    if (!driverId.equals(order.getDriverId())) {
                        s.setCode(Status.FAILED, "订单不存在");
                        return s;
                    } else if (order.getStatus() == OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "订单已被取消");
                        return s;
                    } else if (order.getStatus() != OrderStatus.SERVICING
                            || order.getStatus() != OrderStatus.SERVICE_OVER) {
                        s.setCode(Status.FAILED, "服务状态异常");
                        return s;
                    }
                    return s;
                }
            };

            return patchOrder(redis, orderId, patch, cond);
        }
    }

    public String get(String key) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            return redis.get(key);
        }
    }

    public void setex(String key, int ttl, String value) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            redis.setex(key, ttl, value);
        }
    }

    public void set(String key, String value) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            redis.set(key, value);
        }
    }

    public void remove(String key) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            redis.del(key);
        }
    }

    public void hset(String key, String field, String value) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            redis.hset(key, field, value);
        }
    }

    public String hget(String key, String field) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            return redis.hget(key, field);
        }
    }

    public void hdele(String key, String... field) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            redis.hdel(key, field);
        }
    }

    public Map<String, String> hgetall(String key) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            return redis.hgetAll(key);
        }
    }

    public Long incr(String key) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            return redis.incr(key);
        }
    }

    public Long expire(String key, int ttl) {
        try (Jedis redis = pool.getResource()) {
            redis.select(SIMPLE_CACHE_DB_INDEX);
            return redis.expire(key, ttl);
        }
    }
}
