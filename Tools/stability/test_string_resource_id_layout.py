from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
BUILD_GRADLE = ROOT / "TMessagesProj" / "build.gradle"
STRINGS_TASK = (
    ROOT
    / "buildSrc"
    / "src"
    / "main"
    / "kotlin"
    / "org"
    / "telegram"
    / "tasks"
    / "TelegramStringsTask.kt"
)
RES = ROOT / "TMessagesProj" / "src" / "main" / "res"

# Every locale this app ships translations for must stay selectable in the APK.
# Non-locale qualifiers such as `night` are configuration switches, not locales.
NON_LOCALE_QUALIFIERS = {"night"}

# `ResTable_type.entryCount` is (highest pinned entry id + 1) and each
# configuration block repeats an entryCount x 4-byte offset table.  The pinned
# app string namespace must therefore start low: pinning it at the top of the
# 16-bit entry space made every locale carry a 256 KB offset table
# (96 x 256 KB = 24.6 MB of resources.arsc).
MAX_ENTRY_ID_BASE = 0x4000

# Library-owned string ids are allocated from 0 upwards; a shipped Beta APK
# tops out at 234.  The pinned namespace must clear them with room to spare.
MIN_ENTRY_ID_BASE = 0x0100


def extract_int_constant(source: str, name: str) -> int:
    match = re.search(
        r"const\s+val\s+%s\s*=\s*(0x[0-9A-Fa-f]+|\d+)" % re.escape(name), source
    )
    if not match:
        raise AssertionError("constant %s not found in TelegramStringsTask.kt" % name)
    return int(match.group(1), 0)


def app_locale_qualifiers() -> set[str]:
    locales = set()
    for entry in RES.glob("values-*"):
        if not entry.is_dir():
            continue
        qualifier = entry.name[len("values-"):]
        # Skip configuration qualifiers (night, v21, ...) and locale+config
        # combinations such as `values-b+sr+Latn-night`.
        head = qualifier.split("-")[0]
        if qualifier in NON_LOCALE_QUALIFIERS:
            continue
        if re.fullmatch(r"v\d+", head):  # platform version qualifier
            continue
        if re.fullmatch(r"(b\+)?[A-Za-z]{2,3}(\+[A-Za-z]+)*", head):
            locales.add(qualifier)
    return locales


def declared_locale_filters() -> set[str]:
    source = BUILD_GRADLE.read_text(encoding="utf-8")
    match = re.search(
        r"localeFilters\.addAll\((.*?)\)", source, re.DOTALL
    )
    if not match:
        raise AssertionError("localeFilters.addAll(...) not found in build.gradle")
    return set(re.findall(r"'([^']+)'", match.group(1)))


class StringResourceIdLayoutTest(unittest.TestCase):
    def test_stable_id_base_is_anchored_low_in_the_entry_space(self):
        source = STRINGS_TASK.read_text(encoding="utf-8")
        base = extract_int_constant(source, "STRING_RESOURCE_ID_BASE")
        # Entry part of the resource id (type id 0x0F is fixed for strings).
        entry_base = base & 0xFFFF
        self.assertLess(
            entry_base,
            MAX_ENTRY_ID_BASE,
            "pinning the string namespace at %#x forces a %d-entry offset table "
            "for every locale" % (entry_base, entry_base + 1),
        )
        self.assertGreaterEqual(
            entry_base,
            MIN_ENTRY_ID_BASE,
            "pinned string ids would collide with library-owned string ids",
        )

    def test_stable_ids_are_handed_out_ascending(self):
        source = STRINGS_TASK.read_text(encoding="utf-8")
        if "STRING_RESOURCE_ID_BASE + index" not in source:
            self.fail(
                "stable ids must be handed out ascending from the low base so "
                "that entryCount stays close to the number of pinned strings"
            )

    def test_locale_filter_covers_every_shipped_locale(self):
        declared = declared_locale_filters()
        shipped = app_locale_qualifiers()
        self.assertEqual(
            sorted(shipped - declared),
            [],
            "these shipped locales would be stripped from the APK",
        )
        self.assertEqual(
            sorted(declared - shipped),
            [],
            "localeFilters lists locales that ship no resources",
        )


if __name__ == "__main__":
    unittest.main()
