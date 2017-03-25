package com.skydragon.gplay.runtime.entity.runtime;

import org.json.JSONObject;


public final class RuntimeShareLibraryFileInfo {
    private String name;
    private String md5;

    public static RuntimeShareLibraryFileInfo fromJson(JSONObject jsonObject) {
        RuntimeShareLibraryFileInfo shareLibraryFileInfo = new RuntimeShareLibraryFileInfo();
        shareLibraryFileInfo.name = jsonObject.optString("name");
        shareLibraryFileInfo.md5 = jsonObject.optString("md5");
        return shareLibraryFileInfo;
    }

    public String getName() {
        return name;
    }

    public String getMd5() {
        return md5;
    }
}
