import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { validateStyleMin, featureFilter } from '@maplibre/maplibre-gl-style-spec';
import { PbfReader } from 'pbf';
import sharp from 'sharp';

const root = new URL('../', import.meta.url);
const assetRoot = new URL('android/app/src/main/assets/maps/', root);
const categories = ['parks', 'fuel', 'food', 'parking', 'health', 'shops'];
const themes = ['gta', 'rosa', 'rdr2'];
const variants = [...themes, ...themes.map(theme => `${theme}-night`)];
const json = async path => JSON.parse(await readFile(new URL(path, root), 'utf8'));
const asset = async path => JSON.parse(await readFile(new URL(path, assetRoot), 'utf8'));
const hash = data => createHash('sha256').update(data).digest('hex');

test('native filters cover every actual road grade and the real Praça Cívica park', async () => {
  const roads = await json('public/data/roads.geojson');
  const areas = await json('public/data/areas.geojson');
  for (const road of roads.features) assert.ok(Number.isInteger(road.properties.grade) && Math.abs(road.properties.grade) <= 5, road.id);
  for (const mode of variants) {
    const style = await asset(`styles/${mode}.json`);
    const drawLayers = style.layers.filter(layer => layer.source === 'roads' && (layer.metadata?.part === 'fill' || /^(paths|railway)-/.test(layer.id)));
    const compiled = drawLayers.map(layer => ({ layer, filter: featureFilter(layer.filter, `layers[${style.layers.indexOf(layer)}].filter`).filter }));
    const matchedGrades = {};
    for (const feature of roads.features) {
      const matching = compiled.filter(({ filter }) => filter({ zoom: 17 }, { type: 2, properties: feature.properties }));
      assert.equal(matching.length, 1, `${feature.id}: kind=${feature.properties.kind}, grade=${feature.properties.grade}`);
      const grade = matching[0].layer.metadata?.grade ?? Number(matching[0].layer.id.match(/^(?:paths|railway)-(-?\d+)$/)[1]);
      assert.equal(grade, feature.properties.grade);
      matchedGrades[grade] = (matchedGrades[grade] ?? 0) + 1;
    }
    assert.deepEqual(matchedGrades, { '-1': 24, '0': 22867, '1': 182, '2': 2 });
    const park = style.layers.find(layer => layer.id === 'vegetation-parks');
    const parkFilter = featureFilter(park.filter, `layers[${style.layers.indexOf(park)}].filter`).filter;
    const civic = areas.features.find(feature => feature.properties.osm_id === 'way/670650221');
    assert.ok(civic, 'real Praça Cívica park source must be present');
    assert.ok(parkFilter({ zoom: 17 }, { type: 3, properties: civic.properties }), 'Civic park must match native green-area filter');
    const kinds = new Set(areas.features.filter(feature => parkFilter({ zoom: 17 }, { type: 3, properties: feature.properties })).map(feature => feature.properties.kind));
    assert.ok(kinds.has('park'));
  }
});

test('all day/night native variants satisfy the schema and share geographic sources', async () => {
  const styles = await Promise.all(variants.map(mode => asset(`styles/${mode}.json`)));
  for (const style of styles) {
    assert.deepEqual(style.sources, styles[0].sources);
    assert.deepEqual(validateStyleMin(style).map(error => error.message), []);
    assert.equal(style.glyphs, 'asset://maps/glyphs/{fontstack}/{range}.pbf');
    assert.match(style.sprite, /^asset:\/\/maps\/sprites\/(gta|rosa|rdr2)$/);
    for (const source of Object.values(style.sources)) {
      if (source.type === 'geojson') assert.match(source.data, /^asset:\/\/maps\/data\/.+\.geojson$/);
      else assert.deepEqual(source.tiles, ['asset://maps/tiles/buildings/{z}/{x}/{y}.pbf']);
    }
    assert.ok(style.metadata.gamemap.attribution.includes('OpenStreetMap'));
    assert.ok(style.metadata.gamemap.attribution.includes('Google'));
    assert.ok(style.metadata.gamemap.attribution.includes('Microsoft'));
    for (const layer of style.layers.filter(l => l.type === 'symbol' && l.layout['text-field'])) {
      assert.deepEqual(layer.layout['text-font'], [style.metadata.gamemap.theme === 'rdr2' ? 'GameMapSerif' : 'GameMapSans']);
    }
  }
});

