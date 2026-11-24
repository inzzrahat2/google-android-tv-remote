# Build

Requirements: JDK 17+ and Android SDK platform 37. The Gradle wrapper (9.8.2) downloads everything else.

```
echo "sdk.dir=/path/to/android/sdk" > local.properties
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # minified; signed only if keystore.properties exists
```

## Release signing
Create `keystore.properties` in the project root. It is git-ignored and must never be committed:

```
storeFile=/absolute/path/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Toolchain (pinned in gradle/libs.versions.toml)
AGP 9.4.1 (built-in Kotlin) · Kotlin 2.4.21 · Compose BOM 2026.09.00 · compileSdk/targetSdk 37 · minSdk 26.

## Notes
- Debug builds install as `com.techport.tvremote.debug` next to a release build and include the
  Simulated TV.
- `gradle.properties` sets `-Xmx3g` for Gradle; lower it on machines with little RAM.
