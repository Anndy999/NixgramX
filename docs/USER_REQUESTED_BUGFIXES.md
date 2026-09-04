# User-requested bugfixes

Merge target: `upstream-sync/12.10.1` (not `main`).
PR: https://github.com/Anndy999/NixgramX/pull/2

## How to take this into a compile

```bash
git checkout upstream-sync/12.10.1
git merge --no-ff fix/user-requested-bugs
Tools/scripts/apply-user-bugfixes.sh
```

`ChatActivity.java` is ~2.8MB. The double-tap reaction brace fix stays as
`patches/0001-fix-chatactivity-doubletap-reaction-brace.patch` and is applied
by the script above.

## What is already on this branch (no extra patch)

- Translation / LLM: `MessageTrans.kt` + ChatActivity double-tap / menu IDs `nkbtn_translate` / `nkbtn_translate_llm`
- Channel short-tap: `onItemClickListener` → `createMenu(...)`
- View deleted: header item gated by `chatMenuItemViewDeleted` + `enableSaveDeletedMessages`
- Send APIs: `c1497fa` `SendMessageChatArguments` adaptation
- **UB-1 translation bubble width** (replace-original and keep-original):
  CJK uses `BREAK_STRATEGY_SIMPLE`, bubble width shrinks to visual glyph width
  (`MessageObject.makeStaticLayout` / `shrinkWidthToVisualContent`).
  Also covers captions via `TextLayoutBlocks`.
  Toggle chat-bar 翻译→原文 keeps last-line+timestamp space (`getLastLineWidthForTime`)
  so English is not clipped under `01:43`.
- **UB-4 attach-image pinch-zoom jank**:
  skip `invalidateBlur()` during pinch / zoomed pan in `PhotoViewer`; restore
  blur on gesture end.
- **Auto-update framework**: `UpdateHelper` / settings switch, **default OFF**.
  Channel ID still `0` until you own metadata. See `docs/AUTO_UPDATE.md`.
- **Signing**: NixgramX `release.keystore` (alias `nixgramx`). Passwords not in git.
  Compile steps: `docs/COMPILE_RELEASE.md`.

## Still needs APK

| ID | Item |
| --- | --- |
| UB-5 | 32-bit download boost (`armeabi-v7a`) |
