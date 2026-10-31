/** Game-specific cartographic drawing on the original geographic sources.
 * Materials and offset silhouettes are illustration, never new POIs or measured heights.
 */
const games = new Set(['smw', 'minecraft', 'cyberpunk', 'gta5']);
const svg = body => `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">${body}</svg>`;
const rect = (x, y, w, h, color) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${color}"/>`;
const mix = (color, factor) => '#' + color.slice(1).match(/../g).map(c => Math.max(0, Math.min(255, Math.round(parseInt(c, 16) * factor))).toString(16).padStart(2, '0')).join('');

/** Seamless, local material tiles, shared by world and offline renderers. */
export function gameMaterialSprites(theme, tokens) {
  if (!games.has(theme) || theme === 'gta5') return {};
  const result = {};
  for (const night of [false, true]) {
    const p = tokens.palettes[`${theme}${night ? '-night' : ''}`];
    const suffix = night ? 'night' : 'day';
    const tile = (name, body) => { result[`material-${name}-${suffix}`] = svg(body); };
    if (theme === 'smw') {
      tile('grass', rect(0, 0, 32, 32, p.park) + [[4, 5], [23, 19]].map(([x, y]) =>
        rect(x, y, 2, 4, mix(p.park, .86)) + rect(x + 4, y + 2, 2, 3, mix(p.park, .86))).join(''));
      tile('water', rect(0, 0, 32, 32, p.water) + [[3, 6], [19, 22]].map(([x, y]) =>
        rect(x, y, 8, 2, mix(p.water, 1.23)) + rect(x + 2, y + 2, 4, 1, mix(p.water, 1.23))).join(''));
      tile('roof', rect(0, 0, 32, 32, p.building) + [0, 8, 16, 24].map(y =>
        rect(0, y, 32, 1, mix(p.building, .83)) + rect((y % 16) + 6, y + 1, 1, 7, mix(p.building, .9))
        + rect((y % 16) + 22, y + 1, 1, 7, mix(p.building, .9))).join(''));
    } else if (theme === 'minecraft') {
      const block = (base, variation, cell = 4) => {
        let body = rect(0, 0, 32, 32, base);
        for (let y = 0; y < 32; y += cell) for (let x = 0; x < 32; x += cell) {
          const value = ((x * 13 + y * 23 + x * y * 3) % 17) / 16;
          body += rect(x, y, cell, cell, mix(base, 1 - variation / 2 + value * variation));
        }
        return body;
      };
      tile('grass', block(p.park, .18));
      tile('ground', block(p.urban, .12));
      tile('water', block(p.water, .16, 8));
      tile('roof', block(p.building, .22) + [0, 8, 16, 24].map(y => rect(0, y, 32, 1, mix(p.building, .66))).join(''));
    } else {
      tile('roof', rect(0, 0, 32, 32, p.building) + [0, 8, 16, 24].map(y => rect(0, y, 32, 1, mix(p.building, 1.28))).join('')
        + rect(8, 0, 1, 32, mix(p.building, 1.12)) + rect(24, 0, 1, 32, mix(p.building, 1.12)));
    }
  }
  return result;
}

const scaleZoomWidth = (expression, factor, extra = 0) => {
  if (typeof expression === 'number') return expression * factor + extra;
  if (!Array.isArray(expression) || expression[0] !== 'interpolate') return expression;
  return expression.map((value, index) => index >= 4 && index % 2 === 0 ? Math.max(.1, value * factor + extra) : value);
};

