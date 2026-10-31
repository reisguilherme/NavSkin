"""Create a source delivery, excluding local tools, signing settings and build outputs."""
from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parent.parent
version = json.loads((root / "package.json").read_text(encoding="utf-8"))["version"]
output = root / "ios" / "artifacts" / f"NavSkin-ios-{version}-source.zip"
output.parent.mkdir(parents=True, exist_ok=True)
excluded_dirs = {".build", ".swiftpm", "DerivedData", "xcuserdata", "artifacts", "__pycache__"}
files = []
for file in (root / "ios").rglob("*"):
    if not file.is_file():
        continue
    relative = file.relative_to(root)
    if any(part in excluded_dirs for part in relative.parts):
        continue
    if file.name == "Local.xcconfig" or file.suffix in {".p12", ".mobileprovision", ".p8", ".ipa", ".zip"}:
        continue
    files.append(file)
for name in ["package.json", "scripts/build-ios.sh", "scripts/check-ios-source.sh", "scripts/check-ios-game-assets.py", "scripts/package-ios.py",
             "scripts/build-ios-cloud.sh", "scripts/package-ios-unsigned.py",
             ".github/workflows/ios-cloud.yml", "docs/ios-cloud-research.md",
             "docs/ios-platform-research.md", "docs/navigation-provider-decision.md",
             "docs/world-maps.md", "docs/game-assets-provenance.md", "docs/traffic-provider-research.md", "docs/navigation-v0.8.md", "docs/navigation-v0.9.md", "docs/maps-v0.10.md",
             "docs/navigation-icon-catalogs.md", "docs/design/game-cartography-references.md", "docs/design/new-theme-icon-references.md",
             "docs/eight-theme-assets.md", "docs/approved-map-art-v0.11.md", "docs/vibing-v0.11.md", "docs/navigation-avatars-v0.12.md",
             "tests/test_ios_unsigned_package.py"]:
    files.append(root / name)
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
    for file in sorted(files):
        archive.write(file, "NavSkin-ios/" + file.relative_to(root).as_posix())
with output.open("rb") as stream:
    digest = hashlib.file_digest(stream, "sha256").hexdigest()
summary = {"file": output.name, "kind": "Xcode project source, not an installable IPA",
           "bytes": output.stat().st_size, "sha256": digest, "files": len(files)}
(output.parent / "source-package.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
