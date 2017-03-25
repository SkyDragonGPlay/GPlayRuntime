package com.skydragon.gplay.runtime;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;
import android.view.View;

import com.skydragon.gplay.runtime.bridge.BridgeHelper;
import com.skydragon.gplay.runtime.bridge.RuntimeBridgeProxy;
import com.skydragon.gplay.runtime.bridge.ICallback;
import com.skydragon.gplay.runtime.bridge.IEngineRuntimeBridge;
import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.LogcatHelper;
import com.skydragon.gplay.runtime.utils.TelephoneUtil;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtime.utils.Utils;
import com.skydragon.gplay.service.IDownloadProxy;
import com.skydragon.gplay.service.IRuntimeCallback;
import com.skydragon.gplay.service.IRuntimeProxy;
import com.skydragon.gplay.thirdsdk.IChannelSDKBridge;
import com.skydragon.gplay.thirdsdk.IChannelSDKServicePlugin;
import com.skydragon.gplay.unitsdk.bridge.IUnitSDKBridge;
import com.skydragon.gplay.unitsdk.bridge.IUnitSDKBridgeProxy;

import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RuntimeService implements IRuntimeService, ICallback {

    private static final String TAG = "RuntimeService";

    private Activity mActivity;
    private String mChannelId;
    private RuntimeStub mRuntimeStub;
    private BridgeHelper mBridgeHelper;
    private JSONObject mJsonGameInfo;
    private IUnitSDKBridge mUnitSDKBridge;
    private IEngineRuntimeBridge mEngineBridge;
    private BroadcastReceiver mStopPreDownGameReceiver;

    public static final String METHOD_PRELOAD_SCENES_SET_CALLBACK = "PRELOAD_SCENES_SET_CALLBACK";
    public static final String METHOD_CAPTURE_SCREEN = "CAPTURE_SCREEN";
    public static final String METHOD_SET_RES_COMPLETE_CALLBACK = "SET_RES_COMPLETE_CALLBACK";
    public static final String METHOD_PRELOAD_GAME = "PRELOAD_GAME";
    public static final String METHOD_STOP_PRELOAD_GAME = "STOP_PRELOAD_GAME";
    public static final String CALLBACK_KEY_RESOURCE_COMPLETE = "RESOURCE_COMPLETE";

    public static final String KEY_GAME_INFO = "GAME_INFO";
    public static final String KEY_PRELOAD_STRATEGY = "PRELOAD_STRATEGY";
    public static final String KEY_RESOURCE_BUNDLE_NAME = "RESOURCE_BUNDLE_NAME";
    public static final String KEY_HOST_MANAGER = "HOST_MANAGER";
    public static final String KEY_GAME = "GAME_KEY";

    public static final String ACTION_STOP_PRELOAD_GAME = "STOP_PRELOAD_GAME";

    public final static String PRELOAD_GAME_KEY_STATUS = "STATUS";
    public final static String PRELOAD_GAME_KEY_ERROR_CODE = "ERROR_CODE";
    public final static String PRELOAD_GAME_KEY_ERROR_MSG = "ERROR_MSG";
    public final static String PRELOAD_GAME_KEY_RESULT = "RESULT";
    public final static Integer PRELOAD_GAME_STATUS_START = 1;
    public final static Integer PRELOAD_GAME_STATUS_FINISHED = 2;
    public final static Integer PRELOAD_GAME_STATUS_ERROR = 3;
    public final static Integer PRELOAD_GAME_STATUS_BOOT_COMPLETE = 4;

    public static final Integer CALLBACK_ERROR_CODE_UNKNOWN = -1;
    public static final Integer CALLBACK_ERROR_CODE_NO_NETWORK = 1;
    public static final Integer CALLBACK_ERROR_CODE_NO_LEFT_SPACE = 2;
    public static final Integer CALLBACK_ERROR_CODE_VERIFY_WRONG = 3;
    public static final Integer CALLBACK_ERROR_CODE_RES_EXPIRED = 4;
    public static final Integer CALLBACK_ERROR_CODE_ARCH_NOT_SUPPORT = 5;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Utils.updateCurrAPNType();
                LogWrapper.i(TAG, "the network state has changed:" + Utils.getCurrAPNType());
            }
        }
    };

    @Override
    public boolean init(Context context, String channelID, String runtimeDir, String rootPath) {
        mChannelId = channelID;
        mUnitSDKBridge = loadUnitSDKBridge();
        Utils.setCurrentContext(context);
        RuntimeEnvironment.pathHostRuntimeDir = runtimeDir;
        mRuntimeStub = RuntimeStub.getInstance();

        Utils.updateCurrAPNType();
        //监听网络状态
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(mReceiver, intentFilter);
        return mRuntimeStub.init(context, channelID, rootPath);
    }

    @Override
    public void initRuntime(Activity activity, IChannelSDKBridge channelSDKBridge, IChannelSDKServicePlugin channelSDKServicePlugin) {
        mActivity = activity;

        RuntimeLauncher runtimeLauncher = RuntimeLauncher.getInstance();
        // create Bridge
        if (RuntimeEnvironment.engineType.contains(GameConfigInfo.UNITY)) {
            mEngineBridge = EngineLibraryLoader.getRuntimeBridge(
                    runtimeLauncher.getRuntimeJavaLibraryPath(),
                    runtimeLauncher.getGameExtendJarFile(),
                    runtimeLauncher.getStandardLibraryDirectory() + File.pathSeparator +
                            runtimeLauncher.getExtendLibrariesDir(),
                    runtimeLauncher.getOptimizedDexDir());
        } else {
            mEngineBridge = EngineLibraryLoader.getRuntimeBridge(
                    runtimeLauncher.getRuntimeJavaLibraryPath(),
                    runtimeLauncher.getGameExtendJarFile(),
                    runtimeLauncher.getGameSharedLibraryDirectory() + File.pathSeparator +
                            runtimeLauncher.getExtendLibrariesDir(),
                    runtimeLauncher.getOptimizedDexDir());
        }

        if (mEngineBridge == null) {
            LogWrapper.e(TAG, "create IEngineRuntimeBridge failed!");
            return;
        }

        mEngineBridge.invokeMethodSync("setRuntimeEnvironment", RuntimeEnvironment.getParametersForEngineJAR());
        RuntimeBridgeProxy proxy = new RuntimeBridgeProxy();
        proxy.setListener("onQuitGame", this);
        mEngineBridge.setBridgeProxy(proxy);

        mBridgeHelper = BridgeHelper.getInstance();
        mBridgeHelper.setRuntimeBridge(mEngineBridge);
        mBridgeHelper.setUnitSDKBridge(mUnitSDKBridge);

        if (RuntimeEnvironment.engineType.contains(GameConfigInfo.UNITY)) {
            mBridgeHelper.loadSharedLibrary(getAllSharedLibraries());
            mBridgeHelper.initRuntimeJNI();
            mBridgeHelper.init(mActivity);
        } else {
            mBridgeHelper.init(mActivity);
            mBridgeHelper.loadSharedLibrary(getAllSharedLibraries());
            mBridgeHelper.initRuntimeJNI();
        }

        String gameConfigInfo = getConfigInfo(mActivity, RuntimeEnvironment.currentPackageName);
        mBridgeHelper.setRuntimeConfig(gameConfigInfo);
        mEngineBridge.setOption("config", gameConfigInfo);
        mBridgeHelper.notifyOnLoadSharedLibrary();

        GameInfo gameInfo = mRuntimeStub.getRunningGameInfo();
        mJsonGameInfo = createGameInfoJson(gameInfo, gameConfigInfo);

        if (!RuntimeEnvironment.debugMode) {
            if (null != mUnitSDKBridge) {
                mUnitSDKBridge.init(activity, mChannelId, mJsonGameInfo, channelSDKBridge, channelSDKServicePlugin);
                mUnitSDKBridge.setBridgeProxy(new IUnitSDKBridgeProxy() {
                    @Override
                    public Object invokeMethodSync(String method, Map<String, Object> args) {
                        switch (method) {
                            case "onAsyncActionResult": {
                                int code = (Integer) args.get("ret");
                                String msg = (String) args.get("msg");
                                String callbackId = (String) args.get("callbackid");
                                mBridgeHelper.onAsyncActionResult(code, msg, callbackId);
                                break;
                            }
                            case "outputLog": {
                                int type = (Integer) args.get("type");
                                String tag = (String) args.get("tag");
                                String msg = (String) args.get("msg");
                                mBridgeHelper.outputLog(type, tag, msg);
                                break;
                            }
                            default: {
                                Log.e(TAG, "CocosBridge.invokeMethodSync doesn't support ( " + method + " )");
                            }
                        }
                        return null;
                    }

                    @Override
                    public void runOnGLThread(Runnable r) {
                        mEngineBridge.runOnGLThread(r);
                    }

                });
            }
        }

        mBridgeHelper.startGame();
    }

    @Override
    public Object invokeMethodSync(String method, Map<String, Object> args) {
        if (method != null)
            LogWrapper.i(TAG, "invokeMethodSync method:" + method);
        else {
            LogWrapper.e(TAG, "invokeMethodSync method is null");
            return null;
        }

        switch (method) {
            case "GET_VERSION_CONFIGS": {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("runtime_sdk_version", mRuntimeStub.getVersion());
                    jsonObject.put("engine_jar_version", mBridgeHelper.getEngineRuntimeVersion());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return jsonObject;
            }
            case "GET_RUNTIME_VERSION_CODE":
                return Integer.valueOf(mRuntimeStub.getVersionCode());
            default:
                break;
        }
        return null;
    }

    @Override
    public void invokeMethodAsync(final String method, final Map<String, Object> args, final IRuntimeCallback callback) {
        if (method != null)
            LogWrapper.i(TAG, "invokeMethodAsync method:" + method);
        else {
            LogWrapper.e(TAG, "invokeMethodAsync method is null");
            return;
        }

        BridgeHelper bridgeHelper = BridgeHelper.getInstance();

        switch (method) {
            case METHOD_PRELOAD_SCENES_SET_CALLBACK:
                // 加载场景渠道通知
                bridgeHelper.setPreloadScenesCallback(callback);
                break;
            case METHOD_CAPTURE_SCREEN:
                // 截图接口
                if (callback != null) {
                    bridgeHelper.getEngineBridge().invokeMethodAsync(method, args, new ICallback() {
                        @Override
                        public Object onCallback(String from, Map<String, Object> args) {
                            return callback.onCallback(from, args);
                        }
                    });
                }
                break;
            case METHOD_SET_RES_COMPLETE_CALLBACK:
                // 静默下载完成回调通知
                if (callback != null) {
                    LogWrapper.d(TAG, "InvokeMethodAsync  SET_RES_COMPLETE_CALLBACK()");
                    bridgeHelper.setResCompletedListener(new RuntimeCore.IResourceCompletedListener() {
                        @Override
                        public void onCompleted() {
                            Map<String, Object> resultParams = new HashMap<>();
                            resultParams.put(CALLBACK_KEY_RESOURCE_COMPLETE, true);
                            callback.onCallback(METHOD_SET_RES_COMPLETE_CALLBACK, resultParams);
                        }
                    });
                }
                break;
            case METHOD_PRELOAD_GAME:
                // 预加载游戏接口
                if (callback != null) {
                    String errorMessage = null;
                    do {
                        if (args == null || args.isEmpty()) {
                            errorMessage = "args is null or empty";
                            break;
                        }

                        if (RuntimeLauncher.getInstance().isPreloadingGame()) {
                            errorMessage = "Runtime already preload game!";
                            break;
                        }

                        String gameKey = (String) args.get(KEY_GAME);
                        JSONObject gameInfo = (JSONObject) args.get(KEY_GAME_INFO);
                        Integer strategy = (Integer) args.get(KEY_PRELOAD_STRATEGY);
                        String resBundleName = (String) args.get(KEY_RESOURCE_BUNDLE_NAME);
                        Boolean isHostManager = (Boolean) args.get(KEY_HOST_MANAGER);

                        LogWrapper.d(TAG, "PRELOAD_GAME, strategy:" + strategy +
                                ", bundle name:" + resBundleName + ", isHostManager:" + isHostManager);

                        if (strategy == null) {
                            errorMessage = "[PRELOAD_STRATEGY] is null";
                            break;
                        }

                        if (isHostManager == null) {
                            isHostManager = true;
                        }

                        if (gameInfo != null) {
                            mRuntimeStub.preloadGameWithGameParams(gameInfo, strategy, resBundleName, isHostManager, callback);
                        } else if (gameKey != null) {
                            mRuntimeStub.preloadGameWithGameKey(gameKey, strategy, resBundleName, isHostManager, callback);
                        } else {
                            errorMessage = "[GAME_INFO] and [GAME_KEY] is null!";
                        }
                    } while (false);

                    if (errorMessage != null) {
                        callback.onCallback(METHOD_PRELOAD_GAME,
                                createPreloadGameResult(PRELOAD_GAME_STATUS_ERROR, CALLBACK_ERROR_CODE_UNKNOWN, errorMessage));
                    } else {
                        receiveStopPreLoadGameBroadcast(callback);
                    }
                } else {
                    LogWrapper.e(TAG, "invokeMethodAsync PRELOAD_GAME, callback is null");
                }
                break;
            case METHOD_STOP_PRELOAD_GAME:
                LogWrapper.d(TAG, "InvokeMethodAsync STOP_PRELOAD_GAME");
                mRuntimeStub.stopPreloadGame(callback);
                break;
        }
    }

    //接收停止预下载广播
    private void receiveStopPreLoadGameBroadcast(final IRuntimeCallback callback) {
        if (mStopPreDownGameReceiver == null) {
            mStopPreDownGameReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(ACTION_STOP_PRELOAD_GAME)) {
                        if (mRuntimeStub != null) {
                            LogWrapper.d(TAG, "Start Game Send Broadcast Do STOP_PRELOAD_GAME");
                            mRuntimeStub.stopPreloadGame(callback);
                        }
                    }
                }
            };

            IntentFilter stopPreLoadGame = new IntentFilter();
            stopPreLoadGame.addAction(ACTION_STOP_PRELOAD_GAME);
            Utils.getCurrentContext().registerReceiver(mStopPreDownGameReceiver, stopPreLoadGame);
        }

    }

    public static Map<String, Object> createPreloadGameResult(Integer status, Integer errCode, String errMsg) {
        Map<String, Object> result = new HashMap<>();
        result.put(PRELOAD_GAME_KEY_STATUS, status);
        if (errCode != null) {
            result.put(PRELOAD_GAME_KEY_ERROR_CODE, errCode);
            result.put(PRELOAD_GAME_KEY_ERROR_MSG, errMsg);
        }
        return result;
    }

    @Override
    public void notifyPreloadFinished() {
        RuntimeCore.getInstance().notifyPreloadFinished();
    }

    @Override
    public void retryPreloadScenes() {
        RuntimeCore.getInstance().retryPreloadScenes();
    }

    @Override
    public View getGameView() {
        RuntimeLauncher.getInstance().startOfflineGameUpdateChecker();
        return mBridgeHelper.getEngineLayout();
    }

    @Override
    public void closeGame() {
        if (mBridgeHelper != null) {
            mBridgeHelper.quitGame();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        LogWrapper.d(TAG, "onNewIntent");
        if (mBridgeHelper != null) {
            mBridgeHelper.onNewIntent(intent);
        }
    }

    @Override
    public void onPause() {
        LogWrapper.d(TAG, "onPause");
        if (mBridgeHelper != null) {
            mBridgeHelper.onPause();
        }
    }

    @Override
    public void onResume() {
        LogWrapper.d(TAG, "onResume");
        if (mBridgeHelper != null) {
            mBridgeHelper.onResume();
            Utils.updateCurrAPNType();
        }
    }

    @Override
    public void onDestroy() {
        LogWrapper.d(TAG, "onDestroy");
        if (mBridgeHelper != null) {
            mBridgeHelper.onDestroy();
        }
        LogcatHelper.destroyInstance();
        mRuntimeStub.destroy();
    }

    @Override
    public void onStop() {
        LogWrapper.d(TAG, "onStop");
        if (mBridgeHelper != null) {
            mBridgeHelper.onStop();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        LogWrapper.d(TAG, "onWindowFocusChanged");
        if (mBridgeHelper != null) {
            mBridgeHelper.onWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogWrapper.d(TAG, "onActivityResult");
        if (mBridgeHelper != null) {
            mBridgeHelper.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public Object onCallback(String from, Map<String, Object> params) {
        if (from != null && from.equals("onQuitGame")) {
            quitGame();
        }

        if (LogWrapper.SHOW_LOG) {
            String fromStr = from != null ? from : "null";
            String argsStr = params != null ? params.toString() : "null";

            LogWrapper.d(TAG, "onCallback, from:" + fromStr + ", args:" + argsStr);
        }

        return null;
    }

    @Override
    public boolean cleanAllGameCache() {
        return mRuntimeStub.cleanAllGameCache();
    }

    @Override
    public boolean cleanGameCache(String tag) {
        return mRuntimeStub.cleanGameCache(tag);
    }

    @Override
    public String getCacheDir() {
        return mRuntimeStub.getRootPath();
    }

    @Override
    public void startGame(Activity activity, String gameKey) {
        mRuntimeStub.downloadGameRes(gameKey, false);
    }

    @Override
    public void startGame(Activity activity, String gameKey, boolean bHostManagerRuntime) {
        mRuntimeStub.downloadGameRes(gameKey, bHostManagerRuntime);
    }

    @Override
    public void startGame(Activity activity, JSONObject jsonObject) {
        mRuntimeStub.runGame(jsonObject, true);
    }

    @Override
    public void startGameForDebug(Activity activity, JSONObject jsonObject) {
        RuntimeConstants.setDebugRuntimeEnabled(true);
        mRuntimeStub.runGame(jsonObject, false);
    }

    @Override
    public void retryStartGame(String gameKey) {
        Utils.updateCurrAPNType();
        mRuntimeStub.retryDownloadGameRes(gameKey);
    }

    @Override
    public void cancelStartGame() {
        mRuntimeStub.cancelDownload();
    }

    @Override
    public void cancelPrepareRuntime() {
        RuntimeStub.getInstance().cancelDownload();
    }

    @Override
    public void prepareRuntime(Context context) {
        RuntimeStub.getInstance().downloadRuntime(context);
    }

    @Override
    public void startGameForLocalDebug(Activity activity, String jsonStrGame) {
        RuntimeEnvironment.debugMode = true;
        GameInfo gameInfo = GameInfo.fromJson(jsonStrGame);
        mRuntimeStub.runGame(gameInfo);
    }

    @Override
    public void setRuntimeHostUrl(String host) {
        RuntimeConstants.setHostUrl(host);
    }

    @Override
    public void setUnitSDKHostUrl(String host) {
        if (null != mUnitSDKBridge) {
            mUnitSDKBridge.setServerHostUrl(host);
        }
    }

    @Override
    public void setRuntimeProxy(IRuntimeProxy proxy) {
        mRuntimeStub.setRuntimeProxy(proxy);
    }

    @Override
    public void setSilentDownloadEnabled(boolean enabled) {
        mRuntimeStub.setSilentDownloadEnabled(enabled);
    }

    @Override
    public void startSilentDownload() {
        RuntimeCore.getInstance().startSilentDownload();
    }

    @Override
    public void stopSilentDownload() {
        RuntimeCore.getInstance().stopSilentDownload();
    }

    @Override
    public void setPrepareRuntimeProxy(IDownloadProxy downloadProxy) {
        RuntimeStub.getInstance().setPreDownloadProxy(downloadProxy);
    }

    @Override
    public JSONObject getGameInfo() {
        return mJsonGameInfo;
    }

    private String getConfigInfo(Context ctx, String pkgName) {
        String result = null;
        try {
            JSONObject config = FileUtils.readJsonObjectFromFile(GameConfigInfo.getConfigPath(pkgName));
            config.put("device_id", TelephoneUtil.getDeviceID(ctx));
            config.put("channel_id", RuntimeEnvironment.channel);
            result = config.toString();
        } catch (Exception e) {
            LogWrapper.e(TAG, e.getMessage());
        }
        return result;
    }

    private ArrayList<String> getAllSharedLibraries() {
        ArrayList<String> listSoFile = new ArrayList<>();

        String gameSharedLibraryPath = RuntimeLauncher.getInstance().getGameShareLibraryPath();
        File gameSharedLibrary = new File(gameSharedLibraryPath);
        boolean isExistGameSO = gameSharedLibrary.exists();

        if (isExistGameSO) {
            listSoFile.add(gameSharedLibraryPath);
            return listSoFile;
        } else {
            // get standard libraries
            String standardLibrariesDir = RuntimeLauncher.getInstance().getStandardLibraryDirectory();
            File standardLibrariesDirFile = new File(standardLibrariesDir);
            File[] fsShareLibrary = standardLibrariesDirFile.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return (filename.contains(".so") && !filename.contains("__MACOSX"));
                }
            });

            if (null == fsShareLibrary || fsShareLibrary.length == 0) {
                LogWrapper.e(TAG, "getAllSharedLibraries error: standard libraries is empty!");
                return listSoFile;
            }

            for (File f : fsShareLibrary) {
                listSoFile.add(f.getAbsolutePath());
            }

            return listSoFile;
        }
    }

    private void quitGame() {
        Log.d(TAG, "RuntimeService quitGame called!!!");
        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                RuntimeLauncher.getInstance().destroy();
                if (mRuntimeStub.getRuntimeProxy() != null) {
                    mRuntimeStub.getRuntimeProxy().onGameExit();
                }
            }
        });
    }

    private IUnitSDKBridge loadUnitSDKBridge() {
        try {
            Class<?> cls = RuntimeService.class.getClassLoader().loadClass("com.skydragon.gplay.unitsdk.framework.UnitSDKBridge");
            Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (IUnitSDKBridge) ctor.newInstance();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject createGameInfoJson(GameInfo originalGameInfo, String gameConfig) {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject jsonGameConfig = new JSONObject(gameConfig);
            JSONObject jsonChannelInfo = originalGameInfo.mChannelConfigInfo.getChannelConfigJson();
            jsonObject.put("channel_config", jsonChannelInfo);
            jsonObject.put("engine_type", jsonGameConfig.optString("engine"));
            jsonObject.put("engine_version", jsonGameConfig.optString("engine_version"));
            jsonObject.put("client_id", originalGameInfo.mGameKey);
            jsonObject.put("orientation", jsonGameConfig.optString("orientation"));
            jsonObject.put("game_name", jsonGameConfig.optString("name"));
            jsonObject.put("package_name", jsonGameConfig.optString("package_name"));
            jsonObject.put("version_name", jsonGameConfig.optString("version_name"));
            String downloadUrl = originalGameInfo.mDownloadUrl;
            String iconUrl;
            int densityDpi = mActivity.getResources().getDisplayMetrics().densityDpi;
            if (densityDpi <= 240) {
                iconUrl = downloadUrl + "/icon/icon_small.png";
            } else if (densityDpi >= 480) {
                iconUrl = downloadUrl + "/icon/icon_large.png";
            } else {
                iconUrl = downloadUrl + "/icon/icon_middle.png";
            }
            jsonObject.put("icon_url", iconUrl);
            Log.d(TAG, jsonObject.toString());
            return jsonObject;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }
}
