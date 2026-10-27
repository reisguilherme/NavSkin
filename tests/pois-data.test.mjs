import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { buildPois, classifyPoi, pointInPolygon, representativePoint, polygonParts, poiKinds } from '../scripts/build-pois.mjs';

const square = [[0, 0], [4, 0], [4, 4], [0, 4], [0, 0]];
const toGeometry = ring => ring.map(([lon, lat]) => ({ lon, lat }));
const box = { west: -5, south: -5, east: 10, north: 10 };

test('classification keeps hospital and clinic separate and covers requested POIs', () => {
  assert.equal(classifyPoi({ amenity: 'hospital' }), 'hospital');
  assert.equal(classifyPoi({ amenity: 'doctors' }), 'clinic');
  assert.equal(classifyPoi({ leisure: 'garden' }), 'park');
  assert.equal(classifyPoi({ amenity: 'theatre' }), 'theatre');
  assert.equal(classifyPoi({ tourism: 'museum' }), 'museum');
  assert.equal(classifyPoi({ amenity: 'arts_centre' }), 'culture');
  assert.equal(classifyPoi({ amenity: 'bench' }), null);
});

test('representative points stay inside concave polygons and outside holes', () => {
  const concave = [[[0, 0], [5, 0], [5, 1], [1, 1], [1, 5], [0, 5], [0, 0]]];
  const withHole = [square, [[1, 1], [3, 1], [3, 3], [1, 3], [1, 1]]];
  for (const rings of [concave, withHole]) {
    const point = representativePoint([rings]);
    assert.ok(point, 'polygon has a valid interior point');
    assert.ok(pointInPolygon(point, rings), `representative point ${point} is inside occupied polygon surface`);
  }
});

test('crossing polygons get an interior point in the output bbox', () => {
  const bbox = { west: 0, south: 0, east: 1, north: 1 };
  const rings = [[[-2, -2], [0.5, -2], [0.5, 3], [-2, 3], [-2, -2]]];
  const point = representativePoint([rings], bbox);
  assert.ok(pointInPolygon(point, rings));
  assert.ok(point[0] >= 0 && point[0] <= 1 && point[1] >= 0 && point[1] <= 1);
});

test('only safe same-name node/area duplicates merge and retain all source IDs', () => {
  const elements = [
    { type: 'node', id: 1, lon: 1, lat: 1, tags: { amenity: 'pharmacy', name: 'Farmácia Local' } },
    { type: 'way', id: 2, geometry: toGeometry(square), tags: { amenity: 'pharmacy', name: 'Farmácia Local', website: 'https://example.org' } },
    { type: 'node', id: 3, lon: 1.00001, lat: 1, tags: { amenity: 'pharmacy', name: 'Farmácia Local' } },
    { type: 'node', id: 4, lon: 1.00001, lat: 1, tags: { amenity: 'pharmacy', name: 'Outra Farmácia' } },
    { type: 'node', id: 5, lon: 1, lat: 1, tags: { amenity: 'hospital', name: 'Farmácia Local' } },
  ];
  const result = buildPois({ elements }, box);
  assert.equal(result.collection.features.length, 4);
  assert.equal(result.duplicates.length, 1);
  const retained = result.collection.features.find(feature => feature.id === 'node/1');
  assert.deepEqual(retained.geometry.coordinates, [1, 1]);
  assert.deepEqual(retained.properties.source_ids, ['node/1', 'way/2']);
  assert.equal(retained.properties.website, 'https://example.org');
});

test('incomplete polygon geometry and out-of-bounds nodes are omitted with reasons', () => {
  const result = buildPois({ elements: [
    { type: 'way', id: 1, geometry: toGeometry([[0, 0], [1, 1], [2, 0]]), tags: { amenity: 'theatre', name: 'Incomplete' } },
    { type: 'node', id: 2, lon: 50, lat: 50, tags: { amenity: 'fuel' } },
  ] }, box);
  assert.equal(result.collection.features.length, 0);
  assert.equal(result.omitted.length, 2);
});

test('multipolygon holes are preserved and tagged relation/member duplicates merge', () => {
  const hole = [[1, 1], [3, 1], [3, 3], [1, 3], [1, 1]];
  const tags = { leisure: 'park', name: 'Parque com lago' };
  const result = buildPois({ elements: [
    { type: 'way', id: 7, tags, geometry: toGeometry(square) },
    { type: 'relation', id: 8, tags: { ...tags, type: 'multipolygon' }, members: [
      { type: 'way', ref: 7, role: 'outer', geometry: toGeometry(square) },
      { type: 'way', ref: 9, role: 'inner', geometry: toGeometry(hole) },
    ] },
  ] }, box);
  assert.equal(result.collection.features.length, 1);
  const feature = result.collection.features[0];
  assert.deepEqual(feature.properties.source_ids, ['relation/8', 'way/7']);
  assert.ok(pointInPolygon(feature.geometry.coordinates, [square, hole]));
});

test('shipped POIs have real OSM sources, correct categories, valid coordinates and attribution', async () => {
  const raw = JSON.parse(await fs.readFile(new URL('../data/osm/pois.json', import.meta.url), 'utf8'));
  const geo = JSON.parse(await fs.readFile(new URL('../public/data/pois.geojson', import.meta.url), 'utf8'));
  const metadata = JSON.parse(await fs.readFile(new URL('../public/data/pois-metadata.json', import.meta.url), 'utf8'));
  const source = new Map(raw.elements.map(element => [`${element.type}/${element.id}`, element]));
  assert.equal(metadata.counts.total, geo.features.length);
  assert.equal(metadata.license, 'ODbL-1.0');
  assert.ok(metadata.fetchedAt && metadata.dataTimestamp && metadata.endpoint && metadata.queryFile);
  assert.match(metadata.attribution, /OpenStreetMap/);
  const seen = new Set();
  for (const feature of geo.features) {
    const properties = feature.properties;
    const element = source.get(properties.osm_id);
    assert.ok(element, `${properties.osm_id} exists in real downloaded source`);
    assert.ok(poiKinds.includes(properties.kind));
    assert.equal(properties.kind, classifyPoi(element.tags));
    assert.equal(properties.name, element.tags.name ?? element.tags['name:pt'] ?? '');
    assert.equal(feature.geometry.type, 'Point');
    const [lon, lat] = feature.geometry.coordinates;
    assert.ok(Number.isFinite(lon) && Number.isFinite(lat));
    assert.ok(lon >= metadata.bounds.west && lon <= metadata.bounds.east && lat >= metadata.bounds.south && lat <= metadata.bounds.north);
    if (element.type !== 'node') assert.ok(polygonParts(element).some(rings => pointInPolygon(feature.geometry.coordinates, rings)), `${properties.osm_id} point stays in its actual polygon`);
    for (const id of properties.source_ids) {
      assert.ok(source.has(id));
      assert.ok(!seen.has(id), `source ${id} appears once after deduplication`);
      seen.add(id);
    }
    assert.equal(properties.source_url, `https://www.openstreetmap.org/${properties.osm_id}`);
  }
  for (const kind of ['park', 'pharmacy', 'fuel', 'hospital', 'theatre']) assert.ok(metadata.counts.byKind[kind] > 0, `${kind} has genuine downloaded examples`);
  for (const kind of poiKinds) assert.equal(metadata.counts.byKind[kind], geo.features.filter(feature => feature.properties.kind === kind).length);
  assert.equal(metadata.sourceIds.length, seen.size);
});
