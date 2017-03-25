package com.skydragon.gplay.runtime.entity.game;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.RuntimeLauncher;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public final class GameInfo{
    private static final String TAG = "GameInfo";
    public String mGameKey;                             // 游戏key 唯一
    public String mPackageName;                         // 游戏包名
    public String mDownloadUrl;                         // 当前的下载地址
    public String mGameName;                            // 游戏名称
    public String mGameVersion;                         // 游戏版本号
    public String mEngineVersion;                       // 标准引擎版本
    public String mEngine;                              // 标准引擎类型
    public String mEngineArch;                          // 标准引擎架构
    public String mOrientation;                         // 游戏横竖屏方向, 横屏为 0, 竖屏为 1
    public VerifyInfo mVerifyInfo;                      // 验证信息
    public ChannelConfigInfo mChannelConfigInfo;        // 渠道配置信息
    public int mRunMode;                                // 运行模式, 正常为 1, 自动分场景为 2

    public String mDownloadUrlOfLatestVersion;          // 最新版本的下载地址

    //Hola单机游戏多版本支持, url_prefix + version + url_suffix
    private String mDownloadUrlPrefix;
    private String mDownloadUrlSuffix;

    public boolean isArchSupported() {
        return !TextUtils.equals(mEngineArch, RuntimeConstants.ARCH_NOT_SUPPORTED);
    }

    public void initDownloadURL(boolean isOfflineGame, int currGameVersion) {
        if (isOfflineGame) {
            String recordDownloadUrl;
            Context context = RuntimeLauncher.getInstance().getContext();
            String recordURLFilePath = context.getDir("gplay", 0).getPath() + File.separator + "DownloadURLRecord.bin";

            if (mDownloadUrlPrefix != null) {
                recordDownloadUrl = mDownloadUrlPrefix + currGameVersion;
                if (mDownloadUrlSuffix != null) {
                    recordDownloadUrl = recordDownloadUrl + mDownloadUrlSuffix;
                }
            } else {
                recordDownloadUrl = FileUtils.readStringFromFile(recordURLFilePath);
            }

            if (!TextUtils.isEmpty(recordDownloadUrl) &&
                    !FileUtils.isExist(FileConstants.getOfflineGameMarkerPath(mPackageName))) {
                //没有新版本，有记录的下载地址
                mDownloadUrl = recordDownloadUrl;
            } else {
                mDownloadUrl = mDownloadUrlOfLatestVersion;
                FileUtils.writeStringToFile(recordURLFilePath, mDownloadUrlOfLatestVersion);
            }

            if (mDownloadUrlOfLatestVersion.equalsIgnoreCase(mDownloadUrl)) {
                RuntimeEnvironment.downloadURLChanged = false;
            } else {
                RuntimeEnvironment.downloadURLChanged = true;
            }
        } else {
            mDownloadUrl = mDownloadUrlOfLatestVersion;
            RuntimeEnvironment.downloadURLChanged = false;
        }

        LogWrapper.d(TAG, "initDownloadURL, isOfflineGame:" + isOfflineGame +
                ", downloadURLChanged:" + RuntimeEnvironment.downloadURLChanged +
                "\ndownloadUrl:" + mDownloadUrl + ", downloadUrlOfLatestVersion:" + mDownloadUrlOfLatestVersion);
    }

    public static GameInfo fromJson(JSONObject jsonObject) {
        GameInfo gameInfo = new GameInfo();
        try {
            gameInfo.mGameKey = jsonObject.optString("client_id");
            gameInfo.mPackageName = jsonObject.optString("package_name");
            gameInfo.mGameName = jsonObject.optString("game_name");
            //download url是最新版本的下载地址
            gameInfo.mDownloadUrlOfLatestVersion = FileUtils.ensurePathEndsWithSlash(jsonObject.optString("download_url"));
            gameInfo.mOrientation = jsonObject.optInt("orientation") == 0 ? "landscape" : "portrait";
            gameInfo.mVerifyInfo = parseVerifyInfo(jsonObject.optJSONObject("verifyinfo"));
            gameInfo.mChannelConfigInfo = parseChannelConfigInfo(jsonObject.optJSONObject("channel_config"));
            gameInfo.mRunMode = jsonObject.optInt("run_mode", 1);
            gameInfo.mGameVersion = jsonObject.optString("cur_res_ver_code", "1");

            //兼容Hola单机游戏多版本支持
            JSONObject extra = jsonObject.optJSONObject("gplay_extra");
            if (extra != null) {
                gameInfo.mDownloadUrlPrefix = extra.optString("download_url_prefix", null);
                gameInfo.mDownloadUrlSuffix = extra.optString("download_url_suffix", null);
            }

            return gameInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gameInfo;
    }

    public static GameInfo fromJson(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            return GameInfo.fromJson(jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("client_id", mGameKey);
            jsonObject.put("package_name", mPackageName);
            jsonObject.put("game_name", mGameName);
            jsonObject.put("download_url", mDownloadUrl);
            jsonObject.put("engine", mEngine);
            jsonObject.put("engine_version", mEngineVersion);
            jsonObject.put("arch", mEngineArch);
            jsonObject.put("orientation", mOrientation);
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    /**
     * 获取验证信息
     */
    private static VerifyInfo parseVerifyInfo(JSONObject jsonObject) {
        if (null == jsonObject) return null;
        VerifyInfo info = new VerifyInfo();
        int compatible = jsonObject.optInt("compatible", 1);
        info.setCompatible(compatible);
        int visible = jsonObject.optInt("visible", 1);
        info.setVisible(visible);
        int maintain = jsonObject.optInt("maintain", 1);
        info.setMaintaining(maintain);
        return info;
    }

    /**
     * 获取渠道配置信息
     */
    private static ChannelConfigInfo parseChannelConfigInfo(JSONObject jsonObject) {
        if (jsonObject == null)
            Log.e(TAG, "parseChannelConfigInfo: jsonObject is null");

        ChannelConfigInfo channelConfigInfo = new ChannelConfigInfo();
        channelConfigInfo.setChannelConfigInfo(jsonObject);
        return channelConfigInfo;
    }

    @Override
    public String toString() {
        return "GameInfo{" +
                "mGameKey='" + mGameKey + '\'' +
                ", mPackageName='" + mPackageName + '\'' +
                ", mDownloadUrl='" + mDownloadUrl + '\'' +
                ", mGameName='" + mGameName + '\'' +
                ", mEngineVersion='" + mEngineVersion + '\'' +
                ", mEngine='" + mEngine + '\'' +
                ", mEngineArch='" + mEngineArch + '\'' +
                ", mOrientation=" + mOrientation +
                ", mVerifyInfo=" + mVerifyInfo +
                ", mChannelConfigInfo=" + mChannelConfigInfo.toString() +
                '}';
    }
}
