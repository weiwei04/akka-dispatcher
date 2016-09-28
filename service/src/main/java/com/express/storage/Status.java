/**
 * Copyright (C) 2015
 * Created by Wei Wei(weiwei.inf@gmail.com) on 12/17/15.
 */

package com.express.storage;

public class Status {
    public static final int OK = 0;
    public static final int FAILED = -1;
    public static final int NOT_FOUND = -3;
    public static final int INTERRUPT = -4;

    private Object oldObject = null;
    private Object newObject = null;
    private int code = OK;

    private String message = null;

    public Status() {
        code = OK;
        oldObject = null;
        newObject = null;
    }

    public Status(int code) { this.code = code; }

    public Status(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }

    public void setCode(int code) { this.code = code; }

    public void setCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() { return message; }

    public Object getOldObject() { return oldObject; }

    public Object getNewObject() { return newObject; }

    public void setOldObject(Object object) { oldObject = object; }

    public void setNewObject(Object object) { newObject = object; }

    public boolean ok() { return code == OK; }

    public boolean isFailed() { return code == FAILED; }

    public boolean isNotFound() { return code == NOT_FOUND; }

    public boolean isInterrupt() { return code == INTERRUPT; }
}

