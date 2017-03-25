package com.skydragon.gplay.runtime.entity.runtime;

import com.skydragon.gplay.runtime.entity.resource.ResourceTemplate;
import com.skydragon.gplay.runtime.entity.resource.ResourcesZipTemplate;

import org.json.JSONObject;


public final class RuntimeEngineSupportInfo {

    private String _engine;
    private String _engineVersion;
    private RuntimeShareLibraryArchInfo _shareLibraryArchInfo;
    private ResourceTemplate _javaLibraryInfo;
    private RuntimeCommonResInfo _commonResource;
    private ResourcesZipTemplate _shareLibraries;
    private ResourcesZipTemplate _dynamicLinkLibraries;

    public static RuntimeEngineSupportInfo from(JSONObject jsonObject) {
        RuntimeEngineSupportInfo supportInfo = new RuntimeEngineSupportInfo();
        supportInfo._engine = jsonObject.optString("name");
        supportInfo._engineVersion = jsonObject.optString("version_name");
        supportInfo._shareLibraryArchInfo = RuntimeShareLibraryArchInfo.fromJson(jsonObject.optJSONObject("vm"));
        supportInfo._javaLibraryInfo = ResourceTemplate.fromJson(jsonObject.optJSONObject("runtime"));
        supportInfo._commonResource = RuntimeCommonResInfo.fromJson(jsonObject.optJSONObject("common_resource"));

        JSONObject libraries = jsonObject.optJSONObject("shared_library");
        if (libraries != null) {
            supportInfo._shareLibraries = ResourcesZipTemplate.fromJson(libraries);
        }

        JSONObject dlls = jsonObject.optJSONObject("dll");
        if (dlls != null) {
            supportInfo._dynamicLinkLibraries = ResourcesZipTemplate.fromJson(dlls);
        }

        return supportInfo;
    }

    public String getEngine() {
        return _engine;
    }

    public String getEngineVersion() {
        return _engineVersion;
    }

    public RuntimeCommonResInfo getCommonResource() {
        return _commonResource;
    }

    public ResourceTemplate getJavaLibraryInfo() {
        return _javaLibraryInfo;
    }

    public ResourcesZipTemplate getShareLibraries() {
        return _shareLibraries;
    }

    public ResourcesZipTemplate getDynamicLinkLibraries() {
        return _dynamicLinkLibraries;
    }

    public RuntimeShareLibraryArchInfo getShareLibraryArchInfo() {
        return _shareLibraryArchInfo;
    }
}
