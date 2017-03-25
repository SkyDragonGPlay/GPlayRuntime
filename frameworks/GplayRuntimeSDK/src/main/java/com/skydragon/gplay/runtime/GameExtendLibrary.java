package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.Utils;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author :xiaoyi
 */
class GameExtendLibrary {

    private static final String TAG = "GameExtendLibrary";

    private String mCurrentDownloadName = null;
    private String mArch;
    private GameInfo mGameInfo;
    private ArrayList<GameConfigInfo.ExtendLibInfo> mExtendLibsInfo;
    private ArrayList<GameConfigInfo.ExtendLibInfo> mNeedUpdateExtendLibsList = new ArrayList<>();

    private String mGameExtendLibFileDir;
    private String mExtendLibDownloadSaveDir;

    private int mUnzipThreadPriority = Thread.NORM_PRIORITY;
    private long mTotalSize = 0;
    private boolean isFirstCheck = true;

    interface onRuntimeExtendLibrariesUpdateListener {
        void onProgress(float percent);

        void onDownloadFailure(String errMsg);

        void onUnzipStart();

        void onUnzipProgress(float percent);

        void onUnzipFailed(String errMsg);

        void onSuccess();

        boolean isUnzipInterrupted();
    }

    void init(GameInfo gameInfo, GameConfigInfo configInfo) {
        LogWrapper.i(TAG, "init...");
        mGameInfo = gameInfo;
        mArch = Utils.getPhoneArch();
        mExtendLibsInfo = configInfo.getExtendLibsInfo();

        mGameExtendLibFileDir = getExtendLibFileDir();
        mExtendLibDownloadSaveDir = getExtendLibDownloadSaveDir();
    }

    /**
     * 检查本地历史extend_libraries 是否完整以及md5值
     * 获取需要下载的libs list
     *
     * @return
     */
    private boolean isLocalExtendLibsUpdatedScreenOutByMD5() {
        if (mExtendLibsInfo == null || mExtendLibsInfo.isEmpty()) {
            if (FileUtils.deleteFile(getExtendLibDir())) {
                LogWrapper.w(TAG, "extend_libs not exist ,delete extend_libs success");
            }
            return true;
        }

        deleteUnUseLocalExtendLib();

        mNeedUpdateExtendLibsList.clear();
        for (int i = 0; i < mExtendLibsInfo.size(); i++) {
            String libMD5 = mExtendLibsInfo.get(i).getLibraryMD5();
            String libFilePath = getExtendLibFilePathByName(mExtendLibsInfo.get(i).getLibraryFile());
            File file = new File(libFilePath);
            if (!file.exists()) {
                LogWrapper.w(TAG, "extend libs (" + file.getAbsolutePath() + ") not exist!");
                mNeedUpdateExtendLibsList.add(mExtendLibsInfo.get(i));
            } else if (libMD5 != null && FileUtils.isFileModifiedByCompareMD5(libFilePath, libMD5)) {
                LogWrapper.w(TAG, "extend libs (" + file.getAbsolutePath() + ") md5:" + FileUtils.getFileMD5(libFilePath) + "   md5 in config is :" + libMD5 + " md5 is wrong!");
                mNeedUpdateExtendLibsList.add(mExtendLibsInfo.get(i));
                FileUtils.delete(libFilePath);
            }
        }

        if (!mNeedUpdateExtendLibsList.isEmpty()) {
            calculateTotalSizeOfDownloadLibs();
            return false;
        } else {
            return true;
        }
    }

