package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.callback.OnCancelDownloadListener;
import com.skydragon.gplay.runtime.entity.resource.GameResourceConfigInfo;
import com.skydragon.gplay.runtime.entity.resource.GroupInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import static com.skydragon.gplay.runtime.protocol.FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH;

public final class RuntimeGroup {

    public static final String TAG = "RuntimeGroup";

    private static int sUnzipThreadPriority = Thread.NORM_PRIORITY;
    private GameResourceConfigInfo mResourceConfigInfo;
    private OnCancelDownloadListener mOnCancelDownloadListener = null;

    private String mPackageName = null;
    private String mDownloadUrl = null;
    private String mCurrentDownloadName = null;
    private String mPathCurrentGameResourceDir = null;

    private boolean mIsUnzipping = false;
    private int mRetryTimes = 0;
    private int mRetryTimesForVerifyFailed;

    public static void setUnzipThreadPriority(int priority) {
        sUnzipThreadPriority = priority;
    }


    public interface OnUpdateGroupListener {
        void onStartOfDownload();
        void onProgressOfDownload(long bytesWritten, long totalSize);
        void onSuccessOfDownload(long totalSize);
        void onFailureOfDownload(String errorMsg);
        void onSuccessOfUnzip();
        void onFailureOfUnzip(String errorMsg);
        void onProgressOfUnzip(float percent);
        boolean isUnzipInterrupted();
    }

    private static RuntimeGroup _instance;
    private RuntimeGroup() {}

    public static RuntimeGroup getInstance() {
        if(null == _instance) {
            _instance = new RuntimeGroup();
        }
        return _instance;
    }

    public void init(String packageName, String downloadUrl) {
        LogWrapper.d(TAG, "RuntimeGroup.init ...");

        mPackageName = packageName;
        mDownloadUrl = downloadUrl;

        mPathCurrentGameResourceDir = FileConstants.getGameResourceDir(packageName);

        mResourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();

        if (mResourceConfigInfo == null) {
            LogWrapper.e(TAG, "resource config should not be null");
        }
    }

    public void destroy() {
        mFileDownloader = null;
    }

    private boolean isMD5CorrectOfGroupZip(String groupName, String zipballPath) {
        LogWrapper.d(TAG, "Checking MD5 of ( " + zipballPath + " ) ...");
        if (!FileUtils.isFileModifiedByCompareMD5(zipballPath, findGroup(groupName).getMd5())) {
            LogWrapper.d(TAG, "The MD5 of group zip ( " + groupName + " ) is correct!");
            return true;
        }
        LogWrapper.e(TAG, "The MD5 of group zip ( " + groupName + " ) is wrong!");

        return false;
    }

    private FileDownloadHelper.FileDownloader mFileDownloader;
    private void fetchGroup(final String groupName, final boolean withUnzip, final OnUpdateGroupListener updateGroupListener) {
        String downloadTag = RuntimeConstants.DOWNLOAD_TAG_GROUP;
        if (groupName.equals("boot")) {
            downloadTag = RuntimeConstants.DOWNLOAD_TAG_BOOT_GROUP;
        }

        final GroupInfo resGroup = findGroup(groupName);

        mCurrentDownloadName = groupName;
        LogWrapper.d(TAG, "Fetch group ( " + groupName + " ) ...");

        final String saveTo;
        if (resGroup.getPath().endsWith(RuntimeConstants.SEVEN_G_FILE_SUFFIX)) {
            saveTo = FileConstants.getGroupArchivePath(mPackageName, groupName + RuntimeConstants.SEVEN_G_FILE_SUFFIX);
        }
        else {
            saveTo = FileConstants.getGroupArchivePath(mPackageName, groupName + RuntimeConstants.GIP_FILE_SUFFIX);
        }

        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(FileConstants.getGameDownloadDir(mPackageName));

        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download helper isn't null, please check it.");
        }

