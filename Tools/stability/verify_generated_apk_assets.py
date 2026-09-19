#!/usr/bin/env python3
"""Validate generated Telegram assets inside a built APK."""

from __future__ import annotations

import struct
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

# Each name is used as a runtime key, not a static R.string reference.  Keep
# one representative from every ConfigCell family that resolves its title
# dynamically; a staging APK must retain these resources after shrinking.
DYNAMIC_SETTINGS_STRING_SAMPLES = {
    "ConfigCellText": "GhostMode",
    "ConfigCellTextCheck": "FolderNameAsTitle",
    "ConfigCellSelectBox": "ShowIdAndDc",
    "ConfigCellTextInput": "CustomTitle",
}

# The `string` type must not be pinned across the whole 16-bit entry space.
#
# ResTable_type.entryCount is (highest used entry id + 1) and *every*
# configuration block of the type repeats an entryCount x 4-byte offset table.
# Pinning the app string namespace from the top of the entry space therefore
# made each locale carry a 256 KB offset table: 96 locales x 256 KB = 24.6 MB of
# resources.arsc, i.e. 40% of the shipped Beta APK.  TelegramStringsTask now
# anchors the same (still stable) ids at entry 0x800, which keeps entryCount
# near the number of strings actually pinned.
MAX_STRING_TYPE_ENTRY_COUNT = 0x4000

# Number of locale blocks the `string` type may keep after locale filtering.
# The app ships 19 locales plus the default configuration.
MAX_STRING_TYPE_CONFIG_BLOCKS = 32

RES_TABLE_TYPE = 0x0201


def java_hash(value: str) -> int:
    result = 0
    for char in value:
        result = (31 * result + ord(char)) & 0xFFFFFFFF
    return result


def read_u32(data: bytes, offset: int) -> tuple[int, int]:
    if offset + 4 > len(data):
        raise ValueError("truncated uint32")
    return struct.unpack_from("<I", data, offset)[0], offset + 4


def read_tl_string(data: bytes, offset: int) -> tuple[bytes, int]:
    if offset >= len(data):
        raise ValueError("truncated TL string header")
    length = data[offset]
    header = 1
    if length == 254:
        if offset + 4 > len(data):
            raise ValueError("truncated long TL string header")
        length = int.from_bytes(data[offset + 1 : offset + 4], "little")
        header = 4
    start = offset + header
    end = start + length
    if end > len(data):
        raise ValueError("truncated TL string body")
    padded_end = end + (-(header + length) % 4)
    if padded_end > len(data):
        raise ValueError("truncated TL string padding")
    return data[start:end], padded_end


def expected_default_string_hashes() -> set[int]:
    values = ROOT / "TMessagesProj" / "src" / "main" / "res" / "values"
    names: set[str] = set()
    files = sorted(values.glob("strings*.xml"))
    if not files:
        raise ValueError("no default strings*.xml files found")
    for path in files:
        root = ET.parse(path).getroot()
        for element in root.findall("string"):
            name = element.get("name")
            if name:
                names.add(name)
    if not names:
        raise ValueError("default string namespace is empty")
    return {java_hash(name) for name in names}


def parse_localization_hashes(data: bytes) -> set[int]:
    count, offset = read_u32(data, 0)
    hashes: set[int] = set()
    for _ in range(count):
        name_hash, offset = read_u32(data, offset)
        _, offset = read_tl_string(data, offset)
        hashes.add(name_hash)
    if offset != len(data):
        raise ValueError("localization_en.bin has trailing bytes")
    if len(hashes) != count:
        raise ValueError("localization_en.bin contains duplicate hashes")
    return hashes


def parse_binding_hashes(data: bytes) -> set[int]:
    count, offset = read_u32(data, 0)
    expected_size = 4 + count * 8
    if len(data) != expected_size:
        raise ValueError(
            f"string_resource_ids.bin size mismatch: {len(data)} != {expected_size}"
        )
    hashes: set[int] = set()
    previous_res_id = -1
    for _ in range(count):
        res_id, offset = read_u32(data, offset)
        name_hash, offset = read_u32(data, offset)
        if res_id <= previous_res_id:
            raise ValueError("string resource IDs are not strictly sorted")
        previous_res_id = res_id
        hashes.add(name_hash)
    return hashes


def validate_emoji_pack(data: bytes) -> None:
    emoji_metadata_length, offset = read_u32(data, 0)
    if emoji_metadata_length == 0 or emoji_metadata_length % 12:
        raise ValueError("invalid emoji metadata length")
    emoji_end = offset + emoji_metadata_length
    if emoji_end + 4 > len(data):
        raise ValueError("truncated emoji metadata")
    mask_metadata_length, mask_start = read_u32(data, emoji_end)
    if mask_metadata_length == 0 or mask_metadata_length % 10:
        raise ValueError("invalid emoji mask metadata length")
    if mask_start + mask_metadata_length > len(data):
        raise ValueError("truncated emoji mask metadata")


def validate_dynamic_settings_localization_assets(localization_hashes: set[int]) -> None:
    # Key-only settings titles intentionally resolve through localization_*.bin,
    # not through dynamically discovered R.string entries. The asset must keep
    # every representative runtime key after APK optimization.
    missing = [
        name
        for name in DYNAMIC_SETTINGS_STRING_SAMPLES.values()
        if java_hash(name) not in localization_hashes
    ]
    if missing:
        raise ValueError(
            "localization assets removed dynamic settings strings: " + ", ".join(missing)
        )


