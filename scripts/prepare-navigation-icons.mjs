import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = fileURLToPath(new URL('../', import.meta.url));

/** Prepare offline navigation art; an explicit GTA player raster takes precedence over its legacy SVG. */
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
        let raster;
        let padded;
        if (asset.rasterSource !== undefined) {
          if (theme !== 'gta' || kind !== 'vehicles' || item.id !== 'arrow') {
            throw new Error('Original raster artwork is allowed only for gta/vehicles/arrow');
          }
          const original = asset.rasterSource;
          if (original.file !== 'gta/vehicles/arrow-original.png'
            || !/^[a-f0-9]{64}$/.test(original.sha256 ?? '')
            || original.width !== 16 || original.height !== 16 || original.padding !== 4
            || original.originalDictionary !== 'models/hud.txd' || original.originalTexture !== 'radar_centre'
            || original.sourceUrl !== 'https://gtamidori.com/textdrawsprites/hud.txd/radar_centre.png'
            || original.copyright !== 'Rockstar Games' || !original.licenseStatus?.trim()) {
            throw new Error('Invalid original GTA player raster source or provenance');
          }
          raster = await readFile(path.join(sourceRoot, original.file));
          if (createHash('sha256').update(raster).digest('hex') !== original.sha256) {
            throw new Error('Original GTA player raster checksum mismatch');
          }
          const metadata = await sharp(raster).metadata();
          if (metadata.format !== 'png' || metadata.width !== 16 || metadata.height !== 16 || !metadata.hasAlpha) {
            throw new Error('Original GTA player raster must be a transparent 16 × 16 PNG');
          }
          // 16 px + 4 px on each side gives exact integer scaling at 48 and 96 px.
          padded = await sharp(raster).extend({ top: 4, bottom: 4, left: 4, right: 4,
            background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
        }
        prepared.push({ theme, kind, id: item.id, label: item.label, asset, svg, raster, padded });
      }
    }
  }
  const license = await readFile(path.join(sourceRoot, 'LICENSE.txt'), 'utf8');
  // Read and validate the complete input before emitting any runtime assets.
  for (const { asset, svg, raster, padded } of prepared) {
    await mkdir(path.dirname(path.join(outputRoot, asset.svg)), { recursive: true });
    await copyFile(path.join(sourceRoot, asset.svg), path.join(outputRoot, asset.svg));
    if (raster) {
      await writeFile(path.join(outputRoot, asset.rasterSource.file), raster);
      await sharp(padded).resize(48, 48, { kernel: 'nearest' }).png().toFile(path.join(outputRoot, asset.image));
      await sharp(padded).resize(96, 96, { kernel: 'nearest' }).png().toFile(path.join(outputRoot, asset.preview));
    } else {
      await sharp(Buffer.from(svg)).resize(48, 48).png().toFile(path.join(outputRoot, asset.image));
      await sharp(Buffer.from(svg)).resize(96, 96).png().toFile(path.join(outputRoot, asset.preview));
    }
  }
  const importedSprites = prepared.filter(item => item.raster).map(({ theme, kind, id, asset }) => ({
    theme, kind, id, ...asset.rasterSource, resizeKernel: 'nearest',
    image: asset.image, preview: asset.preview,
    sourceRole: 'Original raster is the source for the runtime PNGs; the legacy SVG is an unused original NavSkin alternative.',
    distribution: 'Local private pack only. Original game artwork and its derivatives are excluded from CC0.',
  }));
  await writeFile(path.join(outputRoot, 'catalog.json'), `${JSON.stringify(catalog, null, 2)}\n`);
  await writeFile(path.join(outputRoot, 'provenance.json'), `${JSON.stringify({ schemaVersion: 1, importedSprites }, null, 2)}\n`);
  await writeFile(path.join(outputRoot, 'LICENSE.txt'), license);
  console.log(`Prepared ${prepared.length} navigation choices (${importedSprites.length} original game raster) and ${prepared.length * 2} transparent PNGs across 3 themes.`);
  return catalog;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await prepareNavigationIcons();
}
