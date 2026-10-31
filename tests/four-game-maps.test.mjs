import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, readdir, mkdtemp, rm, cp, writeFile, access } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';
import sharp from 'sharp';
import { validateStyleMin, createExpression, featureFilter } from '@maplibre/maplibre-gl-style-spec';
import { buildWorldStyle, WORLD_VARIANTS } from '../scripts/prepare-world-maps.mjs';
import { prepareNavigationIcons } from '../scripts/prepare-navigation-icons.mjs';
import { buildNativeStyle } from '../scripts/prepare-android-maps.mjs';

const root = path.resolve(import.meta.dirname, '..');
const added = ['smw', 'cyberpunk'];
const themes = ['gta', 'rosa', 'rdr2', ...added, 'pipboy', 'hollow', 'crossy'];
const categories = ['parks', 'fuel', 'food', 'parking', 'health', 'shops', 'education', 'culture', 'religion'];
const titles = ['Super Mario World', 'Cyberpunk 2077'];
const evaluate = (expression, properties = {}, zoom = 17) => {
  const compiled = createExpression(expression, 'layers[0].layout.icon-image');
  assert.equal(compiled.result, 'success');
  return compiled.value.evaluate({ zoom }, { type: 1, properties });
};

test('draft Minecraft and GTA V cannot be built as selectable runtime styles', async () => {
  const tokens = JSON.parse(await readFile(path.join(root, 'android/maps/theme-tokens.json'), 'utf8'));
  for (const theme of ['minecraft', 'gta5']) {
    assert.ok(tokens.palettes[theme], 'draft source palettes remain available for later work');
    for (const mode of [theme, `${theme}-night`]) {
      assert.throws(() => buildWorldStyle(mode), /Unknown world map variant/);
      assert.throws(() => buildNativeStyle(mode, tokens), /Unknown native map variant/);
    }
  }
});

test('navigation preparation rejects inactive theme mappings before writing runtime assets', async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), 'navskin-active-themes-'));
  try {
    const input = path.join(temporary, 'source');
    const output = path.join(temporary, 'output');
    await cp(path.join(root, 'android/maps/catalogs'), input, { recursive: true });
    const file = path.join(input, 'catalog.json');
    const catalog = JSON.parse(await readFile(file, 'utf8'));
    catalog.saved[0].themeAssets.minecraft = {
      image: 'minecraft/saved/home.png', preview: 'minecraft/saved/home@2x.png', svg: 'minecraft/saved/home.svg',
    };
    await writeFile(file, JSON.stringify(catalog));
    await assert.rejects(prepareNavigationIcons({ sourceRoot: input, outputRoot: output }), /inactive theme mappings/);
    await assert.rejects(access(path.join(output, 'catalog.json')), { code: 'ENOENT' });
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});

test('illustrated map materials resolve locally without adding geographic sources or markers', async () => {
  const tokens = JSON.parse(await readFile(path.join(root, 'android/maps/theme-tokens.json'), 'utf8'));
  for (const theme of added) for (const night of [false, true]) {
    const mode = theme + (night ? '-night' : '');
    const atlas = JSON.parse(await readFile(path.join(root, `android/app/src/main/assets/maps/sprites/${theme}.json`), 'utf8'));
    for (const style of [buildWorldStyle(mode), buildNativeStyle(mode, tokens)]) {
      assert.deepEqual(validateStyleMin(style).map(item => item.message), []);
      const metadata = style.metadata.navskin ?? style.metadata.gamemap;
      assert.match(metadata.artDirection.geometry, /Original feature coordinates/);
      for (const layer of style.layers) {
        const pattern = layer.paint?.['fill-pattern'] ?? layer.paint?.['background-pattern'];
        if (pattern) {
          assert.ok(atlas[pattern], `${mode}/${layer.id}: material must exist in bundled atlas`);
          assert.ok([32, 64].includes(atlas[pattern].width), 'approved material keeps its power-of-two repeat');
        }
        if (layer.id.startsWith('art-')) {
          assert.ok(['fill', 'line'].includes(layer.type), 'illustration must not create fictional point markers');
          assert.ok(style.sources[layer.source]);
          assert.ok(style.layers.some(base => !base.id.startsWith('art-') && base.type === 'fill'
            && base.source === layer.source && base['source-layer'] === layer['source-layer']
            && JSON.stringify(base.filter) === JSON.stringify(layer.filter)), `${layer.id}: follows a real area footprint`);
        }
      }
      assert.equal(style.layers.filter(layer => layer.id.startsWith('poi-')).length, 9);
    }
  }
});

