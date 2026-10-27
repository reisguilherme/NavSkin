# Portable Android toolchain

The Windows toolchain lives in the ignored `.runtime/` directory. It does not install software globally, edit the permanent PATH, install USB drivers, or change Windows virtualization settings. Run commands from the repository root using PowerShell.

```powershell
.\scripts\android-toolchain.ps1 -Action Bootstrap
.\scripts\android-toolchain.ps1 -Action Doctor
```

Bootstrap downloads the official Windows x64 ZIP archives below, checks their published SHA-256 values, and extracts them locally.

| Component | Pinned version | SHA-256 |
| --- | --- | --- |
| Eclipse Temurin JDK | 17.0.20.1+1 | `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0` |
| Gradle | 8.13 | `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78` |
| Android command-line tools | 15859902 | `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` |

Sources: [Eclipse Adoptium release API](https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse), [Temurin release](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20.1%2B1), [Gradle 8.13 checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256), [Android command-line tools](https://developer.android.com/studio#command-tools).

## SDK packages and licenses

Google's SDK packages require accepting the [Android SDK license](https://developer.android.com/studio/terms). No acceptance hashes or automatic answers are included in the script. The person running the following command must review and answer the interactive prompts.

```powershell
.\scripts\android-toolchain.ps1 -Action InstallSdk
```

The default package list is `platform-tools`, `platforms;android-36`, `build-tools;35.0.0`, and `extras;google;auto` (Android Auto Desktop Head Unit). The package list can be overridden explicitly:

```powershell
.\scripts\android-toolchain.ps1 -Action InstallSdk -SdkPackages @('platform-tools', 'platforms;android-36', 'build-tools;35.0.0')
```

To review other pending SDK licenses interactively, use `-Action Licenses`. The new command-line tools print a deprecation notice for `sdkmanager`; it remains included and functional in the pinned package.

## Build and inspect

```powershell
.\scripts\android-toolchain.ps1 -Action Gradle assembleDebug
.\scripts\android-toolchain.ps1 -Action Gradle testDebugUnitTest
.\scripts\android-toolchain.ps1 -Action Devices
```

The default Android project directory is `android/`; use `-ProjectPath` to select another directory. Java, the Android SDK, the Gradle cache, and the Android build user directory are configured only for the invoked process. Gradle uses `.runtime/gradle-user-home`; the default cache outside this workspace is restricted in this environment.

`Devices` runs only `adb devices -l`. Installing an APK, launching an app on an existing device, or forwarding its ports requires a separate deliberate command.

## Emulator and Android Auto

An emulator is optional and has not been installed by Bootstrap. The following interactive SDK command can obtain an official emulator and Google APIs image after reviewing the relevant license:

```powershell
.\scripts\android-toolchain.ps1 -Action InstallSdk -SdkPackages @('emulator', 'system-images;android-36;google_apis;x86_64')
```

Emulator acceleration depends on the host's existing virtualization support. This script does not enable Windows features or install a hypervisor driver. No Android Auto login or Play service restrictions are bypassed.

The [official Desktop Head Unit guide](https://developer.android.com/training/cars/testing/dhu) describes enabling Android Auto developer mode and starting its head unit server on a connected phone. Once the phone is configured, the required port forwarding and DHU launch are separate actions. The `Dhu` action merely runs the locally installed executable with the provided arguments.

## Initial environment audit, 2026-10-26

- No Java, Gradle, Android SDK, ADB, or emulator was present on PATH or in the conventional installation locations checked.
- The existing `.android/` directory contained ADB key files, but no SDK installation or accepted SDK license files were found.
- Windows' present-device inventory did not show a device matching Android, ADB, Pixel, Samsung, Motorola, or Xiaomi. This is weaker evidence than an ADB device query; ADB remains necessary to confirm phone visibility.
- The portable JDK and Gradle ran successfully. Android command-line package listing succeeded.
- SDK installation reached the `android-sdk-license` prompt and was left awaiting a human answer. Until the SDK is installed, no APK compilation, ADB phone test, emulator test, DHU test, or car hardware test has been established.
