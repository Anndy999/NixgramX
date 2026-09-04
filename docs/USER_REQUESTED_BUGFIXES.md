# User-requested bugfixes

Merge target: `upstream-sync/12.10.1` (not `main`).
PR: https://github.com/Anndy999/NixgramX/pull/2

## How to take this into a compile

```bash
git checkout upstream-sync/12.10.1
git merge --no-ff fix/user-requested-bugs
patch -p1 < patches/0001-fix-chatactivity-doubletap-reaction-brace.patch
```

`ChatActivity.java` is ~2.8MB. This agent cannot rewrite the whole file in one GitHub Contents call, so the code change is the patch above. Apply it on the compile machine / Grokbot tree, then build.

## What is already on the compile branch (no extra patch)

- Translation / LLM: `MessageTrans.kt` + ChatActivity double-tap / menu IDs `nkbtn_translate` / `nkbtn_translate_llm`
- Channel short-tap: `onItemClickListener` → `createMenu(...)`
- View deleted: header item gated by `chatMenuItemViewDeleted` + `enableSaveDeletedMessages`
- Send APIs: `c1497fa` `SendMessageChatArguments` adaptation

## Still needs APK

| ID | Item |
| --- | --- |
| UB-4 | Attach-menu image pinch-zoom jank |
| UB-5 | 32-bit download boost (`armeabi-v7a`) |
