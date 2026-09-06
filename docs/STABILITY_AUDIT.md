# Stability Phase v1 source audit

Baseline main e1fe2dca46; no device/heap trace available. Changes and final build evidence are in
FINAL_REPORT.md. This is a bounded source review, not a claim that all lifetime paths are correct.

## Data correctness

AyuMessagesController.deleteCurrent and EditedMessageDao bulk-delete were missing userId filters.
A host SQLite fixture reproduces cross-account deletion for shared channel IDs in the old queries.
All corresponding DAO signatures and callers now carry userId; no schema migration or sorting change.
Queries selecting media for clear also carry userId. Regression covers two accounts and multiple records.

Remaining observations: DeletedMessage uses auto-increment fakeId and non-unique account/dialog/message
indexes. onMessageDeletedInner checks exists separately from insert; concurrent outcomes require Room
stress tests before adding constraints/migrations. AyuData has migrations 21→26 and destructive downgrade
fallback; downgrade recovery needs fixtures and is not claimed safe. allowMainThreadQueries and synchronous
clear are potential ANR paths for large histories. Bulk delete fetches media references after record deletion;
cleanup can be missed. Shared media ownership/reference counting, reaction cleanup, orphan handling,
restart recovery, multi-edit concurrency, migration and disk-growth tests remain NOT TESTED. Do not blindly
remove media or add uniqueness constraints to fix these findings.

## Channel menu

Reviewed ChatActivity.fillMessageMenu and entry paths around message selection/primary grouped message.
Menu uses selectedObject/selectedObjectGroup plus permissions, membership, ephemeral/sponsored/deleted,
scheduled and channel posting restrictions. Lack of posting permission disables chat actions; separate
copy/translate/menu entries must be evaluated with a reproducer. No evidence identifies the intermittent
failure's root cause. No always-true guard, role bypass or generic null-return patch applied.
Collect exact channel role, ordinary/deleted/grouped message type, selection state, invocation sequence
and sanitized stack if any; reproduce with reply/forward/copy/translate and long-press after account switch.

## Lifecycle and performance

Global registration/removal search produced 2,998 matching source lines (Java/Kotlin under main/java),
including addObserver/addListener/registerReceiver/addCallback/layout/text watchers and removals.
Focused review: ChatActivity/DialogsActivity observer teardown, PhotoViewer destroy/blur/watchers/animators,
ChatAttachAlert onDestroy/mention adapter, ChatAvatarContainer attach/detach observers, SharedMediaLayout
registrations, Translator AppScope callbacks. Existing #27 already includes the Exteraless fixes; no additional
leak proven. Animator listeners need lifetime analysis, not a one-to-one text-count rule.
Stories, WebView and media player paths were included in the global search but not exhaustively traced.
Diagnostics asynchronous UI callbacks use WeakReference<Activity>; no retained Activity in the log queue.

No measured hot-path performance patch. ChatMessageCell drawing, RecyclerView binding, ImageLoader,
FileLoader/FileLoadOperation, MessagesStorage, deleted DB, translation layout, Blur3 and notification
processing need frame/CPU/I/O/heap captures. Cache sizes, thread counts and DB synchronization remain
unchanged. For each future performance patch record bottleneck/reproduction/hot path, measured benefit,
memory/low-end impact, data integrity and current official-upstream comparison.

## Push

Existing FCM hybrid MTProto connection is preserved. External provider availability previously returned
before stopping a stale local service; cleanup now reaches the stop/cancel branch and service destruction
cannot restart itself while an external provider is available. The FCM helper now only runs for FCM modes,
not UnifiedPush on devices that happen to have GMS. Both service and broadcast alarm identities are canceled,
including after process restart. UnifiedPush availability still follows the existing distributor-presence
policy; installed-but-unregistered distributor and provider-transition timing need real-device tests.
The legacy fallback alarm targets a Service via getBroadcast; not replaced without Android/ROM validation.
AppStartReceiver delegates to the shared guard. No new Push feature or plugin logic imported.
