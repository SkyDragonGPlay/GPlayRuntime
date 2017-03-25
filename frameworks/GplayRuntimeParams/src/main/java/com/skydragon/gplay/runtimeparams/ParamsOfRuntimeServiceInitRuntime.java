package com.skydragon.gplay.runtimeparams;

import android.app.Activity;

import com.skydragon.gplay.thirdsdk.IChannelSDKBridge;
import com.skydragon.gplay.thirdsdk.IChannelSDKServicePlugin;

/**
 * Created by zhangjunfei on 16/3/21.
 */
public class ParamsOfRuntimeServiceInitRuntime extends GenericsParams {
    public Activity activity;
    public IChannelSDKBridge channelSDKBridge;
    public IChannelSDKServicePlugin channelSDKServicePlugin;
}
