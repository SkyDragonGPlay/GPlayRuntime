package com.skydragon.gplay.runtime.entity.runtime;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class RuntimeCommonResInfo {

    private String mVersionName;
    private int mVersionCode;
    private String mZipMD5;
    private String mUrl;
    private HashMap<String, String> mFileMap = new HashMap<>();

    public static RuntimeCommonResInfo fromJson(JSONObject jsonObject) {

        if (jsonObject != null) {
            RuntimeCommonResInfo runtimeCommonResInfo = new RuntimeCommonResInfo();
            runtimeCommonResInfo.mVersionName = jsonObject.optString("verison_name");
            runtimeCommonResInfo.mVersionCode = jsonObject.optInt("version_code");
            runtimeCommonResInfo.mZipMD5 = jsonObject.optString("md5");
            runtimeCommonResInfo.mUrl = jsonObject.optString("url");
            JSONArray jsonFilesArray = jsonObject.optJSONArray("files");

            try {
                if (jsonFilesArray != null && jsonFilesArray.length() > 0) {
                    for (int i = 0; i < jsonFilesArray.length(); i++) {
                        JSONObject fileJson = (JSONObject) jsonFilesArray.get(i);
                        String fileName = fileJson.optString("name");
                        String md5 = fileJson.optString("md5");
                        runtimeCommonResInfo.mFileMap.put(fileName, md5);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return runtimeCommonResInfo;
        }
        return null;
    }

    public String getVersionName() {
        return mVersionName;
    }


    public String getUrl() {
        return mUrl;
    }

    public String getZipMD5() {
        return mZipMD5;
    }

    public int getVersionCode() {
        return mVersionCode;
    }

    public HashMap<String, String> getFileMap() {
        return mFileMap;
    }
}
