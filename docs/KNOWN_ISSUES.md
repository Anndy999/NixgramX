# Known Issues

| ID | Severity | Summary | Status |
| --- | --- | --- | --- |
| KI-001 | High | Workflow files cannot be pushed without GitHub Actions workflow OAuth/scope | Stale — workflows are on the repo |
| KI-002 | High | APK defaults to owner api_id 39764388; sample id 6 rejected at compile | Done on this branch — my.telegram.org FCM credentials still empty |
| KI-003 | Medium | Icons still temporary NagramX assets | Accepted for Phase 0/1 |
| KI-004 | Medium | Remote-config / updater channel ID is placeholder `0` | Framework in; switch default OFF until you own a channel |
| KI-005 | Medium | Telegram 12.10.1 sync is on `upstream-sync/12.10.1`, not `main` | In progress |
| KI-006 | Low | Legacy resource identifier remains `NagramX` for source compatibility | Fixed — all user-visible values now say `NixgramX` |
| KI-007 | Medium | NixgramX release keystore generated (alias `nixgramx`) | Done — passwords not in git; see SIGNING.md |
| UB-1 | High | Translation bubble width after EN→ZH (replace or keep-original) | Fixed on `fix/user-requested-bugs` |
| UB-2 | High | Save deleted / edit history on full flavor | Tracked |
| UB-3 | High | Channel message menu sometimes does not open (#392) | Tracked |
| UB-4 | Medium | Attach-menu image pinch-zoom jank | Fixed on `fix/user-requested-bugs` — verify on device |
| UB-5 | Medium | 32-bit download boost ineffective (#448) | Needs armeabi-v7a APK |
| KI-008 | Medium | Passkeys need Bitwarden/KeePassDX privileged trust for `app.nixgramx.android`; Google PM fails (not in telegram.org assetlinks) | Documented in PASSKEYS.md | Passkeys (通行密钥) fail on NixgramX: telegram.org assetlinks only lists official package+certs; custom app.nixgramx.android signature → Credential Manager “browser signature mismatch”. Not fixable without Telegram listing us. SUPPORTS_PASSKEYS disabled; login menu explains phone/QR fallback. | Accepted — cryptographic / DAL binding |
| UB-6 | Medium | URL-only updater metadata detected a new version but did not show the original update layout; the popup also dereferenced a missing APK document | Fixed on `fix/update-available-indicator` — URL updates now show the animated entry and open the published update URL |
| UB-7 | High | Translate toggle: message text overlaps translate badge + timestamp | Fixed on `fix/translate-time-misalign` |
| UB-8 | High | Translate toggle: original + translated glyphs overlap (bar + bubbles) | Fixed on `fix/translate-text-overlap`; swap animation on `fix/translate-swap-animation` |
