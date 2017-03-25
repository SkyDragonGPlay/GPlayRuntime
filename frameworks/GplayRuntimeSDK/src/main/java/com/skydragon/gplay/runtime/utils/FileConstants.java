package com.skydragon.gplay.runtime.utils;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.skydragon.gplay.runtime.RuntimeConstants;

import java.io.File;

public final class FileConstants {
    private static final String TAG = "FileConstants";
    private static final String FILE_GPLAY_DEBUG_TXT = "gplay_debug.txt";

    private static String PATH_ROOT = Environment.getExternalStorageDirectory() + File.separator + "gplay" + File.separator;

    public static String APP_DATA_DIR;

    private static String sResourcesDir;
    private static String sDownloadDir;

    public static void init(Context ctx) {
        APP_DATA_DIR = FileUtils.getDataDir(ctx);
    }

    public static String getDebugFile() {
        return Environment.getExternalStorageDirectory() + File.separator + FILE_GPLAY_DEBUG_TXT;
    }

    public static void setCacheDir(String rootPath, String channelID, Context ctx) {
        boolean isPathValid = true;

        if (TextUtils.isEmpty(rootPath)) {
            isPathValid = false;
        }

        if (!FileUtils.ensureDirExists(rootPath)) {
            LogWrapper.e(TAG, "Gplay root path can't be created!");
            isPathValid = false;
        }

        if (isPathValid) {
            PATH_ROOT = FileUtils.ensurePathEndsWithSlash(rootPath);

        } else {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                    !Environment.isExternalStorageRemovable()) {
                File saveDirFile = ctx.getExternalFilesDir(null);
                if (saveDirFile != null) {
                    PATH_ROOT = saveDirFile.getPath() + File.separator + "gplay" + File.separator;
                }
            }
            if (PATH_ROOT == null) {
                PATH_ROOT = ctx.getDir("gplay", 0).getPath();
            }
        }

        sResourcesDir = PATH_ROOT + channelID + File.separator;
        sDownloadDir = sResourcesDir + "download" + File.separator;
    }

    public static String getRootPath() {
        return PATH_ROOT;
    }

    public static String getSystemDataDir() {
        return APP_DATA_DIR + "gplay" + File.separator;
    }

    public static String getResourcesDir() {
        return sResourcesDir;
    }

    public static String getNoMediaFilePath() {
        return sResourcesDir + ".nomedia";
    }

    public static String getLogDir() {
        return sResourcesDir + "log" + File.separator;
    }

    public static String getDownloadDir() {
        return sDownloadDir;
    }

    public static String getGameDownloadDir(String packageName) {
        return sDownloadDir + packageName + File.separator;
    }

    public static String getGamesDir() {
        return sResourcesDir + "games" + File.separator;
    }

    public static String getTempDir() {
        String sPath = getGamesDir() + "temp" + File.separator;
        Utils.ensureDirExists(sPath);
        return sPath;
    }

    // 预加载游戏目录。
    public static String getPreloadDir(String packageName) {
        String sPath = getGamesDir() + packageName + File.separator + "preload/";
        Utils.ensureDirExists(sPath);
        return sPath;
    }

    public static String getGameImagesDir() {
        return sResourcesDir + "game_images" + File.separator;
    }

    public static String getGameRootDir(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return getGamesDir();
        } else {
            return getGamesDir() + packageName + File.separator;
        }
    }

    public static String getOfflineGameMarkerPath(String packageName) {
        return getGameRootDir(packageName) + RuntimeConstants.FILE_OFFLINE_GAME_MARKER;
    }

    public static String getGameResourceDir(String packageName) {
        return getGameRootDir(packageName) + "resource" + File.separator;
    }

    public static String getGameKeyFilePath(String packageName, String gameKey) {
        return getGameRootDir(packageName) + gameKey;
    }

    public static String getResourceConfigPath(String packageName) {
        return getGameRootDir(packageName) + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME;
    }

    public static String getGroupArchivePath(String packageName, String suffix) {
        return getGameDownloadDir(packageName) + suffix;
    }

    public static String getLocalGameInfoJsonPath(String gameKey) {
        return  getGamesDir() + "gameInfo_" + gameKey + ".bin";
    }

    public static String getLocalRuntimeCompatibilityPath() {
        return sResourcesDir + "runtimeCompatibilityInfo.bin";
    }
}
