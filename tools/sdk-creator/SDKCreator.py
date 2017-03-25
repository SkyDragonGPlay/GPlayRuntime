#!/usr/bin/env python
# coding=utf-8

import sys
import os
import shutil

sys.path.append(os.path.join(os.path.dirname(os.path.realpath(__file__)), '..', 'common'))
import common

_creator_list = []


class GplaySDKCreator(object):
    def __init__(self, opts):
        self.TAG = "GplaySDKCreator"
        common.LOGI(self.TAG, "opts: " + str(opts))
        self.workspace = opts["workspace"]
        self.script_path = opts["script_path"]
        self.proguard = not opts["dont_proguard"]
        self.target = opts["target"]
        if not self.target:
            self.target = "online"

        self.build_replacer_list = []

        self.proj_gplaysdk_dir = os.path.join(self.script_path, "../../../GplaySDK/frameworks/GplaySDK")
        self.proj_gplayruntimebridge_dir = os.path.join(self.script_path, "../../../GplaySDK/frameworks/GplayRuntimeBridge")
        self.proj_gplayunitsdkplugin_dir = os.path.join(self.script_path, "../../../GplaySDK/frameworks/GplayUnitSDKPlugin")

        common.LOGI(self.TAG, "GplaySDK proj_gplaysdk_dir: " + os.path.abspath(self.proj_gplaysdk_dir))

        self.version = self._get_version()
        common.LOGI(self.TAG, "GplaySDK version: " + self.version)

        self.proj_gplayunitsdkplugin_src_dir = os.path.join(self.proj_gplayunitsdkplugin_dir, "src", "main", "java")
        self.proj_gplayruntimebridge_src_dir = os.path.join(self.proj_gplayruntimebridge_dir, "src", "main", "java")
        self.proj_gplaysdk_src_dir = os.path.join(self.proj_gplaysdk_dir, "src", "main", "java")

        self.proj_gplayunitsdkplugin_build_dir = os.path.join(self.proj_gplayunitsdkplugin_dir, "build")
        self.proj_gplayruntimebridge_build_dir = os.path.join(self.proj_gplayruntimebridge_dir, "build")
        self.proj_gplaysdk_build_dir = os.path.join(self.proj_gplaysdk_dir, "build")

        if not opts["out"]:
            self.out_dir = os.path.join(self.workspace, "out-gplaysdk")
            common.recreate_folder(self.out_dir)
            self.debug_directory = os.path.join(self.workspace, 'debug')
        else:
            self.out_dir = os.path.join(opts["out"], self._get_version_name().replace('"', ""))
            self.debug_directory = os.path.join(self.out_dir, 'debug')
        self.sdk_jar_no_dex_obfuscated_path = os.path.join(self.out_dir, "libgplaysdk-" + self._get_version_name() + ".jar")

    def _get_version(self):
        return common.find_string_from_file(
            os.path.join(self.proj_gplaysdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "Gplay.java"),
            r"private\s+static\s+final\s+int\s+VERSION_CODE\s*=\s*(.*);"
        )

    def _replace_product_mode(self):
        replacer = common.FileStringReplacer(os.path.join(self.proj_gplaysdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "Gplay.java"))
        self.build_replacer_list.append(replacer)
        reg = r"private\s+static\s+String\s+PRODUCT_MODE\s*=\s*(.*);"

        default_product_mode = "ONLINE_MODE"
        if self.target == "online":
            default_product_mode = "ONLINE_MODE"
        elif self.target == "sandbox":
            default_product_mode = "SANDBOX_MODE"
        elif self.target == "DEVELOP":
            default_product_mode = "DEVELOP_MODE"
        else:
            default_product_mode = "ONLINE_MODE"

        newStr = "private static String PRODUCT_MODE = " + default_product_mode + ";"
        replacer.replace_regexp(reg, newStr)
        replacer.flush()

    def _set_loader_mode_is_dynamic(self):
        replacer = common.FileStringReplacer(os.path.join(self.proj_gplaysdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "loader", "DexLoader.java"))
        self.build_replacer_list.append(replacer)
        reg = r"private\s+static\s+final\s+boolean\s+DEBUG\s*=\s*(.*);"
        newStr = "private static final boolean DEBUG = false;"
        replacer.replace_regexp(reg, newStr)
        replacer.flush()

    def _get_version_name(self):
        return common.find_string_from_file(
            os.path.join(self.proj_gplaysdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "Gplay.java"),
            r"private\s+static\s+final\s+String\s+VERSION\s*=\s*(.*);"
        )

    def _generate_gplaysdk_jar_file(self):
        common.ensure_folder_exists(self.debug_directory)

        proguard_file_path = "None"
        mapping_file_path = "None"
        if self.proguard:
            proguard_file_path = os.path.join(self.script_path, "proguard-gplaysdk.txt")
            mapping_file_path = os.path.join(self.debug_directory, "mapping-gplaysdk-" + self.version + ".txt")

        common.run_command(
            "python " + os.path.join(self.script_path, "../jar-maker/JarMaker.py") +
            " -s " + self.proj_gplayunitsdkplugin_src_dir +
            " -s " + self.proj_gplayruntimebridge_src_dir +
            " -s " + self.proj_gplaysdk_src_dir +
            " -o " + self.out_dir +
            " -f " + os.path.split(self.sdk_jar_no_dex_obfuscated_path)[-1] +
            " --ref-lib " + os.path.join(self.script_path, "..", "common", "lib", "android.jar") +
            " --ref-lib " + os.path.join(self.script_path, "..", "common", "lib", "annotations.jar") +
            " -p " + proguard_file_path +
            " -m " + mapping_file_path
        )

    def run(self):
        common.LOGI(self.TAG, "RUNNING ...")
        self._clean()
        self._replace_product_mode()
        self._set_loader_mode_is_dynamic()
        self._generate_gplaysdk_jar_file()
        self._clean()

        for replacer in self.build_replacer_list:
            replacer.revert()

        common.LOGI(self.TAG, "DONE!")

    def _clean(self):
        common.LOGI(self.TAG, "Cleaning ...")

        common.safe_remove_file(self.proj_gplayunitsdkplugin_build_dir)
        common.safe_remove_file(self.proj_gplayruntimebridge_build_dir)
        common.safe_remove_file(self.proj_gplaysdk_build_dir)

