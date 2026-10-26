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
        # 13.1.1 (7237) official delta moved the 7 TL_update* registrations out.
        self.assertEqual(13, source.count("classStore.put("))

    def test_r8_and_audio_player_official_delta(self):
        rules = (ROOT / "TMessagesProj/proguard-rules.pro").read_text(encoding="utf-8")
        audio = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java").read_text(encoding="utf-8")

        self.assertIn("-keep class org.telegram.tgnet.** { *; }", rules)
        cast_block = audio[audio.index("castItem.setOnClickListener"):]
        self.assertIn("onSubItemClick(6);", cast_block[:300])

    def test_version_and_identity(self):
        props = (ROOT / "gradle.properties").read_text(encoding="utf-8")

        self.assertIn("APP_VERSION_CODE=7237", props)
        self.assertIn("APP_VERSION_NAME=13.1.1", props)
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

        previous = {
            "version": "12.10.6",
            "build": "7112",
            "commit": "f2908b14133bbffbf7ab04f641ecb5bfaf533242",
        }
        target = {
            "version": "13.1.1",
            "build": "7237",
            "commit": "3de37c8cfb039452be7398909461da6288babf15",
        }

        # 13.1.1 sync: prepared_target is official 7237 for the whole cycle.
        self.assertEqual(target, state["prepared_target"])
        self.assertIn(state["base"]["commit"], state["synced_commits"])
        if state["base"] == target:
            # Owner close-out done: base promoted, pending cleared.
            self.assertIsNone(state["pending"])
        else:
            # In-flight: base stays at last closed-out official (12.10.6) until
            # Owner close-out; pending is 13.1.1 while task PRs land.
            self.assertEqual(previous, state["base"])
            if state["pending"] is not None:
                self.assertEqual(target, state["pending"])
        # 12.10.5 / 12.10.6 official commits must never drop out of synced history.
        self.assertIn("dc780e81ed1261c369c27870e8e0999a1eb0b600", state["synced_commits"])
        self.assertIn("f2908b14133bbffbf7ab04f641ecb5bfaf533242", state["synced_commits"])


if __name__ == "__main__":
    unittest.main()
