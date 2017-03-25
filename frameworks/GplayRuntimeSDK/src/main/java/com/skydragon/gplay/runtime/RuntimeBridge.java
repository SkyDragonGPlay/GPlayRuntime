package com.skydragon.gplay.runtime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.service.IDownloadProxy;
import com.skydragon.gplay.service.IRuntimeBridge;
import com.skydragon.gplay.service.IRuntimeBridgeProxy;
import com.skydragon.gplay.service.IRuntimeCallback;
import com.skydragon.gplay.service.IRuntimeProxy;
import com.skydragon.gplay.thirdsdk.IChannelSDKBridge;
import com.skydragon.gplay.thirdsdk.IChannelSDKServicePlugin;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class RuntimeBridge implements IRuntimeBridge {
    private static final String TAG = "RuntimeBridge";
    private IRuntimeService mRuntimeService;
    private IRuntimeBridgeProxy mRuntimeBridgeProxy;

    private HashMap<String,Object> options = new HashMap<>();

    public RuntimeBridge() {
        mRuntimeService = new RuntimeService();
    }

    @Override
    public IRuntimeBridgeProxy getBridgeProxy() {
        return mRuntimeBridgeProxy;
    }

    @Override
    public void setBridgeProxy(IRuntimeBridgeProxy proxy) {
        mRuntimeBridgeProxy = proxy;
    }

    @Override
    public void setOption(String key, Object value) {
        options.put(key, value);
    }

    @Override
    public Object getOption(String key) {
        return options.get(key);
    }

    @Override
    public Object invokeMethodSync(String method, Map<String, Object> args) {
        LogWrapper.d(TAG, "invokeMethodSync method:" + method);

        switch (method) {
            case "init": {
                return mRuntimeService.init(
                        (Context)args.get("context"),
                        (String)args.get("channelID"),
                        (String)args.get("runtimeDir"),
                        (String) args.get("cacheDir"));
            }
            case "initRuntime": {
                mRuntimeService.initRuntime(
                        (Activity) args.get("activity"),
                        (IChannelSDKBridge)args.get("channelSDKBridge"),
                        (IChannelSDKServicePlugin)args.get("channelSDKServicePlugin"));
                break;
            }
            case "getGameView": {
                return mRuntimeService.getGameView();
            }
            case "closeGame": {
                mRuntimeService.closeGame();
                break;
            }
            case "onNewIntent": {
                Intent intent = (Intent)args.get("intent");
                mRuntimeService.onNewIntent(intent);
                break;
            }
            case "onPause": {
                mRuntimeService.onPause();
                break;
            }
            case "onResume": {
                mRuntimeService.onResume();
                break;
            }
            case "onDestroy": {
                mRuntimeService.onDestroy();
                break;
            }
            case "onStop": {
                mRuntimeService.onStop();
                break;
            }
            case "onWindowFocusChanged": {
                boolean hasFocus = (Boolean)args.get("hasFocus");
                mRuntimeService.onWindowFocusChanged(hasFocus);
                break;
            }
            case "onActivityResult": {
                int requestCode = (Integer)args.get("requestCode");
                int resultCode = (Integer)args.get("resultCode");
                Intent data = (Intent)args.get("data");
                mRuntimeService.onActivityResult(requestCode, resultCode, data);
                break;
            }
            case "cleanAllGameCache": {
                return mRuntimeService.cleanAllGameCache();
            }
            case "cleanGameCache": {
                String tag = (String)args.get("tag");
                return mRuntimeService.cleanGameCache(tag);
            }
            case "getCacheDir": {
                return mRuntimeService.getCacheDir();
            }
            case "startGame": {
                startGame(args);
                break;
            }
            case "cancelStartGame": {
                mRuntimeService.cancelStartGame();
                break;
            }
            case "startGameForDebug": {
                Activity activity = (Activity)args.get("activity");
                JSONObject jsonObject = (JSONObject)args.get("jsonObject");
                mRuntimeService.startGameForDebug(activity, jsonObject);
                break;
            }
            case "retryStartGame": {
                String gameKey = (String)args.get("gameKey");
                mRuntimeService.retryStartGame(gameKey);
                break;
            }
            case "cancelPrepareRuntime": {
                mRuntimeService.cancelPrepareRuntime();
                break;
            }
            case "prepareRuntime": {
                Context context = (Context)args.get("context");
                mRuntimeService.prepareRuntime(context);
                break;
            }
            case "startGameForLocalDebug": {
                Activity activity = (Activity)args.get("activity");
                String strGameInfo = (String)args.get("jsonStrGame");
                mRuntimeService.startGameForLocalDebug(activity, strGameInfo);
                break;
            }
            case "setRuntimeHostUrl": {
                String hostUrl = (String)args.get("host");
                mRuntimeService.setRuntimeHostUrl(hostUrl);
                break;
            }
            case "setUnitSDKHostUrl": {
                String hostUrl = (String)args.get("host");
                mRuntimeService.setUnitSDKHostUrl(hostUrl);
                break;
            }
            case "setRuntimeProxy": {
                IRuntimeProxy runtimeProxy = (IRuntimeProxy)args.get("proxy");
                mRuntimeService.setRuntimeProxy(runtimeProxy);
                break;
            }
            case "setSilentDownloadEnabled": {
                boolean enabled = (Boolean)args.get("enabled");
                mRuntimeService.setSilentDownloadEnabled(enabled);
                break;
            }
            case "startSilentDownload": {
                mRuntimeService.startSilentDownload();
                break;
            }
            case "stopSilentDownload": {
                mRuntimeService.stopSilentDownload();
                break;
            }
            case "setPrepareRuntimeProxy": {
                IDownloadProxy proxy = (IDownloadProxy)args.get("downloadProxy");
                mRuntimeService.setPrepareRuntimeProxy(proxy);
                break;
            }
            case "getGameInfo": {
                return mRuntimeService.getGameInfo();
            }
            case "PRELOAD_SCENES_RETRY" :{
                mRuntimeService.retryPreloadScenes();
                break;
            }
            case "PRELOAD_SCENES_ONFINISH" :{
                mRuntimeService.notifyPreloadFinished();
                break;
            }
            case "GET_VERSION_CONFIGS":{
                return mRuntimeService.invokeMethodSync(method,args);
            }
            default:
                LogWrapper.e(TAG, method + " not exist!!!");
                break;
        }
        return null;
    }

    @Override
    public void invokeMethodAsync(final String method, final Map<String, Object> args, final IRuntimeCallback callback) {
        mRuntimeService.invokeMethodAsync(method, args, callback);
    }

    private void startGame(Map<String, Object> args) {
        String errorMsg = RuntimeConstants.DOWNLOAD_ERROR_GAME_PARAMETER_ERROR;
        do {
            if (args == null) {
                LogWrapper.e(TAG,"startGame failed: args is null");
                break;
            }

            if(LogWrapper.SHOW_LOG)
                LogWrapper.i(TAG, "startGame args:" + args.toString());

            Activity activity = (Activity)args.get("activity");
            if (activity == null) {
                LogWrapper.e(TAG,"startGame failed: activity is null");
                break;
            }

            Object objGameKey = args.get("gameKey");
            Object objJson = args.get("jsonObject");
            Object objHostManagerRuntime = args.get("bHostManagerRuntime");

            if (objJson != null) {
                mRuntimeService.startGame(activity, (JSONObject)objJson);
            }
            else if(objHostManagerRuntime != null) {
                if (objGameKey != null) {
                    mRuntimeService.startGame(activity, (String) objGameKey, (Boolean)objHostManagerRuntime);
                }
                else {
                    LogWrapper.e(TAG, "startGame failed:The arguments not valid! args:" + args.toString());
                    break;
                }
            }
            else {
                if (objGameKey != null) {
                    mRuntimeService.startGame(activity, (String) objGameKey);
                }
                else {
                    LogWrapper.e(TAG, "startGame failed:The arguments not valid! args:" + args.toString());
                    break;
                }
            }

            errorMsg = null;
        } while (false);

        if (errorMsg != null) {
            IRuntimeProxy proxy = RuntimeStub.getInstance().getRuntimeProxy();
            if (proxy != null) {
                proxy.onDownloadGameFailure(errorMsg);
            }
        }
    }
}
