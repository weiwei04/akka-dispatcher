package com.express.storage.impl;

public class PushMessage<T> {
    private PushMessageType pushMessageType;

    private T data;

    public PushMessage(PushMessageType pushMessageType, T data) {
        super();
        this.pushMessageType = pushMessageType;
        this.data = data;
    }

    public PushMessageType getPushMessageType() {
        return pushMessageType;
    }

    public void setPushMessageType(PushMessageType pushMessageType) {
        this.pushMessageType = pushMessageType;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Integer getMessageType() {
        return pushMessageType == null ? null : pushMessageType.getCode();
    }

}
