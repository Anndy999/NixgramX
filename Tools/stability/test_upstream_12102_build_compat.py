"""Static guards for Telegram 12.10.2 build-system and proxy API adaptation."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class Upstream12102BuildCompatTest(unittest.TestCase):
    def test_proxy_info_uses_proxy_settings_api(self):
        shared = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/SharedConfig.java").read_text(encoding="utf-8")
        proxy_util = (ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/utils/ProxyUtil.kt").read_text(encoding="utf-8")

        self.assertIn("ProxySettings.fromUri(Uri.parse(url))", shared)
        self.assertIn("ConnectionsManager.setProxySettings(true, finalInfo.settings)", shared)
        self.assertNotIn("finalInfo.address", shared)
        self.assertNotIn("finalInfo.username", shared)
        self.assertIn("it.settings.address", proxy_util)
        self.assertNotIn("{ it.address }", proxy_util)

    def test_missing_media3_consumer_rules_are_filtered_for_agp9(self):
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        filter_line = "android.defaultConfig.consumerProguardFiles.removeAll { !it.exists() }"
        self.assertIn(filter_line, root_build)
        self.assertGreater(root_build.index(filter_line), root_build.index("afterEvaluate"))

    def test_conflict_adaptations_keep_valid_control_flow(self):
        emoji = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/Emoji.java").read_text(encoding="utf-8")
        launch = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java").read_text(encoding="utf-8")

        self.assertIn("} else {\n                    try {\n                        final EmojiPack emojiPack", emoji)
        self.assertNotIn("} else try {", emoji)
        self.assertIn("ConnectionsManager.setProxySettings(false, null);*/", launch)


if __name__ == "__main__":
    unittest.main()
