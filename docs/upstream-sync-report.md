<!-- nixgramx-upstream-sync -->
# Telegram Upstream Sync

## From
- Telegram old base version: 12.10.5
- old commit: dc780e81ed1261c369c27870e8e0999a1eb0b600

## To
- Telegram new version: 12.10.6
- new build: 7112
- new commit: f2908b14133bbffbf7ab04f641ecb5bfaf533242

## Sync result
- changed files count: 19
- clean applied files: 17
- conflict/adaptation files: 0 (resolved by #168 UI-MEDIA)
- adapted after L2: 2

## Clean applied files
- TMessagesProj/jni/voip/tgcalls/group/GroupInstanceCustomImpl.cpp
- TMessagesProj/jni/voip/webrtc/common_video/h265/h265_bitstream_parser.cc
- TMessagesProj/jni/voip/webrtc/common_video/h265/h265_sps_parser.cc
- TMessagesProj/src/main/java/org/telegram/messenger/DispatchQueueMainThreadSync.java
- TMessagesProj/src/main/java/org/telegram/messenger/MediaDataController.java
- TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java
- TMessagesProj/src/main/java/org/telegram/messenger/Utilities.java
- TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAttachAlertAudioLayout.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/FragmentContextView.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/HintsController.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView2.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RLottieNative.java
- TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java
- TMessagesProj/src/main/java/org/telegram/ui/Stories/DarkThemeResourceProvider.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSession.java
- gradle.properties

## Conflict/adaptation files — RESOLVED (#168)
- TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java — adapted edit-time while isEditing()+edit_date==0 (cell-local; TimeStringHelper untouched); 00007.patch removed
- TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundProviderBuilder.java — Nix if/else already ≡ official parentheses; 00014.patch removed (satisfied, no code change)

## Submodule changes
- None

## Dependency changes
- gradle.properties

## High-risk areas
- TMessagesProj/jni/voip/webrtc/common_video/h265/h265_bitstream_parser.cc
- TMessagesProj/jni/voip/webrtc/common_video/h265/h265_sps_parser.cc
- TMessagesProj/src/main/java/org/telegram/messenger/MediaDataController.java
- TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java
- TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAttachAlertAudioLayout.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSession.java

## NixgramX preservation
- package name preserved: app.nixgramx.android (identity files retained)
- branding preserved: identity files retained; runtime Not tested
- FCM preserved: config retained; runtime Not tested
- signing config preserved: build files / keystores retained
- API credential injection and Telegram channel configuration: retained; integration Not tested
- updater preserved: local hooks retained; Not tested
- translation preserved: no conflict overwrite; Not tested
- deleted-message features preserved: no conflict overwrite; Not tested
- NagramX feature hooks and stability fixes preserved: three-way application only; semantic verification Not tested

## Checks
- Gradle config: Not tested — see CI job summary for actual results
- Java/Kotlin compile: Not tested — see CI job summary for actual results
- resource merge: Not tested — see CI job summary for actual results
- manifest merge: Not tested — see CI job summary for actual results
- arm64 Debug build: Not tested — see CI job summary for actual results
- native build status: Not tested — see CI job summary for actual results
- universal build: Not tested — see CI job summary for actual results

## Manual verification required
- [ ] Login — Not tested
- [ ] Messaging — Not tested
- [ ] Translation — Not tested
- [ ] Deleted Messages — Not tested
- [ ] Media — Not tested
- [ ] Push / FCM — Not tested
- [ ] Proxy / network — Not tested
- [ ] Notification — Not tested
- [ ] Multi-account — Not tested

## Review gate
Mechanical application is not semantic validation. Resolve every TODO using the official new implementation and re-weave local features. Do not revert official code, delete hooks, silence exceptions or comment out functionality to compile.
After resolving TODOs, set docs/upstream-base.json base to the target, add its commit to synced_commits and clear pending. CI rejects pending adaptations.
PR waiting for review. No auto merge, no release, no APK publication.

CI run: https://github.com/Anndy999/NixgramX/actions/runs/36764636098 (actual results in job summary).
