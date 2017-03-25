package com.skydragon.gplay.runtime.protocol;

import android.text.TextUtils;

import com.skydragon.gplay.runtime.RuntimeStub;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.yolanda.nohttp.Headers;
import com.yolanda.nohttp.NoHttp;
import com.yolanda.nohttp.RequestMethod;
import com.yolanda.nohttp.download.DownloadListener;
import com.yolanda.nohttp.download.DownloadRequest;
import com.yolanda.nohttp.error.NetworkError;
import com.yolanda.nohttp.error.ServerError;
import com.yolanda.nohttp.error.StorageReadWriteError;
import com.yolanda.nohttp.error.StorageSpaceNotEnoughError;
import com.yolanda.nohttp.error.TimeoutError;
import com.yolanda.nohttp.error.URLError;
import com.yolanda.nohttp.error.UnKnownHostError;
import com.yolanda.nohttp.rest.OnResponseListener;
import com.yolanda.nohttp.rest.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.regex.Pattern;

public class FileDownloadHelper {

    private static final String TAG = "FileDownloadHelper";

    public static final int ERROR_URL = 10;
    public static final int ERROR_NETWORK = 20;
    public static final int ERROR_STORAGE_READ_WRITE = 30;
    public static final int ERROR_STORAGE_SPACE_NOT_ENOUGH = 31;
    public static final int ERROR_FILE_VERIFY = 40;
    public static final int ERROR_UNDEFINED = 100;

    public static FileDownloader downloadFile(final String tag, final String url, final String savePath,
                                              final FileDownloadDelegate delegate){
        return downloadFile(tag, url, savePath, null, delegate);
    }

    public static FileDownloader downloadFile(final String tag, final String url, final String savePath,
                                              final String fileMD5, final FileDownloadDelegate delegate){
        FileDownloader fileDownloader = new FileDownloadHelper.FileDownloader();
        fileDownloader.downloadFile(tag, url, savePath, fileMD5, delegate);
        return fileDownloader;
    }

    public static void cancelAllRequests() {
        NoHttp.getDownloadQueueInstance().cancelAll();
    }

    public static class FileDownloader implements DownloadListener {
        private long mLastUpdateTime = 0;
        private long mLastRequestBytes = 0;
        private boolean mIsDownloadFinished;
        private float mDownloadSpeed = 0f;
        private final Object s_cancelSyncObj = new Object();

        private DownloadRequest mDownloadCall;

        private String mDownloadURL;
        private String mTargetFileMD5;
        private String mDownloadTag;
        private FileDownloadDelegate mDelegate;

        private int mTargetFileSize;

        public void downloadFile(final String tag, final String url, final String savePath,
                                 final String fileMD5, final FileDownloadDelegate delegate) {
            LogWrapper.i(TAG, "url:" + url + ", save path:" + savePath);

            if (delegate == null) {
                LogWrapper.e(TAG, "downloadFile: delegate is null!");
                return;
            }

            String errorMsg;
            do {
                if (TextUtils.isEmpty(savePath)) {
                    errorMsg = "downloadFile: the save path is empty:" + savePath;
                    break;
                }

                final File saveFile = new File(savePath);
                if (saveFile == null) {
                    errorMsg = "downloadFile: fail to constructs a new file using the path:" + savePath;
                    break;
                }

                if (!FileUtils.ensureDirExists(saveFile.getParent())) {
                    errorMsg = "downloadFile: cannot create parent directories for requested File location. savePath:" + savePath;
                    break;
                }

                mDownloadTag = tag;
                mDownloadURL = url;
                mTargetFileMD5 = fileMD5;
                mDelegate = delegate;

                mIsDownloadFinished = false;

                int dirSeparateIndex = savePath.lastIndexOf('/');
                final String bundleSaveDir = savePath.substring(0, dirSeparateIndex + 1);
                final String bundleSaveFileName = savePath.substring(dirSeparateIndex + 1);

                mDownloadCall = NoHttp.createDownloadRequest(url, RequestMethod.GET, bundleSaveDir, bundleSaveFileName, true, true);
                mDownloadCall.setRetryCount(2);
                NoHttp.getDownloadQueueInstance().add(tag, mDownloadCall, this);
                return;
            } while (false);

            LogWrapper.e(TAG, errorMsg);
            delegate.onFailure(ERROR_UNDEFINED, errorMsg);
        }

