# NixgramX

Independent long-term fork based on [NagramX](https://github.com/risin42/NagramX) `12.9.2.1260` (`4335a2e`), with Day-1 identity isolation from NagramX.

**Priorities:** (1) track Telegram Android official upstream; (2) maximize stability / bugfixes. Phase 1 is not a large new-feature push.

## Identity

| Product | applicationId | Display name |
| --- | --- | --- |
| Full (default) | `app.nixgramx.android` | Nixgram |
| `_base` | `app.nixgramx.android.base` | Nixgram |

Icons are temporarily still NagramX assets.

NixgramX **cannot** overlay-install over NagramX (different package + signing). Re-login restores cloud chats; local-only data does not auto-migrate. Prefer settings import/export. See [`docs/IDENTITY.md`](docs/IDENTITY.md).

## Risk

Full builds include Save Deleted Messages and related enhancements (Telegram ToS / account risk). `_base` mirrors NagramX’s ToS-friendlier cut (without those advanced features). **No ban immunity.** See [`docs/BAN_RISK.md`](docs/BAN_RISK.md).

Ghost Mode, hide-typing, and online-status hide/enhance are **removed-by-policy**.

## Docs

| Doc | Purpose |
| --- | --- |
| [IDENTITY.md](docs/IDENTITY.md) | Package IDs, migration, replacement paths |
| [UPSTREAM_AUDIT.md](docs/UPSTREAM_AUDIT.md) | Baseline `4335a2e`, Telegram 12.10.1, `a6c7d0a` |
| [UPSTREAM_SYNC.md](docs/UPSTREAM_SYNC.md) | Watch → Assist → Human → Gate |
| [FEATURE_INVENTORY.md](docs/FEATURE_INVENTORY.md) | NaConfig export + statuses |
| [BUILDING.md](docs/BUILDING.md) | Build full / `_base` |
| [SIGNING.md](docs/SIGNING.md) | Keystore |
| [BAN_RISK.md](docs/BAN_RISK.md) | ToS / ban notes |
| [KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md) | Blockers |
| [STABILITY.md](docs/STABILITY.md) | Stability process |
| [TEST_MATRIX.md](docs/TEST_MATRIX.md) | Release tests |
| [BUG_FIX_SOURCES.md](docs/BUG_FIX_SOURCES.md) | Cherry-pick log |
| [LICENSE_AUDIT.md](docs/LICENSE_AUDIT.md) | GPL notes |

## Download

Releases: https://github.com/Anndy999/NixgramX/releases

## Build (short)

```bash
git clone --recursive --shallow-submodules https://github.com/Anndy999/NixgramX.git NixgramX
cd NixgramX
cp local.properties.example local.properties
# fill TELEGRAM_APP_ID / HASH, KEYSTORE_*, sdk.dir
# replace release.keystore, google-services.json, Maps API key

./gradlew TMessagesProj:assembleRelease
NIXGRAMX_BASE=true ./gradlew TMessagesProj:assembleRelease
```

Details: [`docs/BUILDING.md`](docs/BUILDING.md).

## Verify APK

Official NixgramX signing fingerprint: **TBD** (fill after first release keystore). Do not trust NagramX’s certificate for NixgramX builds.

## License

GNU GPL v2 or later — see `LICENSE`.
