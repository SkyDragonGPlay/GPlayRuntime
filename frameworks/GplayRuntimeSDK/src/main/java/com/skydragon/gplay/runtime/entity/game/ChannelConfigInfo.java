package com.skydragon.gplay.runtime.entity.game;

import org.json.JSONObject;


public final class ChannelConfigInfo {

    private JSONObject mJsonObject;

    public JSONObject getChannelConfigJson() {
        return mJsonObject;
    }

    public void setChannelConfigInfo(JSONObject jsonObject) {
        mJsonObject = jsonObject;
    }

    @Override
    public String toString() {
        if (null != mJsonObject) {
            return mJsonObject.toString();
        }
        return "";
    }
}
