package com.skydragon.gplay.runtime.diffpatch;

import com.skydragon.gplay.runtime.utils.LogWrapper;

public final class DiffPatch {
    /**
     * 增量更新
     * @param oldFilePath 旧版本文件
     * @param outNewFilePath 更新之后输出的文件
     * @param patchFilePath 增量更新包文件
     */
    public static void applyPatch( String oldFilePath, String outNewFilePath, String patchFilePath ) {
        final long timeBegin = System.currentTimeMillis();
        jniApplyPatch(oldFilePath, outNewFilePath, patchFilePath);
        final long timeEnd = System.currentTimeMillis();
        LogWrapper.i("DiffPatch", "BPatch runtime:" + (timeEnd - timeBegin) + "ms");
    }

    public static void applyPatchV2( String oldFilePath, String outNewFilePath, String patchFilePath ) {
        final long timeBegin = System.currentTimeMillis();
        jniApplyPatchV2(oldFilePath, outNewFilePath, patchFilePath);
        final long timeEnd = System.currentTimeMillis();
        LogWrapper.i("DiffPatch", "HPatch runtime:" + (timeEnd - timeBegin) + "ms");
    }
    
    /**
     * 增量更新
     * @param oldPath 旧文件路径
     * @param newPath 新文件路径
     * @param content patch内容
     * @param len     patch内容长度
     */
    public static int applyPatch2( String oldPath, String newPath, byte[] content, int len ) {
        return jniApplyPatch2(oldPath, newPath, content, len );
    }

    native static void jniApplyPatch( String oldFilePath, String outNewFilePath, String patchFilePath );
    native static int jniApplyPatch2( String oldPath, String newPath, byte[] content, int len );
    public native static int jniApplyPatchV2( String oldFilePath, String outNewFilePath, String patchFilePath );
}
