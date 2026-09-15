# Ghost Mode history audit

## Scope and conclusion

Audited on 2026-09-15, against NixgramX `1907172aaea1d652bfecd28b4f9cfaa5bc8301d2` (`origin/main` and the starting point of `docs/ghost-mode-history`). Investigation only; no feature restoration or production changes.

**Ghost Mode was introduced in NagramX by [3de5c6aa39] on 2025-04-22. NixgramX deliberately disabled it in [1ab5068ed6] on 2026-09-04.** The latter is a policy change, not an accidental Telegram upstream-sync deletion. Much of the implementation, configuration and UI resources remains in the current tree.

NixgramX starts with the root snapshot [e6d49a82a2], whose message identifies NagramX `12.9.2.1260`, [4335a2e589]. The Ghost utility is identical between these two snapshots (`git diff 4335a2e589 e6d49a82a2 -- TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java` is empty). NagramX's earlier commits are historical provenance, not ancestors of NixgramX's independent root commit.

## Search method and boundaries

- Fetched origin and NagramX history with `git fetch --unshallow origin` and then `git fetch --unshallow nagramx`; the final `git rev-parse --is-shallow-repository` returned `false`. The second fetch was necessary: the first left upstream shallow boundaries in this shared repository.
- Searched current text and all fetched refs with case-insensitive `ghost`, `Ghost Mode`, `stealth`, `invisible`, `privacy`, and `幽灵模式`; used commit-message searches and content pickaxe (`git log --all -i -G 'ghost|stealth|invisible|privacy|幽灵模式'`), then file histories and actual `git show` diffs. Current-tree search used `rg -n -i 'ghost|stealth|invisible|privacy|幽灵模式'`; bulk JSON/SVG assets were excluded from that text pass.
- Followed discovered keys: `DisableChatAction`, `disable_chat_action`, `DisableSendReadStories`, `sendReadMessagePackets`, `sendReadStoriesPackets`, `sendOnlinePackets`, `sendUploadProgress`, `sendOfflinePacketAfterOnline`, `markReadAfterSend`, `showGhostInDrawer`, `showGhostModeStatus`, the five `*Locked` keys, `ConfigMigrated`, and `ghostModeReadExclusion_` / `ghostModeTypingExclusion_`.
- Inspected `NaConfig.kt`, `NekoConfig.java`, and `NekoXConfig.java`. The named implementation lives in NekoConfig/Ayu utilities; NaConfig supplies precursor settings and silent-send configuration. NekoXConfig did not establish a separate Ghost implementation.
- “Full history” means all commits reachable from fetched refs, including NagramX's separate `base` branch; unavailable/deleted remote refs and independent AyuGram repository history are outside this audit. Package names alone do not establish the original author of copied code. Submodule-fetch notices for dav1d/ffmpeg/libvpx did not prevent obtaining the superproject history.
- `invisible` mostly finds Android view visibility; `privacy` includes normal Telegram privacy settings and policy text. `ghost.webp` is also used by bookmark UI. These are not capabilities by themselves. Telegram Stories' `StealthModeItem` and `stories_stealth_*` settings are a separate feature: [af312dc007] (and parallel-branch [cbb2d92b7b]) hides the non-premium Stories upsell in `PeerStoriesView.java`, not the Ghost request interceptor.

## Earliest introduction and precursors

| Date | Commit | Evidence |
| --- | --- | --- |
| 2020-06-25 | [54ba1537ee] | Earlier Neko configuration contains `disableChatAction`, persisted as `disable_chat_action`. This is a typing/chat-action precursor, not the named Ghost feature. Later config refactors use `DisableChatAction`. |
| 2023-08-13 | [940b3cd1c8] | Adds `NaConfig.disableSendReadStories` / `DisableSendReadStories` and a Stories-read control. |
| 2025-03-04 | [595ab6669a] | Introduces `SilentMessageByDefault`; old `ChatActivityEnterView.sendMessageInternal` call sites pass the inverse boolean as their notify argument. This existing setting was later exposed on the Ghost page. |
| 2025-04-22 | [3de5c6aa39] | Adds `AyuGhostUtils.java`, `GhostModeActivity.java`, `ayu_ghost.xml`, Ghost strings, NekoConfig's grouped settings/toggle and the `ConnectionsManager.sendRequestInternal` interception hook. This is the earliest named Ghost Mode introduction found. |

