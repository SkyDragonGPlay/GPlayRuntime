package com.skydragon.gplay.runtime;

import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;
import com.skydragon.gplay.runtime.callback.OnCallbackListener;
import com.skydragon.gplay.runtime.callback.OnCancelDownloadListener;
import com.skydragon.gplay.runtime.callback.OnDownloadCancelListener;
import com.skydragon.gplay.runtime.callback.ProtocolCallback;
import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.LocalSceneInfo;
import com.skydragon.gplay.runtime.entity.ResultInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.entity.resource.GameResourceConfigInfo;
import com.skydragon.gplay.runtime.entity.resource.ResourceTemplate;
import com.skydragon.gplay.runtime.entity.resource.SceneInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeCompatibilityInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeEngineSupportInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.protocol.ProtocolController;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LoadingProgressController;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtime.utils.Utils;
import com.skydragon.gplay.runtime.utils.ZipUtils;
import com.skydragon.gplay.service.IRuntimeProxy;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import static com.skydragon.gplay.runtime.protocol.FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH;

/**
 * 此类的作用是检测和更新游戏的运行环境，主要功能如下：
 * 1、检查引擎是否有更新
 * 2、检查游戏配置文件是否有更新
 * 3、检查游戏资源配置文件
 * 4、生成游戏so
 * 5、获取boot分组
 */
public final class RuntimeLauncher implements OnDownloadCancelListener {

    private static final String TAG = "RuntimeLauncher";
    private static RuntimeLauncher _instance;
    private RuntimeStub mRuntimeStub = RuntimeStub.getInstance();

    private GameInfo mRunningGameInfo = null;
    private String mRunningGamePackageName = "";
    private String mRunningGameName = "";
    private String mRunningGameKey = "";

    private EngineStandardLibrary mEngineSharedLibrary;
    private AbstractEngineLibController mRuntimeLibController;
    private RuntimeGroup mRuntimeGroup;
    private RuntimeDiffPatchFile mRuntimeDiffPatch;
    private GameExtendJAR mGameExtendJAR;
    private GameExtendLibrary mGameExtendLibrary;
    private EngineCommonRes mEngineCommonRes;


    // 游戏相关
    private String mPathRemoteConfig = "";
    private String mPathRemoteConfigTemp;
    private String mPathPreloadGameDir;
    private String mPathLocalConfig = "";
    private String mPathHostRuntimeLibVersionDir = "";

    private String mEngineType;
    private String mEngineVersion;
    private String mRuntimeVersion;
    private String mRuntimeArch = "";
    private String mPathCurrentGameDir = "";
    private String mPathCurrentGameGroupDir = "";
    private String mEngineJARGipFilePath = "";
    private String mEngineJARGipTempFilePath = "";
    private String mPathResourceConfig = "";
    private String mPathResourceConfigZip = "";
    private String recordErrorMsg;

    private Context mContext;
    private GameConfigInfo mConfigInfo;
    private RuntimeCompatibilityInfo mCurrentCompatibility;
    private RuntimeCompatibilityInfo mRuntimeCompInfo;
    private RuntimeEngineSupportInfo mEngineSupportInfo;
    private GameResourceConfigInfo mResourceConfigInfo;
    private LoadingProgressController mLoadingProgressController;
    private OnCallbackListener<GameInfo> mListener;
    private IPreloadRuntimeResListener mPreloadRuntimeResListener;

    private long mLastTimeNotifyToChannel;
    private long mUpdateShareLibraryBeginTime;
    private long mUpdatePatchFinishTime;
    private long mUpdateShareLibraryFinishTime;
    private long mUpdateBootFinishTime;
    private long mUpdateBootBeginTime;
    private long mUpdatePatchBeginTime;
    private long mUpdateExtendLibsBeginTime;
    private long mUpdateExtendLibsFinishedTime;

    private boolean mGameIsStart;
    private boolean mIsDownloading;
    private boolean mIsOfflineGame;
    private boolean mIsPreloadingGame;
    private boolean mIsLoadingCanceled;
    private boolean mIsPreDownloadRuntime;
    private boolean mUpdateExtendLibsFinished;
    private boolean mRunningSinglePlayerChecker;
    private boolean startParallelGameResLoading;

    private int mRetryTimes;
    private int recordErrorType = -1;
    private int recordErrorStep = RuntimeConstants.ERROR_OCCURRED_STEP_UNKNOWN;
    private int mGameUpdateCheckRetryTimes;
    private int mEngineSupportInfoIndex;
    private static final int THREAD_LOAD_BOOT_SCENE = 1;
    private static final int THREAD_LOAD_RUNTIME_SO = 2;
    private static final int THREAD_LOAD_UNITY_RES = 3;

    private static final int UPDATE_BOOT_SCENE_RETRY_TIMES = 1;
    private static final int PRELOAD_BOOT_SCENE_RETRY_TIMES = 3;

    private static final int CURRENT_STATE_PREPARE_URL = 1;
    private static final int CURRENT_STATE_FETCH_REMOTE_CONFIG = 2;
    private static final int CURRENT_STATE_LOCAL_RES_RECORD_COMPATIBLE = 3;
    private static final int CURRENT_STATE_UPDATE_LOCAL_CONFIG = 4;
    private static final int CURRENT_STATE_UPDATE_RUNTIME_COMP = 5;
    private static final int CURRENT_STATE_UPDATE_RESOURCE_CONFIG = 6;
    private static final int CURRENT_STATE_REFRESH_LOCAL_RECORD = 7;
    private static final int CURRENT_STATE_FETCH_RUNTIME_JAR = 8;
    private static final int CURRENT_STATE_UPDATE_RUNTIME_JAR = 9;
    private static final int CURRENT_STATE_FETCH_DIFFPATCH_SO = 10;
    private static final int CURRENT_STATE_FETCH_RUNTIME_SO = 11;
    private static final int CURRENT_STATE_FETCH_GAME_PATCH_SO = 12;
    private static final int CURRENT_STATE_FETCH_GAME_EXTEND = 13;
    private static final int CURRENT_STATE_DELETE_NO_REF_GROUPS = 14;
    private static final int CURRENT_STATE_UPDATE_BOOT_RES = 15;
    private static final int CURRENT_STATE_PREPARE_RUNTIME_DOWNLOAD_CONFIG = 16;
    private static final int CURRENT_STATE_UPDATE_EXTEND_LIBRARIES = 17;
    private static final int CURRENT_STATE_UPDATE_COMMON_RES = 18;
    private static final int CURRENT_STATE_UPDATE_SHARE_LIBRARIES = 19;
    private static final int CURRENT_STATE_UPDATE_DLL = 20;

    private static final String BOOT_SCENE_KEY = "boot_scene";

    private static final String RUNTIME_STATE_FOR_CHANNEL = "RUNTIME_STATE_COMPLETED";
    private static final String KEY_ERROR_IS_PRE_DOWNLOAD = "ERROR_IN_PRELOAD";
    private static final String KEY_ERROR_OCCUR_STEP = "ERROR_OCCUR_STEP";
    private static final String KEY_ERROR_MSG = "ERROR_MESSAGE";
    // 通知渠道刷新进度的最小时间间隔
    private static final long MIN_NOTIFY_TO_CHANNEL_TIME_INTERVAL = 50;
    private static final long OFFLINE_GAME_CHECK_FILED_TIME_INTERVAL = 10 * 1000;
    private static final long OFFLINE_GAME_CHECK_FAILED_RETRY_TIMES = 2;
    private static final float BEGIN_0 = 0f;
    private static final float FINISHED_100 = 100f;
    private static final float GAME_CONFIG_PROGRESS = 2f;
    private static final float RUNTIME_COMP_PROGRESS = 2f;

    private AtomicInteger mParallelRunningRoutes = new AtomicInteger();

    public static RuntimeLauncher getInstance() {
        if (null == _instance) {
            _instance = new RuntimeLauncher();
        }
        return _instance;
    }

    public void init(Context context) {
        if (TextUtils.isEmpty(RuntimeEnvironment.pathHostRuntimeDir)) {
            RuntimeEnvironment.pathHostRuntimeDir = FileConstants.getSystemDataDir();
        }

        FileUtils.ensureDirExists(FileConstants.getGamesDir());

        RuntimeEnvironment.pathHostRuntimeDir = FileUtils.ensurePathEndsWithSlash(RuntimeEnvironment.pathHostRuntimeDir);
        FileUtils.ensureDirExists(RuntimeEnvironment.pathHostRuntimeDir);

        RuntimeEnvironment.hostPackageName = context.getPackageName();
    }

    private void initGameType() {
        mIsOfflineGame = false;

        // 用于判断本地是否有遗留的游戏配置文件。验证 isOffline 判断是否准确。
        File gameConfigFile = new File(mPathLocalConfig);

        if (gameConfigFile != null && gameConfigFile.exists()) {
            JSONObject jsonConfig = FileUtils.readJsonFile(gameConfigFile);
            LogWrapper.d(TAG, "Init with game config (" + jsonConfig + ")");
            if (jsonConfig != null) {
                mConfigInfo = GameConfigInfo.fromJson(jsonConfig);
                if (mConfigInfo != null) {
                    mIsOfflineGame = mConfigInfo.getGameType() == GameConfigInfo.TYPE_OFFLINE;
                } else {
                    LogWrapper.e(TAG, "initGameType, init game config info failed!");
                }
            } else {
                LogWrapper.e(TAG, "initGameType, parse game config file failed!");
            }
        }

        LogWrapper.i(TAG, "initGameType, game type is offline:" + mIsOfflineGame);
    }

    public void start(Context ctx, GameInfo gameInfo, OnCallbackListener<GameInfo> listener) {
        start(ctx, gameInfo, null, listener);
    }

    /**
     * 预加载游戏资源，不解压、不启动游戏。
     *
     * @param gameInfo 游戏信息
     * @param listener 预加载游戏回调
     */
    public void preloadGame(Context ctx, GameInfo gameInfo, IPreloadRuntimeResListener listener) {
        mPreloadRuntimeResListener = listener;
        mIsPreloadingGame = true;
        // 预下载目录
        mPathPreloadGameDir = FileConstants.getPreloadDir(gameInfo.mPackageName);
        start(ctx, gameInfo, null, new OnCallbackListener<GameInfo>() {
            @Override
            public void onCallBack(GameInfo data) {
            }
        });
    }

    public String getPreloadGameDir() {
        return mPathPreloadGameDir;
    }

    public void sendStopPreloadBroadcast() {
        Intent intent = new Intent();
        intent.setAction(RuntimeService.ACTION_STOP_PRELOAD_GAME);
        Utils.getCurrentContext().sendBroadcast(intent);
    }

