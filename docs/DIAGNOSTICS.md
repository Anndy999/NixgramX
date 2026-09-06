# Local diagnostics

Entry: NixgramX Settings → Diagnostics (existing settings list; Copy / Clear / Close).
A Java uncaught crash is saved before the existing crash-report filter/handler is invoked.
Next unlocked launch offers Copy / Close; the report is retained until Clear and offered once
per process. No native signal, OS kill or ANR capture is claimed.

Storage: Context.getNoBackupFilesDir()/diagnostics/ (typically
/data/user/0/app.nixgramx.android/no_backup/diagnostics/; `_base` has its own package sandbox).
Four events files, each at most 512 KiB; last-crash.txt at most 64 KiB. Total ≤ 2,162,688 bytes
(2 MiB + 64 KiB). Export reads only recent tails (24 KiB) plus last crash (64 KiB) and snapshot.
Repeated Stable warnings are capped to one per event/second; queue bounded to 128 tasks; dropped-task and I/O failure counters are visible. Storage failures
never replace normal functional error handling. No periodic polling, frame/draw/packet logging,
extra network requests or automatic uploads. Timestamp preferences retain only device receipt time.

Stable: fixed WARN and key STATE events; routine translation request/success and Push receipt
events are omitted. Debug/Beta also includes these diagnostics. The receipt timestamp is updated
only in actual FCM/UnifiedPush callbacks, including UnifiedPush wake-up fallback; a token fetch
never updates it. Receipt is not proof of successful parse, notification display or all-message delivery.

Snapshot: provider selection (0=in-app, 1/3=FCM, 2=UnifiedPush), Play Services result, token
present/missing/length, observed token request result, account-slot registeredForPush, last device
receipt UTC epoch time, last Push error code, observed service state, requested native push-connection
state (UNKNOWN until observed; not a socket probe), network state/DC, OS notifications and battery
optimization exemption. Token-fetch/error/service observations are per process; receipt survives restart.

Hooks: token request/result, Telegram registration success/failure, Push receipt/parse error,
service start/stop/conflict, connection-state change, proxy enabled flag, updater check/version/parse/error,
translation request/success/error/timeout (provider number only), FileLoadOperation failure reason.
Updater download/install/verification, decoder/PhotoViewer errors and general request timeout capture
remain uninstrumented; no fabricated success status. Device notification channel-specific settings
and actual push socket liveness still need device validation.

Events accept an enum plus integer only. Never copy exception messages, chat text, token/endpoint,
auth key, phone, API hash/key, proxy address/password, URLs, payload or private media paths.
Crash reports contain version/code/commit, Android/API/manufacturer/model, thread ID and bounded
class/method/line frames/causes; exception messages, custom thread names and suppressed exceptions
are intentionally omitted because they can embed private content. COMMIT_ID must be supplied by
build automation; otherwise commit is explicitly unknown. Copy is a user action to the clipboard.

This does not turn existing Telegram FileLog or existing Crashlytics into sanitized local diagnostics.
The known Push token/payload/key logs are removed, but a full legacy logger privacy audit is pending.
Clear removes diagnostic files; current runtime status and last receipt are separate observations.