        FileDownloadDelegate fileDownloadDelegate = new FileDownloadDelegate() {
            @Override
            public void onStart(String tag) {
                if (mCurrentDownloadName != null && mCurrentDownloadName.equals(groupName)) {
                    updateGroupListener.onStartOfDownload();
                }
            }

            @Override
            public void onSuccess(File file) {
                // android-async-http has a bug, https://github.com/loopj/android-async-http/issues/772
                // this callback will be called when the download process is cancelled.
                // this solution assume we only download a group in the same time
                if (mCurrentDownloadName == null || !mCurrentDownloadName.equals(groupName)) {
                    LogWrapper.d(TAG, "Downloading group is (" + mCurrentDownloadName + "), ignore onSuccess() for group (" + groupName + ")");
                    return;
                }

                mFileDownloader = null;
                FileUtils.renameFile(saveToTemp, saveTo);

                if (!isMD5CorrectOfGroupZip(groupName, saveTo)) {
                    FileUtils.deleteFile(saveTo);
                    String error = "Download group(" + groupName + ") succeed, but md5 is wrong.";
                    LogWrapper.e(TAG, error);

                    if (!RuntimeEnvironment.downloadURLChanged && RuntimeLauncher.getInstance().offlineGameResExpired()) {
                        updateGroupListener.onFailureOfDownload(error + RuntimeConstants.DOWNLOAD_GAME_RES_EXPIRED);
                    }
                    else if (mRetryTimesForVerifyFailed > 0) {
                        mRetryTimesForVerifyFailed -= 1;
                        fetchGroup(groupName, withUnzip, updateGroupListener);
                    }
                    else {
                        updateGroupListener.onFailureOfDownload(error + RuntimeConstants.DOWNLOAD_VERIFY_WRONG);
                    }
                    return;
                }

                LogWrapper.d(TAG, "Download group(" + groupName + ") succeed!");
                updateGroupListener.onSuccessOfDownload(resGroup.getSize());

                setUnzipping(true);
                unzipGroup(saveTo, withUnzip, updateGroupListener);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                mDownloadSpeed = mFileDownloader.getDownloadSpeed();
                updateGroupListener.onProgressOfDownload(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {

                String error = "Download group(" + groupName + ") failed! " + errorMsg;
                LogWrapper.e(TAG, error);
                mFileDownloader = null;

                // 网络异常情况，重试下载
                if ((errorCode != ERROR_STORAGE_SPACE_NOT_ENOUGH) && retryAble()) {
                    fetchGroup(groupName, withUnzip, updateGroupListener);
                    return;
                }

                updateGroupListener.onFailureOfDownload(error);
            }

            @Override
            public void onCancel() {}
        };

        String url = mDownloadUrl + resGroup.getPath();
        mFileDownloader = FileDownloadHelper.downloadFile(downloadTag, url, saveToTemp, fileDownloadDelegate);
    }

    private int mDownloadSpeed = 0;

    public int getDownloadSpeed() {
        if (mFileDownloader != null) {
            return mDownloadSpeed;
        } else {
            return 0;
        }
    }

    void unzipGroup(final String groupArchiveFile, boolean withUnzip, final OnUpdateGroupListener lis) {

        if( !withUnzip ) {
            LogWrapper.d(TAG, "Don't unzip file " + groupArchiveFile);
            return;
        }
        LogWrapper.d(TAG, "Start to unzip with thread priority: " + sUnzipThreadPriority);

        ZipUtils.OnUnzipListener unzipListener = new ZipUtils.OnUnzipListener() {
            @Override
            public void onUnzipSucceed(final List<String> unzipFiles) {
                setUnzipping(false);
                FileUtils.deleteFile(groupArchiveFile);

                LogWrapper.d(TAG, "Unzip group ( " + groupArchiveFile + " ) succeed!");

                lis.onSuccessOfUnzip();
                if (mOnCancelDownloadListener != null) {
                    LogWrapper.d(TAG, "Unzipping DONE, notify cancel download was finished!");
                    mOnCancelDownloadListener.onFinish();
                    mOnCancelDownloadListener = null;
                }
            }

            @Override
            public void onUnzipFailed(String errorMsg) {
                String error = "Unzip ( " + groupArchiveFile + " )failed! errorMsg: " + errorMsg;
                LogWrapper.e(TAG, error);
                setUnzipping(false);
                lis.onFailureOfUnzip(error);
            }

            @Override
            public void onUnzipProgress(float percent) {
                lis.onProgressOfUnzip(percent);
            }

            @Override
            public void onUnzipInterrupt() {
                setUnzipping(false);

                String error = "Unzip group(" + groupArchiveFile + ") interrupted!";
                LogWrapper.w(TAG, error);
                lis.onFailureOfUnzip(error);
            }

            @Override
            public boolean isUnzipInterrupted() {
                return lis.isUnzipInterrupted();
            }

        };

        if (groupArchiveFile.endsWith(RuntimeConstants.SEVEN_G_FILE_SUFFIX))
            ZipUtils.unpack7zAsync(groupArchiveFile, mPathCurrentGameResourceDir, false, sUnzipThreadPriority, unzipListener);
        else
            ZipUtils.unpackZipAsync(groupArchiveFile, mPathCurrentGameResourceDir, false, sUnzipThreadPriority, unzipListener);
    }

    public GroupInfo findGroup(String groupName) {
        if(groupName == null || groupName.isEmpty())
            return null;

        GroupInfo ret = mResourceConfigInfo.getGroupInfoByName(groupName);
        if (ret == null)
            LogWrapper.e(TAG, "Could not find the group: " + groupName);
        return ret;
    }

    /**
     * @param groupName 资源包名
     * @param withUnzip 下载完成是否解压 : true 解压,  false 不解压;
     * @param retryTimes 网络异常重试次数
     * */
    public void updateGroup(String groupName, boolean withUnzip, int retryTimes, OnUpdateGroupListener lis) {
        LogWrapper.v(TAG, "UpdateGroup :" + groupName + " , unzip: " + withUnzip + " , retryTimes :" + retryTimes);
        GroupInfo resGroup = findGroup(groupName);
        if (resGroup == null) {
            LogWrapper.e(TAG, "Can't find '" + groupName + "' section in 'res_groups' of 'manifest.json'");
            return;
        }

        // User may cancel loading while unzipping, so the group zip file may exists on the sdcard.
        // We need to check the zip file at first.
        String groupZipPath;
        if (resGroup.getPath().endsWith(RuntimeConstants.SEVEN_G_FILE_SUFFIX)) {
            groupZipPath = FileConstants.getGroupArchivePath(mPackageName, groupName + RuntimeConstants.SEVEN_G_FILE_SUFFIX);
        }
        else {
            groupZipPath = FileConstants.getGroupArchivePath(mPackageName, groupName + RuntimeConstants.GIP_FILE_SUFFIX);
        }

        if (FileUtils.isExist(groupZipPath)) {
            if (isMD5CorrectOfGroupZip(groupName, groupZipPath)) {
                LogWrapper.d(TAG, "The group ( " + groupZipPath + " ) exists, unzip it ...");
                lis.onSuccessOfDownload(resGroup.getSize());
                unzipGroup(groupZipPath, withUnzip, lis);
                return;
            } else {
                LogWrapper.d(TAG, "MD5 isn't correct, delete the zip file");
                FileUtils.deleteFile(groupZipPath);
            }
        }

        HashMap<String, String> resMD5Map = mResourceConfigInfo.getResMD5Map();
        /*
        * 如果本地的资源都已经具备，则通知下载成功。
        * */
        if (resGroup.isCompletedGroupCheckedByMD5(resMD5Map)) {
            LogWrapper.v(TAG, "No need to download (" + groupName + "), Local resource checking by md5 is Completed ! ");
            lis.onSuccessOfUnzip();
            return;
        }

        if(!RuntimeEnvironment.downloadURLChanged && RuntimeLauncher.getInstance().offlineGameResExpired()) {
            String error = "Download group(" + groupName + ") failed!";
            LogWrapper.w(TAG, error);
            lis.onFailureOfDownload(error + RuntimeConstants.DOWNLOAD_GAME_RES_EXPIRED);
            return;
        }

        // If the group's md5 isn't correct
        // Download a new group from server
        mRetryTimes = retryTimes;
        mRetryTimesForVerifyFailed = 1;
        LogWrapper.d(TAG, "to fetch:" + groupName);
        fetchGroup(groupName, withUnzip, lis);
    }

    protected boolean isUnzipping() {
        return mIsUnzipping;
    }

    private boolean retryAble() {
        return (-- mRetryTimes) >= 0;
    }

    private void setUnzipping(boolean unzipping) {
        mIsUnzipping = unzipping;
    }

    public void cancelCurrentDownload(OnCancelDownloadListener lis) {
        mCurrentDownloadName = null;

        if (mFileDownloader != null) {
            mFileDownloader.cancel();
            mFileDownloader = null;
            lis.onCancel();
        }

        if (isUnzipping()) {
            LogWrapper.d(TAG, "Unzipping, waiting ...");
            lis.onWaitUnzip();
            mOnCancelDownloadListener = lis;
        } else {
            LogWrapper.d(TAG, "No unzipping, notify cancel finished ...");
            lis.onFinish();
        }
    }
}
