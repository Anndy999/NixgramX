#!/usr/bin/env python3
"""Validate generated Telegram assets inside a built APK."""

from __future__ import annotations

import os
import subprocess
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


def find_aapt2() -> Path:
    candidates = []
    android_home = os.environ.get("ANDROID_HOME")
    if android_home:
        build_tools = Path(android_home) / "build-tools"
        candidates.extend(
            path / executable
            for path in sorted(build_tools.glob("*"), reverse=True)
            for executable in ("aapt2", "aapt2.exe")
        )
    candidates.extend(
        Path(path) for path in os.environ.get("PATH", "").split(os.pathsep)
    )
    for candidate in candidates:
        if candidate.name not in {"aapt2", "aapt2.exe"}:
            candidate = candidate / ("aapt2.exe" if os.name == "nt" else "aapt2")
        if candidate.is_file():
            return candidate
    raise ValueError("aapt2 is required to verify final APK resources")


def validate_static_localization_fallback(apk_path: Path) -> None:
    result = subprocess.run(
        [str(find_aapt2()), "dump", "resources", str(apk_path)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode:
        raise ValueError(f"aapt2 could not inspect final APK resources: {result.stderr.strip()}")
    if "string/NekoSettings:" not in result.stdout:
        raise ValueError("final APK removed the static N-Settings localization fallback")


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

    validate_static_localization_fallback(apk_path)

    print(
        f"PASS generated APK assets: {apk_path.name}; "
        f"localization={len(localization_hashes)}, bindings={len(binding_hashes)}, "
        f"lottie={len(lottie) // 8}"
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