The introducing diff removes the old `MessagesController.sendTyping` early return for `disableChatAction` and the old `StoriesController.markStoryAsRead` check for `disableSendReadStories`, centralizing those decisions in the request interceptor. `NekoConfig.checkMigration` reads the old keys and inverts them into `sendUploadProgress` / `sendReadStoriesPackets`, guarded by `ConfigMigrated`. That is direct evidence of continuity rather than an inference from labels.

## Subsequent modifying commits

Dates below are commit author dates. Entries identify feature-relevant changes; large Telegram updates and translation-only commits are not treated as new Ghost capabilities.

| Date | Commit | Verified change |
| --- | --- | --- |
| 2025-04-28 | [585c77b15e] | Adds explicit **Mark as read** message action (`AyuConstants`, `ChatActivity`, `GhostReadMessage` strings) and `markReadOnServer` support. |
| 2025-04-28 | [7b35c65e1c] | Corrects the silent-send notice resource identifier in `GhostModeActivity`. |
| 2025-04-29 | [1a82744947] | Makes interception static; read-on-send uses the storage queue, obtains the dialog's maximum message ID and calls `markReadOnServer`; distinguishes internal reads from explicit interaction when scheduling offline status. |
| 2025-04-29 | [5ecdf4c14f] | Adds read-on-reaction in `SendMessagesHelper`, guarded by `markReadAfterSend && !sendReadMessagePackets`. |
| 2025-04-30 | [a791efc784] | Refactors the adjacent default-silent-send behavior, which the Ghost settings page exposes. |
| 2025-05-02 | [d82289c0ff] | “fix: read mentions #110”: removes `TL_channels_readMessageContents` from the blocked read-request list and moves the status rewrite after read checks. |
| 2025-05-27 / 06-11 | [0fd2fd1d9e], [d6f2c2d18f] | Settings cleanup and replacement of the HeaderCell import; no new network capability in these Ghost-page diffs. |
| 2025-12-25 | [3e8762f714] | Adds `showGhostModeStatus`, its setting/string, and action-bar indicator handling in `DialogsActivity` / `ActionBar`. |
| 2026-01-06 | [a71fb558eb] | Adds per-chat read/typing exceptions, preferences, profile popup, and guards in interceptor/reaction paths; updates manual-read handling. |
| 2026-02-16 | [6cbb4e2743] | Blocks `TL_messages_getMessagesViews` only when `increment` is true; adds peer extraction for its exclusions. |
| 2026-02-22 | [918447ef5b] | Telegram 12.4.0 update: the Ghost activity's own diff only adds its final newline. Not evidence of feature removal. |
| 2026-03-04 | [decc831966] | Manual voice/round-video reads use content-read requests; restores channel content-read classification and adds channel peer extraction. |
| 2026-03-25 / 03-26 / 04-04 | [456878525e], [7f7579a43f], [834d397490] | Dialogs action-menu integration: navigation-bar-dependent shortcut, then only `showGhostInDrawer` gating, then menu spacing. |
| 2026-03-28 | [b9697f0297] | Profile exclusion item also requires `showGhostInDrawer`; the existing broadcast-channel exclusion remains. |
| 2026-09-04 | [e6d49a82a2], [1ab5068ed6] | NixgramX imports the full snapshot, then disables Ghost by policy. |
| 2026-09-04 | [c1497fa73f] | Qualifies the early return as `InterceptResult.Proceed(onCompleteOrig)` and repairs record braces. Preserves disabling; does not restore interception. |

### Removal commits must be separated by branch

