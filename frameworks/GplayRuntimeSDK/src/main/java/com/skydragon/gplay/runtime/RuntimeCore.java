package com.skydragon.gplay.runtime;

import android.content.DialogInterface;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import com.skydragon.gplay.runtime.bridge.BridgeHelper;
import com.skydragon.gplay.runtime.callback.OnCancelDownloadListener;
import com.skydragon.gplay.runtime.entity.resource.GameResourceConfigInfo;
import com.skydragon.gplay.runtime.entity.resource.GroupInfo;
import com.skydragon.gplay.runtime.entity.resource.SceneInfo;
import com.skydragon.gplay.runtime.ui.CustomProgressDialog;
import com.skydragon.gplay.runtime.ui.PromptDialog;
import com.skydragon.gplay.runtime.utils.LoadingProgressController;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtime.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

public final class RuntimeCore {

    public final static String TAG = "RuntimeCore";

    private final static String GROUP_DOWNLOAD_FETCH = "fetch";
    private final static String GROUP_DOWNLOAD_UNZIP = "unzip";
    private final static String GROUP_DOWNLOAD_CONNECT = "connect";

    public final static int ERR_UNKNOWN = -1;
    public final static int ERR_NO_NETWORK = 1;
    public final static int ERR_NO_SPACE = 2;
    public final static int ERR_VERIFY = 3;
    public final static int ERR_GAME_RES_EXPIRED = 4;

    private final static int UPDATE_RES_BUNDLE_RETRY_TIMES = 1;
    private final static int PRELOAD_RES_BUNDLE_RETRY_TIMES = 3;

    private final static int STAGE_FETCH = 1;
    private final static int STAGE_UNZIP = 2;

    public static boolean useNewPreloadResponseMode;

    private boolean mIsInSilentDownload;
    private boolean mIsInDownloadingGroup;
    private boolean mIsInCancelDownload;
    private boolean mSilentDownloadEnabled;
    private boolean mIsStopSilentDownload;
    private boolean mBeginPreloadScenes;
    private boolean mAlreadyStartChecker;
    private boolean mCancelPreloadResBundle;
    private boolean mStartCheckerLocked = true;

    private String mPreloadSceneJson;
    private String mPrevPreloadGroup;
    private String mCurrentDownloadName;
    private String mErrorCode;

    private long mExt;
    private long mOldTimeMillisForProgress;

    private CustomProgressDialog mCustomProgressDialog;
    private PromptDialog mPromptDialog;

    // Setting this value to 0, since `boot` group is downloaded before entering game scene.
    private int mCurrentDownloadIndex;

    private float mDownloadSpeed;
    private float mPercent;

    private final List<String> mWaitingDownloadGroups;
    private List<String> mLackScenesInfo;
    private SceneInfo mCurrentDownloadSceneInfo;
    private Queue<String> mWaitingDownloadScenesQueue;
    private Queue<String> mWaitingSilentDownloadGroupQueue;
    private List<String> mCompletedScenesTmpList;

    private final RuntimeLocalRecord mRuntimeLocalRecord;
    private RuntimeGroup mRuntimeGroup;
    private GroupInfo mCurrentDownloadGroup;
    private GameResourceConfigInfo mResourceConfigInfo;
    private LoadingProgressController mLoadingProgressController;

    private final Object mResCompletedSyncObj = new Object();

    private IPreloadResBundleListener mPreloadResBundleListener;
    private IResourceCompletedListener mResCompletedListener;
    private IPreloadInfoListener mPreloadInfoListener;

    //静默下载失败的重试次数限制
    private int mSilentDownloadRetryLimit = 10;

    Runnable mSilentDownloadTask = new Runnable() {
        public void run() {
            ThreadUtils.getUIHandler().removeCallbacks(mSilentDownloadTask);
            onSilentDownloadNextGroup();
        }
    };

    private static RuntimeCore sRuntimeCore;

    public static RuntimeCore getInstance() {
        if (null == sRuntimeCore) {
            sRuntimeCore = new RuntimeCore();
        }
        return sRuntimeCore;
    }

    private RuntimeCore() {
        mRuntimeLocalRecord = RuntimeLocalRecord.getInstance();
        mWaitingDownloadGroups = new ArrayList<>();
    }

    public void init() {
        if (mRuntimeGroup == null) {
            mRuntimeGroup = RuntimeGroup.getInstance();
            mResourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
            mLoadingProgressController = new LoadingProgressController();
        }
    }

    public boolean isSilentDownloadEnabled() {
        return mSilentDownloadEnabled;
    }

    public void setSilentDownloadEnabled(boolean enabled) {
        mSilentDownloadEnabled = enabled;
    }

    public void seIsInDownloadingGroup(boolean isInDownloadingGroup) {
        mIsInDownloadingGroup = isInDownloadingGroup;
    }

    private void setInSilentDownload(boolean inSilentDownload) {
        mIsInSilentDownload = inSilentDownload;
        // Setting unzip group priority to minimal if it's in silent download.
        if (isInSilentDownload()) {
            RuntimeGroup.setUnzipThreadPriority(Thread.MIN_PRIORITY);
        } else {
            RuntimeGroup.setUnzipThreadPriority(Thread.NORM_PRIORITY);
        }
    }

    private synchronized boolean isWaitingDownloadGroupEmpty() {
        return mWaitingDownloadGroups.isEmpty();
    }

    private void removeGroupFromWaitingDownload(String groupName) {
        if (isWaitingDownloadGroupEmpty()) {
            return;
        }

        String deleteGroupName = null;
        for (String sGroupName : mWaitingDownloadGroups) {
            if (sGroupName.equals(groupName)) {
                deleteGroupName = groupName;
                break;
            }
        }

        if (null != deleteGroupName) {
            mWaitingDownloadGroups.remove(deleteGroupName);
        } else {
            LogWrapper.e(TAG, groupName + " group don't found in download groups list");
        }
    }

    public void setResourceCompletedListener(final IResourceCompletedListener listener) {
        mResCompletedListener = listener;
        startChecker();
    }

