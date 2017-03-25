package com.skydragon.gplay.runtime.utils;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

public final class TelephoneUtil {
    private static final String TAG = "TelephoneUtil";

    private static String IMSI;
    /**
     * 取得IMSI号
     */
    public static String getIMSI(Context ctx) {
        if (IMSI != null) return IMSI;

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Activity.TELEPHONY_SERVICE);
        IMSI = tm.getSubscriberId();
        return IMSI;
    }

    /**
     * 获取机器名称 如 milestone
     *
     */
    public static String getMachineName() {
        return Build.MODEL;
    }

    /**
     * 获取固件版本号
     */
    public static int getFirmWareVersionCode() {
        return Build.VERSION.SDK_INT;
    }
    
    /**
     * 获取mac
     */
    public static String getLocalMacAddress( Context ctx ) {  
        WifiManager wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);  
        WifiInfo info = wifi.getConnectionInfo();  
        return info.getMacAddress();  
    }

    private static String CPU_ABI;
    public static String getCPUABI() {
        if (CPU_ABI != null) return CPU_ABI;

        String abi = Build.CPU_ABI;
        if (abi == null || abi.trim().length() == 0) {
            Log.e(TAG, "getCPUABI failed");
            return null;
        }

        // 检视是否有第二类型，1.6没有这个字段
        try {
            String cpuAbi2 = Build.class.getField("CPU_ABI2").get(null).toString();
            cpuAbi2 = (cpuAbi2 == null || cpuAbi2.trim().length() == 0) ? null : cpuAbi2;
            if (cpuAbi2 != null) {
                abi = abi + "," + cpuAbi2;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        CPU_ABI = abi;
        return abi;
    }

    /*   
       * 唯一的设备ID：   
       * GSM手机的 IMEI 和 CDMA手机的 MEID.    
       * 如果返回的 getDeviceId() 返回值是非法的，那么使用 android.os.Build.SERIAL 代替.
       */
    private static String sDeviceId = null;
    public static String getDeviceID(Context ctx) {
        if (null != sDeviceId) return sDeviceId;

        TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
        sDeviceId = tm.getDeviceId();

        LogWrapper.d(TAG, "IMEI/MEID: " + sDeviceId);

        boolean invalidDeviceID = false;
        if (sDeviceId == null
                || sDeviceId.length() == 0
                || sDeviceId.equals("000000000000000")
                || sDeviceId.equals("0")
                || sDeviceId.contains("*")) {
            LogWrapper.d(TAG, "Device id invalid.");
            invalidDeviceID = true;
        }

        // Is there no IMEI or MEID?
        // Is this at least Android 2.3+?
        // Then let's get the serial.
        if (invalidDeviceID && Build.VERSION.SDK_INT >= 9) {
            LogWrapper.d(TAG, "TRYING TO GET SERIAL OF 2.3+ DEVICE...");

            sDeviceId = Build.SERIAL;

            LogWrapper.d(TAG, "SERIAL: deviceID: [" + sDeviceId + "]");

            if (sDeviceId == null
                    || sDeviceId.length() == 0
                    || sDeviceId.equals("000000000000000")
                    || sDeviceId.equals("0")
                    || sDeviceId.contains("*")) {
                LogWrapper.d(TAG, "SERIAL invalid.");
            }
        }

        LogWrapper.d(TAG, "deviceID: " + sDeviceId);

        return sDeviceId;
    }

    public static String getAppVersionName(Context ctx) {
        String strVerName = null;
        try {
            String packageName = ctx.getPackageName();
            strVerName = ctx.getPackageManager().getPackageInfo(packageName, 0).versionName;
        } catch (Exception e) {
            LogWrapper.e(TAG, e);
        }
        return strVerName;
    }

    public static String getDeviceModel() {
        String deviceName = Build.MANUFACTURER + "-" + Build.MODEL;
        deviceName = deviceName.replace(" ", "");

        return deviceName;
    }

    public static String getOsVersion() {
        return Build.VERSION.RELEASE;
    }
}
