package com.skydragon.gplay.runtime.bridge;

import com.skydragon.gplay.runtime.RuntimeEnvironment;
import com.skydragon.gplay.runtime.RuntimeLauncher;
import com.skydragon.gplay.runtime.RuntimeStub;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;

import java.util.HashMap;
import java.util.Map;

public final class RuntimeBridgeProxy implements IBridgeProxy {
    private static final String TAG = "RuntimeBridgeProxy";

    private Map<String, ICallback> mListenerMap = new HashMap<>();

    public RuntimeBridgeProxy() {
    }

    public void setListener(String method, ICallback listener) {
        if (mListenerMap.containsKey(method)) {
            mListenerMap.remove(method);
        }

        mListenerMap.put(method, listener);
    }

    private Object notifyListener(String method, Map<String, Object> args) {
        Object ret = null;
        if (mListenerMap.containsKey(method)) {
            ret = mListenerMap.get(method).onCallback(method, args);
        }
        return ret;
    }

    @Override
    public Object invokeMethodSync(String method, Map<String, Object> args) {
        Object ret = null;

        LogWrapper.d(TAG, "invokeMethodSync : " + method);
        switch (method) {
            case "getSDKVersionCode": {
                ret = RuntimeStub.getInstance().getVersionCode();
                break;
            }
            case "getGameDir": {
                ret = getGameDir();
                break;
            }
            case "getGameResourceDir": {
                ret = getGameResourceDir();
                break;
            }
            case "getGameSharedLibrariesDir": {
                ret = getGameSharedLibrariesDir();
                break;
            }
            case "getSharedLibraryPath": {
                ret = RuntimeLauncher.getInstance().getStandardLibraryDirectory();
                break;
            }
            case "getRuntimeResourceDir": {
                ret = getRuntimeResourceDir();
                break;
            }
            case "getExtendLibrariesDir": {
                ret = getExtendLibrariesDir();
                break;
            }
            case "getEngineCommonResDir": {
                ret = RuntimeLauncher.getInstance().getEngineCommonResDir();
                break;
            }
            case "getRuntimeImagesDir": {
                ret = getRuntimeImagesDir();
                break;
            }
            case "getRuntimeGenericResourceDir": {
                ret = getRuntimeGenericResourceDir();
                break;
            }
            case "getGameOrientation": {
                ret = getGameOrientation();
                break;
            }
            case "notifyOnPrepareEngineFinished": {
                notifyOnPrepareEngineFinished();
                break;
            }
            case "getGameRunMode": {
                ret = RuntimeStub.getInstance().getRunningGameInfo().mRunMode;
                break;
            }
            default:
                ret = notifyListener(method, args);
                break;
        }

        return ret;
    }

    @Override
    public void invokeMethodAsync(final String method, final Map<String, Object> args, final ICallback callback) {
        String argsStr = args != null ? args.toString() : "null";
        LogWrapper.d(TAG, "invokeMethodAsync:" + method + " args:" + argsStr);
    }

    private String getGameDir() {
        return RuntimeEnvironment.pathCurrentGameDir;
    }

    private String getGameResourceDir() {
        return RuntimeEnvironment.pathCurrentGameDir + "resource/";
    }

    private String getRuntimeResourceDir() {
        return RuntimeEnvironment.pathHostRuntimeResourceDir;
    }

    private String getRuntimeGenericResourceDir() {
        String runtimeDir = FileUtils.ensurePathEndsWithSlash(RuntimeEnvironment.pathHostRuntimeDir);
        return runtimeDir + "resources/";
    }

    private String getExtendLibrariesDir() {
        return RuntimeLauncher.getInstance().getExtendLibrariesDir();
    }

    private String getRuntimeImagesDir() {
        return FileConstants.getGameImagesDir();
    }

    private String getGameOrientation() {
        return RuntimeEnvironment.currentGameOrientation;
    }

    private void notifyOnPrepareEngineFinished() {
        BridgeHelper.getInstance().prepareEngineFinished();
    }

    public Object getGameSharedLibrariesDir() {
        return RuntimeLauncher.getInstance().getGameSharedLibraryDirectory();
    }
}