    public void startResBundleChecker() {
        mStartCheckerLocked = false;
        startChecker();
    }

    // 异步检查资源完整性
    private void startChecker() {
        if (!mStartCheckerLocked && isListeningResCompleted() && !mAlreadyStartChecker) {
            mAlreadyStartChecker = true;
            mRuntimeLocalRecord.startResBundleAsync(mResourceConfigInfo, new RuntimeLocalRecord.ICheckResBundleListener() {
                @Override
                public void onResBundleChecked(final Set<String> sceneNames) {
                    mLackScenesInfo = null;
                    if (sceneNames.isEmpty()) {
                        mResCompletedListener.onCompleted();
                        return;
                    }

                    synchronized (mResCompletedSyncObj) {
                        if (mCompletedScenesTmpList != null && !mCompletedScenesTmpList.isEmpty()) {
                            for (String sceneName : mCompletedScenesTmpList) {
                                if (sceneNames.contains(sceneName)) {
                                    sceneNames.remove(sceneName);
                                }
                            }

                            if (sceneNames.isEmpty()) {
                                mResCompletedListener.onCompleted();
                                return;
                            }
                        }
                        mLackScenesInfo = new LinkedList<>(sceneNames);
                    }
                }

                @Override
                public void onError() {
                }
            });
        }
    }

    private void notifySceneUpdate(String sceneName) {
        if (mLackScenesInfo == null) {
            synchronized (mResCompletedSyncObj) {
                if (mLackScenesInfo == null) {
                    if (mCompletedScenesTmpList == null) {
                        mCompletedScenesTmpList = new ArrayList<>();
                    }
                    mCompletedScenesTmpList.add(sceneName);
                } else {
                    if (mLackScenesInfo.contains(sceneName)) {
                        mLackScenesInfo.remove(sceneName);
                    }
                    if (mLackScenesInfo.isEmpty()) {
                        mResCompletedListener.onCompleted();
                    }
                }
            }
        } else {
            if (mLackScenesInfo.contains(sceneName)) {
                mLackScenesInfo.remove(sceneName);
            }
            if (mLackScenesInfo.isEmpty()) {
                mResCompletedListener.onCompleted();
            }
        }
    }

    private String getGroupFromWaitingDownload() {
        if (mWaitingDownloadGroups.isEmpty()) {
            return null;
        }

        return mWaitingDownloadGroups.get(0);
    }

    private synchronized void prepareWaitingDownloadGroups(String groupName) {
        List<String> listGroupNames = new ArrayList<>();
        listGroupNames.add(groupName);
        prepareWaitingDownloadGroups(listGroupNames);
    }

    private synchronized void prepareWaitingDownloadGroups(List<String> groupNames) {
        mWaitingDownloadGroups.clear();

        if (groupNames.isEmpty())
            return;

        mCurrentDownloadName = groupNames.get(0);

        for (String name : groupNames) {
            LogWrapper.d(TAG, "prepareWaitingDownloadGroups group ( " + name + " ) check downloaded!");

            if (mWaitingDownloadGroups.contains(name)) {
                LogWrapper.e(TAG, "group ( " + name + " ) has been add to waiting download group, don't put it again!");
                continue;
            }

            mWaitingDownloadGroups.add(name);
        }

        // if downloading group is in the waiting download list, complete the download process first
        if (mCurrentDownloadGroup != null && mWaitingDownloadGroups.contains(mCurrentDownloadGroup.getName())) {
            removeGroupFromWaitingDownload(mCurrentDownloadGroup.getName());
            mWaitingDownloadGroups.add(0, mCurrentDownloadGroup.getName());
        }
    }

    public boolean isInSilentDownload() {
        return mIsInSilentDownload;
    }

    public boolean isInCancelDownload() {
        return mIsInCancelDownload;
    }

    public boolean isPreloadingResBundle() {
        return (mPreloadResBundleListener != null);
    }

    public boolean isListeningResCompleted() {
        return (mResCompletedListener != null);
    }

    public void setInCancelDownload(boolean isInCancelDownload) {
        mIsInCancelDownload = isInCancelDownload;
    }

    public void cancelAllRequests() {
        if (null == mRuntimeGroup) {
            LogWrapper.w(TAG, "Please invoke RuntimeCore init method first!");
            return;
        }

        setInCancelDownload(true);
        // Currently, we only have one download instance. So just cancel current download is enough
        mRuntimeGroup.cancelCurrentDownload(new OnCancelDownloadListener() {
            @Override
            public void onFinish() {
                clearDownloadStatus();
                mWaitingDownloadGroups.clear();
                setInCancelDownload(false);
                LogWrapper.d(TAG, "cancel current download finish!");
                if (mPreloadResBundleListener != null)
                    mPreloadResBundleListener.onCancel();
            }

            @Override
            public void onWaitUnzip() {
                LogWrapper.d(TAG, "onWaitUnzip ...");
                if (mPreloadResBundleListener != null)
                    mPreloadResBundleListener.onCancel();
            }

            @Override
            public void onCancel() {
                if (isInSilentDownload()) {
                    decreaseIndexOfSilentDownload();
                    if (mPreloadResBundleListener != null)
                        mPreloadResBundleListener.onCancel();
                }
            }
        });
    }

    public void start() {
        if (null == mRuntimeGroup) {
            LogWrapper.w(TAG, "Please invoke RuntimeCore init method first!");
            return;
        }
        mRuntimeGroup.init(RuntimeEnvironment.currentPackageName, RuntimeEnvironment.urlCurrentGame);
        onSilentDownloadNextGroup();
    }

    public void startSilentDownload() {
        if (null == mRuntimeGroup) {
            LogWrapper.w(TAG, "Start silent download failure, please invoke init method first!");
            return;
        }

        setSilentDownloadEnabled(true);

        if (isInSilentDownload() && !mIsStopSilentDownload) {
            return;
        }

        mIsStopSilentDownload = false;

        onSilentDownloadNextGroup();
    }