- [c645933d9c], 2025-10-05, **“chore: remove Ayu stuff”**, physically deletes `AyuGhostUtils.java`, `GhostModeActivity.java`, Ayu state helpers and removes the NekoConfig settings and network/UI integrations on NagramX **`base`**. `git branch -r --contains c645933d9c` identifies `nagramx/base`; it is not in the imported [4335a2e589] lineage.
- [160a23ef36], 2026-04-25, **“chore: remove ghost item”**, deletes only the Ghost action-menu block in `DialogsActivity.java`, also on **`nagramx/base`**. It is not the cause of removal in NixgramX main. The full upstream snapshot still has this menu block.
- [1ab5068ed6] is the relevant **NixgramX** disabling commit. Its message says “disable Ghost Mode / online-status enhance by policy”; its added `docs/BAN_RISK.md` says the maintainers disabled these in both products to reduce stealth/ToS-sensitive behavior. This reports the recorded project rationale, not an independent legal or ban-risk conclusion.

## What disappeared, and what remains

The [1ab5068ed6] diff is decisive:

1. `AyuGhostUtils.interceptRequest` immediately returns `Proceed(onCompleteOrig)`, before receipt blocking, typing blocking, status rewriting, read-after-send and offline-after-send wrapping.
2. `NekoConfig.isGhostModeActive` immediately returns `false`; `setGhostMode` and `toggleGhostMode` immediately return without changing settings or sending status.
3. `NekoExperimentalSettingsActivity` removes `ghostModeRow` from its row collection unconditionally (the subsequent Save Deleted gating is separately conditional on `BuildConfig.IS_BASE`).
4. `DialogsActivity` adds `false /* NIXGRAMX_POLICY_GHOST_REMOVED */` to the shortcut condition.

These guards remain at the audited current revision, with the compilation correction from [c1497fa73f]. The feature was **disabled and hidden, not comprehensively erased**. Preferences, strings, Ghost activity, popup and helper methods survive.

A narrower residual is important: current `ChatActivity` still has the manual-read action and current `SendMessagesHelper` still has the preference-gated read-on-reaction path, calling `AyuGhostUtils.markReadOnServer` directly. That helper can schedule an offline request when `sendOfflinePacketAfterOnline` is true and the read is not internal. Those paths do not depend on entering the disabled interceptor body. Therefore this audit does **not** claim that every Ghost-related side effect or old preference was purged. No runtime test or remediation was performed.

## Actual historical capabilities, with old code evidence

Unless otherwise stated, paths below refer to the last imported upstream snapshot [4335a2e589], before NixgramX's policy guards. `J/` means `TMessagesProj/src/main/java/`, `K/` means `TMessagesProj/src/main/kotlin/`, and `R/` means `TMessagesProj/src/main/res/`. Method names are exact code locators; pinned source links follow the table.

