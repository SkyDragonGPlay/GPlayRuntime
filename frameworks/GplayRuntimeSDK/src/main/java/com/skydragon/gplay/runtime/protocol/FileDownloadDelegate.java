package com.skydragon.gplay.runtime.protocol;

import java.io.File;

public abstract class FileDownloadDelegate {
    public abstract void onStart(String tag);
    public abstract void onSuccess(File file);
    public abstract void onProgress(long downloadedSize, long totalSize);
    public abstract void onFailure(int errorCode, String errorMsg);
    public abstract void onCancel();
}
