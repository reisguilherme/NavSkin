#!/usr/bin/env node
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { VectorTileLayer } from '@mapbox/vector-tile';
import { PbfReader } from 'pbf';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const metadata = JSON.parse(await fs.readFile(path.join(root, 'public/data/buildings-metadata.json'), 'utf8'));
const tiles = metadata.vectorTiles;
assert.ok(tiles, 'Missing generated vector tile metadata');
const manifest = JSON.parse(await fs.readFile(path.join(root, tiles.manifest), 'utf8'));
assert.equal(manifest.tiles.length, tiles.tileCount);
const listedTiles = new Map(manifest.tiles.map(record => [`${record.z}/${record.x}/${record.y}`, record]));
const [west, south, east, north] = tiles.bounds;
const lonTile = (lon, zoom) => Math.floor((lon + 180) / 360 * 2 ** zoom);
const latTile = (lat, zoom) => Math.floor((1 - Math.asinh(Math.tan(lat * Math.PI / 180)) / Math.PI) / 2 * 2 ** zoom);
let expectedTiles = 0;
for (let z = tiles.minzoom; z <= tiles.maxzoom; z++) {
  for (let x = lonTile(west, z); x <= lonTile(east, z); x++) {
    for (let y = latTile(north, z); y <= latTile(south, z); y++) {
      const key = `${z}/${x}/${y}`;
      assert.ok(listedTiles.has(key), `Declared bounds tile is missing from manifest: ${key}`);
      await fs.access(path.join(root, 'public/tiles/buildings', `${key}.pbf`));
      expectedTiles++;
    }
  }
}
assert.equal(listedTiles.size, expectedTiles, 'Manifest contains tiles outside declared bounds or duplicate tile coordinates');
const maxZoomIds = new Set();
let bytes = 0;
let emptyTileCount = 0;
for (const record of manifest.tiles) {
  const tilePath = path.join(root, 'public/tiles/buildings', String(record.z), String(record.x), `${record.y}.pbf`);
  const buffer = await fs.readFile(tilePath);
  assert.equal(buffer.length, record.bytes, `Incorrect size: ${tilePath}`);
  bytes += buffer.length;
  // VectorTile's top-level parser deliberately drops layers with no features.
  // Parse the sole layer directly so named empty tiles are verified as well.
  const reader = new PbfReader(buffer);
  assert.equal(reader.readVarint(), 26, `Missing layer message: ${tilePath}`);
  const layerBytes = reader.readVarint();
  const layer = new VectorTileLayer(reader, reader.pos + layerBytes);
  assert.equal(reader.pos, buffer.length, `Unexpected trailing tile data: ${tilePath}`);
  assert.equal(layer.name, 'buildings', `Missing buildings layer: ${tilePath}`);
  assert.equal(layer.extent, 4096);
  assert.equal(layer.length, record.features, `Incorrect feature count: ${tilePath}`);
  if (layer.length === 0) emptyTileCount++;
  for (let i = 0; i < layer.length; i++) {
    const feature = layer.feature(i);
    assert.equal(feature.type, 3, 'Tile contains a non-polygon feature');
    assert.equal(typeof feature.properties.gers_id, 'string');
    assert.equal(typeof feature.properties.source_datasets, 'string');
    assert.ok(feature.loadGeometry().length > 0);
    if (record.z === tiles.maxzoom) maxZoomIds.add(feature.properties.gers_id);
  }
}
assert.equal(bytes, tiles.totalBytes);
assert.equal(emptyTileCount, tiles.emptyTileCount, 'Empty tile count differs from metadata');
assert.equal(emptyTileCount, manifest.emptyTileCount, 'Empty tile count differs from manifest');
assert.equal(maxZoomIds.size, metadata.counts.total, 'Highest zoom tiles omit buildings');
console.log(JSON.stringify({ tiles: manifest.tiles.length, declaredBoundsTiles: expectedTiles, emptyTileCount, bytes, maxZoomBuildings: maxZoomIds.size, polygonAndAttributionValidation: 'passed' }));
