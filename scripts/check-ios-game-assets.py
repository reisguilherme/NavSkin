"""Verify the prepared original-asset pack before invoking Apple's build tools."""
from pathlib import Path
import hashlib
import json

resources = Path(__file__).resolve().parent.parent / "ios/NavSkin/Resources"
catalog_file = resources / "game-assets/catalog.json"
if not catalog_file.is_file():
    raise SystemExit("Missing original game asset pack. Use the complete NavSkin iOS source ZIP or prepare the local assets described in docs/game-assets-provenance.md.")
catalog = json.loads(catalog_file.read_text(encoding="utf-8"))
provenance = json.loads((resources / "maps/credits/game-assets-provenance.json").read_text(encoding="utf-8"))
for font in provenance["fonts"]:
    file = resources / "game-assets" / font["file"]
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != font["sha256"]:
        raise SystemExit(f"Missing or changed original font: {font['name']}. Restore the prepared asset pack.")
for theme, profile in catalog["themes"].items():
    for stack in set(profile["map"].values()):
        if not (resources / "maps/glyphs" / stack / "0-255.pbf").is_file():
            raise SystemExit(f"Missing map glyphs: {stack}. Prepare the original asset pack.")
    if not (resources / "maps/sprites" / f"{theme}.png").is_file():
        raise SystemExit(f"Missing original sprite atlas: {theme}.")
print("PASS: original game font hashes, glyphs and sprite atlases are present")
