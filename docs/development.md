# Development

Machine and device facts for building Hyperlog. Versions live in the Gradle files; this note covers
what the files don't explain.

## Toolchain

- **JDK 21** (Temurin) through mise: `mise use -g java@temurin-21`. Gradle needs
  `JAVA_HOME="$(mise where java)"`, because Java isn't on PATH otherwise.
- **Android SDK** at `~/Android/Sdk`, managed by the `android-sdk` mise tool's `sdkmanager`
  (`~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk`). Installed:
  `platforms;android-36`, `platforms;android-37.0`, `build-tools;36.0.0`, `build-tools;37.0.0`,
  `platform-tools`. Each project needs a `local.properties` with `sdk.dir=/home/<you>/Android/Sdk`
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

## Building and testing

- `JAVA_HOME="$(mise where java)" ./gradlew testDebugUnitTest assembleDebug` is what CI
  (`.github/workflows/ci.yml`) runs on every push.
- Versions live in `gradle/libs.versions.toml`. Add new libraries and plugins there.
- **Unit tests run on Robolectric**, so Compose screens and Android resources are tested on the
  JVM without a phone (`createComposeRule` / `createAndroidComposeRule` from
  `androidx.compose.ui.test.junit4.v2`). The default SDK is 36 (`app/src/test/resources/robolectric.properties`);
  use `@Config(sdk = [30])` for an older Android. SDK 36 needs the
  `--add-exports=java.base/jdk.internal.access` JVM argument, already set in `app/build.gradle.kts`.
- Debug builds are `com.hyperlog.app.dev`, labelled "DEV Hyperlog" with an amber DEV-badged icon (`app/src/debug/res`). Launch with
  `adb shell am start -n com.hyperlog.app.dev/com.hyperlog.app.MainActivity`.
- **Icons**: every icon is a Material Symbol (Rounded) vector drawable, added with
  `scripts/add-symbol.sh <name>` and listed in `ui/symbols/Symbol.kt`. Draw them with `SymbolIcon`.
  Never add the Material Symbols variable font; a test fails if `res/font` holds it. `res/font`
  holds only Space Grotesk, the display face, and Inter, the text and number face (licences in
  `docs/licences/`). Inter's static fonts come from its release and then go through
  `scripts/inter-proportional-hyphen.py`, which keeps the hyphen proportional under tabular
  figures; `InterFiguresTest` fails on an unpatched font.

## Phone

- Your phone connects over USB with debugging authorised. adb is at
  `~/Android/Sdk/platform-tools/adb`, which isn't on PATH by default.
- **Screenshots:** `adb exec-out screencap -p > shot.png`, then `magick shot.png -resize 35% small.png`
  to view it. The screen is 1280 × 2856 px; multiply a 35% screenshot's coordinates by 2.857 for
  `adb shell input tap`.
- **Leave the phone's settings and lock state alone**, including any `stay_on_while_plugged_in`
  setting that keeps it awake on USB. A locked phone needs its user to unlock it, so check `adb shell dumpsys window | grep isKeyguardShowing` before driving the UI.
- **Backups:** what's in them, where Auto Backup restores from and the `adb shell bmgr` checks are
  in `docs/backups.md`. Export a manual backup of the dev data before any `pm clear`.
- **Update banner on a debug build:** `.dev` never checks GitHub; launch it with a fake release
  (`--es fake_release 9.9.9` on the `am start` above) to see the banner, and tap it for the
  installer prompt on the build's own APK.
- Debug builds install beside other builds through the `.dev` suffix. The legacy app
  (`com.hyperlog.app.alpha`) may still be installed and holds your old data, so leave it in
  place.

## Emulator

When the phone isn't free, an emulator stands in for it: the same screen, 1280 × 2856 at 480 dpi.

