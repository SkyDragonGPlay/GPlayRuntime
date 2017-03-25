package com.skydragon.gplay.runtime;

import android.text.TextUtils;

import com.skydragon.gplay.runtime.diffpatch.DiffPatch;
import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;
import com.skydragon.gplay.runtime.utils.Utils;
import com.skydragon.gplay.runtime.utils.ZipUtils;

import java.io.File;
import java.util.List;

/**
 * package : com.skydragon.gplay.runtime
 *
 * Description :
 *
 * @author Y.J.ZHOU
 * @date 2016.9.7 14:02.
 */
public class CocosEngineLibController extends AbstractEngineLibController {

    private static final String TAG = "CocosEngineLibController";

    private String mPatchDownloadUrl;
    private FileDownloadHelper.FileDownloader mFileDownloader;

    private String mGameSoMd5;
    private String mPatchMd5;
    private String mEngineType;
    private String mEngineVersion;

    private String mCacheSoFilePath;
    private String mCacheSOMD5;

    private String mGameSoFilePath;
    private String mPatchFilePath;
    private String mGameDownloadRoot;
    private boolean mIsPatchNotUpdate;
    private boolean mIsGameSoBelongToCurrentGame;

    void init(GameInfo gameInfo, GameConfigInfo configInfo, boolean isPreloadGame) {
        LogWrapper.d(TAG, "Cocos engine library controller initialization ...");
        mIsPreloadGame = isPreloadGame;
        mEngineType = gameInfo.mEngine;
        mEngineVersion = gameInfo.mEngineVersion;
        mGameDownloadRoot = FileConstants.getGameDownloadDir(configInfo.getPackageName());

        GameConfigInfo.GamePatchInfo patchInfo = configInfo.getGamePatchInfoByArch(gameInfo.mEngineArch);
        mPatchDownloadUrl = gameInfo.mDownloadUrl + patchInfo.filePath;
        mPatchMd5 = patchInfo.md5;
        mPatchFilePath = getPatchFilePath(isPreloadGame);

        mGameSoMd5 = configInfo.getGameSoMd5(gameInfo.mEngineArch);
        mGameSoFilePath = RuntimeLauncher.getInstance().getGameShareLibraryPath();

        mCacheSoFilePath = getCacheSoFilePath(isPreloadGame);
        mCacheSOMD5 = FileUtils.getFileMD5(mCacheSoFilePath);

        if (mGameSoMd5 != null) {
            if (!isPreloadGame) {
                // 游戏动态库存在并且MD5正确, 不需要更新patch
                String gameSoMD5 = FileUtils.getFileMD5(mGameSoFilePath);
                LogWrapper.i(TAG, "check game so(" + mGameSoFilePath + ") md5:" + gameSoMD5 + ", md5 in config:" + mGameSoMd5);
                mIsGameSoBelongToCurrentGame = !TextUtils.isEmpty(gameSoMD5) && gameSoMD5.equalsIgnoreCase(mGameSoMd5);
                mIsPatchNotUpdate = mIsGameSoBelongToCurrentGame;
            }

            //游戏动态库没有校验通过, 继续判断缓存的动态库
            if (!mIsGameSoBelongToCurrentGame) {
                mIsPatchNotUpdate = mGameSoMd5.equalsIgnoreCase(mCacheSOMD5);
            }
        }

        if (!mIsPatchNotUpdate) {
            mIsPatchNotUpdate = !FileUtils.isFileModifiedByCompareMD5(mPatchFilePath, mPatchMd5);
        }
    }

    @Override
    void updateGamePatch(final OnShareLibraryPatchUpdateListener lis) {
        LogWrapper.d(TAG, "begin update game patch !");
        if (mIsPatchNotUpdate) {
            if(FileUtils.isExist(mCacheSoFilePath)) {
                copyGameSoToHostLibraryPath();
                lis.onSuccess();
            } else {
                ThreadUtils.runAsyncThread(new Runnable() {
                    @Override
                    public void run() {
                        compoundSharedLibrary(lis);
                    }
                });
            }
        } else {
            if (Utils.getCurrAPNType() == Utils.NO_NETWORK) {
                lis.onFailure(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, RuntimeConstants.NO_NETWORK);
            } else {
                mRetryTimes = 1;
                fetchPatch(lis);
            }
        }
    }