class RuntimeSDKCreator(object):
    def __init__(self, opts):
        self.TAG = "RuntimeSDKCreator"
        common.LOGI(self.TAG, "opts: " + str(opts))
        self.build_sdk = opts["build_sdk"]
        self.workspace = opts["workspace"]
        self.script_path = opts["script_path"]
        self.target = opts["target"]
        if not self.target:
            self.target = "online"

        self.build_replacer_list = []

        self.proj_runtimesdk_dir = os.path.join(self.script_path, "../../frameworks/GplayRuntimeSDK")
        self.proj_gplayenginebridge_dir = os.path.join(self.script_path, "../../engines/GplayEngineBridge")
        self.proj_gplayunitsdk_dir = os.path.join(self.script_path, "../../../GPlayUnitSDK/GplayUnitSDK")
        self.proj_gplayunitsdkbridge_dir = os.path.join(self.script_path, "../../../GPlayUnitSDK/GplayUnitSDKBridge")
        self.proj_gplayruntimeparams_dir = os.path.join(self.script_path, "../../frameworks/GplayRuntimeParams")
        self.proj_network_library_dir = os.path.join(self.script_path, "../../../GplaySDK/library/NoHttp")
        self.proj_gplayruntimebridge_dir = os.path.join(self.script_path, "../../../GplaySDK/frameworks/GplayRuntimeBridge")
        self.proj_gplayunitsdkplugin_dir = os.path.join(self.script_path, "../../../GplaySDK/frameworks/GplayUnitSDKPlugin")

        self.proj_gplayruntimebridge_src_dir = os.path.join(self.proj_gplayruntimebridge_dir, "src", "main", "java")
        self.proj_gplayunitsdkplugin_src_dir = os.path.join(self.proj_gplayunitsdkplugin_dir, "src", "main", "java")
        self.proj_runtimesdk_src_dir = os.path.join(self.proj_runtimesdk_dir, "src", "main", "java")
        self.proj_gplayenginebridge_src_dir = os.path.join(self.proj_gplayenginebridge_dir, "src", "main", "java")
        self.proj_gplayunitsdk_src_dir = os.path.join(self.proj_gplayunitsdk_dir,"src", "main", "java")
        self.proj_gplayunitsdkbridge_src_dir = os.path.join(self.proj_gplayunitsdkbridge_dir, "src", "main", "java")
        self.proj_gplayruntimeparams_src_dir = os.path.join(self.proj_gplayruntimeparams_dir, "src", "main", "java")
        self.proj_network_library_src_dir = os.path.join(self.proj_network_library_dir, "src", "main", "java")

        self.proj_gplayruntimebridge_build_dir = os.path.join(self.proj_gplayruntimebridge_dir, "build")
        self.proj_gplayunitsdkplugin_build_dir = os.path.join(self.proj_gplayunitsdkplugin_dir, "build")
        self.proj_gplayenginebridge_build_dir = os.path.join(self.proj_gplayenginebridge_dir, "build")
        self.proj_runtimesdk_build_dir = os.path.join(self.proj_runtimesdk_dir, "build")
        self.proj_gplayenginebridge_build_dir = os.path.join(self.proj_gplayenginebridge_dir, "build")
        self.proj_gplayunitsdk_build_dir = os.path.join(self.proj_gplayunitsdk_dir, "build")
        self.proj_gplayunitsdkbridge_build_dir = os.path.join(self.proj_gplayunitsdkbridge_dir, "build")
        self.proj_gplayruntimeparams_build_dir = os.path.join(self.proj_gplayruntimeparams_dir, "build")
        self.proj_network_library_build_dir = os.path.join(self.proj_network_library_dir, "build")

        self.version = self._get_version()
        common.LOGI(self.TAG, "Runtime SDK versionCode: " + self.version)

        if not opts["out"]:
            self.out_dir = os.path.join(self.workspace, 'out-runtime-sdk')
            self.out_dir = os.path.join(self.out_dir, self._get_version_name().replace('"', ""))
            common.recreate_folder(self.out_dir)
            self.debug_directory = os.path.join(self.workspace, 'debug')
        else:
            self.out_dir = os.path.join(opts["out"], self._get_version_name().replace('"', ""))
            self.debug_directory = os.path.join(self.out_dir, 'debug')
        self.out_dir_temp = os.path.join(self.workspace, "out-runtime-sdk-temp")
        common.recreate_folder(self.out_dir_temp)

        self.sdk_jar_no_dex_obfuscated_path = os.path.join(self.out_dir_temp, "libruntime-sdk-no-dex-obfuscated.jar")
        self.sdk_jar_dex_path = os.path.join(self.out_dir_temp, "libruntime-sdk-dex.jar")
        self.output_class_path = os.path.join(self.out_dir_temp, "classes")
        self.proguard = not opts["dont_proguard"]

    def _get_version(self):
        # Get the version of current SDK
        return common.find_string_from_file(
            os.path.join(self.proj_runtimesdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "runtime", "RuntimeStub.java"),
            r"private\s+static\s+final\s+int\s+VERSION_CODE\s*=\s*(.*)\s*;"
        )

    def _get_version_name(self):
        return common.find_string_from_file(
            os.path.join(self.proj_runtimesdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "runtime", "RuntimeStub.java"),
            r"public\s+static\s+final\s+String\s+VERSION\s*=\s*(.*);"
        )

    def _clean(self):
        common.LOGI(self.TAG, "Cleaning ...")

        file_to_remove_list = [
            self.out_dir_temp,
            self.proj_gplayruntimebridge_build_dir,
            self.proj_gplayunitsdkplugin_build_dir,
            self.proj_runtimesdk_build_dir,
            self.proj_network_library_build_dir,
            self.proj_gplayunitsdk_build_dir,
            self.proj_gplayenginebridge_build_dir,
            self.proj_gplayruntimeparams_build_dir,
            self.proj_gplayunitsdkbridge_build_dir
        ]

        common.safe_remove_files(file_to_remove_list)

    def _set_loader_mode_is_dynamic(self):
        replacer = common.FileStringReplacer(os.path.join(self.proj_runtimesdk_src_dir, "com", "skydragon", "gplay", "runtime", "EngineLibraryLoader.java"))
        self.build_replacer_list.append(replacer)
        reg = r"private\s+static\s+final\s+boolean\s+DEBUG\s*=\s*(.*);"
        newstr = "private static final boolean DEBUG = false;"
        replacer.replace_regexp(reg, newstr)
        replacer.flush()

    def _set_runtime_log_enabled(self, logEnabled):
        replacer = common.FileStringReplacer(os.path.join(self.proj_runtimesdk_dir, "src", "main", "java", "com", "skydragon", "gplay", "runtime", "utils", "LogWrapper.java"))
        self.build_replacer_list.append(replacer)
        reg = r"private\s+static\s+boolean\s+SHOW_LOG\s*=\s*(.*);"
        newstr = None
        if logEnabled:
            newstr = "private static boolean SHOW_LOG = true;"
        else:
            newstr = "private static boolean SHOW_LOG = false;"

        replacer.replace_regexp(reg, newstr)
        replacer.flush()

        self.logEnabled = logEnabled
        ######################################################################################
        # Output file name
        versionName = self._get_version_name().replace('"', "")
        if self.logEnabled:
            self.sdk_file_for_runtimesdk_no_dex = os.path.join(self.out_dir, "libruntime-not-dex-with-log-" + versionName + ".jar")
            self.sdk_file_for_runtimesdk = os.path.join(self.out_dir, "libruntime-with-log-" + versionName + ".jar")
        else:
            self.sdk_file_for_runtimesdk_no_dex = os.path.join(self.out_dir, "libruntime-not-dex-without-log-" + versionName + ".jar")
            self.sdk_file_for_runtimesdk = os.path.join(self.out_dir, "libruntime-without-log-" + versionName + ".jar")
        ######################################################################################

    def _generate_cut_jar(self, jar_path, config, out_dir):
        '''Invoke JarCutter tool to customize jar file'''
        # Ensure the our dir is empty
        common.recreate_folder(out_dir)

        # Run the JarCutter
        common.run_command(
            "python " + os.path.join(self.script_path, "../jar-cutter/JarCutter.py") +
            " -c " + config +
            " -s " + jar_path +
            " -o " + out_dir,
            error_message="cutting jar file (%s) failed!" % jar_path
        )

    def _generate_runtimesdk_jar_file(self, force_dont_proguard=False):
        common.LOGI(self.TAG, "Generating runtime sdk jar file ..., force_dont_proguard:" + str(force_dont_proguard))

        common.ensure_folder_exists(self.debug_directory)

        proguard_file_path = "None"
        mapping_file_path = "None"
        if not force_dont_proguard and self.proguard:
            proguard_file_path = os.path.join(self.script_path, "proguard-runtimesdk.txt")
            mapping_file_path = os.path.join(self.debug_directory, "mapping-libruntime-" + self.version + ".txt")

        common.run_command(
            "python " + os.path.join(self.script_path, "../jar-maker/JarMaker.py") +
            " -s " + self.proj_gplayruntimebridge_src_dir +
            " -s " + self.proj_gplayunitsdkplugin_src_dir +
            " -s " + self.proj_gplayunitsdkbridge_src_dir +
            " -s " + self.proj_gplayenginebridge_src_dir +
            " -s " + self.proj_gplayruntimeparams_src_dir +
            " -s " + self.proj_gplayunitsdk_src_dir +
            " -s " + self.proj_runtimesdk_src_dir +
            " -s " + self.proj_network_library_src_dir +
            " -o " + self.out_dir_temp +
            " -f " + os.path.split(self.sdk_jar_no_dex_obfuscated_path)[-1] +
            " --ref-lib " + os.path.join(self.script_path, "..", "common", "lib", "android.jar") +
            " -p " + proguard_file_path +
            " -m " + mapping_file_path
        )

    def _generate_cut_jar_for_runtime_sdk_dex(self):
        common.LOGI(self.TAG, "Generating jar file for runtime sdk ...")
        out_jar_file_name = common.get_file_name(self.sdk_jar_no_dex_obfuscated_path)
        jar_cutter_out_dir = os.path.join(self.out_dir_temp, "jar-cutter-out-for-runtime-dex")
        jar_cutter_config_path = os.path.join(self.script_path, "jar-cutter-for-runtimesdk-dex.json")
        self._generate_cut_jar(self.sdk_jar_no_dex_obfuscated_path, jar_cutter_config_path, jar_cutter_out_dir)
        cut_jar_file_path = os.path.join(jar_cutter_out_dir, out_jar_file_name)
        common.generate_dex_jar(cut_jar_file_path, self.sdk_jar_dex_path)
        # Copy dex sdk file to the output directory
        shutil.copy(self.sdk_jar_dex_path, self.sdk_file_for_runtimesdk)
        shutil.copy(self.sdk_jar_no_dex_obfuscated_path, self.sdk_file_for_runtimesdk_no_dex)
        # Remove the dex file since it may be generated with the same name
        # in next step (I means generating cut jar for tencent)
        common.safe_remove_file(self.sdk_jar_dex_path)

    def revert(self):
        for replacer in self.build_replacer_list:
            replacer.revert()
        return

    def run(self):
        common.LOGI(self.TAG, "RUNNING ...")
        # Clean temp files before generate classes files
        self._clean()

        if self.build_sdk == "all" or self.build_sdk == "runtime":
            # Generate runtime for normal channel
            self._set_loader_mode_is_dynamic()
            self._set_runtime_log_enabled(False)
            self._generate_runtimesdk_jar_file()
            self._generate_cut_jar_for_runtime_sdk_dex()
            self.revert()
            
            self._set_loader_mode_is_dynamic()
            self._set_runtime_log_enabled(True)
            self._generate_runtimesdk_jar_file()
            self._generate_cut_jar_for_runtime_sdk_dex()
            self.revert()
            
        # clean after build
        self._clean()

        common.LOGI(self.TAG, "DONE!")

