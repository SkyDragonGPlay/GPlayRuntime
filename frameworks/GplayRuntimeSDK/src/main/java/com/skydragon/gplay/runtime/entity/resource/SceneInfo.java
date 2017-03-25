package com.skydragon.gplay.runtime.entity.resource;

import com.skydragon.gplay.runtime.RuntimeLauncher;
import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SceneInfo implements Comparable{
    private static final String TAG = "SceneInfo";
    private String name;
    private List<String> listGroupInfo;
    private List<String> listPatchInfo;
    private int order;
    public static final int DEFAULT_SCENE_VERSION = -1;
    /**
     * 在 GPlayTools v1.0.0 之后， 生成的配置文件中的场景具备版本号。
     * 先前版本无场景版本号，设置默认值为 DEFAULT_SCENE_VERSION
     * */
    private int version = DEFAULT_SCENE_VERSION;

    public SceneInfo() {
        listGroupInfo = new ArrayList<>();
        listPatchInfo = new ArrayList<>();
    }

    public static SceneInfo fromJson(JSONObject jsonObject) {
        SceneInfo sceneInfo = new SceneInfo();
        sceneInfo.name = jsonObject.optString("name");
        JSONArray groupsArray = jsonObject.optJSONArray("groups");
        for(int i = 0; i < groupsArray.length(); i++) {
            String groupName = groupsArray.optString(i);
            sceneInfo.listGroupInfo.add(groupName);
        }

        JSONArray patchArray = jsonObject.optJSONArray("patch");
        if(null != patchArray) {
            for (int i = 0; i < patchArray.length(); i++) {
                String patchName = patchArray.optString(i);
                sceneInfo.listPatchInfo.add(patchName);
            }
        }
        sceneInfo.order = jsonObject.optInt("order");
        sceneInfo.version = jsonObject.optInt("version", DEFAULT_SCENE_VERSION);
        return sceneInfo;
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("name", name);
            JSONArray jsonArray = new JSONArray();
            for(String groupName : listGroupInfo) {
                jsonArray.put(groupName);
            }
            jsonObject.put("groups", jsonArray);
            jsonObject.put("version", version);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    public List<String> getAllGroupInfos() {
        return new ArrayList<>(listGroupInfo);
    }

    public List<String> getAllPatchInfos() {
        return new ArrayList<>(listPatchInfo);
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }

    public boolean isCompletedScenes() {
        for(String groupName : listGroupInfo) {
            GameResourceConfigInfo resourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
            GroupInfo resGroup = resourceConfigInfo.getGroupInfoByName(groupName);
            if((resGroup == null) || ! resGroup.isCompletedGroup()) {
                if(resGroup != null) {
                    LogWrapper.d(TAG, "Group (" + resGroup.getName() + ") is not completed !");
                } else {
                    LogWrapper.d(TAG, "Group doesn't find in resource config !");
                }
                return false;
            }
        }
        return true;
    }

    public int getOrder() {
        return order;
    }

    @Override
    public int compareTo(Object another) {
        SceneInfo otherSceneInfo = (SceneInfo)another;
        int c = order - otherSceneInfo.order;
        if( c == 0 ) {
            return this.name.compareTo(otherSceneInfo.name);
        }
        return c;
    }
}
