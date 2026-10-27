import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import sharp from 'sharp';

const root = path.resolve('ios/NavSkin/Resources');
const android = path.resolve('android/app/src/main/assets');
const hash = data => createHash('sha256').update(data).digest('hex');
async function walk(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? walk(path.join(directory, entry.name)) : path.join(directory, entry.name)))).flat();
}

test('iOS bundle retains original geographic bytes and all local map variants', async () => {
  const manifest = JSON.parse(await fs.readFile(path.join(root, 'bundle-manifest.json')));
  assert.equal(manifest.schemaVersion, 1);
  const tiles = manifest.files.filter(file => file.path.endsWith('.pbf') && file.path.includes('/tiles/'));
  assert.equal(tiles.length, 729);
  for (const file of manifest.files.filter(file => /\.(pbf|geojson)$/.test(file.path))) {
    const actual = await fs.readFile(path.join(root, file.path));
    assert.equal(hash(actual), file.sha256, file.path);
    assert.deepEqual(actual, await fs.readFile(path.join(android, file.path)), file.path);
  }
  for (const theme of ['gta', 'rosa', 'rdr2']) for (const night of ['', '-night']) {
    const style = JSON.parse(await fs.readFile(path.join(root, 'maps/styles', `${theme}${night}.json`)));
    assert.equal(style.version, 8);
    assert.equal(style.sources.buildings.type, 'vector');
    assert.equal(style.layers.filter(layer => layer.id.startsWith('poi-')).length, 9);
    assert.ok(style.glyphs.includes('{fontstack}/{range}.pbf'));
    assert.ok(style.sources.buildings.tiles[0].includes('{z}/{x}/{y}.pbf'));
    assert.equal((await sharp(path.join(root, 'maps/previews', `${theme}.png`)).metadata()).format, 'png');
  }
});

test('iOS catalog choices all resolve to actual transparent artwork in every theme', async () => {
  const catalog = JSON.parse(await fs.readFile(path.join(root, 'navigation-icons/catalog.json')));
  assert.equal(catalog.saved.length, 12);
  assert.equal(catalog.vehicles.length, 6);
  for (const item of [...catalog.saved, ...catalog.vehicles]) for (const theme of catalog.themes) {
    const variant = item.themeAssets[theme];
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