test('six independent POI layers cover every real POI once and keep green areas separate', async () => {
  const pois = await json('android/maps/pois.geojson');
  assert.equal(pois.features.length, 1571);
  for (const mode of variants) {
    const style = await asset(`styles/${mode}.json`);
    const layers = style.layers.filter(layer => layer.source === 'pois');
    assert.deepEqual(layers.map(l => l.id).sort(), categories.map(c => `poi-${c}`).sort());
    const filters = layers.map((layer, i) => featureFilter(layer.filter, `layers[${i}].filter`).filter);
    for (const feature of pois.features) {
      assert.equal(filters.filter(filter => filter({ zoom: 17 }, { type: 1, properties: feature.properties })).length, 1, feature.id);
    }
    const green = style.layers.find(layer => layer.id === 'vegetation-parks');
    assert.equal(green.source, 'areas');
    assert.notEqual(green.layout?.visibility, 'none');
    for (const layer of layers) {
      assert.equal(layer.layout.visibility, 'visible');
      assert.equal(layer.layout['icon-allow-overlap'], false);
      assert.equal(layer.layout['icon-ignore-placement'], false);
      assert.ok(layer.minzoom >= 12);
    }
  }
});

test('hiding all six categories removes every marker while preserving geographic text and park fills', async () => {
  const categoryIds = categories.map(category => `poi-${category}`).sort();
  const isMarker = layer => layer.type === 'circle' || (layer.type === 'symbol' && Object.hasOwn(layer.layout ?? {}, 'icon-image'));
  const visible = layer => layer.layout?.visibility !== 'none';
  for (const mode of variants) {
    const style = await asset(`styles/${mode}.json`);
    const markers = style.layers.filter(isMarker);
    assert.deepEqual(markers.map(layer => layer.id).sort(), categoryIds, `${mode}: category-independent or duplicate marker`);
    assert.ok(markers.every(layer => layer.source === 'pois' && layer.type === 'symbol'));
    for (const marker of markers) marker.layout.visibility = 'none';
    assert.deepEqual(style.layers.filter(layer => visible(layer) && isMarker(layer)), [], `${mode}: marker survives all categories disabled`);
    assert.deepEqual(style.layers.filter(layer => visible(layer) && layer.type === 'symbol').map(layer => layer.id).sort(), ['district-labels', 'place-labels', 'road-labels']);
    for (const label of style.layers.filter(layer => layer.source === 'places')) {
      assert.equal(label.type, 'symbol');
      assert.ok(label.layout['text-field']);
      assert.ok(!Object.hasOwn(label.layout, 'icon-image'));
    }
    assert.equal(style.layers.find(layer => layer.id === 'vegetation-parks').type, 'fill');
    assert.ok(visible(style.layers.find(layer => layer.id === 'vegetation-parks')));
  }
});

test('source GeoJSON bytes and prepared building MVTs are copied without coordinate edits', async () => {
  for (const name of ['areas', 'roads', 'waterways', 'places', 'pois']) {
    const source = new URL(name === 'pois' ? 'android/maps/pois.geojson' : `public/data/${name}.geojson`, root);
    assert.equal(hash(await readFile(new URL(`data/${name}.geojson`, assetRoot))), hash(await readFile(source)), name);
  }
  const manifest = await asset('credits/asset-manifest.json');
  const tiles = manifest.files.filter(file => /tiles\/buildings\/\d+\/\d+\/\d+\.pbf$/.test(file.path));
  assert.equal(tiles.length, 729);
  assert.equal(tiles.reduce((total, tile) => total + tile.bytes, 0), 117785969);
  for (const tile of tiles) {
    assert.equal(hash(await readFile(new URL(tile.path, assetRoot))), tile.sha256, tile.path);
  }
  assert.ok(!manifest.files.some(file => file.path.endsWith('buildings.geojson')));
  for (const mode of variants) {
    const layer = (await asset(`styles/${mode}.json`)).layers.find(l => l.id === 'building-footprints');
    assert.equal(layer.minzoom, 13.4);
    assert.equal(layer['source-layer'], 'buildings');
  }
});

