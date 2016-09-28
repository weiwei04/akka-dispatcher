package com.express.storage.impl;

public enum PushMessageType {
    NEW_ORDER(1), ORDER_STATUS_CHANGE(2), ASSIGN_ORDER(3), CANCEL_ORDER(4), PAY_FOR_ORDER(5);

    private int code;

    private PushMessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
