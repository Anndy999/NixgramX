import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERIFIER = ROOT / "Tools" / "stability" / "verify_generated_apk_assets.py"


def load_verifier():
    spec = importlib.util.spec_from_file_location("verify_generated_apk_assets", VERIFIER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


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

    def test_runtime_only_settings_titles_are_verified_in_localization_assets(self):
        verifier = load_verifier()
        hashes = {
            verifier.java_hash(name)
            for name in verifier.DYNAMIC_SETTINGS_STRING_SAMPLES.values()
        }
        verifier.validate_dynamic_settings_localization_assets(hashes)

        with self.assertRaisesRegex(ValueError, "GhostMode"):
            verifier.validate_dynamic_settings_localization_assets(set())

    def test_validate_emoji_pack_accepts_committed_epk3_asset(self):
        verifier = load_verifier()
        data = (ROOT / "TMessagesProj" / "src" / "main" / "assets" / "emoji.pack").read_bytes()
        verifier.validate_emoji_pack(data)

    def test_validate_emoji_pack_rejects_legacy_and_damaged_headers(self):
        verifier = load_verifier()
        # Legacy format used first u32 as metadata length divisible by 12.
        with self.assertRaisesRegex(ValueError, "unsupported emoji pack magic"):
            verifier.validate_emoji_pack((12 * 10).to_bytes(4, "little") + b"\x00" * 40)
        with self.assertRaisesRegex(ValueError, "truncated EPK3 header"):
            verifier.validate_emoji_pack(b"EPK3")
        # Valid-looking magic/version but wrong declared length.
        import struct
        bad = struct.pack("<4sHHIIIIII", b"EPK3", 3, 20, 64, 64, 1, 1, 999, 0)
        bad += b"\x00" * (32 - len(bad) + 20)
        with self.assertRaisesRegex(ValueError, "EPK3 header fields inconsistent"):
            verifier.validate_emoji_pack(bad)


if __name__ == "__main__":
    unittest.main()
