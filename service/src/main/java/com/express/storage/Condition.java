/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage;

import com.express.cache_value.Order;

public abstract class Condition {
    public abstract Status meet(Order order);
}
