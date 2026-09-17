<!-- nixgramx-upstream-sync -->
# Telegram 12.10.2 Upstream Sync

## Baselines

- Official old: Telegram 12.10.1 (7038), `62b56a07ca7e30e39f7fd00a6728d6bbd716ca1c`
- Official target: Telegram 12.10.2 (7086), `163356809fa4bd81e64969700753e0a15664a288`
- Refreshed NixgramX source: `4654c4c006280e58440fe3ea4bfbb915c347ab95`
- PR #86 merge included: `2bbe55dbc0a01a88c9b679aeee1ba87b34f67816`

## Result

- Official changed paths: 17,262
- Initial clean mechanical applications: 17,151
- Initial protected/conflict inventory: 111
- Resolved: 111
- Remaining: 0

The 111 entries were rechecked against the official old tree, official target,
the old PR #85 base, and the refreshed NixgramX base. A protected-path match was
not treated as a source conflict by itself.

## Classification and resolution

### A. Build and identity

| File/group | Official 12.10.2 change | Current Nix change | Resolution | Why |
|---|---|---|---|---|
| `.gitmodules` | Add `td`, `third_party/boringssl`, updated `tlottie`, and `TMessagesProj_Modules/media` | Keeps Nix module layout | Applied official gitlinks and declarations | Required native/Media3 inputs |
| `TMessagesProj/build.gradle` | `org.telegram.build-plugin`, WebKit 1.14.0, Media3 projects | Single application module, Nix version/signing/channel logic | Re-wove plugin and dependencies into the Nix application module | Do not resurrect removed official app variants |
| `build.gradle` | Media module namespace support | Newer Nix AGP/plugin DSL and JLatex asset filtering | Added media namespace handling; retained newer AGP architecture | Preserve working Nix build architecture |
| `buildSrc/build.gradle.kts` | Register Telegram build plugins | File was absent in the single-module Nix layout | Restored plugin registration, adapted Kotlin/AGP classpath for Gradle 9.7.1 | Required by `org.telegram.build-plugin` |
| `settings.gradle` | Register Media3 modules | Nix plugin management and jlatexmath mapping | Added the media parent mapping and official core settings once | Avoid duplicate repository blocks and invalid virtual parent |
| `gradle.properties` | 12.10.2 / 7086 and resource optimization | Nix package/version/updater metadata | Set official 12.10.2/7086, Nix 12.10.2/1318, preserved `app.nixgramx.android` | Identity and monotonic version gate |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13 | Nix already uses Gradle 9.7.1 with AGP 9.3.1 | Kept the newer compatible Nix wrapper | Downgrading would break the current plugin stack |
| `Dockerfile` | Gradle 8.13 image | Locally absent | Restored the official updated Dockerfile | Keeps the official container path available |
| `TMessagesProj/proguard-rules.pro` | Media3 rules and AndroidX `@Keep` | Extensive Nix reflection/stability rules | Removed obsolete ExoPlayer rules, added AndroidX rules, retained Nix rules | Preserve release behavior while completing Media3 conversion |
| Removed `TMessagesProj_App*` variants | Official variants changed | Nix intentionally consolidated into `TMessagesProj` | Kept them removed and ported relevant build changes | Obsolete paths in the Nix architecture |

### B. Source code overlaps

