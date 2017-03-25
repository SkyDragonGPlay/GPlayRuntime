package com.skydragon.gplay.runtime.callback;

import com.skydragon.gplay.runtime.entity.ResultInfo;

public interface ProtocolCallback<T> {
    /**
     * 成功回调
     *
     */
    void onSuccess(T obj);

    /**
     * 失败回调
     *
     */
    void onFailure(ResultInfo err);
}