| Capability | Actual old code path and limits |
| --- | --- |
| Suppress message read receipts | `J/com/radolyn/ayugram/utils/AyuGhostUtils.java`: `interceptRequest` checks `!sendReadMessagePackets`, `isReadMessageRequest`, `AyuState.getAllowReadPacket()` and chat exclusions. Classifies message/channel history, encrypted history, discussion reads, message/channel content reads, and incrementing message-view requests. `sendFakeReadResponse` calls the delegate with `TL_messages_affectedMessages(pts=-1, pts_count=0)` while preventing the network request. Classification changed in [d82289c0ff], [6cbb4e2743], [decc831966]; this final list must not be attributed wholesale to April 2025. |
| Suppress story reads/views | Same utility: `isReadStoriesRequest` recognizes `TL_stories_readStories` and `TL_stories_incrementStoryViews`; `!sendReadStoriesPackets` blocks them unless read-excluded. This is client-side suppression, distinct from Telegram's server-side Premium story stealth feature. |
| Suppress typing/chat-action updates | `interceptRequest` blocks `TL_messages_setTyping` and `TL_messages_setEncryptedTyping` when `sendUploadProgress` is false unless typing-excluded. Despite the key name, the evidence is these request classes; it does not disable file upload transport. |
| Force an offline status update | `interceptRequest` rewrites `TL_account.updateStatus.offline = true` when `sendOnlinePackets` is false. This is outgoing status manipulation, not proof that the account is universally invisible to the server. |
| Go offline after sending | `handleOfflineAfterSend` wraps callbacks for `TL_messages_sendMessage`, `TL_messages_sendMedia`, `TL_messages_sendMultiMedia`; schedules `performStatusRequest(true)` after `OFFLINE_DELAY_MS = 1000`. A typing exception skips wrapping for extracted peers. No success-only check surrounds the scheduling in that callback. |
| Read on sending/interacting | `handleReadAfterSend` queries `MessagesStorage.getDialogMaxMessageId` on its storage queue, then `markReadOnServer(maxId, peer, true)`. Limited to the three send types above, enabled by `markReadAfterSend && !sendReadMessagePackets`, with a read-exclusion guard. `J/org/telegram/messenger/SendMessagesHelper.java` separately calls `markReadOnServer(req.msg_id, req.peer, false)` for reactions, also checking exclusions. |
| Explicit manual read | `J/org/telegram/ui/ChatActivity.java` offers the `GhostReadMessage` action and invokes `markReadOnServer(selectedObject, false)`. The helper selects message/channel history, encrypted history, or voice/round-video content-read requests, enables one read packet through `AyuState`, and processes returned difference parameters. |
| Per-chat exceptions | `AyuGhostPreferences.java` persists `ghostModeReadExclusion_` and `ghostModeTypingExclusion_` plus `Math.abs(chatId)` in Neko preferences; both default false. `extractDialogId` limits which requests can obtain an exception (ordinary `TL_messages_readMessageContents` has no extraction branch). Keys are not account-qualified in this helper. `GhostModeExclusionPopupWrapper.java` supplies default/read/typing choices, exposed by `ProfileActivity`. |
| Group toggle and option locks | `J/tw/nekomimi/nekogram/NekoConfig.java`: `ghostToggleItems`, `setGhostMode`, `isGhostModeActive`, `toggleGhostMode`. Enabling sets the four send flags false and offline-after-send true for unlocked items. Each has a matching `*Locked` boolean; GhostModeActivity long presses lock options and prevent locking all five. This is a computed group state, not a single stored `ghostMode` boolean. |
| Shortcut and status indicator | `GhostModeActivity`, `DrawerLayoutAdapter`/`LaunchActivity` in the introducing diff, and later `DialogsActivity`/`ActionBar` implement UI access and an optional indicator. `showGhostInDrawer` and `showGhostModeStatus` default false. Icons/indicators are presentation, not extra network capabilities. |
| Send without sound | GhostModeActivity exposes `K/xyz/nextalone/nagram/NaConfig.kt`'s `SilentMessageByDefault`. Historical `J/org/telegram/ui/Components/ChatActivityEnterView.java` and attachment/picker send call sites use it to set notification behavior (see [595ab6669a], [a791efc784]). It is adjacent functionality, outside `ghostToggleItems`, and predates Ghost Mode. |

Pinned old sources: [AyuGhostUtils][old-utils], [AyuGhostPreferences][old-prefs], [NekoConfig][old-config], [GhostModeActivity][old-ui], [ConnectionsManager][old-network], [ChatActivity][old-chat], [SendMessagesHelper][old-send], [ProfileActivity][old-profile], [exclusion popup][old-popup].

### Read receipts / typing / online / native message logic

**Read receipts: yes. Typing: yes. Online status: yes. Java message logic: yes. Native C++ message/protocol logic: no Ghost-specific change found.**

The old `ConnectionsManager.sendRequestInternal` calls the Ghost interceptor **before** `NativeByteBuffer` serialization and `native_sendRequest`. Thus Ghost changes which requests reach native transport, but its interception is Java. The introducing and feature-fix diffs modify Java request dispatch, message storage access, reactions and chat UI; searches of `TMessagesProj/jni/tgnet` and `TMessagesProj/jni/TgNetWrapper.cpp` find no Ghost-specific implementation. Secret-chat TL request handling in Java does not by itself demonstrate a native encryption/protocol change. No claim is made that all Telegram traffic passed through these enumerated hooks.

## Involved file inventory

Prefixes `J/`, `K/`, `R/` are defined above. This lists Ghost-related files and directly related precursor/adjacent paths, not every unrelated file in large import/sync commits.

