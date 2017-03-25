package com.skydragon.gplay.runtime.entity.resource;

import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;

public final class GroupInfo {
    private static final String TAG = "GroupInfo";
    private String name;
    private String path;
    private String md5;
    private int size;
    private int version;
    private JSONArray resourceArray;
    private boolean isUpdated;
    private boolean isChecked;

    public static GroupInfo fromJson(JSONObject jsonObject, int version) {
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.version = version;
        groupInfo.name = jsonObject.optString("name");
        groupInfo.md5 = jsonObject.optString("md5");
        groupInfo.path = jsonObject.optString("path");
        groupInfo.size = jsonObject.optInt("size");
        groupInfo.resourceArray = jsonObject.optJSONArray("res");
        return groupInfo;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name",name);
            jsonObject.put("path",path);
            jsonObject.put("md5",md5);
            jsonObject.put("size", size);
            jsonObject.put("res", resourceArray);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public boolean equals(Object o) {
        if( o instanceof  GroupInfo) {
            GroupInfo groupInfo = (GroupInfo)o;
            return groupInfo.name.equals(name);
        }
        return false;
    }

    public boolean isCompletedGroup(){
        if(isUpdated)
            return true;

        if(resourceArray == null){
            LogWrapper.e(TAG, "Runtime Group res not be right!");
            return false;
        }

        String rootPath = RuntimeEnvironment.pathCurrentGameResourceDir;
        try {
            for (int i = 0; i < resourceArray.length(); i ++){
                String resFileStr = resourceArray.getString(i);
                String groupFile = rootPath + resFileStr;
                File f = new File(groupFile);
                if(!f.exists()){
                    LogWrapper.d(TAG, "Group resource (" + f.getAbsolutePath() + ") not exist !");
                    return false;
                }
            }
        } catch (JSONException e) {
            LogWrapper.e(TAG, "Group res dir not be right!");
            return false;
        }
        isUpdated = true;
        return true;
    }

    public boolean isCompletedGroupCheckedByMD5(HashMap<String, String> resMD5s){
        if(resMD5s == null || resMD5s.isEmpty()) {
            isChecked = false;
            return false;
        }

        if(isChecked)
            return true;

        if(resourceArray == null){
            LogWrapper.e(TAG, "Runtime Group res not be right!");
            return false;
        }

        String rootPath = RuntimeEnvironment.pathCurrentGameResourceDir;
        for (int i = 0; i < resourceArray.length(); i ++){
            String groupFile;
            String groupMD5;
            try {
                String resFileStr = resourceArray.getString(i);
                groupFile = rootPath + resFileStr;
                groupMD5 = resMD5s.get(resFileStr);
            } catch (JSONException e) {
                LogWrapper.e(TAG, "Group res dir not be right!");
                return false;
            }

            File f = new File(groupFile);
            if(!f.exists()) {
                return false;
            }

            if(groupMD5 == null || FileUtils.isFileModifiedByCompareMD5(groupFile, groupMD5)) {
                return false;
            }
        }
        isChecked = true;
        return true;
    }

    public JSONArray getResourcesArray() {
        return resourceArray;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getMd5() {
        return md5;
    }

    public int getSize() {
        return size;
    }
}
