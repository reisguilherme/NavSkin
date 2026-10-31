import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { validateStyleMin, createExpression } from '@maplibre/maplibre-gl-style-spec';
import { MAP_THEMES, buildWorldStyle } from '../scripts/prepare-world-maps.mjs';
import { buildNativeStyle } from '../scripts/prepare-android-maps.mjs';
import { collectionImages } from '../scripts/collection-art.mjs';
import sharp from 'sharp';

const themes = ['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk', 'pipboy', 'hollow', 'crossy'];
const tokens = JSON.parse(await fs.readFile(new URL('../android/maps/theme-tokens.json', import.meta.url), 'utf8'));
test('eight approved themes expose optional 3D real footprints in both coverages and appearances', () => {
  assert.deepEqual(Object.keys(MAP_THEMES), themes);
  for (const theme of themes) for (const night of ['', '-night']) {
    for (const style of [buildWorldStyle(theme + night), buildNativeStyle(theme + night, tokens)]) {
      assert.deepEqual(validateStyleMin(style).map(error => error.message), []);
      const volumes = style.layers.filter(layer => layer.type === 'fill-extrusion');
      assert.equal(volumes.length, style.sources.world ? 2 : 1);
      for (const layer of volumes) {
        assert.ok(layer.id.startsWith('navskin-3d-'));
        assert.equal(layer.layout.visibility, 'none');
        assert.notEqual(layer.source, 'areas', 'hidden OSM duplicate buildings must never be extruded');
        const base = style.layers.find(item => item.id === layer.metadata.navskin3d.footprintLayer);
        assert.equal(base.type, 'fill');
        assert.equal(layer.source, base.source);
        assert.equal(layer['source-layer'], base['source-layer']);
        assert.deepEqual(layer.filter, base.filter);
        const expression = createExpression(layer.paint['fill-extrusion-height'], 'layers[0].paint.fill-extrusion-height');
        assert.equal(expression.result, 'success');
        const evaluate = properties => expression.value.evaluate({zoom: 17}, {type: 3, id: 45, properties});
        assert.equal(evaluate({height: 18}), 18);
        assert.equal(evaluate({render_height: 21}), 21);
        assert.equal(evaluate({'building:levels': 4}), 12);
        assert.equal(evaluate({}), evaluate({}));
        assert.ok(evaluate({}) >= 5 && evaluate({}) <= 25);
        assert.ok(evaluate({height: 10000}) <= 120);
      }
    }
  }
});

test('promoted collection sprites preserve approved pixel dimensions, glyphs and decorative geometry', async () => {
  const expectedDrawing = { pipboy: 'pipboy-building-wire-', hollow: 'hollow-room-pencil-', crossy: 'crossy-shadow-' };
  for (const theme of ['pipboy', 'hollow', 'crossy']) {
    const images = await collectionImages(theme);
    const index = JSON.parse(await fs.readFile(new URL(`../android/app/src/main/assets/maps/sprites/${theme}@2x.json`, import.meta.url), 'utf8'));
    const atlas = new URL(`../android/app/src/main/assets/maps/sprites/${theme}@2x.png`, import.meta.url);
    for (const [id, image] of Object.entries(images)) {
      const entry = index[id];
      assert.ok(entry, `${theme}/${id}`);
      assert.equal(entry.width, image.width);
      assert.equal(entry.height, image.height);
      const pixels = await sharp(await fs.readFile(atlas)).extract({ left: entry.x, top: entry.y, width: entry.width, height: entry.height }).ensureAlpha().raw().toBuffer();
      assert.deepEqual(pixels, Buffer.from(image.data), `${theme}/${id}: approved artwork pixels survive atlas packing`);
    }
    const reference = buildWorldStyle('rosa');
    for (const night of ['', '-night']) {
      const offline = buildNativeStyle(theme + night, tokens);
      for (const layer of offline.layers.filter(layer => layer.type === 'fill' && offline.sources[layer.source]?.type === 'geojson')) {
        assert.equal(layer.paint['fill-pattern'], undefined, 'Native GeoJSON keeps solid areas across repeated style switches');
        assert.ok(layer.paint['fill-color']);
      }
      const style = buildWorldStyle(theme + night);
      assert.deepEqual(style.sources, reference.sources);
      assert.ok(style.layers.some(layer => layer.id.startsWith(expectedDrawing[theme])));
      assert.ok(style.layers.some(layer => layer.paint?.['fill-pattern']), 'material rendering is preserved');
      for (const layer of style.layers.filter(layer => layer.metadata?.road)) {
        assert.deepEqual(layer.filter, reference.layers.find(item => item.id === layer.id).filter, 'real road grades retained');
      }
      for (const layer of style.layers.filter(layer => layer.id.startsWith('poi-'))) {
        assert.deepEqual(layer.filter, reference.layers.find(item => item.id === layer.id).filter, 'real POI identity retained');
      }
    }
  }
});
