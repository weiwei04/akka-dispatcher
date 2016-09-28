/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage;

import com.express.cache_value.Order;

import java.util.List;

public interface OrderService {
    /**
     * 根据订单ID获取订单信息
     * 
     * @param orderId
     * @return
     */
    Order getOrder(String orderId);

    /**
     * 司机获取某个订单的详情
     * @param driverId
     * @param orderId
     * @return
     */
    Order getDriverOrder(String driverId, String orderId);

    /**
     * 获取司机已接订单中的按上车时间排序的第一个订单
     * @param driverId
     * @return
     */
    Order getCurrentOrder(String driverId);

    /**
     * 获取司机已接的订单列表
     * @param driverId
     * @return
     */
    List<Order> getTakenOrders(String driverId);

    /**
     * 客户获取某个订单的详情
     * @param customerId
     * @param orderId
     * @return
     */
    Order getCustomerOrder(String customerId, String orderId);

    /**
     * 订单服务新建订单
     * 内部会调用派单逻辑,寻找合适的司机并推送
     * @param order
     * @return orderId
     * will throw Exception -1:failed, -2:duplicated order
     *         -3:too many cancel
     */
    String createOrder(Order order);

    /**
     * 司机预约大厅
     * @param driverId
     * @param offset
     * @param limit
     * @return 预约大厅订单列表
     */
    List<Order> getAvailableOrders(String driverId, int offset, int limit);

    /**
     * 司机调用这个接口抢单
     * @param driverId
     * @param orderId
     */
    void takeOrder(String driverId,
                  String orderId);


    /**
     * 司机端调用这个接口告知开始前往服务地点
     * @param driverId
     * @param orderId
     */
    void gotoPickupLocation(String driverId, String orderId);

    /**
     * 司机端调用这个接口告知已经到达服务地点
     */
    void arrivePickupLocation(String driverId, String orderId);

    /**
     * 司机端调用这个接口告诉订单服务完成
     * @param driverId
     * @param orderId
     */
    void finishService(String driverId, String orderId);

    /**
     * 司机端调用这个接口告知开始服务
     * @param driverId
     * @param orderId
     */
    void startService(String driverId, String orderId);

    /**
     * 取消订单,客户取消订单
     * @param customerId
     * @param orderId
     */
    void customerCancelOrder(String customerId,
                    String orderId);

    /**
     * 取消订单,司机取消订单
     * @param driverId
     * @param orderId
     */
    void driverCancelOrder(String driverId,
                          String orderId);

    /**
     * 系统调用这个接口取消超时未支付的订单
     * @param orderId
     * @return >= 0:success【涵盖客户取消的订单】, -1:已经完成支付
     */
    void cancelUnpaidOrder(String orderId);

    /**
     * 系统调用这个接口取消超时无司机接单的订单
     * @param orderId
     * @return >= 0:oldStatus【涵盖客户取消的订单】, -1:已经有司机接单
     */
    void cancelTimeoutOrder(String orderId);

    /**
     * 系统调用这个接口寻找司机,然后派单
     * @param orderId
     */
    void dispatch(String orderId);
}
