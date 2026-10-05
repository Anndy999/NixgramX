# 12.10.6 NATIVE-VOIP verify (Issue #166)

> **VERIFY-ONLY.** No production Java/JNI code change in this PR. L2 prepare `4faec58d` already applied the official VoIP/H.265 hunks onto `upstream-sync/12.10.6`. This document records the three-way evidence for Codex review.

_Verified: 2026-10-05 (Asia/Shanghai CST). Branch `upstream-task/12.10.6/166-native-voip` from squash `72eb00141` (#165 into `upstream-sync/12.10.6`)._

## Coordinates

| Role | Value |
|---|---|
| Official OLD | `dc780e81ed1261c369c27870e8e0999a1eb0b600` (12.10.5 / 7105) |
| Official NEW | `f2908b14133bbffbf7ab04f641ecb5bfaf533242` (12.10.6 / 7112) |
| Nix tip | `72eb00141d28888bfc347701b4a2073daa853a64` |
| L2 apply | `4faec58d0` — `sync: Telegram 12.10.6 (7112)` |
| Compare | https://github.com/DrKLO/Telegram/compare/dc780e81ed1261c369c27870e8e0999a1eb0b600...f2908b14133bbffbf7ab04f641ecb5bfaf533242 |

Commands used:

```bash
git fetch upstream dc780e81ed1261c369c27870e8e0999a1eb0b600 f2908b14133bbffbf7ab04f641ecb5bfaf533242
git rev-parse OLD:PATH NEW:PATH HEAD:PATH
git diff NEW HEAD -- PATH   # expect empty for H.265; non-empty only for Nix bitrate locals on GroupInstance
git blame -L 3427,3434 -- GroupInstanceCustomImpl.cpp
```

## 1. `GroupInstanceCustomImpl.cpp`

### Blob hashes

| Tree | Blob |
|---|---|
| OLD `dc780e81` | `8dafe042f…` (index from official diff) |
| NEW `f2908b14` | `f06bec428…` |
| Nix `72eb0014` | `cd5be271b…` (Nix bitrate locals diverge from NEW; SSRC guards match) |

`git diff f2908b14 HEAD -- …/GroupInstanceCustomImpl.cpp` is **non-empty** solely due to pre-existing Nix/Neko audio-bitrate locals (see below). The official SSRC hunk is present and unchanged.

### Official hunk (`dc780e81..f2908b14`)

Inserted in `addIncomingVideoChannel` after `_sharedVideoInformation` null-check, before `_incomingVideoChannels.find`:

```cpp
        if (videoInformation.ssrcGroups.empty()) {
            return;
        }
        for (const auto &group : videoInformation.ssrcGroups) {
            if (group.ssrcs.empty()) {
                return;
            }
        }
```

(Official NEW approx L3412–3419; hunk `@@ -3409,6 +3409,14`.)

### Nix lines (tip `72eb0014`)

Same guards at **L3427–3434** (offset from earlier Nix insertions). `git blame`:

- L3427–3434 → `4faec58d0` (github-actions[bot], L2 prepare)
- Surrounding `_sharedVideoInformation` / `find` lines → bootstrap / prior lineage

```cpp
        if (!_sharedVideoInformation) {
            return;
        }
        if (videoInformation.ssrcGroups.empty()) {
            return;
        }
        for (const auto &group : videoInformation.ssrcGroups) {
            if (group.ssrcs.empty()) {
                return;
            }
        }
        if (_incomingVideoChannels.find(VideoChannelId(videoInformation.endpointId)) != _incomingVideoChannels.end()) {
            return;
        }
```

### Nix locals intentionally untouched (non-overlapping)

Present on Nix tip vs official NEW; **not** part of 12.10.6 delta; leave as-is:

- `GroupInstanceCustomImpl::customAudioBitrate` static + init/dtor assign (lineage `#123` / `bacde1659`, bootstrap `58a744515`)
- Dynamic `WebRTC-Audio-Allocation` field-trial string from `_outgoingAudioBitrateKbit` + `static std::string webrtcInitStr`
- Opus `opusPTimeMs` (`32 → 120` else `10`) and bitrate prefs `*_outgoingAudioBitrateKbit * 1024`

**Overlap with SSRC hunk: none.**

## 2. `h265_bitstream_parser.cc`

### Blob / diff

| Check | Result |
|---|---|
| `git diff f2908b14 HEAD -- …/h265_bitstream_parser.cc` | **empty** (Nix ≡ NEW) |
| `git diff dc780e81 HEAD -- …` | equals official OLD→NEW |

Blob NEW = Nix = `0e69f8b61…` (from official index `77f931815..0e69f8b61`).

### Official hunk summary

- `kMaxLongTermReferencePictures = 32`
- Bounds on `num_long_term_sps` / `num_long_term_pics` and `lt_idx_sps[i]`
- Safer `CalcNumPocTotalCurr` (no fixed `used_by_curr_pic_lt[16]`; early `return 0` on overflow / size mismatch)

**Handling:** VERIFY-only — no edit.

## 3. `h265_sps_parser.cc`

### Blob / diff

| Check | Result |
|---|---|
| `git diff f2908b14 HEAD -- …/h265_sps_parser.cc` | **empty** (Nix ≡ NEW) |

Blob NEW = Nix = `fde63cead…` (from official index `6b33d3fd5..fde63cead`).

### Official hunk

Two `RETURN_EMPTY2_ON_FAIL` checks in `ParseShortTermRefPicSet`:

- `delta_idx_minus1 < st_rps_idx`
- `ref_rps_idx < short_term_ref_pic_set.size()` (after computing `ref_rps_idx`)

**Handling:** VERIFY-only — no edit.

## Conclusion

| File | Official applied? | Nix local overlap? | Action |
|---|---|---|---|
| `GroupInstanceCustomImpl.cpp` | Yes (L3427–3434 via `4faec58d`) | Bitrate/opus locals elsewhere — keep | No code change |
| `h265_bitstream_parser.cc` | Yes (Nix ≡ NEW) | None | No code change |
| `h265_sps_parser.cc` | Yes (Nix ≡ NEW) | None | No code change |

Do **not** strip upstream safety checks. Package / signing / `NIXGRAMX_VERSION_*` untouched. Device: **NOT TESTED**.

Refs #166
