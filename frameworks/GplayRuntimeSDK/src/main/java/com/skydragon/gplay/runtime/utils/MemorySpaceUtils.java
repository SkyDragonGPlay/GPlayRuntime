package com.skydragon.gplay.runtime.utils;


import android.os.Build;
import android.os.StatFs;

public final class MemorySpaceUtils {
    /**
     * 计算剩余空间
     * @param path 目录
     * @return 剩余空间
     */
    private static long getAvailableSize(String path) {
        StatFs fileStats = new StatFs(path);
        fileStats.restat(path);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            return fileStats.getAvailableBlocksLong() * fileStats.getBlockSizeLong();
        else
            return (long) fileStats.getAvailableBlocks() * fileStats.getBlockSize(); // 注意与fileStats.getFreeBlocks()的区别
    }

    /**
     * 是否有足够的空间
     * @param fileLength 文件大小
     */
    public static boolean hasEnoughSDAvailableSize(String path, long fileLength) {
        return getAvailableSize(path) > fileLength;
    }
}
