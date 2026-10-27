#!/usr/bin/env node
/** Make static local MVT tiles; never alter the source GeoJSON geometries. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import geojsonvt from 'geojson-vt';
import vtpbf from 'vt-pbf';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(root, 'public/data/buildings.geojson');
const metadataPath = path.join(root, 'public/data/buildings-metadata.json');
const output = path.join(root, 'public/tiles/buildings');
const metadata = JSON.parse(await fs.readFile(metadataPath, 'utf8'));
const data = JSON.parse(await fs.readFile(sourcePath, 'utf8'));
const started = performance.now();
const minzoom = 11;
const maxzoom = 16;
const options = { maxZoom: maxzoom, indexMaxZoom: 12, indexMaxPoints: 100_000, tolerance: 0.5, extent: 4096, buffer: 64 };

for (const feature of data.features) {
  const properties = feature.properties;
  feature.properties = {
    gers_id: properties.gers_id,
    source_datasets: properties.source_datasets,
    ...(properties.name ? { name: properties.name } : {}),
    ...(typeof properties.height === 'number' ? { height: properties.height } : {}),
  };
}
const index = geojsonvt(data, options);
const [west, south, east, north] = metadata.bbox;
const longitudeTile = (longitude, zoom) => Math.floor((longitude + 180) / 360 * 2 ** zoom);
const latitudeTile = (latitude, zoom) => {
  const radians = latitude * Math.PI / 180;
  return Math.floor((1 - Math.asinh(Math.tan(radians)) / Math.PI) / 2 * 2 ** zoom);
};
const tiles = [];
let totalBytes = 0;
let emptyTileCount = 0;
const byZoom = {};
const emptyByZoom = {};
for (let z = minzoom; z <= maxzoom; z++) {
  let count = 0;
  let emptyCount = 0;
  for (let x = longitudeTile(west, z); x <= longitudeTile(east, z); x++) {
    for (let y = latitudeTile(north, z); y <= latitudeTile(south, z); y++) {
      // Every XYZ tile intersecting the declared bounds must be a real PBF.
      // Omitting empty tiles lets static SPA hosts return index.html for them.
      const tile = index.getTile(z, x, y) ?? { features: [] };
      if (!tile.features.length) {
        emptyTileCount++;
        emptyCount++;
      }
      const bytes = vtpbf.fromGeojsonVt({ buildings: tile }, { version: 2, extent: options.extent });
      const directory = path.join(output, String(z), String(x));
      await fs.mkdir(directory, { recursive: true });
      await fs.writeFile(path.join(directory, `${y}.pbf`), bytes);
      tiles.push({ z, x, y, bytes: bytes.length, features: tile.features.length });
      totalBytes += bytes.length;
      count++;
    }
  }
  byZoom[z] = count;
  emptyByZoom[z] = emptyCount;
  console.log(`Buildings z${z}: ${count} tiles (${emptyCount} empty)`);
}
const generatedAt = new Date().toISOString();
const generationSeconds = Math.round((performance.now() - started) / 10) / 100;
const manifestPath = 'public/tiles/buildings/manifest.json';
await fs.writeFile(path.join(root, manifestPath), JSON.stringify({ release: metadata.release, generatedAt, bbox: metadata.bbox, emptyTileCount, tiles }, null, 2) + '\n');
await fs.writeFile(path.join(output, 'tilejson.json'), JSON.stringify({
  tilejson: '3.0.0', name: metadata.title, scheme: 'xyz',
  tiles: ['/tiles/buildings/{z}/{x}/{y}.pbf'], minzoom, maxzoom,
  bounds: metadata.bbox, attribution: metadata.attribution,
  vector_layers: [{ id: 'buildings', minzoom, maxzoom, fields: { gers_id: 'String', source_datasets: 'String', name: 'String', height: 'Number' } }],
}, null, 2) + '\n');
metadata.vectorTiles = {
  template: '/tiles/buildings/{z}/{x}/{y}.pbf', sourceLayer: 'buildings', scheme: 'xyz',
  minzoom, maxzoom, bounds: metadata.bbox, extent: options.extent, buffer: options.buffer,
  tolerance: options.tolerance, indexMaxZoom: options.indexMaxZoom,
  tileCount: tiles.length, emptyTileCount, totalBytes, byZoom, emptyByZoom, generatedAt, generationSeconds, manifest: manifestPath,
  attributes: { gers_id: 'string', source_datasets: 'semicolon-separated dataset names', name: 'optional string', height: 'optional source height in meters' },
  geometry: 'Display-only Web Mercator tile projection, 4096-unit quantization, clipping and 0.5 tile-unit simplification below z16. Full source geometry remains in buildings.geojson and raw cache. No source polygons were invented.',
};
await fs.writeFile(metadataPath, JSON.stringify(metadata, null, 2) + '\n');
console.log(JSON.stringify({ tiles: tiles.length, bytes: totalBytes, generationSeconds, maxTileBytes: Math.max(...tiles.map(t => t.bytes)) }));
