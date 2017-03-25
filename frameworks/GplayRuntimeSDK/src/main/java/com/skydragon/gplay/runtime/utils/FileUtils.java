package com.skydragon.gplay.runtime.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;

public final class FileUtils {

    private static final String TAG = "FileUtils";

    public static String readStringFromFile(String filePath) {
        return readStringFromFile(new File(filePath));
    }

    public static String readStringFromFile(File file) {
        if (file == null || !file.exists()) {
            LogWrapper.w(TAG, "readStringFromFile failed!");
            return null;
        }

        FileInputStream fs = null;
        try {
            fs = new FileInputStream(file);
            int size = fs.available();
            byte[] buffer = new byte[size];
            fs.read(buffer);
            return new String(buffer, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            TryCloseUtils.tryClose(fs);
        }
        return null;
    }

    public static JSONObject readJsonObjectFromFile(String filePath) {
        return readJsonFile(new File(filePath));
    }

    public static JSONObject readJsonFile(File file) {
        if (file == null || !file.exists()) {
            return null;
        }

        JSONObject ret = null;

        try {
            String fileStr = readStringFromFile(file);
            if (fileStr != null) {
                ret = new JSONObject(fileStr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public static void writeJsonObjectToFile(String filePath, JSONObject jsonObject) {
        try {
            writeStringToFile(filePath, jsonObject.toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static void writeStringToFile(String filePath, String stringContent) {
        if (TextUtils.isEmpty(filePath) || TextUtils.isEmpty(stringContent)) {
            LogWrapper.e(TAG, "writeStringToFile error, file path:" + filePath + ", content:" + stringContent);
            return;
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(filePath);
            writer.write(stringContent);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            TryCloseUtils.tryClose(writer);
        }
    }

    public static boolean isExist(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }

        File file = new File(path);
        return file != null && file.exists();
    }

    public static boolean isDirectory(String path) {
        if (TextUtils.isEmpty(path)) {
            LogWrapper.w(TAG, "isDirectory error, path:" + path);
            return false;
        }

        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public static boolean deleteSubFile(String filePath) {
        File fRoot = new File(filePath);
        if (fRoot.isFile()) return false;
        try {
            for (File f : fRoot.listFiles()) {
                deleteFile(f);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static void deleteFileByDepthFirstSearch(String filePath) {
        if (TextUtils.isEmpty(filePath))
            return;

        File file = new File(filePath);
        if (file == null || !file.exists())
            return;

        Stack<File> fileStack = new Stack<>();
        fileStack.add(file);
        while (!fileStack.empty()) {
            File tmpFile = fileStack.pop();
            if (tmpFile == null || !tmpFile.exists()) {
                continue;
            }

            if (!tmpFile.isDirectory()) {
                tmpFile.delete();
            } else {
                File[] listOfFiles = tmpFile.listFiles();
                if (listOfFiles == null || listOfFiles.length == 0) {
                    tmpFile.delete();
                } else {
                    fileStack.push(tmpFile);
                    for (File aFile : listOfFiles) {
                        fileStack.push(aFile);
                    }
                }
            }
        }
    }

    public static void delete(String filePath) {
        deleteFileByDepthFirstSearch(filePath);
    }

    public static boolean deleteFile(String filePath) {
        boolean ret = false;
        File file = new File(filePath);
        try {
            ret = deleteFile(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;
    }

    public static boolean deleteFile(File file) throws IOException {
        if (file == null) {
            return false;
        }

        if (!file.exists()) {
            LogWrapper.d(TAG, "deleteFile: " + file.getPath() + " doesn't exist!");
            return true;
        }

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (child.isDirectory()) {
                        deleteFile(child);
                    } else {
                        safeDeleteFile(child);
                    }
                }
            }

            return safeDeleteFile(file);
        } else {
            return safeDeleteFile(file);
        }
    }

    private static boolean safeDeleteFile(File file) {
        boolean ret;
        String filePath = file.getAbsolutePath();
        File to = new File(filePath + System.currentTimeMillis());
        ret = file.renameTo(to);

        if (ret) {
            ret = to.delete();
        }

        if (ret) {
            LogWrapper.d(TAG, "safeDeleteFile (" + filePath + ") succeed");
        } else {
            LogWrapper.e(TAG, "safeDeleteFile (" + filePath + ") failed");
        }

        return ret;
    }

    public static boolean ensureDirExists(String dirPath) {
        do {
            if (TextUtils.isEmpty(dirPath)) {
                break;
            }

            File dir = new File(dirPath);
            if (dir == null) {
                break;
            }

            if (!dir.mkdirs() && !dir.isDirectory()) {
                LogWrapper.e(TAG, "Create (" + dirPath + ") failed!");
                break;
            }

            return true;
        } while (false);

        LogWrapper.e(TAG, "ensureDirExists failed! dirPath:" + dirPath);
        return false;
    }

    public static void createNoMediaFile() {
        try {
            new File(FileConstants.getNoMediaFilePath()).createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 重命名文件或文件夹
     *
     * @param resFilePath 源文件路径
     * @param newFilePath 重命名
     * @return 操作成功标识
     */
    public static boolean renameFile(String resFilePath, String newFilePath) {
        boolean ret = false;
        do {
            if (TextUtils.isEmpty(resFilePath) || TextUtils.isEmpty(newFilePath)) {
                break;
            }

            File resFile = new File(resFilePath);
            if (resFile == null) {
                break;
            }

            File newFile = new File(newFilePath);
            ret = resFile.renameTo(newFile);
        } while (false);

        if (!ret) {
            LogWrapper.w(TAG, "renameFile failed! resFile:" + resFilePath + ", new file path:" + newFilePath);
        }
        return ret;
    }

    public static boolean renameFile(File resFile, String newFilePath) {
        if (resFile == null || TextUtils.isEmpty(newFilePath)) {
            LogWrapper.w(TAG, "renameFile failed! resFile:" + resFile + ", new file path:" + newFilePath);
            return false;
        }

        File newFile = new File(newFilePath);
        return resFile.renameTo(newFile);
    }

    /**
     * 拷贝文件/目录到指定路径
     */
    public static boolean copyFile(String srcPath, String dstPath) {
        if (TextUtils.isEmpty(srcPath) || TextUtils.isEmpty(dstPath))
            return false;

        srcPath = removeLastSlash(srcPath);
        dstPath = removeLastSlash(dstPath);

        File fileSrc = new File(srcPath);
        if (fileSrc == null || !fileSrc.exists()) {
            LogWrapper.w(TAG, "copyFile failed!" + srcPath + " not exist");
            return false;
        }

        File fileDst = new File(dstPath);
        if (fileSrc.isDirectory()) {

            // if directory not exists, create it
            if (!fileDst.exists()) {
                fileDst.mkdir();
                LogWrapper.d(TAG, "Directory copied from " + srcPath + "  to " + fileDst);
            }

            // list all the directory contents
            String files[] = fileSrc.list();

            for (String file : files) {
                // recursive copy
                if (!copyFile(srcPath + File.separator + file, dstPath + File.separator + file)) {
                    return false;
                }
            }

        } else {
            // if file, then copy it
            FileChannel inChannel;
            FileChannel outChannel;
            FileInputStream inStream = null;
            FileOutputStream outStream = null;
            try {
                inStream = new FileInputStream(fileSrc);
                outStream = new FileOutputStream(fileDst);
                inChannel = inStream.getChannel();
                outChannel = outStream.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            } finally {
                TryCloseUtils.tryClose(inStream);
                TryCloseUtils.tryClose(outStream);
            }
        }

        return true;
    }

    public static String getDataDir(Context context) {
        String ret = null;
        PackageManager pm = context.getPackageManager();

        try {
            ret = pm.getApplicationInfo(context.getPackageName(), 0).dataDir + File.separator;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return ensurePathEndsWithSlash(ret);
    }

    public static String getFileName(String path, boolean withSuffix) {
        int nPos = path.lastIndexOf('/');
        if (nPos >= 0) {
            path = path.substring(nPos + 1);
        }

        if (!withSuffix) {
            int suffixPos = path.lastIndexOf('.');
            if (suffixPos >= 0) {
                path = path.substring(0, suffixPos);
            }
        }

        return path;
    }

    public static String ensurePathEndsWithSlash(String path) {
        if (TextUtils.isEmpty(path))
            return "";

        if (!path.endsWith(File.separator)) {
            path = path + File.separator;
        }
        return path;
    }

    public static String removeLastSlash(String path) {
        String ret = path;
        while (ret.endsWith(File.separator)) {
            ret = ret.substring(0, ret.length() - 1);
        }
        return ret;
    }

    private static String byteArrayToHex(byte[] byteArray) {
        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] resultCharArray = new char[byteArray.length * 2];

        int index = 0;

        for (byte b : byteArray) {
            resultCharArray[index++] = hexDigits[b >>> 4 & 0xf];
            resultCharArray[index++] = hexDigits[b & 0xf];
        }

        return new String(resultCharArray);
    }

    public static String getFileMD5(String filePath) {
        FileInputStream fileInputStream = null;

        try {
            File file = new File(filePath);

            if (!file.exists()) {
                LogWrapper.w(TAG, "getFileMD5 error: file not exist! file path:" + filePath);
                return null;
            }

            fileInputStream = new FileInputStream(file);

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024 * 10];

            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }

            return byteArrayToHex(md5.digest());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            TryCloseUtils.tryClose(fileInputStream);
        }
        return null;
    }

    public static boolean isFileModifiedByCompareMD5(String path, String md5) {
        if (TextUtils.isEmpty(md5)) {
            LogWrapper.e(TAG, "isFileModifiedByCompareMD5 error, md5:" + md5);
            return true;
        }

        if (TextUtils.isEmpty(path)) {
            LogWrapper.e(TAG, "isFileModifiedByCompareMD5 error, path:" + path);
            return true;
        }

        String fileMD5 = getFileMD5(path);
        LogWrapper.d(TAG, "file (" + path + ") 's MD5=" + fileMD5 + ",Last file MD5=" + md5);
        if (fileMD5 == null) {
            return true;
        }

        boolean ret = fileMD5.equalsIgnoreCase(md5);
        if (!ret) {
            LogWrapper.d(TAG, path + " is modified, file MD5=" + fileMD5 + ", to matched MD5=" + md5);
        }
        return !ret;
    }

    /**
     * 移动文件
     *
     * @param srcFileName 源文件完整路径
     * @param destDirName 目的目录完整路径
     * @return 文件移动成功返回true，否则返回false
     */
    public static boolean moveFile(String srcFileName, String destDirName) {

        File srcFile = new File(srcFileName);
        if (!srcFile.exists() || !srcFile.isFile())
            return false;

        File destDir = new File(destDirName);
        if (!destDir.exists())
            destDir.mkdirs();

        return srcFile.renameTo(new File(destDirName + File.separator + srcFile.getName()));
    }

    /**
     * 移动目录
     *
     * @param srcDirName  源目录完整路径
     * @param destDirName 目的目录完整路径
     * @return 目录移动成功返回true，否则返回false
     */
    public static boolean moveDirectory(String srcDirName, String destDirName) {

        File srcDir = new File(srcDirName);
        if (!srcDir.exists() || !srcDir.isDirectory())
            return false;

        File destDir = new File(destDirName);
        if (!destDir.exists())
            destDir.mkdirs();

        /**
         * 如果是文件则移动，否则递归移动文件夹。删除最终的空源文件夹
         * 注意移动文件夹时保持文件夹的树状结构
         */
        File[] sourceFiles = srcDir.listFiles();
        for (File sourceFile : sourceFiles) {
            if (sourceFile.isFile())
                moveFile(sourceFile.getAbsolutePath(), destDir.getAbsolutePath());
            else if (sourceFile.isDirectory())
                moveDirectory(sourceFile.getAbsolutePath(),
                        destDir.getAbsolutePath() + File.separator + sourceFile.getName());
        }
        return srcDir.delete();
    }

    private static class FileComparator implements Comparator<File> {
        public int compare(File file1, File file2) {
            if (file1.lastModified() > file2.lastModified()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    private static final FileFilter sdkFileFilter = new FileFilter() {
        public boolean accept(File file) {
            String tmp = file.getName().toLowerCase();
            return tmp.endsWith(".jar") || tmp.endsWith(".dex");
        }
    };

    private static ArrayList<File> getAllEngineJavaLibraryList(String engineJavaPath, FileFilter filter) {
        File parentFile = new File(engineJavaPath);
        File[] files = parentFile.listFiles(filter);
        ArrayList<File> fileList = new ArrayList<>();
        if (files != null) {
            Collections.addAll(fileList, files);
            Collections.sort(fileList, new FileComparator());
        }
        return fileList;
    }

    public static String getLatestEngineJavaLibraryPath(String engineJavaPath) {
        ArrayList<File> fileList = getAllEngineJavaLibraryList(engineJavaPath, sdkFileFilter);
        String latestJar = "";
        if (fileList.size() > 0) {
            latestJar = fileList.get(0).getAbsolutePath();
        }
        return latestJar;
    }

    public static void removeUnusedEngineJavaLibrary(String sdkPath) {
        ArrayList<File> fileList = getAllEngineJavaLibraryList(sdkPath, sdkFileFilter);
        if (fileList.size() > 1) {
            for (int i = 1; i < fileList.size(); i++) {
                fileList.get(i).delete();
            }
        }
    }

}