        @Override
        public void onStart(String what, boolean isResume, long rangeSize, Headers responseHeaders, long allCount) {
            if (!what.equals(mDownloadTag)) {
                LogWrapper.d(TAG, "The expected tag:" + mDownloadTag + ", received:" + what);
                return;
            }

            if (allCount > 0) {
                mLastRequestBytes = rangeSize;
                mDownloadSpeed = 0f;
            } else {
                mLastRequestBytes = 0;
                mDownloadSpeed = 0f;
            }

            mIsDownloadFinished = false;
            mLastUpdateTime = System.currentTimeMillis();

            if (mDelegate != null) {
                mDelegate.onStart(what);
            }
        }

        @Override
        public void onProgress(String what, int fileCount, int fileSize) {
            if (mIsDownloadFinished) {
                return;
            }

            long newTimeMillis = System.currentTimeMillis();
            if (newTimeMillis - mLastUpdateTime > 200) {
                mDownloadSpeed = 1000.0f * (fileCount - mLastRequestBytes) / (newTimeMillis - mLastUpdateTime);
                mLastUpdateTime = newTimeMillis;
                mLastRequestBytes = fileCount;
                mTargetFileSize = fileSize;

                if (mDelegate != null) {
                    mDelegate.onProgress(fileCount, fileSize);
                }
            }
        }

        @Override
        public void onFinish(String what, String filePath) {
            if (mIsDownloadFinished) {
                return;
            }
            mIsDownloadFinished = true;
            mDownloadCall = null;

            // 下载结束，需要再次更新下载速度，否则下载小文件一直得不到下载速度
            if (mTargetFileSize > 0) {
                long newTimeMillis = System.currentTimeMillis();
                mDownloadSpeed = 1.0f * (mTargetFileSize - mLastRequestBytes) / (newTimeMillis - mLastUpdateTime);
            }

            if (mDelegate != null) {
                if (mTargetFileMD5 != null && FileUtils.isFileModifiedByCompareMD5(filePath, mTargetFileMD5)) {
                    FileUtils.deleteFile(filePath);
                    mDelegate.onFailure(ERROR_FILE_VERIFY, "Download success but the MD5 is wrong!");
                } else {
                    mDelegate.onSuccess(new File(filePath));
                }
            }
        }

