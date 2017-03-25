package com.skydragon.gplay.runtime.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

import com.skydragon.gplay.runtime.RuntimeConstants;
import com.skydragon.gplay.runtime.bridge.BridgeHelper;
import com.skydragon.gplay.runtime.protocol.FileDownloadDelegate;
import com.skydragon.gplay.runtime.protocol.FileDownloadHelper;
import com.skydragon.gplay.runtime.protocol.ProtocolController;

import org.apache.http.Header;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;


public final class ImageDownloader {

    private static final String TAG = "ImageDownloader";

    private static class DownloadInfo {
        public DownloadInfo(String jsonConfig, long ext) {
            mConfig = jsonConfig;
            mExt = ext;
        }

        public String mConfig;
        public long mExt;
    }

    private static final int MAX_DOWNLOAD_COUNT = 10;
    private static int sCurrentDownloadIndex = 0;

    private static ArrayList<DownloadInfo> sDownloadInfoList = new ArrayList<>();

    private DownloadInfo mDownloadInfo;

    public ImageDownloader(DownloadInfo info) {
        mDownloadInfo = info;
    }

    private void start() {
        try {
            LogWrapper.d(TAG, "downloadRemoteFileFromNative " + "config is " + mDownloadInfo.mConfig);
            if (null == mDownloadInfo.mConfig) {
                LogWrapper.e(TAG, "config is null");
                return;
            }

            final JSONObject conf = new JSONObject(mDownloadInfo.mConfig);
            final String url = conf.getString("url");
            String option = conf.getString("option");
            int width = 0, height = 0;
            if (!option.equals("null")) {
                JSONObject optionObject = conf.getJSONObject("option");
                width = optionObject.getInt("width");
                height = optionObject.getInt("height");
            }
            String picName = conf.getString("picName");
            String picDir = FileConstants.getGameImagesDir();

            String picPath = picDir + picName;
            LogWrapper.d(TAG, "Icon path is " + picPath);
            conf.put("picPath", picPath);
            conf.put("isSuccess", false);

            FileUtils.ensureDirExists(picDir);
            if (FileUtils.isExist(picPath)) {
                LogWrapper.d(TAG, "Icon is exists path is " + picPath);
                conf.put("isSuccess", true);
                postResultOnGlThread(conf);
                return;
            }

            final String savePath = picPath;
            final String savePathTemp = picPath + RuntimeConstants.TEMP_SUFFIX;
            final int finalWidth = width;
            final int finalHeight = height;

            FileDownloadHelper.downloadFile("ImageDownloader", url, savePathTemp, new FileDownloadDelegate() {
                @Override
                public void onStart(String tag) {}

                @Override
                public void onSuccess(File file) {
                    if (!BridgeHelper.getInstance().isRunning()) {
                        return;
                    }

                    FileOutputStream out = null;
                    try {
                        continueDownload();
                        Bitmap bitmap = BitmapFactory.decodeFile(savePathTemp);
                        if (finalWidth != 0 || finalHeight != 0) {
                            Matrix matrix = new Matrix();
                            float scaleWidth = ((float) finalWidth) / bitmap.getWidth();
                            float scaleHeight = ((float) finalHeight) / bitmap.getHeight();
                            matrix.postScale(scaleWidth, scaleHeight);
                            Bitmap resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            out = new FileOutputStream(savePath);
                            Bitmap.CompressFormat format = Bitmap.CompressFormat.JPEG;
                            if (conf.has("suffix") && conf.getString("suffix").equalsIgnoreCase(".png")) {
                                format = Bitmap.CompressFormat.PNG;
                            }
                            resizeBmp.compress(format, 90, out);
                            out.flush();
                            FileUtils.deleteFile(savePathTemp);
                        } else {
                            LogWrapper.d(TAG, "copy origin image");
                            FileUtils.renameFile(savePathTemp, savePath);
                        }
                        conf.put("isSuccess", true);
                        postResultOnGlThread(conf);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        TryCloseUtils.tryClose(out);
                    }
                }

                @Override
                public void onProgress(long downloadedSize, long totalSize) {}

                @Override
                public void onFailure(int errorCode, String errorMsg) {
                    LogWrapper.e(TAG, "download fail");
                    if (!BridgeHelper.getInstance().isRunning()) {
                        return;
                    }
                    continueDownload();
                    postResultOnGlThread(conf);
                    FileUtils.deleteFile(savePathTemp);
                }

                @Override
                public void onCancel() {
                    if (!BridgeHelper.getInstance().isRunning()) {
                        return;
                    }
                    FileUtils.deleteFile(savePathTemp);
                    continueDownload();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void postResultOnGlThread(final JSONObject result) {
        BridgeHelper.getInstance().runOnGLThread(new Runnable() {

            @Override
            public void run() {
                if (BridgeHelper.getInstance().isRunning()) {
                    BridgeHelper.getInstance().downloadRemoteFileCallback(result.toString(), mDownloadInfo.mExt);
                }
            }

        });
    }

    private static void continueDownload() {
        if (sCurrentDownloadIndex - 1 >= 0) {
            sCurrentDownloadIndex--;
        }

        while (sCurrentDownloadIndex < MAX_DOWNLOAD_COUNT && !sDownloadInfoList.isEmpty()) {
            new ImageDownloader(sDownloadInfoList.remove(0)).start();
            sCurrentDownloadIndex++;
        }
    }

    public static void downloadImageFile(final String config, final long ext) {

        ThreadUtils.runOnUIThread(new Runnable() {

            @Override
            public void run() {

                DownloadInfo info = new DownloadInfo(config, ext);
                if (sCurrentDownloadIndex <= MAX_DOWNLOAD_COUNT) {
                    new ImageDownloader(info).start();
                    sCurrentDownloadIndex++;
                } else {
                    sDownloadInfoList.add(info);
                }
            }
        });
    }
}
