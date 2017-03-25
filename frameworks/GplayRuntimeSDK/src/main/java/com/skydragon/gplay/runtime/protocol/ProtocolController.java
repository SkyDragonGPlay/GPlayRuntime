package com.skydragon.gplay.runtime.protocol;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.callback.ProtocolCallback;
import com.skydragon.gplay.runtime.entity.ResultInfo;
import com.skydragon.gplay.runtime.entity.game.GameInfo;
import com.skydragon.gplay.runtime.entity.runtime.RuntimeCompatibilityInfo;
import com.skydragon.gplay.runtime.utils.AESUtil;
import com.skydragon.gplay.runtime.utils.FileConstants;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.Utils;
import com.yolanda.nohttp.NoHttp;
import com.yolanda.nohttp.RequestMethod;
import com.yolanda.nohttp.rest.OnResponseListener;
import com.yolanda.nohttp.rest.Request;
import com.yolanda.nohttp.rest.RequestQueue;
import com.yolanda.nohttp.rest.Response;
import com.yolanda.nohttp.tools.MultiValueMap;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

public final class ProtocolController {

    private static final String TAG = "ProtocolController";

    private static String SERVER_URL = FileUtils.ensurePathEndsWithSlash(RuntimeConstants.getServerUrl());

    private static RequestParamsEx _RequestParamsExt = new RequestParamsEx();

    private static RequestQueue _RequestQueue = NoHttp.getRequestQueueInstance();

    /**
     * 获取游戏信息的url
     */
    private static final String REQUEST_API_GAME_INFO_URL = "api/game/info";

    /**
     * 获取游戏列表
     */
    private static final String REQUEST_API_GAME_LIST_URL = "api/game/list";

    /**
     * 获取SDK的runtime兼容信息
     */
    private static final String REQUEST_API_SDK_INIT_URL = "api/sdk/init";

    /**
     * 获取摘要
     */
    private static final String REQUEST_SYSTEM_INFO = "common/f";


    public static String getApiUrlVer(String url) {
        return "http://sandbox.api.skydragon-inc.cn/" + url;
        //return SERVER_URL + url;
    }

