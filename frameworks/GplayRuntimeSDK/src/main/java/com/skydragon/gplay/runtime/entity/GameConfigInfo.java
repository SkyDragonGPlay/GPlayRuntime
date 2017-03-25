package com.skydragon.gplay.runtime.entity;

import android.text.TextUtils;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.RuntimeLocalRecord;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public final class GameConfigInfo {

    private static final String TAG = "GameConfigInfo";

    /**
     * 离线游戏
     */
    public static final int TYPE_OFFLINE = 1;

    /**
     * 联网游戏
     */
    public static final int TYPE_ONLINE = 2;

    /**
     * 用于判定游戏配置文件是否是由 GPlay Tools v 1.0.0.0 之后的版本生成的。
     */
    private static boolean sIsNewGameConfig = false;

    public static final String UNITY = "unity";
    public static final String COCOS = "cocos";

    private String mPackageName;
    private String mEngine;
    private String mEngineVersion;
    private int mMaxVersionSupport;
    private int mGameType = TYPE_OFFLINE;
    private JSONArray mGameSoInfo;
    private ResourceConfigMetaInfo mResourceConfigMetaInfo;
    private GameExtendInfo mGameExtendInfo;
    private HashMap<String, GamePatchInfo> mPatchInfoMap = new HashMap<>();
    private ArrayList<ExtendLibInfo> mExtendLibsInfo;

    public static boolean isNewGameVersion(GameConfigInfo currGameInfo, JSONObject newGameInfoJson) {
        if (currGameInfo == null || newGameInfoJson == null) {
            LogWrapper.w(TAG, "isNewGameVersion check failed! currGameInfo:" + currGameInfo + ", newGameInfoJson" + newGameInfoJson);
            return false;
        }

        JSONObject resConfigForVersions = newGameInfoJson.optJSONObject("resconfig_for_versions");
        boolean isNewGameConfig = resConfigForVersions != null;

        if (isNewGameConfig) {
            int currGameVersion = RuntimeLocalRecord.getInstance().getGameVersion();

            int newestGameVersion = -1;

            for (Iterator<String> ite = resConfigForVersions.keys(); ite.hasNext(); ) {
                String versionStr = ite.next();

                try {
                    int version = Integer.parseInt(versionStr);
                    if (version > newestGameVersion) {
                        newestGameVersion = version;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            LogWrapper.i(TAG, "isNewGameVersion, newestGameVersion:" + newestGameVersion + "  currGameVersion:" + currGameVersion);
            return newestGameVersion > currGameVersion;
        } else {
            ResourceConfigMetaInfo currResourceInfo = currGameInfo.getResourceConfigMetaInfo();
            ResourceConfigMetaInfo newResourceInfo = new ResourceConfigMetaInfo(newGameInfoJson.optJSONObject("resconfig"));

            return !currResourceInfo.md5.equalsIgnoreCase(newResourceInfo.md5);
        }
    }

    public static GameConfigInfo fromJson(JSONObject jsonObject) {
        GameConfigInfo info = new GameConfigInfo();
        info.mEngine = jsonObject.optString("engine");
        info.mEngineVersion = jsonObject.optString("engine_version");
        info.mMaxVersionSupport = jsonObject.optInt("res_max_version_support", 1);
        info.mPackageName = jsonObject.optString("package_name");
        info.mGameType = jsonObject.optInt("game_type", TYPE_OFFLINE);

        // 设置最大支持版本
        RuntimeLocalRecord.getInstance().setNearlyVersionGap(info.mMaxVersionSupport);

        if (info.mEngine == null) return null;

        if (info.isCocosGame()) {
            info.mGameSoInfo = jsonObject.optJSONArray("game_so");
            JSONArray jsonArray = jsonObject.optJSONArray("patch");
            if (null != jsonArray) {
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject object = jsonArray.optJSONObject(i);
                    if (object != null) {
                        GamePatchInfo patchInfo = new GamePatchInfo(object);
                        if (patchInfo.isValid) {
                            info.mPatchInfoMap.put(patchInfo.arch, patchInfo);
                        } else {
                            LogWrapper.e(TAG, "parse patch info failed! info:" + object);
                        }
                    }
                }
            }
        }

        String pathCurrentGameDir = FileConstants.getGameRootDir(info.mPackageName);
        RuntimeEnvironment.currentPackageName = info.mPackageName;
        FileUtils.ensureDirExists(pathCurrentGameDir);
        final RuntimeLocalRecord localRecordConfig = RuntimeLocalRecord.getInstance();
        // 用本地记录文件初始化 local record config
        localRecordConfig.initWithLocalRecord(pathCurrentGameDir);

        JSONObject resConfigForVersions = jsonObject.optJSONObject("resconfig_for_versions");
        sIsNewGameConfig = resConfigForVersions != null;
        info.mResourceConfigMetaInfo = sIsNewGameConfig ? ResourceConfigMetaInfo.fromNewVersionJson(resConfigForVersions)
                : new ResourceConfigMetaInfo(jsonObject.optJSONObject("resconfig"));
        LogWrapper.d(TAG, "ResourceConfigMetaInfo game_version:" + localRecordConfig.getGameVersion() +
                " ,from_version:" + localRecordConfig.getFromVersion());
        LogWrapper.d(TAG, "ResourceConfigMetaInfo is new config:" + sIsNewGameConfig);

        JSONObject jsonGameExtend = jsonObject.optJSONObject("extend");
        if (null != jsonGameExtend) {
            GameExtendInfo gameExtendInfo = new GameExtendInfo(jsonGameExtend);
            if (gameExtendInfo.isValid) {
                info.mGameExtendInfo = gameExtendInfo;
            }
        }

        JSONObject jsonExtendLibObj = jsonObject.optJSONObject("extend_libraries");
        if (jsonExtendLibObj != null) {
            String arch = Utils.getPhoneArch();
            JSONArray jsonExtendLibsArray = jsonExtendLibObj.optJSONArray(arch);
            if (jsonExtendLibsArray != null && jsonExtendLibsArray.length() > 0) {
                info.mExtendLibsInfo = new ArrayList<>();
                for (int i = 0; i < jsonExtendLibsArray.length(); i++) {
                    ExtendLibInfo extendLibInfo = new ExtendLibInfo(jsonExtendLibsArray.optJSONObject(i));
                    info.mExtendLibsInfo.add(extendLibInfo);
                }
            }
        }

        return info;
    }

    public static class GamePatchInfo {
        public final String arch;
        public final String md5;
        public final String filePath;
        public boolean isValid;

        public GamePatchInfo(JSONObject jsonObject) {
            arch = jsonObject.optString("arch");
            filePath = jsonObject.optString("file");
            md5 = jsonObject.optString("md5");
            isValid = !(TextUtils.isEmpty(filePath) || TextUtils.isEmpty(md5) || TextUtils.isEmpty(arch));
        }
    }

    public static class ResourceConfigMetaInfo {
        private static final String TAG = "ResourceConfigMetaInfo";
        public final String md5;
        public final String filePath;

        public ResourceConfigMetaInfo(JSONObject jsonObject) {
            filePath = jsonObject.optString("file");
            md5 = jsonObject.optString("md5");
        }

        /**
         * 在新版本的游戏配置文件中，包含了多个版本的场景配置信息。
         */
        public static ResourceConfigMetaInfo fromNewVersionJson(JSONObject jsonObject) {
            int gameVersionRecord = RuntimeLocalRecord.getInstance().getGameVersion();
            JSONObject jsonTmp;
            if ((jsonTmp = jsonObject.optJSONObject(String.valueOf(gameVersionRecord))) != null) {
                LogWrapper.d(TAG, "find version (" + gameVersionRecord + "), a suitable config:" + jsonTmp);
                jsonObject = jsonTmp;
            } else {
                int newestGameVersion = -1;
                for (Iterator<String> ite = jsonObject.keys(); ite.hasNext(); ) {
                    String versionStr = ite.next();

                    try {
                        int version = Integer.parseInt(versionStr);
                        if (version > newestGameVersion) {
                            newestGameVersion = version;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (newestGameVersion == -1) {
                    LogWrapper.e(TAG, "failed to find the newest game resource meta info!");
                    return null;
                }

                jsonObject = jsonObject.optJSONObject(String.valueOf(newestGameVersion));
                if (jsonObject == null) {
                    LogWrapper.e(TAG, "failed to parse the newest game resource meta info!");
                    return null;
                }

                LogWrapper.v(TAG, "Resource config meta info: find max version:" + newestGameVersion + ", config: " + jsonObject);
            }

            return new ResourceConfigMetaInfo(jsonObject);
        }

        @Override
        public String toString() {
            return "{file:" + filePath + ",md5:" + md5 + "}";
        }
    }

    public String getGameSoMd5(String archKey) {
        if (mGameSoInfo != null) {
            for (int i = 0; i < mGameSoInfo.length(); i++) {
                try {
                    JSONObject gameInfo = mGameSoInfo.getJSONObject(i);
                    String archName = gameInfo.getString("arch");
                    if (archName != null && archName.equals(archKey)) {
                        return gameInfo.getString("md5");
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getEngine() {
        return mEngine;
    }

    public String getEngineVersion() {
        return mEngineVersion;
    }

    public int getGameType() {
        return mGameType;
    }

    public GamePatchInfo getGamePatchInfoByArch(String arch) {
        return mPatchInfoMap.get(arch);
    }

    public ResourceConfigMetaInfo getResourceConfigMetaInfo() {
        return mResourceConfigMetaInfo;
    }

    public GameExtendInfo getGameExtendInfo() {
        return mGameExtendInfo;
    }

    public static String getConfigPath(String packageName) {
        return FileConstants.getGameRootDir(packageName) + RuntimeConstants.CONFIG_FILE_NAME;
    }

    public static boolean isNewGameConfig() {
        return sIsNewGameConfig;
    }

    public boolean isUnityGame() {
        return mEngine != null && mEngine.contains(UNITY);
    }

    public boolean isCocosGame() {
        return mEngine != null && mEngine.contains(COCOS);
    }

    public static class GameExtendInfo {
        public String filePath;
        public int fileSize;
        public String md5;
        public boolean isValid;

        public GameExtendInfo(JSONObject jsonObject) {
            filePath = jsonObject.optString("file", null);
            fileSize = jsonObject.optInt("size", 0);
            md5 = jsonObject.optString("md5", null);
            isValid = !(TextUtils.isEmpty(filePath) || TextUtils.isEmpty(md5));
        }
    }

    public static class ExtendLibInfo {

        private final String archiveFile;
        private final String archiveMD5;
        private final String libraryMD5;
        private final long size;
        private final String libraryFile;

        public ExtendLibInfo(JSONObject jsonObject) {
            archiveFile = jsonObject.optString("archive_file");
            libraryFile = jsonObject.optString("library_file");
            size = jsonObject.optInt("archive_size");
            archiveMD5 = jsonObject.optString("archive_md5");
            libraryMD5 = jsonObject.optString("library_md5");
        }

        public JSONObject toJson() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("archive_file", archiveFile);
                jsonObject.put("library_file", libraryFile);
                jsonObject.put("archive_md5", archiveMD5);
                jsonObject.put("library_md5", libraryMD5);
                jsonObject.put("size", size);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonObject;
        }

        public String getArchiveFile() {
            return archiveFile;
        }

        public String getLibraryFile() {
            return libraryFile;
        }

        public String getLibraryMD5() {
            return libraryMD5;
        }

        public long getSize() {
            return size;
        }

        public String getArchiveMD5() {
            return archiveMD5;
        }
    }

    public ArrayList<ExtendLibInfo> getExtendLibsInfo() {
        return mExtendLibsInfo;
    }

}
