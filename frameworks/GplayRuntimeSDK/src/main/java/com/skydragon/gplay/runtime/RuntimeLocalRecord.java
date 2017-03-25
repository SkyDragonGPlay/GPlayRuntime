package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.entity.GameConfigInfo;
import com.skydragon.gplay.runtime.entity.LocalSceneInfo;
import com.skydragon.gplay.runtime.entity.resource.GameResourceConfigInfo;
import com.skydragon.gplay.runtime.entity.resource.SceneInfo;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.ThreadUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RuntimeLocalRecord {
    private static final String TAG = "RuntimeLocalRecord";
    private static final String LOCAL_SCENE_VERSION_INFO  = "gplay_local_scenes_record.json";

    private static final String KEY_GAME_VERSION = "versioncode";
    private static final String KEY_FROM_VERSION = "from_version";
    private static final String KEY_SCENES = "scenes";
    private static final String KEY_DOWNLOADED = "groups";

    public static final int DEFAULT_VERSION = -1;
    private static final int NEARLY_VERSION_GAP = 1;

    private int mGameVersion;
    private int mFromVersion;
    /**
     * 新版本游戏配置支持多个游戏版本的差分更新。
     * */
    private int mNearlyVersionGap = NEARLY_VERSION_GAP;
    private HashMap<String, LocalSceneInfo> _localSceneInfoMap = new HashMap<>();

    // 游戏配置信息
    private List<String> _localDownloadList = new ArrayList<>();

    private static RuntimeLocalRecord _instance;

    public static RuntimeLocalRecord getInstance() {
        if(null == _instance) {
            _instance = new RuntimeLocalRecord();
        }
        return _instance;
    }

    public void setNearlyVersionGap(int nearlyVersionGap) {
        this.mNearlyVersionGap = nearlyVersionGap;
    }

    public List<LocalSceneInfo> getRecordScenes() {
        List<LocalSceneInfo> scenes = new ArrayList<>();
        if(_localSceneInfoMap != null && ! _localSceneInfoMap.isEmpty())
            scenes.addAll(_localSceneInfoMap.values());
        return scenes;
    }

    /**
     * 更新游戏配置信息版本号
     * */
    public void updateLocalVersionInfo(GameResourceConfigInfo gameResConfigInfo) {
        // 版本号不同 更新版本号 清除所有本地的 group 信息
        int version = gameResConfigInfo.getVersionCode();
        LogWrapper.i(TAG, "updateLocalVersionInfo record game version:" + mGameVersion + ", new game version: " + version);
        if(mGameVersion != version) {
            mFromVersion = mGameVersion;
            mGameVersion = version;

            // 清除所有本地的 group 信息
            _localDownloadList.clear();
        }
    }

    /* {"versioncode":"2",
    "from_version":"1",
    "scenes": [
        {   "version":"1",
            "name": "boot_scene",
            "groups": [ "gp1", "gp2"]}, ...
    ],
    "groups":[ group1, group2, ... ]
    }*/
    private void initDefaultLocalConfig(String resourceFilePath){
        File f = new File(resourceFilePath);
        LogWrapper.d(TAG, "InitDefaultLocalConfig after create exist " + f.exists());
        if(!f.exists()) {
            mGameVersion = DEFAULT_VERSION;
            mFromVersion = DEFAULT_VERSION;
            // 持久化初始信息
            persis();
        }
    }

    /**
     * 从本地文件初始化记录信息
     * @param packageDir 本地包名目录
     * */
    public void initWithLocalRecord(String packageDir) {
        _localDownloadList.clear();
        String resourceFilePath = packageDir + LOCAL_SCENE_VERSION_INFO;
        LogWrapper.d(TAG, "group version filepath: " + resourceFilePath );
        // 初始化本地文件
        initDefaultLocalConfig(resourceFilePath);

        JSONObject jsonObject = FileUtils.readJsonObjectFromFile(resourceFilePath);
        LogWrapper.d(TAG, "RuntimeLocalRecord begin.. localJson :" + jsonObject);

        if(jsonObject == null) {
            jsonObject = new JSONObject();
        }

        mGameVersion = jsonObject.optInt(KEY_GAME_VERSION, DEFAULT_VERSION);
        mFromVersion = jsonObject.optInt(KEY_FROM_VERSION, DEFAULT_VERSION);
        JSONArray scenesArray = jsonObject.optJSONArray(KEY_SCENES);
        JSONArray downloadedArray = jsonObject.optJSONArray(KEY_DOWNLOADED);

        if(scenesArray != null) {
            try {
                for( int i = 0; i < scenesArray.length(); i++ ) {
                    JSONObject jsonObj = (JSONObject) scenesArray.get(i);
                    addSceneVersionInfo(LocalSceneInfo.fromJson(jsonObj));
                }
            } catch (JSONException e) {
                LogWrapper.e(TAG, "updateSceneRecord Scene Array parse failure");
            }
        }

        if(downloadedArray != null) {
            try {
                for( int i = 0; i < downloadedArray.length(); i++ ) {
                    String groupStr = downloadedArray.getString(i);
                    if(groupStr != null)
                        _localDownloadList.add(groupStr);
                }
            } catch (JSONException e) {
                LogWrapper.d(TAG, "parse download group array list error!");
            }
        }
    }

    //判断场景是否是最新版本。
    public boolean isNewestVersionScene(SceneInfo sceneInfo) {
        LogWrapper.d(TAG, "RuntimeLocalRecord isNewestVersion gameVersion=" + mGameVersion);
        LocalSceneInfo localSceneInfo;
        if(sceneInfo == null || (localSceneInfo = getLocalSceneRecord(sceneInfo.getName())) == null) {
            return false;
        }

        LogWrapper.d(TAG, "RuntimeLocalRecord isNewestVersion localSceneInfo name:" + localSceneInfo.getName() + " , " + localSceneInfo.getGameVerison() + " , " + localSceneInfo.getVerison());

        /**
         * 在新版本配置文件中，场景也具备版本号。如果配置文件场景版本号与本地保存的场景版本号一致，则不需要更新场景。
         * */
        int sceneVersion;
        if((sceneVersion = sceneInfo.getVersion()) != SceneInfo.DEFAULT_SCENE_VERSION) {

            /*
            * 如果刚完成数据兼容，使用新版本的配置文件升级，本地场景版本游戏版本与远程配置文件的游戏版本必然不同。
            * */
            if(localSceneInfo.getVerison() == LocalSceneInfo.DEFAULT_VERSION) {
                return false;
            }

            /**
             * 判断远程配置文件场景版本号是否与本地的场景版本号一直。
             * */
            return (localSceneInfo.getVerison() == sceneVersion);
        }
        /**
         * 远程的场景配置文件是旧新版本。
         * */
        return (localSceneInfo.getGameVerison() == mGameVersion);
    }

    // 判断是否是临近版本
    public boolean isNearlyVersion(String sceneStr) {
        LocalSceneInfo localSceneInfo = getLocalSceneRecord(sceneStr);

        if(localSceneInfo != null) {
            LogWrapper.d(TAG, "IisNearlyVersion  localSceneInfo:" + localSceneInfo.getVerison() + ", gameVersion:" + mGameVersion);
        } else {
            return false;
        }

        /*
        * 旧版本只支持 1 个版本的差分升级。
        * 新版本配置文件支持多个版本的差分升级，mNearlyVersionGap 个版本内可视为临近版本。
        * */
        if(GameConfigInfo.isNewGameConfig()) {
            /*
            * 本地旧版本数据刚完成数据迁移，使用新版本的远程配置文件进行升级。此新旧交替的时候只支持一个版本的差分。
            * */
            if(localSceneInfo.getVerison() == LocalSceneInfo.DEFAULT_VERSION) {
                LogWrapper.d(TAG, "Is nearly version, scene game version:" + localSceneInfo.getGameVerison() + ", game version:" + mGameVersion + " , gap:" + NEARLY_VERSION_GAP);
                return ((localSceneInfo.getGameVerison() + NEARLY_VERSION_GAP) == mGameVersion);
            }

            LogWrapper.d(TAG, "Is nearly version, scene game version:" + localSceneInfo.getGameVerison() + ", from version:" + mFromVersion + " , gap:" + mNearlyVersionGap);
            /*
            * 本地场景信息已经是新版本的远程场景配置信息。
            * 此时如果要进行差分，则场景的游戏版本需要与 FromVersion 相等，并且 FromVersion 需要在可以升级的范围以内。
            * */
            return ((localSceneInfo.getGameVerison() == mFromVersion) && ((mGameVersion - mFromVersion) <= mNearlyVersionGap));
        } else {
            /*
            * 旧版本升级到旧版本的配置文件，只支持一个版本的差分升级。
            * */
            LogWrapper.d(TAG, "Is nearly version, scene version:" + localSceneInfo.getVerison() + ", game version:" + mGameVersion + " , gap:" + NEARLY_VERSION_GAP);
            return ((localSceneInfo.getGameVerison() + NEARLY_VERSION_GAP) == mGameVersion);
        }
    }

    /**
     * 更新本地场景版本到最新版本。
     * 旧游戏配置文件：使用游戏版本号来更新本地场景版本号，得到的值为默认值 DEFAULT_VERSION ；
     * 新游戏配置文件：游戏配置文件中场景也具备版本号，使用场景版本号更新本地场景版本号; 同时更新场景中新增加了游戏版本号 ；
     * */
    public void updateSceneVersionToNewest(SceneInfo sceneInfo) {
        if (sceneInfo == null) {
            LogWrapper.e(TAG, "updateSceneVersionToGameVersion error : scene info is null !");
            return;
        }

        LocalSceneInfo localSceneInfo = _localSceneInfoMap.get(sceneInfo.getName());

        final int configSceneVersion = sceneInfo.getVersion();

        if(localSceneInfo != null){
            localSceneInfo.updateVersion(configSceneVersion);
            localSceneInfo.updateGameVerison(mGameVersion);
            /**
             * 更新到最新版本，则设置场景为未改变状态。
             * */
            localSceneInfo.setUnModified();
        } else {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(LocalSceneInfo.KEY_VERSION, configSceneVersion);
                jsonObject.put(LocalSceneInfo.KEY_GAME_VERSION, mGameVersion);
                jsonObject.put(LocalSceneInfo.KEY_MODIFIED, LocalSceneInfo.STATUS_UNMODIFIED);
                jsonObject.put(LocalSceneInfo.KEY_NAME, sceneInfo.getName());
            } catch (JSONException e) {
                e.printStackTrace();
            }

            localSceneInfo = LocalSceneInfo.fromJson(jsonObject);

            _localSceneInfoMap.put(localSceneInfo.getName(), localSceneInfo);
        }
    }

    /**
     * 调用此方法会后会更新本地的资源分组配置文件，请慎重使用
     */
    synchronized void persis() {
        LogWrapper.w(TAG, "persis: update local record file");
        JSONObject jsonObject = new JSONObject();
        JSONArray sceneArray = new JSONArray();
        JSONArray downloadedArray = new JSONArray();

        for (Map.Entry<String, LocalSceneInfo> stringLocalSceneInfoEntry : _localSceneInfoMap.entrySet()) {
            Map.Entry entry = (Map.Entry) stringLocalSceneInfoEntry;
            LocalSceneInfo localSceneInfo = (LocalSceneInfo) entry.getValue();
            JSONObject localSceneJson = localSceneInfo.toJson();
            if (localSceneJson != null)
                sceneArray.put(localSceneJson);
        }

        if(! _localDownloadList.isEmpty()){
            for (String a_localDownloadList : _localDownloadList) {
                downloadedArray.put(a_localDownloadList);
            }
        }

        try {
            jsonObject.put(KEY_GAME_VERSION, mGameVersion);
            jsonObject.put(KEY_FROM_VERSION, mFromVersion);
            jsonObject.put(KEY_SCENES, sceneArray);
            jsonObject.put(KEY_DOWNLOADED, downloadedArray);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String resourceFilePath = FileConstants.getGameRootDir(RuntimeEnvironment.currentPackageName) + LOCAL_SCENE_VERSION_INFO;

        FileUtils.writeJsonObjectToFile(resourceFilePath, jsonObject);
    }

    public boolean allGroupOfSceneExistInDownloadList(SceneInfo sceneInfo) {
        if(sceneInfo == null)
            return false;

        for (String sceneGroup : sceneInfo.getAllGroupInfos()) {
            if (!isDownloadedGroupInfoContained(sceneGroup)) {
                return false;
            }
        }
        return true;
    }

    public boolean isDownloadedGroupInfoContained(String groupName) {
        return groupName != null && _localDownloadList.contains(groupName);
    }

    public void recordDownloadedGroupInfo(String groupName) {
        if (groupName != null)
            _localDownloadList.add(groupName);
        else
            LogWrapper.e(TAG,"recordDownloadedGroupInfo groupName is null");
    }

    private LocalSceneInfo getLocalSceneRecord(String sceneKey){
        return _localSceneInfoMap.get(sceneKey);
    }

    private void addSceneVersionInfo(LocalSceneInfo localSceneInfo) {
        _localSceneInfoMap.put(localSceneInfo.getName(), localSceneInfo);
    }

    public int getGameVersion() {
        return mGameVersion;
    }

    public int getFromVersion() {
        return mFromVersion;
    }

    public boolean isModified(String sceneName) {
        LocalSceneInfo localSceneInfo;
        return (localSceneInfo = getLocalSceneRecord(sceneName)) == null || localSceneInfo.isModified();
    }

    public void setSceneModified(String sceneName, boolean isModified) {
        LocalSceneInfo localSceneInfo;
        if((localSceneInfo = getLocalSceneRecord(sceneName)) != null) {
            if(isModified) {
                localSceneInfo.setModified();
            } else {
                localSceneInfo.setUnModified();
            }
        }
    }

    public void updateFromVersion(int version) {
        mFromVersion = version;
    }

    // 判断下载记录中是否已包含所有场景
    public synchronized void startResBundleAsync(final GameResourceConfigInfo resourceConfigInfo, final ICheckResBundleListener listener) {
        ThreadUtils.runAsyncThread(new Runnable() {
            @Override
            public void run() {
                List<SceneInfo> sceneInfos;
                Set<String> sceneNames = new HashSet<>();
                if(resourceConfigInfo == null || (sceneInfos = resourceConfigInfo.getAllSceneInfos()).isEmpty()) {
                    listener.onError();
                    return;
                }

                int gameVersion = resourceConfigInfo.getVersionCode();
                for (SceneInfo sceneInfo : sceneInfos) {
                    String sceneName = sceneInfo.getName();
                    LocalSceneInfo localSceneInfo = _localSceneInfoMap.get(sceneName);

                    if (localSceneInfo == null || localSceneInfo.isModified()) {
                        sceneNames.add(sceneName);
                        continue;
                    }

                    if (localSceneInfo.getVerison() == sceneInfo.getVersion()) {
                        if (sceneInfo.isCompletedScenes()) {
                            continue;
                        }
                    } else if (localSceneInfo.getGameVerison() != gameVersion) {
                        if (sceneInfo.isCompletedScenes()) {
                            continue;
                        }
                    }
                    sceneNames.add(sceneName);
                }

                listener.onResBundleChecked(sceneNames);
            }
        });
    }


    public interface ICheckResBundleListener {
        void onResBundleChecked(Set<String> sceneNames);
        void onError();
    }
}
