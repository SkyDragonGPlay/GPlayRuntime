package com.skydragon.gplay.runtime.exception;

import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.RuntimeStub;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.TelephoneUtil;
import com.skydragon.gplay.runtime.utils.TryCloseUtils;
import com.skydragon.gplay.runtime.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AppUncaughtExceptionHandler implements UncaughtExceptionHandler {
    private static final String TAG = "AppUncaughtExceptionHandler";

    private Context mContext;
    private static AppUncaughtExceptionHandler mInstance = null;
    private UncaughtExceptionHandler mDefaultHandler;
    private final DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
    private String mUID = null;

    public static AppUncaughtExceptionHandler getInstance() {
        if (mInstance == null) {
            mInstance = new AppUncaughtExceptionHandler();
        }

        return mInstance;
    }

    public void init(Context context) {
        mContext = context;
        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);
        mUID = TelephoneUtil.getDeviceID(mContext);
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        handleException(ex);

        if (Utils.isStandardGameProcess(mContext)) {
            // cocos 造成的异常，如果是标准的游戏进程（即普通模式下，以GPLAY_PROCESS作为结尾进程名的进程）
            // 由SDK全权接管，为了不让宿主弹出 FC 界面，直接关闭进程
            Utils.killGameProcess();
        } else if (mDefaultHandler != null) {
            // 非 cocos 造成的异常，交回给宿主处理
            LogWrapper.d(TAG, "Gplay can't handle this exception, mDefaultHandler may handle it.");
            if (Looper.getMainLooper().getThread() == thread || Utils.isInGameThread()) {
                mDefaultHandler.uncaughtException(thread, ex);
            }
        }
        // cocos 造成的，但又不是标准模式（大渠道），不杀进程，也不交给宿主，我们自己根据后台日志尽快修复问题
    }

    private boolean handleException(Throwable ex) {
        if (ex == null) {
            ex.printStackTrace();
            return false;
        }

        String result = saveCrashInfo2File(ex);
        return result != null;
    }

    private String saveCrashInfo2File(Throwable ex) {
        Writer writer = new StringWriter();
        if (writer == null)
            return null;

        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        LogWrapper.e(TAG, result);

        if (!result.toLowerCase().contains("cocos")) {
            // only save crash log about coco
            return null;
        }

        FileOutputStream fileOutputStream = null;
        try {
            String time = formatter.format(new Date());
            String fileName = mUID + "-" + time + ".txt";
            String path = FileConstants.getLogDir() + RuntimeConstants.JAVA_CRASHES_DIR + File.separator + RuntimeStub.getInstance().getVersion() + File.separator;

            if (!TextUtils.isEmpty(RuntimeStub.getInstance().getChannelID())) {
                path += RuntimeStub.getInstance().getChannelID() + File.separator;
            }

            if (!TextUtils.isEmpty(RuntimeEnvironment.versionCurrentGameRuntime)) {
                path += RuntimeEnvironment.versionCurrentGameRuntime + File.separator;
            }

            FileUtils.ensureDirExists(path);
            fileOutputStream = new FileOutputStream(path + fileName);
            fileOutputStream.write(result.getBytes());
            return result;
        } catch (Exception e) {
            LogWrapper.e(TAG, "saveCrashInfo2File e[" + e + "]");
        }
        finally {
            TryCloseUtils.tryClose(fileOutputStream);
        }

        return null;
    }
}