        public void cancel() {
            LogWrapper.w(TAG, "cancel download url:" + mDownloadURL);

            if (mIsDownloadFinished) {
                return;
            }
            mIsDownloadFinished = true;

            if (mDownloadCall != null && !mDownloadCall.isCanceled()) {
                mDownloadCall.cancel();

                synchronized (s_cancelSyncObj) {
                    if (mDelegate != null) {
                        try {
                            s_cancelSyncObj.wait(20);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        @Override
        public void onCancel(String what) {
            LogWrapper.d(TAG, "onCancel :" + what);
            synchronized (s_cancelSyncObj) {
                s_cancelSyncObj.notifyAll();
            }

            if (mDelegate != null) {
                mDelegate.onCancel();
            }
        }

        @Override
        public void onDownloadError(String what, Exception exception) {
            if (mIsDownloadFinished) {
                return;
            }
            mIsDownloadFinished = true;

            mErrorMessage = exception.getMessage();

            int errorCode = ERROR_UNDEFINED;
            if (exception instanceof ServerError) {
                errorCode = ERROR_NETWORK;
            } else if (exception instanceof NetworkError) {
                errorCode = ERROR_NETWORK;
            } else if (exception instanceof StorageReadWriteError) {
                errorCode = ERROR_STORAGE_READ_WRITE;
            } else if (exception instanceof StorageSpaceNotEnoughError) {
                errorCode = ERROR_STORAGE_SPACE_NOT_ENOUGH;
            } else if (exception instanceof TimeoutError) {
                errorCode = ERROR_NETWORK;
            } else if (exception instanceof UnKnownHostError) {
                errorCode = ERROR_NETWORK;
            } else if (exception instanceof URLError) {
                errorCode = ERROR_URL;
            }

            if (mDelegate != null) {
                mDelegate.onFailure(errorCode, exception.toString());
            }
        }

        private String mRetryHostURL;
        private String mRetryRelativePath;
        private String mRetryURLHeader;
        private String mErrorMessage;
        //防DNS劫持,从DNS服务器解析IP后通过IP重试下载
        private void avoidHijack() {
            int pathIndex;
            if (mDownloadURL.startsWith("http://")) {
                mRetryURLHeader = "http://";
                pathIndex = mDownloadURL.indexOf("/", 7);
                mRetryHostURL = mDownloadURL.substring(7, pathIndex);
            } else if (mDownloadURL.startsWith("https://")){
                mRetryURLHeader = "https://";
                pathIndex = mDownloadURL.indexOf("/", 8);
                mRetryHostURL = mDownloadURL.substring(8, pathIndex);
            } else {
                mRetryURLHeader = "";
                pathIndex = mDownloadURL.indexOf("/");
                mRetryHostURL = mDownloadURL.substring(0, pathIndex);
            }
            mRetryRelativePath = mDownloadURL.substring(pathIndex);

            LogWrapper.d(TAG, "avoidHijack host:" + mRetryHostURL + " ,header:" + mRetryURLHeader + " ,path:" + mRetryRelativePath);

            if (!isIPAddress(mRetryHostURL)) {
                String channelID = RuntimeStub.getInstance().getChannelID();
                switch (channelID) {
                    case "32400":
                    case "gplay_001":
                        queryIPOnHolaChannel();
                        break;
                    default:
                        mDelegate.onFailure(ERROR_NETWORK, mErrorMessage);
                }
            } else {
                mDelegate.onFailure(ERROR_NETWORK, mErrorMessage);
            }
        }

        private JSONArray mRetryValidIPArray;
        private int mRetryDownloadIndex = 0;
        private void queryIPOnHolaChannel() {
            ProtocolController.queryIPOnHolaChannel(mRetryHostURL, new OnResponseListener<String>() {

                @Override
                public void onSucceed(String what, Response<String> response) {
                    String responseString = response.get();
                    if (TextUtils.isEmpty(responseString)) {
                        mDelegate.onFailure(ERROR_NETWORK, "Query IP success, but response is:" + responseString);
                        return;
                    }

                    try {
                        JSONObject jsonObject = new JSONObject(responseString);
                        JSONArray answerArray = jsonObject.optJSONArray("answer");
                        LogWrapper.d(TAG, "queryIPOnHolaChannel, answer is:" + answerArray);

                        if (answerArray != null && answerArray.length() > 0) {
                            mRetryValidIPArray = new JSONArray();
                            for (int index = 0; index < answerArray.length(); ++index) {
                                JSONObject item = answerArray.getJSONObject(index);
                                String data = item.optString("rdata");
                                String type = item.optString("type");
                                if ("A".equalsIgnoreCase(type) && !TextUtils.isEmpty(data)) {
                                    mRetryValidIPArray.put(data);
                                }
                            }

                            mRetryDownloadIndex = 0;
                            if (mRetryValidIPArray.length() > mRetryDownloadIndex) {
                                String newURL = getRetryDownloadURL();
                                queryIPOnHolaChannel();
                                return;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    mDelegate.onFailure(ERROR_NETWORK, "Query IP success, but parse response failed! responseString:" + responseString);
                }

                @Override
                public void onFailed(String what, Response<String> response) {
                    Exception throwable = response.getException();
                    int statusCode = response.responseCode();
                    String responseString = throwable.getMessage();
                    if (throwable != null) {
                        mDelegate.onFailure(ERROR_NETWORK, "Query IP(" + mDownloadURL + ") failed! Original error:"
                                + mErrorMessage + ", status code:" + statusCode + ", response:" + responseString + ", throwable message:" + throwable.getMessage());
                    } else {
                        mDelegate.onFailure(ERROR_NETWORK, "Query IP(" + mDownloadURL + ") failed! Original error:"
                                + mErrorMessage + ", status code:" + statusCode + ", response:" + responseString);
                    }
                }

                @Override
                public void onStart(String what) {}

                @Override
                public void onFinish(String what) {}
            });
        }

        private String getRetryDownloadURL() {
            String newURL = null;
            if (mRetryValidIPArray != null && mRetryDownloadIndex < mRetryValidIPArray.length()) {
                try {
                    newURL = mRetryURLHeader + mRetryValidIPArray.getString(mRetryDownloadIndex) + mRetryRelativePath;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                mRetryDownloadIndex += 1;
            }

            return newURL;
        }

        private boolean isIPAddress(String address) {
            if (TextUtils.isEmpty(address))
                return false;

            final Pattern pattern = Pattern.compile( "^((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5]|[*])$" );
            return pattern.matcher(address).matches();
        }

        public int getDownloadSpeed() {
            int ret = (int) mDownloadSpeed;
            mDownloadSpeed = 0f;
            return ret;
        }
    }
}
