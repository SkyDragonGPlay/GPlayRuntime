package com.skydragon.gplay.runtime.entity;

public final class ResultInfo {

    private int errorCode = -1;
    private String msg;
    private boolean isSuccess;
    public int getErrorCode() {
        return errorCode;
    }
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    public String getMsg() {
        return msg;
    }
    public void setMsg(String msg) {
        this.msg = msg;
    }

    public void setIsSuccess(boolean isSuccess) {
        this.isSuccess = isSuccess;
    }

    public boolean isSuccessed() {
        return isSuccess;
    }
    
}
