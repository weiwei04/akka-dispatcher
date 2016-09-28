/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/8/15.
 */

package com.express.storage.impl;

import com.express.cache_value.Driver;

public class DispatchStrategy {
    private Integer radius;
    private Integer carType;
    private Integer carGradeType;
    private Integer driverServiceStatus;

    public Integer getRadius() {
        return radius;
    }

    public void setRadius(Integer radius) {
        this.radius = radius;
    }

    public Integer getCarType() {
        return carType;
    }

    public void setCarType(Integer carType) {
        this.carType = carType;
    }

    public Integer getCarGradeType() {
        return carGradeType;
    }

    public void setCarGradeType(Integer carGradeType) {
        this.carGradeType = carGradeType;
    }

    public Integer getDriverServiceStatus() {
        return driverServiceStatus;
    }

    public void setDriverServiceStatus(Integer driverServiceStatus) {
        this.driverServiceStatus = driverServiceStatus;
    }

    public Boolean isMatch(Driver driverStatus) {
        if (driverStatus.getCarType() == this.carType
                && driverStatus.getDriverServiceStatus() == this.driverServiceStatus) {
            if (driverStatus.getIsDegrade()) {
                // TODO grandType的定义（舒适型），找剑锋
                return this.carGradeType.equals(driverStatus.getCarGrandType()) || this.carGradeType.equals(1);
            } else {
                return this.carGradeType.equals(driverStatus.getCarGrandType());
            }
        } else {
            return false;
        }
    }
}
