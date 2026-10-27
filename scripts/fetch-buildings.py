#!/usr/bin/env python3
"""Download Overture buildings, preserve originals, and build the local map extract.

Install dependencies with: python -m pip install --target .runtime/overture -r scripts/requirements-buildings.txt
Use --offline to regenerate from the saved raw download without network access.
"""
import argparse
import hashlib
import importlib.metadata
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import build_opener, install_opener, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".runtime/overture"))

import shapely
from shapely.geometry import box

CACHE = ROOT / "data/overture"
PUBLIC = ROOT / "public/data"
CATALOG_URL = "https://stac.overturemaps.org/catalog.json"
SOURCE_INFO = {
    "Google Open Buildings": {
        "license": "CC-BY-4.0", "licenseUrl": "https://creativecommons.org/licenses/by/4.0/",
        "url": "https://sites.research.google/open-buildings/", "method": "ML-derived roofprint",
        "attribution": "Google Open Buildings",
    },
    "Microsoft ML Buildings": {
        "license": "ODbL-1.0", "licenseUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
        "url": "https://github.com/microsoft/GlobalMLBuildingFootprints", "method": "ML-derived roofprint",
        "attribution": "Microsoft Global ML Building Footprints",
    },
    "OpenStreetMap": {
        "license": "ODbL-1.0", "licenseUrl": "https://www.openstreetmap.org/copyright",
        "url": "https://www.openstreetmap.org", "method": "Community-contributed footprint",
        "attribution": "© OpenStreetMap contributors",
    },
}


def now():
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def write_json(target, value, pretty=False):
    target.parent.mkdir(parents=True, exist_ok=True)
    text = json.dumps(value, ensure_ascii=False, allow_nan=False,
                      indent=2 if pretty else None, separators=None if pretty else (",", ":"))
    target.write_text(text + "\n", encoding="utf-8")


def fetch_json(url):
    # STAC's edge service sometimes rejects Python's default User-Agent.
    opener = build_opener()
    opener.addheaders = [("User-Agent", "Mozilla/5.0 GameMap public-geodata/1.0")]
    install_opener(opener)
    with urlopen(url, timeout=90) as response:
        return json.load(response)


def download(bbox, release):
    from overturemaps.cli import cli
    catalog = fetch_json(CATALOG_URL)
    write_json(CACHE / "catalog.json", catalog, True)
    release = release or catalog["latest"]
    collection_url = f"https://stac.overturemaps.org/{release}/buildings/building/collection.json"
    collection = fetch_json(collection_url)
    assert collection["license"] == "ODbL-1.0", "Review license before using this release"
    write_json(CACHE / "building-collection.json", collection, True)
    cli.main(["download", "--bbox=" + ",".join(map(str, bbox)), "-f", "geojson", "--type", "building",
              "--release", release, "--connect_timeout", "60", "--request_timeout", "120",
              "-o", str(CACHE / "buildings.raw.geojson")], standalone_mode=False)
    return release


