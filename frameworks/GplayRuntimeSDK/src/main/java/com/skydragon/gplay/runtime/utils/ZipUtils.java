package com.skydragon.gplay.runtime.utils;

import com.skydragon.gplay.runtime.RuntimeConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import Utils.SevenZipHelper;

public final class ZipUtils {

    private static final String TAG = "ZipUtils";

    public interface OnUnzipListener {
        void onUnzipSucceed(List<String> unzipFiles);

        void onUnzipProgress(float percent);

        void onUnzipFailed(String errorMsg);

        void onUnzipInterrupt();

        boolean isUnzipInterrupted();
    }

    public static boolean isEnoughSpaceForUnzipFile(String path, String zipFile) {
        long size = getUncompressedSize(zipFile);
        return MemorySpaceUtils.hasEnoughSDAvailableSize(path, size);
    }

    public static void unpackZipAsync(final String zipPath, final String unzipTo, final OnUnzipListener lis) {
        unpackZipAsync(zipPath, unzipTo, false, lis);
    }

    public static void unpackZipAsync(final String zipPath, final String unzipTo, final boolean withFolder, final OnUnzipListener lis) {
        unpackZipAsync(zipPath, unzipTo, withFolder, Thread.NORM_PRIORITY, lis);
    }

    public static void unpackZipAsync(final String zipPath, final String unzipTo, final boolean withFolder, final int unzipThreadPriority, final OnUnzipListener lis) {
        ThreadUtils.runAsyncThread(new Runnable() {

            @Override
            public void run() {

                unpackZip(zipPath, unzipTo, withFolder, new OnUnzipListener() {

                    @Override
                    public void onUnzipSucceed(final List<String> unzipFiles) {
                        LogWrapper.d(TAG, "unpackZipAsync ( " + zipPath + " ) succeed!");
                        ThreadUtils.runOnUIThread(new Runnable() {

                            @Override
                            public void run() {
                                lis.onUnzipSucceed(unzipFiles);
                            }

                        });
                    }

                    @Override
                    public void onUnzipProgress(final float percent) {
                        ThreadUtils.runOnUIThread(new Runnable() {

                            @Override
                            public void run() {
                                lis.onUnzipProgress(percent);
                            }

                        });
                    }

                    @Override
                    public void onUnzipFailed(String errorMsg) {
                        final String msg;
                        if (errorMsg.contains(RuntimeConstants.NO_SPACE_LEFT) ) {
                            msg = RuntimeConstants.NO_SPACE_LEFT;
                        } else {
                            msg = errorMsg;
                        }
                        ThreadUtils.runOnUIThread(new Runnable() {

                            @Override
                            public void run() {
                                lis.onUnzipFailed(msg);
                            }

                        });
                    }

                    @Override
                    public void onUnzipInterrupt() {
                        ThreadUtils.runOnUIThread(new Runnable() {

                            @Override
                            public void run() {
                                lis.onUnzipInterrupt();
                            }

                        });
                    }

                    @Override
                    public boolean isUnzipInterrupted() {
                        return lis.isUnzipInterrupted();
                    }
                });
            }
        });
    }

    public static void unpack7zAsync(final String zipPath, final String unzipTo, final boolean withFolder, int threadPriority, final OnUnzipListener lis) {
        String outDir = unzipTo;
        if (withFolder) {
            outDir = unzipTo + FileUtils.getFileName(zipPath, false) + '/';
        }

        SevenZipHelper.extract7zAsync(zipPath, outDir, threadPriority, new SevenZipHelper.OnExtractListener() {
            @Override
            public void onExtractFailed(String errorMsg) {
                lis.onUnzipFailed(errorMsg);
            }

            @Override
            public void onExtractProgress(float percent) {
                lis.onUnzipProgress(percent);
            }

            @Override
            public void onExtractSucceed() {
                lis.onUnzipSucceed(null);
            }

            @Override
            public void onExtractInterrupt() {
                lis.onUnzipInterrupt();
            }

            @Override
            public boolean isExtractInterrupted() {
                return lis.isUnzipInterrupted();
            }
        });
    }

