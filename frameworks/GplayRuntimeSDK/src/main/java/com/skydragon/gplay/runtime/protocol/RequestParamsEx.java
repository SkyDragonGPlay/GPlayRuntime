package com.skydragon.gplay.runtime.protocol;

import android.content.Context;

import com.skydragon.gplay.runtime.RuntimeStub;
import com.skydragon.gplay.runtime.utils.TelephoneUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestParamsEx {

    private static final ConcurrentHashMap<String, String> s_baseUrlParams = new ConcurrentHashMap<>();

    public RequestParamsEx() {
        super();
        addExtraParams();
    }

    public Map<String, String> getParamsExts() {
        return new HashMap<>(s_baseUrlParams);
    }

    private void addExtraParams() {
        try {
            Context ctx = RuntimeStub.getInstance().getContext();

            if (s_baseUrlParams.isEmpty()) {
                s_baseUrlParams.put("appv", RuntimeStub.VERSION);

                String channelID = RuntimeStub.getInstance().getChannelID();
                if (channelID != null) s_baseUrlParams.put("chn", channelID);

                String deviceID = TelephoneUtil.getDeviceID(ctx);
                if (deviceID != null) {
                    s_baseUrlParams.put("dvid", deviceID);
                    s_baseUrlParams.put("imei", deviceID);
                }

                String imsi = TelephoneUtil.getIMSI(ctx);
                if (imsi != null) s_baseUrlParams.put("imsi", imsi);

                s_baseUrlParams.put("dt", "Android");
                s_baseUrlParams.put("os", TelephoneUtil.getMachineName());
                s_baseUrlParams.put("osv", String.valueOf(TelephoneUtil.getFirmWareVersionCode()));

                String cpu_api = TelephoneUtil.getCPUABI();
                if (cpu_api != null) s_baseUrlParams.put("arch", cpu_api);

                s_baseUrlParams.put("mac", TelephoneUtil.getLocalMacAddress(ctx));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
