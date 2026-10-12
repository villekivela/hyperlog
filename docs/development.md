# Development

Machine and device facts for building Hyperlog. Versions live in the Gradle files; this note covers
what the files don't explain.

## Toolchain

- **JDK 21** (Temurin) through mise: `mise use -g java@temurin-21`. Gradle needs
  `JAVA_HOME="$(mise where java)"`, because Java isn't on PATH otherwise.
- **Android SDK** at `~/Android/Sdk`, managed by the `android-sdk` mise tool's `sdkmanager`
  (`~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk`). Installed:
  `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`, `build-tools;37.0.0`,
  `platform-tools`. Each project needs a `local.properties` with `sdk.dir=/home/villekivela/Android/Sdk`
  (git-ignored).
- **compileSdk 37 is required.** Current Compose (BOM 2026.09) and Material 3 1.5 refuse to build
  against 36. targetSdk stays 36.
- **Material 3 Expressive** APIs (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, connected
  button groups) need `material3` 1.5.x. The 2026.09 BOM still pins 1.4.0, so set the version
  explicitly. The prototype builds against 1.5.0-beta01 with AGP 9.4.1, Gradle 9.8.1 and the
  Kotlin Compose plugin 2.4.21.
- **AGP 9 has Kotlin built in.** Apply `com.android.application` and
  `org.jetbrains.kotlin.plugin.compose`, and leave out `org.jetbrains.kotlin.android`.
- **Gradle wrapper:** generate it with `mise exec gradle@<version> -- gradle wrapper --gradle-version <version>`.

## Phone

- The owner's phone connects over USB with debugging authorised. adb is at
  `~/Android/Sdk/platform-tools/adb`, which isn't on PATH by default.
- **Screenshots:** `adb exec-out screencap -p > shot.png`, then `magick shot.png -resize 35% small.png`
  to view it. The screen is 1280 × 2856 px; multiply a 35% screenshot's coordinates by 2.857 for
  `adb shell input tap`.
- **The phone stays awake while plugged in** (`stay_on_while_plugged_in` = 7, the owner's own
  setting). Leave that setting and the lock state to the owner. A locked phone needs the owner to
  unlock it, so check `adb shell dumpsys window | grep isKeyguardShowing` before driving the UI.
- Debug builds install beside other builds through the `.dev` suffix. The legacy app
  (`com.hyperlog.app.alpha`) may still be installed and holds the owner's old data, so leave it in
  place.

## UI work: designer and tester

UI is designed and checked by agents, and the owner judges whole passes on the phone:

1. A **designer** agent works from `docs/design/designer-brief.md`. It critiques its own screenshots
   on the phone, then updates the design note and screenshots.
2. A **tester** agent, an experienced lifter, then runs `docs/design/tester-brief.md` and writes a
   test report.
3. The designer fixes the report. The owner sees the result.

Both agents drive the same phone, so run them one after the other.

## Prototype

The logging-screen prototype is throwaway code on the `prototype/logging-screen` branch, which is
never merged. Its worktree is `~/Work/hyperlog-prototype`; `prototype/run.sh` there builds,
installs and launches it as "Hyperlog prototype". Its decisions live in `SPEC.md` and
`docs/design/logging-screen.md`; read its code only for reference.

## Legacy

- The old app is `villekivela/hyperlog-legacy`, cloned read-only at `~/Work/hyperlog-legacy`. Code
  worth porting: the progression logic in `src/app/utils/progression.ts` and the plate/rounding
  helpers.
- The owner's last full backup is `~/Downloads/hyperlog-full-backup-2026-10-12.json` (format 7.0).
  It's the input for the "Starting weights from the old app" ticket and the source of the exercise
  library seed. It holds personal data, so it stays out of the repo.

## Git

- This repo's `user.email` is set locally to the owner's GitHub no-reply address. GitHub rejects
  pushes that would publish their private address, so new clones need
  `git config user.email 5814559+villekivela@users.noreply.github.com`.
- Releases go through GitHub Releases in the public `villekivela/hyperlog-releases` repo, installed
  with Obtainium. See `SPEC.md`, Distribution, and ticket #22.