- **Core / preferences:** `J/com/radolyn/ayugram/utils/{AyuGhostUtils,AyuGhostPreferences,AyuState,AyuStateVariable}.java`; `J/com/radolyn/ayugram/AyuConstants.java`; `J/tw/nekomimi/nekogram/NekoConfig.java`; `K/xyz/nextalone/nagram/NaConfig.kt`.
- **Network / message integrations:** `J/org/telegram/tgnet/ConnectionsManager.java`; `J/org/telegram/messenger/{MessagesController,MessagesStorage,SendMessagesHelper}.java`; `J/org/telegram/ui/Stories/StoriesController.java`. Storage supplies the max-message lookup used by read-on-send; it is not itself evidence of a Ghost-specific C++ change.
- **UI:** `J/tw/nekomimi/nekogram/settings/{GhostModeActivity,BaseNekoSettingsActivity,NekoExperimentalSettingsActivity,NekoSettingsActivity}.java`; `J/tw/nekomimi/nekogram/menu/ghostmode/GhostModeExclusionPopupWrapper.java`; `J/org/telegram/ui/{ChatActivity,ProfileActivity,DialogsActivity,LaunchActivity}.java`; `J/org/telegram/ui/Adapters/DrawerLayoutAdapter.java`; `J/org/telegram/ui/ActionBar/ActionBar.java`.
- **Backup / icon mapping:** `J/tw/nekomimi/nekogram/helpers/SettingsBackupHelper.java` retains exclusion prefixes; `J/tw/nekomimi/nekogram/ui/icons/SolarIcons.kt` maps Ghost drawables. These are ancillary persistence/presentation paths.
- **Resources:** introducing `R/drawable/ayu_ghost.xml`; later `R/drawable/ayu_ghost_solar.xml`; `R/values/strings_nax.xml` and translated `R/values-*/strings_nax.xml` (including `values-zh-rCN`, whose `GhostMode` value is `幽灵模式`, and `values-ja-rJP`). Relevant names include `GhostMode`, `GhostEssentialsHeader`, `EnableGhostMode`, `DisableGhostMode`, `GhostModeEnabled`, `GhostModeDisabled`, `GhostModeInDrawer`, `GhostModeStatusIndicator`, `DontSendReadMessagePackets`, `DontReadStoriesPackets`, `DontSendOnlinePackets`, `DontSendUploadProgress`, `SendOfflinePacketAfterOnline`, `MarkReadAfterSend`, `GhostReadMessage`, `GhostModeExcludeRead`, `GhostModeExcludeTyping`, and notice strings. XML contains labels/vector artwork; the settings screen is built in Java, not a Ghost preference XML file.
- **Adjacent silent-send paths:** `J/org/telegram/ui/Components/{ChatActivityEnterView,ChatAttachAlert}.java`, `ContentPreviewViewer`, `DocumentSelectActivity`, `PhotoAlbumPickerActivity`, `PhotoPickerActivity`, `PhotoViewer`, plus chat/config paths in [595ab6669a].

## Validation

Reviewed historical diffs and pinned pre-disable source, checked branch containment to distinguish upstream `base` removals, and compared the imported utility snapshots. Documentation-only validation uses `git diff --check` and a staged file-list check. No Android build/runtime tests were needed or run; behavioral statements describe the old source, not demonstrated server outcomes. No Java/Kotlin/XML production edits, Ghost restoration, or CJK custom-emoji changes are part of this audit.

