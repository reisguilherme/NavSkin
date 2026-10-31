/** Shared production adapters for the three approved browser artworks. */
import * as pipboy from './theme-art/pipboy.mjs';
import * as hollow from './theme-art/hollow.mjs';
import * as crossy from './theme-art/crossy.mjs';
export const COLLECTION_ART = { pipboy, hollow, crossy };
const aliases = { 'pipboy-direction-east': 'road-oneway-forward', 'hollow-oneway': 'road-oneway-forward', 'crossy-oneway': 'road-oneway-forward' };
export const canonicalImage = value => typeof value === 'string'
  ? aliases[value] ?? value.replace(/^(pipboy|hollow|crossy)-(poi-)/, '$2')
  : Array.isArray(value) ? value.map(canonicalImage) : value;

export function applyCollectionArt(style, theme, night) {
  const art = COLLECTION_ART[theme];
  if (!art) return style;
  const name = style.name, sprite = style.sprite, metadata = structuredClone(style.metadata);
  const fonts = new Map(style.layers.filter(layer => layer.layout?.['text-field']).map(layer => [layer.id, layer.layout['text-font']]));
  const names = new Map(style.layers.filter(layer => layer.layout?.['text-field']).map(layer => [layer.id, layer.layout['text-field']]));
  const semanticIcons = new Map(style.layers.filter(layer => layer.id.startsWith('poi-')).map(layer => [layer.id, layer.layout['icon-image']]));
  const painted = art.createStyle(style, { night });
  painted.name = name;
  painted.sprite = sprite;
  painted.metadata = metadata;
  for (const layer of painted.layers) {
    if (layer.layout?.['icon-image']) layer.layout['icon-image'] = canonicalImage(layer.layout['icon-image']);
    if (semanticIcons.has(layer.id)) layer.layout['icon-image'] = semanticIcons.get(layer.id);
    if (fonts.has(layer.id)) layer.layout['text-font'] = fonts.get(layer.id);
    if (names.has(layer.id)) layer.layout['text-field'] = theme === 'pipboy' ? ['upcase', names.get(layer.id)] : names.get(layer.id);
    if (layer.metadata?.navskinLab) {
      layer.metadata.navskinArt = layer.metadata.navskinLab;
      delete layer.metadata.navskinLab;
    }
  }
  // Native retains GeoJSON fill buckets across style switches; patterned fills
  // and same-filter underlays can disappear together. Keep actual area colors,
  // drawn contours, vector roof textures and symbols intact in the offline pack.
  painted.layers = painted.layers.filter(layer => {
    if (layer.type !== 'fill' || painted.sources[layer.source]?.type !== 'geojson' || !layer.paint?.['fill-pattern']) return true;
    delete layer.paint['fill-pattern'];
    return Boolean(layer.paint['fill-color']); // Pattern-only overlays have a separate solid geographic base.
  });
  (metadata.navskin ?? metadata.gamemap).artDirection = {
    theme, source: `scripts/theme-art/${theme}.mjs`, origin: 'Promoted approved NavSkin browser artwork; original authored symbols and materials.',
    geometry: 'Original feature coordinates retained; hatches, paper, trees and shadows are illustration, not surveyed objects.',
    ...(painted.sources.areas?.type === 'geojson' ? { offlineAreaMaterials: 'Native GeoJSON compatibility: solid geographic area colors and contour strokes; vector building materials, POIs and background textures retained.' } : {}),
  };
  Object.assign(style, painted);
  return style;
}

/** Keep original pixel dimensions: shrinking a paper tile into a POI cell loses its drawing. */
export async function collectionImages(theme) {
  if (!COLLECTION_ART[theme]) return {};
  return Object.fromEntries(Object.entries(await COLLECTION_ART[theme].getImages()).map(([id, image]) => [canonicalImage(id), image]));
}
