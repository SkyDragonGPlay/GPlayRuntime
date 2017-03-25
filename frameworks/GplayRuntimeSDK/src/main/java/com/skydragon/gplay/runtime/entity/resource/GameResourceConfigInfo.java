package com.skydragon.gplay.runtime.entity.resource;

import android.text.TextUtils;

import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

public final class GameResourceConfigInfo {
    private static final String TAG = "GameResourceConfigInfo";
    private int versionCode;

    /**
     * 此集合中分组信息都是唯一的
     */
    private TreeSet<SceneInfo> _allSceneInfo = new TreeSet<>();
    private HashMap<String, GroupInfo> _groupInfoMap = new HashMap<>();
    private List<String> _allDeleteFile = new ArrayList<>();
    private HashSet<String> _allGroupHashSet = new HashSet<>();
    private HashMap<String, String> _resMD5Map = new HashMap<>();

    public static GameResourceConfigInfo fromJson(JSONObject jsonObject) {
        GameResourceConfigInfo configInfo = new GameResourceConfigInfo();
        configInfo.versionCode = Integer.parseInt(jsonObject.optString("versioncode"));

        JSONArray jsonArray = jsonObject.optJSONArray("groups");
        if(null != jsonArray) {
            for (int i = 0; i < jsonArray.length(); i++) {
                GroupInfo group = GroupInfo.fromJson(jsonArray.optJSONObject(i), configInfo.versionCode);
                configInfo.addGroup(group);
            }
        }

        jsonArray = jsonObject.optJSONArray("scenes");
        if(null != jsonArray) {
            for (int i = 0; i < jsonArray.length(); i++) {
                SceneInfo sceneInfo = SceneInfo.fromJson(jsonArray.optJSONObject(i));
                configInfo.addScene(sceneInfo);
            }
        }

        jsonArray = jsonObject.optJSONArray("deletes");
        if(null != jsonArray) {
            List<String> deleteFiles = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    deleteFiles.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            configInfo.setDeleteFiles(deleteFiles);
        }

        JSONObject jsonObj = jsonObject.optJSONObject("res_md5");
        HashMap<String, String> resMD5s = new HashMap<>();
        if(null != jsonObj) {
            String key;
            String md5;
            for (Iterator<String> ite = jsonObj.keys(); ite.hasNext() ; ) {
                key = ite.next();
                md5 = jsonObj.optString(key);
                if(key != null && md5 != null) {
                    resMD5s.put(key, md5);
                }
            }
        }
        configInfo.setResMD5s(resMD5s);
        return configInfo;
    }

    public JSONArray allSceneInfoToJson() {
        JSONArray jsonArray = new JSONArray();
        for( SceneInfo sceneInfo : _allSceneInfo ) {
            jsonArray.put(sceneInfo.toJson());
        }
        return jsonArray;
    }

    public List<String> getAllDeleteFile() {
        return _allDeleteFile;
    }

    public SceneInfo getSceneInfoByName(String name) {
        if (TextUtils.isEmpty(name))
            return null;

        for(SceneInfo sceneInfo : _allSceneInfo) {
            if(sceneInfo.getName().equals(name)) return sceneInfo;
        }

        return null;
    }

    public List<SceneInfo> getAllSceneInfos() {
        return new ArrayList<>(_allSceneInfo);
    }

    public void addScene(SceneInfo info) {
        _allSceneInfo.add(info);
    }

    public SceneInfo getSceneByName(String name) {
        for(SceneInfo scene : _allSceneInfo) {
            if(scene.getName().equals(name)) return scene;
        }
        return null;
    }

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("versioncode",versionCode);
            JSONArray jsonGroupInfos = allGroupsToJson();
            JSONArray jsonSceneInfos = allSceneInfoToJson();
            jsonObject.put("groups",jsonGroupInfos);
            jsonObject.put("scenes",jsonSceneInfos);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public JSONArray allGroupsToJson() {
        JSONArray jsonArray = new JSONArray();
        Collection<GroupInfo> set = _groupInfoMap.values();
        for(GroupInfo info : set) {
            jsonArray.put(info.toJson());
        }
        return jsonArray;
    }

    public void addGroup(GroupInfo groupInfo) {
        _groupInfoMap.put(groupInfo.getName(), groupInfo);

        JSONArray resArray = groupInfo.getResourcesArray();
        for(int i = 0; i < resArray.length(); i++){
            try {
                String resName = RuntimeEnvironment.pathCurrentGameResourceDir + resArray.getString(i);
                _allGroupHashSet.add(resName);
            } catch (JSONException e) {
                LogWrapper.d(TAG, "addGroup resArray parse exception!");
            }
        }
    }

    public boolean configContainResource(String resName) {
        return resName != null && _allGroupHashSet.contains(resName);
    }

    public GroupInfo getGroupInfoByName(String name) {
        return _groupInfoMap.get(name);
    }

    public int getVersionCode() {
        return versionCode;
    }

    public void setDeleteFiles(List<String> deleteFiles) {
        this._allDeleteFile = deleteFiles;
    }

    public HashMap<String, String> getResMD5Map() {
        return _resMD5Map;
    }

    public void setResMD5s(HashMap<String,String> resMD5s) {
        this._resMD5Map.putAll(resMD5s);
    }

    @Override
    public String toString() {
        return toJsonObject().toString();
    }
}
