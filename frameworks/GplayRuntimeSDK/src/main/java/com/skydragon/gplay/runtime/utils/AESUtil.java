package com.skydragon.gplay.runtime.utils;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class AESUtil {
    private static final String TAG = "AESUtil";

    public static String encode(String encryptString, String encryptKey) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] bytes = cipher.doFinal(encryptString.getBytes("UTF-8"));
        return new String(Base64.encode(bytes, Base64.DEFAULT), "UTF-8");
    }

    public static String decode(byte[] decryptBytes, String decryptKey) throws Exception {
        byte[] data = Base64.decode(decryptBytes, Base64.DEFAULT);
        SecretKeySpec secretKeySpec = new SecretKeySpec(decryptKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        return new String(cipher.doFinal(data), "UTF-8");
    }

    public static String decode(String encryData, String decryptKey) throws Exception {
        byte[] decryptBytes = encryData.getBytes("UTF-8");
        byte[] data = Base64.decode(decryptBytes, Base64.DEFAULT);
        SecretKeySpec secretKeySpec = new SecretKeySpec(decryptKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        return new String(cipher.doFinal(data), "UTF-8");
    }
}
