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
    const previousSize = layer.layout['text-size'];
    const captionZoom = Array.isArray(previousSize) && previousSize[0] === 'step' && previousSize[2] === 0
      ? previousSize[3] : null;
    if (profile.map[role]) layer.layout['text-font'] = [profile.map[role]];
    if (theme === 'gta') {
      // Pricedown is retained for large area titles; smaller navigation names
      // use Bank Gothic, with enough interior space to remain legible on a phone.
      layer.layout['text-letter-spacing'] = role === 'place' ? 0.03 : 0.02;
      layer.layout['text-transform'] = 'none';
      layer.paint['text-halo-width'] = role === 'place' ? 1.3 : 1.0;
      layer.paint['text-halo-blur'] = 0.15;
      if (role === 'road') {
        layer.layout['text-size'] = ['interpolate', ['linear'], ['zoom'], 14, 16, 16, 17, 18, 19];
        layer.layout['text-padding'] = 4;
        layer.layout['text-max-angle'] = 25;
      } else if (role === 'poi') {
        layer.layout['text-size'] = captionZoom === null ? 16
          : ['step', ['zoom'], 0, captionZoom, 15.5, Math.max(17, captionZoom + 1), 16.5];
        layer.layout['text-max-width'] = 14;
        layer.layout['text-line-height'] = 1.3;
        layer.layout['text-padding'] = 7;
      }
    } else if (['smw', 'minecraft', 'cyberpunk', 'gta5'].includes(theme)) {
      const pixel = theme === 'smw' || theme === 'minecraft';
      layer.layout['text-transform'] = 'none';
      layer.layout['text-letter-spacing'] = pixel ? 0 : 0.02;
      layer.paint['text-halo-width'] = pixel ? 1.1 : 1.25;
      layer.paint['text-halo-blur'] = pixel ? 0 : 0.15;
      if (role === 'road') {
        layer.layout['text-size'] = ['interpolate', ['linear'], ['zoom'], 13, pixel ? 15 : 14, 16, pixel ? 17 : 16, 19, 19];
        layer.layout['text-max-angle'] = 25;
        layer.layout['text-padding'] = 5;
      } else if (role === 'poi') {
        layer.layout['text-size'] = captionZoom === null ? (pixel ? 15 : 14.5)
          : ['step', ['zoom'], 0, captionZoom, pixel ? 15 : 14.5];
        layer.layout['text-line-height'] = 1.3;
        layer.layout['text-padding'] = 7;
      }
    }
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