    public void stopSilentDownload() {
        LogWrapper.d(TAG, "Stop silent download! isSilentDownload = " + isInSilentDownload());
        setSilentDownloadEnabled(false);
        if (isInSilentDownload()) {
            cancelAllRequests();
        }
        mIsStopSilentDownload = true;
    }

    public void preloadPrescribedResBundleBeforeAll(String resBundle, final boolean withAllRes, final IPreloadResBundleListener listener) {
        init();
        mPreloadResBundleListener = listener;
        mCancelPreloadResBundle = false;
        if (mRuntimeGroup == null) {
            mRuntimeGroup = RuntimeGroup.getInstance();
        }
        RuntimeScene runtimeScene = RuntimeScene.getRuntimeScene(resBundle);
        List<String> groups;
        if (runtimeScene != null) {
            groups = runtimeScene.calculateLackOfGroups(isPreloadingResBundle());
            if (groups.isEmpty()) {
                listener.onSuccess();
                return;
            }
        } else {
            listener.onErrorOccurred(ERR_UNKNOWN, "Resource bundle (" + resBundle + ") not found!");
            return;
        }

        if (isInCancelDownload()) {
            listener.onCancel();
            return;
        }

        final ArrayDeque<String> groupsDeque = new ArrayDeque<>(groups);
        fetchResGroups(groupsDeque, new IResourceCompletedListener() {
            @Override
            public void onCompleted() {
                if (withAllRes) {
                    setInCancelDownload(false);
                    clearDownloadStatus();
                    startSilentDownload();
                } else {
                    listener.onSuccess();
                }
            }
        });
    }

    public void preloadAllResBundle(IPreloadResBundleListener listener) {
        mPreloadResBundleListener = listener;
        mCancelPreloadResBundle = false;
        init();
        setInCancelDownload(false);
        clearDownloadStatus();
        startSilentDownload();
    }

    public void destroy() {
        LogWrapper.d(TAG, "RuntimeCore destroyed!");
        cancelAllRequests();
    }

    public void fetchResGroups(final Queue<String> resGroups, final IResourceCompletedListener listener) {

        if (mCancelPreloadResBundle || isInCancelDownload()) {
            if (mPreloadResBundleListener != null)
                mPreloadResBundleListener.onCancel();
            return;
        }

        if (resGroups.isEmpty()) {
            listener.onCompleted();
            return;
        }

        final String groupName = resGroups.poll();

        if (TextUtils.isEmpty(groupName)) {
            fetchResGroups(resGroups, listener);
            return;
        }

        int retryTimes = (isPreloadingResBundle()) ? PRELOAD_RES_BUNDLE_RETRY_TIMES : UPDATE_RES_BUNDLE_RETRY_TIMES;

        mRuntimeGroup.updateGroup(groupName, false, retryTimes, new RuntimeGroup.OnUpdateGroupListener() {
            @Override
            public void onStartOfDownload() {
            }

            @Override
            public void onProgressOfDownload(long bytesWritten, long totalSize) {
            }

            @Override
            public void onSuccessOfDownload(long totalSize) {
                fetchResGroups(resGroups, listener);
            }

            @Override
            public void onFailureOfDownload(String errorMsg) {
                onFailureOfDownloadGroup(groupName, errorMsg);
            }

            @Override
            public void onSuccessOfUnzip() {
            }

            @Override
            public void onFailureOfUnzip(String errorMsg) {
            }

            @Override
            public void onProgressOfUnzip(float percent) {
            }

            @Override
            public boolean isUnzipInterrupted() {
                return mIsInCancelDownload;
            }
        });
    }

    private GroupInfo getNextGroupForSilentDownload() {

        if (mWaitingSilentDownloadGroupQueue == null || mWaitingSilentDownloadGroupQueue.isEmpty()) {
            // 当 scene 中的所有 group 已经下载完毕后，更新场景本地记录信息。
            if (mCurrentDownloadSceneInfo != null) {
                mRuntimeLocalRecord.updateSceneVersionToNewest(mCurrentDownloadSceneInfo);
                if (!isPreloadingResBundle()) {
                    mRuntimeLocalRecord.persis();
                }
                if (isListeningResCompleted()) {
                    notifySceneUpdate(mCurrentDownloadSceneInfo.getName());
                }
                mCurrentDownloadSceneInfo = null;
            }

            if (mWaitingSilentDownloadGroupQueue == null) {
                mWaitingSilentDownloadGroupQueue = new ArrayDeque<>();
            }

            int nextDownloadIndex = increaseIndexOfSilentDownload();
            List<SceneInfo> sceneInfoList = mResourceConfigInfo.getAllSceneInfos();

            // 依 order 次序来获取 scene , 并将 scene 的 groups 加入到静默下载队列中。
            if (nextDownloadIndex >= 0 && nextDownloadIndex < sceneInfoList.size()) {
                mCurrentDownloadSceneInfo = sceneInfoList.get(nextDownloadIndex);

                RuntimeScene scene = RuntimeScene.getRuntimeScene(mCurrentDownloadSceneInfo.getName());
                List<String> groups;
                if (scene != null) {
                    if (!(groups = scene.calculateLackOfGroups(isPreloadingResBundle())).isEmpty()) {
                        LogWrapper.d(TAG, "WaitForSilentQueue add scene:" + scene.getName() + ", group size : " + groups.size());
                        mWaitingSilentDownloadGroupQueue.addAll(groups);
                    } else {
                        LogWrapper.d(TAG, "WaitForSilentQueue add scene:" + scene.getName() + ", waitingList is " + groups);
                    }
                }

                if (mWaitingSilentDownloadGroupQueue.isEmpty()) {
                    return getNextGroupForSilentDownload();
                }

                /**
                 * 将场景本地记录更改为 modified 状态。
                 * */
                mRuntimeLocalRecord.setSceneModified(mCurrentDownloadSceneInfo.getName(), true);
                // 所有场景已经下载完毕。
                if (!isPreloadingResBundle()) {
                    mRuntimeLocalRecord.persis();
                }
            } else {
                // 所有场景已经下载完毕。
                if (isPreloadingResBundle()) {
                    mPreloadResBundleListener.onSuccess();
                }
                return null;
            }
        }

        String groupName = mWaitingSilentDownloadGroupQueue.poll();
        LogWrapper.d(TAG, "Get next group (\"" + groupName + "\") for silentDownload.");

        if (groupName != null) {
            return mRuntimeGroup.findGroup(groupName);
        } else {
            return null;
        }
    }

