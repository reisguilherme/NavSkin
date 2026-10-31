import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import sharp from 'sharp';
import { buildNativeStyle } from '../scripts/prepare-android-maps.mjs';
import { buildWorldStyle } from '../scripts/prepare-world-maps.mjs';
const read = async file => JSON.parse(await fs.readFile(file, 'utf8'));

test('GTA road readability calibration survives the art pass in offline and world coverage', async () => {
  const tokens = await read('android/maps/theme-tokens.json');
  const calibratedSize = ['interpolate', ['linear'], ['zoom'], 14, 16, 16, 17, 18, 19];
  for (const variant of ['gta', 'gta-night']) {
    for (const style of [buildNativeStyle(variant, tokens), buildWorldStyle(variant)]) {
      const road = style.layers.find(layer => layer.id === 'road-labels');
      assert.deepEqual(road.layout['text-size'], calibratedSize, `${variant}: small street names must retain the phone readability calibration`);
      assert.equal(road.layout['text-letter-spacing'], 0.02);
      assert.deepEqual(road.layout['text-font'], ['NavSkinGtaRoad']);
    }
  }
});

test('approved first-five drawings ship material and contour passes over real geometry', async () => {
  for (const theme of ['gta', 'rosa', 'rdr2', 'cyberpunk']) {
    const style = await read(`android/app/src/main/assets/maps/styles/world-${theme}.json`);
    const shadow = style.layers.find(layer => layer.id === 'lab-roof-shadow-building-footprints');
    const footprint = style.layers.find(layer => layer.id === 'building-footprints');
    assert.ok(shadow, `${theme}: approved roof illustration is in the app`);
    assert.equal(shadow.source, footprint.source);
    assert.equal(shadow['source-layer'], footprint['source-layer']);
    assert.deepEqual(shadow.filter, footprint.filter);
    assert.ok(style.layers.some(layer => layer.id === 'lab-park-edge-vegetation-parks'));
    assert.equal((style.metadata.navskin ?? style.metadata.gamemap).artDirection.source, 'scripts/theme-art/five-worlds.mjs');
  }
});

test('Pink runtime toy icons and character car derive exactly from the approved SVGs', async () => {
  const catalog = await read('android/app/src/main/assets/navigation-icons/catalog.json');
  const car = catalog.vehicles.find(item => item.id === 'themed').themeAssets.rosa;
  assert.equal(car.vehicleHeading, 'north');
  assert.match(car.license, /Sanrio.*excluded from CC0/);
  const runtimeSvg = await fs.readFile('android/app/src/main/assets/navigation-icons/' + car.svg);
  assert.deepEqual(runtimeSvg, await fs.readFile(car.source));
  const expected = await sharp(runtimeSvg).resize(96, 96).ensureAlpha().raw().toBuffer();
  const actual = await sharp('android/app/src/main/assets/navigation-icons/' + car.preview).ensureAlpha().raw().toBuffer();
  assert.deepEqual(actual, expected);
  for (const category of ['parks','health','food','fuel','shops','parking','education','culture','religion']) {
    assert.deepEqual(await fs.readFile(`android/maps/icons/rosa/poi-${category}.svg`),
      await fs.readFile(`scripts/theme-art/pink-icons/${category}.svg`));
  }
});
