package com.skydragon.gplay.runtime;


import com.skydragon.gplay.runtime.entity.runtime.RuntimeCommonResInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeCompatibilityInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class EngineCommonRes {

    private static final String TAG = "EngineCommonRes";

    private String mCommonResDownloadUrl;
    private String mDownloadSaveDir;
    private String mEngineCommonResDir;
    private String mArchiveMD5Path;

    private RuntimeCommonResInfo mRuntimeCommonResInfo;
    private RuntimeCompatibilityInfo mRuntimeCompatibilityInfo;
    private HashMap<String, String> mFileMap;
    private int mUnzipThreadPriority = Thread.NORM_PRIORITY;


    public interface OnCommonResUpdateListener {

        void onProgress(long downloadedSize, long totalSize);

        void onDownloadFailure(String errMsg);

        void onUnzipStart();

        void onUnzipProgress(float percent);

        void onUnzipFailed(String errMsg);

        void onSuccess();

        boolean isUnzipInterrupted();
    }

    void init(RuntimeCompatibilityInfo runtimeCompatibilityInfo, String engineType, String engineVersion) {
        LogWrapper.d(TAG, "init EngineCommonRes!");
        mRuntimeCompatibilityInfo = runtimeCompatibilityInfo;
        mRuntimeCommonResInfo = mRuntimeCompatibilityInfo.getRuntimeCompatibility(engineType, engineVersion).getCommonResource();
        if (mRuntimeCommonResInfo == null) {
            return;
        }

        mDownloadSaveDir = FileConstants.getDownloadDir() + "common_res/";
        mEngineCommonResDir = getEngineCommonResDir();
        mArchiveMD5Path = mEngineCommonResDir + "zipMD5.txt";
        mFileMap = mRuntimeCommonResInfo.getFileMap();
        if (mFileMap.isEmpty()) {
            LogWrapper.w(TAG, "current engine " + mRuntimeCommonResInfo.getVersionName() + " don't exist files");
        }
    }

    boolean isNeedUpdateEngineCommonRes() {
        if (mRuntimeCommonResInfo == null) {
            return false;
        }

        String archiveMD5 = getArchiveMD5();
        if (archiveMD5 != null) {
            LogWrapper.d(TAG, "local zip md5:" + archiveMD5 + "<====>remote md5:" + mRuntimeCommonResInfo.getZipMD5());
            if (!archiveMD5.equalsIgnoreCase(mRuntimeCommonResInfo.getZipMD5())) {
                LogWrapper.d(TAG, "zip md5 is wrong! common_res need update!");
                return true;
            }
            Iterator it = mFileMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                String fileName = entry.getKey().toString();
                String filePath = mEngineCommonResDir + fileName;
                if (!FileUtils.isExist(filePath)) {
                    LogWrapper.d(TAG, "local file isn't completed, common_res need update! miss file:" + filePath);
                    return true;
                }
            }
            LogWrapper.d(TAG, "local file is completed,common res don't need update!");
            return false;
        }
        LogWrapper.d(TAG, "local zip md5 is null,common_res need update!");
        return true;
    }

    private FileDownloadHelper.FileDownloader mFileDownloader;
    void fetchEngineCommonResZip(final OnCommonResUpdateListener lis) {
        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download handle isn't null, please check it.");
            return;
        }
        FileUtils.deleteFile(mEngineCommonResDir);
        mCommonResDownloadUrl = mRuntimeCommonResInfo.getUrl();

        String zipName = "common_res.7g";
        final String saveTo = mDownloadSaveDir + zipName;
        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(mDownloadSaveDir);

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_COMMON_RES, mCommonResDownloadUrl, saveToTemp, new FileDownloadDelegate() {
            @Override
            public void onStart(String tag) {
                LogWrapper.d(TAG, "start download common_res!");
            }

            @Override
            public void onSuccess(File file) {
                if (mFileDownloader == null) {
                    LogWrapper.w(TAG, "FileDownloadHelpter is null!");
                } else {
                    mFileDownloader = null;
                    FileUtils.renameFile(saveToTemp, saveTo);
                    if (!FileUtils.isFileModifiedByCompareMD5(saveTo, mRuntimeCommonResInfo.getZipMD5())) {
                        LogWrapper.d(TAG, "Download common_res is success!");
                        lis.onUnzipStart();
                        unzipEngineCommonRes(lis);
                    } else {
                        FileUtils.deleteFile(saveTo);
                        lis.onDownloadFailure("download md5 is wrong!");
                    }
                }
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                lis.onProgress(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download common_res failed" + errorMsg;
                LogWrapper.d(TAG, error);
                mFileDownloader = null;
                lis.onDownloadFailure(error);
            }

            @Override
            public void onCancel() {

            }
        });

    }

    private void unzipEngineCommonRes(final OnCommonResUpdateListener lis) {
        final String zipsaveTo = mDownloadSaveDir + "common_res.7g";
        ZipUtils.unpack7zAsync(zipsaveTo, mEngineCommonResDir, false, mUnzipThreadPriority, new ZipUtils.OnUnzipListener() {
            @Override
            public void onUnzipSucceed(List<String> unzipFiles) {
                LogWrapper.d(TAG, "unzip common res success!");
                FileUtils.deleteFile(mArchiveMD5Path);
                FileUtils.writeStringToFile(mArchiveMD5Path, FileUtils.getFileMD5(zipsaveTo));
                FileUtils.deleteFile(zipsaveTo);
                lis.onSuccess();
            }

            @Override
            public void onUnzipProgress(float percent) {
                LogWrapper.d(TAG, "Unzip common_res percent : " + percent);
                lis.onUnzipProgress(percent);
            }

            @Override
            public void onUnzipFailed(String errorMsg) {
                FileUtils.deleteFile(mDownloadSaveDir);
                LogWrapper.w(TAG, "unzip common_res is failed!");
                lis.onUnzipFailed(errorMsg);
            }

            @Override
            public void onUnzipInterrupt() {
                LogWrapper.w(TAG, "unzip common_res inteerrupted!");
            }

            @Override
            public boolean isUnzipInterrupted() {
                return lis.isUnzipInterrupted();
            }
        });
    }

    /**
     * 获取公用引擎资源路径
     */
    private String getEngineCommonResDir() {
        return RuntimeLauncher.getInstance().getEngineCommonResDir();
    }

    /**
     * 获取本地保存压缩zip的md5值
     */
    private String getArchiveMD5() {
        return FileUtils.readStringFromFile(new File(mArchiveMD5Path));
    }
}
