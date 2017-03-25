#!/usr/bin/env python
# coding=utf-8
import sys
import os
import re
import shutil

_creator_list = []

_sample_project_list = [
    {
        "project_name": "GplayDemo",
        "project_path": "../../../GplaySDK/samples/GplayDemo",
        "version_file": "../../../GplaySDK/frameworks/GplaySDK/src/main/java/com/skydragon/gplay/Gplay.java",
        "depend_libs": [
            "out-gplaysdk/libgplaysdk.jar"
        ],
        "libs_copy_to": "../../../GplaySDK/samples/GplayDemo/libs",
        "replace_files": {
            "../../../GplaySDK/samples/GplayDemo/build.gradle": [
                {
                    "src": "(compile project\(':frameworks:GplaySDK'\))",
                    "dst": "//\\1"
                }
            ],
            "../../settings.gradle": [
                {
                    "src": "(include.*)",
                    "dst": "//\\1"
                },
                {
                    "src": "//(include.*GplayDemo')",
                    "dst": "\\1"
                }
            ]
        },
        "rename": [
            #{ "from": "assets", "to": "assets-renamed" }
        ]
    }
]

def before_run(opts):
    '''Before running APKCreator, do something that APKCreator depends on'''
    if not opts["dont_build_sdk"]:
        common.LOGI("APKCreator.py:before_run", "Generating Gplay SDK jar file...")
        proguard_arg = " --dont-proguard" if opts["dont_proguard"] else ""
        proguard_arg += " -t " + opts["sdk_type"]
        # Calling the SDKCreator to generating CocosPlay jar file
        common.run_command("python %s" % os.path.join(opts["script_path"], "SDKCreator.py" + proguard_arg))

def revert():
    '''Revert changes since some project configuration files would be changed!'''
    for c in _creator_list:
        for replacer in c.build_gradle_replacer_list:
            replacer.revert()
        c._rename_revert()

class APKCreator(object):
    '''APKCreator is used for pack a java project to balabala.apk file'''
    def __init__(self, opts):
        self.TAG = "APKCreator"
        common.LOGI(self.TAG, "opts:" + str(opts))
        self.proguard = not opts["dont_proguard"]
        self.project_info = opts["project_info"]
        self.workspace = opts["workspace"]
        self.script_path = opts["script_path"]
        if opts["out"]:
            self.out_dir = opts["out"]
        else:
            self.out_dir = os.path.join(self.workspace, "out-apk")
        common.ensure_folder_exists(self.out_dir)

        # The name of the apk generated at project directory
        self.generated_apk_name = self.project_info["project_name"] + "-release.apk"

        # The final apk name created at output dir
        self.out_apk_name = self.project_info["project_name"] + ".apk"

        # The android project root directory
        self.proj_dir = os.path.join(self.script_path, self.project_info["project_path"])
        self.proj_libs_dir = os.path.join(self.proj_dir, "libs")
        common.ensure_folder_exists(self.proj_libs_dir)

        self.build_gradle_replacer_list = []
        self.copied_lib_list = []
        self.version_name = self._get_version_name()
        self.version_code = self._get_version_code()

        if opts["libs"]:
            self.project_info["depend_libs"] = [ os.path.join(opts["libs"], self.version_name, "libgplaysdk.jar") ]

        # Ensure the version name & version code are the same
        common.assert_if_failed(
            self.version_name.replace(".", "") == self.version_code,
            "VERSION(%s) doesn't match VERSION_CODE(%s)" % (self.version_name, self.version_code)
        )

    def _get_version_name(self):
        return common.find_string_from_file(
            os.path.join(self.script_path, self.project_info["version_file"]),
            r"private\s+static\s+final\s+String\s+VERSION\s*=\s*\"(.*)\"\s*;"
        )

    def _get_version_code(self):
        return common.find_string_from_file(
            os.path.join(self.script_path, self.project_info["version_file"]),
            r"private\s+static\s+final\s+int\s+VERSION_CODE\s*=\s*(.*)\s*;"
        )

    def _copy_depend_libs(self):
        common.LOGI(self.TAG, "Copying depended libraries to project libs directory ")
        for depend_lib in self.project_info["depend_libs"]:
            lib_path = os.path.join(self.script_path, depend_lib)
            common.assert_if_failed(common.is_file(lib_path), "Cannot find " + lib_path)
            self.copied_lib_list.append(os.path.join(self.proj_libs_dir, os.path.split(lib_path)[-1]))
            shutil.copy2(lib_path, self.proj_libs_dir)

    def _modify_androidmanifest(self):
        common.LOGI(self.TAG, "Modifying AndroidManifest.xml ...")
        manifest_replacer = common.FileStringReplacer(
            os.path.join(self.script_path,
                         self.project_info["project_path"], "AndroidManifest.xml")
        )
        manifest_replacer.replace_regexp(
            r"(android:versionName\s*=\s*\").*(\s*\")",
            r"\g<1>" + self.version_name + r"\g<2>"
        )
        manifest_replacer.replace_regexp(
            r"(android:versionCode\s*=\s*\").*(\s*\")",
            r"\g<1>" + self.version_code + r"\g<2>"
        )
        manifest_replacer.flush()

    def _modify_project_dependence(self):
        common.LOGI(self.TAG, "Modifying project dependencies ... ")
        for (file_path, replace_list) in self.project_info["replace_files"].items():
            replacer = common.FileStringReplacer(os.path.join(self.script_path, file_path))
            self.build_gradle_replacer_list.append(replacer)

            for replace_element in replace_list:
                replacer.replace_regexp(replace_element["src"], replace_element["dst"])
            replacer.flush()

    def _rename(self):
        common.LOGI(self.TAG, "Renaming stuff ...")
        for rename_info in self.project_info["rename"]:
            rename_from = os.path.join(self.proj_dir, rename_info["from"])
            rename_to = os.path.join(self.proj_dir, rename_info["to"])
            if os.path.exists(rename_from):
                shutil.move(rename_from, rename_to)

    def _rename_revert(self):
        common.LOGI(self.TAG, "Reverting renaming stuff ...")
        for rename_info in self.project_info["rename"]:
            rename_from = os.path.join(self.proj_dir, rename_info["to"])
            rename_to = os.path.join(self.proj_dir, rename_info["from"])
            if os.path.exists(rename_from):
                shutil.move(rename_from, rename_to)

    def _remove_jar_files(self):
        common.LOGI(self.TAG, "Removing libraries copied to project libs directory ...")
        common.safe_remove_files(self.copied_lib_list)

    def _generate_apk(self):
        common.LOGI(self.TAG, "Generating APK ...")

        common.generate_apk_by_gradle(self.proj_dir)

    def _move_apk(self):
        src = os.path.join(self.proj_dir, "build", "outputs", "apk", self.generated_apk_name)
        dst = os.path.join(self.out_dir, self.out_apk_name)
        common.LOGI(self.TAG, "Moving (%s) to (%s)" % (src, dst))
        shutil.move(src, dst)

    def _clean(self):
        common.LOGI(self.TAG, "Cleaning ...")

        # Remove jar file
        self._remove_jar_files()

        common.safe_remove_files([
            os.path.join(self.script_path, "../../local.properties"),
            os.path.join(self.proj_dir, "bin"),
            os.path.join(self.proj_dir, "build"),
            os.path.join(self.proj_dir, "gen"),
            os.path.join(self.proj_dir, "local.properties")
        ])

        # Revert renaming
        self._rename_revert()

    def run(self):
        common.LOGI(self.TAG, "Running ...")
        self._clean()
        self._rename()
        self._modify_androidmanifest()
        self._copy_depend_libs()
        self._modify_project_dependence()

        # Generating Cocos Runtime demo APK file
        self._generate_apk()

        # Move the apk file to out folder
        self._move_apk()

        self._clean()

        for replacer in self.build_gradle_replacer_list:
            replacer.revert()

        common.LOGI(self.TAG, "DONE!")

