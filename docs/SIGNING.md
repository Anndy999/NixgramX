# Signing

## Local / CI

- Keystore path: `TMessagesProj/release.keystore` (NixgramX PKCS12, alias `nixgramx`)
- Passwords via `local.properties` or env: `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`
- CI injects base64 `LOCAL_PROPERTIES` (same keys). Do not commit `local.properties`.
- Fingerprints: `docs/IDENTITY.md`

## First-time setup on a compile machine

```bash
cp local.properties.example local.properties
# fill TELEGRAM_APP_ID / TELEGRAM_APP_HASH from https://my.telegram.org/apps
# fill KEYSTORE_PASS / ALIAS_PASS from nixgramx-release-signing.txt (gitignored, given to the owner)
```

`ALIAS_NAME` must stay `nixgramx` unless you rotate.

To encode CI secret:

```bash
base64 -w0 local.properties | gh secret set LOCAL_PROPERTIES --repo Anndy999/NixgramX
```

## Rotate

```bash
FORCE=1 Tools/scripts/generate-nixgramx-keystore.sh
# update docs/IDENTITY.md SHA-256, replace CI LOCAL_PROPERTIES, keep the old keystore if Play/users already trust it
```

Never reuse the NagramX certificate. Never commit passwords.

## Debug / staging / release

All build types in `TMessagesProj/build.gradle` point `signingConfig` at `signingConfigs.release`. The NixgramX keystore must be present before assembling.
