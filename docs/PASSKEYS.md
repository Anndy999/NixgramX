# Passkeys on NixgramX (limited, NagramX-compatible)

NixgramX keeps NagramX-style **limited** passkey support on Android 14+ (`SUPPORTS_PASSKEYS = SDK_INT >= 34`), using `CREDENTIAL_MANAGER_SET_ORIGIN` and Bitwarden/KeePassDX.

## Why Google Password Manager fails

`https://telegram.org/.well-known/assetlinks.json` only lists official Telegram packages and signing certs. NixgramX (`app.nixgramx.android` + our release keystore) is not listed, so Google’s provider reports signature / origin mismatch.

## What works (same as NagramX)

1. Android 14+
2. Bitwarden **or** KeePassDX as the system passkey / credential provider
3. Bitwarden → Settings → Autofill → **Privileged apps** → trust **NixgramX** / `app.nixgramx.android` when prompted
4. Prefer creating the passkey **from this app** (or add this package+fingerprint to an existing vault item). Do not expect an official-Telegram-only item to work unchanged.

Release signing SHA-256 (current keystore):

`52:54:81:59:97:91:41:62:6E:E5:B4:07:B8:4E:E7:0A:33:44:ED:91:29:7F:5F:BE:8E:91:DF:8F:0C:29:A3:1C`

## Fallback

Phone / SMS and QR login always work.