    @Override
    boolean engineLibNeedUpdate() {
        return ! (isPatchNotUpdate() && isGameSoBelongToCurrentGame());
    }

    //patch文件存在并且和配置文件记录的MD5一致
    private boolean isPatchNotUpdate() {
        LogWrapper.d(TAG, "isPatchNotUpdate,md5:" + mPatchMd5 + ", whether the patch need update:" + (!mIsPatchNotUpdate));
        return mIsPatchNotUpdate;
    }

    // host目录下game so是否是当前游戏的so
    boolean isGameSoBelongToCurrentGame() {
        if(!FileUtils.isExist(mGameSoFilePath)) {
            LogWrapper.i(TAG, "isGameSoBelongToCurrentGame: game so not exist!");
            return false;
        }

        if (mGameSoMd5 != null) {
            return mIsGameSoBelongToCurrentGame;
        }

        String gameSoMD5 = FileUtils.getFileMD5(mGameSoFilePath);
        return !TextUtils.isEmpty(gameSoMD5) && gameSoMD5.equalsIgnoreCase(mCacheSOMD5);
    }

    private void extractPatch(String patchArchive, final String saveTo, final OnShareLibraryPatchUpdateListener updatePatchListener) {
        final File patchArchiveFile = new File(patchArchive);
        final String output = patchArchiveFile.getParent();
        LogWrapper.d(TAG, "ExtractPatch(" + patchArchive + " , " + saveTo + ")");

        ZipUtils.OnUnzipListener listener = new ZipUtils.OnUnzipListener() {
            @Override
            public void onUnzipSucceed(List<String> unzipFiles) {
                final String patchArchiveFileName = mPatchDownloadUrl.substring(mPatchDownloadUrl.lastIndexOf(File.separator) + 1);
                final String patchFileName = patchArchiveFileName.substring(0, patchArchiveFileName.lastIndexOf(RuntimeConstants.SEVEN_G_FILE_SUFFIX));
                final String patchFilePath = output + File.separator + patchFileName;

                FileUtils.renameFile(patchFilePath, saveTo);
                try {
                    FileUtils.deleteFile(patchArchiveFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                updatePatchListener.onDownloadSuccess();
                copyPatchToGameDir(saveTo, updatePatchListener);
            }

            @Override
            public void onUnzipProgress(float percent) {}

            @Override
            public void onUnzipFailed(String errorMsg) {
                updatePatchListener.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, "Extract Patch failed!");
            }

            @Override
            public void onUnzipInterrupt() {}

            @Override
            public boolean isUnzipInterrupted() { return false; }
        };

        LogWrapper.d(TAG, "Begin unpack7zAsync(" + patchArchive + " , " + output + ")");
        ZipUtils.unpack7zAsync(patchArchive, output, false, Thread.NORM_PRIORITY, listener);
    }

    private void fetchPatch(final OnShareLibraryPatchUpdateListener updatePatchListener) {
        LogWrapper.d(TAG, "Fetch game patch...");
        final String shareLibraryPatchDownloadDir = getShareLibraryPatchDownloadDirectory();
        final String saveTo = shareLibraryPatchDownloadDir + mEngineType + RuntimeConstants.PATCH_FILE_SUFFIX;
        final String saveToTemp = saveTo + RuntimeConstants.TEMP_SUFFIX;

        FileUtils.ensureDirExists(shareLibraryPatchDownloadDir);

        if (mFileDownloader != null) {
            LogWrapper.w(TAG, "Current download helper isn't null, please check it.");
            return;
        }

        mFileDownloader = FileDownloadHelper.downloadFile(RuntimeConstants.DOWNLOAD_TAG_GAME_PATCH, mPatchDownloadUrl, saveToTemp, new FileDownloadDelegate() {
            @Override
            public void onSuccess(File file) {
                LogWrapper.d(TAG, "Fetch game patch onSuccess()  " + mFileDownloader);
                if(mFileDownloader == null)
                    return;
                mFileDownloader = null;
                LogWrapper.d(TAG, "Download the patch of share library succeed!");

                if (mPatchDownloadUrl.endsWith(RuntimeConstants.SEVEN_G_FILE_SUFFIX)) {
                    extractPatch(saveToTemp, saveTo, updatePatchListener);
                } else {
                    FileUtils.renameFile(saveToTemp, saveTo);
                    updatePatchListener.onDownloadSuccess();
                    copyPatchToGameDir(saveTo, updatePatchListener);
                }
            }

            @Override
            public void onProgress(long downloadedSize, long totalSize) {
                if(mFileDownloader != null)
                    updatePatchListener.onProgress(downloadedSize, totalSize);
            }

            @Override
            public void onFailure(int errorCode, String errorMsg) {
                String error = "Download the patch of share library failed! " + errorMsg;
                LogWrapper.e(TAG, error);
                if(mFileDownloader != null) {
                    mFileDownloader = null;
                    if (errorCode == FileDownloadHelper.ERROR_STORAGE_SPACE_NOT_ENOUGH) {
                        updatePatchListener.onFailure(RuntimeConstants.MODE_TYPE_NO_SPACE_LEFT, error);
                    } else {
                        if (enableRetry()) {
                            fetchPatch(updatePatchListener);
                        } else {
                            updatePatchListener.onFailure(RuntimeConstants.MODE_TYPE_NETWORK_ERROR, error);
                        }
                    }
                }
            }

            @Override
            public void onCancel() {
                LogWrapper.d(TAG, "Fetch game patch onCancel()");
                updatePatchListener.onFailure(RuntimeConstants.MODE_TYPE_CANCLE_PRELOADING, "Download cancel !");
            }

            @Override
            public void onStart(String tag) {
                updatePatchListener.onDownloadStart();
            }
        });
    }

    private void onCompoundSharedLibraryFinish(final String tempFilePath, final OnShareLibraryPatchUpdateListener lis) {
        ThreadUtils.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                LogWrapper.i(TAG, "onCompoundSharedLibraryFinish");
                mCompoundStop = true;

                String md5ForCacheSo = FileUtils.getFileMD5(tempFilePath);

                if(!RuntimeConstants.isDebugRuntime && !TextUtils.isEmpty(md5ForCacheSo)
                        && !TextUtils.isEmpty(mGameSoMd5) && !md5ForCacheSo.equalsIgnoreCase(mGameSoMd5)) {
                    FileUtils.deleteFile(tempFilePath);
                    lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, "The md5 of share library is wrong!");
                    return;
                }

                FileUtils.renameFile(tempFilePath, mCacheSoFilePath);

                lis.onMergeSuccess();

                copyGameSoToHostLibraryPath();

                lis.onSuccess();
            }
        });
    }

    private void compoundSharedLibrary(final OnShareLibraryPatchUpdateListener lis) {
        LogWrapper.d(TAG, "compoundSharedLibrary called!!!");

        final String engineSoPath = RuntimeLauncher.getInstance().getCocosStandardEnginePath();
        final String tempFilePath = mCacheSoFilePath + "merge";
        LogWrapper.d(TAG, "before applyPatch, engine so:" + engineSoPath + "\npath:" + mPatchFilePath
                + "\ntemporary game so:" + tempFilePath);

        if (!FileUtils.isExist(engineSoPath)) {
            String error = "Engine share library not exist!";
            LogWrapper.e(TAG, error);
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
            return;
        }

        if (!FileUtils.isExist(mPatchFilePath)) {
            String error = "The patch of share library not exist!";
            LogWrapper.e(TAG, error);
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
            return;
        }

        if(!TextUtils.isEmpty(mPatchMd5) && FileUtils.isFileModifiedByCompareMD5(mPatchFilePath, mPatchMd5)) {
            String error = "The patch of share library verify failed with MD5!";
            LogWrapper.e(TAG, error);
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
            return;
        }

        lis.onMergeStart();

        File patchFile = new File(engineSoPath);
        long timeEstimate = patchFile.length() / KB;
        imitateMergeProgress(PERCENT_OF_ESTIMATE_TIME, timeEstimate, new OnMergeProgressListener() {
            @Override
            public void onMergeProgress(float percent) {
                lis.onMergeProgress(percent);
            }
        });

        if (mPatchDownloadUrl.endsWith(RuntimeConstants.SEVEN_G_FILE_SUFFIX)) {
            DiffPatch.applyPatchV2(engineSoPath, tempFilePath, mPatchFilePath);
        } else {
            DiffPatch.applyPatch(engineSoPath, tempFilePath, mPatchFilePath);
        }

        onCompoundSharedLibraryFinish(tempFilePath, lis);
    }

    private String getShareLibraryPatchDownloadDirectory() {
        // 标准 runtime，下载到统一的 runtime 下载目录中去
        StringBuilder sb = new StringBuilder();
        sb.append(mGameDownloadRoot).append(mEngineType)
                .append(File.separator).append(mEngineVersion)
                .append("/patch/");
        return sb.toString();
    }

    public String getCacheSoFilePath(boolean isPreloadGame) {
        String shareLibrarySaveDir = (isPreloadGame) ? RuntimeLauncher.getInstance().getPreloadGameDir() + "lib/" : RuntimeEnvironment.pathCurrentGameDir + "lib/";
        FileUtils.ensureDirExists(shareLibrarySaveDir);
        return shareLibrarySaveDir + "lib" + mEngineType + "-" + mEngineVersion + RuntimeConstants.SHARE_LIBRARY_FILE_SUFFIX;
    }

    public String getPatchFilePath(boolean isPreloadGame) {
        String patchSaveDir = (isPreloadGame) ? RuntimeLauncher.getInstance().getPreloadGameDir() + "lib/patch/" : RuntimeEnvironment.pathCurrentGameDir + "lib/patch/";
        FileUtils.ensureDirExists(patchSaveDir);
        return patchSaveDir + "lib" + mEngineType + "-" + mEngineVersion  + RuntimeConstants.PATCH_FILE_SUFFIX;
    }

    @Override
    boolean copyPreloadEngineToStandardDir() {
        // 复制游戏 game patch
        String preloadPath =  getCacheSoFilePath(true);
        String standardPath = getCacheSoFilePath(false);
        FileUtils.copyFile(preloadPath, standardPath);
        // 复制游戏 so
        String preloadSO = getPatchFilePath(true);
        String standardSO = getPatchFilePath(false);
        FileUtils.copyFile(preloadSO, standardSO);
        return false;
    }

    @Override
    public void cancelUpdate() {
        if (mFileDownloader != null) {
            mFileDownloader.cancel();
        }
    }

    private void copyPatchToGameDir(final String saveTo, final OnShareLibraryPatchUpdateListener lis) {
        // 游戏使用的 patch 路径
        FileUtils.copyFile(saveTo, mPatchFilePath);
        FileUtils.deleteFile(saveTo);

        if (!FileUtils.isFileModifiedByCompareMD5(mPatchFilePath, mPatchMd5)) {
            compoundSharedLibrary(lis);
        } else {
            String error = "The md5 of standard library patch is wrong!";
            LogWrapper.e(TAG, error);
            lis.onFailure(RuntimeConstants.MODE_TYPE_FILE_VERIFY_WRONG, error);
        }
    }

    private void copyGameSoToHostLibraryPath() {
        if(mIsPreloadGame) return;

        LogWrapper.d(TAG, "copyGameSoToHostLibraryPath, src:" + mCacheSoFilePath + " target:" + mGameSoFilePath);
        FileUtils.deleteFile(mGameSoFilePath);
        FileUtils.copyFile(mCacheSoFilePath, mGameSoFilePath);
    }
}
