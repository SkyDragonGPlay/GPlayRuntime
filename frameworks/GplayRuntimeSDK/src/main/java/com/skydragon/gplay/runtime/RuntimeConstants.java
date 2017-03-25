package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.utils.Utils;

public final class RuntimeConstants {

    public static final String PATH_CURRENT_GAME_DIR = "pathCurrentGameDir";
    public static final String PATH_CURRENT_GAME_GROUP_DIR = "pathCurrentGameGroupDir";
    public static final String PATH_CURRENT_GAME_PATCH_DIR = "pathCurrentGamePatchDir";
    public static final String PATH_HOST_RUNTIME_DIR = "pathHostRuntimeDir";
    public static final String PATH_HOST_RUNTIME_ENGINE_DIR = "pathHostRuntimeEngineDir";
    public static final String PATH_HOST_RUNTIME_ENGINE_COMMON_RES_DIR = "pathHostRuntimeEngineCommonResDir";
    public static final String PATH_HOST_RUNTIME_PATCH_SO_DIR = "pathHostRuntimeDiffPatchDir";
    public static final String PATH_HOST_RUNTIME_ENGINE_JAVA_LIBRARY_DIR = "pathHostRuntimeJavaLibraryDir";
    public static final String PATH_HOST_RUNTIME_ENGINE_SHARELIBRARY_DIR = "pathHostRuntimeShareLibraryDir";
    public static final String PATH_HOST_RUNTIME_RESOURCE_DIR = "pathHostRuntimeResourceDir";
    public static final String PATH_HOST_RUNTIME_DEX_DIR = "pathHostRuntimeDexDir";
    public static final String PATH_HOST_RUNTIME_GAME_DIR = "pathHostRuntimeGameDir";
    public static final String PATH_HOST_GAME_EXTEND_DIR = "pathHostGameExtendDir";
    public static final String PATH_HOST_RUNTIME_EXTEND_LIBRARIES = "pathHostRuntimeExtendLibsDir";
    public static final String PATH_SD_CARD_RUNTIME_LIB_VERSION_DIR = "pathSDCardRuntimeLibVersionDir";
    public static final String PATH_RUNTIME_JAR_GIP_FILE_TEMP = "pathRuntimeJarGipFileTemp";
    public static final String PATH_DOWNLOAD_SHARE_LIBRARY_DIR = "pathShareLibrariesDir";
    public static final String PATH_DOWNLOAD_DLL_DIR = "pathDownloadDllDir";
    public static final String PATH_HOST_RUNTIME_DLL_DIR = "pathHostDllDir";
    public static final String PATH_RUNTIME_JAR_GIP_FILE = "pathRuntimeJarGipFile";
    public static final String PATH_RESOURCE_CONFIG = "pathResourceConfig";
    public static final String PATH_RESOURCE_CONFIG_TEMP = "pathResourceConfigTemp";
    public static final String PATH_RESOURCE_CONFIG_GIP = "pathResourceConfigGip";
    public static final String PATH_LOCAL_CONFIG = "pathLocalConfig";

    public static final String SHARE_LIBRARY_FILE_SUFFIX = ".so";
    public static final String PATCH_FILE_SUFFIX = ".p";
    public static final String GIP_FILE_SUFFIX = ".gip";
    public static final String JAR_FILE_SUFFIX = ".jar";
    public static final String ZIP_FILE_SUFFIX = ".zip";
    public static final String SEVEN_G_FILE_SUFFIX = ".7g";
    public static final String TEMP_SUFFIX = ".temp";

    public static final String ARCH_NOT_SUPPORTED = "NOTSUPPORTED";

    public static final String REMOTE_CONFIG_FILE_NAME = "gplay_game_config.json.remote";
    public static final String REMOTE_CONFIG_FILE_NAME_TEMP = REMOTE_CONFIG_FILE_NAME + RuntimeConstants.TEMP_SUFFIX;
    public static final String CONFIG_FILE_NAME = "gplay_game_config.json";
    public static final String RESOURCE_CONFIG_FILE_NAME = "gplay_resource_config.json";
    public static final String GAME_EXTEND_FILE_NAME = "game_extend.jar";
    public static final String RESOURCE_CONFIG_FILE_NAME_GIP = "gplay_resource_config" + RuntimeConstants.GIP_FILE_SUFFIX;
    public static final String RESOURCE_CONFIG_FILE_NAME_TEMP = RESOURCE_CONFIG_FILE_NAME_GIP + RuntimeConstants.JAR_FILE_SUFFIX;
    public static final String RUNTIME_FILE_JAR_NAME = "gplay-runtime-jar" + RuntimeConstants.JAR_FILE_SUFFIX;
    public static final String RUNTIME_FILE_JAR_NAME_TEMP = RUNTIME_FILE_JAR_NAME + RuntimeConstants.TEMP_SUFFIX;

