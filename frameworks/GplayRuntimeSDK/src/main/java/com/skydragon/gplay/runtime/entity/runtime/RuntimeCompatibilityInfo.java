package com.skydragon.gplay.runtime.entity.runtime;

import com.skydragon.gplay.runtime.utils.LogWrapper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeCompatibilityInfo {
    static private String TAG = "RuntimeCompatibilityInfo";

    private List<RuntimeEngineSupportInfo> mListInfo;
    private RuntimeDiffPatchInfo mRuntimeDiffPatchInfo;

    public RuntimeCompatibilityInfo() {
        mListInfo = new ArrayList<>();
    }

    public static RuntimeCompatibilityInfo fromJson(JSONObject jsonObject) {
        LogWrapper.v(TAG, "RuntimeCompatibilityInfo fromJson  " + jsonObject);
        if (jsonObject == null)
            return null;

        RuntimeCompatibilityInfo compatibilityInfo = new RuntimeCompatibilityInfo();

        JSONArray jsonArray = jsonObject.optJSONArray("engines");
        if (null == jsonArray) {
            LogWrapper.e(TAG, "Failed to init compatibility info!");
            return compatibilityInfo;
        }

        for (int i = 0; i < jsonArray.length(); i++) {
            compatibilityInfo.mListInfo.add(RuntimeEngineSupportInfo.from(jsonArray.optJSONObject(i)));
        }

        compatibilityInfo.mRuntimeDiffPatchInfo = RuntimeDiffPatchInfo.fromJson(jsonObject.optJSONObject("diffpatch"));
        return compatibilityInfo;
    }

    public RuntimeEngineSupportInfo getRuntimeCompatibility(int n) {
        LogWrapper.v(TAG, "getRuntimeCompatibility  size " + mListInfo.size() + "  ,  " + n);
        if (mListInfo.size() > n)
            return mListInfo.get(n);
        return null;
    }

    public RuntimeEngineSupportInfo getRuntimeCompatibility(String engineType, String engineVersion) {
        for (RuntimeEngineSupportInfo supportInfo : mListInfo) {
            String engine = supportInfo.getEngine();
            String version = supportInfo.getEngineVersion();
            if (engine.equals(engineType) && version.equals(engineVersion)) {
                return supportInfo;
            }
        }
        LogWrapper.e(TAG, engineType + " v" + engineVersion + " is not supported!");
        return null;
    }

    public RuntimeDiffPatchInfo getRuntimeDiffPatchInfo() {
        return mRuntimeDiffPatchInfo;
    }

}
