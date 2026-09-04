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

## Day-1 status

- Watch workflow file prepared locally (see `.github/workflows/upstream-watch.yml`) — **not pushed** until workflow OAuth/scope granted.
- No L2+ sync executed in this cut.

## Manual sync checklist (when performing)

1. Record Telegram target SHA / version / build / submodule state.
2. Merge or cherry-pick Telegram changes onto NixgramX working branch.
3. Re-verify identity files (`APP_PACKAGE`, `BaseRemoteHelper`, `google-services.json`, Maps key, About URLs).
4. Re-verify policy removals still disabled.
5. Build full + `_base` (at least arm64-v8a).
6. Update `UPSTREAM_AUDIT.md` and release notes.

## References

- Telegram: https://github.com/DrKLO/Telegram  
- NagramX (archived): https://github.com/risin42/NagramX  
- NixgramX: https://github.com/Anndy999/NixgramX  
