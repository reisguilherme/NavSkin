import catalog from '../android/maps/game-assets/catalog.json' with { type: 'json' };
import fs from 'node:fs/promises';
import path from 'node:path';
const source = path.resolve(import.meta.dirname, '../android/maps/game-assets');

export function applyGameTypography(style, theme, offline) {
  const profile = catalog.themes[theme];
  if (!profile?.map) return style;
  for (const layer of style.layers) {
    if (!layer.layout?.['text-field']) continue;
    const role = layer.id === 'road-labels' ? 'road' : layer.id.startsWith('poi-') ? 'poi' : 'place';
    if (profile.map[role]) layer.layout['text-font'] = [profile.map[role]];
  }
  if (theme === 'gta') {
    // Navigation names take collision priority over optional place captions.
    const index = style.layers.findIndex(layer => layer.id === 'road-labels');
    if (index >= 0) {
      const [roadNames] = style.layers.splice(index, 1);
      roadNames.layout['symbol-spacing'] = 170;
      style.layers.push(roadNames);
    }
  }
  style.glyphs = offline ? 'asset://maps/glyphs/{fontstack}/{range}.pbf'
    : 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf';
  const metadata = style.metadata.navskin ?? style.metadata.gamemap;
  metadata.typography = { profile: theme, roles: profile.map, catalog: 'game-assets/catalog.json',
    unicodeFallback: 'Noto for codepoints absent from the selected game font' };
  return style;
}

export async function copyGameAssetFiles(outputMaps) {
  await fs.mkdir(path.join(path.dirname(outputMaps), 'game-assets'), { recursive: true });
  await fs.cp(path.join(source, 'runtime'), path.join(path.dirname(outputMaps), 'game-assets'), { recursive: true });
  await fs.copyFile(path.join(source, 'catalog.json'), path.join(path.dirname(outputMaps), 'game-assets/catalog.json'));
  await fs.cp(path.join(source, 'glyphs'), path.join(outputMaps, 'glyphs'), { recursive: true });
  await fs.copyFile(path.join(source, 'provenance.json'), path.join(outputMaps, 'credits/game-assets-provenance.json'));
}
