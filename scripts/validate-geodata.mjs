#!/usr/bin/env node
/** Integrity checks against the cached OSM source; no network required. */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = async file => JSON.parse(await fs.readFile(path.join(root, file), 'utf8'));
const source = await read('data/osm/extract.json');
const discovery = await read('data/osm/places.json');
const metadata = await read('public/data/metadata.json');
const byId = new Map([...source.elements, ...discovery.elements].map(e => [`${e.type}/${e.id}`, e]));
const equal = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);
let checked = 0;

function allPairs(coords) {
  if (typeof coords[0] === 'number') return [coords];
  return coords.flatMap(allPairs);
}
function sourcePairs(e) {
  if (e.type === 'node') return [[e.lon, e.lat]];
  const parts = e.type === 'relation' ? (e.members ?? []).flatMap(m => m.geometry ?? []) : (e.geometry ?? []);
  return parts.filter(p => Number.isFinite(p?.lon) && Number.isFinite(p?.lat)).map(p => [p.lon, p.lat]);
}

for (const name of ['roads', 'areas', 'waterways', 'places']) {
  const collection = await read(`public/data/${name}.geojson`);
  assert.equal(collection.type, 'FeatureCollection');
  assert.equal(collection.features.length, metadata.counts[name].total);
  assert(collection.features.length > 0, `${name} must not be empty`);
  const seen = new Set();
  for (const f of collection.features) {
    assert.equal(f.type, 'Feature');
    assert(f.id === f.properties.osm_id && byId.has(f.id), `Missing OSM provenance for ${f.id}`);
    assert(!seen.has(f.id), `Duplicate feature ${f.id}`);
    seen.add(f.id);
    assert.equal(typeof f.properties.kind, 'string');
    assert.equal(typeof f.properties.name, 'string');
    assert(Number.isFinite(f.properties.grade), `Invalid grade ${f.id}`);
    assert(Number.isFinite(f.properties.layer), `Invalid layer ${f.id}`);
    const coords = allPairs(f.geometry.coordinates);
    for (const p of coords) {
      assert.equal(p.length, 2);
      assert(p.every(Number.isFinite), `Invalid coordinate in ${f.id}`);
      assert(p[0] > -50 && p[0] < -48 && p[1] > -18 && p[1] < -15, `Coordinate outside Goiânia region ${f.id}`);
    }
    // A label can be a documented polygon centroid; map geometry must use source vertices.
    if (name !== 'places') {
      const sourceSet = new Set(sourcePairs(byId.get(f.id)).map(p => p.join(',')));
      assert(coords.every(p => sourceSet.has(p.join(','))), `Invented or changed source geometry in ${f.id}`);
    }
    if (f.geometry.type === 'LineString') assert(coords.length >= 2);
    const polygons = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.type === 'MultiPolygon' ? f.geometry.coordinates : [];
    for (const polygon of polygons) {
      assert(polygon.length >= 1);
      for (const ring of polygon) assert(ring.length >= 4 && equal(ring[0], ring.at(-1)), `Unclosed polygon ${f.id}`);
    }
    checked++;
  }
  console.log(`${name}: ${collection.features.length} real OSM features validated.`);
}

assert.equal(metadata.verifiedLocations.length, 5);
for (const name of ['Centro', 'Praça Cívica', 'Setor Bueno', 'Parque Vaca Brava', 'Parque Flamboyant']) {
  const place = metadata.verifiedLocations.find(p => p.name === name);
  assert(place && byId.has(place.osmId), `${name} has not been verified from OSM`);
  assert(place.coordinates[0] >= metadata.bounds.west && place.coordinates[0] <= metadata.bounds.east);
  assert(place.coordinates[1] >= metadata.bounds.south && place.coordinates[1] <= metadata.bounds.north);
}
assert.equal(metadata.verifiedLocations.find(p => p.name === 'Parque Flamboyant').osmId, 'way/220688361', 'Do not substitute the homonymous suburb for the actual park.');
assert.equal(metadata.license, 'ODbL-1.0');
assert(metadata.attribution.includes('OpenStreetMap'));
assert(metadata.fetchedAt && metadata.dataTimestamp && metadata.endpoint);
assert(metadata.counts.areas.byKind.park > 0 && metadata.counts.areas.byKind.water > 0 && metadata.counts.areas.byKind.building > 0);
assert(metadata.counts.roads.byKind.primary > 0 && metadata.counts.roads.byKind.residential > 0);
console.log(`PASS: ${checked} features, provenance, real vertices, polygon closure, road hierarchy, buildings, water, parks, and five required locations.`);
