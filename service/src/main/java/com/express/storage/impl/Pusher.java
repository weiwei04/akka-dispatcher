package com.express.storage.impl;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.express.cache_value.Order;

public class Pusher {

    private final Logger logger = LogManager.getLogger(Pusher.class);

    // @Value("#{pushConfig.pushUrl}")
    private String pushUrl;

    public void pushForNewOrder(Order order, List<String> driverIds) {
        push(order, driverIds, PushMessageType.NEW_ORDER);
    }

    public void pushForAssignOrder(Order order, List<String> driverIds) {
        push(order, driverIds, PushMessageType.ASSIGN_ORDER);
    }

    public void pushForCancelOrder(Order order, List<String> driverIds) {
        push(order, driverIds, PushMessageType.CANCEL_ORDER);
    }

    public void pushForPay(Order order, List<String> driverIds) {
        push(order, driverIds, PushMessageType.PAY_FOR_ORDER);
    }

    // TODO 需要引入common.jar
    private void push(Order order, List<String> driverIdList, PushMessageType messageType) {
        // String driverIds = convertDriverIds(driverIdList);
        // Map<String, Object> parameters = new HashMap<String, Object>();
        // parameters.put("driverIds", driverIds);
        // PushMessage<OrderVoForDriver> pushMessage = new
        // PushMessage<OrderVoForDriver>(messageType,
        // OrderConveter.Order2OrderVo(order));
        // parameters.put("content", StringUtil.convert2String(pushMessage));
        // parameters.put("title", "租个司机");
        // parameters.put("orderId", order.getId());
        // parameters.put("type", 1);
        // try {
        // String result = RequestUtil.doPost(pushUrl,
        // StringUtil.convert2String(parameters));
        // logger.info("push result: " + result);
        // } catch (ConnectException e) {
        // logger.error("error happens while call push service: " +
        // e.getMessage());
        // }
    }

    private String convertDriverIds(List<String> driverIdList) {
        String driverIds = null;

        StringBuilder driverIdBuilder = new StringBuilder();
        for (String driverId : driverIdList) {
            driverIdBuilder.append(driverId);
            driverIdBuilder.append(",");
        }
        if (driverIdBuilder.length() > 0) {
            driverIdBuilder.setLength(driverIdBuilder.length() - 1);
        }

        driverIds = driverIdBuilder.toString();
        return driverIds;
    }

}
