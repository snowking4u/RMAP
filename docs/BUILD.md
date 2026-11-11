# Build and release

## Toolchain

| Component | Version |
| --- | --- |
| JDK | 17 |
| Gradle | 8.9 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 |
| Room | 2.6.1 |
| WorkManager | 2.9.1 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |

`local.properties` must exist and contain the SDK path:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

It is gitignored and must never be committed.

## Debug build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build has application ID `com.homerentmanager.debug` and version suffix `-debug`.

## Unit and Robolectric tests

```bash
./gradlew :app:testDebugUnitTest
```

HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.

See [TESTING.md](TESTING.md) for coverage and manual checks.

## Release signing

Generate a keystore **outside the repository**. Keep it backed up; losing it prevents updates to an installed app.

```bash
mkdir -p ~/.android-keys
keytool -genkeypair \
  -keystore ~/.android-keys/home-rent-manager.jks \
  -alias home-rent-manager \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

Create `keystore.properties` in the repository root (gitignored):

```properties
storeFile=/home/your-user/.android-keys/home-rent-manager.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=home-rent-manager
keyPassword=YOUR_KEY_PASSWORD
```

`app/build.gradle.kts` loads this file if present. It does not create the file and does not contain credentials.

Build the signed release APK:

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Verify the signature and certificate:

```bash
"$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" verify \
  --print-certs app/build/outputs/apk/release/app-release.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

If `keystore.properties` is missing, the release build is unsigned and cannot be installed as a normal release. Never commit the keystore, `keystore.properties`, passwords, or generated APKs.

### Release APK checklist

- [ ] Version code/name updated in `app/build.gradle.kts`.
- [ ] `./gradlew :app:testDebugUnitTest` passes.
- [ ] `./gradlew :app:assembleRelease` succeeds.
- [ ] `apksigner verify --print-certs` shows the expected certificate.
- [ ] Fresh install shows the seven starter rooms and **Add Room** works.
- [ ] Upgrade install preserves data (test with a previous version when changing schema).
- [ ] Manual backup/restore round-trip tested on a real device.
- [ ] Auto Backup settings and quota screen reviewed.
- [ ] No secrets or rental data in `git status`.

## Build configuration notes

- **R8/minification is disabled** (`isMinifyEnabled = false`, `isShrinkResources = false`) to avoid release-only reflection/resource issues. The APK is larger but behavior matches debug. If enabling R8 later, test Room, Compose, WorkManager, and PDF flows again and keep consumer rules.
- Lint does not abort builds (`abortOnError = false`, `checkReleaseBuilds = false`). Run `./gradlew :app:lintDebug` manually before a public release.
- Room schemas are exported to `app/schemas/` and should be committed whenever the schema changes.
- The Gradle wrapper downloads Gradle 8.9 from `services.gradle.org`. On networks where that host is blocked, change `distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` to a trusted mirror, for example:

  ```properties
  distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
  ```

  Verify the mirror before trusting it. Do not commit a mirror URL for a public release without documenting it.

## ARM64 Linux build hosts

Google ships `aapt2` for x86_64 Linux, not Linux ARM64. On an ARM64 host the SDK binary fails with `cannot execute: required file not found` or `Exec format error`. Options:

1. Build on an x86_64 Linux machine, macOS, or Windows (recommended).
2. Run the x86_64 `aapt2` through QEMU user emulation. On Ubuntu ARM64:

   ```bash
   sudo dpkg --add-architecture amd64
   # add an amd64 archive source, then:
   sudo apt-get update
   sudo apt-get install qemu-user-binfmt libc6:amd64 libgcc-s1:amd64

   cat > /opt/aapt2-shim/aapt2 <<'EOF'
   #!/bin/sh
   exec /usr/bin/qemu-x86_64 -cpu max /path/to/aapt2 "$@"
   EOF
   chmod +x /opt/aapt2-shim/aapt2

   ./gradlew :app:assembleDebug \
     -Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2
   ```

   Point the wrapper at the AAPT2 binary extracted by AGP (under `~/.gradle/caches/.../transformed/aapt2-*-linux/aapt2`) or at `$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt2`. The shim must be named `aapt2`.

## Low-memory machines

This project was built on a constrained ARM64 Linux host. If Gradle is killed:

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  -Dkotlin.compiler.execution.strategy=in-process
```

Avoid running multiple Gradle builds simultaneously. `org.gradle.parallel=true` is enabled for normal machines; disable it if memory is tight.

## Reproducibility

- Dependencies are resolved from Google Maven and Maven Central.
- Room schemas are committed.
- No build step downloads rental data, credentials, or external app content.
- The app has no INTERNET permission, so a built APK cannot transmit rental data. WorkManager contributes only local scheduling permissions.
