/** Promote approved authored studies into editable sources and semantic navigation catalogs. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { collectionImages } from './collection-art.mjs';
const root = path.resolve(import.meta.dirname, '..');
const read = async file => JSON.parse(await fs.readFile(path.join(root, file), 'utf8'));
const write = async (file, value) => { await fs.mkdir(path.dirname(path.join(root, file)), { recursive: true }); await fs.writeFile(path.join(root, file), value); };
const json = (file, value) => write(file, JSON.stringify(value, null, 2) + '\n');
const newThemes = ['pipboy', 'hollow', 'crossy'];
const categories = ['parks', 'fuel', 'food', 'parking', 'health', 'shops', 'education', 'culture', 'religion'];
const subtypes = { cafe: 'food', 'fast-food': 'food', restaurant: 'food', bar: 'food', pharmacy: 'health', hospital: 'health', clinic: 'health', school: 'education', museum: 'culture', theatre: 'culture', worship: 'religion' };

/** SVG scanline runs preserve the approved procedural pixels as standalone geometry. */
function vectorPixels(image, title) {
  let body = '';
  for (let y = 0; y < image.height; y++) for (let x = 0; x < image.width;) {
    const at = (y * image.width + x) * 4, color = Array.from(image.data.slice(at, at + 4));
    let end = x + 1;
    while (end < image.width && color.every((v, i) => image.data[(y * image.width + end) * 4 + i] === v)) end++;
    if (color[3]) body += `<rect x="${x}" y="${y}" width="${end - x}" height="1" fill="rgb(${color.slice(0, 3)})" opacity="${color[3] / 255}"/>`;
    x = end;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 ${image.width} ${image.height}"><title>${title}</title><!-- NavSkin authored approved collection artwork, CC0-1.0; deterministic editable pixel geometry. -->${body}</svg>\n`;
}

export async function prepareCollectionAssets() {
  const tokens = await read('android/maps/theme-tokens.json');
  const palettes = {
    pipboy: ['#061008', '#09150a', '#10250e', '#0b250c', '#0a1c0d', '#b7f19a', '#b5f198', '#061008'],
    'pipboy-night': ['#020a04', '#061108', '#0a1c0c', '#081a09', '#061509', '#a2d88b', '#a4ed88', '#020a04'],
    hollow: ['#252e3d', '#252e3d', '#293c40', '#121e2c', '#303b49', '#eee5cc', '#eee5cc', '#111a27'],
    'hollow-night': ['#151d2b', '#151d2b', '#1d3036', '#091521', '#222d3c', '#e8e3d0', '#e8e3d0', '#080f1b'],
    crossy: ['#a1c677', '#a8c583', '#91bf59', '#58b9ce', '#dbd8c4', '#555961', '#303d35', '#eff2d8'],
    'crossy-night': ['#536f57', '#627b62', '#517c53', '#387c96', '#9caaa0', '#303844', '#eef1d8', '#24342e'],
  };
  for (const [mode, [background, urban, park, water, building, road, label, labelHalo]] of Object.entries(palettes)) {
    tokens.palettes[mode] = { background, urban, commercial: urban, industrial: urban, park, wood: park, grass: park, water,
      square: urban, waterOutline: label, building, buildingOutline: labelHalo, road, roadOutline: label,
      motorway: road, primary: road, secondary: road, tertiary: road, busway: road, service: road,
      motorwayCenter: label, path: road, rail: road, label, labelHalo };
  }
  tokens.specification.collectionReference = 'Approved Pip-Boy, Hollow Knight and Crossy Road studies promoted in scripts/theme-art; art changes require regeneration, not palette replacement.';
  await json('android/maps/theme-tokens.json', tokens);
  const fontCatalog = await read('android/maps/game-assets/catalog.json');
  fontCatalog.themes.pipboy = { ui: structuredClone(fontCatalog.themes.cyberpunk.ui), map: { road: 'NavSkinCyberpunk', poi: 'NavSkinCyberpunk', place: 'NavSkinCyberpunk' }, origin: 'Existing Blender Pro typography reused by the approved terminal study; not a Fallout font.' };
  fontCatalog.themes.hollow = { ui: structuredClone(fontCatalog.themes.rdr2.ui), map: { road: 'NavSkinRdrMap', poi: 'NavSkinRdrMap', place: 'NavSkinRdrMap' }, origin: 'Existing serif typography reused by the approved ink study; not a Hollow Knight font.' };
  fontCatalog.themes.crossy = { map: { road: 'GameMapSans', poi: 'GameMapSans', place: 'GameMapSans' }, origin: 'Bundled Noto Sans with system UI; not an original Crossy Road font.' };
  for (const [theme, base] of [['pipboy', 'cyberpunk'], ['hollow', 'rdr2']]) {
    await fs.cp(path.join(root, `android/maps/game-assets/runtime/${base}/fonts`), path.join(root, `android/maps/game-assets/runtime/${theme}/fonts`), { recursive: true });
    fontCatalog.themes[theme].ui.file = fontCatalog.themes[theme].ui.file.replace(`game-assets/${base}/`, `game-assets/${theme}/`);
  }
  await json('android/maps/game-assets/catalog.json', fontCatalog);
  await fs.cp(path.join(root, 'android/maps/fonts/GameMapSans'), path.join(root, 'android/maps/game-assets/glyphs/GameMapSans'), {recursive:true});
  const provenance = await read('android/maps/game-assets/provenance.json');
  provenance.authoredCollection = newThemes.map(theme => ({ theme, origin: 'NavSkin-authored approved browser study', source: `scripts/theme-art/${theme}.mjs`, license: 'CC0-1.0; fonts retain their existing notices', geometry: 'Real source coordinates preserved; textures are illustrations.' }));
  provenance.fontCopies = ['pipboy', 'hollow'].map(theme => ({ theme, file: fontCatalog.themes[theme].ui.file,
    sourceTheme: theme === 'pipboy' ? 'cyberpunk' : 'rdr2', note: 'Byte-identical theme-local UI font copy with the original notices. Not an original font from the inspired game.' }));
  await json('android/maps/game-assets/provenance.json', provenance);

  const catalog = await read('android/maps/catalogs/catalog.json');
  catalog.themes = ['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk', ...newThemes];
  for (const theme of newThemes) {
    const images = await collectionImages(theme);
    for (const [subtype, category] of Object.entries(subtypes)) images[`poi-${subtype}`] ??= images[`poi-${category}`];
    for (const id of [...categories.map(id => `poi-${id}`), ...Object.keys(subtypes).map(id => `poi-${id}`), 'road-oneway-forward']) {
      if (!images[id]) throw new Error(`Missing approved artwork: ${theme}/${id}`);
      await write(`android/maps/icons/${theme}/${id}.svg`, vectorPixels(images[id], `${theme} · ${id}`));
    }
    // Navigation IDs retain semantic silhouettes and a north-facing vehicle anchor.
    const base = { pipboy: 'cyberpunk', hollow: 'rdr2', crossy: 'smw' }[theme];
    for (const kind of ['saved', 'vehicles']) for (const item of catalog[kind]) {
      let svg = await fs.readFile(path.join(root, `android/maps/catalogs/${base}/${kind}/${item.id}.svg`), 'utf8');
      const colors = [...new Set(svg.match(/#[\da-f]{6}/gi))];
      const palette = theme === 'pipboy' ? ['#a8f586','#061008','#67a64b','#ccf5ae','#3f7134']
        : theme === 'hollow' ? ['#e9e2cc','#172330','#98aba8','#8b9ba4','#c0c7b6']
        : ['#f4e9d0','#506b5a','#91bf59','#86b9c4','#dcaa77'];
      svg = svg.replace(/#[\da-f]{6}/gi, color => {
        const target = palette[colors.indexOf(color) % palette.length];
        return '#' + [1, 3, 5].map(at => Math.round(parseInt(target.slice(at, at + 2), 16) * .86 + parseInt(color.slice(at, at + 2), 16) * .14).toString(16).padStart(2, '0')).join('');
      });
      svg = svg.replace(/<title>[\s\S]*?<\/title>/, `<title>${theme} · ${kind} · ${item.id}</title>`)
        .replace('<!--', '<!-- NavSkin authored collection navigation adaptation, CC0-1.0. ');
      await write(`android/maps/catalogs/${theme}/${kind}/${item.id}.svg`, svg);
      item.themeAssets[theme] = { image: `${theme}/${kind}/${item.id}.png`, preview: `${theme}/${kind}/${item.id}@2x.png`, svg: `${theme}/${kind}/${item.id}.svg`, origin: 'navskin-authored', license: 'CC0-1.0' };
    }
  }
  await json('android/maps/catalogs/catalog.json', catalog);
  console.log('Prepared approved collection art, palettes, font profiles and stable navigation choices.');
}
if (process.argv[1] && path.resolve(process.argv[1]) === import.meta.filename) await prepareCollectionAssets();