def read_string_pool(data: bytes, offset: int) -> tuple[list[str], int]:
    """Decode a ResStringPool chunk; returns (strings, chunk size)."""
    _, header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
    count, _, flags, strings_start, styles_start = struct.unpack_from(
        "<IIIII", data, offset + 8
    )
    offsets = struct.unpack_from(f"<{count}I", data, offset + header_size)
    utf8 = bool(flags & (1 << 8))
    base = offset + strings_start
    end = offset + (styles_start if styles_start else chunk_size)

    strings = []
    for relative in offsets:
        position = base + relative
        if position >= end:
            strings.append("")
            continue
        if utf8:
            length = data[position]
            if length & 0x80:
                length = ((length & 0x7F) << 8) | data[position + 1]
                position += 2
            else:
                position += 1
            byte_length = data[position]
            if byte_length & 0x80:
                byte_length = ((byte_length & 0x7F) << 8) | data[position + 1]
                position += 2
            else:
                position += 1
            raw = data[position : position + byte_length]
            strings.append(raw.decode("utf-8", "replace"))
        else:
            length = struct.unpack_from("<H", data, position)[0]
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | struct.unpack_from(
                    "<H", data, position + 2
                )[0]
                position += 4
            else:
                position += 2
            raw = data[position : position + length * 2]
            strings.append(raw.decode("utf-16-le", "replace"))
    return strings, chunk_size


def read_string_type_layout(data: bytes) -> tuple[int, int]:
    """Return the `string` type's (widest entryCount, config block count).

    entryCount drives the size of the per-configuration offset table, so this
    is the measurement that catches an id layout pinned across the whole 16-bit
    entry space.
    """
    _, header_size, _ = struct.unpack_from("<HHI", data, 0)
    _, global_size = read_string_pool(data, header_size)
    package_offset = header_size + global_size
    _, package_header_size, package_size = struct.unpack_from(
        "<HHI", data, package_offset
    )
    type_strings_offset = struct.unpack_from("<I", data, package_offset + 268)[0]
    type_names, _ = read_string_pool(data, package_offset + type_strings_offset)

    widest_entry_count = 0
    blocks = 0
    offset = package_offset + package_header_size
    end = package_offset + package_size
    while offset + 8 <= end:
        chunk_type, _, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_size == 0 or offset + chunk_size > end:
            break
        if chunk_type == RES_TABLE_TYPE:
            type_id = data[offset + 8]
            if 0 < type_id <= len(type_names) and type_names[type_id - 1] == "string":
                entry_count = struct.unpack_from("<I", data, offset + 12)[0]
                widest_entry_count = max(widest_entry_count, entry_count)
                blocks += 1
        offset += chunk_size

    if blocks == 0:
        raise ValueError("resources.arsc contains no `string` type")
    return widest_entry_count, blocks


def validate_string_type_layout(data: bytes) -> tuple[int, int]:
    entry_count, blocks = read_string_type_layout(data)
    if entry_count > MAX_STRING_TYPE_ENTRY_COUNT:
        raise ValueError(
            "the `string` type spans "
            f"{entry_count} entries (limit {MAX_STRING_TYPE_ENTRY_COUNT}); every "
            "configuration block then carries a "
            f"{entry_count * 4 // 1024} KB offset table"
        )
    if blocks > MAX_STRING_TYPE_CONFIG_BLOCKS:
        raise ValueError(
            f"the `string` type keeps {blocks} locale blocks "
            f"(limit {MAX_STRING_TYPE_CONFIG_BLOCKS}); localeFilters did not apply"
        )
    return entry_count, blocks


def validate(apk_path: Path) -> None:
    required = {
        "assets/lottie_meta.bin",
        "assets/string_resource_ids.bin",
        "assets/emoji.pack",
        "assets/localization_en.bin",
    }
    with zipfile.ZipFile(apk_path) as apk:
        names = set(apk.namelist())
        missing = sorted(required - names)
        if missing:
            raise ValueError(f"missing generated assets: {', '.join(missing)}")
        if "resources.arsc" not in names:
            raise ValueError("resources.arsc is missing from the APK")
        entry_count, locale_blocks = validate_string_type_layout(
            apk.read("resources.arsc")
        )

        for name in required:
            if apk.getinfo(name).file_size == 0:
                raise ValueError(f"empty generated asset: {name}")

        emoji_info = apk.getinfo("assets/emoji.pack")
        if emoji_info.compress_type != zipfile.ZIP_STORED:
            raise ValueError("assets/emoji.pack must be stored uncompressed for openFd()")

        localization_hashes = parse_localization_hashes(
            apk.read("assets/localization_en.bin")
        )
        validate_dynamic_settings_localization_assets(localization_hashes)
        binding_hashes = parse_binding_hashes(
            apk.read("assets/string_resource_ids.bin")
        )
        expected_hashes = expected_default_string_hashes()
        if localization_hashes != expected_hashes:
            missing_count = len(expected_hashes - localization_hashes)
            extra_count = len(localization_hashes - expected_hashes)
            raise ValueError(
                "localization_en.bin does not match default strings*.xml: "
                f"missing={missing_count}, extra={extra_count}"
            )
        if not expected_hashes.issubset(binding_hashes):
            raise ValueError(
                "string_resource_ids.bin does not cover the default localization namespace"
            )

        lottie = apk.read("assets/lottie_meta.bin")
        if len(lottie) % 8:
            raise ValueError("lottie_meta.bin length is not a sequence of 64-bit entries")
        validate_emoji_pack(apk.read("assets/emoji.pack"))

    print(
        f"PASS generated APK assets: {apk_path.name}; "
        f"localization={len(localization_hashes)}, bindings={len(binding_hashes)}, "
        f"lottie={len(lottie) // 8}, string_entry_count={entry_count}, "
        f"string_locale_blocks={locale_blocks}"
    )


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: verify_generated_apk_assets.py APK [APK ...]", file=sys.stderr)
        return 2
    try:
        for raw_path in sys.argv[1:]:
            validate(Path(raw_path))
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"FATAL: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
