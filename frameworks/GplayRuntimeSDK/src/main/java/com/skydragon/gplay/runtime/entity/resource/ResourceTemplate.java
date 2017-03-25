package com.skydragon.gplay.runtime.entity.resource;

import org.json.JSONObject;

public class ResourceTemplate {
    private static final String VERSION_NAME = "verison_name";
    private static final String VERSION_CODE = "version_code";
    protected static final String MD5 = "md5";
    private static final String DOWNLOAD_URL = "url";

    private String _version_name;
    private int _version_code;
    private String _md5;
    private String _download_url;

    public ResourceTemplate(JSONObject jsonObject) {
        if (jsonObject != null) {
            _version_name = jsonObject.optString(VERSION_NAME);
            _version_code = jsonObject.optInt(VERSION_CODE);
            _md5 = jsonObject.optString(MD5);
            _download_url = jsonObject.optString(DOWNLOAD_URL);
        }
    }

    public static ResourceTemplate fromJson(JSONObject jsonObject) {
        return new ResourceTemplate(jsonObject);
    }

    public String getVersionName() {
        return _version_name;
    }

    public int getVersionCode() {
        return _version_code;
    }

    public String getMD5() {
        return _md5;
    }

    public String getDownloadUrl() {
        return _download_url;
    }
}