test('offline areas retain solid fills across native theme transitions while vector materials remain', async () => {
  const tokens = JSON.parse(await readFile(path.join(root, 'android/maps/theme-tokens.json'), 'utf8'));
  for (const theme of added) for (const night of ['', '-night']) {
    const style = buildNativeStyle(theme + night, tokens);
    for (const layer of style.layers.filter(layer => layer.type === 'fill' && style.sources[layer.source]?.type === 'geojson')) {
      assert.equal(layer.paint['fill-pattern'], undefined, `${theme}/${layer.id}: GeoJSON material buckets can disappear after a Native style switch`);
      assert.ok(layer.paint['fill-color'], `${theme}/${layer.id}: retain the actual area fill`);
    }
    assert.ok(style.layers.some(layer => layer.paint?.['fill-pattern'] && style.sources[layer.source]?.type === 'vector'), 'Building materials must remain');
    const world = buildWorldStyle(theme + night);
    assert.ok(world.layers.some(layer => layer.paint?.['fill-pattern']), 'World materials must remain');
  }
});

test('readable theme fonts retain POI caption zoom gates in both coverages', async () => {
  const tokens = JSON.parse(await readFile(path.join(root, 'android/maps/theme-tokens.json'), 'utf8'));
  for (const theme of ['gta', ...added]) {
    const threshold = theme === 'gta' ? tokens.labels.gta.poiNamesMinzoom : tokens.visibility.poiNamesMinzoom;
    for (const style of [buildWorldStyle(theme), buildNativeStyle(theme, tokens)]) {
      const labels = style.layers.filter(layer => layer.id.startsWith('poi-') && layer.layout?.['text-field']);
      assert.equal(labels.length, 9);
      for (const layer of labels) {
        assert.equal(evaluate(layer.layout['text-size'], {}, threshold - 0.1), 0, `${theme}/${layer.id}: captions hidden before street zoom`);
        assert.ok(evaluate(layer.layout['text-size'], {}, threshold + 0.1) >= 14.5);
      }
    }
  }
});

test('new offline styles retain the bundled sources, road topology and all semantic filters', async () => {
  const tokens = JSON.parse(await readFile(path.join(root, 'android/maps/theme-tokens.json'), 'utf8'));
  const reference = buildNativeStyle('rosa', tokens);
  for (const theme of added) for (const mode of [theme, `${theme}-night`]) {
    const style = buildNativeStyle(mode, tokens);
    assert.deepEqual(validateStyleMin(style).map(item => item.message), []);
    assert.deepEqual(style.sources, reference.sources);
    assert.equal(style.metadata.gamemap.offline, true);
    assert.equal(style.sprite, `asset://maps/sprites/${theme}`);
    assert.equal(style.glyphs, 'asset://maps/glyphs/{fontstack}/{range}.pbf');
    for (const id of categories) {
      const layer = style.layers.find(item => item.id === `poi-${id}`);
      assert.deepEqual(layer.filter, ['==', ['get', 'kind'], id]);
      assert.equal(evaluate(layer.layout['icon-image'], { kind: id }), `poi-${id}`);
    }
    const topology = value => value.layers.filter(layer => layer.metadata?.road).map(layer => ({ id: layer.id, filter: layer.filter, metadata: layer.metadata }));
    assert.deepEqual(topology(style), topology(reference));
    assert.deepEqual(style.layers.find(layer => layer.id === 'road-oneway-directions').filter,
      reference.layers.find(layer => layer.id === 'road-oneway-directions').filter);
  }
});

test('all five themes build day and night world maps with identical real geography and category semantics', () => {
  assert.deepEqual(WORLD_VARIANTS, themes.flatMap(theme => [theme, `${theme}-night`]));
  const reference = buildWorldStyle('rosa');
  const backgrounds = new Set();
  for (const [index, theme] of added.entries()) for (const mode of [theme, `${theme}-night`]) {
    const style = buildWorldStyle(mode);
    assert.match(style.name, new RegExp(titles[index]));
    assert.deepEqual(validateStyleMin(style).map(item => item.message), []);
    assert.deepEqual(style.sources, reference.sources);
    assert.equal(style.sprite, `asset://maps/sprites/${theme}`);
    assert.equal(style.metadata.navskin.sourceCoordinatesModified, false);
    assert.equal(style.metadata.navskin.offline, false);
    const groups = style.layers.filter(layer => layer.id.startsWith('poi-'));
    assert.deepEqual(groups.map(layer => layer.id).sort(), categories.map(id => `poi-${id}`).sort());
    for (const layer of groups) {
      assert.deepEqual(layer.filter, reference.layers.find(item => item.id === layer.id).filter);
      assert.equal(evaluate(layer.layout['icon-image'], { subclass: 'unknown' }), layer.id);
      assert.equal(layer.layout['icon-allow-overlap'], false);
    }
    const direction = style.layers.find(layer => layer.id === 'road-oneway-directions');
    assert.ok(featureFilter(direction.filter, 'layers[0].filter').filter({ zoom: 17 }, { type: 2, properties: { class: 'minor', oneway: -1 } }));
    assert.equal(evaluate(direction.layout['icon-rotate'], { oneway: -1 }), 180);
    backgrounds.add(style.layers.find(layer => layer.id === 'background').paint['background-color']);
  }
  assert.equal(backgrounds.size, 4, 'every new appearance needs a distinct cartographic palette');
});

