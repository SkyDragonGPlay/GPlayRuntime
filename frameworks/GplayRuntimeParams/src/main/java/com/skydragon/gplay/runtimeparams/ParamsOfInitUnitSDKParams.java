package com.skydragon.gplay.runtimeparams;

import android.content.Context;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhangjunfei on 16/3/18.
 */
public class ParamsOfInitUnitSDKParams extends GenericsParams{
    /**
     * Gplay 给游戏分配的标识
     */
    public String uApiKey;
    /**
     * Gplay 给游戏分配的密钥
     */
    public String apiSecret;
    /**
     * Gplay 给游戏分配私钥
     */
    public String privateKey;

}