class CocosRuntimeCreator(object):
    def __init__(self, opts):
        self.TAG = "CocosRuntimeCreator"
        common.LOGI(self.TAG, "opts: " + str(opts))
        self.workspace = opts["workspace"]
        self.script_path = opts["script_path"]

        if opts["engineVersion"] == "V2":
            self.cocosEnginePath = "../../../GPlayCPSDKCore/engines/CocosEngineV2"
        else:
            self.cocosEnginePath = "../../../GPlayCPSDKCore/engines/CocosEngine"

        self.proj_cocosruntime_dir = os.path.join(self.script_path, self.cocosEnginePath)
        self.proj_gplayenginebridge_dir = os.path.join(self.script_path, "../../engines/GplayEngineBridge")

        common.LOGI(self.TAG, "cocos engine proj_cocosruntime_dir: " + os.path.abspath(self.proj_cocosruntime_dir))

        self.proj_cocosruntime_src_dir = os.path.join(self.proj_cocosruntime_dir, "src", "main", "java")
        self.proj_gplayenginebridge_src_dir = os.path.join(self.proj_gplayenginebridge_dir, "src", "main", "java")

        self.proj_cocosruntime_build_dir = os.path.join(self.proj_cocosruntime_dir, "build")
        self.proj_gplayenginebridge_build_dir = os.path.join(self.proj_gplayenginebridge_dir, "build")

        self.version = self._get_version()

        if not opts["out"]:
            self.out_dir = os.path.join(self.workspace, "out-cocosruntime")
            self.out_dir = os.path.join(self.out_dir, self._get_version_name().replace('"', ""))
            common.recreate_folder(self.out_dir)
            self.debug_directory = os.path.join(self.workspace, 'debug')
        else:
            self.out_dir = os.path.join(opts["out"], self._get_version_name().replace('"', ""))
            self.debug_directory = os.path.join(self.out_dir, 'debug')
        self.out_dir_temp = os.path.join(self.workspace, "out-cocosruntime-sdk-temp")
        common.recreate_folder(self.out_dir_temp)
        versionName = self._get_version_name().replace('"', "")
        self.sdk_name_for_cocosruntime = "libcocosruntime-" + versionName + ".jar"
        self.sdk_jar_dex_path = os.path.join(self.out_dir_temp, "libcocosruntime-dex.jar")
        self.sdk_jar_no_dex_obfuscated_path = os.path.join(self.out_dir, "libcocosruntime-no-dex-" + versionName + ".jar")

    def _get_version(self):
        # Get the version of current SDK
        return common.find_string_from_file(
            os.path.join(self.proj_cocosruntime_src_dir, "com", "skydragon", "gplay", "runtime", "bridge", "CocosRuntimeBridge.java"),
            r"private\s+static\s+final\s+int\s+VERSION_CODE\s*=\s*(.*)\s*;"
        )

    def _get_version_name(self):
        versionName =  common.find_string_from_file(
            os.path.join(self.proj_cocosruntime_src_dir, "com", "skydragon", "gplay", "runtime", "bridge", "CocosRuntimeBridge.java"),
            r"private\s+static\s+final\s+String\s+VERSION\s*=\s*(.*);"
        )
        return versionName

    def _generate_cocosruntime_jar_file(self):
        common.ensure_folder_exists(self.debug_directory)

        common.run_command(
            "python " + os.path.join(self.script_path, "../jar-maker/JarMaker.py") +
            " -s " + self.proj_gplayenginebridge_src_dir +
            " -s " + self.proj_cocosruntime_src_dir +
            " -o " + self.out_dir +
            " -f " + os.path.split(self.sdk_jar_no_dex_obfuscated_path)[-1] +
            " --ref-lib " + os.path.join(self.script_path, "..", "common", "lib", "android.jar") +
            " --ref-lib " + os.path.join(self.script_path, "..", "common", "lib", "annotations.jar")
        )

    def _generate_cut_jar(self, jar_path, config, out_dir):
        '''Invoke JarCutter tool to customize jar file'''
        # Ensure the our dir is empty
        common.recreate_folder(out_dir)

        # Run the JarCutter
        common.run_command(
            "python " + os.path.join(self.script_path, "../jar-cutter/JarCutter.py") +
            " -c " + config +
            " -s " + jar_path +
            " -o " + out_dir,
            error_message="cutting jar file (%s) failed!" % jar_path
        )

    def _generate_cut_jar_for_runtime_sdk_dex(self):
        common.LOGI(self.TAG, "Generating jar file for runtime sdk ...")
        out_jar_file_name = common.get_file_name(self.sdk_jar_no_dex_obfuscated_path)
        jar_cutter_out_dir = os.path.join(self.out_dir_temp, "jar-cutter-out-for-runtime-dex")
        jar_cutter_config_path = os.path.join(self.script_path, "jar-cutter-for-cocosruntime-dex.json")
        self._generate_cut_jar(self.sdk_jar_no_dex_obfuscated_path, jar_cutter_config_path, jar_cutter_out_dir)
        cut_jar_file_path = os.path.join(jar_cutter_out_dir, out_jar_file_name)
        common.generate_dex_jar(cut_jar_file_path, self.sdk_jar_dex_path)
        # Copy dex sdk file to the output directory
        shutil.copy(self.sdk_jar_dex_path, os.path.join(self.out_dir, self.sdk_name_for_cocosruntime))
        # Remove the dex file since it may be generated with the same name
        # in next step (I means generating cut jar for tencent)
        common.safe_remove_file(self.sdk_jar_dex_path)

    def run(self):
        common.LOGI(self.TAG, "RUNNING ...")
        self._clean()
        self._generate_cocosruntime_jar_file()
        self._generate_cut_jar_for_runtime_sdk_dex();
        self._clean()

        common.LOGI(self.TAG, "DONE!")

    def _clean(self):
        common.LOGI(self.TAG, "Cleaning ...")

        file_to_remove_list = [
            self.out_dir_temp,
            self.proj_cocosruntime_build_dir,
            self.proj_gplayenginebridge_build_dir
        ]

        common.safe_remove_files(file_to_remove_list)

