/** Approved toy-city drawings and user-referenced Hello Kitty convertible. */
import fs from 'node:fs/promises';
import path from 'node:path';
const root = path.resolve(import.meta.dirname, '..');
const source = path.join(root, 'scripts/theme-art/pink-icons');
const categories = ['parks', 'health', 'food', 'fuel', 'shops', 'parking', 'education', 'culture', 'religion'];
export async function preparePinkAssets() {
  const copy = async (name, target) => fs.copyFile(path.join(source, `${name}.svg`), path.join(root, target));
  for (const category of categories) await copy(category, `android/maps/icons/rosa/poi-${category}.svg`);
  const aliases = { cafe: 'food', 'fast-food': 'food', restaurant: 'food', bar: 'food', pharmacy: 'health', hospital: 'health', clinic: 'health', school: 'education', museum: 'culture', theatre: 'culture', worship: 'religion' };
  for (const [subtype, category] of Object.entries(aliases)) await copy(category, `android/maps/icons/rosa/poi-${subtype}.svg`);
  const catalogPath = path.join(root, 'android/maps/catalogs/catalog.json');
  const catalog = JSON.parse(await fs.readFile(catalogPath, 'utf8'));
  for (const [kind, id, sourceName] of [
    ['saved', 'home', 'saved-home'], ['saved', 'work', 'saved-work'], ['saved', 'favorite', 'saved-heart'],
    ['vehicles', 'themed', 'vehicle'],
  ]) {
    await copy(sourceName, `android/maps/catalogs/rosa/${kind}/${id}.svg`);
    Object.assign(catalog[kind].find(item => item.id === id).themeAssets.rosa, {
      origin: id === 'themed' ? 'navskin-authored-character-adaptation' : 'navskin-authored',
      license: id === 'themed' ? 'Original NavSkin vector drawing from user-provided toy reference. Hello Kitty character rights remain with Sanrio; excluded from CC0.' : 'CC0-1.0',
      source: `scripts/theme-art/pink-icons/${sourceName}.svg`,
      ...(id === 'themed' ? { title: 'Carrinho Hello Kitty', vehicleHeading: 'north' } : {}),
    });
  }
  await fs.writeFile(catalogPath, JSON.stringify(catalog, null, 2) + '\n');
}
if (process.argv[1] && path.resolve(process.argv[1]) === import.meta.filename) await preparePinkAssets();
