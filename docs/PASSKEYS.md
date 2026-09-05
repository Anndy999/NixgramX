# Passkeys on NixgramX (limited, NagramX-compatible)

NixgramX keeps NagramX-style **limited** passkey support on Android 14+ (`SUPPORTS_PASSKEYS = SDK_INT >= 34`), using `CREDENTIAL_MANAGER_SET_ORIGIN` and Bitwarden/KeePassDX.

## Root constraint

WebAuthn **rpId/origin is `https://telegram.org`**. Google Password Manager + `https://telegram.org/.well-known/assetlinks.json` only list official Telegram packages/certs, so they **fail** on `app.nixgramx.android` + our release keystore. Bitwarden/KeePassDX can work only via **privileged-app trust** with the **correct signing SHA-256 fingerprint**.

## Setup checklist

1. Android 14+
2. Set **Bitwarden** as the system Autofill / preferred credential provider (**not** Google)
3. Bitwarden → Settings → Autofill → **Privileged apps** → add `app.nixgramx.android` with fingerprint:
   - With colons: `52:54:81:59:97:91:41:62:6E:E5:B4:07:B8:4E:E7:0A:33:44:ED:91:29:7F:5F:BE:8E:91:DF:8F:0C:29:A3:1C`
   - Without colons: `52548159979141626EE5B407B84EE70A3344ED91297F5FBE8E91DF8F0C29A31C`
4. Force-stop Bitwarden + NixgramX, then retry
5. First time: login with SMS/code once, then create a **new** passkey inside NixgramX settings (passkeys created in official Telegram / Google PM often will not work here)

## Fallback

Phone / SMS and QR login always work.