    private boolean deleteUnUseLocalExtendLib() {
        String libDir = mGameExtendLibFileDir;
        File f = new File(libDir);
        List<String> fileNameList = new ArrayList<>();
        List<String> extendLibNameList = new ArrayList<>();
        if (f != null && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    fileNameList.add(file.getName());
                }
            }
        }
        for (int i = 0; i < mExtendLibsInfo.size(); i++) {
            extendLibNameList.add(mExtendLibsInfo.get(i).getLibraryFile());
        }
        fileNameList.removeAll(extendLibNameList);
        if (!fileNameList.isEmpty()) {
            for (int i = 0; i < fileNameList.size(); i++) {
                if (FileUtils.deleteFile(getExtendLibFilePathByName(fileNameList.get(i)))) {
                    LogWrapper.i(TAG, "delete unuse extend library " + fileNameList.get(i) + " success!");
                }
            }
            return true;
        }
        return false;
    }

    boolean copyFileToExtendSharedLibraryDir() {
        String extendSharedLibDir = RuntimeLauncher.getInstance().getExtendLibrariesDir();
        FileUtils.deleteFile(extendSharedLibDir);
        FileUtils.ensureDirExists(extendSharedLibDir);
        return FileUtils.copyFile(mGameExtendLibFileDir, extendSharedLibDir);
    }

    void updateExtendLibs(final onRuntimeExtendLibrariesUpdateListener runtimeExtendLibrariesUpdateListener) {
        if (!isLocalExtendLibsUpdatedScreenOutByMD5() && !mNeedUpdateExtendLibsList.isEmpty()) {
            GameConfigInfo.ExtendLibInfo nextDownloadExtendLib = getNextDownloadExtendLib();
            LogWrapper.d(TAG, "extend_library " + nextDownloadExtendLib.getLibraryFile() + " need update");
            fetchNextExtendLibs(nextDownloadExtendLib, runtimeExtendLibrariesUpdateListener);
        } else {
            LogWrapper.d(TAG, "extend_libraries not need update or is updated");
            runtimeExtendLibrariesUpdateListener.onSuccess();
        }
    }

    private FileDownloadHelper.FileDownloader mFileDownloader;
    private void fetchNextExtendLibs(final GameConfigInfo.ExtendLibInfo extendLibInfo, final onRuntimeExtendLibrariesUpdateListener runtimeExtendLibrariesUpdateListener) {
        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download handle isn't null, please check it.");
            return;
        }

        final String zipLibName = getCurrentDownloadZipName(extendLibInfo);
        LogWrapper.i(TAG, "current download file :" + extendLibInfo.toString());
        mCurrentDownloadName = zipLibName;
        String currentExtendLibDownloadUrl = mGameInfo.mDownloadUrl + extendLibInfo.getArchiveFile();
        final String extendLibsDownloadDir = mExtendLibDownloadSaveDir + mArch + "/";
        final String saveTo = extendLibsDownloadDir + mCurrentDownloadName;
        final String saveTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(extendLibsDownloadDir);

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_EXTEND_LIB, currentExtendLibDownloadUrl, saveTemp, new FileDownloadDelegate() {
            @Override
            public void onStart(String tag) {
                LogWrapper.i(TAG, "start download extend_lib/" + mCurrentDownloadName);
            }

            @Override
            public void onSuccess(File file) {
                if (mFileDownloader == null || !mCurrentDownloadName.equals(zipLibName)) {
                    LogWrapper.w(TAG, "Downloading extend_lib is (" + mCurrentDownloadName + "), ignore onSuccess() for (" + zipLibName + ")");
                } else {
                    mFileDownloader = null;
                    FileUtils.renameFile(saveTemp, saveTo);
                    LogWrapper.i(TAG, "Downloading " + zipLibName + " successfully!");
                    LogWrapper.i(TAG, "Start to unzip with thread priority: " + mUnzipThreadPriority);
                    runtimeExtendLibrariesUpdateListener.onUnzipStart();
                    unzipExtendLib(extendLibInfo, zipLibName, runtimeExtendLibrariesUpdateListener);
                }
            }

            @Override
            public void onFailure(int code, String errorMsg) {
                String error = "Download extend lib failed" + errorMsg;
                LogWrapper.w(TAG, error);
                mFileDownloader = null;
                runtimeExtendLibrariesUpdateListener.onDownloadFailure(error);
            }

            @Override
            public void onCancel() {}

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                runtimeExtendLibrariesUpdateListener.onProgress((1.0f * extendLibInfo.getSize() / mTotalSize) * downloadedSize / totalSize);
            }
        });
    }

    private void unzipExtendLib(final GameConfigInfo.ExtendLibInfo extendLibInfo, String libName, final onRuntimeExtendLibrariesUpdateListener runtimeExtendLibrariesUpdateListener) {
        final String extendLibDownloadDir = mExtendLibDownloadSaveDir + mArch + "/";
        final String saveTo = extendLibDownloadDir + libName;
        final String soFileName = extendLibInfo.getLibraryFile();
        FileUtils.ensureDirExists(extendLibDownloadDir);

        ZipUtils.unpack7zAsync(saveTo, mGameExtendLibFileDir, false, mUnzipThreadPriority, new ZipUtils.OnUnzipListener() {
            @Override
            public void onUnzipSucceed(List<String> unzipFiles) {
                LogWrapper.i(TAG, "unpack extend library is success!");
                FileUtils.deleteFile(saveTo);
                FileUtils.deleteFile(extendLibDownloadDir);

                if (!FileUtils.isFileModifiedByCompareMD5(getExtendLibFilePathByName(soFileName), extendLibInfo.getLibraryMD5())) {
                    mNeedUpdateExtendLibsList.remove(0);
                    if (mNeedUpdateExtendLibsList.size() > 0) {
                        fetchNextExtendLibs(getNextDownloadExtendLib(), runtimeExtendLibrariesUpdateListener);
                    } else {
                        runtimeExtendLibrariesUpdateListener.onSuccess();
                    }
                } else {
                    String errorMsg = "recently download config file:" + extendLibInfo.getLibraryFile() + " s'md5 :" + extendLibInfo.getLibraryMD5() + " download file md5: " + FileUtils.getFileMD5(getExtendLibFilePathByName(soFileName));
                    runtimeExtendLibrariesUpdateListener.onDownloadFailure(errorMsg);
                }
            }

            @Override
            public void onUnzipProgress(float percent) {
                float correctPercent = percent * extendLibInfo.getSize() / mTotalSize;
                LogWrapper.i(TAG, "Unzip extend library res percent : " + correctPercent);
                runtimeExtendLibrariesUpdateListener.onUnzipProgress(correctPercent);
            }

            @Override
            public void onUnzipFailed(String errorMsg) {
                LogWrapper.w(TAG, "unzip extend lib failed!");
                FileUtils.deleteFile(saveTo);
                runtimeExtendLibrariesUpdateListener.onUnzipFailed(errorMsg);
            }

            @Override
            public void onUnzipInterrupt() {
                LogWrapper.w(TAG, "Unzip extend lib res interrupted!");
            }

            @Override
            public boolean isUnzipInterrupted() {
                return runtimeExtendLibrariesUpdateListener.isUnzipInterrupted();
            }
        });
    }

    /**
     * 获取游戏资源目录下的extend_libs
     *
     * @return (game dir)/extend_libs/
     */
    private String getExtendLibDir() {
        return RuntimeEnvironment.pathCurrentGameDir + "extend_libs/";
    }

    /**
     * 获取第三方动态库目录（根据架构）
     *
     * @return (game dir)/extend_libs/arch/
     */
    private String getExtendLibFileDir() {
        String extendLibSaveDir = RuntimeEnvironment.pathCurrentGameDir + "extend_libs/" + mArch + "/";
        FileUtils.ensureDirExists(extendLibSaveDir);
        return extendLibSaveDir;
    }


    /**
     * 获取第三方动态库文件
     *
     * @return (game dir)/extend_libs/arch/filename.so
     */
    private String getExtendLibFilePathByName(String fileName) {
        String extendLibSaveDir = RuntimeEnvironment.pathCurrentGameDir + "extend_libs/" + mArch + "/";
        FileUtils.ensureDirExists(extendLibSaveDir);
        return extendLibSaveDir + fileName;
    }

    /**
     * 获取第三方动态库的下载目录
     *
     * @return (download dir)/extend_libs/
     */
    private String getExtendLibDownloadSaveDir() {
        return FileConstants.getDownloadDir() + "extend_libs/";
    }

    /**
     * 获取当前需要下载的压缩文件名称
     */
    private String getCurrentDownloadZipName(GameConfigInfo.ExtendLibInfo extendLibInfo) {
        String fileName = extendLibInfo.getLibraryFile();
        return fileName.substring(0, fileName.lastIndexOf(".")) + RuntimeConstants.SEVEN_G_FILE_SUFFIX;
    }

    private GameConfigInfo.ExtendLibInfo getNextDownloadExtendLib() {
        if (mNeedUpdateExtendLibsList.isEmpty()) {
            return null;
        }
        return mNeedUpdateExtendLibsList.get(0);
    }

    /**
     * 获取下载文件的总长度
     */
    private void calculateTotalSizeOfDownloadLibs() {
        if (isFirstCheck) {
            for (int i = 0; i < mNeedUpdateExtendLibsList.size(); i++) {
                mTotalSize = mTotalSize + mNeedUpdateExtendLibsList.get(i).getSize();
            }
        }
        isFirstCheck = false;
    }
}