function glyphIds(buffer) {
  const ids = new Set();
  new PbfReader(buffer).readFields((tag, _, pbf) => {
    if (tag !== 1) return;
    pbf.readMessage((stackTag, stack, stackPbf) => {
      if (stackTag !== 3) return;
      stackPbf.readMessage((glyphTag, glyph, glyphPbf) => {
        if (glyphTag === 1) ids.add(glyphPbf.readVarint());
      }, {});
    }, {});
  }, {});
  return ids;
}

test('local Noto glyph PBFs cover all label codepoints including uppercase and accents', async () => {
  const needed = new Set();
  for (const name of ['roads', 'places', 'pois']) {
    const data = await asset(`data/${name}.geojson`);
    for (const feature of data.features) {
      const label = feature.properties.name ?? '';
      for (const char of label + label.toUpperCase()) needed.add(char.codePointAt(0));
    }
  }
  const blocks = [...new Set([...needed].map(cp => Math.floor(cp / 256) * 256))];
  for (const font of ['GameMapSans', 'GameMapSerif']) {
    const provided = new Set();
    for (const start of blocks) {
      const path = new URL(`glyphs/${font}/${start}-${start + 255}.pbf`, assetRoot);
      for (const id of glyphIds(await readFile(path))) provided.add(id);
    }
    assert.deepEqual([...needed].filter(cp => !provided.has(cp)), [], font);
  }
  assert.match(await readFile(new URL('credits/Noto-OFL-1.1.md', assetRoot), 'utf8'), /SIL OPEN FONT LICENSE Version 1\.1/);
  assert.match(await readFile(new URL('credits/Noto-Serif-OFL-1.1.md', assetRoot), 'utf8'), /SIL OPEN FONT LICENSE Version 1\.1/);
});

test('both 1x and 2x sprite atlases contain six different editable category icons', async () => {
  for (const mode of themes) {
    for (const ratio of [1, 2]) {
      const suffix = ratio === 2 ? '@2x' : '';
      const index = await asset(`sprites/${mode}${suffix}.json`);
      const png = await sharp(await readFile(new URL(`sprites/${mode}${suffix}.png`, assetRoot))).metadata();
      assert.deepEqual(Object.keys(index).sort(), categories.map(c => `poi-${c}`).sort());
      for (const entry of Object.values(index)) {
        assert.equal(entry.pixelRatio, ratio);
        assert.equal(entry.width, 32 * ratio);
        assert.ok(entry.x + entry.width <= png.width);
        assert.ok(entry.y + entry.height <= png.height);
      }
    }
    const hashes = [];
    for (const category of categories) {
      const svg = await readFile(new URL(`android/maps/icons/${mode}/poi-${category}.svg`, root), 'utf8');
      assert.match(svg, /<svg/);
      assert.ok((await stat(new URL(`android/maps/icons/${mode}/poi-${category}.svg`, root))).size > 200);
      hashes.push(hash(svg));
    }
    assert.equal(new Set(hashes).size, 6);
  }
});

test('GTA preserves approved palette and every day theme has distinct cartography with the same road grades', async () => {
  const poc = await json('public/styles/tokens.json');
  const native = await json('android/maps/theme-tokens.json');
  assert.deepEqual(native.palettes.gta, poc.palettes.gta);
  assert.deepEqual(native.roads, poc.roads);
  assert.deepEqual(native.zoomStops, poc.zoomStops);
  assert.notEqual(native.palettes.rosa.roadOutline, native.palettes.gta.roadOutline);
  const styles = await Promise.all(themes.map(theme => asset(`styles/${theme}.json`)));
  for (const style of styles) {
    let previous = -1;
    for (let grade = -5; grade <= 5; grade++) {
      const casings = style.layers.map((l, i) => l.metadata?.grade === grade && l.metadata?.part === 'casing' ? i : -1).filter(i => i >= 0);
      const fills = style.layers.map((l, i) => l.metadata?.grade === grade && l.metadata?.part === 'fill' ? i : -1).filter(i => i >= 0);
      assert.ok(Math.min(...casings) > previous);
      assert.ok(Math.max(...casings) < Math.min(...fills));
      previous = Math.max(...fills);
    }
  }
  for (const id of ['landuse-urban', 'water-fill', 'vegetation-parks', 'building-footprints', 'road-0-primary-fill', 'place-labels']) {
    const paints = styles.map(style => JSON.stringify(style.layers.find(l => l.id === id).paint));
    assert.equal(new Set(paints).size, 3, id);
  }
});

