# Ghost Mode COMPAT audit (restore investigation)

**Status:** docs-only. Implementation is **not authorized**. This file inventories what already exists on current `main`, traces git history, and classifies restore portability. Do **not** treat this as a restore plan or a license to re-enable stealth intercepts.

**Scope constraint:** restore from NixgramX’s own git history (inherited NagramX/AyuGram code), **not** a redesign, and **not** expansion beyond old capabilities.

**Tree audited:** `main` at `1907172aaea1d652bfecd28b4f9cfaa5bc8301d2` (Merge PR #58). Branch `docs/ghost-mode-compat` is docs-only.

**Related policy docs (do not contradict):**

- `docs/FEATURE_INVENTORY.md` — Ghost Mode / hide-typing / online-status hide marked `removed-by-policy`
- `docs/IDENTITY.md` — same three items listed as policy removals in both full and `_base`
- `docs/BAN_RISK.md` — stealth/ToS-sensitive behavior called out
- `docs/CODEX_TASK_NIXGRAMX_BOOTSTRAP_V1.2.md` §5 — owner exclusion: no UI switch, no dedicated logic re-introduction, no re-import via later NagramX sync
- `docs/UPSTREAM_AUDIT.md` — UI row remains removed; intercept path retained **disabled**

---

## 1. Inventory on current `main`

Ghost Mode was **not deleted**. Source, strings, drawables, prefs, and most UI still exist. Runtime stealth is **killed by policy guards** (`NIXGRAMX_POLICY_GHOST_REMOVED`) so network intercept and master toggle do nothing.

### 1.1 Core classes (what the code actually does)

| Path | Role on current `main` |
| --- | --- |
| `TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/GhostModeActivity.java` | Settings fragment. Header “Ghost essentials”; collapse-arrow master toggle (`%d/5`); five child checkboxes; long-press “lock” so a checkbox is skipped by `setGhostMode`; plus `markReadAfterSend`, `SilentMessageByDefault` (NaConfig), `showGhostInDrawer`, `showGhostModeStatus`. **Still compiled.** Experimental-settings row that opens it is removed (see §1.6). Master toggle calls `NekoConfig.toggleGhostMode()` which currently no-ops. **Individual checkboxes still write prefs.** |
| `TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java` | Packet interceptor + explicit mark-read helpers. `interceptRequest()` is the stealth core; **first statement is `if (true) return InterceptResult.Proceed(onCompleteOrig)`**. Dead code after that still documents old behavior (block typing, block reads + fake `TL_messages_affectedMessages`, block story reads, force `updateStatus.offline=true`, mark-read-after-send, offline-after-send). Live methods: `markReadOnServer(...)`, `performStatusRequest(Boolean offline)`, dialog-id helpers. |
| `TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostPreferences.java` | Per-chat exclusions in `nkmrcfg`: `ghostModeReadExclusion_<abs(chatId)>`, `ghostModeTypingExclusion_<abs(chatId)>`. In-memory `ConcurrentHashMap` cache. Used by intercept (dead), ChatActivity “Read Message” menu, SendMessagesHelper reaction mark-read, and Profile swipe-back popup. |
| `TMessagesProj/src/main/java/tw/nekomimi/nekogram/menu/ghostmode/GhostModeExclusionPopupWrapper.java` | Profile overflow swipe-back: Default / Exclude Read / Exclude Typing. Writes `AyuGhostPreferences`. Gated by `showGhostInDrawer` in `ProfileActivity.createGhostModeExclusionItem`. |
| `TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuState.java` + `AyuStateVariable.java` | `allowReadPacket` one-shot/count token so a **deliberate** read can pass the interceptor. `getAllowReadPacket()` is true if `sendReadMessagePackets` is true **or** the token fires. Also used by TTL/secret-media “burn” (`ChatActivity` `OPTION_TTL`). Delete-permit APIs in the same class are Save-Deleted, not Ghost. |

### 1.2 Prefs keys (`NekoConfig`, store `nkmrcfg`)

Defaults from `NekoConfig` field initializers (all `configTypeBool`):

| Key | Default | Ghost-toggle polarity | Meaning in old live code |
| --- | --- | --- | --- |
| `sendReadMessagePackets` | `true` | ghost **off** when `true` | Allow `messages.readHistory` / channel / contents / discussion / views-increment |
| `sendReadStoriesPackets` | `true` | ghost **off** when `true` | Allow `stories.readStories` / `stories.incrementStoryViews` |
| `sendOnlinePackets` | `true` | ghost **off** when `true` | Allow `account.updateStatus` with `offline=false` |
| `sendUploadProgress` | `true` | ghost **off** when `true` | Allow `messages.setTyping` / `messages.setEncryptedTyping` |
| `sendOfflinePacketAfterOnline` | `false` | ghost **on** when `true` | After a send (or explicit mark-read), schedule `updateStatus(offline=true)` in 1s |
| `markReadAfterSend` | `true` | **not** in the 5-item master toggle | When reads are suppressed, send/reaction still marks max-id (or reaction `msg_id`) read |
| `showGhostInDrawer` | `false` | UI only | Drawer / MainTabs long-press shortcut; also gates Profile exclusion item |
| `showGhostModeStatus` | `false` | UI only | Title-bar ghost drawable when `isGhostModeActive()` |
| `sendReadMessagePacketsLocked` (and 4 siblings) | `false` | lock | Long-press on a checkbox; locked items are skipped by `setGhostMode`; max 4 locked |

`isGhostModeActive()` (pre-kill-switch): all **unlocked** items must be in “ghost state”. Ghost state is `!item` except `sendOfflinePacketAfterOnline`, which is ghost-state when **true**. `toggleGhostMode()` flips unlocked items, then `performStatusRequest(sendOnlineNow)` so turning ghost **off** can send an online packet immediately.

**Current kill-switch:** `isGhostModeActive()` always returns `false`; `setGhostMode` / `toggleGhostMode` return immediately. Prefs themselves are **not** rewritten to defaults by the kill-switch.

### 1.3 Strings (zh / en)

Canonical English: `TMessagesProj/src/main/res/values/strings_nax.xml`. Simplified Chinese: `values-zh-rCN/strings_nax.xml`. Also present in `values-zh-rTW` and 12 other `strings_nax.xml` locales.

| Name | EN | zh-rCN |
| --- | --- | --- |
| `GhostMode` | Ghost Mode | 幽灵模式 |
| `GhostEssentialsHeader` | Ghost essentials | 幽灵选项 |
| `EnableGhostMode` / `DisableGhostMode` | Enable/Disable Ghost | 启用/禁用幽灵模式 |
| `GhostModeEnabled` / `GhostModeDisabled` | Ghost mode turned on/off | 幽灵模式已启用/已禁用 |
| `GhostModeInDrawer` | Shortcuts | 快捷方式 |
| `GhostModeStatusIndicator` | Ghost Mode Indicator | 幽灵模式指示器 |
| `DontSendReadMessagePackets` | Don't Read Messages | 不显示消息已读 |
| `DontReadStoriesPackets` | Don't Read Stories | 不显示动态已读 |
| `DontSendOnlinePackets` | Don't Send Online | 不发送在线状态 |
| `DontSendUploadProgress` | Don't Send Typing | 不发送输入状态 |
| `SendOfflinePacketAfterOnline` | Go Offline Automatically | 在线后自动离线 |
| `MarkReadAfterSend` | Read on Interact | 动作后设置已读 |
| `GhostModeNotice` | Long tap … prevent it from changing on toggling Ghost Mode | 长按任何选项以阻止其在切换幽灵模式时发生变化。 |
| `MarkReadAfterSendNotice` | Automatically reads message when you send a new one or tap a reaction | 发送新消息或点击表情回应时，自动将消息标记为已读。 |
| `GhostReadMessage` | Read Message | 标记已读 |
| `GhostModeExcludeRead` / `GhostModeExcludeTyping` | Exclude Read / Typing | 排除已读 / 排除输入 |
| `SendWithoutSoundRowNotice` | Automatically sends outgoing messages without sound | 对方将不会收到声音提醒。 |

`SilentMessageByDefault` lives on the same settings screen but is **NaConfig**, not a Ghost intercept flag.

### 1.4 Drawables

**Ghost Mode UI icons:**

- `TMessagesProj/src/main/res/drawable/ayu_ghost.xml` — vector used by drawer, MainTabs, Dialogs title status
- `TMessagesProj/src/main/res/drawable/ayu_ghost_solar.xml` — solar variant; Profile exclusion item; mapped in `SolarIcons.kt`

**Not Ghost Mode** (do not restore-treat as stealth assets):

- `drawable-*/ghost.webp`, `drawable/ghost_solar.xml` — Telegram deleted-account / unknown-peer avatar (`Theme.avatarDrawables[1]`, `BookmarksChatCell`)
- `org.telegram.ui.Components.PacmanAnimation` “ghost” path — game animation
- `ColorParser` `"ghostwhite"`

### 1.5 Hook sites on current `main` (NixgramX-only vs still live)

| File | What the code does | Gated by policy kill-switch? |
| --- | --- | --- |
| `org.telegram.tgnet.ConnectionsManager.sendRequestInternal` | Calls `AyuGhostUtils.interceptRequest` before serialize/`native_sendRequest`. If `blockRequest`, returns without sending. | Intercept body is no-op (`Proceed`). **Call site still present.** |
| `tw.nekomimi.nekogram.NekoConfig` | Master toggle + 13 prefs | `isGhostModeActive` / `setGhostMode` / `toggleGhostMode` forced off |
| `NekoExperimentalSettingsActivity` | Builds `ghostModeRow` then `cellGroup.rows.remove(ghostModeRow)` | Yes (row removed) |
| `DialogsActivity` drawer | Enable/Disable Ghost shortcut + `presentFragment(GhostModeActivity)` | `if (false /* NIXGRAMX_POLICY_GHOST_REMOVED */ && showGhostInDrawer)` |
| `DialogsActivity.updateStatus` | Replaces title right-drawable with `ayu_ghost` when active + `showGhostModeStatus` | Effectively off because `isGhostModeActive()` is false |
| `ActionBar.setTitle` | Allows right-drawable for premium **or** (ghost active + status indicator) | Same |
| `MainTabsActivity.processLongClick` (INDEX_SETTINGS) | If `showGhostInDrawer`, adds Enable/Disable Ghost + opens `GhostModeActivity` / calls `toggleGhostMode()` | **Not gated.** Toggle no-ops; fragment can still open if pref is true |
| `ProfileActivity.createGhostModeExclusionItem` | Swipe-back exclusion menu; skipped for broadcast channels | Only `showGhostInDrawer` (default false). **Not** the `NIXGRAMX_POLICY_GHOST_REMOVED` flag |
| `ChatActivity` context menu | Adds `GhostReadMessage` (`AyuConstants.OPTION_READ_MESSAGE`) when `!sendReadMessagePackets` and not self and not read-excluded | **Not gated.** Menu can appear if pref was imported/toggled. Handler calls `AyuGhostUtils.markReadOnServer(selectedObject, false)` which **does** send a read (uses `AyuState.setAllowReadPacket`) |
| `SendMessagesHelper.sendReaction` | If `markReadAfterSend && !sendReadMessagePackets` and not excluded, `markReadOnServer(req.msg_id, req.peer, false)` | **Not gated.** Independent of intercept |
| `UserCell` / `ProfileActivity` self-status text | If self and (`!sendOnlinePackets` \|\| `sendOfflinePacketAfterOnline`), show `VoipOfflineTitle` locally | **Not gated.** Local UI only; does not hide from others while intercept is no-op |
| `SettingsBackupHelper.importSettings` | Preserves keys with prefixes `ghostModeReadExclusion_` / `ghostModeTypingExclusion_` across import wipe | Not a stealth hook |

`sendRequest` and `sendRequestSync` both funnel through `sendRequestInternal`, so the Java intercept (when live) covers both.

### 1.6 How it is disabled today (not deleted)

Three cooperating guards, all introduced in Day-1 identity cut `1ab5068ed6bfd8a18d46aaac80b772dfb4e0fd9a`:

1. **Master API:** `NekoConfig.isGhostModeActive/setGhostMode/toggleGhostMode` — `if (true) return`.
2. **Network:** `AyuGhostUtils.interceptRequest` — `if (true) return InterceptResult.Proceed(...)`.
3. **Primary UI entry:** experimental settings row removed; Dialogs drawer shortcut forced false.

`c1497fa73f79dae8c5e497199ed2eaf80400f1eb` only changed `Proceed(...)` → `InterceptResult.Proceed(...)` so the kill-switch compiled after Java record nested-name rules.

**Implication:** flipping child checkboxes on `GhostModeActivity` (if reached) still mutates `nkmrcfg`, but **does not** suppress typing/read/online packets. Local self-status text and the “Read Message” menu **can** still follow those prefs.

---

## 2. Git archaeology

NixgramX history **starts** at NagramX import. There is no pre-NagramX Ghost Mode commit on this repo. Lineage below is (A) NixgramX SHAs that exist locally, then (B) NagramX SHAs from `risin42/NagramX` (archived 2026-08-23) that the bootstrap copied, then (C) AyuGram origin. “stealth” string: **no** matching commit subject on NixgramX. “幽灵” exists only as zh strings, not commit messages.

### 2.1 NixgramX (this repo)

| SHA | Date | Subject | Ghost-related files / effect |
| --- | --- | --- | --- |
| `e6d49a82a25980eb87054fd2024bd48f6376050d` | 2026-09-04 | Bootstrap NixgramX from NagramX 12.9.2.1260 (`4335a2e589aac4a82f8fceb21b3102c5559db2bf`) | **Introduce on this repo.** Adds `AyuGhostUtils`, `AyuGhostPreferences`, `GhostModeActivity`, `GhostModeExclusionPopupWrapper`, NekoConfig keys, ConnectionsManager intercept, strings, `ayu_ghost.xml`, hooks in ChatActivity / SendMessagesHelper / DialogsActivity / ProfileActivity / MainTabsActivity / SettingsBackupHelper. Feature is **live** in this commit. |
| `1ab5068ed6bfd8a18d46aaac80b772dfb4e0fd9a` | 2026-09-04 | Day-1 identity cut: … disable Ghost Mode / online-status enhance by policy | **Disable (not delete).** `AyuGhostUtils.interceptRequest` early-return; NekoConfig three methods no-op; Dialogs drawer `if (false /* NIXGRAMX_POLICY_GHOST_REMOVED */)`; `NekoExperimentalSettingsActivity` `rows.remove(ghostModeRow)`. Docs: FEATURE_INVENTORY / IDENTITY / BAN_RISK. |
| `549c9d0a` | 2026-09-04 | sync(telegram): partial apply 12.10.1 (7038) L2 assist | Touches `NekoExperimentalSettingsActivity` / `ConnectionsManager` as part of Telegram overlay. `docs/UPSTREAM_AUDIT.md` records ConnectionsManager intercept as a reject then restored. |
| `103de02d` | 2026-09-04 | adapt(sync): L3 re-merge priority rejects onto Telegram 12.10.1 | Restores `import AyuGhostUtils` + intercept block on `ConnectionsManager` after L2 Telegram base. |
| `c1497fa73f79dae8c5e497199ed2eaf80400f1eb` | 2026-09-04 | fix(build): adapt Neko/Ayu send APIs to Telegram 12.10.1 | `AyuGhostUtils`: qualify `InterceptResult.Proceed`; re-indent record factory methods. **Compile adapt, not behavior restore.** |
| `d8250b0a` | 2026-09-05 | brand: present NixgramX consistently | Touches `NekoConfig.java` (unrelated branding near ghost block). |
| `4d90875e7f93b380a921f817fa1d6f58f9b0fec9` | 2026-09-06 | feat(debug): record network updater … | Touches `ConnectionsManager.java` (debug logging; not Ghost logic). |

No later NixgramX commit **re-enables** Ghost Mode. No commit **removes** the Java/XML sources.

### 2.2 NagramX (source of the tree; not in this git object store)

`GhostModeActivity.java` / `AyuGhostUtils.java` history on `risin42/NagramX` (full SHAs from GitHub):

| SHA | Date | Subject | Files / evolution |
| --- | --- | --- | --- |
| `3de5c6aa395f66954ae47eddd99abce89b9e9c02` | 2025-04-26 | **feat: ghost mode** | **Introduce.** Adds `AyuGhostUtils.java`, `GhostModeActivity.java`, `ayu_ghost.xml`, strings; wires `ConnectionsManager.sendRequestInternal`; retargets `AyuState.getAllowReadPacket` from stub `return true` to `NekoConfig.sendReadMessagePackets`; removes older `NekoConfig.disableChatAction` early-return in `MessagesController.sendTyping` (that symbol is **gone** on current NixgramX). Initial intercept: typing, force-offline `updateStatus`, read-history family, story reads, read-after-send, offline-after-send. |
| `585c77b15edc579de1e5958853d6d10536faeef0` | 2025-04-28 | feat: ability to mark messages as read in ghost mode #109 | ChatActivity `GhostReadMessage` + `markReadOnServer` |
| `7b35c65e1c2ed1b732bf745caffb688f1a9944d8` | 2025-04-28 | chore: minor improvements | `GhostModeActivity` |
| `1a82744947bb187651694df2391c0bd4bfe4ef14` | 2025-05-01 | fix: handle read on interact better | `AyuGhostUtils` mark-read-after-send |
| `0fd2fd1d9e5874f548552fba8e8d50ba243b7670` | 2025-06-01 | refactor: improve N-Settings UI | `GhostModeActivity` |
| `d82289c0ff3ef73b05dbda0490d1b1cabc0640d8` | 2025-05-02 | fix: read mentions #110 | `AyuGhostUtils.isReadMessageRequest`: **drops** `TL_channels_readMessageContents` (current NixgramX has it again; see 6cbb4e2 / decc831). Does **not** add `TL_messages_readMentions`. |
| `d6f2c2d18f2d962e76319ea91a99a5c6dd8a0b2a` | 2025-06-14 | refactor: HeaderCell implementation | `GhostModeActivity` |
| `3e8762f71408588643ce721e1d5c8e19a4a079a7` | 2025-12-25 | feat: ghost mode status indicator | `showGhostModeStatus` + Dialogs/ActionBar drawable |
| `a71fb558eb3f30abfd3566004fced23d41a8d4f4` (PR #252; commit also seen as `38deb36c1e6bf975c12175de39dc1bdedc45ceab`) | 2026-01-07 | feat: add ghost mode exclusion functionality | **Adds `AyuGhostPreferences.java` + `GhostModeExclusionPopupWrapper.java`.** Intercept/ChatActivity/SendMessagesHelper honor per-dialog read/typing exclusions. `markReadOnServer(MessageObject)` overload. |
| `918447ef5bd63e4c9f876bb467b0a2d6071e14e6` | 2026-02-23 | update to 12.4.0 (6506) | `GhostModeActivity` overlay on Telegram bump |
| `6cbb4e2743af698616e3da3cc400411440018651` | 2026-02-17 | fix: block channel view increments | `isReadMessageRequest` includes `TL_messages_getMessagesViews` with `increment` |
| `decc8319661a165bb6f3a6a0704d59740ceb77ab` | 2026-03-05 | fix: mark voice/round messages as read in ghost mode | `markReadOnServer(MessageObject)` uses `channels/messages.readMessageContents` for voice/round |

NixgramX bootstrap `4335a2e` already contains this NagramX evolution (exclusions, view-increment block, voice/round mark-read). Later NagramX-after-1260 ghost commits: **INSUFFICIENT_EVIDENCE** (NagramX archived; not fetched as a git remote here).

### 2.3 AyuGram origin (not in this repo)

Package `com.radolyn.ayugram` and `AyuState` copyright headers point at AyuGram. Public `AyuGram/AyuGram4A` documents “full ghost mode” as a ToS-breaking feature. `AyuConfig.isGhostModeActive()` there is the 4-flag form (`!sendReadPackets && !sendOnlinePackets && !sendUploadProgress && sendOfflinePacketAfterOnline`). NagramX split stories-read into a 5th flag, added locks, exclusions, and the ConnectionsManager intercept (AyuGram historically patched send sites more locally). **INSUFFICIENT_EVIDENCE** for a complete AyuGram→NagramX file-by-file map; not required to restore from **NixgramX** history.

---

## 3. Comparison to DrKLO/Telegram upstream

Upstream sample: `DrKLO/Telegram` `ConnectionsManager.sendRequestInternal` on `master` (read 2026-09-15). NixgramX Telegram base recorded in `docs/upstream-base.json`: `12.10.1` / `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c`.

Official Android Telegram **has no Ghost Mode**. Presence, typing, and read receipts are sent as normal MTProto. Privacy settings (“Last Seen & Online”) are **server-side who-can-see-me**, not client suppression of `updateStatus` / `readHistory` / `setTyping`.

### 3.1 NixgramX-only hooks (absent from DrKLO)

All of §1.1–§1.5. The single network chokepoint vs upstream is:

```java
// ConnectionsManager.sendRequestInternal — NixgramX only
AyuGhostUtils.InterceptResult interceptResult = AyuGhostUtils.interceptRequest(object, onCompleteOrig);
if (interceptResult.blockRequest()) {
    return;
}
final var onComplete = interceptResult.effectiveOnComplete();
```

DrKLO goes straight to `NativeByteBuffer` + `native_sendRequest`.

### 3.2 Which old hooks touch which behaviors

| Behavior | Old live mechanism (dead intercept + still-present side paths) | Official Telegram |
| --- | --- | --- |
| **Read receipts** | Intercept `TL_messages_readHistory`, `TL_messages_readEncryptedHistory`, `TL_messages_readDiscussion`, `TL_messages_readMessageContents`, `TL_channels_readHistory`, `TL_channels_readMessageContents`, `TL_messages_getMessagesViews` with `increment`. Block + `sendFakeReadResponse` (`pts=-1`, `pts_count=0`) unless `AyuState.getAllowReadPacket()` or per-chat read exclusion. | Always send; local pts from real `messages.affectedMessages` / `updates` |
| **Mark-as-read (explicit)** | ChatActivity `OPTION_READ_MESSAGE` → `markReadOnServer(MessageObject)` (history or contents; secret `readEncryptedHistory`). `AyuState.setAllowReadPacket(true, 1)` so intercept would allow that one packet. | Open chat / scroll sends read; no “read this while ghosted” item |
| **Mark-as-read after send / reaction** | Intercept `handleReadAfterSend` on `sendMessage` / `sendMedia` / `sendMultiMedia`: storage `getDialogMaxMessageId` then `markReadOnServer`. Plus `SendMessagesHelper.sendReaction` uses reaction `msg_id`. Gated `markReadAfterSend && !sendReadMessagePackets`. | Sending/reacting does not replace the normal read path |
| **Typing** | Intercept drop `TL_messages_setTyping` / `TL_messages_setEncryptedTyping` unless typing-excluded | Always send while composing (plus upload-progress actions) |
| **Online** | Intercept mutates `TL_account.updateStatus.offline = true` when `!sendOnlinePackets`. `toggleGhostMode` / `markReadOnServer` may `performStatusRequest`. Offline-after-send wraps send callbacks (+1s). Local self subtitle uses `VoipOfflineTitle`. | App resume/pause sends real online/offline; self UI shows Online |
| **Send** | Not blocked. Wrappers only: read-after-send + offline-after-send. `SilentMessageByDefault` on the same screen is silent-notify, not stealth. | N/A |
| **Stories** | Intercept drop `TL_stories_readStories` / `TL_stories_incrementStoryViews` (no fake response) | Always increment/read |

### 3.3 TL methods the old intercept does **not** list

Present on current `main` and sent via `ConnectionsManager.sendRequest`, **not** in `isReadMessageRequest` / `isMessageSendRequest`:

- `TL_messages_readMentions` (`MessagesController.markMentionsAsRead`)
- `TL_messages_readReactions` (`MessagesController.markReactionsAsRead`)
- `TL_messages_forwardMessages`, `sendInlineBotResult`, scheduled-send variants (offline-after-send / read-after-send would miss them)

That is the **old NagramX capability set**, not a missing NixgramX port. Restoring intercept as-is would still leak those. **Do not add them** — that would expand beyond old capabilities.

---

## 4. Compatibility matrix (restore from old/current NixgramX code)

Restore here means: remove the three kill-switches and un-hide the experimental row / drawer, **without** rewriting intercept against new TL.

| Piece | Verdict | Why |
| --- | --- | --- |
| `GhostModeActivity.java` | **Directly portable** | Compiles; uses current `BaseNekoSettingsActivity`, `TextCheckCell2`, `CheckBoxCell`. No Telegram 12.10.1 API break visible in this file. |
| `AyuGhostPreferences.java` + `GhostModeExclusionPopupWrapper.java` | **Directly portable** | Isolated prefs + popup widgets. Profile call sites already compiled. |
| Strings (`strings_nax.xml` en/zh and other locales) | **Directly portable** | Already in tree. |
| `ayu_ghost.xml` / `ayu_ghost_solar.xml` / `SolarIcons.kt` mapping | **Directly portable** | Already in tree. |
| NekoConfig keys + `ghostToggleItems` | **Directly portable as data** | Keys/defaults unchanged since bootstrap. **Not** portable as live API until kill-switches are removed (not authorized). |
| `ConnectionsManager` intercept call site | **Needs re-adapt (already done once)** | Dropped in 12.10.1 L2 overlay; restored in `103de02d`. Future Telegram `sendRequestInternal` signature/integrity changes will need the same 8-line re-insert. Current `master` DrKLO still has the same `sendRequestInternal` shape. |
| `AyuGhostUtils.interceptRequest` body | **Needs re-adapt / verify** | Compiles on 12.10.1 (`TL_account.updateStatus`, `TL_stories.*`, `TL_messages_*`). Nested `InterceptResult` already qualified (`c1497fa7`). Coverage is frozen to the old instanceof list; 12.10.1 still sends `readMentions` / `readReactions` outside that list. Re-adapt means **keep the old list compiling**, not grow it. |
| `AyuGhostUtils.markReadOnServer` | **Needs re-adapt / verify** | Uses current `MessagesController.getInputChannel/getInputPeer`, `processNewDifferenceParams`, encrypted `max_date`. Voice/round contents path is NagramX `decc831`. Secret-chat / forum / monoforum peer mapping: **INSUFFICIENT_EVIDENCE** vs 12.10.1 edge cases. |
| `SendMessagesHelper.sendReaction` ghost block | **Needs re-adapt / verify** | Still present after 12.10.1 send-API work (`c1497fa7` touched other send helpers). Reaction request fields (`msg_id`, `peer`) must remain. |
| `ChatActivity` context-menu insert | **Needs re-adapt / verify** | Menu builder was a high-reject file in `UPSTREAM_AUDIT.md` (`ChatActivity.java` ~42 reject hunks). Ghost item is in tree now; future syncs are likely to drop it again. |
| `DialogsActivity.updateStatus` ghost drawable | **Needs re-adapt / verify** | Depends on `statusDrawable` / `AnimatedEmojiDrawable.WrapSizeDrawable` / folder-title-as-name. Title pipeline moved with 12.10.x; currently compiles. |
| `MainTabsActivity` long-press shortcut | **Needs re-adapt** | Not in original NagramX `3de5c6a` drawer-only wiring; NixgramX/NagramX later UI. **Not policy-gated** today. Restore of drawer + this path must stay consistent; do not invent a third entry. |
| `ActionBar.setTitle` right-drawable or-clause | **Needs re-adapt / verify** | Coupled to premium star / emoji status drawable. Official Telegram has no ghost clause. |
| Fake read response (`pts=-1`) | **Cannot safely restore as-is without risk notes** | Old code. Feeding `processNewDifferenceParams(-1, …)` / callback consumers a synthetic `affectedMessages` can desync local pts or hide errors. This is **old behavior**, not a new design — flag as known hazard, do not “fix” by expanding. |
| Policy kill-switches / FEATURE_INVENTORY | **Cannot safely restore** | Owner exclusion in bootstrap task book: do not re-introduce via sync or “complete NagramX”. BAN_RISK: stealth/ToS. Both products (full and `_base`). Removing kill-switches is a **product-policy** change, not a compile problem. |
| Hide-typing / online-hide as separate features | **Cannot safely restore as extras** | Same intercept; inventory lists them as policy-removed independently. Restoring Ghost **is** restoring those packet classes. Do not add new hide-typing UI. |
| `ShowOnlineStatus` (NaConfig) | **Out of scope** | Different feature (show **others’** online badge in groups). Policy-removed in `NekoChatSettingsActivity` / `AndroidUtil`. Not Ghost Mode. Do not bundle. |
| New TL (mentions/reactions/business/etc.) | **Cannot safely restore by expanding intercept** | Would exceed old capabilities. Leave as leak-parity with NagramX 12.9.2.1260. |
| Native/`sendRequestTyped` bypass | **INSUFFICIENT_EVIDENCE** | Java `sendRequestTyped` calls `sendRequest` → intercept. JNI `native_sendRequest` from other languages: not evidenced. VoIP/push presence: not evidenced as going through this Java intercept. |

---

## 5. Capabilities: confirmed by code vs INSUFFICIENT_EVIDENCE

### 5.1 Confirmed (source on current `main` or cited SHA)

**Old live capabilities (code still present; intercept currently no-op):**

- Suppress outgoing **read history** packets (user, channel, discussion, encrypted, message contents) via ConnectionsManager intercept.
- Suppress **story read / view increment**.
- Suppress **typing / encrypted typing**.
- Rewrite **`account.updateStatus` to offline**.
- After send: optional **max-id read** + delayed **offline** status.
- After reaction: optional **read that msg_id**.
- Per-chat **exclude read** / **exclude typing** (`abs(chatId)` keys).
- Manual **Read Message** context action.
- Five-flag master toggle with long-press **locks** (max 4 locked).
- Drawer / MainTabs shortcut; title **ghost indicator**.
- Settings import **preserves** exclusion keys.
- Default silent send control on the same screen (`SilentMessageByDefault`) — **confirmed UI adjacency**, not a stealth packet.

**Current `main` runtime (kill-switches on):**

- `isGhostModeActive()` is always false → title indicator never shows via that path.
- Intercept never blocks.
- Experimental Ghost row is absent.
- Dialogs drawer Ghost shortcut is compiled out (`if (false)`).
- Child prefs can still be written if `GhostModeActivity` is opened.
- `markReadOnServer` / reaction mark-read / local `VoipOfflineTitle` still follow prefs.

**Policy:**

- Explicitly removed-by-policy on **full and `_base`**.
- Bootstrap task book forbids re-introduction because of later NagramX sync.

### 5.2 INSUFFICIENT_EVIDENCE

- Whether 12.10.1 introduced additional read/typing/online TL methods beyond the list in §3.3 (business bots, todo, monoforum, gifts, live stories, paid messages). Not exhaustively grepped against every `sendRequest(` site.
- Whether `native_sendRequest` / tgnet C++ ever emits typing/read/online without the Java interceptor.
- Whether fake `pts=-1` still leaves chat unread badges / mention counters consistent after 12.10.1 `processNewDifferenceParams`.
- Whether `Math.abs(chatId)` exclusion keys collide across user / chat / encrypted-id spaces.
- Completeness of NagramX history **after** tag `1260` / commit `4335a2e`.
- Runtime QA (no device run in this audit): actual other-party ticks, last-seen, typing indicator, story views.
- AyuGram intercept vs NagramX intercept feature-complete mapping.
- Whether imported `nkmrcfg` from NagramX/AyuGram users already has ghost flags true on NixgramX installs (would affect local UI even while intercept is dead).

---

## 6. Explicit non-goals

- Do **not** implement restore in this PR or any follow-up unless a later owner decision **explicitly** authorizes it.
- Do **not** expand intercept to `readMentions` / `readReactions` / forwards / inline / VoIP “to make Ghost complete”.
- Do **not** redesign UX, add new flags, or port newer NagramX-after-1260 ghost commits without a separate COMPAT pass.
- Do **not** conflate `ShowOnlineStatus`, Pacman ghosts, or Telegram deleted-account `ghost.webp` with Ghost Mode.
- Do **not** change production Java/Kotlin/XML for this investigation.

If restore is ever authorized, the historically faithful change is: delete the three `NIXGRAMX_POLICY_GHOST_REMOVED` guards and stop removing `ghostModeRow` — then **stop**. Anything else is a new feature.
