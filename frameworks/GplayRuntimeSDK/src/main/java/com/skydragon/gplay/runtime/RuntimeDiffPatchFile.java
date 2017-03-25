package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.runtime.RuntimeCompatibilityInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeDiffPatchInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.List;

import static com.skydragon.gplay.runtime.protocol.FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH;

public final class RuntimeDiffPatchFile {

    public static final String TAG = "RuntimeDiffPatchFile";

    private String mDiffPatchDownloadUrl;
    private RuntimeDiffPatchInfo mDiffPatchInfo;

    public interface OnDiffPatchUpdateListener {
        void onDownloadProgress(long downloadedSize, long totalSize);
        void onUnzipStart();
        void onUnzipProgress(float percent);
        void onFailed(int errorType, String errorMsg);
        void onSuccess();
        boolean isUnzipInterrupted();
    }

    void init(RuntimeCompatibilityInfo compatibility) {
        LogWrapper.d(TAG, "init ...");

        mDiffPatchInfo = compatibility.getRuntimeDiffPatchInfo();
    }

    boolean isDiffPatchShareLibraryUpdated() {
        LogWrapper.d(TAG, "isDiffPatchShareLibraryUpdated " + mDiffPatchInfo);
        // 游戏使用 so 存放的目录
        if (null == mDiffPatchInfo) return false;

        String diffPatchSo = RuntimeLauncher.getInstance().getDiffPatchShareLibraryPath();
        File file = new File(diffPatchSo);
        LogWrapper.d(TAG, "isDiffPatchShareLibraryUpdated file(" + diffPatchSo + ") exists " + file.exists());
        return file.exists() && !FileUtils.isFileModifiedByCompareMD5(diffPatchSo, mDiffPatchInfo.getSoMd5());
    }

    void updateDiffPatchSo(final OnDiffPatchUpdateListener lis) {
        if (isDiffPatchShareLibraryUpdated()) {
            lis.onSuccess();
        } else {
            mDiffPatchDownloadUrl = mDiffPatchInfo.getDownloadUrl();
            fetchDiffPatchZip(lis);
        }
    }

    private String getDiffPatchDownloadDirectory() {
        // 标准 runtime，下载到统一的 runtime 下载目录中去
        StringBuilder sb = new StringBuilder();
        sb.append(FileConstants.getDownloadDir());
        sb.append("diffpatch/");
        return sb.toString();
    }

    private int mRetryTimes = 1;

    private FileDownloadHelper.FileDownloader mFileDownloader;
    private void fetchDiffPatchZip(final OnDiffPatchUpdateListener updatePatchListener) {
        final String patchName = "diffpatch.so";
        LogWrapper.d(TAG, "Fetch Diff patch...");

        String diffPatchDownloadDir = getDiffPatchDownloadDirectory();
        final String saveTo = diffPatchDownloadDir + patchName;
        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(diffPatchDownloadDir);

        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download helper isn't null, please check it.");
        }

        FileDownloadDelegate delegate = new FileDownloadDelegate() {
            @Override
            public void onStart(String tag) {
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                updatePatchListener.onDownloadProgress(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                mFileDownloader = null;
                String error = "Download patch library failed!" + errorMsg;
                LogWrapper.e(TAG, error);
                if (errorCode == ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                    updatePatchListener.onFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                } else {
                    if (mRetryTimes > 0) {
                        mRetryTimes -= 1;
                        fetchDiffPatchZip(updatePatchListener);
                    }
                    else {
                        updatePatchListener.onFailed(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, error);
                    }
                }
            }

            @Override
            public void onCancel() {}

            @Override
            public void onSuccess(File file) {
                mFileDownloader = null;
                LogWrapper.d(TAG, "Download patch library succeed. save to:" + saveTo);
                FileUtils.renameFile(saveToTemp, saveTo);

                updatePatchListener.onUnzipStart();
                unzipDiffPatch(saveTo, updatePatchListener);
            }
        };

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_GAME_PATCH, mDiffPatchDownloadUrl, saveToTemp, delegate);
    }

    private void unzipDiffPatch(final String diffPatchZipFile, final OnDiffPatchUpdateListener updateSoListener) {
        String soDownloadDir = getDiffPatchDownloadDirectory();

        ZipUtils.unpackZipAsync(diffPatchZipFile, soDownloadDir, new ZipUtils.OnUnzipListener() {

            @Override
            public void onUnzipSucceed(List<String> listFiles) {
                LogWrapper.d(TAG, "unzip patch library success!");

                FileUtils.deleteFile(diffPatchZipFile);

                copyDiffPatchToGameDir(listFiles.get(0), updateSoListener);
            }

            @Override
            public void onUnzipProgress(float percent) {
                LogWrapper.d(TAG, "Unzip patch library percent : " + percent);
                updateSoListener.onUnzipProgress(percent);
            }

            @Override
            public void onUnzipFailed(String errorMsg) {
                String error = "Unzip patch library failed!" + errorMsg;
                LogWrapper.e(TAG, error);

                FileUtils.deleteFile(diffPatchZipFile);
                if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
                    updateSoListener.onFailed(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                }
                else {
                    updateSoListener.onFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
                }
            }

            @Override
            public void onUnzipInterrupt() {
                LogWrapper.w(TAG, "Unzip patch library interrupted!");
            }

            @Override
            public boolean isUnzipInterrupted() {
                return updateSoListener.isUnzipInterrupted();
            }
        });
    }

    private void copyDiffPatchToGameDir(final String diffPatchSoFile, final OnDiffPatchUpdateListener lis) {
        String soDownloadDir = getDiffPatchDownloadDirectory();

        String sDiffPatchPath = RuntimeLauncher.getInstance().getDiffPatchShareLibraryPath();
        LogWrapper.d(TAG, "FileUtils.copyFile( " + soDownloadDir + diffPatchSoFile + ", " + sDiffPatchPath + " )");
        // 游戏使用的 patch 路径
        FileUtils.copyFile(soDownloadDir + diffPatchSoFile, sDiffPatchPath);

        LogWrapper.d(TAG, "FileUtils.deleteFile( " + soDownloadDir + " )");
        FileUtils.deleteFile(soDownloadDir);

        if (isDiffPatchShareLibraryUpdated()) {
            lis.onSuccess();
        } else {
            LogWrapper.d(TAG, "DiffPatch SO file is not valid!");
            lis.onFailed(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.COPY_FAILED);
        }
    }
}
