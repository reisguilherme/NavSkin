#!/usr/bin/env bash
# Build a real iOS-device app on macOS; personal signing happens later on Windows.
set -euo pipefail
taskRoot="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
if [[ "$(uname -s)" != Darwin ]] || ! command -v xcodebuild >/dev/null 2>&1; then
  echo 'Cloud iOS build requires a macOS runner with Xcode. No IPA was generated.' >&2
  exit 2
fi
python3 -B -m unittest discover -s "$taskRoot/tests" -p 'test_ios_unsigned_package.py'
output="$taskRoot/ios/artifacts/cloud"
mkdir -p "$output"
project="$taskRoot/ios/NavSkin.xcodeproj"
xcodebuild -version | tee "$output/xcode.log"
swift --version | tee "$output/swift.log"
swift test --package-path "$taskRoot/ios/Packages/NavSkinCore" \
  --scratch-path "$taskRoot/ios/DerivedData/Core" 2>&1 | tee "$output/core-tests.log"

if [[ "${RUN_APPLE_TESTS:-true}" == true ]]; then
  appleResult="$output/AppleTests-$(date +%Y%m%d-%H%M%S)-$$.xcresult"
  xcrun simctl list devices available --json > "$output/simulators.json"
  simulatorID="$(python3 - "$output/simulators.json" <<'PY'
import json
import sys
data = json.load(open(sys.argv[1]))
candidates = []
for runtime, devices in data.get('devices', {}).items():
    if '.iOS-' not in runtime:
        continue
    try:
        version = tuple(int(part) for part in runtime.split('.iOS-', 1)[1].split('-'))
    except ValueError:
        continue
    if version < (17,):
        continue
    for device in devices:
        if device.get('isAvailable') and device.get('name', '').startswith('iPhone'):
            candidates.append((version, device.get('state') == 'Booted', device['udid']))
if not candidates:
    raise SystemExit('No available iPhone simulator with iOS 17+. Check the runner image or disable apple_tests for a build-only run.')
print(sorted(candidates, reverse=True)[0][2])
PY
)"
  xcodebuild -project "$project" -scheme NavSkin -configuration Debug \
    -destination "platform=iOS Simulator,id=$simulatorID" \
    -derivedDataPath "$taskRoot/ios/DerivedData/Simulator" \
    -resultBundlePath "$appleResult" \
    CODE_SIGNING_ALLOWED=NO test 2>&1 | tee "$output/apple-tests.log"
fi

xcodebuild -project "$project" -scheme NavSkin -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath "$taskRoot/ios/DerivedData/Device" \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
  build 2>&1 | tee "$output/device-build.log"

app="$taskRoot/ios/DerivedData/Device/Build/Products/Release-iphoneos/NavSkin.app"
python3 "$taskRoot/scripts/package-ios-unsigned.py" "$app" "$output/NavSkin-unsigned.ipa"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '## NavSkin para iPhone'
    echo ''
    echo 'O artefato contém NavSkin-unsigned.ipa, compilado para iPhone físico.'
    echo 'Ele exige assinatura pessoal antes de instalar; não é um build para TestFlight.'
    echo 'Use AltServer no Windows e sua conta Apple localmente. Não envie credenciais Apple ao GitHub.'
    echo 'Instruções: ios/docs/SEM-MAC.md.'
  } >> "$GITHUB_STEP_SUMMARY"
fi
