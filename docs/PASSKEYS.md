# Passkeys (通行密钥) on NixgramX

## Root cause

Telegram Android passkeys use WebAuthn with RP ID `telegram.org` via Android Credential Manager (`PasskeysController`).

For a native app to assert that web origin, Android requires a Digital Asset Links match at:

`https://telegram.org/.well-known/assetlinks.json`

with relation `delegate_permission/common.get_login_creds` (and typically `handle_all_urls`).

That file currently lists only official packages / signing certs, e.g.:

- `org.telegram.messenger` (+ `.web` / `.beta`)
- `org.thunderdog.challegram` (Telegram X)

NixgramX is `app.nixgramx.android` signed with the NixgramX release keystore. It is **not** listed, so Credential Manager fails with localized errors such as:

> 由于浏览器签名不匹配，通行密钥操作失败

Official API note ([Passkeys in unofficial Telegram apps](https://core.telegram.org/api/passkeys)): unofficial apps cannot use passkeys because the server requires RP ID `telegram.org`.

Upstream `BuildVars` comment: *“works only on official app ids, disable on your forks”*.

## Why we cannot “just fix” it

| Approach | Why it fails |
| --- | --- |
| Keep Credential Manager + `setOrigin(https://telegram.org)` | Needs DAL / privileged-browser association we do not have |
| Change `applicationId` to `org.telegram.messenger` | Forbidden (identity) and still needs official signing cert |
| Reuse official Telegram / NagramX keystore | Forbidden |
| Host our own `assetlinks.json` | We do not control `telegram.org` |
| Bitwarden / KeePassDX trust hacks | May bypass local provider checks; Telegram still expects `telegram.org` origin / official binding |

This is **not** a NixgramX regression vs NagramX 12.10.1 — same cryptographic constraint.

## What we ship

- `BuildVars.SUPPORTS_PASSKEYS = false` (no broken Credential Manager create/get)
- Login “Passkey” menu still visible → clear dialog (EN / zh-CN / zh-TW) pointing to **phone/SMS** or **QR** login
- Signature/origin-looking errors mapped to the same dialog if the path is re-enabled later

## How to verify

1. Install release APK (`app.nixgramx.android`).
2. Open login → ⋮ → Passkey / 通行密钥 → expect explanation dialog, **not** system signature-mismatch toast.
3. Confirm phone-number SMS login and QR login still work.
4. Optional: official Telegram app on same device can still use passkeys for the same account.

## Residual limitation

Passkey **create** and **login** inside NixgramX will not work until Telegram adds `app.nixgramx.android` + our SHA-256 cert to `telegram.org` assetlinks (extremely unlikely) or changes RP policy for unofficial clients.