test('new theme sprites cover every category, subtype and traffic direction with visible standalone authored artwork', async () => {
  const required = ['road-oneway-forward', ...categories.map(id => `poi-${id}`),
    'poi-cafe', 'poi-fast-food', 'poi-restaurant', 'poi-bar', 'poi-pharmacy', 'poi-hospital',
    'poi-clinic', 'poi-school', 'poi-museum', 'poi-theatre', 'poi-worship'];
  const hashes = new Set();
  for (const theme of added) {
    const folder = path.join(root, 'android/maps/icons', theme);
    const files = await readdir(folder);
    for (const id of required) {
      assert.ok(files.includes(`${id}.svg`), `${theme}/${id} must resolve without a missing-image placeholder`);
      const svg = await readFile(path.join(folder, `${id}.svg`), 'utf8');
      assert.match(svg, /NavSkin authored/i);
      assert.doesNotMatch(svg, /<(?:image|text|foreignObject)\b/);
      const pixels = await sharp(Buffer.from(svg)).resize(32, 32).ensureAlpha().raw().toBuffer();
      const alpha = [...pixels].filter((_, i) => i % 4 === 3);
      assert.ok(alpha.filter(value => value > 64).length > 50, `${theme}/${id} must remain visible at map size`);
      assert.ok(alpha.some(value => value === 0), `${theme}/${id} needs transparent padding`);
      hashes.add(createHash('sha256').update(pixels).digest('hex'));
    }
  }
  assert.ok(hashes.size >= 38, 'the theme families and semantic subtypes must not collapse into duplicate icons');
});

test('navigation generator emits all five skins while retaining saved and vehicle preference IDs', async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), 'navskin-four-maps-'));
  try {
    const catalog = await prepareNavigationIcons({ outputRoot: temporary });
    assert.deepEqual(catalog.themes, themes);
    const directories = (await readdir(temporary, { withFileTypes: true })).filter(item => item.isDirectory()).map(item => item.name);
    assert.deepEqual(directories.sort(), [...themes].sort(), 'inactive theme directories must not be emitted');
    assert.deepEqual(catalog.saved.map(item => item.id), ['home', 'work', 'favorite', 'restaurant', 'cafe', 'park', 'shopping', 'gym', 'health', 'parking', 'meeting', 'special']);
    assert.deepEqual(catalog.vehicles.map(item => item.id), ['arrow', 'compact', 'sport', 'suv', 'motorcycle', 'themed']);
    const images = new Set();
    for (const kind of ['saved', 'vehicles']) for (const item of catalog[kind]) {
      assert.deepEqual(Object.keys(item.themeAssets), themes);
      for (const asset of Object.values(item.themeAssets)) {
        images.add(asset.image); images.add(asset.preview);
      }
    }
    assert.equal(images.size, 288, 'eight skins retain 144 choices with normal and preview PNGs');
    const provenance = JSON.parse(await readFile(path.join(temporary, 'provenance.json'), 'utf8'));
    assert.deepEqual(provenance.authoredThemes, [...added, 'pipboy', 'hollow', 'crossy']);
    for (const theme of added) for (const kind of ['saved', 'vehicles']) for (const item of catalog[kind]) {
      const asset = item.themeAssets[theme];
      assert.equal(asset.origin, 'navskin-authored');
      for (const [file, size] of [[asset.image, 48], [asset.preview, 96]]) {
        const image = sharp(await readFile(path.join(temporary, file)));
        const metadata = await image.metadata();
        assert.equal(metadata.width, size);
        assert.equal(metadata.height, size);
        assert.equal(metadata.hasAlpha, true);
        const pixels = await image.ensureAlpha().raw().toBuffer();
        let minX = size, minY = size, maxX = -1, maxY = -1;
        for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (pixels[(y * size + x) * 4 + 3] > 16) {
          minX = Math.min(minX, x); minY = Math.min(minY, y);
          maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        assert.ok(minX > 0 && minY > 0 && maxX < size - 1 && maxY < size - 1, `${file}: artwork must not clip`);
        if (kind === 'vehicles') {
          assert.ok(Math.abs((minX + maxX) / 2 - (size - 1) / 2) <= size * 0.045, `${file}: horizontal rotation anchor`);
          assert.ok(Math.abs((minY + maxY) / 2 - (size - 1) / 2) <= size * 0.06, `${file}: vertical rotation anchor`);
        }
      }
    }
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});
