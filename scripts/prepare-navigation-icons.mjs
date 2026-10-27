import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = fileURLToPath(new URL('../', import.meta.url));

/** Rasterize the editable, offline navigation catalog without rewriting its SVG sources. */
export async function prepareNavigationIcons({
  sourceRoot = path.join(root, 'android/maps/catalogs'),
  outputRoot = path.join(root, 'android/app/src/main/assets/navigation-icons'),
} = {}) {
  const catalog = JSON.parse(await readFile(path.join(sourceRoot, 'catalog.json'), 'utf8'));
  const expectedThemes = ['gta', 'rosa', 'rdr2'];
  if (catalog.schemaVersion !== 1 || JSON.stringify(catalog.themes) !== JSON.stringify(expectedThemes)) {
    throw new Error('Navigation catalog must declare schema 1 and gta, rosa, rdr2 themes');
  }
  if (catalog.vehicleHeading !== 'north' || JSON.stringify(catalog.vehicleAnchor) !== '[0.5,0.5]') {
    throw new Error('Vehicle artwork must point north around the center anchor');
  }
  const prepared = [];
  for (const [kind, minimum] of [['saved', 12], ['vehicles', 6]]) {
    const entries = catalog[kind];
    if (!Array.isArray(entries) || entries.length < minimum || new Set(entries.map(item => item.id)).size !== entries.length) {
      throw new Error(`Navigation ${kind} catalog is missing choices or repeats semantic IDs`);
    }
    const fallback = kind === 'saved' ? catalog.fallbackSaved : catalog.fallbackVehicle;
    if (!entries.some(item => item.id === fallback)) throw new Error(`Missing ${kind} fallback`);
    for (const item of entries) {
      if (!/^[a-z][a-z0-9-]*$/.test(item.id) || !item.label?.trim()) throw new Error(`Invalid ${kind} semantic choice`);
      for (const theme of expectedThemes) {
        const asset = item.themeAssets?.[theme];
        const basename = `${theme}/${kind}/${item.id}`;
        if (asset?.image !== `${basename}.png` || asset?.preview !== `${basename}@2x.png` || asset?.svg !== `${basename}.svg`) {
          throw new Error(`Invalid local asset paths for ${theme}/${kind}/${item.id}`);
        }
        const svg = await readFile(path.join(sourceRoot, asset.svg), 'utf8');
        const references = [...svg.matchAll(/\b(?:href|xlink:href)\s*=\s*(["'])(.*?)\1/g)];
        if (!/viewBox="0 0 64 64"/.test(svg)
          || /<(?:image|text|foreignObject)\b|<!ENTITY|https?:\/\/(?!www\.w3\.org\/2000\/svg)/.test(svg)
          || references.some(([, , target]) => !/^#[a-zA-Z][\w.-]*$/.test(target))) {
          throw new Error(`Artwork must be standalone 64 × 64 SVG geometry: ${asset.svg}`);
        }
        prepared.push({ theme, kind, id: item.id, label: item.label, asset, svg });
      }
    }
  }
  const license = await readFile(path.join(sourceRoot, 'LICENSE.txt'), 'utf8');
  // Read and validate the complete input before emitting any runtime assets.
  for (const { asset, svg } of prepared) {
    await mkdir(path.dirname(path.join(outputRoot, asset.svg)), { recursive: true });
    await copyFile(path.join(sourceRoot, asset.svg), path.join(outputRoot, asset.svg));
    await sharp(Buffer.from(svg)).resize(48, 48).png().toFile(path.join(outputRoot, asset.image));
    await sharp(Buffer.from(svg)).resize(96, 96).png().toFile(path.join(outputRoot, asset.preview));
  }
  await writeFile(path.join(outputRoot, 'catalog.json'), `${JSON.stringify(catalog, null, 2)}\n`);
  await writeFile(path.join(outputRoot, 'LICENSE.txt'), license);
  console.log(`Prepared ${prepared.length} original navigation SVGs and ${prepared.length * 2} transparent PNGs across 3 themes.`);
  return catalog;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await prepareNavigationIcons();
}