export function applyGameMapArtDirection(style, theme, night, p) {
  if (!games.has(theme)) return style;
  const suffix = night ? 'night' : 'day';
  const pixel = theme === 'smw' || theme === 'minecraft';
  const material = name => `material-${name}-${suffix}`;
  const buildingLayer = layer => layer.type === 'fill' && /^building-(?:footprints(?:-local)?|osm)$/.test(layer.id);
  const vegetation = layer => layer.type === 'fill' && /^(vegetation-(?:grass|wood|parks)|protected-areas)$/.test(layer.id);
  const layers = [];
  for (const layer of style.layers) {
    if (layer.type === 'background' && theme === 'minecraft') layer.paint['background-pattern'] = material('ground');
    if (layer.type === 'fill' && theme === 'minecraft' && layer.id === 'landuse-urban') layer.paint['fill-pattern'] = material('ground');
    if (pixel && vegetation(layer)) {
      layer.paint['fill-pattern'] = material('grass');
      layer.paint['fill-antialias'] = false;
      // An illustrated terrace edge follows the actual polygon; it is not a terrain elevation dataset.
      if (layer.id === 'vegetation-parks' || layer.id === 'protected-areas') {
        layers.push({ ...structuredClone(layer), id: `art-terrace-${layer.id}`,
          paint: { 'fill-color': theme === 'smw' ? p.buildingOutline : '#4D3E28', 'fill-opacity': 1,
            'fill-translate': [0, theme === 'smw' ? 5 : 3], 'fill-translate-anchor': 'map' } });
      }
    }
    if (pixel && layer.id === 'water-fill') {
      layer.paint['fill-pattern'] = material('water');
      layer.paint['fill-antialias'] = false;
    }
    if (layer.id === 'water-outline' && pixel) layer.paint['line-width'] = ['interpolate', ['linear'], ['zoom'], 10, 1.5, 16, 3];
    if (buildingLayer(layer)) {
      if (theme !== 'gta5') {
        layers.push({ ...structuredClone(layer), id: `art-wall-${layer.id}`,
          paint: { 'fill-color': theme === 'cyberpunk' ? '#180B19' : p.buildingOutline,
            'fill-opacity': 1, 'fill-translate': theme === 'minecraft' ? [3, 3] : [0, 3], 'fill-translate-anchor': 'map' } });
        layer.paint['fill-pattern'] = material('roof');
      }
      layer.paint['fill-antialias'] = !pixel;
    }
    if (layer.metadata?.road) {
      const casing = layer.metadata.part === 'casing';
      const factor = { smw: .92, minecraft: .95, cyberpunk: .72, gta5: .8 }[theme];
      const edge = { smw: 1.5, minecraft: .7, cyberpunk: 1.1, gta5: .2 }[theme];
      layer.paint['line-width'] = scaleZoomWidth(layer.paint['line-width'], factor, casing ? edge : 0);
      layer.layout['line-join'] = pixel ? 'miter' : 'round';
      layer.layout['line-cap'] = pixel ? 'butt' : 'round';
    }
    if (layer.id.startsWith('paths-')) {
      layer.paint['line-color'] = p.path;
      if (theme === 'smw') {
        layer.paint['line-width'] = ['interpolate', ['linear'], ['zoom'], 14, 1.5, 17, 3];
        layer.paint['line-dasharray'] = [1, 1.25];
      } else if (theme === 'cyberpunk') layer.paint['line-opacity'] = .5;
    }
    if (layer.id.startsWith('railway-')) {
      layer.paint['line-opacity'] = theme === 'cyberpunk' ? .4 : .65;
      layer.paint['line-width'] = ['interpolate', ['linear'], ['zoom'], 12, .3, 17, 1.5];
    }
    if (layer.id.startsWith('motorway-') && theme !== 'smw') layer.paint['line-opacity'] = .55;
    if (layer.id.startsWith('poi-')) {
      layer.layout['icon-size'] = ['interpolate', ['linear'], ['zoom'], 12, pixel ? .82 : .7, 15, pixel ? 1 : .82, 18, pixel ? 1.18 : 1];
      layer.layout['icon-padding'] = 6;
    }
    // Native retains GeoJSON fill buckets across style switches. Introducing a
    // pattern can then hide both that fill and same-filter underlays (RDR -> SMW
    // emulator regression). Keep these areas solid; vector tiles retain materials.
    if (layer.type === 'fill' && style.sources[layer.source]?.type === 'geojson') {
      delete layer.paint['fill-pattern'];
    }
    // Keep vector area colors visible while the material atlas loads.
    if (layer.type === 'fill' && layer.paint?.['fill-pattern']) {
      const base = structuredClone(layer);
      base.id = `art-base-${layer.id}`;
      delete base.paint['fill-pattern'];
      layers.push(base);
    }
    layers.push(layer);
    if (buildingLayer(layer) || (pixel && layer.id === 'vegetation-parks')) {
      layers.push({ id: `art-edge-${layer.id}`, type: 'line', source: layer.source,
        ...(layer['source-layer'] ? { 'source-layer': layer['source-layer'] } : {}),
        ...(layer.filter ? { filter: structuredClone(layer.filter) } : {}),
        minzoom: Math.max(layer.minzoom ?? 0, 14), ...(layer.layout?.visibility ? { layout: { visibility: layer.layout.visibility } } : {}),
        paint: { 'line-color': buildingLayer(layer) ? p.buildingOutline : p.waterOutline,
          'line-width': ['interpolate', ['linear'], ['zoom'], 14, .3, 17, theme === 'smw' ? 1.1 : .65],
          'line-opacity': theme === 'gta5' ? .55 : 1 } });
    }
  }
  style.layers = layers;
  (style.metadata.navskin ?? style.metadata.gamemap).artDirection = {
    theme, drawing: theme === 'smw' ? 'SNES terraces, outlined roofs, stippled paths and water tiles'
      : theme === 'minecraft' ? 'pixel materials, block roof edges and earth paths'
      : theme === 'cyberpunk' ? 'red architectural volumes, technical roof lines and cyan road wireframe'
      : 'pause-map road hierarchy, muted urban masses and standalone blips',
    geometry: 'Original feature coordinates retained; material tiles and offset silhouettes are decorative, not surveyed height.',
    ...(style.sources.areas?.type === 'geojson' ? {
      offlineAreaMaterials: 'GeoJSON areas use solid fills to retain geometry across Native style switches; vector building materials are preserved.',
    } : {}) };
  return style;
}
