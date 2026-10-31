import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import sharp from 'sharp';

const root = path.resolve('ios/NavSkin/Resources');
const android = path.resolve('android/app/src/main/assets');
const themes = ['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk', 'pipboy', 'hollow', 'crossy'];
const hash = data => createHash('sha256').update(data).digest('hex');
async function walk(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? walk(path.join(directory, entry.name)) : path.join(directory, entry.name)))).flat();
}

test('iOS bundle retains original geographic bytes and all local map variants', async () => {
  const manifest = JSON.parse(await fs.readFile(path.join(root, 'bundle-manifest.json')));
  assert.equal(manifest.schemaVersion, 1);
  const packagedStyles = manifest.files.filter(file => /^maps\/styles\/(?!theme-tokens\.json$).+\.json$/.test(file.path)).map(file => file.path).sort();
  const expectedStyles = themes.flatMap(theme => ['', 'world-'].flatMap(prefix => ['', '-night'].map(night => `maps/styles/${prefix}${theme}${night}.json`))).sort();
  assert.deepEqual(packagedStyles, expectedStyles, 'Only active theme styles may be packaged');
  const previews = manifest.files.filter(file => /^maps\/previews\/.+\.png$/.test(file.path)).map(file => file.path).sort();
  assert.deepEqual(previews, themes.map(theme => `maps/previews/${theme}.png`).sort(), 'Only active theme previews may be packaged');
  for (const platformRoot of [root, android]) {
    const previewIndex = JSON.parse(await fs.readFile(path.join(platformRoot, 'maps/previews/index.json')));
    assert.deepEqual(previewIndex.previews.map(preview => preview.theme), themes, 'Preview metadata must contain only active themes');
    for (const preview of previewIndex.previews) {
      assert.equal(hash(await fs.readFile(path.join(platformRoot, 'maps/previews', preview.file))), preview.sha256);
      assert.equal(hash(await fs.readFile(path.join(platformRoot, `maps/styles/world-${preview.theme}.json`))), preview.styleSha256,
        `Preview must record the current ${preview.theme} style`);
    }
  }
  assert.ok(!manifest.files.some(file => /(?:^|\/)(?:minecraft|gta5)(?:\/|\.|-)/.test(file.path)), 'Removed theme assets must not remain in the bundle');
  const tiles = manifest.files.filter(file => file.path.endsWith('.pbf') && file.path.includes('/tiles/'));
  assert.equal(tiles.length, 729);
  for (const file of manifest.files.filter(file => /\.(pbf|geojson)$/.test(file.path))) {
    const actual = await fs.readFile(path.join(root, file.path));
    assert.equal(hash(actual), file.sha256, file.path);
    assert.deepEqual(actual, await fs.readFile(path.join(android, file.path)), file.path);
  }
  for (const theme of themes) for (const night of ['', '-night']) {
    const style = JSON.parse(await fs.readFile(path.join(root, 'maps/styles', `${theme}${night}.json`)));
    assert.equal(style.version, 8);
    assert.equal(style.sources.buildings.type, 'vector');
    assert.equal(style.layers.filter(layer => layer.id.startsWith('poi-')).length, 9);
    assert.ok(style.glyphs.includes('{fontstack}/{range}.pbf'));
    assert.ok(style.sources.buildings.tiles[0].includes('{z}/{x}/{y}.pbf'));
    assert.equal((await sharp(path.join(root, 'maps/previews', `${theme}.png`)).metadata()).format, 'png');
    const world = JSON.parse(await fs.readFile(path.join(root, 'maps/styles', `world-${theme}${night}.json`)));
    assert.equal(world.version, 8);
    assert.equal(world.sources.world.url, 'https://tiles.openfreemap.org/planet');
    assert.equal(world.glyphs, 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf');
    assert.equal(world.layers.filter(layer => layer.id.startsWith('poi-')).length, 9);
    for (const prefix of ['', 'world-']) {
      const relative = `maps/styles/${prefix}${theme}${night}.json`;
      const record = manifest.files.find(file => file.path === relative);
      assert.ok(record, `Bundle manifest is missing ${relative}`);
      const bytes = await fs.readFile(path.join(root, relative));
      assert.equal(hash(bytes), record.sha256, relative);
      assert.deepEqual(bytes, await fs.readFile(path.join(android, relative)), relative);
    }
  }
});

test('iOS catalog choices all resolve to actual transparent artwork in every theme', async () => {
  const catalog = JSON.parse(await fs.readFile(path.join(root, 'navigation-icons/catalog.json')));
  assert.equal(catalog.saved.length, 12);
  assert.equal(catalog.vehicles.length, 6);
  assert.deepEqual(catalog.themes, themes);
  for (const item of [...catalog.saved, ...catalog.vehicles]) for (const theme of themes) {
    const variant = item.themeAssets[theme];
    assert.ok(variant, `${item.id} has no ${theme} artwork`);
    for (const key of ['image', 'preview', 'svg']) assert.ok((await fs.stat(path.join(root, 'navigation-icons', variant[key]))).size > 0);
    const metadata = await sharp(path.join(root, 'navigation-icons', variant.preview)).metadata();
    assert.ok(metadata.hasAlpha);
    assert.equal(metadata.width, 96);
  }
});

test('checked-in Xcode project includes every app source and local resources', async () => {
  const project = await fs.readFile('ios/NavSkin.xcodeproj/project.pbxproj', 'utf8');
  for (const source of (await walk('ios/NavSkin')).filter(file => file.endsWith('.swift'))) {
    assert.ok(project.includes(path.relative('ios', source).replaceAll('\\', '/')), source);
  }
  assert.ok(project.includes('Packages/NavSkinCore'));
  assert.ok(project.includes('MapLibre'));
  assert.ok(project.includes('6.31.0'));
  assert.ok(project.includes('NavSkin/Resources'));
  assert.ok(project.includes('NavSkin/PrivacyInfo.xcprivacy'));
  assert.ok(project.includes('NavSkinTests'));
  const plist = await fs.readFile('ios/NavSkin/Info.plist', 'utf8');
  assert.ok(plist.includes('NSLocationWhenInUseUsageDescription'));
  assert.ok(plist.includes('<string>location</string>'));
  assert.ok(!plist.includes('NSAllowsArbitraryLoads'));
  assert.ok(!plist.includes('NSLocationAlways'));
});
