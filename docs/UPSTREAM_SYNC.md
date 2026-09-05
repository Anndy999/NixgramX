# Upstream Sync Process

## Goals

1. Track `DrKLO/Telegram` master latest `update to x.y.z (build)` commit.
2. Preserve NixgramX identity, signing, FCM, Maps, remote-config placeholders.
3. Preserve NagramX feature baseline (4335a2e behavior) adapted onto new Telegram code.
4. Never auto-merge unresolved conflicts into `beta`, `main`, or Stable.

## Automation levels

| Level | Name | Allowed |
| --- | --- | --- |
| L1 | Watch | Detect new Telegram `update to` commits; open issue/report (`upstream-watch.yml`) |
| L2 | Assist | Open sync branch + conflict report; **no** auto-merge |
| L3 | Human adapt | Resolve conflicts, re-apply NixgramX identity + policy |
| L4 | Gate | CI + manual checklist before entering `beta`, then Stable |

## Current sync attempt: 12.10.1 (L3 Adapt in progress)

| Field | Value |
| --- | --- |
| Branch | `upstream-sync/12.10.1` |
| Telegram target | `update to 12.10.1 (7038)` / `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c` |
| Baseline for delta | Telegram `update to 12.9.2 (6991)` / `b7561f0c641b` (matches NagramX 12.9.2 era) |
| NixgramX start | `main` @ `9db59a81` (bootstrap from NagramX `4335a2e` + identity cut) |
| Strategy | **Upstream delta patch** then **3-way merge** (telegram-12.9.2 base → NixgramX features → telegram-12.10.1) for reject files. Prefer Telegram new APIs; re-weave NaConfig/Neko/Ayu hooks. |
| Submodules | Synced to Telegram 12.10.1 gitlinks (see UPSTREAM_AUDIT.md) |
| Status | **L3 Adapt advanced — still NOT ready for main.** High-risk UI rejects re-merged; no SDK compile verification yet. |

### Procedure used

1. Created `upstream-sync/12.10.1` from latest `main`.
2. Added remotes `telegram` + `nagramx`; fetched `62b56a07`.
3. Generated filtered patch 12.9.2→12.10.1; `git apply --reject`.
4. For reject paths: rebuilt from Telegram 12.10.1 then re-applied NixgramX feature delta (`git diff b7561f0 HEAD -- path`); remaining feature hunks archived under `docs/sync-12.10.1-rejects/`.
5. Identity/build files resolved manually (keep NixgramX AGP/application layout; bump version to 12.10.1/7038; add `:jlatexmath`).
6. Registered + `git submodule update --init` for new third_party / tlottie / jlatexmath submodules.
7. Draft PR only — do not merge.

### L3 Adapt progress (2026-09-04)

**Fully re-merged via 3-way (feature hooks on Telegram 12.10.1):**

- `ChatActivity` (23 conflicts resolved) — double-tap actions, Ayu deleted guards, NaConfig text-style order, message menu (translate/LLM/repeat/bookmark/report/details), TLKeyboardHelper bot long-press, welcome-message mode, reactions hide/show
- `ChatActivityEnterView` — send params + `sendMessageChatArguments`, confirm-all-links, video-record camera popup, `disableNewLines`
- `ChatMessageCell` — KeyboardButtonProto long-press delegate, Ayu deleted / bookmark time strings + welcome empty time
- `SendMessagesHelper` — Pangu/canSendGames + welcomeMessageChatId, `prepareSendingLocation`
- `ProfileActivity`, `FilterTabsView`, `VideoPlayer`, `RichMessageLayout`
- Also: `ActionBar`, `ActionBarMenuItem`, `AudioPlayerAlert`, `ChatAvatarContainer`, `EditTextCaption`, `ChatCustomReactionsEditActivity`, `PeerStoriesView`, `FileLog`, `Utilities`, `ConnectionsManager` (kept DnsTxt/Firebase task classes still referenced)

**Reject archive:** `docs/sync-12.10.1-rejects/` cleared for the above (empty / CMake already on TG linker flags).

### Manual sync checklist (remaining L3)

1. Spot-check any remaining non-reject compile breaks from signature drift (esp. `sendSticker` / `SendMessageChatArguments` call sites outside adapted files).
2. Re-verify identity files (`APP_PACKAGE`, `BaseRemoteHelper`, `google-services.json`, Maps key, About URLs).
3. Re-verify policy removals still disabled (Ghost Mode UI row removed; hide-typing / online enhance).
4. Build full + `_base` (at least arm64-v8a) — **not done** (no Android SDK in this agent environment).
5. Re-export `FEATURE_INVENTORY.md` after adaptations compile.
6. Update this file + `UPSTREAM_AUDIT.md` when L3 closes / before merge to main.

## References

- Telegram: https://github.com/DrKLO/Telegram
- NagramX (archived): https://github.com/risin42/NagramX
- NixgramX: https://github.com/Anndy999/NixgramX