def main():
    from optparse import OptionParser
    parser = OptionParser()

    parser.add_option("-p", "--project",
                      action="append", type="string", dest="project", default=[],
                      help="The project name, it builds all project by default, if this argument is assigned, only [GplayDemo] are valid!")

    parser.add_option("-l", "--libs",
                      action="store", type="string", dest="libs", default=None,
                      help="The Gplay SDK root, exp: root/1.1.0/libgplaysdk.jar")

    parser.add_option("-o", "--out",
                      action="store", type="string", dest="out", default=None,
                      help="The output folder")

    parser.add_option("-t", "--sdk-type",
                      action="store", type="string", dest="sdk_type", default="online",
                      help="The generated SDK is for online, sandbox, develop")

    parser.add_option("", "--dont-build-sdk",
                      action="store_true", dest="dont_build_sdk", default=False,
                      help="Whether to build SDK before running APKCreator")

    parser.add_option("", "--dont-proguard",
                      action="store_true", dest="dont_proguard", default=False,
                      help="Whether to obfuscate java codes")

    (opts, args) = parser.parse_args()

    for project_name in opts.project:
        common.assert_if_failed(project_name in ["GplayDemo"], "-p(--project) %s isn't supported" % opts.project)

    opts = vars(opts)
    opts['workspace'] = os.path.abspath(os.getcwd())
    opts['script_path'] = os.path.dirname(os.path.realpath(__file__))

    if not opts["libs"]:
        before_run(opts)

    def build_project(project_info):
        opts["project_info"] = project_info
        creator = APKCreator(opts)
        _creator_list.append(creator)
        creator.run()

    # Build all projects if -p wasn't assigned
    if len(opts["project"]) == 0:
        for info in _sample_project_list:
            build_project(info)
    else:
        for info in _sample_project_list:
            if info["project_name"] in opts["project"]:
                build_project(info)


if __name__ == '__main__':
    sys.path.append(os.path.join(os.path.split(os.path.realpath(__file__))[0], '..', 'common'))
    import common
    try:
        main()
    except Exception as e:
        if e.__class__.__name__ == "ErrorMessage":
            common.LOGE("APKCreator.py", e.message)
            sys.exit(1)
        else:
            raise
    finally:
        common.LOGI("APKCreator.py", "Script execution finished!")
        revert()