    private int increaseIndexOfSilentDownload() {
        return ++mCurrentDownloadIndex;
    }

    private int decreaseIndexOfSilentDownload() {
        --mCurrentDownloadIndex;
        if (mCurrentDownloadIndex < 0)
            mCurrentDownloadIndex = 0;
        LogWrapper.d(TAG, "decrease current download index: " + mCurrentDownloadIndex);
        return mCurrentDownloadIndex;
    }

    private void startDownloadGroups() {
        if (isWaitingDownloadGroupEmpty()) {
            LogWrapper.d(TAG, "waiting download groups is empty now, download done!");
            if (!mIsInSilentDownload && mWaitingDownloadScenesQueue.isEmpty()) {
                mLoadingProgressController.reset();
                mPercent = 100.0f;
                responseDownloadToNative(mCurrentDownloadName, true, mPercent, STAGE_UNZIP, false);
            }
            return;
        }

        mCurrentDownloadName = getGroupFromWaitingDownload();
        LogWrapper.d(TAG, "Start download group \"" + mCurrentDownloadName + "\"");
        startDownload(mRuntimeGroup.findGroup(mCurrentDownloadName));
    }

    private void fetchNextSceneFromPreloadScenes() {
        // 当 scene 中的所有 group 已经下载完毕后，更新场景本地记录信息。
        if (mCurrentDownloadSceneInfo != null) {
            mRuntimeLocalRecord.updateSceneVersionToNewest(mCurrentDownloadSceneInfo);
            if (!isPreloadingResBundle()) {
                mRuntimeLocalRecord.persis();
            }
            if (isListeningResCompleted()) {
                notifySceneUpdate(mCurrentDownloadSceneInfo.getName());
            }
            mCurrentDownloadSceneInfo = null;
        }

        if (mWaitingDownloadScenesQueue == null || mWaitingDownloadScenesQueue.isEmpty()) {
            LogWrapper.d(TAG, "Waiting download scenes queue is empty");
            // 下载 scene 队列已经完成
            mLoadingProgressController.reset();
            mPercent = 100.0f;
            if (!isInSilentDownload()) {
                responseDownloadToNative(mCurrentDownloadName, true, mPercent, STAGE_UNZIP, false);
            }
            return;
        } else {
            LogWrapper.d(TAG, "Waiting download scenes queue is not empty");
        }

        // 场景队列队列中的下一个分组信息。
        String sceneName = mWaitingDownloadScenesQueue.poll();
        LogWrapper.d(TAG, "Waiting download next scene is: " + sceneName);

        mCurrentDownloadSceneInfo = mResourceConfigInfo.getSceneInfoByName(sceneName);
        if (mCurrentDownloadSceneInfo == null) {
            fetchNextSceneFromPreloadScenes();
            return;
        }

        List<String> groups = null;
        RuntimeScene runtimeScene = RuntimeScene.getRuntimeScene(sceneName);
        if (runtimeScene != null) {
            groups = runtimeScene.calculateLackOfGroups(isPreloadingResBundle());
        }

        if (groups == null || groups.isEmpty()) {
            fetchNextSceneFromPreloadScenes();
            return;
        } else {
            // count groups Size
            long sceneSize = 0;
            for (String groupName : groups) {
                GroupInfo groupInfo = mResourceConfigInfo.getGroupInfoByName(groupName);
                if (groupInfo != null) {
                    sceneSize += groupInfo.getSize();
                }
            }
            onSceneBeginLoading(sceneName, sceneSize);
            /**
             * 将场景本地记录更改为 modified 状态。
             * */
            mRuntimeLocalRecord.setSceneModified(sceneName, true);
            if (!isPreloadingResBundle()) {
                mRuntimeLocalRecord.persis();
            }
            // 将解析 scene 得到的 group 列表添加到下载队列
            mWaitingDownloadGroups.addAll(groups);
        }

        mCurrentDownloadName = getGroupFromWaitingDownload();
        LogWrapper.d(TAG, "Waiting download group is: " + mCurrentDownloadName);
        startDownload(mRuntimeGroup.findGroup(mCurrentDownloadName));
    }

    private void startDownload(GroupInfo resGroup) {
        if (resGroup == null) {
            LogWrapper.e(TAG, "Start Download, but group is null !");
            return;
        }

        LogWrapper.d(TAG, "<======= start download(" + resGroup.getName() + ")  ======>  mIsInSilentDownload=" + mIsInSilentDownload);

        final String groupName = resGroup.getName();
        if (mRuntimeLocalRecord.isDownloadedGroupInfoContained(groupName) && resGroup.isCompletedGroup()) {
            onSuccessOfDownloadGroup(groupName);
            LogWrapper.d(TAG, "startDownload completed, group(" + groupName + ") already downloaded ! ");
            return;
        }

        ThreadUtils.printCurrentThreadName("startDownload: " + groupName);

        if (mIsInDownloadingGroup) {
            LogWrapper.e(TAG, "Oops, previous group: " + mPrevPreloadGroup + ", current group: " + groupName);
            return;
        }
        mCurrentDownloadGroup = resGroup;
        seIsInDownloadingGroup(true);
        mPrevPreloadGroup = groupName;

        mOldTimeMillisForProgress = System.currentTimeMillis();
        mDownloadSpeed = 0.0f;

        int retryTimes = (isPreloadingResBundle()) ? PRELOAD_RES_BUNDLE_RETRY_TIMES : UPDATE_RES_BUNDLE_RETRY_TIMES;
        mRuntimeGroup.updateGroup(groupName, !isPreloadingResBundle(), retryTimes, new RuntimeGroup.OnUpdateGroupListener() {
            @Override
            public void onStartOfDownload() {
                onStartOfDownloadGroup(groupName);
            }

            @Override
            public void onProgressOfDownload(long bytesWritten, long totalSize) {
                onProgressOfDownloadGroup(groupName, (int) bytesWritten, (int) totalSize);
            }

            @Override
            public void onSuccessOfDownload(long totalSize) {
                LogWrapper.d(TAG, "updateGroup onSuccessOfDownload(" + groupName + ") totalSize=" + totalSize);
                mDownloadSpeed = 0.0f;

                if (isPreloadingResBundle()) {
                    mRuntimeLocalRecord.recordDownloadedGroupInfo(groupName);
                    onSuccessOfDownloadGroup(groupName);
                }
            }

            @Override
            public void onFailureOfDownload(String errorMsg) {
                onFailureOfDownloadGroup(groupName, errorMsg);
            }

            @Override
            public void onSuccessOfUnzip() {
                LogWrapper.d(TAG, "updateGroup onSuccessOfUnzip(" + groupName + ")");
                onProgressOfUnzipGroup(groupName, 100.0f);
                // 持久化下载成功的资源
                mRuntimeLocalRecord.recordDownloadedGroupInfo(groupName);
                mRuntimeLocalRecord.persis();
                onSuccessOfDownloadGroup(groupName);
            }

            @Override
            public void onFailureOfUnzip(String errorMsg) {
                onFailureOfDownloadGroup(groupName, errorMsg);
            }

            @Override
            public void onProgressOfUnzip(float percent) {
                onProgressOfUnzipGroup(groupName, percent);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return false;
            }
        });
    }