def main():
    common.LOGI("SDKCreator.py:main", "main entry ...")

    from optparse import OptionParser

    parser = OptionParser()

    build_sdk = ("all", "gplay", "runtime", "cocosv2", "cocosv3")

    build_target = ("online", "sandbox", "develop")

    parser.add_option("-t", "--target",
                      action="store", type="choice", choices=build_target, dest="target", default="online",
                      help="SDK run in target to be built, could be " + "'" + "', '".join(build_target) + "'")

    parser.add_option("-o", "--out",
                      action="store", type="string", dest="out", default=None,
                      help="The output folder")

    parser.add_option("-b", "--build-sdk",
                      action="store", type="choice", choices=build_sdk,
                      dest="build_sdk", default="all",
                      help="SDK kind to be built, could be " + "'" + "', '".join(build_sdk) + "'")

    parser.add_option("", "--dont-proguard",
                      action="store_true", dest="dont_proguard", default=False,
                      help="Whether to obfuscate java codes")

    (opts, args) = parser.parse_args()

    common.assert_if_failed(len(args) == 0, "Don't add parameters without `-t`,`-b` and `--dont-proguard`")

    opts = vars(opts)
    opts['workspace'] = os.path.abspath(os.getcwd())
    opts['script_path'] = os.path.dirname(os.path.realpath(__file__))

    if opts["build_sdk"] == "all" or opts["build_sdk"] == "runtime":
        runtime_sdk_creator = RuntimeSDKCreator(opts)
        _creator_list.append(runtime_sdk_creator)
        runtime_sdk_creator.run()

    if opts["build_sdk"] == "all" or opts["build_sdk"] == "gplay":
        gplay_sdk_creator = GplaySDKCreator(opts)
        _creator_list.append(gplay_sdk_creator)
        gplay_sdk_creator.run()

    if opts["build_sdk"] == "all" or opts["build_sdk"] == "cocosv3":
        opts["engineVersion"] = "V3"
        cocosv3_creator = CocosRuntimeCreator(opts)
        _creator_list.append(cocosv3_creator)
        cocosv3_creator.run()

    if opts["build_sdk"] == "all" or opts["build_sdk"] == "cocosv2":
        opts["engineVersion"] = "V2"
        cocosv2_creator = CocosRuntimeCreator(opts)
        _creator_list.append(cocosv2_creator)
        cocosv2_creator.run()


if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        # raceback.print_exc()
        #for creator in _creator_list:
        #    creator.revert()

        if e.__class__.__name__ == "ErrorMessage":
            common.LOGE("SDKCreator.py", e.message)
            sys.exit(1)
        else:
            raise
