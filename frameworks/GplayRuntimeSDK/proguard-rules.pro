# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/zhangjunfei/work/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class arch to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
-dontshrink
-dontoptimize
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontusemixedcaseclassnames
-ignorewarnings
-repackageclasses com.skydragon.gplay.runtime
-keepattributes InnerClasses,Signature,*Annotation*
-dontpreverify
-verbose
-dontwarn

##########################################################################
## Android System libraries that don't need to be obfuscated

-keep public class com.google.vending.licensing.ILicensingService
-keep public class com.android.vending.licensing.ILicensingService

-keepclassmembers public class * extends android.view.View {
    void set*(***);
    *** get*();
}

-keep public class * extends android.app.Activity

-keep public class * extends android.app.Application

-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class android.support.v4.app.FragmentManagerMaker

-keepclassmembers enum  * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclasseswithmembers,allowshrinking class * {
    native <methods>;
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

-keep class android.content.**{ *;}
-keep class android.os.**{ *;}

-keep public class com.skydragon.gplay.runtime.RuntimeBridge {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.runtime.bridge.RuntimeBridgeProxy {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.runtime.diffpatch.DiffPatch {
    *** applyPatch(***);
}

-keep public interface com.skydragon.gplay.runtime.bridge.IBridge{*;}
-keep public interface com.skydragon.gplay.runtime.bridge.IBridgeProxy{*;}
-keep public interface com.skydragon.gplay.runtime.bridge.ICallback{*;}
-keep public interface com.skydragon.gplay.runtime.bridge.IEngineRuntimeBridge{*;}
-keep public interface com.skydragon.gplay.runtime.bridge.IEngineRuntimeGetBridge{*;}

-keep public interface com.skydragon.gplay.runtime.callback.IActivityCallback{*;}
-keep public interface com.skydragon.gplay.runtime.callback.OnRuntimeStatusChangedListener{*;}

-keep public interface com.skydragon.gplay.unitsdk.bridge.IUnitSDKBridge{*;}
-keep public interface com.skydragon.gplay.unitsdk.bridge.IUnitSDKBridgeProxy{*;}

-keep public interface com.skydragon.gplay.thirdsdk.IChannelSDKBridge{*;}
-keep public interface com.skydragon.gplay.thirdsdk.IChannelSDKCallback{*;}
-keep public interface com.skydragon.gplay.thirdsdk.IChannelSDKServicePlugin{*;}

-keep public interface com.skydragon.gplay.callback.OnRequestGameInfoListListener{*;}
-keep public interface com.skydragon.gplay.service.IDownloadProxy{*;}
-keep public interface com.skydragon.gplay.service.IRuntimeBridge{*;}
-keep public interface com.skydragon.gplay.service.IRuntimeBridgeProxy{*;}
-keep public interface com.skydragon.gplay.service.IRuntimeCallback{*;}
-keep public interface com.skydragon.gplay.service.IRuntimeProxy{*;}

-keep public class com.skydragon.gplay.runtime.utils.Utils {
    public static int getAPNType();
}

-keep public class com.skydragon.gplay.unitsdk.framework.UnitSDKBridge {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.runtime.RuntimeNativeWrapper {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.unitsdk.framework.UnitSDK {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.unitsdk.framework.UnitSDKImpl {
    public <fields>;
    public <methods>;
}

-keep public class com.skydragon.gplay.unitsdk.framework.util.Util {
    public static java.lang.String logLevel();
}

-keep public class com.skydragon.gplay.runtimeparams.GenericsParams {
    public <fields>;
    public <methods>;
}

-keepclasseswithmembers class * extends com.skydragon.gplay.runtimeparams.GenericsParams{
    public <fields>;
    public <methods>;
}

-keep class * implements com.skydragon.gplay.thirdsdk.IChannelSDKCallback{
    *;
}
