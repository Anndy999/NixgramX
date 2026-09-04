# Signing

## Local / CI

- Keystore path: `TMessagesProj/release.keystore`
- Passwords via `local.properties` or env: `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`
- CI may inject base64 `LOCAL_PROPERTIES`

## Requirements

1. Generate a **new** NixgramX upload/release keystore — do **not** reuse NagramX’s certificate.
2. Record SHA-256 fingerprint in `IDENTITY.md` after first official signing.
3. Never commit real keystore passwords or `local.properties`.

## Debug / staging / release

All build types in `TMessagesProj/build.gradle` currently point `signingConfig` at `signingConfigs.release` (NagramX pattern). Ensure the NixgramX keystore is present before assembling.
