package com.skydragon.gplay.runtime.entity.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeShareLibraryArchInfo {
    private String mZipUrl;
    private String mZipMd5;
    private String mExtension;

    private List<RuntimeShareLibraryFileInfo> listSoFiles;

    public RuntimeShareLibraryArchInfo() {
        listSoFiles = new ArrayList<>();
    }

    public static RuntimeShareLibraryArchInfo fromJson(JSONObject jsonObject) {
        RuntimeShareLibraryArchInfo archInfo = new RuntimeShareLibraryArchInfo();
        archInfo.mZipUrl = jsonObject.optString("url");
        archInfo.mZipMd5 = jsonObject.optString("md5");
        archInfo.mExtension = jsonObject.optString("extension", "gip");
        JSONArray jsonArray = jsonObject.optJSONArray("sofiles");
        for (int i = 0; i < jsonArray.length(); i++) {
            RuntimeShareLibraryFileInfo fileInfo = RuntimeShareLibraryFileInfo.fromJson(jsonArray.optJSONObject(i));
            archInfo.listSoFiles.add(fileInfo);
        }
        return archInfo;
    }

    public String getZipUrl() {
        return mZipUrl;
    }

    public List<RuntimeShareLibraryFileInfo> getListSoFiles() {
        return new ArrayList<>(listSoFiles);
    }

    public String getZipMd5() {
        return mZipMd5;
    }

    public String getExtension() {
        return mExtension;
    }
}
