package com.skydragon.gplay.runtime;

import java.util.HashMap;

public final class RuntimeEnvironment {

    public static boolean debugMode = false;
    public static String urlCurrentGame = "";
    public static boolean downloadURLChanged = false;  //标示下载地址是否有变化
    public static String engineType = ";";
    public static String engineVersion = "";
    public static String versionCurrentGameRuntime = "";
    public static String currentPackageName = "Unknown";
    public static String currentGameKey = "";
    public static String currentGameName = "";
    public static String pathCurrentGameDir = "";
    public static String pathCurrentGameResourceDir = "";
    public static String pathHostRuntimeDir = "";
    public static String pathHostRuntimeLibVersionDir = "";
    public static String pathHostRuntimeResourceDir = "";
    public static String pathLocalConfig = "";
    public static boolean boolSilentDownloadEnabled = true;
    public static boolean boolHostManageRuntime = false;// 判断是否是集成模式
    public static boolean boolIsOfflineGame = false;
    public static String channel = "";
    public static String currentGameOrientation = "";
    public static String hostPackageName = "";
    public static String currentGameArch = "";

    public static HashMap<String, Object> getParametersForEngineJAR() {
        HashMap<String, Object> allParameters = new HashMap<>(20);
        allParameters.put("GameKey", currentGameKey);
        allParameters.put("GameName", currentGameName);
        allParameters.put("GameDownloadURL", urlCurrentGame);
        allParameters.put("GamePackageName", currentPackageName);
        allParameters.put("GameOrientation", currentGameOrientation);
        allParameters.put("GameArch", currentGameArch);

        allParameters.put("EngineType", engineType);
        allParameters.put("EngineVersion", engineVersion);

        allParameters.put("GameDir", pathCurrentGameDir);
        allParameters.put("GameResourceDir", pathCurrentGameResourceDir);
        allParameters.put("CommonResourceDir", pathHostRuntimeResourceDir);

        allParameters.put("SilentDownloadEnabled", boolSilentDownloadEnabled);
        allParameters.put("HostManageRuntimeEnabled", boolHostManageRuntime);
        allParameters.put("IsOfflineGame", boolIsOfflineGame);
        allParameters.put("ChannelID", channel);

        allParameters.put("HostPackageName", hostPackageName);
        allParameters.put("DebugMode", debugMode);
        return allParameters;
    }
}