    public void start(Context ctx, GameInfo gameInfo, RuntimeCompatibilityInfo compatibilityInfo, OnCallbackListener<GameInfo> listener) {
        if (!mIsPreloadingGame) {
            //发送广播停止预下载
            sendStopPreloadBroadcast();
        }

        mListener = listener;
        mRunningGameInfo = gameInfo;
        mCurrentCompatibility = compatibilityInfo;
        mContext = ctx;
        mRunningGamePackageName = gameInfo.mPackageName;
        mRunningGameName = gameInfo.mGameName;
        mRunningGameKey = gameInfo.mGameKey;
        mPathRemoteConfig = FileConstants.getTempDir() + RuntimeConstants.REMOTE_CONFIG_FILE_NAME + "." + mRunningGameKey;
        mPathRemoteConfigTemp = FileConstants.getTempDir() + RuntimeConstants.REMOTE_CONFIG_FILE_NAME_TEMP + "." + mRunningGameKey;
        mPathLocalConfig = FileConstants.getGameRootDir(mRunningGamePackageName) + RuntimeConstants.CONFIG_FILE_NAME;

        if (isDownloading()) {
            LogWrapper.d(TAG, mRunningGameInfo.mPackageName + " is downloading, show the loading dialog again.");
            mPreloadRuntimeResListener.onErrorOccurred(RuntimeConstants.MODE_TYPE_UNKNOWN_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UNKNOWN, "Preloader is running, please close or wait for completion!");
            return;
        }

        mIsDownloading = true;
        mIsPreDownloadRuntime = false;
        setLoadingCancelled(false);

        //创建进度条信息
        mLoadingProgressController = new LoadingProgressController();
        mLoadingProgressController.addStageWithProgress(RuntimeConstants.LOADING_FETCH_GAME_CONFIG, GAME_CONFIG_PROGRESS);
        if (!RuntimeEnvironment.boolHostManageRuntime) {
            mLoadingProgressController.addStageWithProgress(RuntimeConstants.LOADING_FETCH_RUNTIME_COMP, RUNTIME_COMP_PROGRESS);
        }

        if (RuntimeEnvironment.boolHostManageRuntime) {
            if (Utils.isEmpty(mRuntimeArch)) {
                mRuntimeArch = Utils.getPhoneArch();
            }
            mRunningGameInfo.mEngineArch = mRuntimeArch;
        }

        initGameType();

        if (mRuntimeStub.isHolaChannelID()) {
            //Hola渠道的游戏发布地址,版本号需要叠加1000以兼容游戏资源的迁移
            mRunningGameInfo.initDownloadURL(mIsOfflineGame, RuntimeLocalRecord.getInstance().getGameVersion() + 1000);
        } else {
            mRunningGameInfo.initDownloadURL(mIsOfflineGame, RuntimeLocalRecord.getInstance().getGameVersion());
        }

        notifyCurrentStepFinished(CURRENT_STATE_PREPARE_URL);
    }

    private void initLoadingProgressController() {
        if (!RuntimeEnvironment.boolHostManageRuntime) {
            //引擎JAR
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_ENGINE_JAR, 8);
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_LOAD_ENGINE_JAR, 2);