- **Install:** `~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=$HOME/Android/Sdk 'emulator' 'system-images;android-36;google_apis;x86_64'`.
- **Create the AVD:** `avdmanager create avd -n hyperlog -k 'system-images;android-36;google_apis;x86_64' -d pixel_9_pro_xl`,
  then set `hw.lcd.width=1280` and `hw.lcd.height=2856` in `~/.android/avd/hyperlog.avd/config.ini`.
- **Start it headless:** `~/Android/Sdk/emulator/emulator -avd hyperlog -no-window -no-audio -gpu swiftshader_indirect`.
  It comes up as `emulator-5554`.
- **With the phone attached too**, set `ANDROID_SERIAL=emulator-5554` (or `adb -s emulator-5554`)
  for every command, so nothing reaches the phone.
- **Load data** by pushing a database through `run-as` into an installed `.dev` build:
  ```
  adb shell am force-stop com.hyperlog.app.dev
  adb push hyperlog.db /data/local/tmp/hyperlog.db
  adb shell run-as com.hyperlog.app.dev sh -c 'rm -f databases/hyperlog.db-wal databases/hyperlog.db-shm; cp /data/local/tmp/hyperlog.db databases/hyperlog.db'
  adb shell rm /data/local/tmp/hyperlog.db
  ```
  An older database migrates on the next launch, as it would on the phone.
- The emulator's settings are yours to change: `adb shell settings put system font_scale 1.15`
  matches the phone's text size, and `adb shell cmd uimode night yes|no` switches dark and light.

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
- Your legacy backup (the old app's full-backup JSON export, format 7.0) is the input for the "Starting weights from the old app" ticket and the source of the exercise
  library seed. It holds personal data, so it stays out of the repo.

## Git

- Set this repo's `user.email` locally to your GitHub no-reply address. GitHub rejects pushes
  that would publish a private address, so new clones need
  `git config user.email <id>+<username>@users.noreply.github.com`.
- Releases are GitHub Releases in this repo (`villekivela/hyperlog`, public); the app checks for
  them itself. See `SPEC.md`, Distribution, and "Releases" below.

## Releases

- **Cut a release** from the commit to ship: `git tag v0.2.0 && git push origin v0.2.0`. The
  `Release` workflow (`.github/workflows/release.yml`) builds with `-PhyperlogVersion=<tag>`,
  signs, checks the signature with `apksigner verify --print-certs`, and publishes a normal release
  on the tag with one asset, `hyperlog-0.2.0.apk`. The tag must be `vX.Y.Z` (minor and patch
  below 100) and higher than every earlier one; never move or reuse a tag.
- **Dry run:** `gh workflow run release.yml --ref <branch> -f version=v0.0.1` does everything but
  publish and uploads the signed APK as the `hyperlog-apk` workflow artifact.
- **The key** is a PKCS12 keystore (alias `hyperlog`, one password for store and key). It lives
  outside the repo, backed up by the owner, and in the repo's Actions secrets as
  `HYPERLOG_KEYSTORE_BASE64` (`base64 -w0 hyperlog.p12`) and `HYPERLOG_KEYSTORE_PASSWORD`. Losing
  it means no more updates over the installed app. Its certificate (`CN=Hyperlog`) has SHA-256
  `e4a80af631f1a0b9df8a3b12d845e9b735805d0ca1f701d0c1becd873cb6652b`; every release APK must
  print that in `apksigner verify --print-certs`.
- **Signing a local release build:** set `HYPERLOG_KEYSTORE_FILE` and `HYPERLOG_KEYSTORE_PASSWORD`
  as env vars or Gradle properties (`HYPERLOG_KEY_ALIAS` defaults to `hyperlog`). Without them,
  `assembleRelease` gives `app-release-unsigned.apk`.
- **First install on a phone:** open `https://github.com/villekivela/hyperlog/releases/latest` in
  the phone's browser, download the APK and open it; Android asks once to allow installs from the
  browser. Uninstall the legacy app first when switching (its signature differs). Later versions
  arrive through the in-app banner.
