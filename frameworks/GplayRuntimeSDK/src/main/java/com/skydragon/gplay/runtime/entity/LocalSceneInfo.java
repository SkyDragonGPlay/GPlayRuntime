package com.skydragon.gplay.runtime.entity;

import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONException;
import org.json.JSONObject;

public final class LocalSceneInfo {
    private static final String TAG = "LocalSceneInfo";
    public static final String KEY_NAME = "name";
    public static final String KEY_MODIFIED = "modified";
    public static final String KEY_VERSION = "version";
    public static final String KEY_GAME_VERSION = "game_version";
    public static final int DEFAULT_VERSION = -1;
    /*
    * 中间态标识，1 标志当前场景被修改； 2 标准未被修改；-1 旧版本配置文件设置的默认值。
    * */
    public static final int STATUS_OLD_CONFIG = -1;
    public static final int STATUS_MODIFIED = 1;
    public static final int STATUS_UNMODIFIED = 2;

    private String _name;
    private int _gameVersion;
    private int _version;
    private int _modified;

    public static LocalSceneInfo fromJson(JSONObject sceneJsonObj){
        LocalSceneInfo localSceneInfo = new LocalSceneInfo();
        localSceneInfo._version = sceneJsonObj.optInt(KEY_VERSION, DEFAULT_VERSION);
        localSceneInfo._gameVersion = sceneJsonObj.optInt(KEY_GAME_VERSION, DEFAULT_VERSION);
        localSceneInfo._modified = sceneJsonObj.optInt(KEY_MODIFIED, STATUS_OLD_CONFIG);
        localSceneInfo._name = sceneJsonObj.optString(KEY_NAME);
        return localSceneInfo;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(KEY_NAME, _name);
            jsonObject.put(KEY_VERSION, _version);
            jsonObject.put(KEY_GAME_VERSION, _gameVersion);
            jsonObject.put(KEY_MODIFIED, _modified);
        } catch (JSONException e) {
            LogWrapper.d(TAG, "parse to local jsonobject failure!");
        }
        return jsonObject;
    }

    public int getVerison() {
        return _version;
    }

    public void updateVersion(int version) {
        this._version = version;
    }

    public int getGameVerison() {
        return _gameVersion;
    }

    public void updateGameVerison(int version) {
        this._gameVersion = version;
    }

    public String getName() {
        return _name;
    }

    public boolean isModified() {
        return ! (_modified == STATUS_UNMODIFIED);
    }

    public void setModified() {
        _modified = STATUS_MODIFIED;
    }

    public void setUnModified() {
        _modified = STATUS_UNMODIFIED;
    }

    public boolean isOldConfigStatus() {
        return (_modified == STATUS_OLD_CONFIG);
    }
}
