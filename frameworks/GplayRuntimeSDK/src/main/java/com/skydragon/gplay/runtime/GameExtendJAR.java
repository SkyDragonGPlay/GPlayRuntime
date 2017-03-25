package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.Utils;

import java.io.File;

public final class GameExtendJAR {

    public static final String TAG = "GameExtendJAR";

    private String mGameExtendDownloadUrl;

    private GameInfo mGameInfo;
    private String mGamePkgName;

    private GameConfigInfo.GameExtendInfo mGameExtendInfo;

    public interface OnRuntimeGameExtendUpdateListener {
        void onProgress(long downloadedSize, long totalSize);
        void onFailure(int errorType, String errorMsg);
        void onSuccess();
    }

    void init(GameInfo gameInfo, GameConfigInfo configInfo) {
        LogWrapper.d(TAG, "init ...");

        mGameInfo = gameInfo;
        if(null == mGameInfo) {
            LogWrapper.e(TAG, "GameExtendJAR init failure, GameInfo is null!");
        }

        mGameExtendInfo = configInfo.getGameExtendInfo();
        if(null == mGameExtendInfo) {
            LogWrapper.e(TAG, "GameExtendJAR init failure, GameExtendInfo is null!");
        }

        mGamePkgName = configInfo.getPackageName();
    }

    public String getGameExtendFile() {
        String extendSaveDir = RuntimeEnvironment.pathCurrentGameDir + "extend/" ;
        FileUtils.ensureDirExists(extendSaveDir);
        return extendSaveDir + RuntimeConstants.GAME_EXTEND_FILE_NAME;
    }

    boolean isGameExtendFileBelongToCurrentGame() {
        String sHostExtendJarFile = RuntimeLauncher.getInstance().getGameExtendPath();
        if (!FileUtils.isExist(sHostExtendJarFile)) return false;
        String sLocalExtendJarFile = getGameExtendFile();
        File f = new File(sLocalExtendJarFile);
        if (!f.exists()) return false;

        String sMd5HostExtendJar = FileUtils.getFileMD5(sHostExtendJarFile);
        if (Utils.isEmpty(sMd5HostExtendJar)) return false;

        String sMd5LocalExtendJar = FileUtils.getFileMD5(sLocalExtendJarFile);
        return !Utils.isEmpty(sMd5LocalExtendJar) && sMd5LocalExtendJar.equalsIgnoreCase(sMd5HostExtendJar) && sMd5HostExtendJar.equalsIgnoreCase(mGameExtendInfo.md5);
    }

    public boolean isGameExtendJarUpdated() {
        String sLocalExtendJarFile = getGameExtendFile();
        File f = new File(sLocalExtendJarFile);
        return f.exists() && !FileUtils.isFileModifiedByCompareMD5(sLocalExtendJarFile, mGameExtendInfo.md5);
    }

    void updateGameExtendJarFile(final boolean isPreloadGame, final OnRuntimeGameExtendUpdateListener lis) {
        LogWrapper.d(TAG, "Update game extend jar file .");

        if(isPreloadGame) {
            mGameExtendDownloadUrl = mGameInfo.mDownloadUrl + mGameExtendInfo.filePath;
            fetchGameExtendJar(isPreloadGame, lis);
            return;
        }

        if(isGameExtendJarUpdated()) {
            if (copyExtendJarToHostGameExtendJar()) {
                lis.onSuccess();
            } else {
                lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.COPY_FAILED);
            }
        } else {
            mGameExtendDownloadUrl = mGameInfo.mDownloadUrl + mGameExtendInfo.filePath;
            fetchGameExtendJar(isPreloadGame, lis);
        }
    }

    public String getGameExtendJarDownloadDirectory() {
        return FileConstants.getGameDownloadDir(mGamePkgName) + "extend/";
    }

    private FileDownloadHelper.FileDownloader mFileDownloader;
    private void fetchGameExtendJar(final boolean isPreloadingGame, final OnRuntimeGameExtendUpdateListener updateGameExtendListener) {
        LogWrapper.d(TAG, "Fetch game extend jar...");
        final String gameExtendJarDownloadDir = getGameExtendJarDownloadDirectory();
        final String saveTo = gameExtendJarDownloadDir + getRuntimeDownloadGameExtendJarName();
        final String saveToTemp = (isPreloadingGame) ? getPreloadExtendJarPath() : saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(gameExtendJarDownloadDir);
        FileUtils.deleteSubFile(gameExtendJarDownloadDir);

        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download helper isn't null, please check it.");
        }

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_GAME_EXTEND, mGameExtendDownloadUrl, saveToTemp, new FileDownloadDelegate() {

            @Override
            public void onStart(String tag) {}

            @Override
            public void onSuccess(File file) {
                // 预加载模式
                if (isPreloadingGame) {
                    updateGameExtendListener.onSuccess();
                    return;
                }

                mFileDownloader = null;
                FileUtils.renameFile(saveToTemp, saveTo);
                LogWrapper.d(TAG, "Download game extend jar ( " + saveTo + " ) succeed!");

                updateLocalAndHostGameExtendJar(updateGameExtendListener);
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                updateGameExtendListener.onProgress(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download extend jar failed!" + errorMsg;
                LogWrapper.e(TAG, error);
                mFileDownloader = null;
                if (errorCode == FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                    updateGameExtendListener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                }
                else {
                    updateGameExtendListener.onFailure(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, error);
                }
            }

            @Override
            public void onCancel() {}
        });
    }

    public String getRuntimeDownloadGameExtendJarName() {
        return "game_extend" + RuntimeConstants.JAR_FILE_SUFFIX;
    }

    public String getPreloadExtendJarPath() {
        return RuntimeLauncher.getInstance().getPreloadGameDir() + getRuntimeDownloadGameExtendJarName();
    }

    private void updateLocalAndHostGameExtendJar(final OnRuntimeGameExtendUpdateListener lis) {
        final String gameExtendJarDownloadDir = getGameExtendJarDownloadDirectory();
        final String saveTo = gameExtendJarDownloadDir + getRuntimeDownloadGameExtendJarName();
        String md5 = FileUtils.getFileMD5(saveTo);
        if(mGameExtendInfo.md5.equalsIgnoreCase(md5)) {
            FileUtils.copyFile(saveTo, getGameExtendFile());
            FileUtils.deleteSubFile(gameExtendJarDownloadDir);
            if (copyExtendJarToHostGameExtendJar()) {
                lis.onSuccess();
            } else {
                lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, RuntimeConstants.COPY_FAILED);
            }
        } else {
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, "The md5 of extend jar is wrong!");
        }
    }

    private boolean copyExtendJarToHostGameExtendJar() {
        FileUtils.deleteSubFile(RuntimeLauncher.getInstance().getGameExtendDirectory());
        String hostGameExtendJar = RuntimeLauncher.getInstance().getGameExtendPath();
        return FileUtils.copyFile(getGameExtendFile(), hostGameExtendJar);
    }
}
