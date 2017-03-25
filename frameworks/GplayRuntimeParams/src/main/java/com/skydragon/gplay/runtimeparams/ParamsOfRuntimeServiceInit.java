package com.skydragon.gplay.runtimeparams;

import android.content.Context;

/**
 * Created by zhangjunfei on 16/3/21.
 */
public class ParamsOfRuntimeServiceInit extends GenericsParams{
    public Context context;
    //渠道ID
    public String channelID;
    //runtime文件存放根目录路径
    public String runtimeDir;
    //游戏资源文件在sd卡上的存放路径
    public String cacheDir;
}
