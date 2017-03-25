package com.skydragon.gplay.runtime.utils;

import android.text.TextUtils;

import java.util.HashMap;

public class LoadingProgressController {

    private static final String TAG = "LoadingProgressController";

    //各阶段总权重(不包含固定占比的阶段)
    private float mTotalWeight = 0f;
    //按权重计算的阶段信息
    private HashMap<String, Float> mStageWeightMap = new HashMap<>();
    //按固定占比计算的阶段信息
    private HashMap<String, Float> mStageProgressMap = new HashMap<>();
    //各阶段当前实际进度信息
    private HashMap<String, Float> mStageRealProgressInAll = new HashMap<>();

    private float mTotalProgressForWeight = 100f;
    private float mCurrProgress = 0f;

    public LoadingProgressController() {
    }

    //添加按固定占比的阶段
    public void addStageWithProgress(String stageName, float progress) {
        LogWrapper.d(TAG, "addStageWithProgress, stage name:" + stageName + ", progress:" + progress);
        if (!TextUtils.isEmpty(stageName) && progress > 0f && progress <= 100f) {
            mStageProgressMap.put(stageName, progress);
            mStageRealProgressInAll.put(stageName, 0f);

            mTotalProgressForWeight = 100f;
            for (Float progressInTotal : mStageProgressMap.values()) {
                mTotalProgressForWeight -= progressInTotal;
            }
        } else {
            LogWrapper.e(TAG, "addStageWithProgress error, stage name:" + stageName + ", progress:" + progress);
        }
    }

    //添加按权重计算占比的阶段
    public void addStageWithWeight(String stageName, float weight) {
        LogWrapper.d(TAG, "addStageWithWeight, stage name:" + stageName + ", weight:" + weight);
        if (!TextUtils.isEmpty(stageName) && weight > 0f) {
            mStageWeightMap.put(stageName, weight);
            mStageRealProgressInAll.put(stageName, 0f);

            mTotalWeight = 0f;
            for (Float stageWeight : mStageWeightMap.values()) {
                mTotalWeight += stageWeight;
            }
        } else {
            LogWrapper.e(TAG, "addStageWithWeight error, stage name:" + stageName + ", weight:" + weight);
        }
    }

    /**
     * 更新某一阶段的进度
     * @param stageName 阶段别名
     * @param stageProgress 有效值:0-100
     * @return 返回整体进度
     */
    public float updateStageProgress(String stageName, float stageProgress) {
        if (!TextUtils.isEmpty(stageName) && stageProgress >= 0f) {
            boolean isStageExist = false;
            if (mStageWeightMap.containsKey(stageName)) {
                float progress = (stageProgress / 100f) * (mStageWeightMap.get(stageName) / mTotalWeight) * mTotalProgressForWeight;
                mStageRealProgressInAll.put(stageName, progress);
                isStageExist = true;
            } else if(mStageProgressMap.containsKey(stageName)){
                float progress = (stageProgress / 100f) * (mStageProgressMap.get(stageName));
                mStageRealProgressInAll.put(stageName, progress);
                isStageExist = true;
            }

            if (isStageExist) {
                float currProgress = 0f;
                for (Float stageProgressInTotal : mStageRealProgressInAll.values()) {
                    currProgress += stageProgressInTotal;
                }
                if (currProgress > 100f) {
                    currProgress = 100f;
                }

                mCurrProgress = currProgress;
            }
        } else {
            LogWrapper.e(TAG, "updateStageProgress error, stage name:" + stageName + ", stage progress:" + stageProgress);
        }

        return mCurrProgress;
    }

    public void reset(){
        mCurrProgress = 0f;
        mTotalWeight = 0f;
        mTotalProgressForWeight = 100f;
        mStageRealProgressInAll.clear();
        mStageWeightMap.clear();
        mStageProgressMap.clear();
    }
}
