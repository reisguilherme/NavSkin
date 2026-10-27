#!/usr/bin/env bash
set -euo pipefail
taskRoot="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
action="${1:-doctor}"
if [[ "$(uname -s)" != Darwin ]] || ! command -v xcodebuild >/dev/null 2>&1; then
  echo 'iOS build unavailable: macOS with full Xcode and the iOS SDK is required. This script does not produce an IPA on Windows/Linux.' >&2
  exit 2
fi
project="$taskRoot/ios/NavSkin.xcodeproj"
if [[ "$action" != doctor ]]; then
  python3 -B "$taskRoot/scripts/check-ios-game-assets.py"
fi
derived="$taskRoot/ios/DerivedData"
output="$taskRoot/ios/artifacts"
mkdir -p "$output"
case "$action" in
  doctor)
    xcodebuild -version
    xcrun --sdk iphoneos --show-sdk-version
    xcodebuild -list -project "$project"
    ;;
  simulator)
    xcodebuild -project "$project" -scheme NavSkin -configuration Debug \
      -destination 'generic/platform=iOS Simulator' -derivedDataPath "$derived" \
      CODE_SIGNING_ALLOWED=NO build
    ;;
  test)
    if [[ -z "${2:-}" ]]; then
      echo 'Usage: bash scripts/build-ios.sh test SIMULATOR_UDID' >&2
      echo 'List available devices with: xcrun simctl list devices available' >&2
      exit 2
    fi
    swift test --package-path "$taskRoot/ios/Packages/NavSkinCore"
    xcodebuild -project "$project" -scheme NavSkin -configuration Debug \
      -destination "platform=iOS Simulator,id=$2" -derivedDataPath "$derived" \
      -resultBundlePath "$output/tests-$(date +%Y%m%d-%H%M%S).xcresult" \
      CODE_SIGNING_ALLOWED=NO test
    ;;
  archive)
    if [[ -z "${2:-}" ]]; then
      echo 'Usage: bash scripts/build-ios.sh archive YOUR_APPLE_TEAM_ID' >&2
      echo 'Configure your bundle identifier in ios/Config/Local.xcconfig first.' >&2
      exit 2
    fi
    xcodebuild -project "$project" -scheme NavSkin -configuration Release \
      -destination 'generic/platform=iOS' -derivedDataPath "$derived" \
      -archivePath "$output/NavSkin.xcarchive" "DEVELOPMENT_TEAM=$2" archive
    echo 'Archive created. Export/install from Xcode Organizer with your account and provisioning profile.'
    ;;
  *) echo 'Actions: doctor, simulator, test SIMULATOR_UDID, archive TEAM_ID' >&2; exit 2 ;;
esac
