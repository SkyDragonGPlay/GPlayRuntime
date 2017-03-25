package com.skydragon.gplay.runtime;

import android.content.Context;
import android.widget.Toast;

import com.skydragon.gplay.runtime.bridge.BridgeHelper;
import com.skydragon.gplay.runtime.entity.resource.GameResourceConfigInfo;
import com.skydragon.gplay.runtime.entity.resource.GroupInfo;
import com.skydragon.gplay.runtime.entity.resource.SceneInfo;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class RuntimeScene {
    public static final String TAG = "RuntimeScene";

    RuntimeLocalRecord mRuntimeLocalRecord;
    private SceneInfo mSceneInfo;

    public static RuntimeScene getRuntimeScene(String sceneStr){
        SceneInfo sceneInfo = findScene(sceneStr);
        if(sceneInfo != null){
            return new RuntimeScene(sceneInfo);
        }
        return null;
    }

    private RuntimeScene(SceneInfo sceneInfo) {
        mSceneInfo = sceneInfo;
        mRuntimeLocalRecord = RuntimeLocalRecord.getInstance();
    }

    /*
    * 计算场景在本地缺少的分组。
    * */
    public List<String> calculateLackOfGroups(boolean isPreloadMode){
        boolean isModified = mRuntimeLocalRecord.isModified(mSceneInfo.getName());
        LogWrapper.d(TAG, "Calculate lack of groups, scene:" + mSceneInfo.getName() + ", isModified(" + isModified + ")");
        if(!isModified) {
            if(mRuntimeLocalRecord.isNewestVersionScene(mSceneInfo)
                    && mSceneInfo.isCompletedScenes()){
                mRuntimeLocalRecord.updateSceneVersionToNewest(mSceneInfo);
                if(!isPreloadMode) mRuntimeLocalRecord.persis();
                return new ArrayList<>();
            } else if(mRuntimeLocalRecord.isNearlyVersion(mSceneInfo.getName())) {
                /*
                * 场景未被修改并且是临近版本，进行 patch 逻辑。
                * */
                if(canUpdateByPatch()){
                    List<String> patchs = mSceneInfo.getAllPatchInfos();
                    if(patchs.isEmpty()){
                        // 更新本地场景记录的版本号
                        List<String> groupsList = mSceneInfo.getAllGroupInfos();
                        for(String group : groupsList) {
                            if(!mRuntimeLocalRecord.isDownloadedGroupInfoContained(group))
                                mRuntimeLocalRecord.recordDownloadedGroupInfo(group);
                        }
                        mRuntimeLocalRecord.updateSceneVersionToNewest(mSceneInfo);
                        if(!isPreloadMode) mRuntimeLocalRecord.persis();
                        return new ArrayList<>();
                    }

                    patchs = removeGroupsAlreadyDownloaded(mSceneInfo.getAllPatchInfos());
                    if(patchs.isEmpty()){
                        // 无需下载分组时更新本地场景信息。
                        mRuntimeLocalRecord.updateSceneVersionToNewest(mSceneInfo);
                        if(!isPreloadMode) mRuntimeLocalRecord.persis();
                    }

                    // 返回所有需要下载的 patch 包
                    return patchs;
                }
            }
        }

        LogWrapper.d(TAG, "Need to complete update!");
        /*
        * 进行完整升级
        * */
        List<String> groups = removeGroupsAlreadyDownloaded(mSceneInfo.getAllGroupInfos());

        if(groups.isEmpty()){
            // 无需下载分组时更新本地场景信息。
            mRuntimeLocalRecord.updateSceneVersionToNewest(mSceneInfo);
            mRuntimeLocalRecord.persis();
            LogWrapper.d(TAG, "The groups of " + mSceneInfo.getName() + " are all contained in history list !");
        }
        return groups;
    }

    private List<String> removeGroupsAlreadyDownloaded(List<String> groupList) {
        List<String> needToLoadGroups = new ArrayList<>();
        for(String group : groupList){
            if(!mRuntimeLocalRecord.isDownloadedGroupInfoContained(group) || !groupExist(group)){
                needToLoadGroups.add(group);
            }
        }
        return needToLoadGroups;
    }

    private boolean groupExist(String groupName) {
        GameResourceConfigInfo resourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
        GroupInfo resGroup = resourceConfigInfo.getGroupInfoByName(groupName);

        return (resGroup != null) && resGroup.isCompletedGroup();
    }

    /**
     * 此方法用于校验两个相邻的scene版本，其中旧版本scene所具有的本地资源是否足够使用新版本的patch包来升级到全新版本。
     * 新版本filePathOfGroups - 新版本filePathOfPatchs = filePathOfShouldExist ，通过 filePathOfShouldExist 来校验本地资源。
     * */
    private boolean canUpdateByPatch(){
        // 所有的场景中所有 groups 的集合
        List<String> groupInfos = mSceneInfo.getAllGroupInfos();
        // 所有的场景中所有 patchs 的集合
        List<String> patchInfos = mSceneInfo.getAllPatchInfos();

        List<String> filePathOfGroups = new LinkedList<>();
        List<String> filePathOfPatchs = new LinkedList<>();

        // 获取 新版本filePathOfGroups
        GameResourceConfigInfo resourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
        try {
            for (String aGroupInfo : groupInfos) {
                GroupInfo groupInfo = resourceConfigInfo.getGroupInfoByName(aGroupInfo);
                if (groupInfo != null) {
                    JSONArray resArray = groupInfo.getResourcesArray();

                    for (int i = 0; i < resArray.length(); i++) {
                        filePathOfGroups.add(resArray.getString(i));
                    }
                }
            }
        } catch (JSONException e) {
            LogWrapper.e(TAG, "Could not get resource array from group res!");
            return false;
        }

        // 获取 新版本filePathOfPatchs
        for (String patchInfo : patchInfos) {
            GroupInfo groupInfo = resourceConfigInfo.getGroupInfoByName(patchInfo);
            if (groupInfo != null) {
                JSONArray resArray = groupInfo.getResourcesArray();
                for (int i = 0; i < resArray.length(); i++) {
                    try {
                        filePathOfPatchs.add(resArray.getString(i));
                    } catch (JSONException e) {
                        LogWrapper.e(TAG, "Could not get resource array from patch res!");
                        return false;
                    }
                }
            }
        }

        //  新版本filePathOfGroups - 新版本filePathOfPatchs = filePathOfShouldExist
        List<String> filePathOfShouldExist = new ArrayList<>();

        if(filePathOfPatchs.isEmpty()){
            filePathOfShouldExist.addAll(filePathOfGroups);
        } else {
            nextRes:
            for (String groupRes : filePathOfGroups) {
                for (int i = 0; i < filePathOfPatchs.size(); i++) {
                    if (filePathOfPatchs.get(i).equals(groupRes)) {
                        continue nextRes;
                    }
                }
                filePathOfShouldExist.add(groupRes);
            }
        }

        // 通过 filePathOfShouldExist 来校验本地资源
        String rootPath = RuntimeEnvironment.pathCurrentGameResourceDir;
        for(int i = 0; i < filePathOfShouldExist.size(); i++){
            String groupFile = rootPath + filePathOfShouldExist.get(i);
            File f = new File(groupFile);
            if(!f.exists()) return false;
        }
        return true;
    }

    private static SceneInfo findScene(final String sceneStr) {
        GameResourceConfigInfo resourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
        SceneInfo ret = resourceConfigInfo.getSceneByName(sceneStr);
        if (ret == null) {
            if(RuntimeConstants.isDebugRuntimeEnabled()) {
                final Context ctx = RuntimeStub.getInstance().getContext();
                if(null == ctx ) {
                    return null;
                }

                ThreadUtils.runOnUIThread(new Runnable() {
                    public void run() {
                        Toast.makeText(ctx, sceneStr + " scene don't exist, Please check the place on invoking preloadGroup or preloadGroups method", Toast.LENGTH_LONG).show();
                    }
                });

                ThreadUtils.runOnUIThread(new Runnable(){
                    public void run() {
                        BridgeHelper.getInstance().quitGame();
                    }
                }, 500);
            }
        }
        return ret;
    }

    public long getSize(){
        long sum = 0L;
        List<String> listGroupInfo = mSceneInfo.getAllGroupInfos();

        GameResourceConfigInfo resourceConfigInfo = RuntimeLauncher.getInstance().getResourceConfigInfo();
        for (String groupName : listGroupInfo) {
            GroupInfo groupInfo = resourceConfigInfo.getGroupInfoByName(groupName);
            sum += groupInfo.getSize();
        }
        return sum;
    }

    public SceneInfo getSceneInfo(){
        return mSceneInfo;
    }

    public String getName() {
        return mSceneInfo.getName();
    }
}
