package com.skydragon.gplay.runtimeparams;

import android.content.Context;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created by zhangjunfei on 16/3/18.
 */
public class ParamsOfInitChannelParams extends GenericsParams{
    public Context ctx;
    /**
     * 渠道ID
     */
    public String channelId;
    /**
     * 游戏在渠道上申请的参数信息
     */
    public JSONObject jsonChannelInfo;
    /**
     * 通知URL
     */
    public String loginNotifyUrl;
    /**
     * 游戏信息
     */
    public String jsonGameInfo;

}
