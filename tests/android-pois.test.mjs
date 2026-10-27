import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import {
  androidKinds, androidPoiQuery, classifyAndroidPoi, buildAndroidPois,
} from '../scripts/android-pois.mjs';
import { polygonParts, pointInPolygon } from '../scripts/build-pois.mjs';

const bbox = { west: 0, south: 0, east: 10, north: 10 };
const node = (id, tags, lon = 3, lat = 3) => ({ type: 'node', id, lon, lat, tags });
const way = (id, tags, points = [[2, 2], [4, 2], [4, 4], [2, 4], [2, 2]]) => ({
  type: 'way', id, tags, geometry: points.map(([lon, lat]) => ({ lon, lat })),
});

test('all requested OSM tags map to nine semantic categories with the six existing IDs preserved', () => {
  assert.deepEqual(androidKinds, ['parks', 'fuel', 'food', 'parking', 'health', 'shops', 'education', 'culture', 'religion']);
  for (const [key, value, expected] of [
    ['leisure', 'park', 'parks'], ['leisure', 'garden', 'parks'],
    ['amenity', 'fuel', 'fuel'], ['amenity', 'parking', 'parking'],
    ...['restaurant', 'cafe', 'fast_food', 'bar', 'pub'].map(value => ['amenity', value, 'food']),
    ...['hospital', 'clinic', 'doctors', 'pharmacy'].map(value => ['amenity', value, 'health']),
    ...['supermarket', 'convenience'].map(value => ['shop', value, 'shops']),
    ...['school', 'university', 'college', 'kindergarten'].map(value => ['amenity', value, 'education']),
    ...['theatre', 'cinema', 'arts_centre'].map(value => ['amenity', value, 'culture']),
    ['tourism', 'museum', 'culture'], ['amenity', 'place_of_worship', 'religion'],
  ]) assert.equal(classifyAndroidPoi({ [key]: value }), expected, key + '=' + value);
  for (const tags of [{}, { amenity: 'bank' }, { shop: 'clothes' }, { office: 'doctor' }]) {
    assert.equal(classifyAndroidPoi(tags), null);
  }
});

test('education, culture, worship and drinking POIs retain exact original subtypes and provenance', () => {
  const tags = [
    { amenity: 'school' }, { amenity: 'university' }, { amenity: 'college' }, { amenity: 'kindergarten' },
    { tourism: 'museum' }, { amenity: 'theatre' }, { amenity: 'cinema' }, { amenity: 'arts_centre' },
    { amenity: 'place_of_worship', religion: 'christian' }, { amenity: 'bar' }, { amenity: 'pub' },
  ];
  const input = tags.map((value, index) => node(index + 100, { name: `Fonte ${index}`, ...value }));
  const result = buildAndroidPois({ elements: input }, bbox);
  assert.equal(result.collection.features.length, input.length);
  for (const original of input) {
    const feature = result.collection.features.find(item => item.properties.osm_id === `node/${original.id}`);
    assert.deepEqual(feature.properties.raw_tags, original.tags);
    assert.deepEqual(feature.geometry.coordinates, [original.lon, original.lat]);
    assert.equal(feature.properties.kind, classifyAndroidPoi(original.tags));
  }
});

test('a fuel site with a convenience shop has one category and unchanged raw provenance', () => {
  const source = node(1, { name: 'Posto Real', amenity: 'fuel', shop: 'convenience', kind: 'original-tag', opening_hours: '24/7' });
  const before = structuredClone(source);
  const result = buildAndroidPois({ elements: [source] }, bbox);
  assert.equal(result.collection.features.length, 1);
  const properties = result.collection.features[0].properties;
  assert.equal(properties.kind, 'fuel');
  assert.equal(properties.amenity, 'fuel');
  assert.equal(properties.shop, 'convenience');
  assert.equal(properties.raw_tags.kind, 'original-tag');
  assert.deepEqual(properties.raw_tags, source.tags);
  assert.deepEqual(properties.source_tags, [{ osm_id: 'node/1', tags: source.tags }]);
  assert.deepEqual(result.categoryConflicts, [{ osm_id: 'node/1', categories: ['fuel', 'shops'], selected: 'fuel' }]);
  assert.deepEqual(source, before);
});

test('shops are exported using their real tags, never the internal adapter category', () => {
  const tags = { name: 'Mercado da Fonte', shop: 'supermarket', 'addr:street': 'Rua Real', website: 'https://example.org/' };
  const result = buildAndroidPois({ elements: [node(2, tags)] }, bbox);
  const properties = result.collection.features[0].properties;
  assert.equal(properties.kind, 'shops');
  assert.equal(properties.shop, 'supermarket');
  assert.equal(properties.amenity, undefined);
  assert.equal(properties.name, tags.name);
  assert.equal(properties.source_url, 'https://www.openstreetmap.org/node/2');
  assert.deepEqual(properties.raw_tags, tags);
});

test('deduplication reuses node and area rules, preserves every original source tag', () => {
  const source = [
    node(1, { name: 'Café Real', amenity: 'cafe' }),
    way(2, { name: 'Café Real', amenity: 'cafe', cuisine: 'coffee_shop' }),
    node(3, { name: 'Café Real', amenity: 'cafe' }, 3.00001, 3),
    way(4, { name: 'Café Real', amenity: 'restaurant' }),
  ];
  const result = buildAndroidPois({ elements: source }, bbox);
  assert.equal(result.collection.features.length, 3);
  const retained = result.collection.features.find(feature => feature.id === 'node/1');
  assert.deepEqual(retained.properties.source_ids, ['node/1', 'way/2']);
  assert.deepEqual(retained.properties.source_tags, [
    { osm_id: 'node/1', tags: source[0].tags },
    { osm_id: 'way/2', tags: source[1].tags },
  ]);
  assert.equal(result.collection.features.find(feature => feature.id === 'way/4').properties.amenity, 'restaurant');
  assert.ok(result.collection.features.every(feature => feature.properties.kind === 'food'));
});

