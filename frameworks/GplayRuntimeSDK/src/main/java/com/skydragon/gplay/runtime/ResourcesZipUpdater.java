package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.resource.ResourcesZipTemplate;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ResourcesZipUpdater {

    private static String TAG = "ResourcesZipUpdater";
    private ResourcesZipTemplate _resZipTemplate;
    private String _tmpSaveDir;
    private String _destDir;
    private int _retryTimes = 1;

    public static ResourcesZipUpdater createUpdater(ResourcesZipTemplate resZipTemplete, String tmpDir, String destDir) {
        ResourcesZipUpdater resZipUpdater = new ResourcesZipUpdater();
        resZipUpdater._resZipTemplate = resZipTemplete;
        resZipUpdater._tmpSaveDir = FileUtils.ensurePathEndsWithSlash(tmpDir);
        resZipUpdater._destDir = FileUtils.ensurePathEndsWithSlash(destDir);
        return resZipUpdater;
    }

    private ResourcesZipUpdater() {}

    public boolean isResourcesIntegrity() {
        Map<String, String> resources = _resZipTemplate.getResources();
        File file;
        for (Iterator<String> ite = resources.keySet().iterator(); ite.hasNext(); ) {
            String fileName = ite.next();

            file = new File(_destDir + fileName);

            if (!file.exists()) {
                return false;
            }

            String md5 = resources.get(fileName);

            if(FileUtils.isFileModifiedByCompareMD5(file.getAbsolutePath(), md5)) {
                return false;
            }
        }

        return true;
    }

    public void updateResources(final String zipFileName, final String downloadTag, final ResourcesUpdateListener listener) {

        final String saveTo = _tmpSaveDir + zipFileName;
        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        File zipFile = new File(saveTo);
        if(zipFile.exists()
                && FileUtils.isFileModifiedByCompareMD5(saveTo, _resZipTemplate.getMD5())) {
            unzipResources(saveTo, listener);
            return;
        }

        final FileDownloadDelegate delegate = new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {}

            @Override
            public void onSuccess(File file) {
                FileUtils.renameFile(saveToTemp, saveTo);
                unzipResources(saveTo, listener);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                listener.onDownloadProgress(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                if(!retryAble()) {
                    String error = "Download failed ! " + errorMsg;
                    LogWrapper.e(TAG, error);
                    if (errorCode == FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                        listener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                    } else {
                        listener.onFailure(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, error);
                    }
                    return;
                }
                // retry it
                updateResources(zipFileName, downloadTag, listener);
            }

            @Override
            public void onCancel() {}
        };

        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                FileDownloadHelper.downloadFile(downloadTag, _resZipTemplate.getDownloadUrl(), saveToTemp, delegate);
            }
        });
    }

    private void unzipResources(final String zipFilePath, final ResourcesUpdateListener updateSoListener) {

        ZipUtils.OnUnzipListener onUnzipListener = new ZipUtils.OnUnzipListener() {
            @Override
            public void onUnzipSucceed(List<String> unzipFiles) {
                FileUtils.deleteFile(_tmpSaveDir);
                updateSoListener.onSuccess();
            }

            @Override
            public void onUnzipProgress(float percent) {
                updateSoListener.onUnzipProgress(percent);
            }

            @Override
            public void onUnzipFailed(String errorMsg) {

                String error = "Unzip (" + zipFilePath + ") failed! " + errorMsg;
                LogWrapper.e(TAG, error);

                FileUtils.deleteFile(zipFilePath);
                if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
                    updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                } else {
                    updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
                }
            }

            @Override
            public void onUnzipInterrupt() {
                LogWrapper.d(TAG, "Unzip engine ( " + zipFilePath + " ) interrupted!");
            }

            @Override
            public boolean isUnzipInterrupted() {
                return false;
            }
        };

        ZipUtils.unpack7zAsync(zipFilePath, _destDir, false, Thread.NORM_PRIORITY, onUnzipListener);
    }

    private boolean retryAble() {
        return (-- _retryTimes >= 0);
    }

    interface ResourcesUpdateListener {
        void onSuccess();
        void onFailure(int code, String errorMsg);
        void onDownloadProgress(long downloadedSize, long totalSize);
        void onUnzipProgress(float percent);
    }
}
