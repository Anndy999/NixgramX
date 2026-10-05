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

        self.assertIn("APP_VERSION_CODE=7112", props)
        self.assertIn("APP_VERSION_NAME=12.10.6", props)
        self.assertIn("NIXGRAMX_VERSION_NAME=12.10.6", props)
        # NIXGRAMX_VERSION_CODE is the fork distribution version and is bumped
        # on every release. Assert the invariant, not a frozen value: the key
        # must be declared exactly once and hold an integer.
        codes = [line for line in props.splitlines() if line.startswith("NIXGRAMX_VERSION_CODE=")]
        self.assertEqual(1, len(codes), "NIXGRAMX_VERSION_CODE must be declared exactly once")
        self.assertRegex(codes[0], r"^NIXGRAMX_VERSION_CODE=\d+$")
        self.assertIn("APP_PACKAGE=app.nixgramx.android", props)

    def test_upstream_state_is_complete(self):
        state = json.loads((ROOT / "docs/upstream-base.json").read_text(encoding="utf-8"))

        target = {
            "version": "12.10.6",
            "build": "7112",
            "commit": "f2908b14133bbffbf7ab04f641ecb5bfaf533242",
        }

        # 12.10.6 Stable close-out (1355): base/prepared_target match official 7112.
        self.assertIsNone(state["pending"])
        self.assertEqual(target, state["base"])
        self.assertEqual(state["base"], state["prepared_target"])
        self.assertIn(state["base"]["commit"], state["synced_commits"])
        # 12.10.5 official commit must never drop out of synced history.
        self.assertIn("dc780e81ed1261c369c27870e8e0999a1eb0b600", state["synced_commits"])
        # 12.10.6 official commit must be recorded in synced history.
        self.assertIn("f2908b14133bbffbf7ab04f641ecb5bfaf533242", state["synced_commits"])

if __name__ == "__main__":
    unittest.main()
