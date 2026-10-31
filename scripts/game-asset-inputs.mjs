import fs from 'node:fs/promises';
import path from 'node:path';

const sourceRoot = path.resolve(import.meta.dirname, '../android/maps/game-assets/source');

/** Resolve source paths against the manifest, independent of the checkout location. */
export async function loadGameAssetInputs(manifestPath = path.join(sourceRoot, 'import-manifest.json')) {
  const manifestRoot = path.dirname(path.resolve(manifestPath));
  const input = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  const resolve = value => value === undefined ? undefined : path.resolve(manifestRoot, value);
  for (const font of Object.values(input.fonts ?? {})) {
    font.path = resolve(font.path);
    font.licenseFiles = (font.licenseFiles ?? []).map(resolve);
  }
  input.spritesGta = resolve(input.spritesGta);
  input.spritesRdr = resolve(input.spritesRdr);
  return { input, wasmRoot: input.fontMaker ? resolve(input.fontMaker) : path.join(sourceRoot, 'font-maker') };
}
