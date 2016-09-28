/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.express.cache_value.Driver;
import com.express.cache_value.Order;
import com.express.storage.Cache;
import com.express.storage.Database;
import com.express.storage.OrderService;
import com.express.storage.OrderStatus;
import com.express.storage.PayStatus;
import com.express.storage.Status;

public class OrderServiceImpl implements OrderService {

    private static final Logger logger = LogManager.getLogger(OrderServiceImpl.class);

    private Cache cache;

    private Database db;

    // @Autowired
    // private DriverLocationService driverLocationService;

    private Pusher pusher;

    /**
     * 根据订单ID获取订单信息
     *
     * @param orderId
     * @return
     */
    public Order getOrder(String orderId) {
        Order order = cache.getOrder(orderId);
        if (order != null) {
            return order;
        }

        return db.getOrder(orderId);
    }

    /**
     * 司机获取某个订单的详情
     * 
     * @param driverId
     * @param orderId
     * @return
     */
    public Order getDriverOrder(String driverId, String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        if (!driverId.equals(driverId)) {
            logger.warn("driverId mismatch for order[{}], expected[{}] actual[{}]", orderId, driverId,
                    order.getDriverId());
            return null;
        }

        return order;
    }

    /**
     * 获取司机已接订单中的按上车时间排序的第一个订单
     * 
     * @param driverId
     * @return
     */
    public Order getCurrentOrder(String driverId) {
        return cache.getCurrentOrder(driverId);
    }

    /**
     * 获取司机已接的订单列表
     * 
     * @param driverId
     * @return
     */
    public List<Order> getTakenOrders(String driverId) {
        return cache.getTakenOrders(driverId);
    }

    /**
     * 客户获取某个订单的详情
     * 
     * @param customerId
     * @param orderId
     * @return
     */
    public Order getCustomerOrder(String customerId, String orderId) {
        Order order = getOrder(orderId);
        if (order == null) {
            return null;
        }

        if (!customerId.equals(orderId)) {
            logger.warn("customerId mismatch for order[{}], expected[{}] actual[{}]", orderId, customerId,
                    order.getId());
            return null;
        }

        return order;
    }

    /**
     * 订单服务新建订单 内部会调用派单逻辑,寻找合适的司机并推送
     * 
     * @param order
     * @return orderId will throw Exception -1:failed, -2:duplicated order
     *         -3:too many cancel
     */
    public String createOrder(Order order) {
        String id = "";
        order = Order.newBuilder().mergeFrom(order).setId(id).build();

        cache.insert(order);
        db.insert(order);
        cache.dispatch(id);

        logger.info("create order[{}] success", id);

        return id;
    }

    /**
     * 司机预约大厅
     * 
     * @param driverId
     * @param offset
     * @param limit
     * @return 预约大厅订单列表
     */
    public List<Order> getAvailableOrders(String driverId, int offset, int limit) {
        return cache.getAvailableOrders(driverId);
    }

    /**
     * 司机调用这个接口抢单
     * 
     * @param driverId
     * @param orderId
     */
    public void takeOrder(String driverId, String orderId) {

        Status s = cache.takeOrder(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException(Response, s.getMessage());
        }

        // TODO: write db based on version
        db.update((Order) s.getNewObject());
    }

    /**
     * 司机端调用这个接口告知开始前往服务地点
     * 
     * @param driverId
     * @param orderId
     * @return 0:success, -1:failed
     */
    public void gotoPickupLocation(String driverId, String orderId) {
        Status s = cache.gotoPickupLocation(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException();
        }

        db.update((Order) s.getNewObject());
    }

    /**
     * 司机端调用这个接口告知已经到达服务地点
     */
    public void arrivePickupLocation(String driverId, String orderId) {
        Status s = cache.arrivePickupLocation(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException;
        }
        db.update((Order) s.getNewObject());
    }

    /**
     * 司机端调用这个接口告诉订单服务完成
     * 
     * @param driverId
     * @param orderId
     */
    public void finishService(String driverId, String orderId) {
        Status s = cache.finishService(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException
        }
        db.update((Order) s.getNewObject());
    }

    /**
     * 司机端调用这个接口告知开始服务
     * 
     * @param driverId
     * @param orderId
     */
    public void startService(String driverId, String orderId) {
        Status s = cache.startService(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException
        }
        db.update((Order) s.getNewObject());
    }

    /**
     * 取消订单,客户取消订单
     * 
     * @param customerId
     * @param orderId
     */
    public void customerCancelOrder(String customerId, String orderId) {
        Status s = cache.customerCancelOrder(customerId, orderId);
        if (!s.ok()) {
            // throw new BusinessException
        }

        // TODO: write db based on version
        db.update((Order) s.getNewObject());
    }

    /**
     * 取消订单,司机取消订单
     * 
     * @param driverId
     * @param orderId
     */
    public void driverCancelOrder(String driverId, String orderId) {
        Status s = cache.driverCancelOrder(driverId, orderId);
        if (!s.ok()) {
            // throw new BusinessException
        }
        db.update((Order) s.getNewObject());
    }

    /**
     * 系统调用这个接口取消超时未支付的订单
     * 
     * @param orderId
     */
    public void cancelUnpaidOrder(String orderId) {
        Status s = cache.cancelUnpaidOrder(orderId);
        if (!s.ok()) {
            logger.warn("failed to cancel unpaid order[{}], error[{}]", orderId, s.getMessage());
        }
    }

