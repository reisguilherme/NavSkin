/** Production adapter for the first five approved map-lab drawing studies. */
import { createThemeStyle, getThemeImages } from './theme-art/five-worlds.mjs';
const themes = new Set(['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk']);

export function approvedMapImages(theme) {
  return themes.has(theme) ? { ...getThemeImages(theme, false), ...getThemeImages(theme, true) } : {};
}

export function applyApprovedMapArt(style, theme, night) {
  if (!themes.has(theme)) return style;
  const name = style.name, metadata = structuredClone(style.metadata);
  const painted = createThemeStyle(style, theme, { night });
  painted.name = name;
  painted.metadata = metadata;
  for (const layer of painted.layers) {
    if (layer.metadata?.navskinLab) {
      layer.metadata.navskinArt = layer.metadata.navskinLab;
      delete layer.metadata.navskinLab;
    }
    // The panel's road-label filter targets its local GeoJSON schema. Worldwide
    // vector tiles retain their original source-layer/class filter and names.
    const original = style.layers.find(item => item.id === layer.id);
    // Navigation typography has already been calibrated for phone readability.
    // The decorative drawing study must not shrink it in the offline schema.
    if (theme === 'gta' && layer.id === 'road-labels' && original) {
      layer.layout['text-size'] = structuredClone(original.layout['text-size']);
      layer.layout['text-letter-spacing'] = original.layout['text-letter-spacing'];
    }
    if (original?.['source-layer'] && layer.type === 'symbol') {
      if (original.filter) layer.filter = structuredClone(original.filter);
      else delete layer.filter;
    }
    if (theme === 'gta' && layer.id.startsWith('poi-')) {
      layer.paint['text-halo-width'] = 1.2;
      layer.paint['text-halo-color'] = '#000000';
    }
    // MapLibre Native GeoJSON fill-pattern buckets can disappear after style
    // switches. Solid real areas and their new contours remain in offline mode.
    if (layer.type === 'fill' && painted.sources[layer.source]?.type === 'geojson') delete layer.paint?.['fill-pattern'];
  }
  (metadata.navskin ?? metadata.gamemap).artDirection = {
    theme, source: 'scripts/theme-art/five-worlds.mjs',
    origin: 'Approved NavSkin map-lab drawings promoted to production.',
    geometry: 'Original feature coordinates, footprints, road grades and POIs retained. Paper, shadow and hatch effects are illustrations.',
  };
  Object.assign(style, painted);
  return style;
}
