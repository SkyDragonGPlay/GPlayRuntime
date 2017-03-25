package com.skydragon.gplay.runtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.skydragon.gplay.runtime.callback.OnCallbackListener;
import com.skydragon.gplay.runtime.callback.ProtocolCallback;
import com.skydragon.gplay.runtime.entity.ResultInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.exception.AppUncaughtExceptionHandler;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.protocol.ProtocolController;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.LogcatHelper;
import com.skydragon.gplay.runtime.utils.Utils;
import com.skydragon.gplay.service.IDownloadProxy;
import com.skydragon.gplay.service.IRuntimeCallback;
import com.skydragon.gplay.service.IRuntimeProxy;
import com.yolanda.nohttp.NoHttp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RuntimeStub {

    public static final String TAG = "RuntimeStub";

    public static final String VERSION = "1.7.1_rc1";
    private static final int VERSION_CODE = 81;

    private static final int PRELOAD_ONLY_BOOT_SCENE = 1;
    private static final int PRELOAD_COMPLETE_GAME = 2;
    private static final int PRELOAD_PRESCRIBED_RES = 3;
    private static final int PRELOAD_COMPLETE_GAME_WITH_PRESCRIBED_RES = 4;

    private Context mContext = null;
    private String mRunningGameName = null;
    private String mRunningGamePackageName = null;
    private String mRunningGameKey = null;
    private GameInfo mRunningGameInfo = null;
    private boolean mIsPreloadingGame;
    private boolean mCancelPreloadGame;

    private String channelID = null;

    private IRuntimeProxy mRuntimeProxy;
    private IDownloadProxy mDownloadProxy;

    private static RuntimeStub _instance;

    private RuntimeStub() {}

    public static RuntimeStub getInstance() {
        if (null == _instance) {
            _instance = new RuntimeStub();
        }
        return _instance;
    }

    private final Handler sHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            RuntimeLauncher.getInstance().setIsDownloading(false);
            IRuntimeProxy runtimeProxy = getRuntimeProxy();
            IDownloadProxy downloadProxy = getPreDownloadProxy();

            String errorType = null;
            String errorDescription = (String) msg.obj;
            switch (msg.what) {
                case RuntimeConstants.MODE_TYPE_NETWORK_ERROR:
                    errorType = RuntimeConstants.DOWNLOAD_ERROR_NETWORK_FAILED;
                    break;
                case RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT:
                    errorType = RuntimeConstants.DOWNLOAD_ERROR_NO_SPACE_LEFT;
                    break;
                case RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG:
                    errorType = RuntimeConstants.DOWNLOAD_ERROR_FILE_VERIFY_WRONG;
                    break;
                case RuntimeConstants.MODE_TYPE_NOT_SUPPORT_ARCH:
                    errorType = RuntimeConstants.DOWNLOAD_ERROR_ARCH_NOT_SUPPORTED;
                    break;
            }

            if (errorType != null) {
                if (null != runtimeProxy) {
                    try {
                        JSONObject error = new JSONObject();
                        error.put("type", errorType);
                        error.put("description", errorDescription);
                        runtimeProxy.onDownloadGameFailure(error.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (downloadProxy != null) {
                    downloadProxy.onDownloadFailure(errorType);
                }
            }
        }

    };

    public Handler getMsgHandler() {
        return sHandler;
    }

    public String getVersion() {
        return VERSION;
    }

    public int getVersionCode() {
        return VERSION_CODE;
    }

    /**
     * Initialize GPlay,
     * If using Gplay.enterGameChannel, doesn't need to invoke this method.
     *
     * @param context  The context GPlay will use
     * @param AppID    The ID of the application who integrates GPlay
     * @param cacheDir The root path of GPlay
     * @return true or false
     */
    public boolean init(Context context, String AppID, String cacheDir) {
        if (context == null) {
            LogWrapper.e(TAG, "context is null");
            return false;
        }

        FileConstants.setCacheDir(cacheDir, AppID, context);

        mContext = context.getApplicationContext();
        NoHttp.initialize(context, new NoHttp.Config().setReadTimeout(300 * 1000));
        AppUncaughtExceptionHandler.getInstance().init(mContext);
        channelID = AppID;

        FileConstants.init(context);
        FileUtils.ensureDirExists(FileConstants.getResourcesDir());
        FileUtils.createNoMediaFile();

        LogWrapper.i(TAG, "init Version:" + RuntimeStub.VERSION + "  Server:" + RuntimeConstants.getServerUrl() + "  CAppID:" + channelID);

        RuntimeLauncher.getInstance().init(context);
        return true;
    }

    boolean isValidContext() {
        if (mContext == null) {
            LogWrapper.e(TAG, "Invalid context, was 'Gplay.init(context)' invoked?");
            return false;
        }
        return true;
    }

    public boolean destroy() {
        try {
            sHandler.removeCallbacksAndMessages(null);
            RuntimeLauncher.getInstance().destroy();
            cancelDownload();
        } catch (Exception e) {
            LogWrapper.e(TAG, e);
            return false;
        }
        return true;
    }

    public String getChannelID() {
        return channelID;
    }

    public boolean isHolaChannelID() {
        return "32400".equals(channelID) || "gplay_001".equals(channelID);
    }

    public void setContext(Context context) {
        mContext = context.getApplicationContext();
    }

    public Context getContext() {
        return mContext;
    }

    public String getRootPath() {
        return FileConstants.getRootPath();
    }

    public GameInfo getRunningGameInfo() {
        return mRunningGameInfo;
    }

    public void setRuntimeProxy(IRuntimeProxy proxy) {
        mRuntimeProxy = proxy;
    }

    public IRuntimeProxy getRuntimeProxy() {
        return mRuntimeProxy;
    }

    public void setPreDownloadProxy(IDownloadProxy downloadProxy) {
        mDownloadProxy = downloadProxy;
    }

    public IDownloadProxy getPreDownloadProxy() {
        return mDownloadProxy;
    }

    public void retryDownloadGameRes(String gameKey) {
        tryLoadingGame(gameKey);
    }

    public void cancelDownload() {
        FileDownloadHelper.cancelAllRequests();
        ProtocolController.cancelAllRequests();
    }

    /**
     * Clears all game cache, downloaded game resources will be cleared.
     */
    public boolean cleanAllGameCache() {
        if (RuntimeLauncher.getInstance().isDownloading()) {
            LogWrapper.e(TAG, "Can not clear all game cache when there is some game downloading");
            return false;
        }

        String[] paths = new String[]{FileConstants.getGamesDir(), FileConstants.getDownloadDir(),
                FileConstants.getSystemDataDir()};
        for (String path : paths) {
            FileUtils.deleteFile(path);
        }
        return true;
    }

    public void cleanGameCache(String[] tags) {
        for (String tag : tags) {
            cleanGameCache(tag);
        }
    }

    /**
     * Clears one game cache by the package name or gameKey
     *
     * @param tag packageName or gameKey
     */
    public boolean cleanGameCache(String tag) {
        if (TextUtils.isEmpty(tag)) {
            LogWrapper.e(TAG, "tag is empty!");
            return false;
        }

        if (mRunningGamePackageName.equals(tag)) {
            LogWrapper.e(TAG, "Can not clear cache of a downloading game");
            return false;
        }

        String packageName = Utils.getPackageNameByGameKey(tag);
        if (packageName == null) {
            packageName = tag;
        }

        LogWrapper.d(TAG, "cleanGameCache packageName : " + packageName);
        String gamePath = FileConstants.getGameRootDir(packageName);
        FileUtils.deleteFile(gamePath);
        gamePath = FileConstants.getGameDownloadDir(packageName);
        FileUtils.deleteFile(gamePath);

        return true;
    }

    /**
     * 运行游戏 普通模式
     *
     * @param gameKey The gameKey on a certain channel
     */
    public void downloadGameRes(String gameKey, boolean bHostManagerRuntime) {
        RuntimeEnvironment.boolHostManageRuntime = bHostManagerRuntime;
        checkStatusBeforeRunGame(gameKey);
    }

    // 普通模式
    private void checkStatusBeforeRunGame(String gameKey) {
        if (!isValidContext())
            return;

        tryLoadingGame(gameKey);
    }

    /**
     * Runs a game by the game key on a certain channel
     *
     * @param gameKey The gameKey on a certain channel
     */
    private void tryLoadingGame(final String gameKey) {
        LogWrapper.d(TAG, "RuntimeStub tryLoadingGame start is host manager=" + RuntimeEnvironment.boolHostManageRuntime + ", runningGameInfo is null? " + (null == mRunningGameInfo));
        if (RuntimeEnvironment.boolHostManageRuntime && null != mRunningGameInfo) {
            LogWrapper.d(TAG, "RuntimeStub tryLoadingGame runtime game!!!");
            runGame(mRunningGameInfo);
        } else {
            if (mRuntimeProxy != null) {
                mRuntimeProxy.onDownloadGameStart();

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("download_type", "engine");
                } catch (JSONException ignored) {
                }

                mRuntimeProxy.onMessage(jsonObject.toString());
            }
            if (getPreDownloadProxy() != null) {
                getPreDownloadProxy().onDownloadStart();
            }
            mRunningGameKey = gameKey;
            requestGameInfoByKey(gameKey);
        }
    }

    /**
     * 获取游戏信息
     *
     * @param gameKey The gameKey on a certain channel
     */
    private void requestGameInfoByKey(final String gameKey) {
        ProtocolController.requestGameInfoByKey(channelID, gameKey, new ProtocolCallback<GameInfo>() {

            @Override
            public void onSuccess(GameInfo obj) {
                doRunGame(obj);
            }

            @Override
            public void onFailure(ResultInfo err) {
                JSONObject jsonObject = FileUtils.readJsonObjectFromFile(FileConstants.getLocalGameInfoJsonPath(gameKey));
                if (jsonObject != null) {
                    mRunningGameInfo = GameInfo.fromJson(jsonObject);
                }
                if (!RuntimeEnvironment.boolHostManageRuntime && null != mRunningGameInfo && Utils.getCurrAPNType() == Utils.NO_NETWORK) {
                    doRunGame(mRunningGameInfo);
                } else {
                    String errorStr = RuntimeConstants.DOWNLOAD_ERROR_NETWORK_FAILED;
                    if (err.getErrorCode() == 305) {// no such info
                        errorStr = RuntimeConstants.DOWNLOAD_ERROR_GAME_NOT_EXIST;
                    }
                    RuntimeLauncher.getInstance().setIsDownloading(false);
                    if (getRuntimeProxy() != null) {
                        getRuntimeProxy().onDownloadGameFailure(errorStr);
                    }
                    if (getPreDownloadProxy() != null) {
                        getPreDownloadProxy().onDownloadFailure(errorStr);
                    }
                }
            }
        });
    }

    /**
     * The method is used internally
     * GPlay android simulator also uses this method by reflection since it's private.
     *
     * @param gameInfo game info
     */
    private void doRunGame(GameInfo gameInfo) {
        if (!isValidContext()) {
            return;
        }

        if (gameInfo == null) {
            Utils.showToast(mContext, "Could not find the game!");
            return;
        }

        FileUtils.ensureDirExists(FileConstants.getGameRootDir(gameInfo.mPackageName));

        if (!TextUtils.isEmpty(gameInfo.mGameKey)) {
            File gameKeyFile = new File(FileConstants.getGameKeyFilePath(gameInfo.mPackageName, gameInfo.mGameKey));
            if (!gameKeyFile.exists()) {
                try {
                    gameKeyFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 依次检查不可见、不兼容、维护中、不支持
            Object[][] checks = new Object[4][];
            checks[0] = new Object[]{gameInfo.mVerifyInfo != null && !gameInfo.mVerifyInfo.isVisible(), RuntimeConstants.DOWNLOAD_ERROR_INVISIBLE};
            checks[1] = new Object[]{gameInfo.mVerifyInfo != null && !gameInfo.mVerifyInfo.isCompatible(), RuntimeConstants.DOWNLOAD_ERROR_INCOMPATIBLE};
            checks[2] = new Object[]{gameInfo.mVerifyInfo != null && gameInfo.mVerifyInfo.isMaintaining(), RuntimeConstants.DOWNLOAD_ERROR_MAINTAINING};
            checks[3] = new Object[]{!gameInfo.isArchSupported(), RuntimeConstants.DOWNLOAD_ERROR_ARCH_NOT_SUPPORTED};

            for (Object[] check : checks) {
                if ((Boolean) check[0]) {
                    if (getRuntimeProxy() != null) {
                        getRuntimeProxy().onDownloadGameFailure((String) check[1]);
                    }
                    if (getPreDownloadProxy() != null) {
                        getPreDownloadProxy().onDownloadFailure((String) check[1]);
                    }
                    LogWrapper.e(TAG, "Wrong game status, please check play server!");
                    return;
                }
            }

            // 保存正在运行的游戏信息
            mRunningGameInfo = gameInfo;
            mRunningGameName = gameInfo.mGameName;
            mRunningGamePackageName = gameInfo.mPackageName;
            mRunningGameKey = gameInfo.mGameKey;

            // 运行游戏
            RuntimeLauncher.getInstance().start(mContext, mRunningGameInfo,
                    new OnCallbackListener<GameInfo>() {
                        @Override
                        public void onCallBack(GameInfo data) {
                            if (getRuntimeProxy() != null) {
                                getRuntimeProxy().onDownloadGameSuccess(mRunningGameInfo.toJson().toString());
                            }
                            if (getPreDownloadProxy() != null) {
                                getPreDownloadProxy().onDownloadSuccess();
                            }
                        }
                    }
            );
        }
    }

    public void runGame(JSONObject jsonObject, boolean isHostManagerRuntime) {
        RuntimeEnvironment.boolHostManageRuntime = isHostManagerRuntime;
        runGame(GameInfo.fromJson(jsonObject));
    }

    /**
     * 预先下载游戏
     *
     * @param gameInfo             game info
     * @param strategy             preload strategy
     * @param resBundleName        resource bundle name
     * @param isHostManagerRuntime host manager
     */
    public void preloadGame(GameInfo gameInfo, final int strategy, final String resBundleName, boolean isHostManagerRuntime, final IRuntimeCallback callback) {
        if (!isValidContext())
            return;

        if (gameInfo == null) {
            callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME,
                    RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR,
                            RuntimeService.CALLBACK_ERROR_CODE_UNKNOWN, "Could not find the game!"));
            LogWrapper.d(TAG, "PRELOAD_GAME() callback(Could not find the game!)");
            return;
        }

        mIsPreloadingGame = true;

        FileUtils.ensureDirExists(FileConstants.getGameRootDir(gameInfo.mPackageName));

        RuntimeEnvironment.boolHostManageRuntime = isHostManagerRuntime;

        // 保存正在运行的游戏信息
        mRunningGameInfo = gameInfo;
        mRunningGameName = gameInfo.mGameName;
        mRunningGamePackageName = gameInfo.mPackageName;
        mRunningGameKey = gameInfo.mGameKey;
        RuntimeEnvironment.currentGameKey = mRunningGameKey;
        RuntimeEnvironment.currentGameName = mRunningGameName;
        RuntimeEnvironment.currentPackageName = mRunningGamePackageName;

        switch (strategy) {
            case PRELOAD_ONLY_BOOT_SCENE:
            case PRELOAD_COMPLETE_GAME:
                break;
            case PRELOAD_PRESCRIBED_RES:
            case PRELOAD_COMPLETE_GAME_WITH_PRESCRIBED_RES:
                if (resBundleName == null || resBundleName.isEmpty()) {
                    callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME,
                            RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR,
                                    RuntimeService.CALLBACK_ERROR_CODE_UNKNOWN, "\"RESOURCE_BUNDLE_NAME\" is empty!"));
                    LogWrapper.d(TAG, "PRELOAD_GAME() callback(\"RESOURCE_BUNDLE_NAME\" is empty!)");
                    mIsPreloadingGame = false;
                    return;
                }
                break;
            default:
                callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME,
                        RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR,
                                RuntimeService.CALLBACK_ERROR_CODE_UNKNOWN, "\"PRELOAD_STRATEGY\" transfer error! Do not accept parameter value " + strategy));
                LogWrapper.d(TAG, "PRELOAD_GAME() callback(\"PRELOAD_STRATEGY\" transfer error! Do not accept parameter value " + strategy + ")");
                mIsPreloadingGame = false;
                return;
        }

        // 开始预加载游戏
        callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_START, null, null));
        LogWrapper.d(TAG, "PRELOAD_GAME() callback(" + RuntimeService.PRELOAD_GAME_STATUS_START + ")");

        if (cancelPreloadGame()) {
            mStopPreloadGameListener.onStopSuccess();
            return;
        }

        // 预加载游戏
        RuntimeLauncher.getInstance().preloadGame(mContext, mRunningGameInfo, new RuntimeLauncher.IPreloadRuntimeResListener() {
            @Override
            public void onErrorOccurred(int errCode, int occurredStep, String errMsg) {
                RuntimeLauncher.getInstance().setIsDownloading(false);

                // 判断是否停止预加载
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                    return;
                }

                String errorMsg = "An error occurred at step(" + occurredStep + "), " + errMsg;
                Integer errorCode = RuntimeService.CALLBACK_ERROR_CODE_UNKNOWN;

                switch (errCode) {
                    case RuntimeConstants.MODE_TYPE_NETWORK_ERROR:
                        errorCode = RuntimeService.CALLBACK_ERROR_CODE_NO_NETWORK;
                        break;
                    case RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT:
                        errorCode = RuntimeService.CALLBACK_ERROR_CODE_NO_LEFT_SPACE;
                        break;
                    case RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG:
                        errorCode = RuntimeService.CALLBACK_ERROR_CODE_VERIFY_WRONG;
                        break;
                    case RuntimeConstants.MODE_TYPE_NOT_SUPPORT_ARCH:
                        errorCode = RuntimeService.CALLBACK_ERROR_CODE_ARCH_NOT_SUPPORT;
                        break;
                }

                callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR, errorCode, errorMsg));

                LogWrapper.d(TAG, "PRELOAD_GAME() callback(" + RuntimeService.PRELOAD_GAME_STATUS_ERROR + ")");
                mIsPreloadingGame = false;
            }

            @Override
            public void onSuccess() {
                RuntimeLauncher.getInstance().setIsDownloading(false);
                // 判断是否停止预加载
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                    return;
                }

                // 通知游戏首包之前的资源已经下载完毕。
                callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_BOOT_COMPLETE, null, null));
                LogWrapper.d(TAG, "PRELOAD_GAME() callback(" + RuntimeService.PRELOAD_GAME_STATUS_BOOT_COMPLETE + ")");
                switch (strategy) {
                    case PRELOAD_ONLY_BOOT_SCENE:
                        // 预加载游戏完毕
                        callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_FINISHED, null, null));
                        LogWrapper.d(TAG, "PRELOAD_GAME() callback(" + RuntimeService.PRELOAD_GAME_STATUS_FINISHED + ")");
                        mIsPreloadingGame = false;
                        break;
                    case PRELOAD_COMPLETE_GAME:
                        RuntimeCore.getInstance().preloadAllResBundle(createPreloadResBundleListener(callback));
                        break;
                    case PRELOAD_PRESCRIBED_RES:
                        RuntimeCore.getInstance().preloadPrescribedResBundleBeforeAll(resBundleName, false, createPreloadResBundleListener(callback));
                        break;
                    case PRELOAD_COMPLETE_GAME_WITH_PRESCRIBED_RES:
                        RuntimeCore.getInstance().preloadPrescribedResBundleBeforeAll(resBundleName, true, createPreloadResBundleListener(callback));
                        break;
                }
            }

            @Override
            public void onCancel() {
                RuntimeLauncher.getInstance().setIsDownloading(false);
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                }
            }
        });
    }

    public void preloadGameWithGameParams(JSONObject gameInfoParams, final int strategy, final String resBundleName, boolean isHostManagerRuntime, final IRuntimeCallback callback) {
        this.preloadGame(GameInfo.fromJson(gameInfoParams), strategy, resBundleName, isHostManagerRuntime, callback);
    }

    //根据gameKey进行预下载
    public void preloadGameWithGameKey(final String gameKey, final int strategy, final String resBundleName, final boolean isHostManagerRuntime, final IRuntimeCallback callback) {
        ProtocolController.requestGameInfoByKey(channelID, gameKey, new ProtocolCallback<GameInfo>() {

            @Override
            public void onSuccess(GameInfo gameInfo) {
                if (_instance != null) {
                    _instance.preloadGame(gameInfo, strategy, resBundleName, isHostManagerRuntime, callback);
                }
            }

            @Override
            public void onFailure(ResultInfo err) {
                if (callback != null) {
                    callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR,
                            RuntimeService.CALLBACK_ERROR_CODE_UNKNOWN, "getGameInfo is fail! errorCode:" + err.getErrorCode() + ",errorMsg:" + err.getMsg()));
                }
            }
        });
    }

    private boolean cancelPreloadGame() {
        return (mCancelPreloadGame && mStopPreloadGameListener != null);
    }

    // 停止预下载
    public void stopPreloadGame(final IRuntimeCallback callback) {
        if (callback == null) {
            LogWrapper.w(TAG, "stopPreloadGame, callback is null!");
        }

        if (!isPreloadGame()) {
            LogWrapper.d(TAG, "STOP_PRELOAD_GAME() callback : " + 1);
            if (callback != null) {
                callback.onCallback(RuntimeService.METHOD_STOP_PRELOAD_GAME, createStopPreloadGameResult());
            }
            return;
        }

        synchronized (this) {
            mCancelPreloadGame = true;
            mStopPreloadGameListener = new IStopPreloadGameListener() {
                @Override
                public void onStopSuccess() {
                    mCancelPreloadGame = false;
                    mStopPreloadGameListener = null;
                    mIsPreloadingGame = false;
                    LogWrapper.d(TAG, "STOP_PRELOAD_GAME() callback : " + 1);
                    if (callback != null) {
                        callback.onCallback(RuntimeService.METHOD_STOP_PRELOAD_GAME, createStopPreloadGameResult());
                    }
                }
            };
        }

        if (RuntimeLauncher.getInstance().isPreloadingGame()) {
            RuntimeLauncher.getInstance().cancelPreloadGame();
            return;
        }

        if (RuntimeCore.getInstance().isPreloadingResBundle()) {
            RuntimeCore.getInstance().cancelPreloadResBundle();
        }
    }

    private IStopPreloadGameListener mStopPreloadGameListener;

    interface IStopPreloadGameListener {
        void onStopSuccess();
    }

    public Map<String, Object> createStopPreloadGameResult() {
        Map<String, Object> result = new HashMap<>();
        result.put(RuntimeService.PRELOAD_GAME_KEY_RESULT, 1);
        return result;
    }

    private RuntimeCore.IPreloadResBundleListener createPreloadResBundleListener(final IRuntimeCallback callback) {
        return new RuntimeCore.IPreloadResBundleListener() {
            @Override
            public void onErrorOccurred(int errCode, String errMsg) {
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                    RuntimeCore.getInstance().setInCancelDownload(false);
                    return;
                }

                if (callback != null) {
                    Integer errorCode = RuntimeCore.ERR_UNKNOWN;
                    switch (errCode) {
                        case RuntimeCore.ERR_NO_NETWORK:
                            errorCode = RuntimeService.CALLBACK_ERROR_CODE_NO_NETWORK;
                            break;
                        case RuntimeCore.ERR_NO_SPACE:
                            errorCode = RuntimeService.CALLBACK_ERROR_CODE_NO_LEFT_SPACE;
                            break;
                        case RuntimeCore.ERR_VERIFY:
                            errorCode = RuntimeService.CALLBACK_ERROR_CODE_VERIFY_WRONG;
                            break;
                        case RuntimeCore.ERR_GAME_RES_EXPIRED:
                            errorCode = RuntimeService.CALLBACK_ERROR_CODE_RES_EXPIRED;
                            break;
                    }
                    callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_ERROR, errorCode, errMsg));
                    LogWrapper.d(TAG, "PRELOAD_GAME() callback(" + RuntimeService.PRELOAD_GAME_STATUS_ERROR + " , " + errorCode + " , " + errMsg + ")");
                }
                mIsPreloadingGame = false;
            }

            @Override
            public void onSuccess() {
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                    RuntimeCore.getInstance().setInCancelDownload(false);
                    return;
                }

                if (callback != null) {
                    callback.onCallback(RuntimeService.METHOD_PRELOAD_GAME, RuntimeService.createPreloadGameResult(RuntimeService.PRELOAD_GAME_STATUS_FINISHED, null, null));
                }
                mIsPreloadingGame = false;
            }

            @Override
            public void onCancel() {
                if (cancelPreloadGame()) {
                    mStopPreloadGameListener.onStopSuccess();
                    RuntimeCore.getInstance().setInCancelDownload(false);
                }
            }
        };
    }

    /**
     * The method is used internally
     * GPlay android simulator also uses this method by reflection since it's private.
     *
     * @param gameInfo game info
     */
    public void runGame(GameInfo gameInfo) {
        if (!isValidContext())
            return;

        if (gameInfo == null) {
            Utils.showToast(mContext, "Could not find the game!");
            return;
        }
        FileUtils.ensureDirExists(FileConstants.getGameRootDir(gameInfo.mPackageName));

        // 保存正在运行的游戏信息
        mRunningGameInfo = gameInfo;
        mRunningGameName = gameInfo.mGameName;
        mRunningGamePackageName = gameInfo.mPackageName;
        mRunningGameKey = gameInfo.mGameKey;
        RuntimeEnvironment.currentGameKey = mRunningGameKey;
        RuntimeEnvironment.currentGameName = mRunningGameName;
        RuntimeEnvironment.currentPackageName = mRunningGamePackageName;

        if (mRuntimeProxy != null) {
            mRuntimeProxy.onDownloadGameStart();
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("download_type", "game");
            } catch (JSONException ignored) {
            }
            mRuntimeProxy.onMessage(jsonObject.toString());
        }

        // 运行游戏
        RuntimeLauncher.getInstance().start(mContext, mRunningGameInfo,
                new OnCallbackListener<GameInfo>() {
                    @Override
                    public void onCallBack(GameInfo data) {
                        if (getRuntimeProxy() != null) {
                            if (data.mRunMode == 2) {
                                LogcatHelper.getInstance().start();
                            }
                            getRuntimeProxy().onDownloadGameSuccess(mRunningGameInfo.toJson().toString());
                        }
                        if (getPreDownloadProxy() != null) {
                            getPreDownloadProxy().onDownloadSuccess();
                        }
                    }
                }
        );
    }

    public void downloadRuntime(Context context) {
        RuntimeLauncher.getInstance().startDownloadRuntime(context);
    }

    public boolean isPreloadGame() {
        return mIsPreloadingGame;
    }

    public void setSilentDownloadEnabled(boolean enabled) {
        RuntimeEnvironment.boolSilentDownloadEnabled = enabled;
        RuntimeCore.getInstance().setSilentDownloadEnabled(enabled);
    }
}
