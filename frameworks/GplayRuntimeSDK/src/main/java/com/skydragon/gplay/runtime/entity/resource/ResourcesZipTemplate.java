package com.skydragon.gplay.runtime.entity.resource;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ResourcesZipTemplate extends ResourceTemplate {
    private Map<String, String> _resourceBundle;
    private static final String FILE_BUNDLE = "files";
    private static final String FILE_NAME = "name";

    public ResourcesZipTemplate(JSONObject jsonObject) {
        super(jsonObject);
        _resourceBundle = new HashMap<>();
        JSONArray array = jsonObject.optJSONArray(FILE_BUNDLE);

        try {
            for (int i = 0; i < array.length(); i++) {
                JSONObject fileJson = (JSONObject) array.get(i);
                String fileName = fileJson.optString(FILE_NAME);
                String md5 = fileJson.optString(MD5);
                if (!TextUtils.isEmpty(fileName) && !TextUtils.isEmpty(md5)) {
                    _resourceBundle.put(fileName, md5);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static ResourcesZipTemplate fromJson(JSONObject jsonObject) {
        return new ResourcesZipTemplate(jsonObject);
    }

    public Map<String, String> getResources() {
        return _resourceBundle;
    }
}
