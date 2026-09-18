"""NGX diagnostics sources must stay typed and free of production-path instrumentation.

This suite also guards the BaseNekoSettingsActivity ViewHolder type contract.
The shared factory owns the concrete class instantiated for every TYPE_* slot, so a
settings page that relies on that factory must never consume a slot as a different
class. The contract is stated as "factory producer type == consumer cast type" rather
than "the page must import one particular HeaderCell implementation", so it keeps
holding if the shared framework ever changes which HeaderCell it uses.

Pages that override onCreateViewHolder define their own factory and are therefore
out of scope of the shared-factory contract.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
DIAG = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/diagnostics"
SETTINGS = ROOT / "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings"
BASE = SETTINGS / "BaseNekoSettingsActivity.java"
PROBLEM_DIAGNOSTICS = SETTINGS / "ProblemDiagnosticsActivity.java"
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

IMPORT_RE = re.compile(r"^import\s+(?!static\s)([\w.]+)\s*;\s*$", re.M)
CASE_RE = re.compile(r"case\s+(TYPE_\w+)\s*:(.*?)(?=case\s+TYPE_\w+\s*:|break\s*;)", re.S)
CTOR_RE = re.compile(r"new\s+([A-Za-z_][\w.]*)\s*\(")
CAST_RE = re.compile(r"\(\s*([A-Za-z_]\w*(?:\.[A-Za-z_]\w+)*)\s*\)\s*holder\s*\.\s*itemView")


def _imports(text):
    """Map simple class name -> fully qualified name for explicit non-static imports."""
    found = {}
    for match in IMPORT_RE.finditer(text):
        fqn = match.group(1)
        found.setdefault(fqn.rsplit(".", 1)[-1], fqn)
    return found


def _resolve(name, imports):
    return name if "." in name else imports.get(name)


def _factory_type_map(base_text):
    """Map every TYPE_* slot to the fully qualified class the factory instantiates."""
    imports = _imports(base_text)
    produced = {}
    for match in CASE_RE.finditer(base_text):
        slot, body = match.group(1), match.group(2)
        ctor = CTOR_RE.search(body)
        if ctor is None:
            continue
        resolved = _resolve(ctor.group(1), imports)
        if resolved:
            produced.setdefault(slot, resolved)
    return produced


def _consumed_types(text):
    """Fully qualified type of every cast applied to `holder.itemView`."""
    imports = _imports(text)
    resolved = set()
    for match in CAST_RE.finditer(text):
        fqn = _resolve(match.group(1), imports)
        if fqn:
            resolved.add(fqn)
    return resolved


def _simple(fqn):
    return fqn.rsplit(".", 1)[-1]


def _settings_pages():
    return sorted(
        path
        for path in SETTINGS.glob("*.java")
        if path.name not in {"BaseNekoSettingsActivity.java", "BaseNekoXSettingsActivity.java"}
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

    def test_problem_diagnostics_matches_factory_header_cell(self):
        """The page must consume TYPE_HEADER as the class the factory produces."""
        factory = _factory_type_map(BASE.read_text(encoding="utf-8"))
        self.assertIn("TYPE_HEADER", factory, "shared factory lost its TYPE_HEADER branch")
        text = PROBLEM_DIAGNOSTICS.read_text(encoding="utf-8")
        consumed = {t for t in _consumed_types(text) if _simple(t) == "HeaderCell"}
        self.assertTrue(consumed, "ProblemDiagnosticsActivity no longer casts a HeaderCell")
        self.assertEqual(
            {factory["TYPE_HEADER"]},
            consumed,
            "ProblemDiagnosticsActivity consumes %s but BaseNekoSettingsActivity "
            "TYPE_HEADER produces %s" % (sorted(consumed), factory["TYPE_HEADER"]),
        )

    def test_settings_pages_respect_factory_type_contract(self):
        """A page using the shared factory may only cast to types it really produces."""
        factory = _factory_type_map(BASE.read_text(encoding="utf-8"))
        produced = set(factory.values())
        self.assertTrue(produced, "shared factory exposes no cell types")
        for page in _settings_pages():
            text = page.read_text(encoding="utf-8")
            if "onCreateViewHolder" in text:
                continue  # page owns its own factory
            consumed = _consumed_types(text)
            if not consumed:
                continue
            unexpected = consumed - produced
            self.assertFalse(
                unexpected,
                "%s casts holder.itemView to %s, which the shared factory never "
                "produces (known: %s)"
                % (page.name, sorted(unexpected), sorted(produced)),
            )


if __name__ == "__main__":
    unittest.main()
