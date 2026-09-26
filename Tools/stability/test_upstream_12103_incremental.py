import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class Upstream12103IncrementalTest(unittest.TestCase):
    def test_tl_class_store_matches_factory_migration(self):
        source = (ROOT / "TMessagesProj/src/main/java/org/telegram/tgnet/TLClassStore.java").read_text(encoding="utf-8")

        self.assertIn("SparseArray<TLObjectFactory>", source)
        self.assertIn("TLObject response = factory.create();", source)
        self.assertNotIn("SparseArray<Class>", source)
        self.assertNotIn("newInstance()", source)
        self.assertEqual(20, source.count("classStore.put("))

    def test_r8_and_audio_player_official_delta(self):
        rules = (ROOT / "TMessagesProj/proguard-rules.pro").read_text(encoding="utf-8")
        audio = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java").read_text(encoding="utf-8")

        self.assertIn("-keep class org.telegram.tgnet.** { *; }", rules)
        cast_block = audio[audio.index("castItem.setOnClickListener"):]
        self.assertIn("onSubItemClick(6);", cast_block[:300])

    def test_version_and_identity(self):
        props = (ROOT / "gradle.properties").read_text(encoding="utf-8")

        self.assertIn("APP_VERSION_CODE=7105", props)
        self.assertIn("APP_VERSION_NAME=12.10.5", props)
        self.assertIn("NIXGRAMX_VERSION_NAME=12.10.4", props)
        # NIXGRAMX_VERSION_CODE is the fork distribution version and is bumped
        # on every release. Assert the invariant, not a frozen value: the key
        # must be declared exactly once and hold an integer.
        codes = [line for line in props.splitlines() if line.startswith("NIXGRAMX_VERSION_CODE=")]
        self.assertEqual(1, len(codes), "NIXGRAMX_VERSION_CODE must be declared exactly once")
        self.assertRegex(codes[0], r"^NIXGRAMX_VERSION_CODE=\d+$")
        self.assertIn("APP_PACKAGE=app.nixgramx.android", props)

    def test_upstream_state_is_complete(self):
        state = json.loads((ROOT / "docs/upstream-base.json").read_text(encoding="utf-8"))

        # During 12.10.5 sync: base stays at last closed-out official until Owner close-out.
        self.assertEqual("c84801762fd5f936c8296ecf09a14a48ebfc4fe4", state["base"]["commit"])
        self.assertIn(state["base"]["commit"], state["synced_commits"])
        self.assertIsNotNone(state["pending"])
        self.assertEqual("12.10.5", state["pending"]["version"])
        self.assertEqual("7105", state["pending"]["build"])
        self.assertEqual("dc780e81ed1261c369c27870e8e0999a1eb0b600", state["pending"]["commit"])


if __name__ == "__main__":
    unittest.main()