    public static boolean unpackZip(String zipPath, String unzipTo, OnUnzipListener lis) {
        return unpackZip(zipPath, unzipTo, false, lis);
    }

    public static long getUncompressedSize(String zipPath) {
        ZipFile zf;
        long uncompressedSize = -1;

        try {
            zf = new ZipFile(zipPath);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        Enumeration<? extends ZipEntry> entries = zf.entries();
        while (entries.hasMoreElements()) {
            uncompressedSize += entries.nextElement().getSize();
        }
        TryCloseUtils.tryClose(zf);
        return uncompressedSize;
    }


    public static boolean unpackZip(String zipPath, String unzipTo, boolean withFolder, OnUnzipListener lis) {
        unzipTo = FileUtils.ensurePathEndsWithSlash(unzipTo);
        boolean ret = false;
        boolean isInterrupted = false;
        String errorMsg = "UNKNOWN";
        List<String> listUnzipFiles = new ArrayList<>();
        if (withFolder) {
            unzipTo = unzipTo + FileUtils.getFileName(zipPath, false) + '/';
        }

        if (lis == null) {
            lis = new OnUnzipListener() {
                @Override
                public void onUnzipSucceed(List<String> unzipFiles) {

                }

                @Override
                public void onUnzipProgress(float percent) {

                }

                @Override
                public void onUnzipFailed(String errorMsg) {

                }

                @Override
                public void onUnzipInterrupt() {

                }

                @Override
                public boolean isUnzipInterrupted() {
                    return false;
                }
            };
        }
        
        ZipFile zf = null;
        InputStream zis = null;
        long zipFileSize = getUncompressedSize(zipPath);
        long uncompressSize = 0;
        try {
            String filePath;
            zf = new ZipFile(zipPath);
            ZipEntry ze;
            byte[] buffer = new byte[1024 * 100];
            int count;
            float oldPercent = 0.0f;
            float percent;

            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                if (lis.isUnzipInterrupted()) {
                    LogWrapper.d(TAG, " unpackZip interrupt");
                    isInterrupted = true;
                    break;
                }

                ze = entries.nextElement();
                filePath = ze.getName();

                String temp = filePath;
                filePath = unzipTo + filePath;
                File file = new File(filePath);

                if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    // Make sure the directory exists
                    file.getParentFile().mkdirs();

                    FileOutputStream fout = new FileOutputStream(filePath);

                    zis = zf.getInputStream(ze);
                    while ((count = zis.read(buffer, 0 , buffer.length)) != -1) {
                        if (lis.isUnzipInterrupted()) {
                            LogWrapper.d(TAG, " unpackZip interrupt");
                            isInterrupted = true;
                            break;
                        }
                        uncompressSize += count;
                        fout.write(buffer, 0, count);

                        percent = uncompressSize * 100.0f / zipFileSize;
                        if (percent < 100.0f && percent - oldPercent > 1.0f) {
                            lis.onUnzipProgress(percent);
                            oldPercent = percent;
                        }
                    }
                    listUnzipFiles.add(temp);
                    TryCloseUtils.tryClose(fout);
                    TryCloseUtils.tryClose(zis);
                    zis = null;
                }
            }
            ret = true;
        } catch (IOException e) {
            errorMsg = e.getMessage();
            e.printStackTrace();
        } finally {
            TryCloseUtils.tryClose(zis);
            TryCloseUtils.tryClose(zf);
        }

        if (lis.isUnzipInterrupted()) {
            isInterrupted = true;
        }

        if (isInterrupted) {
            lis.onUnzipInterrupt();
        } else if (ret) {
            lis.onUnzipProgress(100.0f);
            lis.onUnzipSucceed(listUnzipFiles);
        } else {
            lis.onUnzipFailed(errorMsg);
        }

        return ret;
    }
}
