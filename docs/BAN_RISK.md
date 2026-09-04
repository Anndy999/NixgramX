# Ban / ToS Risk

NixgramX is **not** an official Telegram client and offers **no ban immunity**.

## Full build (`app.nixgramx.android`)

Includes local Save Deleted Messages and related enhancements inherited from NagramX. These may conflict with Telegram Terms of Service and can carry account risk.

## `_base` build (`app.nixgramx.android.base`)

Aligned with NagramX `_base` wording:

> ToS-compliant version, without advanced features such as Save Deleted Messages. Release only, no CI updates.

`_base` reduces exposure to those advanced features but is **still unofficial** and is **not** a guarantee against limitations or bans.

## Policy-removed features

Ghost Mode, hide-typing, and online-status hide/enhance are disabled in NixgramX (both products) to reduce stealth/ToS-sensitive behavior. See `FEATURE_INVENTORY.md`.

## Recommendations

- Prefer `_base` if you need fewer ToS-sensitive enhancements.
- Do not use modified clients where prohibited by local law or Telegram ToS for your use case.
- NixgramX maintainers do not provide recovery for banned accounts.
