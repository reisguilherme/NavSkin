#!/usr/bin/env bash
set -euo pipefail
taskRoot="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
taskChecks="$taskRoot/.runtime/ios-checks"
mkdir -p "$taskChecks"
swiftc -swift-version 5 -parse-as-library \
  "$taskRoot/ios/NavSkin/App/AppTypes.swift" \
  "$taskRoot/ios/NavSkin/Map/MapResourceRepository.swift" \
  "$taskRoot/ios/Validation/ProjectProbe.swift" \
  -o "$taskChecks/project-probe"
"$taskChecks/project-probe" "$taskRoot/ios"
find "$taskRoot/ios/NavSkin" "$taskRoot/ios/NavSkinTests" -name '*.swift' -print0 | \
  xargs -0 swiftc -frontend -parse -swift-version 5
echo 'PASS: Swift syntax for app and Apple-platform tests (does not type-check Apple frameworks)'
