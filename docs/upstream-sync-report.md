<!-- nixgramx-upstream-sync -->
# Telegram Upstream Sync

## From
- Telegram old base version: 12.10.4
- old commit: c84801762fd5f936c8296ecf09a14a48ebfc4fe4

## To
- Telegram new version: 12.10.5
- new build: 7105
- new commit: dc780e81ed1261c369c27870e8e0999a1eb0b600

## Sync result
- changed files count: 48
- clean applied files: 40
- conflict/adaptation files: 8

## Clean applied files
- TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java
- TMessagesProj/src/main/java/org/telegram/messenger/DatabaseMigrationHelper.java
- TMessagesProj/src/main/java/org/telegram/messenger/DownloadController.java
- TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java
- TMessagesProj/src/main/java/org/telegram/messenger/TelegramMediaSession.java
- TMessagesProj/src/main/java/org/telegram/messenger/UserNameResolver.java
- TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeDescription.java
- TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedFileDrawable.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView2.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraViewBase.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RLottieDrawable.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RoundVideoProgressView.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RoundVideoSettings.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/TelegramRoundVideoUpload.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/VideoTimelineView.java
- TMessagesProj/src/main/java/org/telegram/ui/PhotoViewer.java
- TMessagesProj/src/main/java/org/telegram/ui/RoundVideoSettingsActivity.java
- TMessagesProj/src/main/java/org/telegram/ui/Stories/SelfStoryViewsPage.java
- TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesStorage.java
- TMessagesProj/src/main/java/org/telegram/ui/WebAppDisclaimerAlert.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoCameraController.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoCodecRecorder.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoDiagnostics.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoGlProcessor.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoMp4Writer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoOverlayRenderer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoRemuxer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSession.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSwitchTimingStore.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/BooleanSetting.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/EnumSetting.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/FloatSetting.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/IntSetting.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/LongSetting.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/SettingsPreferences.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/SharedSettings.java
- TMessagesProj/src/main/java/org/telegram/utils/settings/StringSetting.java
- gradle.properties

## Conflict/adaptation files — TODO
- TMessagesProj/src/main/java/org/telegram/messenger/ChannelBoostsController.java — Identity/build/layout policy: explicit adaptation required; docs/upstream-sync-todo/00001.patch
- TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00004.patch
- TMessagesProj/src/main/java/org/telegram/messenger/MusicBrowserService.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00006.patch
- TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00009.patch
- TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00013.patch
- TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00024.patch
- TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java — Three-way conflict or unsupported change: keep local file; adapt upstream delta; docs/upstream-sync-todo/00026.patch
- TMessagesProj/src/main/res/values/strings.xml — Identity/build/layout policy: explicit adaptation required; docs/upstream-sync-todo/00046.patch

## Submodule changes
- None

## Dependency changes
- gradle.properties

## High-risk areas
- TMessagesProj/src/main/java/org/telegram/messenger/DownloadController.java
- TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java
- TMessagesProj/src/main/java/org/telegram/messenger/TelegramMediaSession.java
- TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java
- TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RoundVideoProgressView.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/RoundVideoSettings.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/TelegramRoundVideoUpload.java
- TMessagesProj/src/main/java/org/telegram/ui/Components/VideoTimelineView.java
- TMessagesProj/src/main/java/org/telegram/ui/RoundVideoSettingsActivity.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoCameraController.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoCodecRecorder.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoDiagnostics.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoGlProcessor.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoMp4Writer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoOverlayRenderer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoRemuxer.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSession.java
- TMessagesProj/src/main/java/org/telegram/utils/camera/roundvideo/RoundVideoSwitchTimingStore.java

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

CI run: https://github.com/Anndy999/NixgramX/actions/runs/36196041609 (actual results in job summary).
