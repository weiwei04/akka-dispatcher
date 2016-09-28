/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage;

import com.express.cache_value.Order;

public class Database {

    public Order getOrder(String id) {
        return null;
    }

    public int insert(Order order) {
        return 0;
    }

    public int update(Order order) {
        // TODO: update db where version < order.getVersion()
        return 0;
    }
}
