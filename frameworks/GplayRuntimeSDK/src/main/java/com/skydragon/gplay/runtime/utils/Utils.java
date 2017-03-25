

package com.skydragon.gplay.runtime.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.yolanda.nohttp.tools.NetUtil;

import java.io.File;

public final class Utils {
    public static final String TAG = "Utils";

    // APN type
    public static final int TYPE_MOBILE = 0;
    public static final int TYPE_WIFI = 1;
    public static final int NO_NETWORK = -1;

    private static Context sContext = null;

    public static void setCurrentContext(Context ctx) {
        sContext = ctx;
    }

    public static Context getCurrentContext() {
        return sContext;
    }

    private static int s_CurrAPNType = TYPE_MOBILE;
    private static int s_currNetType = ConnectivityManager.TYPE_MOBILE;

    public static void updateCurrAPNType() {
        if (sContext != null) {
            s_CurrAPNType = getAPNType(sContext);
            NetUtil.setCurrNetworkType(s_CurrAPNType);
        } else {
            LogWrapper.e(TAG, "Context is null, please make sure Utils.setCurrentContext was invoked!");
        }
    }

    public static int getCurrAPNType() {
        return s_CurrAPNType;
    }

    public static int getNetworkType() {
        return s_currNetType;
    }

    /**
     * 获取当前的网络状态
     *
     * @return Utils.TYPE_MOBILE, Utils.TYPE_WIFI, Utils.NO_NETWORK
     */
    private static int getAPNType(Context context) {
        int netType = NO_NETWORK;
        if (null == context) {
            return netType;
        }

        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

        if (networkInfo == null) {
            return netType;
        }

        s_currNetType = networkInfo.getType();
        if (s_currNetType == ConnectivityManager.TYPE_MOBILE /*移动网络*/
                || s_currNetType == ConnectivityManager.TYPE_WIMAX /*新兴的一种蜂窝网络*/
                || s_currNetType == ConnectivityManager.TYPE_MOBILE_DUN /*拨号网络,常用于运营商无线热点*/) {
            netType = TYPE_MOBILE;
        } else if (s_currNetType == ConnectivityManager.TYPE_WIFI) {
            netType = TYPE_WIFI;
        }

        return netType;
    }

    /**
     * 获取当前连接的网络类型，native层调用
     */
    public static int getAPNType() {
        return getCurrAPNType();
    }

    public static void showToast(Context ctx, String msg) {
        showToast(ctx, msg, Toast.LENGTH_SHORT);
    }

    public static void showToast(Context ctx, String msg, int duration) {
        if (null == ctx) {
            return;
        }
        Toast.makeText(ctx, msg, duration).show();
    }

    public static boolean isStandardGameProcess(Context context) {
        if (context != null) {
            String processName = getCurrentProcessName(context);
            return processName != null && processName.toLowerCase().endsWith("gplay");
        }
        return false;
    }

    public static void killGameProcess() {
        //延迟50毫秒杀死进程
        ThreadUtils.runAsyncThread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }

    public static boolean isInGameThread() {
        boolean ret = false;
        String threadName = Thread.currentThread().getName();
        if (RuntimeEnvironment.engineType != null) {
            if (RuntimeEnvironment.engineType.contains(GameConfigInfo.UNITY)) {
                ret = threadName.contains("UnityMain");
            } else if (RuntimeEnvironment.engineType.contains("cocos")) {
                ret = threadName.contains("GL");
            }
        }

        if (!ret) {
            LogWrapper.e(TAG, "Oops, isInGameThread failed, it wasn't invoked from game thread!");
        }
        return ret;
    }

    public static String getPhoneArch(){
        String cpuArch = "armeabi";
        String cpuAbi = TelephoneUtil.getCPUABI();
        if (cpuAbi != null) {
            String[] CPUAbis = cpuAbi.split(",");
            if(CPUAbis.length > 0)
                cpuArch = CPUAbis[0];
        }

        return cpuArch;
    }

    public static String formatSlash(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        String ret = path;

        while (ret.contains("\\")) {
            ret = ret.replace("\\", File.separator);
        }

        while (ret.contains("//")) {
            ret = ret.replace("//", File.separator);
        }
        return ret;
    }

    public static String getCurrentProcessName(Context context) {
        int pid = android.os.Process.myPid();
        ActivityManager mActivityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : mActivityManager.getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                return appProcess.processName;
            }
        }
        return null;
    }

    /**
     * 通过gameKey得到包名
     *
     */
    public static String getPackageNameByGameKey(String gameKey) {
        String packageName = null;
        File dirGames = new File(FileConstants.getGamesDir());
        String[] names = dirGames.list();
        if (names != null) {
            for (String name : names) {
                File gameKeyFile = new File(FileConstants.getGameKeyFilePath(name, gameKey));
                if (gameKeyFile.exists()) {
                    packageName = name;
                    break;
                }
            }
        }

        return packageName;
    }

    public static int getActivityOrientation(String orientation) {
        if (TextUtils.isEmpty(orientation)) {
            return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
        int orientation_int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_LANDSCAPE))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        else if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_PORTRAIT))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        else if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_REVERSE_LANDSCAPE))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        else if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_REVERSE_PORTRAIT))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        else if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_SENSOR_LANDSCAPE))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        else if (orientation.equalsIgnoreCase(RuntimeConstants.ORIENTATION_SENSOR_PORTRAIT))
            orientation_int = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        return orientation_int;
    }

    public static boolean isLandscape(String orientation) {
        return isLandscape(getActivityOrientation(orientation));
    }

    public static boolean isLandscape(int orientation) {
        return orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                || orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
    }

    public static Point getScreenSize(Activity activity) {
        WindowManager w = activity.getWindowManager();
        Display d = w.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        d.getMetrics(metrics);
        // since SDK_INT = 1;
        int widthPixels = metrics.widthPixels;
        int heightPixels = metrics.heightPixels;
        // includes window decorations (status bar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17) {
            try {
                widthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(d);
                heightPixels = (Integer) Display.class.getMethod("getRawHeight").invoke(d);
            } catch (Exception ignored) {
            }
        }
        // includes window decorations (status bar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(d, realSize);
                widthPixels = realSize.x;
                heightPixels = realSize.y;
            } catch (Exception ignored) {
            }
        }
        return new Point(widthPixels, heightPixels);
    }

    public static void ensureDirExists(String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            if (!dir.mkdirs())
                Log.e("Gplay", "Create (" + dirPath + ") failed!");
        }
    }

    public static boolean isEmpty(String s) {
        return (null == s || s.trim().isEmpty());
    }

    public static String parseCryptData(String data) {
        StringBuilder sb = new StringBuilder(data.substring(10, 14));
        sb.append(data.substring(24, 28));
        sb.append(data.substring(38, 42));
        sb.append(data.substring(52, 56));
        return sb.toString();
    }
}