[0fd2fd1d9e]: https://github.com/risin42/NagramX/commit/0fd2fd1d9e5874f548552fba8e8d50ba243b7670
[160a23ef36]: https://github.com/risin42/NagramX/commit/160a23ef3678c4517c3b350505be65a3b08d1f28
[1a82744947]: https://github.com/risin42/NagramX/commit/1a82744947bb187651694df2391c0bd4bfe4ef14
[1ab5068ed6]: https://github.com/Anndy999/NixgramX/commit/1ab5068ed6bfd8a18d46aaac80b772dfb4e0fd9a
[3de5c6aa39]: https://github.com/risin42/NagramX/commit/3de5c6aa395f66954ae47eddd99abce89b9e9c02
[3e8762f714]: https://github.com/risin42/NagramX/commit/3e8762f71408588643ce721e1d5c8e19a4a079a7
[4335a2e589]: https://github.com/risin42/NagramX/commit/4335a2e589aac4a82f8fceb21b3102c5559db2bf
[456878525e]: https://github.com/risin42/NagramX/commit/456878525e1b1802befeb87e45480cff2255fd87
[54ba1537ee]: https://github.com/risin42/NagramX/commit/54ba1537eeb183a979725d59deee64dd4c5fb834
[585c77b15e]: https://github.com/risin42/NagramX/commit/585c77b15edc579de1e5958853d6d10536faeef0
[595ab6669a]: https://github.com/risin42/NagramX/commit/595ab6669a32317d9979e400fd282e3c26d39b70
[5ecdf4c14f]: https://github.com/risin42/NagramX/commit/5ecdf4c14f8001d70d5531eaf5d55d7c0431c971
[6cbb4e2743]: https://github.com/risin42/NagramX/commit/6cbb4e2743af698616e3da3cc400411440018651
[7b35c65e1c]: https://github.com/risin42/NagramX/commit/7b35c65e1c2ed1b732bf745caffb688f1a9944d8
[7f7579a43f]: https://github.com/risin42/NagramX/commit/7f7579a43f83bee905f283474cf2ea6f7b2fe72b
[834d397490]: https://github.com/risin42/NagramX/commit/834d3974907072b07fe77748c9014bf2fac2c29b
[918447ef5b]: https://github.com/risin42/NagramX/commit/918447ef5bd63e4c9f876bb467b0a2d6071e14e6
[940b3cd1c8]: https://github.com/risin42/NagramX/commit/940b3cd1c8fc5cb0e97f9bd9f45163094280cfc1
[a71fb558eb]: https://github.com/risin42/NagramX/commit/a71fb558eb3f30abfd3566004fced23d41a8d4f4
[a791efc784]: https://github.com/risin42/NagramX/commit/a791efc784db2373a4eb91e7762fcdb2242d8cda
[af312dc007]: https://github.com/risin42/NagramX/commit/af312dc007875236445dc3a9ddf5342b7e2ec6f5
[b9697f0297]: https://github.com/risin42/NagramX/commit/b9697f0297ae759554680ac7ebd202e8cedeac32
[c1497fa73f]: https://github.com/Anndy999/NixgramX/commit/c1497fa73f79dae8c5e497199ed2eaf80400f1eb
[c645933d9c]: https://github.com/risin42/NagramX/commit/c645933d9c0712b15f3238cfa8913e879cf939ac
[cbb2d92b7b]: https://github.com/risin42/NagramX/commit/cbb2d92b7bf2fe5113492b05a63bac427ce0ac9a
[d6f2c2d18f]: https://github.com/risin42/NagramX/commit/d6f2c2d18f2d962e76319ea91a99a5c6dd8a0b2a
[d82289c0ff]: https://github.com/risin42/NagramX/commit/d82289c0ff3ef73b05dbda0490d1b1cabc0640d8
[decc831966]: https://github.com/risin42/NagramX/commit/decc8319661a165bb6f3a6a0704d59740ceb77ab
[e6d49a82a2]: https://github.com/Anndy999/NixgramX/commit/e6d49a82a25980eb87054fd2024bd48f6376050d
[old-utils]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java
[old-prefs]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostPreferences.java
[old-config]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/tw/nekomimi/nekogram/NekoConfig.java
[old-ui]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/GhostModeActivity.java
[old-network]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java
[old-chat]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
[old-send]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/org/telegram/messenger/SendMessagesHelper.java
[old-profile]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java
[old-popup]: https://github.com/risin42/NagramX/blob/4335a2e589aac4a82f8fceb21b3102c5559db2bf/TMessagesProj/src/main/java/tw/nekomimi/nekogram/menu/ghostmode/GhostModeExclusionPopupWrapper.java