            //合并差分文件的动态库
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_PATCH_SO, 5);

            //引擎标准动态库
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_ENGINE_SO, 20);
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_ENGINE_SO, 5);

            if (mEngineSupportInfo != null) {
                //引擎通用资源，当前只有unity游戏才有
                if (mEngineSupportInfo.getCommonResource() != null) {
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_ENGINE_COMMON_RES, 16);
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_ENGINE_COMMON_RES, 4);
                }
                //引擎扩展动态库，当前就unity需要的gplay动态库
                if (mEngineSupportInfo.getShareLibraries() != null) {
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_ENGINE_EXTEND_LIBS, 4);
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_ENGINE_EXTEND_LIBS, 1);
                }
                //引擎扩展DLL，当前就unity需要的gplay dll
                if (mEngineSupportInfo.getDynamicLinkLibraries() != null) {
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_ENGINE_EXTEND_DLL, 4);
                    mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_ENGINE_EXTEND_DLL, 1);
                }
            }
        }

        if (!mIsPreDownloadRuntime) {
            //游戏资源配置
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_GAME_RESOURCES_CONFIG, 4);

            //游戏扩展JAR
            if (mConfigInfo.getGameExtendInfo() != null) {
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_JAR, 8);
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_GAME_EXTEND_JAR, 2);
            }
            //游戏扩展动态库
            if (mConfigInfo.getExtendLibsInfo() != null) {
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_LIBS, 16);
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_GAME_EXTEND_LIBS, 4);
            }
            //游戏首包
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_BOOT_GROUP, 30);
            mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_UNZIP_BOOT_GROUP, 5);

            if (mConfigInfo.getEngine().contains(GameConfigInfo.COCOS)) {
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_COMPATIBLE_LOCAL_RES_RECORD, 2);

                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_FETCH_GAME_PATCH, 30);
                mLoadingProgressController.addStageWithWeight(RuntimeConstants.LOADING_COMPOUND_GAME_SO, 5);
            }
        }
    }

    private void startGame() {
        // 启动游戏前填充 RuntimeEnvironment 给引擎层使用
        RuntimeEnvironment.currentGameOrientation = mRunningGameInfo.mOrientation;
        RuntimeEnvironment.currentPackageName = mRunningGamePackageName;
        RuntimeEnvironment.currentGameKey = mRunningGameKey;
        RuntimeEnvironment.currentGameName = mRunningGameName;
        RuntimeEnvironment.pathCurrentGameDir = mPathCurrentGameDir;
        RuntimeEnvironment.engineType = mEngineType;
        RuntimeEnvironment.engineVersion = mEngineVersion;
        RuntimeEnvironment.pathHostRuntimeLibVersionDir = mPathHostRuntimeLibVersionDir;
        RuntimeEnvironment.pathHostRuntimeResourceDir = getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_RESOURCE_DIR);
        RuntimeEnvironment.currentGameArch = mRunningGameInfo.mEngineArch;
        RuntimeEnvironment.pathLocalConfig = mPathLocalConfig;
        RuntimeEnvironment.urlCurrentGame = mRunningGameInfo.mDownloadUrl;
        RuntimeEnvironment.channel = RuntimeStub.getInstance().getChannelID();
        // 开始游戏
        doStartGame();
        mGameIsStart = true;

        if (mIsOfflineGame) {
            String path = FileConstants.getOfflineGameMarkerPath(mRunningGamePackageName);
            // 删除更新标志文件
            FileUtils.deleteFile(path);
        }

        RuntimeCore.getInstance().startResBundleChecker();
    }

    public void startDownloadRuntime(Context ctx) {
        mContext = ctx;

        if (isDownloading()) {
            LogWrapper.d(TAG, "Runtime is downloading, show the loading dialog again.");
            return;
        }
        mIsDownloading = true;
        mIsPreDownloadRuntime = true;
        mRunningGameKey = "runtime";

        setLoadingCancelled(false);

        //创建进度条信息
        mLoadingProgressController = new LoadingProgressController();
        if (!RuntimeEnvironment.boolHostManageRuntime) {
            mLoadingProgressController.addStageWithProgress(RuntimeConstants.LOADING_FETCH_RUNTIME_COMP, RUNTIME_COMP_PROGRESS);
        }

        notifyCurrentStepFinished(CURRENT_STATE_PREPARE_URL);
    }

    private void prepareRuntimeDownloadConfig(int engineSupportInfoIndex) {
        mEngineSupportInfo = mCurrentCompatibility.getRuntimeCompatibility(engineSupportInfoIndex);
        LogWrapper.d(TAG, "prepareRuntimeDownloadConfig mEngineSupportInfo:" + mEngineSupportInfo + ", info index:" + engineSupportInfoIndex);
        mEngineType = mEngineSupportInfo.getEngine();
        mEngineVersion = mEngineSupportInfo.getEngineVersion();
        mRuntimeVersion = mEngineSupportInfo.getJavaLibraryInfo().getVersionName();
        notifyCurrentStepFinished(CURRENT_STATE_PREPARE_RUNTIME_DOWNLOAD_CONFIG);
    }

    private void updateLocalConfigFile() {
        if (isPreloadingGame()) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_LOCAL_CONFIG);
            return;
        }

        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            LogWrapper.w(TAG, "Did not find the available network, not request the remote game configuration file!");
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_LOCAL_CONFIG);
            return;
        }

        if (isExistConfigFile()) {
            if (isConfigUpdated()) {
                FileUtils.deleteFile(mPathRemoteConfig);
            } else {
                File f = new File(mPathRemoteConfig);
                if (f.exists()) {
                    replaceLocalConfigFile();
                }
            }
        } else {
            replaceLocalConfigFile();
        }

        notifyCurrentStepFinished(CURRENT_STATE_UPDATE_LOCAL_CONFIG);
    }

    private void updateEngineJAR() {
        if (RuntimeEnvironment.boolHostManageRuntime) {
            // 宿主管理 runtime 模式，跳过更新 runtime 等操作
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_JAR);
            return;
        }

        if (isRuntimeJarUpdated(mEngineSupportInfo.getJavaLibraryInfo())) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_JAR);
        } else {
            mEngineJARGipFilePath = getPath(mConfigInfo, RuntimeConstants.PATH_RUNTIME_JAR_GIP_FILE);
            mEngineJARGipTempFilePath = getPath(mConfigInfo, RuntimeConstants.PATH_RUNTIME_JAR_GIP_FILE_TEMP);

            if (isRuntimeJarGipFileExist()) {
                if (isRuntimeJarGipUpdated(mEngineSupportInfo.getJavaLibraryInfo())) {
                    loadRuntimeJar();
                } else {
                    FileUtils.deleteFile(mEngineJARGipFilePath);
                    FileUtils.deleteFile(mEngineJARGipTempFilePath);
                    fetchEngineJAR();
                }
            } else {
                fetchEngineJAR();
            }
        }
    }

    private void updateEngineCommonRes() {
        if (RuntimeEnvironment.boolHostManageRuntime) {
            // 宿主管理 runtime 模式，跳过更新引擎公共资源 操作
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_COMMON_RES);
            return;
        }
        mEngineCommonRes = new EngineCommonRes();
        mEngineCommonRes.init(mRuntimeCompInfo, mEngineType, mEngineVersion);

        if (!mEngineCommonRes.isNeedUpdateEngineCommonRes()) {
            LogWrapper.d(TAG, "don't need update common_res!");
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_COMMON_RES);
            return;
        }
        fetchEngineCommonRes();
    }

    private void fetchEngineCommonRes() {

        LogWrapper.i(TAG, "start update engine common res!");

        EngineCommonRes.OnCommonResUpdateListener lis = new EngineCommonRes.OnCommonResUpdateListener() {
            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                float percent = calculatePercent(downloadedSize, totalSize);
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_COMMON_RES, percent);
            }

            @Override
            public void onDownloadFailure(String errMsg) {
                errMsg = "Download failure! " + errMsg;
                if (mRetryTimes < RuntimeConstants.DOWNLOAD_RETRY_TIMES) {
                    LogWrapper.d(TAG, errMsg);
                    mRetryTimes++;
                    fetchEngineCommonRes();
                } else {
                    LogWrapper.e(TAG, errMsg);
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_COMMON_RES, errMsg);
                }
            }

            @Override
            public void onUnzipStart() {

            }

            @Override
            public void onUnzipProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_ENGINE_COMMON_RES, percent);
            }

            @Override
            public void onUnzipFailed(String errMsg) {
                if (errMsg.contains(RuntimeConstants.DOWNLOAD_ERROR_NO_SPACE_LEFT)) {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB, "No space left! unzip common_res failed");
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB, "unzip common_res  failed");
                }
            }

            @Override
            public void onSuccess() {
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_COMMON_RES);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return false;
            }
        };

        mEngineCommonRes.fetchEngineCommonResZip(lis);
    }


    private void updateRuntimeCompatibility() {
        // 宿主模式不需要获取兼容信息
        if (RuntimeEnvironment.boolHostManageRuntime) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_COMP);
            return;
        }

        if (Utils.getCurrAPNType() == Utils.NO_NETWORK && mConfigInfo != null && mConfigInfo.getGameType() == GameConfigInfo.TYPE_ONLINE) {
            String error = "updateRuntimeCompatibility:network access is a must for online game";
            LogWrapper.e(TAG, error);
            notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY, error);
            return;
        }

        if (RuntimeEnvironment.debugMode) {
            mEngineSupportInfo = mRuntimeCompInfo.getRuntimeCompatibility(mRunningGameInfo.mEngine, mRunningGameInfo.mEngineVersion);
            mRunningGameInfo.mEngineArch = Utils.getPhoneArch();
            RuntimeEnvironment.versionCurrentGameRuntime = mEngineSupportInfo.getJavaLibraryInfo().getVersionName();
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_COMP);
            return;
        }

        updateProgress(RuntimeConstants.LOADING_FETCH_RUNTIME_COMP, BEGIN_0);

        ProtocolController.downloadRuntimeCompatibility(String.valueOf(mRuntimeStub.getVersionCode()), Utils.getPhoneArch(), new ProtocolCallback<RuntimeCompatibilityInfo>() {
            @Override
            public void onSuccess(RuntimeCompatibilityInfo obj) {
                readAndSetRuntimeCompatibility(obj);
            }

            @Override
            public void onFailure(ResultInfo err) {
                LogWrapper.d(TAG, "updateRuntimeCompatibility onFailure msg: " + err.getMsg());
                if (mRetryTimes < RuntimeConstants.DOWNLOAD_RETRY_TIMES) {
                    mRetryTimes++;
                    updateRuntimeCompatibility();
                } else {
                    mCurrentCompatibility = RuntimeCompatibilityInfo.fromJson(FileUtils.readJsonObjectFromFile(FileConstants.getLocalRuntimeCompatibilityPath()));
                    if (mCurrentCompatibility != null && Utils.getCurrAPNType() == Utils.NO_NETWORK) {
                        readAndSetRuntimeCompatibility(mCurrentCompatibility);
                    } else {
                        final String error = "updateRuntimeCompatibility:get runtime compatibility failed!";
                        LogWrapper.e(TAG, error);
                        notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY, error);
                    }
                }
            }
        });
    }

    private void readAndSetRuntimeCompatibility(RuntimeCompatibilityInfo obj) {
        mRuntimeCompInfo = obj;
        mCurrentCompatibility = obj;
        if (!mIsPreDownloadRuntime) {
            mEngineSupportInfo = mRuntimeCompInfo.getRuntimeCompatibility(mRunningGameInfo.mEngine, mRunningGameInfo.mEngineVersion);
            if (mEngineSupportInfo == null) {
                Utils.showToast(mContext, "请配置兼容列表", 5000);
                LogWrapper.e(TAG, "Please config compatibility table on server first!");
                return;
            }
        }

        String arch = Utils.getPhoneArch();
        if (arch.equalsIgnoreCase(RuntimeConstants.ARCH_NOT_SUPPORTED)) {
            notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NOT_SUPPORT_ARCH, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY, "arch not supported");
            return;
        }

        RuntimeEnvironment.currentGameArch = arch;
        mRuntimeArch = arch;
        if (!mIsPreDownloadRuntime) {
            mRunningGameInfo.mEngineArch = arch;
            mRuntimeVersion = mEngineSupportInfo.getJavaLibraryInfo().getVersionName();
            RuntimeEnvironment.versionCurrentGameRuntime = mRuntimeVersion;
            ThreadUtils.runAsyncThread(new Runnable() {
                @Override
                public void run() {
                    // 删除掉旧版本 dex 文件
                    String dexPath = getOptimizedDexDir();
                    FileUtils.deleteSubFile(dexPath);
                }
            });
        }
        notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_COMP);
    }

    private void updateGameResourceConfig() {
        if (!isPreloadingGame() && Utils.getCurrAPNType() == Utils.NO_NETWORK) { // 网络无法访问的情况下读取本地配置文件
            File f = new File(mPathResourceConfig);
            String resConfigMd5 = mConfigInfo.getResourceConfigMetaInfo().md5;
            if (!f.exists()) {
                String error = "no network and local resource configuration not exist";
                LogWrapper.e(TAG, error);
                notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY, error);
            } else if (FileUtils.isFileModifiedByCompareMD5(mPathResourceConfig, resConfigMd5)) {
                String error = "no network and the md5 of local resource configuration is wrong";
                LogWrapper.e(TAG, error);
                notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY, error);
            } else {
                LogWrapper.w(TAG, "no network, using existing resource configuration file!");
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RESOURCE_CONFIG);
            }
            return;
        }

        if (isRemoteManifestUpdated()) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RESOURCE_CONFIG);
        } else {
            mRetryTimes = 0;
            fetchGameResourceConfig();
        }
    }

    private void updateEngineSharedLibrary() {
        mUpdateShareLibraryBeginTime = System.currentTimeMillis();
        LogWrapper.i(TAG, "time statistics, update share library begin:" + mUpdateShareLibraryBeginTime);

        if (RuntimeEnvironment.boolHostManageRuntime) {
            // 宿主管理 runtime 模式，跳过更新 runtime 等操作
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_SO);
            return;
        }

        mEngineSharedLibrary = new EngineStandardLibrary();
        mEngineSharedLibrary.init(mEngineType, mEngineVersion, mRuntimeArch, mEngineSupportInfo);
        fetchEngineSharedLibrary();
    }

    private void updateGameExtendJar() {
        LogWrapper.d(TAG, "updateGameExtendJar start!");
        if (null == mConfigInfo.getGameExtendInfo()) {
            LogWrapper.d(TAG, "The game not exit extend jar!");
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_EXTEND);
            return;
        }
        mGameExtendJAR = new GameExtendJAR();
        mGameExtendJAR.init(mRunningGameInfo, mConfigInfo);
        fetchGameExtendJar();
    }

    private void updateExtendLibraries() {
        ArrayList<GameConfigInfo.ExtendLibInfo> extendLibInfoArrayList = mConfigInfo.getExtendLibsInfo();
        if (extendLibInfoArrayList == null || extendLibInfoArrayList.isEmpty()) {
            LogWrapper.w(TAG, "don't need update extend library");
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_EXTEND_LIBRARIES);
            return;
        }

        mUpdateExtendLibsBeginTime = System.currentTimeMillis();
        mGameExtendLibrary = new GameExtendLibrary();
        mGameExtendLibrary.init(mRunningGameInfo, mConfigInfo);
        LogWrapper.d(TAG, "start update extend library");

        fetchExtendLibraries();
    }

    private void fetchExtendLibraries() {
        LogWrapper.w(TAG, "fetch extend library called!!!");
        updateProgress(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_LIBS, BEGIN_0);
        GameExtendLibrary.onRuntimeExtendLibrariesUpdateListener lis = new GameExtendLibrary.onRuntimeExtendLibrariesUpdateListener() {

            @Override
            public void onProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_LIBS, percent);
            }

            @Override
            public void onDownloadFailure(String errMsg) {
                errMsg = "Download failure! " + errMsg;
                if (mRetryTimes < RuntimeConstants.DOWNLOAD_RETRY_TIMES) {
                    LogWrapper.w(TAG, errMsg);
                    mRetryTimes++;
                    fetchExtendLibraries();
                } else {
                    LogWrapper.e(TAG, errMsg);
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB, errMsg);
                }
            }

            @Override
            public void onUnzipStart() {
            }

            @Override
            public void onUnzipProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_GAME_EXTEND_LIBS, percent);
            }

            @Override
            public void onUnzipFailed(String errMsg) {
                if (errMsg.contains(RuntimeConstants.DOWNLOAD_ERROR_NO_SPACE_LEFT)) {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB, "No space left! unzip extend_libraries archive failed");
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB, "unzip extend_libraries archive failed");
                }
            }

            @Override
            public void onSuccess() {
                if (mGameExtendLibrary.copyFileToExtendSharedLibraryDir()) {
                    notifyCurrentStepFinished(CURRENT_STATE_UPDATE_EXTEND_LIBRARIES);
                }
            }

            @Override
            public boolean isUnzipInterrupted() {
                return false;
            }
        };

        mGameExtendLibrary.updateExtendLibs(lis);
    }

    private void updateBootScene() {
        mUpdateBootBeginTime = System.currentTimeMillis();
        LogWrapper.i(TAG, "time statistics, update boot resource begin:" + mUpdateBootBeginTime);

        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            LogWrapper.w(TAG, "Did not find the available network, try to using exist boot group resources!");
            final RuntimeLocalRecord runtimeLocalRecord = RuntimeLocalRecord.getInstance();
            if (mConfigInfo.getGameType() == GameConfigInfo.TYPE_OFFLINE
                    && runtimeLocalRecord.isDownloadedGroupInfoContained("boot")
                    && mRuntimeGroup != null && mRuntimeGroup.findGroup("boot").isCompletedGroup()) {
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_BOOT_RES);
            } else {
                recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE,
                        "no network and boot group not exist");
            }
            return;
        }

        if (mRuntimeStub.getRuntimeProxy() != null) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("download_type", "game");
            } catch (JSONException ignored) {
            }
            mRuntimeStub.getRuntimeProxy().onMessage(jsonObject.toString());
        }

        final RuntimeScene bootScene = RuntimeScene.getRuntimeScene(BOOT_SCENE_KEY);

        if (bootScene == null) {
            String error = "RuntimeLauncher can't load boot_scene!!";
            LogWrapper.e(TAG, error);
            recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, error);
            return;
        }

        LogWrapper.i(TAG, "begin check ...");
        List<String> groups = bootScene.calculateLackOfGroups(isPreloadingGame());
        if (groups.isEmpty()) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_BOOT_RES);
            return;
        }

        final String bootGroupName = groups.get(0);
        final RuntimeLocalRecord runtimeLocalRecord = RuntimeLocalRecord.getInstance();

        LogWrapper.i(TAG, "local record contain : " + bootGroupName);
        if (runtimeLocalRecord.isDownloadedGroupInfoContained(bootGroupName)) {
            // 已经是完整包
            if (mRuntimeGroup.findGroup(bootGroupName).isCompletedGroup()) {
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_BOOT_RES);
                return;
            }
        }

        /*
        * Modify the boot scene info
        * */
        runtimeLocalRecord.setSceneModified(bootGroupName, true);
        if (!isPreloadingGame()) {
            runtimeLocalRecord.persis();
        }

        LogWrapper.d(TAG, "begin boot scene updating ...");

        final RuntimeGroup.OnUpdateGroupListener lis = new RuntimeGroup.OnUpdateGroupListener() {

            private long mDownBootFinishTime;

            @Override
            public void onStartOfDownload() {
            }

            @Override
            public void onProgressOfDownload(long bytesWritten, long totalSize) {
                float percent = calculatePercent(bytesWritten, totalSize);
                updateProgress(RuntimeConstants.LOADING_FETCH_BOOT_GROUP, percent);
            }

            @Override
            public void onSuccessOfDownload(long totalSize) {
                mDownBootFinishTime = System.currentTimeMillis();
                LogWrapper.i(TAG, "time statistics,download boot:" + (mDownBootFinishTime - mUpdateBootBeginTime) + "ms");
                updateProgress(RuntimeConstants.LOADING_FETCH_BOOT_GROUP, FINISHED_100);
                postCurrStateToChannel(RuntimeConstants.LOADING_FETCH_BOOT_GROUP);

                if (isPreloadingGame()) {
                    // 添加下载成功的资源到列表
                    runtimeLocalRecord.recordDownloadedGroupInfo(bootGroupName);
                    runtimeLocalRecord.updateSceneVersionToNewest(bootScene.getSceneInfo());
                    notifyCurrentStepFinished(CURRENT_STATE_UPDATE_BOOT_RES);
                }
            }

            @Override
            public void onFailureOfDownload(String errorMsg) {
                LogWrapper.i(TAG, "time statistics,download boot failed:" + (System.currentTimeMillis() - mUpdateBootBeginTime) + "ms");

                if (errorMsg.contains(RuntimeConstants.DOWNLOAD_VERIFY_WRONG)) {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                } else if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                } else {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                }
            }

            @Override
            public void onSuccessOfUnzip() {
                LogWrapper.i(TAG, "time statistics, the time between boot download finish and unzip finish:" + (System.currentTimeMillis() - mDownBootFinishTime) + "ms");

                // 添加下载成功的资源到列表
                runtimeLocalRecord.recordDownloadedGroupInfo(bootGroupName);
                runtimeLocalRecord.updateSceneVersionToNewest(bootScene.getSceneInfo());
                runtimeLocalRecord.persis();
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_BOOT_RES);
                postCurrStateToChannel(RuntimeConstants.LOADING_UNZIP_BOOT_GROUP);
            }

            @Override
            public void onFailureOfUnzip(String errorMsg) {
                LogWrapper.i(TAG, "time statistics, the time between boot download finish and unzip failed:" + (System.currentTimeMillis() - mDownBootFinishTime) + "ms");
                if (errorMsg.contains(RuntimeConstants.DOWNLOAD_VERIFY_WRONG)) {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                } else if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                } else {
                    recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, errorMsg);
                }
            }

            @Override
            public void onProgressOfUnzip(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_BOOT_GROUP, percent);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return mIsLoadingCanceled;
            }
        };

        if (mIsLoadingCanceled) {
            recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_CANCLE_PRELOADING,
                    RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_PATCH, "Cancel loading boot scene !");
            return;
        }

        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int retryTimes = (isPreloadingGame()) ? PRELOAD_BOOT_SCENE_RETRY_TIMES : UPDATE_BOOT_SCENE_RETRY_TIMES;
                mRuntimeGroup.updateGroup(bootGroupName, !isPreloadingGame(), retryTimes, lis);
            }
        });
    }

    private void fetchEngineSharedLibrary() {
        if (mEngineSharedLibrary.isLibrariesUpdated()) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_SO);
            return;
        }

        updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_SO, BEGIN_0);
        EngineStandardLibrary.OnShareLibraryUpdateListener lis = new EngineStandardLibrary.OnShareLibraryUpdateListener() {

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                float percent = calculatePercent(downloadedSize, totalSize);
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_SO, percent);
            }

            @Override
            public void onFailure(int errorType, String errorMsg) {
                recordParallelFileDownloadFailed(THREAD_LOAD_RUNTIME_SO, errorType, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_SO, errorMsg);
            }

            @Override
            public void onUnzipProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_ENGINE_SO, percent);
            }

            @Override
            public void onSuccess() {
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_SO);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return false;
            }
        };

        mEngineSharedLibrary.updateLibraries(lis);
    }

    private void fetchGameExtendJar() {

        if (!isPreloadingGame() && mGameExtendJAR.isGameExtendFileBelongToCurrentGame()) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_EXTEND);
            return;
        }

        GameExtendJAR.OnRuntimeGameExtendUpdateListener lis = new GameExtendJAR.OnRuntimeGameExtendUpdateListener() {

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                float percent = calculatePercent(downloadedSize, totalSize);
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_JAR, percent);
            }

            @Override
            public void onFailure(int errorType, String errorMsg) {
                notifyFileDownloadFailed(errorType, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_EXTEND_JAR, errorMsg);
            }

            @Override
            public void onSuccess() {
                getInstance().postCurrStateToChannel(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_JAR);
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_EXTEND);
            }
        };

        mGameExtendJAR.updateGameExtendJarFile(isPreloadingGame(), lis);
    }


    private void updateDiffPatchSo() {
        if (RuntimeEnvironment.boolHostManageRuntime) {
            // 宿主管理 runtime 模式，跳过更新 patch动态库 操作
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_DIFFPATCH_SO);
            return;
        }

        mRuntimeDiffPatch = new RuntimeDiffPatchFile();
        mRuntimeDiffPatch.init(mCurrentCompatibility);

        if (mRuntimeDiffPatch.isDiffPatchShareLibraryUpdated()) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_DIFFPATCH_SO);
            return;
        }

        fetchDiffPatchSo();
    }

    private void fetchDiffPatchSo() {
        if (mRuntimeDiffPatch.isDiffPatchShareLibraryUpdated()) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_DIFFPATCH_SO);
            return;
        }

        RuntimeDiffPatchFile.OnDiffPatchUpdateListener lis = new RuntimeDiffPatchFile.OnDiffPatchUpdateListener() {
            @Override
            public void onDownloadProgress(long downloadedSize, long totalSize) {
            }

            @Override
            public void onUnzipProgress(float percent) {
            }

            @Override
            public void onFailed(int errorType, String errorMsg) {
                recordParallelFileDownloadFailed(THREAD_LOAD_RUNTIME_SO, errorType,
                        RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_DIFF_PATCH_SO, errorMsg);
            }

            @Override
            public void onSuccess() {
                LogWrapper.d(TAG, "FetchDiffPatchSo onSuccess ...");
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_DIFFPATCH_SO);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return mIsLoadingCanceled;
            }

            @Override
            public void onUnzipStart() {
            }
        };

        mRuntimeDiffPatch.updateDiffPatchSo(lis);
    }

    private void notifyPreDownloadRuntimeStepFinished(int curState) {
        if (mIsLoadingCanceled) {
            return;
        }

        switch (curState) {
            case CURRENT_STATE_PREPARE_URL:
                updateRuntimeCompatibility();
                break;
            case CURRENT_STATE_UPDATE_RUNTIME_COMP:
                initLoadingProgressController();
                prepareRuntimeDownloadConfig(mEngineSupportInfoIndex);
                break;
            case CURRENT_STATE_PREPARE_RUNTIME_DOWNLOAD_CONFIG:
                updateEngineJAR();
                break;
            case CURRENT_STATE_FETCH_RUNTIME_JAR:
                loadRuntimeJar();
                break;
            case CURRENT_STATE_UPDATE_RUNTIME_JAR:
                mRetryTimes = 0;
                updateDiffPatchSo();
                break;
            case CURRENT_STATE_FETCH_DIFFPATCH_SO:
                loadDiffPatchLibrary();
                mRetryTimes = 0;
                updateEngineCommonRes();
                break;
            case CURRENT_STATE_UPDATE_COMMON_RES:
                mRetryTimes = 0;
                updateEngineSharedLibrary();
                break;
            case CURRENT_STATE_FETCH_RUNTIME_SO:
                mRetryTimes = 0;
                mEngineSupportInfoIndex = mEngineSupportInfoIndex + 1;
                RuntimeEngineSupportInfo supportInfo = mCurrentCompatibility.getRuntimeCompatibility(mEngineSupportInfoIndex);
                if (null != supportInfo) {
                    prepareRuntimeDownloadConfig(mEngineSupportInfoIndex);
                } else {
                    if (mRuntimeStub.getPreDownloadProxy() != null) {
                        mRuntimeStub.getPreDownloadProxy().onDownloadSuccess();
                    }
                    getLoadingProgressController().reset();
                    reset();
                }
                break;
            default:
                LogWrapper.e(TAG, "Invalid current state in notifyPreDownloadRuntimeStepFinished!");
                break;
        }
    }

    private void notifyCurrentStepFinished(int curState) {
        LogWrapper.d(TAG, "Notify current step( " + curState + " ) is Finished, is preload game(" + isPreloadingGame() + ") , is cancel (" + mIsLoadingCanceled + ")");
        if (mIsPreDownloadRuntime) {
            notifyPreDownloadRuntimeStepFinished(curState);
            return;
        }

        if (mIsLoadingCanceled) {
            if (isPreloadingGame()) {
                if (!startParallelGameResLoading) {
                    mPreloadRuntimeResListener.onCancel();
                } else {
                    recordParallelFileDownloadFailed(THREAD_LOAD_RUNTIME_SO, RuntimeConstants.MODE_TYPE_CANCLE_PRELOADING,
                            RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_PATCH, "Cancel loading runtime so !");
                }
                mIsPreloadingGame = false;
            }
            return;
        }

        switch (curState) {
            case CURRENT_STATE_PREPARE_URL:
                startParallelGameResLoading = false;
                mRetryTimes = 0;
                fetchRemoteConfigFile();
                break;
            case CURRENT_STATE_FETCH_REMOTE_CONFIG:
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_CONFIG, FINISHED_100);
                postCurrStateToChannel(RuntimeConstants.LOADING_FETCH_GAME_CONFIG);
                updateLocalConfigFile();
                break;
            case CURRENT_STATE_UPDATE_LOCAL_CONFIG:
                updateRuntimeCompatibility();
                break;
            case CURRENT_STATE_UPDATE_RUNTIME_COMP:
                updateProgress(RuntimeConstants.LOADING_FETCH_RUNTIME_COMP, FINISHED_100);
                initLoadingProgressController();
                makeLocalResRecordCompatibility();
                break;
            case CURRENT_STATE_LOCAL_RES_RECORD_COMPATIBLE:
                updateProgress(RuntimeConstants.LOADING_COMPATIBLE_LOCAL_RES_RECORD, FINISHED_100);
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_RESOURCES_CONFIG, BEGIN_0);
                updateGameResourceConfig();
                break;
            case CURRENT_STATE_UPDATE_RESOURCE_CONFIG:
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_RESOURCES_CONFIG, FINISHED_100);
                updateLocalRecordConfig();
                break;
            case CURRENT_STATE_REFRESH_LOCAL_RECORD:
                mRetryTimes = 0;
                updateEngineJAR();
                break;
            case CURRENT_STATE_FETCH_RUNTIME_JAR:
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_JAR, FINISHED_100);
                loadRuntimeJar();
                break;
            case CURRENT_STATE_UPDATE_RUNTIME_JAR:
                updateProgress(RuntimeConstants.LOADING_LOAD_ENGINE_JAR, FINISHED_100);
                mRetryTimes = 0;
                updateGameExtendJar();
                break;
            case CURRENT_STATE_FETCH_GAME_EXTEND:
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_EXTEND_JAR, FINISHED_100);
                mRetryTimes = 0;
                postCurrStateToChannel(RuntimeConstants.LOADING_DELETE_NO_REF_GROUPS);
                if (!isPreloadingGame()) {
                    // 异步删除无相关文件
                    startLowPriorityDeleteNoRefGroups();
                }
                notifyCurrentStepFinished(CURRENT_STATE_DELETE_NO_REF_GROUPS);
                break;
            case CURRENT_STATE_DELETE_NO_REF_GROUPS:
                updateProgress(RuntimeConstants.LOADING_DELETE_NO_REF_GROUPS, FINISHED_100);
                updateDiffPatchSo();
                break;
            case CURRENT_STATE_FETCH_DIFFPATCH_SO:
                updateProgress(RuntimeConstants.LOADING_FETCH_PATCH_SO, FINISHED_100);

                loadDiffPatchLibrary();
                mRetryTimes = 0;
                updateEngineCommonRes();
                break;
            case CURRENT_STATE_UPDATE_COMMON_RES:
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_COMMON_RES, FINISHED_100);
                startParallelGameResLoading = true;
                /*
                * 将下载 boot 场景，与 Runtime So 加载并行处理。
                * */
                startParallelGameResLoading();
                break;
            case CURRENT_STATE_UPDATE_SHARE_LIBRARIES:
                updateUnityDLL();
                break;
            case CURRENT_STATE_UPDATE_DLL:
                completedRuntimeRoutes();
                break;
            case CURRENT_STATE_FETCH_RUNTIME_SO:
                mUpdateShareLibraryFinishTime = System.currentTimeMillis();
                LogWrapper.i(TAG, "time statistics,finish update share library:" + (mUpdateShareLibraryFinishTime - mUpdateShareLibraryBeginTime) + "ms(total time)");
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_SO, FINISHED_100);
                updateProgress(RuntimeConstants.LOADING_UNZIP_ENGINE_SO, FINISHED_100);
                mRetryTimes = 0;
                updateGamePatch();
                break;
            case CURRENT_STATE_FETCH_GAME_PATCH_SO:
                mUpdatePatchFinishTime = System.currentTimeMillis();
                LogWrapper.i(TAG, "time statistics,finish update share library patch:" + (mUpdatePatchFinishTime - mUpdatePatchBeginTime) + "ms(total time)");

                if (mConfigInfo.isCocosGame()) {
                    updateProgress(RuntimeConstants.LOADING_COMPOUND_GAME_SO, FINISHED_100);
                }
                mRetryTimes = 0;
                updateExtendLibraries();
                break;
            case CURRENT_STATE_UPDATE_EXTEND_LIBRARIES:
                mUpdateExtendLibsFinishedTime = System.currentTimeMillis();
                LogWrapper.i(TAG, "time statistics,finish update extend libs :" + (mUpdateExtendLibsFinishedTime - mUpdateExtendLibsBeginTime) + "ms(total time)");
                completedRuntimeRoutes();
                break;
            case CURRENT_STATE_UPDATE_BOOT_RES:
                mUpdateBootFinishTime = System.currentTimeMillis();
                LogWrapper.i(TAG, "time statistics,finish update boot resource:" + (mUpdateBootFinishTime - mUpdateBootBeginTime) + "ms(total time)");

                updateProgress(RuntimeConstants.LOADING_UNZIP_BOOT_GROUP, FINISHED_100);

                completedRuntimeRoutes();
                break;
            default:
                LogWrapper.e(TAG, "Invalid current state in notifyCurrentStepFinished!");
                break;
        }
    }

    // 完成并行路线后，通知异常或开启游戏。
    private void completedRuntimeRoutes() {
        int routes = mParallelRunningRoutes.decrementAndGet();
        if (routes != 0) {
            // 并行未完成
            return;
        }
        if (recordErrorType >= 0) {
            notifyFileDownloadFailed(recordErrorType, recordErrorStep, recordErrorMsg);
            return;
        }

        ThreadUtils.runAsyncThread(new Runnable() {
            @Override
            public void run() {

                getLoadingProgressController().reset();

                // 预加载 Runtime 资源完成。
                if (isPreloadingGame()) {
                    // 将加载到的资源复制到游戏目录。
                    copyPreloadResToGameDir();
                    // 预加载游戏首包成功
                    ThreadUtils.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            processPreloadGameFinished();
                        }
                    });
                    return;
                }

                ThreadUtils.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        long currTime = System.currentTimeMillis();
                        LogWrapper.i(TAG, "time statistics, the time between boot update finish and start game:" + (currTime - mUpdateBootFinishTime) + "ms");
                        LogWrapper.i(TAG, "time statistics, the time between patch update finish and start game:" + (currTime - mUpdatePatchFinishTime) + "ms");
                        startGame();
                    }
                });
            }
        });
    }

    private void processPreloadGameFinished() {
        mIsPreloadingGame = false;
        if (mPreloadRuntimeResListener != null) {
            mPreloadRuntimeResListener.onSuccess();
            mPreloadRuntimeResListener = null;
        }
        reset();
    }

    private void startParallelGameResLoading() {
        recordErrorType = -1;
        mRetryTimes = 0;

        // 并行开始
        int parallel = mParallelRunningRoutes.incrementAndGet();
        LogWrapper.v(TAG, "Parallel incrementAndGet( " + parallel + ")");

        if (mConfigInfo.isUnityGame()) {
            parallel = mParallelRunningRoutes.incrementAndGet();
            LogWrapper.v(TAG, "Parallel incrementAndGet( " + parallel + ")");
            ThreadUtils.runAsyncThread(new Runnable() {
                @Override
                public void run() {
                    updateUnityShareLibraries();
                }
            });
        }
        parallel = mParallelRunningRoutes.incrementAndGet();
        LogWrapper.v(TAG, "Parallel incrementAndGet( " + parallel + ")");
        updateEngineSharedLibrary();

        /*
        * Begin loading boot scene
        * */
        parallel = mParallelRunningRoutes.incrementAndGet();
        LogWrapper.v(TAG, "Parallel incrementAndGet( " + parallel + ")");
        ThreadUtils.runAsyncThread(new Runnable() {
            @Override
            public void run() {
                updateBootScene();
            }
        });

        completedRuntimeRoutes();
    }

    private void updateUnityShareLibraries() {
        String tmpDir = getRuntimePath(RuntimeConstants.PATH_DOWNLOAD_SHARE_LIBRARY_DIR);
        String destDir = getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_SHARELIBRARY_DIR);
        String gipFile = "unity_share_libraries_zip.gip";
        String downloadTag = RuntimeConstants.DOWNLOAD_TAG_SHARED_LIBRARIES;
        ResourcesZipUpdater resZipUpdater = ResourcesZipUpdater.createUpdater(mEngineSupportInfo.getShareLibraries(), tmpDir, destDir);

        if (resZipUpdater.isResourcesIntegrity()) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_SHARE_LIBRARIES);
            return;
        }

        resZipUpdater.updateResources(gipFile, downloadTag, new ResourcesZipUpdater.ResourcesUpdateListener() {
            @Override
            public void onSuccess() {
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_SHARE_LIBRARIES);
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                recordParallelFileDownloadFailed(THREAD_LOAD_UNITY_RES, code, RuntimeConstants.ERROR_OCCURRED_STEP_UNITY_SHARD_LIB, errorMsg);
            }

            @Override
            public void onDownloadProgress(long downloadedSize, long totalSize) {
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_EXTEND_LIBS, calculatePercent(downloadedSize, totalSize));
            }

            @Override
            public void onUnzipProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_ENGINE_EXTEND_LIBS, percent);
            }
        });
    }

    private void updateUnityDLL() {
        String tmpDir = getRuntimePath(RuntimeConstants.PATH_DOWNLOAD_DLL_DIR);
        String destDir = getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_DLL_DIR);
        String gipFile = "unity_dll_zip.gip";
        String downloadTag = RuntimeConstants.DOWNLOAD_TAG_DLL;
        ResourcesZipUpdater resZipUpdater = ResourcesZipUpdater.createUpdater(mEngineSupportInfo.getDynamicLinkLibraries(), tmpDir, destDir);

        if (resZipUpdater.isResourcesIntegrity()) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_DLL);
            return;
        }

        resZipUpdater.updateResources(gipFile, downloadTag, new ResourcesZipUpdater.ResourcesUpdateListener() {
            @Override
            public void onSuccess() {
                notifyCurrentStepFinished(CURRENT_STATE_UPDATE_DLL);
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                recordParallelFileDownloadFailed(THREAD_LOAD_UNITY_RES, code, RuntimeConstants.ERROR_OCCURRED_STEP_UNITY_DLL, errorMsg);
            }

            @Override
            public void onDownloadProgress(long downloadedSize, long totalSize) {
                updateProgress(RuntimeConstants.LOADING_FETCH_ENGINE_EXTEND_DLL, calculatePercent(downloadedSize, totalSize));
            }

            @Override
            public void onUnzipProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_UNZIP_ENGINE_EXTEND_DLL, percent);
            }
        });
    }

    private void updateLocalRecordConfig() {
        updateProgress(RuntimeConstants.LOADING_REFRESH_RECORD_CONFIG, BEGIN_0);
        String manifestPath = (isPreloadingGame()) ? mPathPreloadGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME
                : FileConstants.getResourceConfigPath(mRunningGamePackageName);

        LogWrapper.d(TAG, "Update local record config, manifestPath : " + manifestPath);
        JSONObject jsonResourceConfig = FileUtils.readJsonObjectFromFile(manifestPath);
        mResourceConfigInfo = GameResourceConfigInfo.fromJson(jsonResourceConfig);

        // 更新本地记录
        RuntimeLocalRecord.getInstance().updateLocalVersionInfo(mResourceConfigInfo);
        if (!isPreloadingGame()) {
            RuntimeLocalRecord.getInstance().persis();
        }
        mRuntimeGroup = RuntimeGroup.getInstance();
        mRuntimeGroup.init(mRunningGamePackageName, mRunningGameInfo.mDownloadUrl);
        notifyCurrentStepFinished(CURRENT_STATE_REFRESH_LOCAL_RECORD);
    }

    /**
     * 兼容旧版本的本地场景资源记录文件
     */
    private void makeLocalResRecordCompatibility() {
        final RuntimeLocalRecord localRecordConfig = RuntimeLocalRecord.getInstance();

        List<LocalSceneInfo> recordScenes = localRecordConfig.getRecordScenes();
        LogWrapper.d(TAG, "MakeResRecordCom recordSceneSize " + recordScenes.size());

        /*
        * 一、本地无任何场景信息，或者已经是最新的配置信息。
        * */
        if (recordScenes.isEmpty() || !recordScenes.get(0).isOldConfigStatus()) {
            LogWrapper.d(TAG, "MakeResRecordCom no local scene info , Stop !!");
            notifyCurrentStepFinished(CURRENT_STATE_LOCAL_RES_RECORD_COMPATIBLE);
            return;
        }

        /*
        * 二、依次更新本地的场景版本。
        * */
        // 获取到本地历史遗留的远程场景配置信息。
        String pkgPath = FileConstants.getResourceConfigPath(mRunningGamePackageName);
        JSONObject jsonResLegacyConfig = FileUtils.readJsonObjectFromFile(pkgPath);
        mResourceConfigInfo = GameResourceConfigInfo.fromJson(jsonResLegacyConfig);
        LogWrapper.d(TAG, "MakeResRecordCom Local Legacy config path : " + mResourceConfigInfo.toString());

        // 无本地历史遗留的远程场景配置信息, 或远程配置信息为空。
        if (mResourceConfigInfo == null || mResourceConfigInfo.getAllSceneInfos().isEmpty()) {
            LogWrapper.d(TAG, "MakeResRecordCom Local Legacy config:" + mResourceConfigInfo);
            if (mResourceConfigInfo != null) {
                LogWrapper.d(TAG, "MakeResRecordCom all sceneinfos is empty:" + mResourceConfigInfo.getAllSceneInfos().isEmpty());
            }

            for (LocalSceneInfo localSceneInfo : recordScenes) {
                updateLocalSceneInfoCompatibility(localSceneInfo, true);
            }
        } else {
            SceneInfo sceneInfo = mResourceConfigInfo.getAllSceneInfos().get(0);
            LogWrapper.d(TAG, "MakeResRecordCom sceneinfo verison :  " + sceneInfo.getVersion());
            if (sceneInfo.getVersion() == SceneInfo.DEFAULT_SCENE_VERSION) {
                // 本地历史遗留的远程场景配置信息为旧版。
                for (LocalSceneInfo localSceneInfo : recordScenes) {
                    int remoteGameVersion = mResourceConfigInfo.getVersionCode();
                    final SceneInfo remoteConfigSceneInfo = mResourceConfigInfo.getSceneByName(localSceneInfo.getName());
                        /*
                        * 判断场景是否未被修改需要满足三个条件
                        * 1、场景版本号是否与本地的远程配置文件的游戏版本号相同
                        * 2、是否所有分组都记录在下载列表中
                        * 3、是否所有分组文件都完整
                        * */
                    if (localSceneInfo.getVerison() == remoteGameVersion
                            && localRecordConfig.allGroupOfSceneExistInDownloadList(remoteConfigSceneInfo)
                            && remoteConfigSceneInfo.isCompletedScenes()) {
                        updateLocalSceneInfoCompatibility(localSceneInfo, false);
                    } else {
                        updateLocalSceneInfoCompatibility(localSceneInfo, true);
                    }
                }
            } else {
                // 本地历史遗留的远程场景配置信息为新版。
                for (LocalSceneInfo localSceneInfo : recordScenes) {
                    updateLocalSceneInfoCompatibility(localSceneInfo, true);
                }
            }
        }

        /*
        * 三、更新本地 FromVersion 到场景版本。
        * */
        if (localRecordConfig.getFromVersion() == RuntimeLocalRecord.DEFAULT_VERSION) {
            LogWrapper.d(TAG, "MakeResRecordCom update fromVersion to v" + localRecordConfig.getGameVersion());
            localRecordConfig.updateFromVersion(localRecordConfig.getGameVersion());
        }

        /* 持久化兼容信息 */
        localRecordConfig.persis();
        LogWrapper.d(TAG, "MakeResRecordCom after record compatibility : " + localRecordConfig.toString());
        notifyCurrentStepFinished(CURRENT_STATE_LOCAL_RES_RECORD_COMPATIBLE);
    }

    /**
     * 更新本地场景信息兼容性
     *
     * @param localSceneInfo 本地的场景信息
     * @param modified       本地场景信息是否是完整态
     */
    private void updateLocalSceneInfoCompatibility(LocalSceneInfo localSceneInfo, boolean modified) {
        if (modified) {
            localSceneInfo.setModified();
        } else {
            localSceneInfo.setUnModified();
        }
        localSceneInfo.updateGameVerison(localSceneInfo.getVerison());
        localSceneInfo.updateVersion(LocalSceneInfo.DEFAULT_VERSION);
    }

    static boolean s_loadPatchLibrary = true;

    private void loadDiffPatchLibrary() {
        if (s_loadPatchLibrary) {
            s_loadPatchLibrary = false;
            System.load(getDiffPatchShareLibraryPath());
        }
    }

    private String getRuntimePath(String key) {
        return getPath(null, key);
    }

    private String getPath(GameConfigInfo config, String key) {
        String path;

        String pathCurrentGameDir;
        String engineVersion;
        String engineType;

        if (config == null) {
            pathCurrentGameDir = "";
            engineVersion = mEngineVersion;
            engineType = mEngineType;
        } else {
            pathCurrentGameDir = FileConstants.getGameRootDir(config.getPackageName());
            engineVersion = config.getEngineVersion();
            engineType = config.getEngine();
        }

        String pathHostRuntimeEngineVersionDir = RuntimeEnvironment.pathHostRuntimeDir + "engine" + File.separator
                + engineType + File.separator + engineVersion + File.separator;
        String pathSDCardRuntimeLibVersionDir = FileConstants.getDownloadDir();

        switch (key) {
            case RuntimeConstants.PATH_SD_CARD_RUNTIME_LIB_VERSION_DIR:
                path = pathSDCardRuntimeLibVersionDir;
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_DIR:
                path = RuntimeEnvironment.pathHostRuntimeDir;
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_DIR:
                path = pathHostRuntimeEngineVersionDir;
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_COMMON_RES_DIR:
                path = pathHostRuntimeEngineVersionDir + "common_res/";
                break;
            case RuntimeConstants.PATH_RUNTIME_JAR_GIP_FILE:
                return pathSDCardRuntimeLibVersionDir + RuntimeConstants.RUNTIME_FILE_JAR_NAME;
            case RuntimeConstants.PATH_RUNTIME_JAR_GIP_FILE_TEMP:
                return pathSDCardRuntimeLibVersionDir + RuntimeConstants.RUNTIME_FILE_JAR_NAME_TEMP;
            case RuntimeConstants.PATH_DOWNLOAD_DLL_DIR:
                return pathSDCardRuntimeLibVersionDir + File.separator
                        + engineType + File.separator + engineVersion + File.separator + "dll/";
            case RuntimeConstants.PATH_DOWNLOAD_SHARE_LIBRARY_DIR:
                return pathSDCardRuntimeLibVersionDir + File.separator
                        + engineType + File.separator + engineVersion + File.separator + "sharelibrary/";
            case RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_JAVA_LIBRARY_DIR:
                path = pathHostRuntimeEngineVersionDir + "javalibrary/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_SHARELIBRARY_DIR:
                path = pathHostRuntimeEngineVersionDir + "sharelibrary/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_DLL_DIR:
                path = pathHostRuntimeEngineVersionDir + "dll/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_RESOURCE_DIR:
                path = pathHostRuntimeEngineVersionDir + "resource/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_PATCH_SO_DIR:
                path = RuntimeEnvironment.pathHostRuntimeDir + "diffpatch/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_DEX_DIR:
                path = RuntimeEnvironment.pathHostRuntimeDir + "dex/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_GAME_DIR:
                path = RuntimeEnvironment.pathHostRuntimeDir + "game/";
                break;
            case RuntimeConstants.PATH_HOST_GAME_EXTEND_DIR:
                path = RuntimeEnvironment.pathHostRuntimeDir + "extend/";
                break;
            case RuntimeConstants.PATH_HOST_RUNTIME_EXTEND_LIBRARIES:
                path = RuntimeEnvironment.pathHostRuntimeDir + "extend_libs/";
                break;
            case RuntimeConstants.PATH_CURRENT_GAME_DIR:
                path = pathCurrentGameDir;
                break;
            case RuntimeConstants.PATH_CURRENT_GAME_PATCH_DIR:
                path = pathCurrentGameDir;
                break;
            case RuntimeConstants.PATH_CURRENT_GAME_GROUP_DIR:
                path = pathCurrentGameDir + "group/";
                break;
            case RuntimeConstants.PATH_RESOURCE_CONFIG:
                return FileConstants.getResourceConfigPath(mRunningGamePackageName);
            case RuntimeConstants.PATH_RESOURCE_CONFIG_TEMP:
                return pathCurrentGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME_TEMP;
            case RuntimeConstants.PATH_RESOURCE_CONFIG_GIP:
                return pathCurrentGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME_GIP;
            case RuntimeConstants.PATH_LOCAL_CONFIG:
                return pathCurrentGameDir + RuntimeConstants.CONFIG_FILE_NAME;
            default:
                LogWrapper.e(TAG, "unknown key for getPath");
                return null;
        }

        FileUtils.ensureDirExists(path);
        return path;
    }

    public boolean offlineGameResExpired() {
        // 联网游戏或还未进入游戏，不去检查资源是否过期
        if (!mIsOfflineGame || !mGameIsStart) {
            return false;
        }

        // 无网络环境，不去检查资源是否过期
        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            return false;
        }

        String markerPath = FileConstants.getOfflineGameMarkerPath(mRunningGamePackageName);
        // 检查到更新标志文件，确认资源已经过期。
        return FileUtils.isExist(markerPath);
    }

    void startOfflineGameUpdateChecker() {
        if (!mIsOfflineGame) {
            return;
        }

        mGameUpdateCheckRetryTimes = 0;
        mRunningSinglePlayerChecker = true;
        runningOfflineGameUpdateChecker();
    }

    private void stopOfflineGameUpdateChecker() {
        mRunningSinglePlayerChecker = false;
        if (mOfflineConfigDownloader != null) {
            mOfflineConfigDownloader.cancel();
            mOfflineConfigDownloader = null;
        }
    }

    private FileDownloadHelper.FileDownloader mOfflineConfigDownloader;
    private FileDownloadHelper.FileDownloader mFileDownloader;
    /**
     * 检查服务端是否有最新版的单机游戏配置文件(gplay_game_config.json)并下载。
     * 下载完成与本地游戏配置文件不一致则转存为 /game_pkg/single_play_update.mark 通过检查此文件来确定更新单机游戏。
     */
    private void runningOfflineGameUpdateChecker() {
        if (!mRunningSinglePlayerChecker || mRunningGameInfo == null || !mGameIsStart) {
            LogWrapper.w(TAG, "runningOfflineGameUpdateChecker, something is wrong. mRunningGameInfo:" + mRunningGameInfo + ", mGameIsStart:" + mGameIsStart);
            return;
        }

        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            ThreadUtils.runOnUIThread(mOfflineGameUpdateChecker, OFFLINE_GAME_CHECK_FILED_TIME_INTERVAL);
            return;
        }

        final String configPath = FileConstants.getTempDir() + RuntimeConstants.DOWNLOAD_TAG_NEWEST_CONFIG_JSON;
        FileUtils.deleteFile(configPath);

        // gplay_game_config.json
        final String url = mRunningGameInfo.mDownloadUrlOfLatestVersion + RuntimeConstants.CONFIG_FILE_NAME;
        mOfflineConfigDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_NEWEST_CONFIG_JSON, url, configPath, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {}

            @Override
            public void onSuccess(File file) {
                LogWrapper.d(TAG, "runningOfflineGameUpdateChecker: download succeed.");

                boolean isGameUpdated = false;
                JSONObject newGameConfig = FileUtils.readJsonFile(file);
                if (newGameConfig != null) {
                    isGameUpdated = GameConfigInfo.isNewGameVersion(mConfigInfo, newGameConfig);
                    if (isGameUpdated) {
                        String path = FileConstants.getOfflineGameMarkerPath(mRunningGamePackageName);
                        FileUtils.writeStringToFile(path, "new game version");
                    }
                } else {
                    LogWrapper.w(TAG, "runningOfflineGameUpdateChecker: parse json object failed");
                }

                LogWrapper.i(TAG, "Offline game update checker result:" + isGameUpdated);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {}

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                ThreadUtils.runOnUIThread(mOfflineGameUpdateChecker, OFFLINE_GAME_CHECK_FILED_TIME_INTERVAL);
            }

            @Override
            public void onCancel() {}
        });
    }

    private void fetchRemoteConfigFile() {
        LogWrapper.d(TAG, "Fetching remote config file!");

        /*
        * 本地已经存在配置文件，可以直接判断是否单机游戏。
        * 预加载模式不分单机网游，直接使用最新配置文件。
        * */
        if (mIsOfflineGame && !isPreloadingGame()) {
            if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
                if (mConfigInfo != null) {
                    readGameInfoAndSetRuntimeEnvironment(mConfigInfo);
                    notifyCurrentStepFinished(CURRENT_STATE_FETCH_REMOTE_CONFIG);
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG,
                            "Offline game download config file failed:can't access to network.");
                }
                return;
            }

            boolean newGameVersionMark = FileUtils.isExist(FileConstants.getOfflineGameMarkerPath(mRunningGamePackageName));
            if (mConfigInfo != null && !newGameVersionMark) {
                // 单机游戏 ，并且不存在最新游戏配置文件
                readGameInfoAndSetRuntimeEnvironment(mConfigInfo);
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_REMOTE_CONFIG);
                return;
            }

            LogWrapper.i(TAG, "Fetching remote config file, need to fetch game config :" + mConfigInfo + ", new game version mark:" + newGameVersionMark);
            updateGameConfig();
            return;
        }

        /*
        * 第一次进入游戏或联网游戏。
        * */
        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG,
                    "Download config file failed:can't access to network.");
            return;
        }

        updateGameConfig();
    }

    private void copyPreloadResToGameDir() {
        LogWrapper.d(TAG, "Begin copy preload resource to game dir!");
        // game_config   mPathPreloadGameDir + RuntimeConstants.REMOTE_CONFIG_FILE_NAME mPathLocalConfig
        String gameConfig = mPathPreloadGameDir + RuntimeConstants.REMOTE_CONFIG_FILE_NAME;
        String markPath = FileConstants.getOfflineGameMarkerPath(mRunningGamePackageName);

        // 复制预加载资源先设置 mark 标记。
        FileUtils.copyFile(gameConfig, markPath);
        // 复制游戏配置文件
        FileUtils.copyFile(gameConfig, mPathLocalConfig);

        // 复制游戏资源配置文件。
        String gameResConfigPreload = mPathPreloadGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME;
        String gameResConfig = mPathCurrentGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME;
        FileUtils.copyFile(gameResConfigPreload, gameResConfig);

        // 复制 extend.jar
        if (mGameExtendJAR != null) {
            String gameExtendJarPath = mGameExtendJAR.getGameExtendJarDownloadDirectory() + mGameExtendJAR.getRuntimeDownloadGameExtendJarName();
            FileUtils.copyFile(mGameExtendJAR.getPreloadExtendJarPath(), gameExtendJarPath);
        }
        // 拷贝预加载的游戏引擎
        if (mRuntimeLibController != null) {
            mRuntimeLibController.copyPreloadEngineToStandardDir();
        }

        // 删除 mark 标记。
        FileUtils.deleteFile(markPath);

        // 删除预加载目录
        FileUtils.delete(mPathPreloadGameDir);
    }

    private void updateGameConfig() {
        LogWrapper.d(TAG, "updateGameConfig start!");

        // Temp file should be deleted at first.
        FileUtils.deleteFile(mPathRemoteConfigTemp);

        // 下载 gplay_game_config.json
        String url = mRunningGameInfo.mDownloadUrl + RuntimeConstants.CONFIG_FILE_NAME;
        LogWrapper.i(TAG, "fetch game config url = " + url);
        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_CONFIG_JSON, url, mPathRemoteConfigTemp, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download game config file failed!" + errorMsg;
                LogWrapper.e(TAG, error);
                if(errorCode == ERROR_STORAGE_SPACE_NOT_ENOUGH)  {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG, error);
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG, error);
                }
            }

            @Override
            public void onSuccess(File file) {
                JSONObject jsonConfig = FileUtils.readJsonFile(file);
                mConfigInfo = null;
                if (jsonConfig != null) {
                    mConfigInfo = GameConfigInfo.fromJson(jsonConfig);
                }

                if (mConfigInfo != null) {
                    readGameInfoAndSetRuntimeEnvironment(mConfigInfo);
                    if (isPreloadingGame()) {
                        FileUtils.renameFile(mPathRemoteConfigTemp, mPathPreloadGameDir + RuntimeConstants.REMOTE_CONFIG_FILE_NAME);
                    } else {
                        FileUtils.renameFile(mPathRemoteConfigTemp, mPathRemoteConfig);
                    }
                    notifyCurrentStepFinished(CURRENT_STATE_FETCH_REMOTE_CONFIG);
                } else {
                    if (mRetryTimes < RuntimeConstants.DOWNLOAD_RETRY_TIMES) {
                        LogWrapper.w(TAG, "Parse config file failed, try download again!");
                        mRetryTimes++;
                        updateGameConfig();
                    } else {
                        String error = "Download game config success but parsing failed!";
                        LogWrapper.e(TAG, error);
                        notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG,
                                RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG, error);
                    }
                }
            }

            @Override
            public void onCancel() {
                RuntimeLauncher.this.onCancel();
            }
        });
    }

    private void replaceLocalConfigFile() {
        FileUtils.deleteFile(mPathLocalConfig);
        FileUtils.renameFile(mPathRemoteConfig, mPathLocalConfig);
    }

    private void fetchGameResourceConfig() {
        LogWrapper.d(TAG, "Fetching remote gplay_resource_config file!");
        final String url = mRunningGameInfo.mDownloadUrl + mConfigInfo.getResourceConfigMetaInfo().filePath;
        final String saveToPath = mPathResourceConfigZip;

        // 兼容旧的 gplay_resource_config.json 文件的请求不要有提示
        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_MANIFEST_JSON, url, saveToPath, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {
                LogWrapper.d(TAG, "fetchGameResourceConfig onStart: " + tag);
            }

            @Override
            public void onSuccess(File file) {
                if (isPreloadingGame()) {
                    // 预下载模式，将游戏资源配置文件解压到预下载目录。
                    ZipUtils.unpackZip(saveToPath, mPathPreloadGameDir, null);
                    FileUtils.delete(saveToPath);
                } else {
                    FileUtils.deleteFile(mPathResourceConfig);
                    ZipUtils.unpackZip(saveToPath, mPathCurrentGameDir, null);
                    FileUtils.delete(saveToPath);
                }

                if (isRemoteManifestUpdated()) {
                    notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RESOURCE_CONFIG);
                    postCurrStateToChannel(RuntimeConstants.LOADING_FETCH_GAME_RESOURCES_CONFIG);
                } else {
                    if (mRetryTimes < RuntimeConstants.DOWNLOAD_RETRY_TIMES) {
                        LogWrapper.d(TAG, "Fetch manifest file success, but md5 value is wrong, try again!");
                        mRetryTimes++;
                        fetchGameResourceConfig();
                    } else {
                        String error = "The MD5 of downloaded resource config file is wrong.";
                        LogWrapper.e(TAG, error);
                        notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_RESOURCE, error);
                    }
                }
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download game resource config file failed!" + errorMsg;
                LogWrapper.e(TAG, error);
                if(errorCode == ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_RESOURCE, error);
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_RESOURCE, error);
                }
            }

            @Override
            public void onCancel() {
                RuntimeLauncher.this.onCancel();
            }
        });
    }

    private void fetchEngineJAR() {
        String url = mEngineSupportInfo.getJavaLibraryInfo().getDownloadUrl();
        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_RUNTIME_JAR, url, mEngineJARGipTempFilePath, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {
                LogWrapper.d(TAG, "fetchEngineJAR onStart: " + tag);
            }

            @Override
            public void onSuccess(File file) {
                //TODO:校验MD5

                FileUtils.deleteFile(mEngineJARGipFilePath);
                FileUtils.renameFile(mEngineJARGipTempFilePath, mEngineJARGipFilePath);
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_RUNTIME_JAR);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download runtime jar failed! " + errorMsg;
                LogWrapper.e(TAG, error);
                if(errorCode == ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_RUNTIME_JAR, error);
                } else {
                    notifyFileDownloadFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_RUNTIME_JAR, error);
                }
            }

            @Override
            public void onCancel() {
                RuntimeLauncher.this.onCancel();
            }
        });
    }

    private void updateProgress(final String stageName, float stagePercent) {
        if (isPreloadingGame()) {
            return;
        }

        final float globalPercent = mLoadingProgressController.updateStageProgress(stageName, stagePercent);

        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - mLastTimeNotifyToChannel < MIN_NOTIFY_TO_CHANNEL_TIME_INTERVAL && globalPercent < 100f) {
            return;
        }
        mLastTimeNotifyToChannel = currentTimeMillis;

        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                IRuntimeProxy runtimeProxy = mRuntimeStub.getRuntimeProxy();
                if (runtimeProxy != null && mFileDownloader != null) {
                    if (mRuntimeGroup != null) {
                        runtimeProxy.onDownloadGameProgress((int) globalPercent, mFileDownloader.getDownloadSpeed() + mRuntimeGroup.getDownloadSpeed());
                    } else {
                        runtimeProxy.onDownloadGameProgress((int) globalPercent, mFileDownloader.getDownloadSpeed());
                    }
                }
            }
        });
    }

    private void loadRuntimeJar() {
        if (RuntimeEnvironment.boolHostManageRuntime) {
            // 宿主管理 runtime 模式，跳过更新 runtime 等操作
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_JAR);
            return;
        }

        if (isRuntimeJarUpdated(mEngineSupportInfo.getJavaLibraryInfo())) {
            notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_JAR);
            return;
        }

        final String pathHostRuntimeJavaLibDir = getRuntimeJavaLibraryDirectory();
        // 游戏使用的 patch 路径
        FileUtils.copyFile(mEngineJARGipFilePath, pathHostRuntimeJavaLibDir + RuntimeConstants.RUNTIME_FILE_JAR_NAME);
        FileUtils.deleteFile(mEngineJARGipFilePath);
        clearNotUsedEngineJavaLibrary(pathHostRuntimeJavaLibDir);

        notifyCurrentStepFinished(CURRENT_STATE_UPDATE_RUNTIME_JAR);
    }

    private void clearNotUsedEngineJavaLibrary(String pathHostRuntimeLibDir) {
        FileUtils.removeUnusedEngineJavaLibrary(pathHostRuntimeLibDir);
    }

    private void updateGamePatch() {
        mUpdatePatchBeginTime = System.currentTimeMillis();
        LogWrapper.i(TAG, "time statistics, update share library patch begin:" + mUpdatePatchBeginTime);

        if (mConfigInfo.isCocosGame()) {
            mRuntimeLibController = new CocosEngineLibController();
            mRuntimeLibController.init(mRunningGameInfo, mConfigInfo, isPreloadingGame());

            if (!isPreloadingGame() && !mRuntimeLibController.engineLibNeedUpdate()) {
                LogWrapper.i(TAG, "Don't need update game patch !");
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_PATCH_SO);
                return;
            }

            fetchGamePatch();
        } else if (mConfigInfo.isUnityGame()) {
            notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_PATCH_SO);
        } else {
            LogWrapper.e(TAG, "Error occurred at update game patch, can't recognition engine \"" + mConfigInfo.getEngine() + "\"");
        }
    }

    private void fetchGamePatch() {
        LogWrapper.d(TAG, "fetch game patch called!!!");
        updateProgress(RuntimeConstants.LOADING_FETCH_GAME_PATCH, BEGIN_0);

        AbstractEngineLibController.OnShareLibraryPatchUpdateListener lis = new AbstractEngineLibController.OnShareLibraryPatchUpdateListener() {

            private long mDownloadFinishTime;

            @Override
            public void onDownloadStart() {
                LogWrapper.d(TAG, "time statistics, start download share library patch:" + System.currentTimeMillis());
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                recordParallelFileDownloadFailed(THREAD_LOAD_RUNTIME_SO, errorCode, RuntimeConstants.ERROR_OCCURRED_STEP_FETCH_GAME_PATCH, errorMsg);
            }

            @Override
            public void onDownloadSuccess() {
                mDownloadFinishTime = System.currentTimeMillis() - mUpdatePatchBeginTime;
                LogWrapper.i(TAG, "time statistics,download share library patch:" + mDownloadFinishTime + "ms");
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_PATCH, FINISHED_100);
                postCurrStateToChannel(RuntimeConstants.LOADING_FETCH_GAME_PATCH);
            }

            @Override
            public void onSuccess() {
                notifyCurrentStepFinished(CURRENT_STATE_FETCH_GAME_PATCH_SO);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                float percent = calculatePercent(downloadedSize, totalSize);
                updateProgress(RuntimeConstants.LOADING_FETCH_GAME_PATCH, percent);
            }

            @Override
            public void onMergeStart() {
                LogWrapper.d(TAG, "time statistics, start merge share library patch:" + System.currentTimeMillis());
            }

            @Override
            public void onMergeProgress(float percent) {
                updateProgress(RuntimeConstants.LOADING_COMPOUND_GAME_SO, percent);
            }

            @Override
            public void onMergeSuccess() {
                postCurrStateToChannel(RuntimeConstants.LOADING_COMPOUND_GAME_SO);
            }
        };

        mRuntimeLibController.updateGamePatch(lis);
    }

    private void notifyFileDownloadFailed(int what, int step, String errMsg) {
        mIsDownloading = false;
        if (isPreloadingGame()) {
            // 预加载游戏异常。
            mIsPreloadingGame = false;
            mPreloadRuntimeResListener.onErrorOccurred(what, step, errMsg);
            return;
        }
        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj.put(KEY_ERROR_OCCUR_STEP, step);
            jsonObj.put(KEY_ERROR_MSG, errMsg);
            jsonObj.put(KEY_ERROR_IS_PRE_DOWNLOAD, mIsPreDownloadRuntime);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        Message msg = Message.obtain();
        msg.what = what;
        msg.obj = jsonObj.toString();
        mRuntimeStub.getMsgHandler().sendMessageDelayed(msg, 300);
    }

    private synchronized void recordParallelFileDownloadFailed(int threadOfLoader, int what, int step, String msg) {
        if (recordErrorType < 0) {
            recordErrorType = what;
            recordErrorStep = step;
            recordErrorMsg = msg;
        }
        completedRuntimeRoutes();
    }

    private void startLowPriorityDeleteNoRefGroups() {
        LogWrapper.d(TAG, "deleteNoRefGroups ...");
        ThreadUtils.runAsyncThread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                if (mResourceConfigInfo != null) {
                    List<String> filePaths = mResourceConfigInfo.getAllDeleteFile();
                    if (filePaths != null) {
                        for (String fileStr : filePaths) {
                            FileUtils.delete(RuntimeEnvironment.pathCurrentGameResourceDir + fileStr);
                        }
                    }
                }

                // remove all no refrence filee
                File rootFolder = new File(RuntimeEnvironment.pathCurrentGameResourceDir);
                deleteNoRefFileByDepthFirstSearch(rootFolder);
                Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
            }
        });
    }

    private void deleteNoRefFileByDepthFirstSearch(File file) {
        if (file == null || !file.exists())
            return;

        Stack<File> fileStack = new Stack<>();
        fileStack.add(file);
        while (!fileStack.empty()) {
            File tmpFile = fileStack.pop();
            if (tmpFile == null || !tmpFile.exists()) {
                continue;
            }

            if (!tmpFile.isDirectory()) {
                String tmpFilePath = tmpFile.getAbsolutePath();
                if (!mResourceConfigInfo.configContainResource(tmpFilePath)) {
                    //LogWrapper.i(TAG, "deleteNoRefFileByDepthFirstSearch, delete:" + tmpFilePath);
                    tmpFile.delete();
                }
            } else {
                File[] listOfFiles = tmpFile.listFiles();
                if (listOfFiles == null || listOfFiles.length == 0) {
                    tmpFile.delete();
                } else {
                    fileStack.push(tmpFile);
                    for (File aFile : listOfFiles) {
                        fileStack.push(aFile);
                    }
                }
            }
        }
    }

    private static float calculatePercent(long already, long totally) {
        float percent = 0f;
        if (totally >= already && already > 0) {
            percent = (already * 100f) / totally;
        }
        return percent;
    }

    private void readGameInfoAndSetRuntimeEnvironment(GameConfigInfo config) {
        String packageName = config.getPackageName();
        LogWrapper.d(TAG, "packageName is " + packageName + " in configjson, RunningGamePackageName: " + mRunningGamePackageName);
        if (!mRunningGamePackageName.equals(packageName)) {
            LogWrapper.e(TAG, "packageName (" + mRunningGamePackageName + ") in runGame would be same as package_name(" + packageName + ") value in config.json file!");
            Utils.showToast(mContext, "工具中配置的游戏包名与后台的不一致!", Toast.LENGTH_LONG);
            return;
        }

        mEngineType = config.getEngine();
        mEngineVersion = config.getEngineVersion();

        mPathCurrentGameDir = getPath(config, RuntimeConstants.PATH_CURRENT_GAME_DIR);
        mPathCurrentGameGroupDir = getPath(config, RuntimeConstants.PATH_CURRENT_GAME_GROUP_DIR);
        mPathHostRuntimeLibVersionDir = getPath(config, RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_JAVA_LIBRARY_DIR);

        mPathResourceConfig = getPath(config, RuntimeConstants.PATH_RESOURCE_CONFIG);
        mPathResourceConfigZip = getPath(config, RuntimeConstants.PATH_RESOURCE_CONFIG_GIP);

        FileUtils.ensureDirExists(mPathCurrentGameDir);
        FileUtils.ensureDirExists(mPathCurrentGameGroupDir);

        mRunningGameInfo.mEngine = mEngineType;
        mRunningGameInfo.mEngineVersion = mEngineVersion;

        RuntimeEnvironment.currentPackageName = packageName;
        RuntimeEnvironment.pathCurrentGameDir = mPathCurrentGameDir;
        RuntimeEnvironment.engineType = mRunningGameInfo.mEngine;
        RuntimeEnvironment.engineVersion = mRunningGameInfo.mEngineVersion;
        RuntimeEnvironment.currentGameOrientation = mRunningGameInfo.mOrientation;
        RuntimeEnvironment.pathCurrentGameResourceDir = FileConstants.getGameResourceDir(packageName);

        // Host manage runtime mode doesn't need to create runtime folder.
        if (!RuntimeEnvironment.boolHostManageRuntime) {
            FileUtils.ensureDirExists(mPathHostRuntimeLibVersionDir);
        }
    }

    void postCurrStateToChannel(final String currState) {
        if (isPreloadingGame() || mRuntimeStub == null) {
            return;
        }

        final IRuntimeProxy runtimeProxy = mRuntimeStub.getRuntimeProxy();
        if (runtimeProxy == null)
            return;

        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                JSONObject jsonObject = new JSONObject();
                boolean shouldPost = true;
                try {
                    switch (currState) {
                        case RuntimeConstants.LOADING_FETCH_GAME_CONFIG:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_FETCH_GAME_CONFIG");
                            break;
                        case RuntimeConstants.LOADING_FETCH_GAME_RESOURCES_CONFIG:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_FETCH_GAME_RESOURCES_CONFIG");
                            break;
                        case RuntimeConstants.LOADING_FETCH_GAME_PATCH:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_FETCH_GAME_PATCH");
                            break;
                        case RuntimeConstants.LOADING_COMPOUND_GAME_SO:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_COMPOUND_GAME_SO");
                            break;
                        case RuntimeConstants.LOADING_FETCH_GAME_EXTEND_JAR:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_FETCH_GAME_EXTEND_JAR");
                            break;
                        case RuntimeConstants.LOADING_UNZIP_GAME_EXTEND_JAR:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_UNZIP_GAME_EXTEND_JAR");
                            break;
                        case RuntimeConstants.LOADING_DELETE_NO_REF_GROUPS:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_DELETE_NO_REF_GROUPS");
                            break;
                        case RuntimeConstants.LOADING_FETCH_BOOT_GROUP:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_FETCH_BOOT_GROUP");
                            break;
                        case RuntimeConstants.LOADING_UNZIP_BOOT_GROUP:
                            jsonObject.put(RUNTIME_STATE_FOR_CHANNEL, "LOADING_UNZIP_BOOT_GROUP");
                            break;
                        default:
                            shouldPost = false;
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (shouldPost)
                    runtimeProxy.onMessage(jsonObject.toString());
            }
        });
    }

    // 异步单机游戏更新检查
    private Runnable mOfflineGameUpdateChecker = new Runnable() {
        @Override
        public void run() {
            if (mGameUpdateCheckRetryTimes++ < OFFLINE_GAME_CHECK_FAILED_RETRY_TIMES)
                runningOfflineGameUpdateChecker();
            else {
                LogWrapper.i(TAG, "runningOfflineGameUpdateChecker: end retry");
            }
        }
    };

    private void doStartGame() {
        LogWrapper.d(TAG, "doStartGame ...");

        getLoadingProgressController().reset();
        mListener.onCallBack(mRunningGameInfo);
        reset();
    }

    @Override
    public void onCancel() {
        this.reset();
        setLoadingCancelled(true);
    }

    public void reset() {
        mIsDownloading = false;
    }

    public void destroy() {
        LogWrapper.d(TAG, "destroy ...");
        mGameIsStart = false;
        // 在结束游戏时关闭单机游戏更新检查。
        stopOfflineGameUpdateChecker();

        cancel();
    }

    public void cancel() {
        if (mRuntimeGroup != null) {
            mRuntimeGroup.destroy();
        }
        onCancel();
    }

    public boolean isPreloadingGame() {
        return (mIsPreloadingGame && mPreloadRuntimeResListener != null);
    }

    public boolean isDownloading() {
        return mIsDownloading;
    }

    private boolean isResourceManifestModified() {
        // 预下载模式，判断预下载目录下的游戏配置文件。
        String gameResConfigPath = (isPreloadingGame()) ? (mPathPreloadGameDir + RuntimeConstants.RESOURCE_CONFIG_FILE_NAME) : mPathResourceConfig;
        File f = new File(gameResConfigPath);
        if (!f.exists()) return true;

        String remoteManifestMD5 = mConfigInfo.getResourceConfigMetaInfo().md5;
        return FileUtils.isFileModifiedByCompareMD5(gameResConfigPath, remoteManifestMD5);
    }

    private boolean isRuntimeJarUpdated(ResourceTemplate libraryInfo) {
        // 宿主管理 runtime 模式由宿主负责更新 runtime
        boolean b = !RuntimeEnvironment.boolHostManageRuntime && !isRuntimeJarModified(libraryInfo);
        LogWrapper.d(TAG, "Whether need to update engine Jar ?: " + b);
        return b;
    }

    private boolean isRuntimeJarGipUpdated(ResourceTemplate libraryInfo) {
        // 宿主管理 runtime 模式由宿主负责更新 runtime
        boolean b = !RuntimeEnvironment.boolHostManageRuntime && !isRuntimeJarZipModified(libraryInfo);
        LogWrapper.d(TAG, "Whether need to update engine Jar ?: " + b);
        return b;
    }

    private boolean isRuntimeJarGipFileExist() {
        boolean ret = false;
        if (FileUtils.isExist(getPath(mConfigInfo, RuntimeConstants.PATH_RUNTIME_JAR_GIP_FILE))) {
            ret = true;
        }
        LogWrapper.d(TAG, "Does runtime jar gip file exist?: " + ret);
        return ret;
    }

    private boolean isRemoteManifestUpdated() {
        return !isResourceManifestModified();
    }

    private boolean isExistConfigFile() {
        boolean ret = FileUtils.isExist(mPathLocalConfig);
        LogWrapper.d(TAG, "Local config file exists?: " + ret);
        return ret;
    }

    private boolean isConfigUpdated() {
        String localConfigMD5 = FileUtils.getFileMD5(mPathLocalConfig);
        String remoteConfigMD5 = FileUtils.getFileMD5(mPathRemoteConfig);
        return (localConfigMD5 != null && remoteConfigMD5 != null && localConfigMD5.equalsIgnoreCase(remoteConfigMD5));
    }

    private boolean isRuntimeJarModified(ResourceTemplate javaLibraryInfo) {
        String jarFilePath = getRuntimeJavaLibraryPath();
        return !FileUtils.isExist(jarFilePath) ||
                FileUtils.isFileModifiedByCompareMD5(jarFilePath, javaLibraryInfo.getMD5());
    }

    private boolean isRuntimeJarZipModified(ResourceTemplate javaLibraryInfo) {
        return !FileUtils.isExist(mEngineJARGipFilePath)
                || FileUtils.isFileModifiedByCompareMD5(mEngineJARGipFilePath, javaLibraryInfo.getMD5());
    }

    public LoadingProgressController getLoadingProgressController() {
        return mLoadingProgressController;
    }

    public void setIsDownloading(boolean isDownloading) {
        mIsDownloading = isDownloading;
    }

    public void setLoadingCancelled(boolean canceled) {
        mIsLoadingCanceled = canceled;
    }

    RuntimeCompatibilityInfo getRuntimeCompatibilityInfo() {
        return mCurrentCompatibility;
    }

    public GameResourceConfigInfo getResourceConfigInfo() {
        return mResourceConfigInfo;
    }

    public RuntimeEngineSupportInfo getEngineSupportInfo() {
        return mEngineSupportInfo;
    }

    public Context getContext() {
        return mContext;
    }

    public String getRuntimeJavaLibraryDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_JAVA_LIBRARY_DIR);
    }

    public String getRuntimeJavaLibraryPath() {
        return FileUtils.getLatestEngineJavaLibraryPath(getRuntimeJavaLibraryDirectory());
    }

    public String getStandardLibraryDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_SHARELIBRARY_DIR);
    }

    public String getCocosStandardEnginePath() {
        return getStandardLibraryDirectory() + RuntimeConstants.MAIN_SHARE_LIBRARY_NAME;
    }

    public String getRuntimeResourceDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_RESOURCE_DIR);
    }

    public String getEngineCommonResDir() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_ENGINE_COMMON_RES_DIR);
    }

    public String getExtendLibrariesDir() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_EXTEND_LIBRARIES);
    }

    public String getGameSharedLibraryDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_GAME_DIR);
    }

    public String getGameShareLibraryPath() {
        return getGameSharedLibraryDirectory() + RuntimeConstants.MAIN_SHARE_LIBRARY_NAME;
    }

    public String getGameExtendDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_GAME_EXTEND_DIR);
    }

    public String getGameExtendPath() {
        return getGameExtendDirectory() + RuntimeConstants.GAME_EXTEND_FILE_NAME;
    }

    public String getGameExtendJarFile() {
        GameConfigInfo.GameExtendInfo extendInfo = mConfigInfo.getGameExtendInfo();
        if (null == extendInfo) {
            return "";
        }
        return getGameExtendPath();
    }

    public String getDiffPatchShareLibraryDirectory() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_PATCH_SO_DIR);
    }

    public String getDiffPatchShareLibraryPath() {
        return getDiffPatchShareLibraryDirectory() + "libdiffpatch.so";
    }

    public String getOptimizedDexDir() {
        return getRuntimePath(RuntimeConstants.PATH_HOST_RUNTIME_DEX_DIR);
    }

    public void cancelPreloadGame() {
        LogWrapper.d(TAG, "Cancel preload game !");
        setLoadingCancelled(true);

        if (mRuntimeLibController != null) {
            mRuntimeLibController.cancelUpdate();
        }

        if (mRuntimeGroup != null) {
            mRuntimeGroup.cancelCurrentDownload(new OnCancelDownloadListener() {
                @Override
                public void onCancel() {
                    if (mPreloadRuntimeResListener != null) {
                        recordParallelFileDownloadFailed(THREAD_LOAD_BOOT_SCENE, RuntimeConstants.MODE_TYPE_CANCLE_PRELOADING,
                                RuntimeConstants.ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE, "Cancel loading boot scene !");
                    }
                }

                @Override
                public void onWaitUnzip() {
                }

                @Override
                public void onFinish() {
                }
            });
        }
    }

    public interface IPreloadRuntimeResListener {
        void onErrorOccurred(int errCode, int occurredStep, String errMsg);

        void onSuccess();

        void onCancel();
    }
}