    public static final String LOADING_FETCH_RUNTIME_COMP = "fetch_runtime_comp";
    public static final String LOADING_COMPATIBLE_LOCAL_RES_RECORD = "local_res_record_compatible";

    public static final String LOADING_FETCH_ENGINE_JAR = "fetch_engine_jar";
    public static final String LOADING_LOAD_ENGINE_JAR = "load_engine_jar";

    public static final String LOADING_FETCH_ENGINE_SO = "fetch_engine_so";
    public static final String LOADING_UNZIP_ENGINE_SO = "unzip_engine_so";

    public static final String LOADING_FETCH_PATCH_SO = "fetch_patch_so";
    public static final String LOADING_FETCH_GAME_PATCH = "fetch_game_patch";
    public static final String LOADING_COMPOUND_GAME_SO = "compound_game_so";

    public static final String LOADING_FETCH_GAME_EXTEND_JAR = "fetch_game_extend_jar";
    public static final String LOADING_UNZIP_GAME_EXTEND_JAR = "unzip_game_extend_jar";
    public static final String LOADING_FETCH_GAME_EXTEND_LIBS = "fetch_game_extend_libraries";
    public static final String LOADING_UNZIP_GAME_EXTEND_LIBS = "unzip_game_extend_libraries";

    public static final String LOADING_FETCH_ENGINE_COMMON_RES = "fetch_engine_common_res";
    public static final String LOADING_UNZIP_ENGINE_COMMON_RES = "unzip_engine_common_res";
    public static final String LOADING_FETCH_ENGINE_EXTEND_LIBS = "fetch_engine_extend_libraries";
    public static final String LOADING_UNZIP_ENGINE_EXTEND_LIBS = "unzip_engine_extend_libraries";
    public static final String LOADING_FETCH_ENGINE_EXTEND_DLL = "fetch_engine_extend_dll";
    public static final String LOADING_UNZIP_ENGINE_EXTEND_DLL = "unzip_engine_extend_dll";

    public static final String LOADING_FETCH_GAME_CONFIG = "fetch_game_config";
    public static final String LOADING_REFRESH_RECORD_CONFIG = "refresh_local_record";
    public static final String LOADING_FETCH_GAME_RESOURCES_CONFIG = "fetch_manifest";
    public static final String LOADING_DELETE_NO_REF_GROUPS = "delete_no_ref_groups";

    public static final String LOADING_FETCH_BOOT_GROUP = "fetch_boot_group";
    public static final String LOADING_UNZIP_BOOT_GROUP = "unzip_boot_group";

    public static final String FILE_OFFLINE_GAME_MARKER = "offline_game_update.mark";

    public static final int MODE_TYPE_UNKNOWN_ERROR = -1;
    public static final int MODE_TYPE_NETWORK_ERROR = 0;
    public static final int MODE_TYPE_NO_SPACE_LEFT = 1;
    public static final int MODE_TYPE_FILE_VERIFY_WRONG = 2;
    public static final int MODE_TYPE_NOT_SUPPORT_ARCH = 3;
    public static final int MODE_TYPE_CANCLE_PRELOADING = 4;

    public static final int ERROR_OCCURRED_STEP_UNKNOWN = -1;
    public static final int ERROR_OCCURRED_STEP_FETCH_GAME_CONFIG = 1;
    public static final int ERROR_OCCURRED_STEP_UPDATE_RUNTIME_COMPATIBILITY = 2;
    public static final int ERROR_OCCURRED_STEP_FETCH_GAME_RESOURCE = 3;
    public static final int ERROR_OCCURRED_STEP_FETCH_RUNTIME_JAR = 4;
    public static final int ERROR_OCCURRED_STEP_FETCH_GAME_EXTEND_JAR = 6;
    public static final int ERROR_OCCURRED_STEP_FETCH_DIFF_PATCH_SO = 7;
    public static final int ERROR_OCCURRED_STEP_FETCH_GAME_SO = 8;
    public static final int ERROR_OCCURRED_STEP_FETCH_GAME_PATCH = 9;
    public static final int ERROR_OCCURRED_STEP_UPDATE_BOOT_SCENE = 10;
    public static final int ERROR_OCCURRED_STEP_FETCH_EXTEND_LIB = 11;
    public static final int ERROR_OCCURRED_STEP_FETCH_COMMON_RES = 12;
    public static final int ERROR_OCCURRED_STEP_UNITY_SHARD_LIB = 13;
    public static final int ERROR_OCCURRED_STEP_UNITY_DLL = 14;