    /**
     * 根据游戏key获取游戏信息
     *
     * @param gamekey  游戏key
     * @param callback 回调接口
     */
    public static void requestGameInfoByKey(final String channelId, final String gamekey, final ProtocolCallback<GameInfo> callback) {

        String url = getApiUrlVer(REQUEST_API_GAME_INFO_URL);
        final Request request = NoHttp.createJsonObjectRequest(url, RequestMethod.GET);
        request.add(_RequestParamsExt.getParamsExts());
        request.add("chn", channelId);
        request.add("cid", gamekey);

        printRequest(request);

        _RequestQueue.add("GameInfo", request, new OnResponseListener() {
            @Override
            public void onStart(String what) {}

            @Override
            public void onSucceed(String what, Response response) {
                LogWrapper.d(TAG, "requestGameInfoByKey client_id: " + gamekey + ",success : " + response.isSucceed());
                JSONObject jsonObject = (JSONObject)response.get();
                ResultInfo resultInfo = parseResultInfo(jsonObject);
                if (!resultInfo.isSuccessed()) {
                    callback.onFailure(resultInfo);
                    return;
                }
                String tempData = jsonObject.optString("data");
                LogWrapper.d(TAG, "requestGameInfoByKey encryptData: " + tempData);
                final String encryptData = tempData;
                requestCryptData(new ProtocolCallback<String>() {
                    @Override
                    public void onSuccess(String obj) {
                        LogWrapper.d(TAG, "requestCryptData onSuccess: " + obj);
                        JSONObject jsonObject;
                        String decryptData = "";
                        try {
                            jsonObject = new JSONObject(obj);
                            decryptData = jsonObject.optString("data");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        String decryptKey = Utils.parseCryptData(decryptData);
                        LogWrapper.d(TAG, "requestCryptData decrpt key: " + decryptKey);
                        String sJsonStr = null;
                        try {
                            sJsonStr = AESUtil.decode(encryptData, decryptKey);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        LogWrapper.i(TAG, "deCryptData: " + sJsonStr);
                        try {
                            jsonObject = new JSONObject(sJsonStr);
                            GameInfo gInfo = GameInfo.fromJson(jsonObject);
                            FileUtils.writeStringToFile(FileConstants.getLocalGameInfoJsonPath(gamekey),sJsonStr);
                            callback.onSuccess(gInfo);
                            return;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        callback.onFailure(new ResultInfo());
                    }

                    @Override
                    public void onFailure(ResultInfo err) {}
                });
            }

            @Override
            public void onFailed(String what, Response response) {
                callback.onFailure(new ResultInfo());
            }

            @Override
            public void onFinish(String what) {}
        });
    }

    private static void printRequest(Request request) {
        LogWrapper.v(TAG, "Requet " + request.url());
        MultiValueMap map =request.getParamKeyValues();

        LogWrapper.v(TAG, "params[");
        if(map != null) {
            Set<String> set = map.keySet();
            for (String key : set) {
                LogWrapper.v(TAG, key + ":" + map.getValues(key));
            }
        }
        LogWrapper.v(TAG, "]");
    }

    /**
     * 下载 Runtime Core 与当前 sdk 的兼容信息
     *
     * @param version  SDK 版本
     * @param arch  手机架构
     * @param callback 访问回调
     */
    public static void downloadRuntimeCompatibility(String version, String arch, final ProtocolCallback<RuntimeCompatibilityInfo> callback) {
        LogWrapper.d(TAG, "ProtocolController downloadRuntimeCompatibility called! version:" + version + ", arch:" + arch);
        final String url = getApiUrlVer(REQUEST_API_SDK_INIT_URL);
        final Request request = NoHttp.createJsonObjectRequest(url, RequestMethod.GET);
        request.add("ver", version);
        request.add("arch",  arch);
        _RequestQueue.add("RuntimeCompatibility", request, new OnResponseListener() {
            @Override
            public void onStart(String what) {}

            @Override
            public void onSucceed(String what, Response response) {
                if (response == null) {
                    ResultInfo resultInfo = new ResultInfo();
                    resultInfo.setMsg("Can not get Json from response");
                    callback.onFailure(resultInfo);
                } else {
                    JSONObject responseJson = (JSONObject) response.get();
                    JSONObject jsonData = responseJson.optJSONObject("data");
                    if (null != jsonData) {
                        RuntimeCompatibilityInfo info = parseRuntimeCompatibilityInfo(jsonData);
                        FileUtils.writeJsonObjectToFile(FileConstants.getLocalRuntimeCompatibilityPath(),jsonData);
                        callback.onSuccess(info);
                    } else {
                        LogWrapper.w(TAG, "downloadRuntimeCompatibility response:" + responseJson + ", url:" + url);
                        ResultInfo resultInfo = new ResultInfo();
                        resultInfo.setMsg("Can not get Json from response");
                        callback.onFailure(resultInfo);
                    }
                }
            }

            @Override
            public void onFailed(String what, Response response) {
                ResultInfo resultInfo = new ResultInfo();
                Exception ex = response.getException();
                if(ex != null) {
                    resultInfo.setMsg(ex.getMessage());
                } else {
                    resultInfo.setMsg("Unknow error occourred! ");
                }
                callback.onFailure(resultInfo);
            }

            @Override
            public void onFinish(String what) {}
        });
    }


    public static void requestCryptData(final ProtocolCallback<String> callback) {
        String url = getApiUrlVer(REQUEST_SYSTEM_INFO);
        final Request request = NoHttp.createStringRequest(url, RequestMethod.GET);
        request.add(_RequestParamsExt.getParamsExts());
        _RequestQueue.add("CryptData", request, new OnResponseListener() {
            @Override
            public void onStart(String what) {}

            @Override
            public void onSucceed(String what, Response response) {
                callback.onSuccess((String) response.get());
            }

            @Override
            public void onFailed(String what, Response response) {
                ResultInfo info = new ResultInfo();
                info.setIsSuccess(false);
                callback.onFailure(info);
            }

            @Override
            public void onFinish(String what) {}
        });
    }

    /**
     * 获取 runtime 版本兼容信息
     */
    public static RuntimeCompatibilityInfo parseRuntimeCompatibilityInfo(JSONObject jsonObject) {
        return RuntimeCompatibilityInfo.fromJson(jsonObject);
    }

    public static void queryIPOnHolaChannel(String hostUrl, OnResponseListener<String> responseListener) {

        final String queryIPURL = "http://52.89.140.122/" + hostUrl + "/a";
        LogWrapper.d(TAG, "queryIPOnHolaChannel url:" + queryIPURL);
        String url = getApiUrlVer(REQUEST_SYSTEM_INFO);
        final Request request = NoHttp.createStringRequest(url, RequestMethod.GET);
        request.add(_RequestParamsExt.getParamsExts());
        _RequestQueue.add("QueryIPOnHolaChannel", request, responseListener);
    }

    /**
     * 返回请求结果信息
     */
    public static ResultInfo parseResultInfo(JSONObject jsonObject) {
        ResultInfo info = new ResultInfo();
        try {
            JSONObject resultObject = jsonObject.getJSONObject("result");
            String status = resultObject.optString("status");
            info.setIsSuccess(status.equals("ok"));
            int errorCode = resultObject.optInt("error_no");
            info.setErrorCode(errorCode);
            String error = resultObject.optString("error");
            info.setMsg(error);
            return info;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return info;
    }

    public static void cancelAllRequests() {
        _RequestQueue.cancelAll();
    }
}
