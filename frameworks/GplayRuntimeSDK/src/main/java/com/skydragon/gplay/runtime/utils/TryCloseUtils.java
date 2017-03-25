package com.skydragon.gplay.runtime.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.util.zip.ZipFile;

public final class TryCloseUtils {
    
    /**
     * 关闭输入流
     */
    public static void tryClose( InputStream is ) {
        if( null == is ) return;
        try {
            is.close();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭输出流
     */
    public static void tryClose( OutputStream os ) {
        if( null == os ) return;
        try {
            os.close();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    public static void tryClose( BufferedReader reader ) {
        if( null == reader ) return;
        try {
            reader.close();
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭文件流
     */
    public static void tryClose(FileInputStream fStream) {
        if(null != fStream) {
            try {
                fStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void tryClose(Writer writer) {
        if( null != writer ) {
            try{
                writer.close();
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    }
    /**
     * 关闭ZipFile
     */
    public static void tryClose( ZipFile zipFile ) {
        if( null != zipFile ) {
            try{
                zipFile.close();
            } catch( Exception e ) {
                e.printStackTrace();
            }
        }
    }
    
    public static void tryClose( FileChannel fc ) {
        try{
            if( null != fc ) {
                fc.close();
            }
        } catch( Exception e ) {
            e.printStackTrace();
        }
    }
}