const luminance = color => color.slice(1).match(/../g).map(value => Number.parseInt(value, 16) / 255)
  .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  .reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index], 0);
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + 0.05) / (Math.min(luminance(a), luminance(b)) + 0.05);

test('host night variants retain the same three themes, geometry, symbols and readable palettes', async () => {
  const tokens = await json('android/maps/theme-tokens.json');
  const withoutPaint = style => style.layers.map(({ paint, ...layer }) => layer);
  for (const theme of themes) {
    const day = await asset(`styles/${theme}.json`);
    const night = await asset(`styles/${theme}-night.json`);
    const p = tokens.palettes[`${theme}-night`];
    assert.deepEqual(night.sources, day.sources);
    assert.deepEqual(withoutPaint(night), withoutPaint(day));
    assert.equal(night.sprite, day.sprite);
    assert.equal(night.glyphs, day.glyphs);
    assert.equal(night.metadata.gamemap.theme, theme);
    assert.equal(night.metadata.gamemap.appearance, 'night');
    assert.ok(luminance(p.urban) < luminance(tokens.palettes[theme].urban), `${theme}: dim urban area`);
    assert.ok(luminance(p.building) < luminance(tokens.palettes[theme].building), `${theme}: dim footprints`);
    assert.ok(contrast(p.label, p.labelHalo) >= 7, `${theme}: label/halo contrast`);
    assert.ok(contrast(p.road, p.urban) >= 3, `${theme}: road/urban contrast`);
    for (const category of categories) {
      const layer = night.layers.find(layer => layer.id === `poi-${category}`);
      assert.equal(layer.layout.visibility, 'visible');
      assert.equal(layer.layout['icon-image'], `poi-${category}`);
      assert.deepEqual(layer.layout, day.layers.find(dayLayer => dayLayer.id === `poi-${category}`).layout);
    }
    assert.notEqual(night.layers.find(layer => layer.id === 'water-fill').paint['fill-color'], day.layers.find(layer => layer.id === 'water-fill').paint['fill-color']);
  }
});

test('Rosa stays candy pink and mint in both appearances while RDR2 uses parchment and dark ink', async () => {
  const tokens = await json('android/maps/theme-tokens.json');
  const channels = color => color.slice(1).match(/../g).map(v => Number.parseInt(v, 16));
  for (const mode of ['rosa', 'rosa-night']) {
    const p = tokens.palettes[mode];
    const [r, g, b] = channels(p.urban);
    assert.ok(Math.min(r, b) > g * 1.1, `${mode}: pink/lilac urban fabric`);
    const [pr, pg, pb] = channels(p.park);
    assert.ok(pg > pr * 1.5 && pb > pr * 1.5, `${mode}: saturated mint parks`);
    const [wr, wg, wb] = channels(p.water);
    assert.ok(wg > wr * 1.5 && wb > wr * 1.5, `${mode}: turquoise water`);
  }
  for (const mode of ['rdr2', 'rdr2-night']) {
    const style = await asset(`styles/${mode}.json`);
    const p = tokens.palettes[mode];
    assert.ok(luminance(p.road) < luminance(p.urban) / 3, `${mode}: dark ink roads on parchment`);
    assert.deepEqual(Object.keys(style.sources).sort(), ['areas', 'buildings', 'places', 'pois', 'roads', 'waterways']);
    for (const [id, token] of [['vegetation-parks', 'park'], ['water-fill', 'water'], ['building-footprints', 'building']]) {
      assert.equal(style.layers.find(l => l.id === id).paint['fill-color'], p[token]);
    }
    assert.equal(style.layers.find(l => l.id === 'road-0-primary-fill').paint['line-color'], p.primary);
  }
});
