"""Guard the APK Telegram API credential source and CI precedence."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
BUILD_GRADLE = ROOT / "TMessagesProj/build.gradle"
BUILD_VARS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
BUILD_WORKFLOWS = (
    "canary.yml",
    "full-verify.yml",
    "quick-verify.yml",
    "release.yml",
    "self-hosted-debug.yml",
    "staging.yml",
    "upstream-sync-ci.yml",
)


class TelegramApiCredentialsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gradle = BUILD_GRADLE.read_text(encoding="utf-8")
        cls.build_vars = BUILD_VARS.read_text(encoding="utf-8")

    def test_environment_precedes_local_properties_without_a_source_fallback(self):
        self.assertIn(
            "def appId = System.getenv('TELEGRAM_APP_ID')?.trim() ?: properties?.getProperty('TELEGRAM_APP_ID')?.trim()",
            self.gradle,
        )
        self.assertIn(
            "def appHash = System.getenv('TELEGRAM_APP_HASH')?.trim() ?: properties?.getProperty('TELEGRAM_APP_HASH')?.trim()",
            self.gradle,
        )
        self.assertNotIn("39764388", self.gradle)
        self.assertIn("appId == '6'", self.gradle)
        self.assertIn("^[0-9a-f]{32}$", self.gradle)

    def test_generated_build_config_is_the_only_runtime_credential_source(self):
        self.assertIn("buildConfigField 'int', 'APP_ID', appId", self.gradle)
        self.assertIn("buildConfigField 'String', 'APP_HASH', '\"' + appHash + '\"'", self.gradle)
        self.assertIn("public static int APP_ID = 0;", self.build_vars)
        self.assertIn('public static String APP_HASH = "";', self.build_vars)
        self.assertIn("APP_ID = BuildConfig.APP_ID;", self.build_vars)
        self.assertIn("APP_HASH = BuildConfig.APP_HASH;", self.build_vars)

    def test_all_android_build_workflows_inject_client_credentials(self):
        expected_id = "TELEGRAM_APP_ID: ${{ secrets.TELEGRAM_APP_ID }}"
        expected_hash = "TELEGRAM_APP_HASH: ${{ secrets.TELEGRAM_APP_HASH }}"
        for name in BUILD_WORKFLOWS:
            workflow = (ROOT / ".github/workflows" / name).read_text(encoding="utf-8")
            self.assertIn(expected_id, workflow, name)
            self.assertIn(expected_hash, workflow, name)


if __name__ == "__main__":
    unittest.main()