    void onSilentDownloadNextGroup() {
        if (!isSilentDownloadEnabled()) {
            return;
        }

        if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
            ThreadUtils.getUIHandler().postDelayed(mSilentDownloadTask, 10000);
            return;
        }

        if (mIsInDownloadingGroup) {
            LogWrapper.d(TAG, "group(" + mPrevPreloadGroup + ") is downloading, don't start silent download!");
            return;
        }

        GroupInfo resGroup = getNextGroupForSilentDownload();

        if (resGroup == null) {
            return;
        }

        LogWrapper.d(TAG, "Begin silent download group ( " + resGroup.getName() + " )");

        prepareWaitingDownloadGroups(resGroup.getName());

        if (isWaitingDownloadGroupEmpty()) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!BridgeHelper.getInstance().isRunning()) {
                        LogWrapper.d(TAG, "Game have been shutdown, cancel download request!");
                        return;
                    }
                    onSilentDownloadNextGroup();
                }
            }, 100);
        } else {
            setInSilentDownload(true);
            startDownloadGroups();
        }
    }

    private void clearDownloadStatus() {
        mCurrentDownloadGroup = null;
        seIsInDownloadingGroup(false);
    }

    private void onSuccessOfDownloadGroup(String groupName) {
        removeGroupFromWaitingDownload(groupName);

        LogWrapper.d(TAG, "Update group ( " + groupName + " ) finished!");
        ThreadUtils.printCurrentThreadName("onSuccessOfDownloadGroup:" + groupName + ", toNative: " + !isInSilentDownload());
        if (mCurrentDownloadGroup != null && !mCurrentDownloadGroup.getName().equals(groupName)) {
            LogWrapper.e(TAG, "Oops, some errors happened, current download group name: " + mCurrentDownloadGroup.getName() + ", downloaded group name: " + groupName);
        }

        clearDownloadStatus();
        // If there are another group needs to be downloaded, don't make silent download works.

        if (isWaitingDownloadGroupEmpty() && !isInCancelDownload()) {
            if (isSilentDownloadEnabled() && (null == mWaitingDownloadScenesQueue || mWaitingDownloadScenesQueue.isEmpty())) {
                if (!isInSilentDownload()) {
                    responseDownloadToNative(mCurrentDownloadName, true, 100, STAGE_UNZIP, false);
                }
                // 当前为静态下载
                onSilentDownloadNextGroup();
            } else {
                // 当前为主动下载
                // 当没有要下载的场景时,则退出
                fetchNextSceneFromPreloadScenes();
            }
        } else {
            // If there are another group needs to be downloaded, don't make silent download works.
            startDownloadGroups();
        }
    }

    private void onStartOfDownloadGroup(String groupName) {
        if (!mIsInSilentDownload) {
            mPercent = mLoadingProgressController.updateStageProgress(GROUP_DOWNLOAD_CONNECT + groupName, 100.0f);
            responseDownloadToNative(groupName, false, mPercent, STAGE_FETCH, false);
        }
    }

    private void onProgressOfDownloadGroup(String groupName, int bytesWritten, int totalSize) {
        if (!isInSilentDownload()) {
            mPercent = mLoadingProgressController.updateStageProgress(GROUP_DOWNLOAD_FETCH + groupName, 100.0f * bytesWritten / totalSize);
            long newTimeMillis = System.currentTimeMillis();
            mDownloadSpeed = mRuntimeGroup.getDownloadSpeed();
            // Notify native to refresh loading process if the interval time is larger than 100 milliseconds
            if (newTimeMillis - mOldTimeMillisForProgress > 100 || bytesWritten >= totalSize) {
                responseDownloadToNative(groupName, false, mPercent, STAGE_FETCH, false);
                mOldTimeMillisForProgress = newTimeMillis;
            }
        }
    }

    private void onProgressOfUnzipGroup(String groupName, float unZipPercnet) {
        if (!isInSilentDownload()) {
            long newTimeMillis = System.currentTimeMillis();
            mDownloadSpeed = 0;
            mPercent = mLoadingProgressController.updateStageProgress(GROUP_DOWNLOAD_UNZIP + groupName, unZipPercnet);
            // Notify native to refresh loading process if the interval time is larger than 100 milliseconds
            if (newTimeMillis - mOldTimeMillisForProgress > 100) {
                responseDownloadToNative(groupName, false, mPercent, STAGE_UNZIP, false);
                mOldTimeMillisForProgress = newTimeMillis;
            }
        }
    }

    private void onFailureOfDownloadGroup(final String groupName, final String errorMsg) {
        int errorCode;
        if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
            mErrorCode = "err_no_space";
            errorCode = ERR_NO_SPACE;
        } else if (errorMsg.contains(RuntimeConstants.DOWNLOAD_VERIFY_WRONG)) {
            mErrorCode = "err_verify";
            errorCode = ERR_VERIFY;
        } else if (errorMsg.contains(RuntimeConstants.DOWNLOAD_GAME_RES_EXPIRED)) {
            mErrorCode = "err_game_res_expired";
            errorCode = ERR_GAME_RES_EXPIRED;
        } else {
            mErrorCode = "err_network";
            errorCode = ERR_NO_NETWORK;
        }

        clearDownloadStatus();

        if (isPreloadingResBundle()) {
            mPreloadResBundleListener.onErrorOccurred(errorCode, errorMsg);
            return;
        }

        if (isInSilentDownload()) {
            if (errorCode != ERR_GAME_RES_EXPIRED && errorCode != ERR_NO_SPACE) {
                if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
                    decreaseIndexOfSilentDownload();
                    onSilentDownloadNextGroup();
                } else if (mSilentDownloadRetryLimit > 0) {
                    mSilentDownloadRetryLimit -= 1;
                    // If it's in silent download, we need to download it again.
                    ThreadUtils.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            decreaseIndexOfSilentDownload();
                            onSilentDownloadNextGroup();
                        }
                    }, 500);
                }
            }
        } else {
            if (mPreloadInfoListener != null && mExt == 0 && mBeginPreloadScenes) {
                mPreloadInfoListener.onLoadingScenesFailure(errorCode, errorMsg, mPercent, 1);
            }
            responseDownloadToNative(groupName, false, mPercent, STAGE_FETCH, true);
        }
        mErrorCode = "";
    }

    // 渠道调用重试加载场景
    public void retryPreloadScenes() {
        if (mPreloadSceneJson != null) {
            BridgeHelper.getInstance().runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    nativePreload(mPreloadSceneJson, mExt);
                }
            });
        }
    }

    /***
     * @param sceneJson : { "scenes" : ["scene1", "scene2"] }
     * @param ext       : 1:使用游戏进度条 ; 0:使用渠道或者Gplay定制进度条(默认)
     */
    public void nativePreload(String sceneJson, long ext) {
        mPreloadSceneJson = sceneJson;
        mExt = ext;
        // NOTE THAT IT'S IN GL THREAD
        LogWrapper.d(TAG, "GL Thread: onNativePreload scene : " + sceneJson + " , ext : " + ext);
        boolean isEngineContext = Utils.isInGameThread();
        if (!isEngineContext) {
            LogWrapper.e(TAG, "Oops, it wasn't invoked from engine thread!");
            return;
        }

        JSONArray sceneArray;
        try {
            JSONObject jsonObject = new JSONObject(sceneJson);
            sceneArray = jsonObject.optJSONArray("scenes");
        } catch (JSONException e) {
            LogWrapper.e(TAG, "nativePreload sceneJson parse failure!");
            return;
        }

        preloadScenes(sceneArray);
    }

    private void preloadScenes(final JSONArray sceneArray) {
        LogWrapper.d(TAG, "preloadScenes : " + sceneArray);

        mWaitingDownloadScenesQueue = new ArrayDeque<>();

        final List<String> allGroups = new ArrayList<>();
        try {
            // 计算所有要下载的 groups 总的大小，并且添加 Scenes 到待下载队列。
            for (int i = 0; i < sceneArray.length(); i++) {
                // 计算总的scene下载大小
                String sceneName = (String) sceneArray.get(i);
                RuntimeScene runtimeScene = RuntimeScene.getRuntimeScene(sceneName);
                if (runtimeScene == null)
                    continue;

                List<String> groups = runtimeScene.calculateLackOfGroups(isPreloadingResBundle());
                if (mResourceConfigInfo.getSceneInfoByName(sceneName) != null && !groups.isEmpty()) {
                    // 添加待下载的 scene
                    mWaitingDownloadScenesQueue.add(sceneName);
                }

                if (!groups.isEmpty())
                    allGroups.addAll(groups);
            }
        } catch (JSONException e) {
            LogWrapper.d(TAG, "parse sceneArray failure!!!");
        }

        LogWrapper.d(TAG, "waitingDownloadScenesQueue size:" + mWaitingDownloadScenesQueue.size());

        // mark preload Scenes begin
        onLoadScenesStart();

        if (mWaitingDownloadScenesQueue.isEmpty()) {
            //直接通知CP下载成功
            responseFinishedToNativeDirectly(mCurrentDownloadName);
            LogWrapper.d(TAG, "No need to fetch scene !");
            return;
        }

        responseDownloadToNative("", false, 0, STAGE_FETCH, false);

        initLoadingProgressController(allGroups);

        ThreadUtils.runOnUIThread(new Runnable() {

            @Override
            public void run() {
                if (!BridgeHelper.getInstance().isRunning()) {
                    LogWrapper.d(TAG, "Game have been shutdown, cancel download request!");
                    return;
                }

                setInSilentDownload(false);

                if (mCurrentDownloadGroup == null || !allGroups.contains(mCurrentDownloadGroup.getName())) {
                    setInCancelDownload(true);
                    // mIsInDownloadingGroup has to be set to false before cancel current download, since the cancel operation may return directly.
                    seIsInDownloadingGroup(false);

                    mRuntimeGroup.cancelCurrentDownload(new OnCancelDownloadListener() {

                        @Override
                        public void onFinish() {
                            setInCancelDownload(false);
                            // 清除原有的下载链表
                            mWaitingDownloadGroups.clear();
                            // 清除待正在的 Scene
                            mCurrentDownloadSceneInfo = null;
                            // 依次下载更新场景的资源
                            fetchNextSceneFromPreloadScenes();
                        }

                        @Override
                        public void onWaitUnzip() {
                            LogWrapper.d(TAG, "onWaitUnzip ...");
                        }

                        @Override
                        public void onCancel() {
                            // Since we will cancel current download, the current download needs to be started again.
                            // Therefore, decrease the index to make getNextResGroupForDownload happy.
                            decreaseIndexOfSilentDownload();
                        }
                    });

                } else {
                    LogWrapper.d(TAG, "The group ( " + mCurrentDownloadGroup.getName() + " ) is downloading!");
                }
            }
        });
    }

    private void initLoadingProgressController(final List<String> allGroups) {
        if (allGroups == null || mLoadingProgressController == null || allGroups.isEmpty())
            return;

        mLoadingProgressController.reset();
        int groupSize = allGroups.size();
        float connectProgress = 1.2f;
        if (groupSize > 20) {
            connectProgress = 20 / groupSize;
        }

        for (String groupName : allGroups) {
            GroupInfo groupInfo = mResourceConfigInfo.getGroupInfoByName(groupName);
            mLoadingProgressController.addStageWithProgress(GROUP_DOWNLOAD_CONNECT + groupInfo.getName(), connectProgress);
            mLoadingProgressController.addStageWithWeight(GROUP_DOWNLOAD_FETCH + groupInfo.getName(), groupInfo.getSize() * 0.85f);
            mLoadingProgressController.addStageWithWeight(GROUP_DOWNLOAD_UNZIP + groupInfo.getName(), groupInfo.getSize() * 0.1f);
        }
    }

    private void responseDownloadToNative(final String groupName, final boolean isCompleted, final float percent, int stage, final boolean isFailed) {

        if (!isFailed)
            onLoadingScenes(groupName, isCompleted, percent, stage);

        if (mExt == 0 && mPreloadInfoListener != null) {
            return;
        }

        //游戏没有实现自定义交互界面，并且宿主也没有实现自定义交互界面
        if (mExt == 0 && mPreloadInfoListener == null) {
            if (isCompleted) { //已完成，关闭进度条及对话框
                if (mCustomProgressDialog != null) {
                    mCustomProgressDialog.cancel();
                }
                if (mPromptDialog != null) {
                    mPromptDialog.cancel();
                }
            } else if(isFailed) {
                showErrorDialog(mErrorCode);
            } else { //未完成，新建进度条或者更新进度
                if (mCustomProgressDialog != null && mCustomProgressDialog.isShowing()) {
                    mCustomProgressDialog.setProgress(percent);
                } else {
                    ThreadUtils.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCustomProgressDialog == null) {
                                if (isZh()) {
                                    mCustomProgressDialog = new CustomProgressDialog(Utils.getCurrentContext(), "正在下载游戏必要资源，请稍候…");
                                } else {
                                    mCustomProgressDialog = new CustomProgressDialog(Utils.getCurrentContext(), "Downloading game assets, patience is a virtue :)");
                                }
                                mCustomProgressDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                                    @Override
                                    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                                if (isZh()) {
                                                    showPromptDialogCN("returnkey");
                                                } else {
                                                    showPromptDialogEN("returnkey");
                                                }
                                            }
                                        }
                                        return false;
                                    }
                                });
                            }

                            mCustomProgressDialog.setProgress(percent);

                            if (!mCustomProgressDialog.isShowing()){
                                mCustomProgressDialog.show();
                            }
                        }
                    });
                }

                //未完成时不通知native层进度消息
                return;
            }
        }

        if (useNewPreloadResponseMode) {
            BridgeHelper.getInstance().preloadResponse2(isCompleted, isFailed, mErrorCode, percent, mDownloadSpeed, groupName);
        } else {
            final String responseJson = getPreloadResponseJson(groupName, isCompleted, percent, stage, isFailed);
            BridgeHelper.getInstance().preloadResponse(responseJson, isCompleted, mExt);
        }
    }

    private void showErrorDialog(final String errCode) {

        if(mCustomProgressDialog != null)
            mCustomProgressDialog.cancel();

        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (isZh()) {
                    showPromptDialogCN(errCode);
                } else {
                    showPromptDialogEN(errCode);
                }
            }
        });
    }

    private void showPromptDialogCN(final String errorCode) {
        if (mPromptDialog == null) {
            mPromptDialog = new PromptDialog(Utils.getCurrentContext());
        }
        if (!mPromptDialog.isShowing()) {
            mPromptDialog.show();
        }

        View.OnClickListener negativeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPromptDialog.cancel();
                BridgeHelper.getInstance().quitGame();
            }
        };

        View.OnClickListener positiveListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPromptDialog.cancel();
                retryPreloadScenes();
                if(mCustomProgressDialog != null)
                    mCustomProgressDialog.show();
            }
        };

        if (errorCode.equals("err_no_space")) {
            mPromptDialog.setMessage("存储空间满了 \n 请释放出一些空间后继续!");
            mPromptDialog.setPositiveButton("继续", positiveListener);
            mPromptDialog.setNegativeButton("取消", negativeListener);

        } else if (errorCode.equals("err_verify")) {
            mPromptDialog.setMessage("文件校验失败,请重试!");
            mPromptDialog.setPositiveButton("重试", positiveListener);
            mPromptDialog.setNegativeButton("取消", negativeListener);

        } else if (errorCode.equals("err_game_res_expired")) {
            mPromptDialog.setMessage("游戏资源过期，请重试！");
            mPromptDialog.setPositiveButton("重试", positiveListener);
            mPromptDialog.setNegativeButton("取消", negativeListener);

        } else if (errorCode.equals("err_network")) {
            mPromptDialog.setMessage("下载意外中断 \n 请确认网络通畅后点击继续!");
            mPromptDialog.setPositiveButton("继续", positiveListener);
            mPromptDialog.setNegativeButton("取消", negativeListener);

        } else if (errorCode.equals("returnkey")) {
            mPromptDialog.setMessage("下载未完成，是否退出？");
            mPromptDialog.setPositiveButton("取消", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPromptDialog.cancel();
                }
            });
            mPromptDialog.setNegativeButton("确认退出", negativeListener);

        }
    }

    private void showPromptDialogEN(final String errorCode) {
        if (mPromptDialog == null) {
            mPromptDialog = new PromptDialog(Utils.getCurrentContext());
        }
        if (!mPromptDialog.isShowing()) {
            mPromptDialog.show();
        }

        View.OnClickListener negativeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPromptDialog.cancel();
                BridgeHelper.getInstance().quitGame();
            }
        };

        View.OnClickListener positiveListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPromptDialog.cancel();
                retryPreloadScenes();
                if(mCustomProgressDialog != null)
                    mCustomProgressDialog.show();
            }
        };

        if (errorCode.equals("err_no_space")) {
            mPromptDialog.setMessage("No more storage space! Free up some \n space before pressing continue!");
            mPromptDialog.setPositiveButton("Continue", positiveListener);
            mPromptDialog.setNegativeButton("Cancel", negativeListener);

        } else if (errorCode.equals("err_verify")) {
            mPromptDialog.setMessage("Oops! Something went wrong.\n Download failed.");
            mPromptDialog.setPositiveButton("Retry", positiveListener);
            mPromptDialog.setNegativeButton("Cancel", negativeListener);

        } else if (errorCode.equals("err_game_res_expired")) {
            mPromptDialog.setMessage("Bah! game res is expired!");
            mPromptDialog.setPositiveButton("Retry", positiveListener);
            mPromptDialog.setNegativeButton("Cancel", negativeListener);

        } else if (errorCode.equals("err_network")) {
            mPromptDialog.setMessage("Bah! Download interrupted! \n Pick up where you left off? ");
            mPromptDialog.setPositiveButton("Of course", positiveListener);
            mPromptDialog.setNegativeButton("No", negativeListener);

        } else if (errorCode.equals("returnkey")) {
            mPromptDialog.setMessage("Download incomplete, \n are you sure you want to exit?");
            mPromptDialog.setPositiveButton("No, must finish!", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPromptDialog.cancel();
                }
            });
            mPromptDialog.setNegativeButton("YES", negativeListener);
        }
    }

    private boolean isZh() {
        String language = Locale.getDefault().getLanguage();
        if (Locale.CHINESE.getLanguage().equals(language))
            return true;
        else
            return false;
    }

    private void responseFinishedToNativeDirectly(String groupName) {
        if (useNewPreloadResponseMode) {
            BridgeHelper.getInstance().preloadResponse2(true, false, "", 100.0f, mDownloadSpeed, groupName);
        } else {
            String responseJson = getPreloadResponseJson(groupName, true, 100.0f, STAGE_UNZIP, false);
            BridgeHelper.getInstance().preloadResponse(responseJson, true, mExt);
        }
    }

    // 渠道调用通知游戏完成
    public void notifyPreloadFinished() {
        if (mExt == 0 && mPreloadInfoListener != null && mPreloadSceneJson != null) {
            if (useNewPreloadResponseMode) {
                BridgeHelper.getInstance().preloadResponse2(true, false, "", 100.0f, mDownloadSpeed, mCurrentDownloadName);
            } else {
                final String responseJson = getPreloadResponseJson(mCurrentDownloadName, true, 100.0f, STAGE_UNZIP, false);
                BridgeHelper.getInstance().preloadResponse(responseJson, true, mExt);
            }

            mPreloadSceneJson = null;
        }
    }

    private String getPreloadResponseJson(String groupName, boolean isCompleted, float percent, int stage, boolean isFailed) {

        return "{" +
                "\"groupName\":\"" + groupName + "\"" + "," +
                "\"isCompleted\":" + Boolean.toString(isCompleted) + "," +
                "\"percent\":" + String.format(Locale.US, "%.02f", percent) + "," +
                "\"stage\":" + Integer.toString(stage) + "," +
                "\"isFailed\":" + Boolean.toString(isFailed) + "," +
                "\"downloadSpeed\":" + String.format(Locale.US, "%.02f", mDownloadSpeed) + "," +
                "\"errorCode\":" + (isFailed ? ("\"" + mErrorCode + "\"") : "\"\"") +
                "}";
    }

    private void onLoadScenesStart() {
        if (mPreloadInfoListener != null && mExt == 0) {
            mBeginPreloadScenes = true;
        }
    }

    private void onSceneBeginLoading(String sceneName, long totallySize) {
        if (mPreloadInfoListener != null && mExt == 0 && mBeginPreloadScenes) {
            mPreloadInfoListener.onSceneBeginLoading(sceneName, totallySize);
        }
    }

    private void onLoadingScenes(String groupName, boolean isCompleted, float percent, int stage) {
        if (mPreloadInfoListener != null && mExt == 0 && mBeginPreloadScenes) {
            LogWrapper.d(TAG, "onLoadingScenes groupName=" + groupName + ",isCompleted=" + isCompleted + ",percent=" + percent + ",stage=" + stage);
            String sceneName = mCurrentDownloadSceneInfo != null ? mCurrentDownloadSceneInfo.getName() : "";
            mPreloadInfoListener.onLoadingScenes(sceneName, groupName, isCompleted, mDownloadSpeed, percent, stage);
            if (percent == 100 && isCompleted) {
                mBeginPreloadScenes = false;
            }
        }
    }

    public void setPreloadScenesCallback(IPreloadInfoListener listener) {
        mPreloadInfoListener = listener;
    }

    public void cancelPreloadResBundle() {
        mCancelPreloadResBundle = true;
        stopSilentDownload();
        if (mRuntimeGroup != null) {
            mRuntimeGroup.cancelCurrentDownload(new OnCancelDownloadListener() {
                @Override
                public void onCancel() {
                    if (mPreloadResBundleListener != null)
                        mPreloadResBundleListener.onCancel();
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

    public interface IPreloadInfoListener {
        void onSceneBeginLoading(String sceneName, long totallySize);

        void onLoadingScenes(String sceneName, String groupName, boolean isCompleted, float downloadSpeed, float percent, int stage);

        void onLoadingScenesFailure(int errorCode, String errorMsg, float percent, int stage);
    }

    public interface IResourceCompletedListener {
        void onCompleted();
    }

    public interface IPreloadResBundleListener {
        void onErrorOccurred(int errCode, String errMsg);

        void onSuccess();

        void onCancel();
    }
}