    public static final String JAVA_CRASHES_DIR = "java_crashes";

    public static final int DOWNLOAD_RETRY_TIMES = 3;
    public static final String DOWNLOAD_VERIFY_WRONG = "download_verify_wrong";
    public static final String DOWNLOAD_GAME_RES_EXPIRED = "download_err_game_res_expired";

    public static final String NO_SPACE_LEFT = "No space left on device";
    public static final String NO_NETWORK = "No network!";
    public static final String COPY_FAILED = "Copy failed!";

    public static final String DOWNLOAD_TAG_CONFIG_JSON = "download_config_json";
    public static final String DOWNLOAD_TAG_NEWEST_CONFIG_JSON = "download_newest_config_json";
    public static final String DOWNLOAD_TAG_RUNTIME_JAR = "download_runtime_jar";
    public static final String DOWNLOAD_TAG_RUNTIME_SO = "download_runtime_so";
    public static final String DOWNLOAD_TAG_GAME_PATCH = "download_game_patch";
    public static final String DOWNLOAD_TAG_GAME_EXTEND = "download_game_extend";
    public static final String DOWNLOAD_TAG_MANIFEST_JSON = "download_manifest_json";
    public static final String DOWNLOAD_TAG_GROUP = "download_group";
    public static final String DOWNLOAD_TAG_BOOT_GROUP = "download_boot_group";
    public static final String DOWNLOAD_TAG_EXTEND_LIB = "download_extend_lib";
    public static final String DOWNLOAD_TAG_COMMON_RES = "download_common_res";
    public static final String DOWNLOAD_TAG_SHARED_LIBRARIES = "download_share_libraries";
    public static final String DOWNLOAD_TAG_DLL = "download_dll";

    public static final String DOWNLOAD_ERROR_GAME_PARAMETER_ERROR = "error_game_parameter_error";
    public static final String DOWNLOAD_ERROR_GAME_NOT_EXIST = "error_game_not_exist";
    public static final String DOWNLOAD_ERROR_NETWORK_FAILED = "error_network_failed";
    public static final String DOWNLOAD_ERROR_INVISIBLE = "error_invisible";
    public static final String DOWNLOAD_ERROR_INCOMPATIBLE = "error_incompatible";
    public static final String DOWNLOAD_ERROR_MAINTAINING = "error_maintaining";
    public static final String DOWNLOAD_ERROR_ARCH_NOT_SUPPORTED = "error_arch_not_supported";
    public static final String DOWNLOAD_ERROR_FILE_VERIFY_WRONG = "error_file_verify_wrong";
    public static final String DOWNLOAD_ERROR_NO_SPACE_LEFT = "error_no_space_left";

    // 线上、沙箱、测试
    public static final String MODE_ONLINE = "online";
    public static final String MODE_SANDBOX = "sandbox";

    public static final String ONLINE_ADDRESS = "http://api.skydragon-inc.cn/";
    public static final String SANDBOX_ADDRESS = "http://sandbox.api.skydragon-inc.cn/";

    public static final String ORIENTATION_PORTRAIT = "portrait";
    public static final String ORIENTATION_LANDSCAPE = "landscape";
    public static final String ORIENTATION_REVERSE_PORTRAIT = "reverse_portrait";
    public static final String ORIENTATION_REVERSE_LANDSCAPE = "reverse_landscape";
    public static final String ORIENTATION_SENSOR_PORTRAIT = "sensor_portrait";
    public static final String ORIENTATION_SENSOR_LANDSCAPE = "sensor_landscape";

    public static final String MAIN_SHARE_LIBRARY_NAME = "libgame.so";

    public static boolean isDebugRuntime = false;

    public static boolean isDebugRuntimeEnabled() {
        return isDebugRuntime;
    }

    public static void setDebugRuntimeEnabled(boolean b) {
        isDebugRuntime = b;
    }

    public static String hostUrl;

    public static String getServerUrl() {
        if (Utils.isEmpty(hostUrl)) {
            return ONLINE_ADDRESS;
        }
        return hostUrl;
    }

    public static void setHostUrl(String url) {
        hostUrl = url;
    }
}
