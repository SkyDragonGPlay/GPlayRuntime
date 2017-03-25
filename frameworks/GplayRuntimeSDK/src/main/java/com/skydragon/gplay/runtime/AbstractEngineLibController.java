package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.utils.ThreadUtils;

/**
 * package : com.skydragon.gplay.runtime
 *
 * Description :
 *
 * @author Y.J.ZHOU
 * @date 2016.9.7 10:51.
 */
public abstract class AbstractEngineLibController {

    public static final String TAG = "AbstractEngineLibController";
    final static int KB = 1024;
    final static int MILLISECOND = 1000;
    final static int TIME_INTERVAL = 150;
    final static float PERCENT_OF_ESTIMATE_TIME = 0.97f;
    boolean mCompoundStop = false;
    int mRetryTimes = 1;

    protected boolean mIsPreloadGame;

    // 初始化
    abstract void init(GameInfo gameInfo, GameConfigInfo configInfo, boolean isPreloadingGame);

    // 游戏引擎是否需要更新
    abstract boolean engineLibNeedUpdate();

    // 更新 game patch 文件
    abstract void updateGamePatch(final OnShareLibraryPatchUpdateListener lis);

    // 复制预加载的游戏引擎到标准目录
    abstract boolean copyPreloadEngineToStandardDir();

    // 停止加载
    abstract void cancelUpdate();

    protected boolean enableRetry() {
        return (-- mRetryTimes >= 0);
    }

    /*
    * @param percentOfEstimate 设置预测完成时间进度百分比
    * @param path patch 包路径
    * */
    protected void imitateMergeProgress(final double percent, final long timeEstimate, final OnMergeProgressListener listener){
        ThreadUtils.runAsyncThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // y = base^x (0 < a < 1)  <==>  log (y , 1 / x) = a
                    final double powBase = Math.pow(1 - percent, (double) 1 / timeEstimate * MILLISECOND);
                    final long timeBegin = System.currentTimeMillis();

                    while (!mCompoundStop) {
                        Thread.sleep(TIME_INTERVAL);
                        final long nowTime = System.currentTimeMillis();
                        double alreadyTime = (double) (nowTime - timeBegin) / MILLISECOND;
                        double timeGap = (double) TIME_INTERVAL / MILLISECOND;
                        if(alreadyTime < timeGap) {
                            alreadyTime = timeGap;
                        }

                        float percent = (float)(Math.pow(powBase, alreadyTime));
                        // (1-percent) 恒大于 0
                        percent = (1.0f - percent) * 100;
                        if(!mCompoundStop)
                            listener.onMergeProgress(percent);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public interface OnMergeProgressListener {
        void onMergeProgress(float percent);
    }

    public interface OnMergingListener extends OnMergeProgressListener{
        void onMergeStart();
        void onMergeSuccess();
        void onFailure(int errorCode, String errorMsg);
    }

    public interface OnShareLibraryPatchUpdateListener extends OnMergingListener {
        void onProgress(long downloadedSize, long totalSize);
        void onDownloadStart();
        void onDownloadSuccess();
        void onSuccess();
        void onFailure(int errorCode, String errorMsg);
    }
}
