package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.bridge.BridgeHelper;
import com.skydragon.gplay.runtime.utils.ImageDownloader;
import com.skydragon.gplay.runtime.utils.LogWrapper;

public final class RuntimeNativeWrapper {
    public static final String TAG = "RuntimeNativeWrapper";

    public static void preloadGroupsFromNative(final String groupsJsonString, int ext) {
        RuntimeCore.getInstance().nativePreload(groupsJsonString, ext);
    }

    public static void preloadGroupsFromNative(final String groupsJsonString, long ext) {
        RuntimeCore.getInstance().nativePreload(groupsJsonString, ext);
    }

    public static void downloadRemoteFileFromNative(String config, long ext) {
        ImageDownloader.downloadImageFile(config, ext);
    }

    public static int getGameRunModeFromNative() {
        return RuntimeStub.getInstance().getRunningGameInfo().mRunMode;
    }

    public static void quitGameFromNative() {
        BridgeHelper.getInstance().quitGame();
    }

    /**
     * Extension Sync API
     */
    public static String extensionSyncAPI(String method, String stringArg, int intArg, double doubleArg) {
        LogWrapper.d(TAG, "extensionSyncAPI method:" + method);
        switch (method) {
            case "getGPlayGameRunMode": {
                switch (RuntimeStub.getInstance().getRunningGameInfo().mRunMode)
                {
                    case 1:
                        return "gplay_normal";
                    case 2:
                        return "gplay_divide_res";
                    default:
                        break;
                }
                break;
            }
            case "UseNewPreloadResponseMode":
                RuntimeCore.useNewPreloadResponseMode = true;
                break;
            default:
                break;
        }
        return null;
    }

    /**
     * Extension Async API
     * return result for native by nativeExtensionAPI
     */
    public static void extensionASyncAPI(String method, String stringArg, int intArg, double doubleArg) {

    }

}
