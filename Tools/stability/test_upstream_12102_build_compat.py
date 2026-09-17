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

    def test_nix_video_player_egl_extension_keeps_android_import(self):
        video_player = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/VideoPlayer.java").read_text(encoding="utf-8")

        self.assertIn("import android.opengl.EGLContext;", video_player)
        self.assertIn("private EGLContext eglParentContext;", video_player)
        self.assertIn("public void setEGLContext(EGLContext ctx)", video_player)

    def test_nix_call_sites_use_telegram_12102_apis(self):
        source_root = ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram"
        nix_sources = "\n".join(path.read_text(encoding="utf-8") for path in source_root.rglob("*.java"))

        self.assertNotIn('new RLottieDrawable(R.raw.sun, String.valueOf(R.raw.sun),', nix_sources)
        self.assertNotIn('new RLottieDrawable(R.raw.qr_matrix, "qr_matrix",', nix_sources)
        self.assertNotIn('? "caption_hide" : "name_hide", dp(24), dp(24)', nix_sources)
        self.assertNotIn('checkProxy("ping.neko",', nix_sources)
        self.assertIn('ProxySettings.builder()', nix_sources)
        self.assertIn('checkProxy(pingSettings,', nix_sources)

    def test_pinned_media3_keeps_its_supported_android_sdk(self):
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")

        self.assertIn("def isPinnedMedia3Module =", root_build)
        self.assertIn("if (!isPinnedMedia3Module) {\n                    compileSdk = 37", root_build)
        self.assertIn("if (isPinnedMedia3Module && plugins.hasPlugin", root_build)

        for workflow in (ROOT / ".github/workflows").glob("*.yml"):
            contents = workflow.read_text(encoding="utf-8")
            if "platforms;android-37.0" in contents:
                self.assertIn("platforms;android-35", contents, workflow.name)

    def test_known_agp_931_lint_crash_is_scoped_out(self):
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")

        self.assertIn("if (project.path == ':TMessagesProj')", root_build)
        self.assertIn("disable 'ThreadConstraint'", root_build)


if __name__ == "__main__":
    unittest.main()
