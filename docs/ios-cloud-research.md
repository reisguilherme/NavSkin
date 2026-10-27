# iOS cloud build and Windows installation research

Verified 2026-10-27 against the primary sources linked below. This records a feasible build/install route; no GitHub macOS build or physical iPhone installation has been executed by this research.

## Build in GitHub Actions

- GitHub lists `macos-26` as a standard Apple Silicon runner and `macos-26-intel` as its Intel counterpart. `macos-26` is generally available. An explicit OS label avoids silently changing operating systems through `macos-latest`. [Runner reference](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job), [GA announcement](https://github.blog/changelog/2026-02-26-macos-26-is-now-generally-available-for-github-hosted-runners/)
- Its current image manifest defaults to **Xcode 26.6** and ships **Bash 3.2.57**. Keep build scripts compatible with Bash 3.2, and record `xcodebuild -version` in each run because image contents can change. [Official image manifest](https://raw.githubusercontent.com/actions/runner-images/main/images/macos/macos-26-arm64-Readme.md)
- Official releases verified during research: checkout **v7.0.1**, commit `3d3c42e5aac5ba805825da76410c181273ba90b1`; upload-artifact **v7.0.2**, commit `cf430e030ddbb5b0abf93d22962f4752f3646cd9`. Both use Node 24. [Checkout release](https://github.com/actions/checkout/releases/tag/v7.0.1), [upload-artifact release](https://github.com/actions/upload-artifact/releases/tag/v7.0.2)
- Standard runners in public repositories have free Actions compute. Private repositories consume the owner's included allowance; excess compute/storage can be billed. Keep the workflow manual and artifact retention short; choose repository visibility deliberately. [GitHub billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- An Actions artifact download requires GitHub authentication and expires according to retention. Downloading an artifact does not sign its contained app. [Official artifact action manifest](https://raw.githubusercontent.com/actions/upload-artifact/v7.0.2/action.yml)

Implementation review points (engineering recommendations): resolve the pinned SPM package, compile the simulator target, run tests, then build Release with the **iphoneos** SDK and `generic/platform=iOS`, with code signing disabled. Package the resulting device app as `Payload/NavSkin.app` at the archive root. Validate Info.plist, embedded frameworks/resources, executable ARM64 architecture **and iOS device platform**; ARM64 alone also describes Apple Silicon simulator builds. Preserve executable permissions and symlinks during copying/zipping. Treat this IPA as an unsigned input for local signing, never as an immediately installable iPhone app. Apple distinguishes SDK platforms in Mach-O metadata. [Apple engineering explanation](https://developer.apple.com/forums/thread/662611)

## Install/sign locally from Windows

AltServer for Windows requires Windows 10 or later. Its documented setup uses iTunes **and** iCloud downloaded directly from Apple. The Microsoft Store iCloud version has a separate documented workaround, so do not substitute it silently. Use downloads linked by the official guide. Connect and unlock the iPhone, trust the computer, and enable Developer Mode on iOS 16+. The app itself requires iOS 17+. [Official downloads](https://altstore.io/), [Windows setup](https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows), [iCloud workaround](https://faq.altstore.io/altstore-classic/troubleshooting-guide#what-if-i-need-to-use-the-windows-store-version-of-icloud)

The shortest documented manual route is **Shift-click AltServer's tray icon → Sideload .ipa…**, then select the downloaded IPA. AltServer supports this without first installing AltStore. Directly installed apps need manual reinstallation every seven days. The latest documented Windows release is **1.7.4**, dated 2026-03-24, including the iOS 26.4 launch fix. [Official release notes](https://faq.altstore.io/release-notes/altserver)

AltStore Classic is optional if automatic refresh is preferred. It signs apps using the user's personal development certificate, and AltServer installs them. Refresh needs AltServer reachable on the same Wi-Fi or a USB connection. AltStore itself occupies one of the three active app slots. [Official project description](https://github.com/altstoreio/AltStore), [refresh/connectivity guidance](https://faq.altstore.io/altstore-classic/troubleshooting-guide), [active app limits](https://faq.altstore.io/altstore-classic/activating-apps)

Apple documents free Personal Team limits of **10 App IDs**, **3 devices**, and **3 apps per device**, with App IDs/devices/profiles expiring after **7 days**. This supports personal testing; App Store/TestFlight distribution needs the relevant paid program. [Apple account limits](https://developer.apple.com/help/account/basics/about-your-developer-account)

The user enters their own Apple Account credentials only in local Apple/AltServer/AltStore interfaces. The GitHub build needs no Apple password, certificate, provisioning profile, Team ID or Apple signing secret. Success still needs the first real macOS build followed by local signing and a physical-device run.

## Delivery update · NavSkin 0.5.0

The first repository workflow attempt did not start: GitHub reported an account billing lock before any job step. The user chose a friend’s local Mac instead. The same script runs locally without GitHub or Apple signing credentials; see [the Mac README](../ios/README.md). Actions is manual-only. Apple build and physical-device testing remain pending.
