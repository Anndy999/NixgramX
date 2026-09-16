"""NGX diagnostics sources must stay typed and free of production-path instrumentation."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
DIAG = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/diagnostics"
FORBIDDEN_APIS = (
    "Value.string",
    "Value.label",
    "event(String",
    "Map<String,String>",
    "JSONObject",
    "Bundle dump",
)
FORBIDDEN_FILES = (
    "ChatActivity.java",
    "PhotoViewer.java",
    "ConnectionsManager.java",
    "TranslateController.java",
    "MediaController.java",
)


class NgxDiagnosticsPrivacyTest(unittest.TestCase):
    def test_typed_api_only(self):
        for path in DIAG.glob("*.java"):
            text = path.read_text(encoding="utf-8")
            for token in FORBIDDEN_APIS:
                self.assertNotIn(token, text, path.name)

    def test_no_value_string_factory(self):
        core = (DIAG / "NgxDiagnosticCore.java").read_text(encoding="utf-8")
        self.assertIn("public static Value bool", core)
        self.assertIn("public static Value integer", core)
        self.assertNotIn("public static Value string", core)


if __name__ == "__main__":
    unittest.main()
