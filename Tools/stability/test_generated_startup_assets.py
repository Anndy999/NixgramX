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
            "lottie_meta.bin string_resource_ids.bin emoji.pack localization_en.bin",
            workflow,
        )
        self.assertIn('unzip -Z1 "$PRIVATE_BETA_APK"', workflow)
        self.assertNotIn("grep -Fxq", workflow)

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


if __name__ == "__main__":
    unittest.main()
