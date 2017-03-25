package com.skydragon.gplay.runtimeparams;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by zhangjunfei on 16/3/18.
 */
public class GenericsParams{
    private static final String TAG = "GenericsParams";

    public static <T> void from( Map<String, Object> args, T out) {
        if(null == out) {
            Log.e(TAG, "Out object can't be null!");
            return;
        }
        Field[] flds = out.getClass().getFields();
        for(Field fld : flds) {
            fld.setAccessible(true);
            Object obj = args.get(fld.getName());
            try {
                fld.set(out, obj);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public Map<String, Object> toMap() {
        Map<String,Object> args = new HashMap<String,Object>();
        Field[] flds = this.getClass().getFields();
        for(Field fld : flds) {
            Object obj = null;
            try {
                fld.setAccessible(true);
                obj = fld.get(this);
                args.put(fld.getName(), obj);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return args;
    }

    public String toJsonStr() {
        Map<String,Object> datas = toMap();
        JSONObject jsonObject = new JSONObject();
        Set<String> keys = datas.keySet();
        for(String key : keys) {
            try {
                jsonObject.put(key, datas.get(key));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonObject.toString();
    }

}