def build():
    source_metadata = json.loads((PUBLIC / "metadata.json").read_text(encoding="utf-8"))
    bbox = source_metadata["bbox"]
    state = json.loads((CACHE / "buildings.raw.geojson.state").read_text(encoding="utf-8"))
    assert [state["bbox"][key] for key in ["xmin", "ymin", "xmax", "ymax"]] == bbox, "Raw download bounds differ"
    release = state["last_release"]
    raw_path = CACHE / "buildings.raw.geojson"
    raw_bytes = raw_path.read_bytes()
    raw = json.loads(raw_bytes)
    raw_features = raw["features"]
    assert raw_features, "Empty download"
    geometries = shapely.from_geojson([json.dumps(f["geometry"]) for f in raw_features])
    assert shapely.is_valid(geometries).all(), "Upstream invalid polygons require review"
    assert not shapely.is_empty(geometries).any(), "Empty upstream geometries require review"
    intersects = shapely.intersects(geometries, box(*bbox))
    selected = [f for f, keep in zip(raw_features, intersects) if keep]
    features, ids = [], set()
    source_counts, type_counts = Counter(), Counter()
    update_ranges, versions = defaultdict(list), defaultdict(set)
    with_height = with_name = 0
    for feature in selected:
        gers_id = feature["id"]
        assert gers_id not in ids, f"Duplicate GERS ID: {gers_id}"
        ids.add(gers_id)
        original = feature["properties"]
        sources = original.get("sources") or []
        datasets = sorted({s["dataset"] for s in sources})
        assert datasets and all(dataset in SOURCE_INFO for dataset in datasets), "Review new upstream sources"
        source_counts.update(datasets)
        for source in sources:
            if source.get("update_time"):
                update_ranges[source["dataset"]].append(source["update_time"])
            if source.get("version"):
                versions[source["dataset"]].add(source["version"])
        props = {"gers_id": gers_id, "kind": "building", "source": "Overture Maps",
                 "sources": datasets, "source_datasets": "; ".join(datasets)}
        if original.get("names"):
            props["names"] = original["names"]
            if original["names"].get("primary"):
                props["name"] = original["names"]["primary"]
                with_name += 1
        for key in ["height", "class", "subtype", "num_floors"]:
            if original.get(key) is not None:
                props[key] = original[key]
        if "height" in props:
            with_height += 1
        features.append({"type": "Feature", "geometry": feature["geometry"], "properties": props})
        type_counts[feature["geometry"]["type"]] += 1
    output = PUBLIC / "buildings.geojson"
    write_json(output, {"type": "FeatureCollection", "features": features})
    osm_count = source_metadata["counts"]["areas"]["byKind"]["building"]
    metadata = {
        "title": "Goiânia — Overture building footprints", "bbox": bbox, "bounds": source_metadata["bounds"],
        "crs": "EPSG:4326", "release": release, "releaseDate": release[:10],
        "dataTimestamp": release[:10], "fetchedAt": state["last_run"], "generatedAt": now(),
        "imageryCaptureDate": None,
        "source": "Overture Maps Foundation buildings theme",
        "sourceUrl": "https://docs.overturemaps.org/guides/buildings/",
        "catalogUrl": CATALOG_URL,
        "collectionUrl": f"https://stac.overturemaps.org/{release}/buildings/building/collection.json",
        "dataUrl": f"s3://overturemaps-us-west-2/release/{release}/theme=buildings/type=building/",
        "license": "ODbL-1.0", "licenseUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
        "attribution": "© OpenStreetMap contributors, Overture Maps Foundation; Google Open Buildings; Microsoft Global ML Building Footprints",
        "attributionUrl": "https://docs.overturemaps.org/attribution/",
        "counts": {"total": len(features), "raw": len(raw_features), "excludedEnvelopeOnly": len(raw_features) - len(features),
                   "bySource": dict(source_counts), "byGeometry": dict(type_counts), "withName": with_name, "withHeight": with_height},
        "comparison": {"osmBuildingCount": osm_count, "coverageRatio": round(len(features) / osm_count, 2),
                       "interpretation": "Feature count comparison, not a completeness or accuracy score; data dates and conflation differ."},
        "sources": [{"dataset": dataset, "count": source_counts[dataset], **SOURCE_INFO[dataset],
                     "versions": sorted(versions[dataset]),
                     "sourceUpdateTimeRange": [min(update_ranges[dataset]), max(update_ranges[dataset])] if update_ranges[dataset] else None}
                    for dataset in sorted(source_counts)],
        "files": {"geojson": "public/data/buildings.geojson", "geojsonBytes": output.stat().st_size,
                  "raw": "data/overture/buildings.raw.geojson", "rawBytes": len(raw_bytes),
                  "rawSha256": hashlib.sha256(raw_bytes).hexdigest()},
        "processing": {
            "query": "Official overturemaps Python client: type building, STAC file pruning plus bbox envelope pushdown; exact geometry-intersects test afterwards.",
            "geometry": "Unmodified upstream Polygon/MultiPolygon coordinates. No generated buildings, coordinate rounding, simplification, or footprint repair.",
            "clipping": "Keep entire footprints intersecting bbox; edge coordinates may extend beyond it.",
            "provenance": "Public sources is a compact dataset-name list. Full upstream source records, confidence, record_id, property, update_time, provider, resource and version remain in raw cache, joined by GERS ID.",
            "duplicates": "Use this conflated layer in place of OSM buildings; do not stack the two building sources.",
            "timestamps": "Release, download, generation and upstream update dates are separate. None establishes imagery acquisition date or current ground truth.",
        },
        "validation": {"invalidGeometries": 0, "emptyGeometries": 0, "duplicateGersIds": 0,
                       "outsideQuery": 0, "geometryBounds": shapely.total_bounds(geometries[intersects]).tolist()},
        "warnings": ["Most footprints here are ML-derived roofprints: omissions, false detections, misalignment and age are possible.",
                     "These are map features, not confirmed cadastral records, parcel boundaries, ownership or verified building occupancy.",
                     "Heights are sparse source attributes; no heights have been invented."]
    }
    write_json(PUBLIC / "buildings-metadata.json", metadata, True)
    write_json(CACHE / "query.json", {"bbox": bbox, "release": release, "type": "building", "format": "geojson",
                                     "client": "overturemaps", "clientVersion": importlib.metadata.version("overturemaps"),
                                     "fetchedAt": state["last_run"], "collectionUrl": metadata["collectionUrl"]}, True)
    print(json.dumps({"release": release, "buildings": len(features), "sources": dict(source_counts),
                      "geojsonBytes": output.stat().st_size, "validation": metadata["validation"]}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Build from saved raw data, with no network")
    parser.add_argument("--release", help="Pin an available Overture release (defaults to STAC latest)")
    args = parser.parse_args()
    if not args.offline:
        bounds = json.loads((PUBLIC / "metadata.json").read_text(encoding="utf-8"))["bbox"]
        download(bounds, args.release)
    build()
