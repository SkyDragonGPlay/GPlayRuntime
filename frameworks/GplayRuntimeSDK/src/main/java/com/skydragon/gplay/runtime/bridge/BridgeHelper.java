package com.skydragon.gplay.runtime.bridge;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;

import com.skydragon.gplay.runtime.RuntimeCore;
import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.RuntimeStub;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtimeparams.ParamsOfOnActivityResult;
import com.skydragon.gplay.runtimeparams.ParamsOfOnNewIntent;
import com.skydragon.gplay.service.IRuntimeCallback;
import com.skydragon.gplay.unitsdk.bridge.IUnitSDKBridge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BridgeHelper {

    private static final int RESULT_CODE_SUCCESS = 1;
    private static final int RESULT_CODE_PROGRESSING = 2;
    private static final int RESULT_CODE_FAILURE = 3;
    private static final String KEY_RESULT_CODE = "result_code";
    private static final String KEY_RESULT_MSG = "result_msg";
    private static final String KEY_ERROR_CODE = "error_code";
    private static final String KEY_SCENE_NAME = "scene_name";
    private static final String KEY_PERCENT = "percent";
    private static final String KEY_DOWNLOAD_SPEED = "download_speed";
    private static final String KEY_STAGE = "stage";
    private static final String TAG = "BridgeHelper";

    private static BridgeHelper sBridgeHelper;
    private IEngineRuntimeBridge mEngineBridge;
    private String mLastSceneName = "";

    private IUnitSDKBridge mUnitSDKBridge;

    private boolean mIsRunning;

    public static BridgeHelper getInstance() {
        if (null == sBridgeHelper) {
            sBridgeHelper = new BridgeHelper();
        }
        return sBridgeHelper;
    }

    public void setRuntimeBridge(IEngineRuntimeBridge bridge) {
        mEngineBridge = bridge;
    }

    public void setUnitSDKBridge(IUnitSDKBridge bridge) {
        mUnitSDKBridge = bridge;
    }

    public IEngineRuntimeBridge getEngineBridge() {
        return mEngineBridge;
    }

    public void init(Activity activity) {
        RuntimeCore.getInstance().init();
        mEngineBridge.init(activity);
    }

    public void initRuntimeJNI() {
        mEngineBridge.initRuntimeJNI();
    }

    public String getEngineRuntimeVersion() {
        return mEngineBridge.getRuntimeVersion();
    }

    public void onPause() {
        mEngineBridge.onPause();
        if (null != mUnitSDKBridge) {
            mUnitSDKBridge.invokeMethodSync("onPause", new HashMap<String, Object>());
        }
    }

    public void onResume() {
        mEngineBridge.onResume();
        if (null != mUnitSDKBridge) {
            mUnitSDKBridge.invokeMethodSync("onResume", new HashMap<String, Object>());
        }
    }

    public void onStop() {
        mEngineBridge.onStop();
        if (null != mUnitSDKBridge) {
            mUnitSDKBridge.invokeMethodSync("onStop", new HashMap<String, Object>());
        }
    }

    public void onDestroy() {
        mIsRunning = false;
        RuntimeCore.getInstance().destroy();
        mEngineBridge.onDestroy();
        if (null != mUnitSDKBridge) {
            mUnitSDKBridge.invokeMethodSync("onDestroy", new HashMap<String, Object>());
        }
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        mEngineBridge.onWindowFocusChanged(hasFocus);
    }

    public void onNewIntent(Intent intent) {
        mEngineBridge.onNewIntent(intent);
        if (null != mUnitSDKBridge) {
            ParamsOfOnNewIntent params = new ParamsOfOnNewIntent();
            params.intent = intent;
            mUnitSDKBridge.invokeMethodSync("onNewIntent", params.toMap());
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mEngineBridge.onActivityResult(requestCode, resultCode, data);
        if (null != mUnitSDKBridge) {
            ParamsOfOnActivityResult params = new ParamsOfOnActivityResult();
            params.requestCode = requestCode;
            params.resultCode = resultCode;
            params.data = data;
            mUnitSDKBridge.invokeMethodSync("onActivityResult", params.toMap());
        }
    }

    public void preloadResponse(final String responseJson, final boolean isDone, final long ext) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (!BridgeHelper.getInstance().isRunning()) {
                    LogWrapper.d(TAG, "Game have been shutdown, cancel to call nativePreloadResponse");
                    return;
                }

                Map<String, Object> args = new HashMap<>();
                args.put("responseJson", responseJson);
                args.put("isDone", isDone);
                args.put("ext", ext);
                mEngineBridge.invokeMethodSync("preloadResponse", args);
            }
        });
    }

    public void preloadResponse2(final boolean isDone, final boolean isFailed, final String errorCode, final float percent, final float downloadSpeed, final String groupName) {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                if (!mIsRunning) {
                    LogWrapper.d(TAG, "Game have been shutdown, cancel to call nativePreloadResponse");
                }

                Map<String, Object> args = new HashMap<>();
                args.put("isDone", isDone);
                args.put("isFailed", isFailed);
                args.put("errorCode", errorCode);
                args.put("percent", percent);
                args.put("downloadSpeed", downloadSpeed);
                args.put("groupName", groupName);
                mEngineBridge.invokeMethodSync("preloadResponse2", args);
            }
        });
    }

    public void downloadRemoteFileCallback(String responseJson, long ext) {
        Map<String, Object> args = new HashMap<>();
        args.put("responseJson", responseJson);
        args.put("ext", ext);
        mEngineBridge.invokeMethodSync("downloadRemoteFileCallback", args);
    }

    public void notifyOnLoadSharedLibrary() {
        mEngineBridge.notifyOnLoadSharedLibrary();
    }

    public void loadSharedLibrary(List<String> soPaths) {
        mEngineBridge.loadSharedLibrary(soPaths);
    }

    public ViewGroup getEngineLayout() {
        return mEngineBridge.getEngineLayout();
    }

    public void runOnGLThread(Runnable runnable) {
        mEngineBridge.runOnGLThread(runnable);
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void setRuntimeConfig(String jsonStr) {
        Map<String, Object> args = new HashMap<>();
        args.put("jsonStr", jsonStr);
        mEngineBridge.invokeMethodSync("setRuntimeConfig", args);
    }

    public void startGame() {
        LogWrapper.d(TAG, "startGame ...");
        mIsRunning = true;
        RuntimeCore.getInstance().setSilentDownloadEnabled(RuntimeEnvironment.boolSilentDownloadEnabled);
        mEngineBridge.startGame();
        RuntimeCore.getInstance().start();
    }

    public void setResCompletedListener(final RuntimeCore.IResourceCompletedListener listener) {
        RuntimeCore.getInstance().setResourceCompletedListener(listener);
    }

    public void setPreloadScenesCallback(final IRuntimeCallback callback) {
        RuntimeCore.getInstance().setPreloadScenesCallback(new RuntimeCore.IPreloadInfoListener() {

            @Override
            public void onSceneBeginLoading(String sceneName, long totallySize) {
                onResponseRuntimeCallback(callback, RESULT_CODE_PROGRESSING, null, null, sceneName, 0, 0, 1);
            }

            @Override
            public void onLoadingScenes(String sceneName, String groupName, boolean isCompleted, float downloadSpeed, float percent, int stage) {
                mLastSceneName = sceneName;
                if (percent == 100) {
                    if (isCompleted) {
                        onResponseRuntimeCallback(callback, RESULT_CODE_SUCCESS, null, null, mLastSceneName, 100.0f, 0f, stage);
                    }
                } else {
                    onResponseRuntimeCallback(callback, RESULT_CODE_PROGRESSING, null, null, sceneName, percent, downloadSpeed, stage);
                }
            }

            @Override
            public void onLoadingScenesFailure(int errorCode, String errorMsg, float percent, int stage) {
                onResponseRuntimeCallback(callback, RESULT_CODE_FAILURE, errorCode, errorMsg, mLastSceneName, percent, 0f, stage);
            }
        });
    }

    private void onResponseRuntimeCallback(IRuntimeCallback callback, Integer resultCode
            , Integer errorCode, String errorMsg,String sceneName, float percent, float speed, int stage) {
        Map<String, Object> params = new HashMap<>();
        // `1 下载成功` `2 下载中` `3 下载失败`
        params.put(KEY_RESULT_CODE, resultCode);
        // `-1 未知错误` `1 网络错误` `2 SD卡没有空间` `3 文件校验失败`
        if (errorCode != null) {
            params.put(KEY_ERROR_CODE, errorCode);
            if (TextUtils.isEmpty(errorMsg)) {
                params.put(KEY_RESULT_MSG, "Unknown error !");
            } else {
                params.put(KEY_RESULT_MSG, errorMsg);
            }
        }
        params.put(KEY_SCENE_NAME, sceneName);
        params.put(KEY_PERCENT, percent);
        params.put(KEY_DOWNLOAD_SPEED, speed);
        //  1 表示下载中, 2 表示解压缩中
        params.put(KEY_STAGE, stage);
        callback.onCallback("PRELOAD_SCENES_SET_CALLBACK", params);
    }

    public void quitGame() {
        //note: This method has to be invoked in GL thread
        LogWrapper.d(TAG, "Quit Game Start ...");
        if (null == mEngineBridge) return;
        mIsRunning = false;
        mEngineBridge.quitGame();
    }

    public void prepareEngineFinished() {
        LogWrapper.d(TAG, "prepareEngineFinished ...");
        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (RuntimeStub.getInstance().getRuntimeProxy() != null) {
                    RuntimeStub.getInstance().getRuntimeProxy().onGameEnter();
                }
            }
        });
    }

    public void onAsyncActionResult(int ret, String msg, String callbackId) {
        Map<String, Object> params = new HashMap<>();
        params.put("ret", ret);
        params.put("msg", msg);
        params.put("callbackid", callbackId);
        mEngineBridge.invokeMethodSync("onAsyncActionResult", params);
    }

    public void outputLog(int type, String tag, String msg) {
        switch (type) {
            case Log.VERBOSE:
                Log.v(tag, msg);
                break;
            case Log.DEBUG:
                Log.d(tag, msg);
                break;
            case Log.INFO:
                Log.i(tag, msg);
                break;
            case Log.WARN:
                Log.w(tag, msg);
                break;
            case Log.ERROR:
            case Log.ASSERT:
                Log.e(tag, msg);
                break;
            default:
                Log.v(tag, msg);
                break;
        }
    }
}
