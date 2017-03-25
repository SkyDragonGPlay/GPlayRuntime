package com.skydragon.gplay.runtime;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.skydragon.gplay.service.IDownloadProxy;
import com.skydragon.gplay.service.IRuntimeCallback;
import com.skydragon.gplay.service.IRuntimeProxy;
import com.skydragon.gplay.thirdsdk.IChannelSDKBridge;
import com.skydragon.gplay.thirdsdk.IChannelSDKServicePlugin;

import org.json.JSONObject;

import java.util.Map;

/**
 * 游戏引擎
 */
public interface IRuntimeService {

    /**
     * 初始化游戏引擎
     *
     */
    void initRuntime(Activity activity, IChannelSDKBridge channelSDKBridge, IChannelSDKServicePlugin channelSDKServicePlugin);

    /**
     * 得到用于显示的view
     *
     */
    View getGameView();

    /**
     * 关闭游戏
     */
    void closeGame();

    /**
     * 生命周期onPause
     */
    void onPause();

    /**
     * 生命周期onResume
     */
    void onResume();

    /**
     * 生命周期onStop
     */
    void onStop();

    /**
     * 生命周期onDestroy
     */
    void onDestroy();

    void onWindowFocusChanged(boolean hasFocus);

    /**
     * 生命周期onNewIntent
     *
     */
    void onNewIntent(Intent intent);

    /**
     * 生命周期onActivityResult
     *
     */
    void onActivityResult(int requestCode, int resultCode, Intent data);

    /**
     * 初始化SDK
     *
     */
    boolean init(Context context, String channelID, String runtimeDir, String cacheDir);

    /**
     * 设置回调宿主代理
     *
     */
    void setRuntimeProxy(IRuntimeProxy proxy);


    /**
     * 清除所有游戏缓存
     *
     */
    boolean cleanAllGameCache();

    /**
     * 清除游戏缓存
     *
     * @param tag 游戏gameKey
     */
    boolean cleanGameCache(String tag);

    /**
     * 获取SDCard缓存路径
     *
     */
    String getCacheDir();

    /**
     * 启动游戏
     *
     */
    void startGame(Activity activity, String gameKey);

    /**
     * 启动游戏
     *
     * @param bHostManagerRuntime 由宿主管理Runtime
     */
    void startGame(Activity activity, String gameKey, boolean bHostManagerRuntime);

    /**
     * 启动游戏
     */
    void startGame(Activity activity, JSONObject jsonObject);

    /**
     * 启动游戏,模拟器接口
     */
    void startGameForDebug(Activity activity, JSONObject jsonObject);

    /**
     * 设置Runtime API接口访问的服务器Host 地址, 所有接口path路径都是固定的,但可以更换host地址
     */
    void setRuntimeHostUrl(String host);

    /**
     * 设置统一SDK接口访问的服务器Host 地址, 所有接口path路径都是固定的,但可以更换host地址
     */
    void setUnitSDKHostUrl(String host);

    /**
     * 取消游戏下载
     */
    void cancelStartGame();

    /**
     * 重试游戏下载
     */
    void retryStartGame(String gameKey);

    /**
     * 设置预下载Gplay文件的监听代理
     */
    void setPrepareRuntimeProxy(IDownloadProxy downloadProxy);

    /**
     * 预下载启动Gplay需要的文件
     */
    void prepareRuntime(Context context);

    /**
     * 停止预下载Gplay相关文件
     */
    void cancelPrepareRuntime();

    /**
     * 本地调试模式
     */
    void startGameForLocalDebug(Activity activity, String gameInfo);

    /**
     * 静默下载游戏开关，需要在 startGame 前调用
     */
    void setSilentDownloadEnabled(boolean enabled);

    /**
     * 启动静默下载
     */
    void startSilentDownload();

    /**
     * 停止静默下载
     */
    void stopSilentDownload();

    /**
     * 获取游戏信息
     * {
     *     "engine_type":"", //引擎类型
     *     "engine_version":"", //引擎版本
     *     "client_id":"", //游戏ID
     *     "orientation":"", //屏幕方向, 横屏 landscape, 竖屏 portrait
     *     "game_name":"", //游戏名称
     *     "package_name":"", //包名
     *     "version_name":"", //版本名称
     *     "icon_url": ""// 图标下载地址
     * }
     */
    JSONObject getGameInfo();

    Object invokeMethodSync(String method, Map<String, Object> args);

    void invokeMethodAsync(final String method, final Map<String, Object> args, final IRuntimeCallback callback);

    void notifyPreloadFinished();

    void retryPreloadScenes();
}
