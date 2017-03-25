package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.runtime.RuntimeEngineSupportInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeShareLibraryArchInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeShareLibraryFileInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.List;

public class EngineStandardLibrary {
    private static final String TAG = "EngineStandardLibrary";

    private String mArchiveDownloadDir;

    private RuntimeShareLibraryArchInfo mLibrariesInfo;
    private String mEngineType;
    private String mEngineVersion;
    private String mDeviceArch;
    private String mSharedLibDir;

    private int mDownloadFailRetryTimes = 1;
    private String mCurrDownloadArchiveName;

    void init( String engine, String engineVersion, String arch, RuntimeEngineSupportInfo compatibility) {
        mSharedLibDir = RuntimeLauncher.getInstance().getStandardLibraryDirectory();
        mEngineType = engine;
        mEngineVersion = engineVersion;
        mDeviceArch = arch;
        mLibrariesInfo = compatibility.getShareLibraryArchInfo();
        mArchiveDownloadDir = getLibrariesDownloadDir();

        LogWrapper.v(TAG, "init " + mEngineType + " , " + mEngineVersion + " , " + mArchiveDownloadDir);
    }

    boolean isLibrariesUpdated() {
        // 游戏使用 so 存放的目录
        String sharedLibDir = mSharedLibDir;

        List<RuntimeShareLibraryFileInfo> list = mLibrariesInfo.getListSoFiles();
        for(RuntimeShareLibraryFileInfo soInfo : list) {
            String soName = soInfo.getName();
            String soFile = sharedLibDir + soName;
            LogWrapper.v(TAG, "isLibrariesUpdated （" + soFile + ")");
            boolean ret = FileUtils.isFileModifiedByCompareMD5(soFile, soInfo.getMd5());
            if(ret) return false;
        }

        LogWrapper.d(TAG, "Engine standard libraries are correct ? " + true);
        return true;
    }

    void updateLibraries(final OnShareLibraryUpdateListener lis) {
        if (isLibrariesUpdated()) {
            lis.onSuccess();
        } else {
            fetchLibraries(lis);
        }
    }

    private String getLibrariesDownloadDir() {
        // 标准 runtime，下载到统一的 runtime 下载目录中去
        StringBuilder sb = new StringBuilder(FileConstants.getDownloadDir());
        sb.append(mEngineType);
        sb.append(File.separator);
        sb.append(mEngineVersion);
        sb.append(File.separator);
        return sb.toString();
    }

    private FileDownloadHelper.FileDownloader mFileDownloader;
    private void fetchLibraries(final OnShareLibraryUpdateListener updateSoListener) {
        final String archiveFileName = getArchiveSaveFileName();
        mCurrDownloadArchiveName = archiveFileName;
        LogWrapper.d(TAG, "Fetch engine standard libraries: " + archiveFileName);

        FileUtils.deleteFile(mArchiveDownloadDir);
        final String saveTo = mArchiveDownloadDir + archiveFileName;
        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(mArchiveDownloadDir);

        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download helper isn't null, please check it.");
        }

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_RUNTIME_SO, mLibrariesInfo.getZipUrl(),
                saveToTemp, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {}

            @Override
            public void onSuccess(File file) {
                // android-async-http has a bug,
                // https://github.com/loopj/android-async-http/issues/772
                // this callback will be called when the download process is cancelled.
                // this solution assume we only download a group in the same time
                if (mCurrDownloadArchiveName == null || !mCurrDownloadArchiveName.equals(archiveFileName)) {
                    LogWrapper.d(TAG, "Downloading standard libraries is (" + mCurrDownloadArchiveName + "), ignore onSuccess() for " + archiveFileName);
                    return;
                }

                mFileDownloader = null;
                FileUtils.renameFile(saveToTemp, saveTo);

                LogWrapper.d(TAG, "Download standard libraries " + archiveFileName + " succeed!");

                unzipLibrariesArchive(updateSoListener);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                updateSoListener.onProgress(downloadedSize, totalSize);
            }

             @Override
             public void onFailure(int errorCode, String errorMsg) {
                 String error = "Download standard libraries(" + archiveFileName + ") failed!" + errorMsg;
                 LogWrapper.e(TAG, error);
                 mFileDownloader = null;
                 if (errorCode == FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                     updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                 } else {
                     if (mDownloadFailRetryTimes > 0){
                         mDownloadFailRetryTimes -= 1;
                         fetchLibraries(updateSoListener);
                     } else {
                         updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, error);
                     }
                 }
             }

            @Override
             public void onCancel() {}
        });
    }

    private void unzipLibrariesArchive(final OnShareLibraryUpdateListener updateSoListener) {
        String soGipName = getArchiveSaveFileName();

        final String saveTo = mArchiveDownloadDir + soGipName;

        ZipUtils.OnUnzipListener onUnzipListener = new ZipUtils.OnUnzipListener() {

            @Override
            public void onUnzipSucceed(List<String> listFiles) {
                LogWrapper.d(TAG, "unzip engine standard libraries success!");

                FileUtils.deleteFile(saveTo);

                copyLibrariesDir(updateSoListener);
            }

            @Override
            public void onUnzipProgress(float percent) {
                LogWrapper.d(TAG, "Unzip engine standard libraries percent : " + percent);
                updateSoListener.onUnzipProgress(percent);
            }

            @Override
            public void onUnzipFailed(String errorMsg) {
                String error = "Unzip standard libraries(" + saveTo + ") failed!" + errorMsg;
                LogWrapper.e(TAG, error);

                FileUtils.deleteFile(saveTo);
                if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT)) {
                    updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                }
                else {
                    updateSoListener.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
                }
            }

            @Override
            public void onUnzipInterrupt() {
                LogWrapper.d(TAG, "Unzip engine standard libraries ( " + saveTo + " ) interrupted!");
            }

            @Override
            public boolean isUnzipInterrupted() {
                return updateSoListener.isUnzipInterrupted();
            }
        };

        if (mLibrariesInfo.getExtension().equals("7g"))
            ZipUtils.unpack7zAsync(saveTo, mArchiveDownloadDir, false, Thread.NORM_PRIORITY, onUnzipListener);
        else
            ZipUtils.unpackZipAsync(saveTo, mArchiveDownloadDir, false, Thread.NORM_PRIORITY, onUnzipListener);
    }

    private String getArchiveSaveFileName() {
        if (mLibrariesInfo.getExtension().equals("7g"))
            return mEngineType + "-runtime-so-" + mDeviceArch + RuntimeConstants.SEVEN_G_FILE_SUFFIX;
        else
            return mEngineType + "-runtime-so-" + mDeviceArch + RuntimeConstants.GIP_FILE_SUFFIX;
    }

    private void copyLibrariesDir(final OnShareLibraryUpdateListener lis) {
        // 游戏使用 so 存放的目录
        String sharedLibDir = mSharedLibDir;
        FileUtils.ensureDirExists(sharedLibDir);

        // 游戏使用的 so 路径
        FileUtils.copyFile(mArchiveDownloadDir, sharedLibDir);

        // 删除掉下载的文件
        FileUtils.deleteFile(mArchiveDownloadDir);

        if (isLibrariesUpdated()) {
            lis.onSuccess();
        } else {
            LogWrapper.d(TAG, "engine standard libraries file is not valid!");
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, "The MD5 of standard libraries is wrong!");
        }
    }

    public interface OnShareLibraryUpdateListener {
        void onProgress(long downloadedSize, long totalSize);
        void onUnzipProgress(float percent);
        void onFailure(int errorType, String errorMsg);
        void onSuccess();
        boolean isUnzipInterrupted();
    }
}
