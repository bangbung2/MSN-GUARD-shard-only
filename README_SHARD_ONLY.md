# MSN-GUARD — SHARD-only source variant

This is a source-level modification of the uploaded MSN-GUARD repository.

## Changes made
- The transport rail and saved-protocol resolver expose SHARD only. Other protocol enum values remain in source because shared VPN service/settings code references them; this avoids a brittle mass deletion that could break compilation.
- Adds **Export SHARD accounts** and **Import SHARD accounts** in the SHARD settings section.
- Account backups use JSON format `MSN-GUARD-SHARD-ACCOUNTS` version 1 and contain the cached SHARD node list. The importer validates the marker, format and node parsing before replacing the local list.
- Imported lists remain local until the user explicitly taps **Update node list**; this avoids an automatic refresh immediately overwriting the import.
- ABI splits produce separate APKs for `arm64-v8a`, `armeabi-v7a`, and `x86_64` instead of also producing a large universal APK.
- The SHARD-only package excludes native sidecars used only by the hidden Tor/AnyTLS/Psiphon transports (`libtor.so`, `libobfs4proxy.so`, `libanytls.so`, and `libgojni.so`). The original source/assets remain in the project; only those binaries are omitted from the packaged APK.

## Security note
SHARD account JSON contains proxy node credentials in plain text. Keep the file private. Do not upload it to public storage or send it to untrusted recipients.

## Build
Use a machine with Android Studio/Android SDK, NDK 26.3.11579264, CMake 3.22.1, Java 17+, and the network access required by Gradle and the native build scripts.

From the project root:
```bash
./gradlew assembleRelease
```
On Windows:
```powershell
.\\gradlew.bat assembleRelease
```

The release outputs will be under `app/build/outputs/apk/release/`. With ABI splitting enabled, choose the APK matching the target device. This environment did not have the Android SDK/NDK installed, so the APK has **not** been built or runtime-tested here.

## Important
- This is a source variant, not a compiled or signed APK.
- Preserve the repository's `LICENSE` and AGPL-3.0 obligations when distributing modified builds.
- Test SHARD connect/disconnect, account import/export, manual subscription refresh, DNS, and all three ABI builds before installing on a primary device. Hidden non-SHARD screens should not be used in this build because their native sidecars are intentionally not packaged.
