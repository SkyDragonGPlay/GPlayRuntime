package com.skydragon.gplay.runtime;

import com.skydragon.gplay.runtime.bridge.IEngineRuntimeBridge;
import com.skydragon.gplay.runtime.bridge.IEngineRuntimeGetBridge;
import com.skydragon.gplay.runtime.utils.FileUtils;
import com.skydragon.gplay.runtime.utils.LogWrapper;
import com.skydragon.gplay.runtime.utils.Utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.HashMap;

import dalvik.system.DexClassLoader;

public final class EngineLibraryLoader {
    
    private static final String TAG = "EngineLibraryLoader";
    private static final boolean DEBUG = false;
    private static final String RUNTIME_GET_BRIDGE_CLASS_NAME = "com.skydragon.gplay.runtime.bridge.RuntimeGetBridge";
    
    private static HashMap<String, IEngineRuntimeBridge> sBridgeMap = new HashMap<>(5);
    
    public static IEngineRuntimeBridge getRuntimeBridge(String jarPath, String extendJar, String soPaths, String optimizedDexDir) {
        
        if (DEBUG) {
            try {
                Class<?> cls = Class.forName(RUNTIME_GET_BRIDGE_CLASS_NAME);
                IEngineRuntimeGetBridge iRuntimeGetBridge = (IEngineRuntimeGetBridge)cls.newInstance();
                return iRuntimeGetBridge.getRuntimeBridge();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } 
        }
        else {
            IEngineRuntimeBridge ret = sBridgeMap.get(jarPath);

            if (ret != null) {
                LogWrapper.d(TAG, "Return IBridge in cache! jarPath: " + jarPath);
                return ret;
            }

            if (!FileUtils.isExist(jarPath)) {
                LogWrapper.e(TAG, "Oops, jarPath (" + jarPath + ") doesn't exist!");
                return null;
            }

            if (optimizedDexDir == null) {
                LogWrapper.e(TAG, "optimizedDexDir is null!");
                return null;
            }

            try {
                String sLoadJars = jarPath;
                if(!Utils.isEmpty(extendJar)) {
                    sLoadJars = sLoadJars + File.pathSeparator + extendJar;
                }
                ClassLoader parent = EngineLibraryLoader.class.getClassLoader();
                LogWrapper.d(TAG, "Parent class loader: " + parent);
                LogWrapper.d(TAG, "loadRuntimeJarLibrary: " + sLoadJars + ", runtimelib: " + optimizedDexDir);

                DexClassLoader clsLoader = new DexClassLoader(sLoadJars, optimizedDexDir, soPaths, parent);

                Class<?> cls = clsLoader.loadClass(RUNTIME_GET_BRIDGE_CLASS_NAME);
                Constructor<?> ctor = cls.getDeclaredConstructor();
                ctor.setAccessible(true);
                IEngineRuntimeGetBridge bridgeGet = (IEngineRuntimeGetBridge) ctor.newInstance();
                ret = bridgeGet.getRuntimeBridge();
                sBridgeMap.put(jarPath, ret);

            } catch (Exception e) {
                LogWrapper.e(TAG, e);
            }
            return ret;
        }
        return null;
    }
}
