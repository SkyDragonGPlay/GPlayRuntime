package com.skydragon.gplay.runtime.utils;

import android.os.Environment;

import com.skydragon.gplay.runtime.RuntimeStub;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class LogcatHelper {

    private static LogcatHelper INSTANCE = null;
    private static String PATH_LOGCAT;
    private LogDumper mLogDumper = null;
    private String mPackageName;
    private String mVersionName;
    private static final String TAG = "gplay_divide_res";

    /**
     * 初始化目录
     */
    public void init() {
        RuntimeStub runtimeStub = RuntimeStub.getInstance();
        mPackageName = runtimeStub.getRunningGameInfo().mPackageName;
        mVersionName = runtimeStub.getRunningGameInfo().mGameVersion;

        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {// 优先保存到SD卡中
            PATH_LOGCAT = Environment.getExternalStorageDirectory()
                    .getAbsolutePath() + File.separator + "gplay_off_line_auto_split";
        } else {// 如果SD卡不存在，就保存到本应用的目录下
            PATH_LOGCAT = runtimeStub.getContext().getFilesDir().getAbsolutePath()
                    + File.separator + "gplay_off_line_auto_split";
        }
    }

    public static LogcatHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LogcatHelper();
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        if (INSTANCE != null) {
            INSTANCE.stop();
        }
    }

    private LogcatHelper() {
        init();
    }

    public void start() {
        if (mLogDumper == null) {
            mLogDumper = new LogDumper();
            mLogDumper.start();
        }
    }

    private void stop() {
        if (mLogDumper != null) {
            mLogDumper.stopLogs();
            mLogDumper = null;
        }
    }

    private void deleteFile(File file) {
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
            }
        }
    }

    private void deleteTagFile(String filepath) {
        File mFile = new File(filepath);
        File[] files = mFile.listFiles();

        for (File file : files) {
            if (checkTagFile(file.getName())) {
                deleteFile(file);
            }
        }
    }

    private boolean checkTagFile(String fileName) {
        boolean isTagFile = false;
        String filePackageName = fileName.substring(0, fileName.indexOf("_"));
        if (filePackageName.equals(mPackageName)) {
            isTagFile = true;
        }
        return isTagFile;
    }

    private class LogDumper extends Thread {

        private Process logcatProcess;
        private BufferedReader mReader = null;
        private boolean mRunning = true;
        String cmd = null;
        private FileOutputStream out = null;

        public LogDumper() {
            cmd = "logcat -s " + TAG;//打印标签过滤信息
        }

        public void stopLogs() {
            mRunning = false;
        }

        @Override
        public void run() {
            try {
                if (mPackageName != null) {
                    deleteTagFile(PATH_LOGCAT);
                    File file = new File(PATH_LOGCAT);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    out = new FileOutputStream(new File(PATH_LOGCAT, mPackageName + "_" + mVersionName + ".log"));
                }
                if (out == null)
                    return;

                logcatProcess = Runtime.getRuntime().exec(cmd);
                mReader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()), 1024);
                String line;

                while (mRunning && mReader != null) {
                    line = mReader.readLine();
                    if (line == null || line.isEmpty()) {
                        try {
                            Thread.sleep(50);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        continue;
                    }

                    out.write((line + "\n").getBytes());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (logcatProcess != null) {
                    logcatProcess.destroy();
                    logcatProcess = null;
                }

                TryCloseUtils.tryClose(mReader);
                mReader = null;

                TryCloseUtils.tryClose(out);
                out = null;
            }
        }
    }
}
