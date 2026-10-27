import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { validateStyleMin, featureFilter } from '@maplibre/maplibre-gl-style-spec';

const builder = await import('../scripts/prepare-world-maps.mjs').catch(() => ({}));
const themes = ['gta', 'rosa', 'rdr2'];
const variants = themes.flatMap(theme => [theme, `${theme}-night`]);
const categories = ['parks', 'fuel', 'food', 'parking', 'health', 'shops'];
const asset = async mode => JSON.parse(await readFile(new URL(`../android/app/src/main/assets/maps/styles/world-${mode}.json`, import.meta.url), 'utf8'));

test('world builder produces six valid variants using the production planet service', async () => {
  assert.equal(typeof builder.buildWorldStyle, 'function', 'world cartography builder is missing');
  const styles = variants.map(builder.buildWorldStyle);
  for (const [index, style] of styles.entries()) {
    assert.deepEqual(validateStyleMin(style).map(error => error.message), []);
    assert.deepEqual(style.sources, styles[0].sources, 'themes must render the same real geometry');
    assert.equal(style.sources.world.type, 'vector');
    assert.equal(style.sources.world.url, 'https://tiles.openfreemap.org/planet');
    assert.deepEqual(style.sources.world.bounds, [-180, -85.05113, 180, 85.05113]);
    assert.equal(style.metadata.navskin.offline, false);
    assert.equal(style.metadata.navskin.coverage, 'world');
    assert.match(style.sources.world.attribution, /OpenFreeMap.*OpenMapTiles.*OpenStreetMap/);
    assert.equal(style.glyphs, 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf');
    assert.equal(style.sprite, `asset://maps/sprites/${variants[index].replace(/-night$/, '')}`);
    assert.deepEqual(await asset(variants[index]), style, 'generated asset is stale');
    assert.ok(!JSON.stringify(style).match(/http:|demotiles|api_key|geoapify|\/maps\/data\//i));
  }
  assert.throws(() => builder.buildWorldStyle('unknown'), /unknown.*world.*variant/i);
});

test('world POI filters classify the same six independent categories across all themes', () => {
  assert.equal(typeof builder.buildWorldStyle, 'function', 'world cartography builder is missing');
  const examples = {
    parks: [{ class: 'park', subclass: 'park' }, { class: 'garden', subclass: 'garden' }],
    fuel: [{ class: 'fuel', subclass: 'fuel' }],
    food: [{ class: 'restaurant', subclass: 'restaurant' }, { class: 'cafe', subclass: 'cafe' }, { class: 'fast_food', subclass: 'fast_food' }],
    parking: [{ class: 'parking', subclass: 'parking' }],
    health: [{ class: 'hospital', subclass: 'hospital' }, { class: 'hospital', subclass: 'clinic' }, { class: 'doctors', subclass: 'doctors' }, { class: 'pharmacy', subclass: 'pharmacy' }],
    shops: [{ class: 'grocery', subclass: 'supermarket' }, { class: 'shop', subclass: 'convenience' }],
  };
  const reference = builder.buildWorldStyle('gta');
  for (const mode of variants) {
    const style = builder.buildWorldStyle(mode);
    const poi = style.layers.filter(layer => layer.id.startsWith('poi-'));
    assert.deepEqual(poi.map(layer => layer.id).sort(), categories.map(id => `poi-${id}`).sort());
    assert.deepEqual(poi.map(layer => layer.filter), reference.layers.filter(layer => layer.id.startsWith('poi-')).map(layer => layer.filter));
    const filters = poi.map(layer => ({ category: layer.id.slice(4), matches: featureFilter(layer.filter, `layers[${style.layers.indexOf(layer)}].filter`).filter }));
    for (const [category, features] of Object.entries(examples)) {
      for (const properties of features) {
        assert.deepEqual(filters.filter(({ matches }) => matches({ zoom: 18 }, { type: 1, properties })).map(({ category }) => category), [category]);
      }
    }
    for (const properties of [{ class: 'school', subclass: 'school' }, { class: 'shop', subclass: 'clothes' }, { class: 'fuel', subclass: 'charging_station' }]) {
      assert.equal(filters.filter(({ matches }) => matches({ zoom: 18 }, { type: 1, properties })).length, 0);
    }
    for (const layer of poi) {
      assert.equal(layer.source, 'world');
      assert.equal(layer['source-layer'], 'poi');
      assert.equal(layer.layout['icon-image'], layer.id);
      assert.equal(layer.layout['icon-allow-overlap'], false);
      assert.equal(layer.layout['icon-ignore-placement'], false);
    }
  }
});

test('hiding world categories preserves park fills, buildings and geographic labels', () => {
  assert.equal(typeof builder.buildWorldStyle, 'function', 'world cartography builder is missing');
  for (const mode of variants) {
    const style = builder.buildWorldStyle(mode);
    const isMarker = layer => layer.type === 'circle' || Object.hasOwn(layer.layout ?? {}, 'icon-image');
    const before = structuredClone(style.layers.filter(layer => !layer.id.startsWith('poi-')));
    for (const layer of style.layers.filter(layer => layer.id.startsWith('poi-'))) layer.layout.visibility = 'none';
    assert.equal(style.layers.filter(layer => isMarker(layer) && layer.layout?.visibility !== 'none').length, 0);
    assert.deepEqual(style.layers.filter(layer => !layer.id.startsWith('poi-')), before);
    assert.equal(style.layers.find(layer => layer.id === 'vegetation-parks').type, 'fill');
    assert.equal(style.layers.find(layer => layer.id === 'building-footprints')['source-layer'], 'building');
    for (const id of ['road-labels', 'place-labels', 'district-labels']) {
      assert.equal(style.layers.find(layer => layer.id === id).type, 'symbol');
    }
  }
});

test('world roads preserve tunnels, ground roads and bridges without casing cuts at intersections', () => {
  assert.equal(typeof builder.buildWorldStyle, 'function', 'world cartography builder is missing');
  const style = builder.buildWorldStyle('gta');
  const roads = style.layers.filter(layer => layer.metadata?.road);
  const cases = [{ class: 'primary' }, { class: 'minor', brunnel: 'tunnel' }, { class: 'motorway', brunnel: 'bridge' }, { class: 'busway' }];
  for (const properties of cases) {
    const fills = roads.filter(layer => layer.metadata.part === 'fill' && featureFilter(layer.filter, `layers[${style.layers.indexOf(layer)}].filter`).filter({ zoom: 17 }, { type: 2, properties }));
    assert.equal(fills.length, 1, JSON.stringify(properties));
    assert.equal(fills[0].metadata.grade, properties.brunnel === 'bridge' ? 1 : properties.brunnel === 'tunnel' ? -1 : 0);
  }
  let previousEnd = -1;
  for (const grade of [-1, 0, 1]) {
    const indexes = part => style.layers.flatMap((layer, index) => layer.metadata?.road && layer.metadata.grade === grade && layer.metadata.part === part ? [index] : []);
    assert.ok(Math.min(...indexes('casing')) > previousEnd);
    assert.ok(Math.max(...indexes('casing')) < Math.min(...indexes('fill')));
    previousEnd = Math.max(...indexes('fill'));
  }
});

test('world themes keep their palettes and global font coverage without changing geometry', async () => {
  assert.equal(typeof builder.buildWorldStyle, 'function', 'world cartography builder is missing');
  const tokens = JSON.parse(await readFile(new URL('../android/maps/theme-tokens.json', import.meta.url), 'utf8'));
  for (const mode of variants) {
    const style = builder.buildWorldStyle(mode);
    const palette = tokens.palettes[mode];
    assert.equal(style.layers.find(layer => layer.id === 'background').paint['background-color'], palette.background);
    assert.equal(style.layers.find(layer => layer.id === 'vegetation-parks').paint['fill-color'], palette.park);
    assert.equal(style.layers.find(layer => layer.id === 'water-fill').paint['fill-color'], palette.water);
    for (const layer of style.layers.filter(layer => layer.layout?.['text-field'])) assert.deepEqual(layer.layout['text-font'], ['Noto Sans Bold']);
  }
});

test('prepared asset manifest declares online world and the separate bundled offline map', async () => {
  const manifest = JSON.parse(await readFile(new URL('../android/app/src/main/assets/maps/credits/asset-manifest.json', import.meta.url), 'utf8'));
  assert.equal(manifest.offline, false, 'a package with online world styles cannot claim to be fully offline');
  assert.equal(manifest.mapModes.world.offline, false);
  assert.equal(manifest.mapModes.goiania.offline, true);
  assert.deepEqual(manifest.mapModes.world.styleAssets.sort(), variants.map(mode => `styles/world-${mode}.json`).sort());
  assert.deepEqual(manifest.mapModes.goiania.styleAssets.sort(), variants.map(mode => `styles/${mode}.json`).sort());
  for (const mode of variants) assert.ok(manifest.files.some(file => file.path === `styles/world-${mode}.json`));
  assert.ok(manifest.files.some(file => file.path === 'credits/world-sources.json'));
  for (const file of ['scripts/prepare-world-maps.mjs', 'android/maps/world-source.json']) assert.ok(manifest.buildInputs.some(input => input.path === file));
});
