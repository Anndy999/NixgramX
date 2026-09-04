# User-requested bugfixes

Merge target: `upstream-sync/12.10.1` (not `main`).
This branch starts at `c1497fa` (latest compile tip when opened).

Owner scope (do not expand):

| ID | Item | Status on this line | Merge note |
| --- | --- | --- | --- |
| UB-1 | Translation + LLM translate entry | Hooks re-applied in ChatActivity 12.10.1 3-way merge; needs APK to confirm menu still opens | Keep; do not rewrite translator |
| UB-2 | Save deleted messages / edit history | Full package keeps NaConfig flags; `_base` stays gated by `IS_BASE` | Verify after first full APK |
| UB-3 | Channel tap → message menu (NagramX #392) | Menu path re-merged; still a runtime check | Do not strip feature hooks to make the menu appear |
| UB-4 | Paperclip image / image-link pinch zoom jank | Not patched yet — needs device profile of PhotoViewer / AttachAlert | Code-only guess is unsafe |
| UB-5 | 32-bit download boost (NagramX #448) | Not patched yet — needs `armeabi-v7a` APK | Do not change arm64 download path |

Already on the compile branch (do not duplicate):

- `c1497fa` MessageHelper / send APIs adapted to `SendMessageChatArguments`
- `718bbe91` on old `fix/nagramx-bugs-from-12.10.1` is the same sticker-send class of fix; prefer the compile-branch version

How to use when compiling:

```bash
git checkout upstream-sync/12.10.1
git merge --no-ff fix/user-requested-bugs
```

Or merge the Draft PR that targets `upstream-sync/12.10.1`.
Never merge this into `main` until the 12.10.1 sync itself is accepted.
