from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class GeneratedStartupAssetsTest(unittest.TestCase):
    def test_application_module_uses_asset_generating_plugin(self):
        build_gradle = (ROOT / "TMessagesProj" / "build.gradle").read_text(
            encoding="utf-8"
        )
        self.assertIn("id 'org.telegram.build-plugin'", build_gradle)
        self.assertIn("id 'org.telegram.build-app-plugin'", build_gradle)

    def test_private_beta_rejects_apk_without_startup_assets(self):
        workflow = (ROOT / ".github" / "workflows" / "private-beta.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            'python3 Tools/stability/verify_generated_apk_assets.py "$PRIVATE_BETA_APK"',
            workflow,
        )

    def test_lottie_generator_has_an_explicit_output_directory(self):
        plugin = (
            ROOT
            / "buildSrc"
            / "src"
            / "main"
            / "kotlin"
            / "org"
            / "telegram"
            / "plugin"
            / "TelegramBuildAppPlugin.kt"
        ).read_text(encoding="utf-8")
        self.assertIn(
            'outputDir.set(project.layout.buildDirectory.dir("generated/lottieMeta/${variant.name}/assets"))',
            plugin,
        )

    def test_all_nixgramx_string_resource_files_feed_localization_assets(self):
        app_plugin = (
            ROOT
            / "buildSrc"
            / "src"
            / "main"
            / "kotlin"
            / "org"
            / "telegram"
            / "plugin"
            / "TelegramBuildAppPlugin.kt"
        ).read_text(encoding="utf-8")
        self.assertEqual(2, app_plugin.count('include("strings*.xml")'))
        self.assertEqual(2, app_plugin.count('include("values-*/strings*.xml")'))

        build_plugin = (
            ROOT
            / "buildSrc"
            / "src"
            / "main"
            / "kotlin"
            / "org"
            / "telegram"
            / "plugin"
            / "TelegramBuildPlugin.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('include("values-*/strings*.xml")', build_plugin)

    def test_android_chinese_script_locales_map_to_packaged_assets(self):
        generator = (
            ROOT
            / "buildSrc"
            / "src"
            / "main"
            / "kotlin"
            / "org"
            / "telegram"
            / "tasks"
            / "localization"
            / "GenerateLocalizationUtilsJavaTask.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('if (languageTags.contains("zh-CN")', generator)
        self.assertIn('java.appendLine("        if (\\\"zh\\\".equals(language)) {")', generator)
        self.assertIn('\\"Hant\\"', generator)
        self.assertIn('\\"HK\\"', generator)
        self.assertIn('\\"MO\\"', generator)

    def test_async_ayu_size_refresh_never_notifies_during_layout(self):
        activity = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
            / "NekoExperimentalSettingsActivity.java"
        ).read_text(encoding="utf-8")
        method = activity.split("public void refreshAyuDataSize()", 1)[1].split(
            "private void exportAyuDB()", 1
        )[0]
        self.assertIn("listView.isComputingLayout()", method)
        self.assertIn("listView.post(this::refreshAyuDataSize)", method)
        self.assertIn("position >= listAdapter.getItemCount()", method)
        self.assertLess(
            method.index("listView.isComputingLayout()"),
            method.index("listAdapter.notifyItemChanged(position)"),
        )

    def test_emoji_pack_is_stored_uncompressed_for_open_fd(self):
        build_gradle = (ROOT / "TMessagesProj" / "build.gradle").read_text(
            encoding="utf-8"
        )
        self.assertIn('noCompress += "pack"', build_gradle)


if __name__ == "__main__":
    unittest.main()