test('polygon points lie inside complete geometry, outside holes, and incomplete ways are omitted', () => {
  const source = {
    type: 'relation', id: 10, tags: { type: 'multipolygon', leisure: 'park', name: 'Parque Real' },
    members: [
      { type: 'way', role: 'outer', ref: 11, geometry: way(11, {}, [[1, 1], [9, 1], [9, 9], [1, 9], [1, 1]]).geometry },
      { type: 'way', role: 'inner', ref: 12, geometry: way(12, {}, [[2, 2], [8, 2], [8, 8], [2, 8], [2, 2]]).geometry },
    ],
  };
  const incomplete = way(13, { shop: 'convenience' }, [[1, 1], [3, 1], [3, 3]]);
  const result = buildAndroidPois({ elements: [source, incomplete] }, bbox);
  assert.equal(result.collection.features.length, 1);
  const feature = result.collection.features[0];
  assert.ok(polygonParts(source).some(rings => pointInPolygon(feature.geometry.coordinates, rings)));
  assert.equal(feature.properties.coordinate_source, 'Interior point from OSM polygon');
  assert.deepEqual(result.omitted.map(entry => entry.osm_id), ['way/13']);
});

test('dedicated query includes all nine groups from one attributable snapshot', () => {
  for (const value of ['fuel', 'parking', 'restaurant', 'cafe', 'fast_food', 'bar', 'pub', 'hospital', 'clinic', 'doctors', 'pharmacy', 'park', 'garden', 'supermarket', 'convenience', 'school', 'university', 'college', 'kindergarten', 'museum', 'theatre', 'cinema', 'arts_centre', 'place_of_worship']) {
    assert.ok(androidPoiQuery.includes(value), value);
  }
  assert.ok(androidPoiQuery.includes('out meta geom;'));
  assert.ok(!androidPoiQuery.includes('bank'));
});

test('prepared Android package matches cached OSM source, metadata and point geometry', async () => {
  const root = fileURLToPath(new URL('../', import.meta.url));
  const data = JSON.parse(await fs.readFile(root + 'data/android/pois.json', 'utf8'));
  const provenance = JSON.parse(await fs.readFile(root + 'data/android/pois.provenance.json', 'utf8'));
  const collection = JSON.parse(await fs.readFile(root + 'android/maps/pois.geojson', 'utf8'));
  const metadata = JSON.parse(await fs.readFile(root + 'android/maps/pois-metadata.json', 'utf8'));
  const originals = new Map(data.elements.map(element => [element.type + '/' + element.id, element]));
  assert.ok(!data.remark);
  assert.equal(provenance.dataTimestamp, data.osm3s.timestamp_osm_base);
  assert.equal(provenance.fetchedAt, metadata.fetchedAt);
  assert.equal(metadata.dataTimestamp, provenance.dataTimestamp);
  assert.equal(metadata.counts.total, collection.features.length);
  assert.equal(metadata.counts.total, Object.values(metadata.counts.byKind).reduce((sum, count) => sum + count, 0));
  assert.deepEqual(Object.keys(metadata.counts.byKind), androidKinds);
  assert.deepEqual(metadata.counts.byKind, Object.fromEntries(androidKinds.map(kind => [
    kind, collection.features.filter(feature => feature.properties.kind === kind).length,
  ])));
  const allSourceIds = [];
  for (const feature of collection.features) {
    const p = feature.properties, original = originals.get(p.osm_id);
    assert.equal(feature.type, 'Feature');
    assert.equal(feature.geometry.type, 'Point');
    assert.equal(p.kind, classifyAndroidPoi(original.tags));
    assert.deepEqual(p.raw_tags, original.tags);
    assert.equal(p.name, original.tags.name ?? original.tags['name:pt'] ?? '');
    const [lon, lat] = feature.geometry.coordinates;
    assert.ok(Number.isFinite(lon) && Number.isFinite(lat));
    assert.ok(lon >= metadata.bounds.west && lon <= metadata.bounds.east && lat >= metadata.bounds.south && lat <= metadata.bounds.north);
    if (original.type !== 'node') assert.ok(polygonParts(original).some(rings => pointInPolygon([lon, lat], rings)));
    for (const entry of p.source_tags) assert.deepEqual(entry.tags, originals.get(entry.osm_id).tags);
    assert.deepEqual(p.source_tags.map(entry => entry.osm_id), p.source_ids);
    allSourceIds.push(...p.source_ids);
  }
  assert.equal(new Set(allSourceIds).size, allSourceIds.length, 'one original element must not appear in two categories');
  assert.deepEqual(metadata.sourceIds, allSourceIds.sort());
  assert.ok(metadata.counts.byKind.shops > 0, 'shops need a dedicated query, not the old web subset');
  for (const category of ['education', 'culture', 'religion']) {
    assert.ok(metadata.counts.byKind[category] > 0 || metadata.dataCollection.notCollectedCategories.includes(category), category);
  }
  if (!metadata.dataCollection.completeForCurrentSpecification) {
    assert.ok(metadata.warnings.some(warning => warning.includes('No unrelated snapshot was merged')));
    assert.ok(Object.values(metadata.dataCollection.missingTagSelectors).some(values => values.length > 0));
  }
});