    /**
     * 系统调用这个接口取消超时无司机接单的订单
     * 
     * @param orderId
     */
    public void cancelTimeoutOrder(String orderId) {
        Status s = cache.cancelTimeoutOrder(orderId);
        if (!s.ok()) {
            logger.warn("failed to cancel timeout order[{}], error[{}]", orderId, s.getMessage());
        }
    }

    public void createCharge(String customerId, String orderId) {
        Status s = cache.createCharge(customerId, orderId);
        if (!s.ok()) {
            logger.error("failed to create charge for order[{}], error[{}]", orderId, s.getMessage());
            // TODO throw new BusinessException
        }
    }

    public void confirmCharge(String orderId) {
        Status s = cache.confirmCharge(orderId);
        if (!s.ok()) {
            logger.error("failed to confirm charge for order[{}], error[{}]", orderId, s.getMessage());
        }
    }

    /**
     * 系统调用这个接口寻找司机,然后派单
     * 
     * @param orderId
     */
    public void dispatch(String orderId) {
        // OrderAgent orderAgent = new OrderAgent(orderId, pool);

        // Order order = orderAgent.getOrder();
        Order order = cache.getOrder(orderId);
        if (order == null) {
            cache.cancelDispatch(orderId);
            logger.warn("order[{}] not exist will not dispatch", orderId);
            return;
        }

        if (cancelDispatch(order)) {
            return;
        }

        DispatchStrategy dispatchStrategy = getDispatchStrategy(order);

        List<String> driverIds = findDrivers(order, dispatchStrategy);
        if (null != driverIds && !driverIds.isEmpty()) {
            pusher.pushForNewOrder(order, driverIds);
            logger.info("dispatch [{}] drivers for order[{}]", driverIds.size(), orderId);
        } else {
            logger.info("no qualified drivers found for order[{}]", orderId);
        }
    }

    private boolean cancelDispatch(Order order) {
        int status = order.getStatus();
        String orderId = order.getId();
        // 还无司机接单
        if (status == OrderStatus.CREATE) {
            if (dispatchTimeout(order)) {
                cancelTimeoutOrder(orderId);
                cache.cancelDispatch(orderId);
                logger.warn("cancel timeout order[{}] ", orderId);
                return true;
            }
        }
        // 支付超时
        if (status == OrderStatus.TAKEN && order.getPayStatus() != PayStatus.FINISH_PAY) {
            if (payTimeout(order)) {
                cancelUnpaidOrder(orderId);
                return true;
            }
            logger.trace("driver[{}] taken order[{}] waiting to pay", order.getDriverId(), orderId);
        } else if (order.getPayStatus() == PayStatus.FINISH_PAY) {
            cache.cancelDispatch(orderId);
            logger.info("order[{}] waiting driver to service, cancel dispatch", orderId);
            return true;
        }
        return false;
    }

    private DispatchStrategy getDispatchStrategy(Order order) {
        DispatchStrategy dispatchStrategy = new DispatchStrategy();
        dispatchStrategy.setRadius(getPushRadius(order));
        // TODO
        return null;

    }

    public List<String> findDrivers(Order order, DispatchStrategy dispatchStragegy) {

        List<String> distanceAvailableDriverIds = queryDriversByDistance(order, dispatchStragegy.getRadius());

        if (distanceAvailableDriverIds == null) {
            return null;
        }
        List<String> availableDrivers = filterDrivers(order, distanceAvailableDriverIds, dispatchStragegy);

        // TODO cache中添加getNotifiedDrivers()方法
        List<String> notifiedDriverIds = new ArrayList<String>();
        availableDrivers.removeAll(notifiedDriverIds);

        addRedisDriverList(order, availableDrivers);

        logger.info("locationList:" + Arrays.toString(availableDrivers.toArray()));
        return availableDrivers;
    }

    // TODO DB中封装查找附近司机的功能
    private List<String> queryDriversByDistance(Order order, int radius) {
        List<String> drivers = new ArrayList<String>();
        return drivers;
    }

    private List<String> filterDrivers(Order order, List<String> driverIds, DispatchStrategy dispatchStragegy) {
        List<String> availableDriverIds = new ArrayList<String>();
        for (String driverId : driverIds) {
            Driver driver = cache.getDriver(driverId);
            if (dispatchStragegy.isMatch(driver)) {
                availableDriverIds.add(driver.getId());
            }
        }

        return availableDriverIds;
    }

    private void addRedisDriverList(Order order, List<String> driverIds) {
        cache.addQualifiedDrivers(order.getId(), driverIds);
    }

    private boolean dispatchTimeout(Order order) {
        Date createTime = new Date(order.getCreateOrderTime());
        // TODO 引入DateUtil,同时把10分钟配置化
        // return DateUtil.addMins(createTime, 10).after(new Date());
        return false;
    }

    private boolean payTimeout(Order order) {
        Date takeOrderTime = new Date(order.getTakeOrderTime());
        // TODO 引入DateUtil,同时把5分钟配置化
        // return DateUtil.addMins(createTime, 5).after(new Date());
        return false;
    }

    private int getPushRadius(Order order) {
        Date pickupTime = new Date(order.getPickupTime());
        // TODO 引入DateUtil,同时把60分钟和8km配置化
        // if (DateUtil.addMins(new Date(), 60).after(pickupTime)) {
        // return 8000;
        // } else {
        // //TODO 全城的策略还需要加
        // return 100000;
        // }
        return 100;
    }
}
