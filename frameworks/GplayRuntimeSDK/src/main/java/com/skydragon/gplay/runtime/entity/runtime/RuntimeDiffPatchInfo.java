package com.skydragon.gplay.runtime.entity.runtime;

import org.json.JSONObject;


public final class RuntimeDiffPatchInfo {
    private String version;
    private int versionCode;
    private String downloadUrl;
    private String md5;
    private String so_md5;

    public static RuntimeDiffPatchInfo fromJson(JSONObject jsonObject) {
        RuntimeDiffPatchInfo diffPatchInfo = new RuntimeDiffPatchInfo();
        diffPatchInfo.version = jsonObject.optString("version_name");
        diffPatchInfo.versionCode = jsonObject.optInt("version_code");
        diffPatchInfo.downloadUrl = jsonObject.optString("url");
        diffPatchInfo.md5 = jsonObject.optString("md5");
        diffPatchInfo.so_md5 = jsonObject.optString("so_md5");
        return diffPatchInfo;
    }

    public String getVersion() {
        return version;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getMd5() {
        return md5;
    }

    public String getSoMd5() {
        return so_md5;
    }
}
