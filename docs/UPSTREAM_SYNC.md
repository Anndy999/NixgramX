# Upstream Sync Process

## Goals

1. Track `DrKLO/Telegram` master latest `update to x.y.z (build)` commit.
2. Preserve NixgramX identity, signing, FCM, Maps, remote-config placeholders.
3. Preserve NagramX feature baseline (4335a2e behavior) adapted onto new Telegram code.
4. Never auto-merge unresolved conflicts into `main` / Stable.

## Automation levels

| Level | Name | Allowed |
| --- | --- | --- |
| L1 | Watch | Detect new Telegram `update to` commits; open issue/report (`upstream-watch.yml`) |
| L2 | Assist | Open sync branch + conflict report; **no** auto-merge |
| L3 | Human adapt | Resolve conflicts, re-apply NixgramX identity + policy |
| L4 | Gate | CI + manual checklist before Stable |

## Current sync attempt: 12.10.1 (L2 Assist)

| Field | Value |
| --- | --- |
| Branch | `upstream-sync/12.10.1` |
| Telegram target | `update to 12.10.1 (7038)` / `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` |
| Baseline for delta | Telegram `update to 12.9.2 (6991)` / `b7561f0c641b` (matches NagramX 12.9.2 era) |
| NixgramX start | `main` @ `9db59a81` (bootstrap from NagramX `4335a2e` + identity cut) |
| Strategy | **Upstream delta patch** (not git merge): histories are unrelated after Day-1 bootstrap. Applied `git diff b7561f0..62b56a07` onto working tree; excluded `TMessagesProj_App*`, `buildSrc`, `Dockerfile`, `README.md`. |
| Submodules | Synced to Telegram 12.10.1 gitlinks (see UPSTREAM_AUDIT.md) |
| Status | **Partial sync — NOT ready for main.** Feature re-adaptation incomplete on high-churn UI files. |

### Procedure used

1. Created `upstream-sync/12.10.1` from latest `main`.
2. Added remotes `telegram` + `nagramx`; fetched `62b56a07`.
3. Generated filtered patch 12.9.2→12.10.1; `git apply --reject`.
4. For reject paths: rebuilt from Telegram 12.10.1 then re-applied NixgramX feature delta (`git diff b7561f0 HEAD -- path`); remaining feature hunks archived under `docs/sync-12.10.1-rejects/`.
5. Identity/build files resolved manually (keep NixgramX AGP/application layout; bump version to 12.10.1/7038; add `:jlatexmath`).
6. Registered + `git submodule update --init` for new third_party / tlottie / jlatexmath submodules.
7. Draft PR only — do not merge.

### Manual sync checklist (remaining L3)

1. Port rejected feature hunks from `docs/sync-12.10.1-rejects/` (esp. `ChatActivity`, `ChatActivityEnterView`, `ChatMessageCell`, `ProfileActivity`, `SendMessagesHelper`, `FilterTabsView`, `VideoPlayer`, `RichMessageLayout`).
2. Re-verify identity files (`APP_PACKAGE`, `BaseRemoteHelper`, `google-services.json`, Maps key, About URLs).
3. Re-verify policy removals still disabled (Ghost Mode UI row removed; hide-typing / online enhance).
4. Build full + `_base` (at least arm64-v8a).
5. Re-export `FEATURE_INVENTORY.md` after adaptations compile.
6. Update this file + `UPSTREAM_AUDIT.md` when L3 closes.

## References

- Telegram: https://github.com/DrKLO/Telegram
- NagramX (archived): https://github.com/risin42/NagramX
- NixgramX: https://github.com/Anndy999/NixgramX
