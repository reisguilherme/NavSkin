#!/usr/bin/env python3
"""Validate the delivered building extract against unmodified Overture geometry."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / ".runtime" / "overture"))

import shapely
from shapely.geometry import box


def validate():
    data = json.loads((ROOT / "public/data/buildings.geojson").read_text(encoding="utf-8"))
    metadata = json.loads((ROOT / "public/data/buildings-metadata.json").read_text(encoding="utf-8"))
    raw = json.loads((ROOT / "data/overture/buildings.raw.geojson").read_text(encoding="utf-8"))
    features = data["features"]
    assert data["type"] == "FeatureCollection" and features, "Empty or invalid FeatureCollection"
    assert len(features) == metadata["counts"]["total"], "Metadata count differs"
    ids = [f["properties"]["gers_id"] for f in features]
    assert len(set(ids)) == len(ids), "Duplicate GERS identifiers"
    originals = {f["id"]: f for f in raw["features"]}
    for feature in features:
        props = feature["properties"]
        assert props["kind"] == "building", "Unexpected feature kind"
        assert props["sources"] and props["source_datasets"], "Missing source attribution"
        assert feature["geometry"] == originals[props["gers_id"]]["geometry"], "Coordinates were changed"
        assert feature["geometry"]["type"] in {"Polygon", "MultiPolygon"}, "Non-polygon building"
    geometries = shapely.from_geojson([json.dumps(f["geometry"]) for f in features])
    assert shapely.is_valid(geometries).all(), "Invalid polygon geometry"
    assert not shapely.is_empty(geometries).any(), "Empty geometry"
    assert shapely.intersects(geometries, box(*metadata["bbox"])).all(), "Building misses query bounds"
    assert metadata["license"] == "ODbL-1.0", "Incorrect theme license"
    assert metadata["counts"]["total"] > metadata["comparison"]["osmBuildingCount"] * 10, "No substantial coverage improvement"
    print(json.dumps({"buildings": len(features), "invalid": 0, "empty": 0, "duplicates": 0,
                      "outsideQuery": 0, "coordinatesPreserved": True, "release": metadata["release"]}))


if __name__ == "__main__":
    validate()