| File | Official 12.10.2 change | Current Nix change | Resolution | Why |
|---|---|---|---|---|
| `Emoji.java` | Packed `EmojiPack` loader | System/custom emoji rendering | Official packed loader plus the Nix system/custom branch | Preserve both new assets and user setting |
| `LocaleController.java` | Generated localization assets/API | Context-locale workaround | Adopted the new localization implementation | Old workaround is superseded by official localization assets |
| `MediaController.java` | Media3 types and `@OptIn` | Nix download/chat/audio hooks | Kept Nix imports/hooks and applied Media3 API/annotation | Feature preservation on the new media API |
| `ResLottieMeta.java` | Runtime `lottie_meta.bin` lookup | Generated 12.10.1 Java table | Adopted official runtime metadata implementation | Old generated table is obsolete |
| `SharedConfig.java` | Import cleanup | Nix proxy helpers | Retained used Nix imports | Required by existing proxy behavior |
| `VoIPPreNotificationService.java` | Android 13+ `VibrationEffect`/`VibrationAttributes` | Legacy ringtone vibration | Adopted official version-aware implementation | Background ringtone correctness |
| `ConnectionsManager.java` | Media3 bandwidth meter and `ProxySettings`/web proxy API | Ghost, DNS/proxy, current-account FCM and diagnostics | Adopted new APIs, retained Nix hooks, added proxy diagnostic to the new entry point | Preserve Nix behavior without the removed signature |
| `ChatActivity.java` | New `RLottieDrawable` constructor | Configurable boost menu | New constructor with the Nix visibility gate | Preserve menu customization |
| `ChatActivityEnterView.java` | Import cleanup | Nix compose/input helpers | Retained imports still used by Nix code | Avoid deleting live hooks |
| `TranscribeButton.java` | Import cleanup | Nix translation/transcription hooks | Retained imports still used by Nix code | Preserve translation features |
| `LaunchActivity.java` | New proxy disable API | Nix proxy persistence | Called the new API and retained `SharedConfig.setProxyEnable(false)` | Keep UI/native state consistent |
| `LoginActivity.java` | New Google resource package | Existing Google login | Adopted official resource IDs | Required by updated Play Services |
| `PeerColorActivity.java` | New button state and Blur3/edge-to-edge implementation | Local premium override and crossfade guard | Adopted official UI, retained `isPremiumOrLocal` and crossfade override | Preserve Nix entitlement and stability behavior |
| `StoriesController.java` | Media3 opt-in | Disable-stories setting | Kept both annotation and `NaConfig` hook | Preserve the user setting |
| `Channel*`, `strings.xml` | Small official API/resource updates | Local additions | Three-way applied clean official hunks | No semantic overlap remained |

### C. Submodule conversion and obsolete source paths

- Old in-tree `com/google/android/exoplayer2` files were removed after migrating
  remaining Nix imports to `androidx.media3`.
- The local WAV edits belonged to the removed ExoPlayer package and had no callers
  after migration; the locked official Media3 implementation is authoritative,
  so duplicate old-package classes were not kept.
- The removed legacy `VoIPController.java` and old Lottie Gradle plugin sources
  were not resurrected; official 12.10.2 replacements are used.
- Nix `MediaStreamingProvider` and secret-media RTMP reflection were migrated to
  the new Media3 packages.

### D. Binary and test-data artifacts

The BoringSSL corpus/certificate files and FFmpeg header in the initial TODO list
had no Nix delta. Their official deletions were applied exactly. These were
false positives caused by protected-name matching such as `nextupdate`, not
source conflicts.

### E. Generated and mechanical paths

Official build-plugin task sources and the TLS test were applied exactly because
their old blobs matched the Nix baseline. Generated 12.10.1 Lottie metadata was
replaced by the official 12.10.2 runtime metadata path.

## NixgramX preservation

- Package: `app.nixgramx.android`
- Drawer account reorder: RecyclerView, ItemTouchHelper, login-time persistence retained
- Drawer theme pipeline: `needSetDayNightTheme`, `lastDayTheme`, `lastDarkTheme`, `DRAWER_THEME_REFRESH` retained
- Navigation diagnostics: `FLOATING_SCROLL` and `MAIN_TABS_VISIBLE` use `NgxDiagnostics`
- FCM current-account diagnostics and PhotoViewer guard tests retained
- Ghost, translation, deleted-message, Glass/Blur3, updater and channel hooks retained

## Verification

- Static/privacy checks: Passed locally
- Stability tests: 57 passed locally
- PR #86 / FCM / PhotoViewer targeted tests: 11 passed locally
- Gradle buildSrc compilation: Passed locally
- Gradle project configuration: reached Android SDK discovery locally; the host has no Android SDK
- Gradle, Java/Kotlin, resources, manifest, lint, native, arm64 APK: CI required
- Device behavior: Not tested for 12.10.2

## Gate

All adaptation TODOs are resolved in source and `pending` is cleared. This is
not release approval: dedicated upstream-sync CI and a 12.10.2 private beta
device smoke test are still mandatory before Public Beta.
