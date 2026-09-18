from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "TMessagesProj" / "src" / "main" / "res"


def read_strings(directory: Path, pattern: str = "strings*.xml"):
    values = {}
    for path in sorted(directory.glob(pattern)):
        for node in ET.parse(path).getroot().findall("string"):
            name = node.get("name")
            if name:
                values[name] = node
    return values


class NixLocalizationCoverageTest(unittest.TestCase):
    def test_all_translatable_nix_strings_have_chinese_translations(self):
        default_nix = read_strings(RES / "values", "strings_*.xml")
        zh_cn = read_strings(RES / "values-zh-rCN")
        zh_tw = read_strings(RES / "values-zh-rTW")

        required = {
            name
            for name, node in default_nix.items()
            if node.get("translatable") != "false"
        }
        self.assertEqual([], sorted(required - zh_cn.keys()))
        self.assertEqual([], sorted(required - zh_tw.keys()))

    def test_nix_crash_diagnostics_are_localized(self):
        zh_cn = read_strings(RES / "values-zh-rCN")
        zh_tw = read_strings(RES / "values-zh-rTW")
        required = {"NixDiagnostics", "NixLastCrash", "NixCrashPrivacy"}

        self.assertEqual([], sorted(required - zh_cn.keys()))
        self.assertEqual([], sorted(required - zh_tw.keys()))

    def test_settings_do_not_embed_known_user_visible_english(self):
        passcode_settings = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
            / "NekoPasscodeSettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn('setText("Clear passcodes"', passcode_settings)
        self.assertIn("getString(R.string.PasscodeClearAll)", passcode_settings)

        diagnostics_settings = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
            / "NgxDiagnosticsSettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertNotIn('" / dropped "', diagnostics_settings)
        self.assertNotIn('" / capture"', diagnostics_settings)

    def test_async_diagnostics_refreshes_defer_during_layout(self):
        settings_dir = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
        )
        advanced = (settings_dir / "NgxDiagnosticsSettingsActivity.java").read_text(
            encoding="utf-8"
        )
        base = (settings_dir / "BaseNekoSettingsActivity.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("listView.isComputingLayout()", advanced)
        self.assertIn("listView.post(this::refreshAdapterSafely)", advanced)
        self.assertIn("listView.isComputingLayout()", base)
        self.assertIn("listView.post(this::refreshRowsSafely)", base)

    def test_async_emoji_refreshes_defer_during_layout(self):
        settings_dir = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
        )
        chat = (settings_dir / "NekoChatSettingsActivity.java").read_text(
            encoding="utf-8"
        )
        emoji = (settings_dir / "NekoEmojiSettingsActivity.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("listView.post(this::refreshEmojiSetsSafely)", chat)
        self.assertIn("listView.post(this::updateListAnimatedSafely)", emoji)
        self.assertIn("finished && listView.isComputingLayout()", emoji)

    def test_delayed_settings_refreshes_defer_during_layout(self):
        settings_dir = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "settings"
        )
        general = (settings_dir / "NekoGeneralSettingsActivity.java").read_text(
            encoding="utf-8"
        )
        translator = (
            settings_dir / "NekoTranslatorSettingsActivity.java"
        ).read_text(encoding="utf-8")
        self.assertIn("listView.post(this::refreshFcmPushStatusRow)", general)
        self.assertIn("listView.post(this::checkTemperatureRows)", translator)

    def test_dynamic_settings_strings_are_kept_when_resources_are_shrunk(self):
        locale_controller = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "org"
            / "telegram"
            / "messenger"
            / "LocaleController.java"
        ).read_text(encoding="utf-8")
        key_only_start = locale_controller.index("public static String getString(String key)")
        resource_lookup = locale_controller.index("int resourceId = getStringResId(key);", key_only_start)
        asset_lookup = locale_controller.index("getLocalizationAssetString(key)", key_only_start)
        self.assertLess(asset_lookup, resource_lookup)
        self.assertIn("localizationInternal.getByResName(key)", locale_controller)

    def test_staging_apk_verifier_covers_dynamic_settings_resource_families(self):
        verifier = (
            ROOT / "Tools" / "stability" / "verify_generated_apk_assets.py"
        ).read_text(encoding="utf-8")
        self.assertIn("validate_dynamic_settings_localization_assets", verifier)
        self.assertIn("validate_dynamic_settings_localization_assets(localization_hashes)", verifier)
        self.assertNotIn("validate_static_localization_fallback", verifier)
        self.assertNotIn("aapt2", verifier)
        samples = (
            "GhostMode",
            "FolderNameAsTitle",
            "ShowIdAndDc",
            "CustomTitle",
        )
        for key in samples:
            self.assertIn(key, verifier)
        for directory in ("values", "values-zh-rCN", "values-zh-rTW"):
            strings = read_strings(RES / directory)
            self.assertEqual([], sorted(set(samples) - strings.keys()), directory)

    def test_missing_dynamic_title_is_logged_without_exposing_its_config_key(self):
        cell = (
            ROOT
            / "TMessagesProj"
            / "src"
            / "main"
            / "java"
            / "tw"
            / "nekomimi"
            / "nekogram"
            / "config"
            / "cell"
            / "ConfigCellText.java"
        ).read_text(encoding="utf-8")
        self.assertIn("LOCALIZATION_KEY_MISSING", cell)
        self.assertIn("getString(R.string.NekoSettings)", cell)
        self.assertNotIn("? key : title", cell)


if __name__ == "__main__":
    unittest.main()
