// Optional authoring tool: recreates the initial original vector artwork.
// Normal builds use prepare-navigation-icons.mjs and preserve hand-edited SVGs.
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const sourceRoot = fileURLToPath(new URL('./', import.meta.url));
const saved = [
  ['home', 'Casa'], ['work', 'Trabalho'], ['favorite', 'Favorito'],
  ['restaurant', 'Restaurante'], ['cafe', 'Café'], ['park', 'Parque'],
  ['shopping', 'Compras'], ['gym', 'Academia'], ['health', 'Saúde'],
  ['parking', 'Estacionamento'], ['meeting', 'Encontro'], ['special', 'Local especial'],
];
const vehicles = [
  ['arrow', 'Seta clássica'], ['compact', 'Carro compacto'], ['sport', 'Carro esportivo'],
  ['suv', 'SUV'], ['motorcycle', 'Moto'], ['themed', 'Veículo do tema'],
];
const themes = {
  gta: { name: 'GTA SA', ink: '#20221d', paper: '#fff8d8', main: '#39d8a0', second: '#ffcb41', detail: '#f55e67', glass: '#8adcf3', white: '#fffdf4', outer: '#fffdf4', hatch: false },
  rosa: { name: 'Rosa Pop', ink: '#731b55', paper: '#fff7fc', main: '#ff77b4', second: '#fed45e', detail: '#ad8bff', glass: '#79e4e0', white: '#fff7fc', outer: '#fff7fc', hatch: false },
  rdr2: { name: 'RDR2', ink: '#392819', paper: '#edcd8e', main: '#cf9f56', second: '#b67339', detail: '#ac4e39', glass: '#b4c3ad', white: '#ffe7b7', outer: '#ffe7b7', hatch: true },
};

const rect = (x,y,w,h,fill,rx=2) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}"/>`;
const circle = (cx,cy,r,fill) => `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}"/>`;
const line = (x1,y1,x2,y2,stroke) => `<path d="M${x1} ${y1}L${x2} ${y2}" fill="none"${stroke ? ` stroke="${stroke}"` : ''}/>`;
const shape = (d,fill,extra='') => `<path d="${d}" fill="${fill}" ${extra}/>`;
const heart = (x,y,size,fill) => `<path d="M0 2C-3 -2 -6 0 -6 3C-6 6 0 10 0 10C0 10 6 6 6 3C6 0 3 -2 0 2Z" transform="translate(${x} ${y}) scale(${size/12})" fill="${fill}" vector-effect="non-scaling-stroke"/>`;
const star = (fill) => shape('M32 7L39 22L56 24L44 36L47 53L32 45L17 53L20 36L8 24L25 22Z',fill);
const bow = (x,y,p) => `<g transform="translate(${x} ${y})" stroke-width="2">${shape('M0 0C-3 -7 -10 -6 -10 -1C-10 4 -4 5 0 1C4 5 10 4 10 -1C10 -6 3 -7 0 0Z',p.detail)}${circle(0,0,2.7,p.second)}</g>`;
const flower = (x,y,p) => `<g transform="translate(${x} ${y})" stroke-width="1.7">${circle(0,-3.6,3.4,p.detail)}${circle(3.7,0,3.4,p.main)}${circle(1.9,4.1,3.4,p.detail)}${circle(-2.7,3.4,3.4,p.main)}${circle(-3.7,-1.3,3.4,p.detail)}${circle(0,0,2.4,p.second)}</g>`;

function savedArt(id,theme,p) {
  const cute = theme === 'rosa';
  const western = theme === 'rdr2';
  switch (id) {
    case 'home': return shape('M10 30L32 11L54 30L48 30V53H17V30Z',p.main) + shape('M9 30L32 10L55 30',p.detail) + rect(27,36,11,17,p.paper,1) + (cute ? heart(23,27,12,p.second) : rect(21,26,10,8,p.glass,0)) + (western ? line(42,34,42,46) : '');
    case 'work': return shape('M23 20V13H41V20', 'none') + rect(10,20,44,33,p.main,cute?7:2) + shape('M10 30L30 37H34L54 30','none') + rect(28,31,8,9,p.second,1) + (cute ? bow(44,20,p) : western ? line(15,44,23,48) : '');
    case 'favorite': return cute ? heart(32,14,46,p.main) + flower(48,21,p) : star(p.second) + (western ? `${line(28,24,23,34)}${line(32,25,27,35)}` : shape('M33 17L36 25L43 26',p.white,'stroke-width="2"'));
    case 'restaurant': return circle(33,32,17,p.paper) + circle(33,32,10,p.main) + shape('M8 12V25Q8 30 14 30Q20 30 20 25V12M14 12V53M8 21H20','none') + shape('M54 12C46 19 46 33 54 33V53Z',p.second) + (cute ? flower(33,31,p) : western ? line(27,36,36,27) : '');
    case 'cafe': return shape('M43 25H48Q57 25 55 34Q54 42 43 40','none') + shape('M11 23H44V40Q44 50 32 50H23Q11 50 11 40Z',p.main) + line(9,53,48,53) + shape('M20 16C15 10 24 11 21 5M32 17C27 11 36 11 33 5','none','stroke-width="2.5"') + (cute ? heart(27,29,17,p.second) : western ? line(18,30,20,42) : rect(19,29,15,5,p.second,0));
    case 'park': return western ? shape('M26 55V37H19Q11 37 11 28V21Q11 14 18 17V28H26V12Q26 5 34 7Q38 8 38 13V31H45V21Q45 15 52 18V33Q52 41 38 41V55Z',p.main) + line(21,55,44,55) + line(30,17,30,48) : rect(28,36,9,18,p.second,1) + shape('M32 8C22 8 21 18 18 20C7 22 8 35 16 39C19 44 28 42 32 40C36 43 45 44 49 38C57 31 51 20 45 20C44 12 38 8 32 8Z',p.main) + (cute ? flower(43,24,p) : shape('M20 29L25 25M36 17L40 20','none','stroke-width="2"'));
    case 'shopping': return shape('M15 22H49L53 54H11Z',p.main) + shape('M23 24V17Q23 8 32 8Q41 8 41 17V24','none') + (cute ? bow(32,37,p) : shape('M25 34L29 38L40 29','none','stroke-width="4"'));
    case 'gym': return rect(20,27,24,10,p.second,1) + rect(12,17,10,30,p.main,2) + rect(5,23,7,18,p.detail,1) + rect(42,17,10,30,p.main,2) + rect(52,23,7,18,p.detail,1) + (cute ? heart(32,26,10,p.paper) : '');
    case 'health': return shape('M23 9H41V23H55V41H41V55H23V41H9V23H23Z',p.main) + (cute ? heart(32,25,18,p.paper) : shape('M21 33H27L31 26L35 38L39 32H44','none',`stroke="${p.paper}" stroke-width="3"`));
    case 'parking': return rect(10,8,44,48,p.main,cute?10:3) + shape('M25 47V19H35Q44 19 44 29Q44 37 35 37H25M25 26H34Q37 26 37 29Q37 31 34 31H25',p.paper,'stroke-width="2"') + (cute ? flower(49,49,p) : western ? `${line(15,13,20,13)}${line(44,51,49,51)}` : '');
    case 'meeting': return circle(22,20,8,p.main) + circle(43,22,7,p.second) + shape('M7 52V43Q7 32 21 32Q35 32 35 43V52Z',p.main) + shape('M35 52V43Q35 35 43 34Q56 34 57 46V52Z',p.second) + (cute ? heart(33,7,11,p.detail) : western ? shape('M12 16L16 12H26L30 16Z',p.paper) : '');
    case 'special': return shape('M32 56L16 35C4 15 20 5 32 7C46 6 61 18 48 36Z',p.detail) + (cute ? flower(32,25,p) : shape('M32 15L36 22L44 23L38 29L40 37L32 33L24 37L26 29L20 23L28 22Z',p.second,'stroke-width="2.2"'));
    default: throw new Error(`Unknown saved icon ${id}`);
  }
}

function vehicleArt(id,theme,p) {
  const cute = theme === 'rosa';
  const western = theme === 'rdr2';
  if (id === 'arrow') {
    return shape('M32 7L53 55L32 45L11 55Z',p.main) + shape('M32 16V39L21 44Z',p.paper,'stroke-width="1.8"');
  }
  if (id === 'motorcycle') {
    return rect(28,7,8,15,p.ink,3) + rect(28,43,8,14,p.ink,3) + line(18,20,46,20) + shape('M24 20Q32 15 40 20L38 37L41 46H23L26 37Z',p.main) + rect(27,31,10,14,p.detail,4) + rect(29,21,6,7,p.glass,2) + circle(32,14,3,p.second);
  }
  if (id === 'themed' && western) {
    return shape('M25 8L23 27M39 8L41 27','none','stroke-width="3"') + rect(13,26,7,12,p.ink,1) + rect(44,26,7,12,p.ink,1) + rect(13,45,7,11,p.ink,1) + rect(44,45,7,11,p.ink,1) + rect(19,24,26,32,p.main,2) + rect(22,30,20,19,p.paper,1) + line(23,35,41,35) + line(23,41,41,41) + rect(22,21,20,5,p.detail,1) + shape('M28 13L32 8L36 13',p.second,'stroke-width="2"');
  }
  const sport = id === 'sport';
  const suv = id === 'suv';
  const themed = id === 'themed';
  const body = sport ? 'M27 8H37Q42 10 43 18L47 43Q47 54 40 56H24Q17 54 17 43L21 18Q22 10 27 8Z'
    : suv ? 'M24 8H40Q44 8 45 15V51Q44 56 40 56H24Q20 56 19 51V15Q20 8 24 8Z'
    : themed ? 'M25 8H39Q44 9 44 15L46 47Q46 55 39 56H25Q18 55 18 47L20 15Q20 9 25 8Z'
    : 'M25 8H39Q44 10 45 18V47Q45 55 39 56H25Q19 55 19 47V18Q20 10 25 8Z';
  let art = rect(14,17,7,12,p.ink,2) + rect(43,17,7,12,p.ink,2) + rect(14,39,7,12,p.ink,2) + rect(43,39,7,12,p.ink,2) + shape(body,p.main);
  art += shape(sport ? 'M23 23L41 23L39 34H25Z' : 'M23 21H41L39 29H25Z',p.glass,'stroke-width="2"');
  art += shape(suv ? 'M24 43H40V50H24Z' : 'M25 41H39L41 48H23Z',p.glass,'stroke-width="2"');
  art += line(24,32,24,39) + line(40,32,40,39);
  art += rect(22,12,6,3,p.paper,1) + rect(36,12,6,3,p.paper,1) + rect(22,51,6,3,p.detail,1) + rect(36,51,6,3,p.detail,1);
  if (sport) art += rect(29,10,6,11,p.second,0) + rect(29,47,6,8,p.second,0) + rect(18,49,28,4,p.detail,1);
  if (suv) art += line(27,31,27,40) + line(37,31,37,40) + rect(25,9,14,7,p.second,1);
  if (themed && cute) art += bow(32,35,p) + heart(32,14,9,p.second);
  else if (themed) art += rect(29,9,6,12,p.second,0) + rect(27,32,10,7,p.detail,1) + rect(29,47,6,8,p.second,0);
  return art;
}

function svgFor(kind,id,theme) {
  const p = { ...themes[theme] };
  if (theme === 'gta') {
    p.main = (kind === 'saved' ? {
      home: '#33c9a3', work: '#f3bb3a', restaurant: '#f35e68', cafe: '#b88552',
      park: '#53cb72', shopping: '#42c9df', gym: '#52cddd', health: '#41d995',
      parking: '#4a9fdf', meeting: '#b999f1',
    } : { sport: '#43bddf', suv: '#edb94b', themed: '#ec6c73' })[id] ?? p.main;
  }
  if (theme === 'rosa' && kind === 'vehicles') {
    p.main = { sport: '#b599ff', suv: '#79e4cf', themed: '#ff77b4' }[id] ?? p.main;
  }
  const art = kind === 'saved' ? savedArt(id,theme,p) : vehicleArt(id,theme,p);
  // Pale contour remains visible over dark maps and route strokes. The inner
  // ink contour is intentionally strong enough at a 48px map display size.
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64" role="img" aria-label="${id}">
  <title>${p.name} · ${kind} · ${id}</title>
  <!-- Original GameMap geometry, CC0. Vehicle front points up (north); anchor 0.5,0.5. -->
  <defs><g id="art" stroke-linejoin="round" stroke-linecap="round">${art}</g></defs>
  <use href="#art" stroke="${p.outer}" stroke-width="6.2"/>
  <use href="#art" stroke="${p.ink}" stroke-width="3.2"/>
</svg>\n`;
}

const catalog = {
  schemaVersion: 1, themes: Object.keys(themes),
  saved: [], vehicles: [], fallbackSaved: 'special', fallbackVehicle: 'arrow',
  vehicleAnchor: [0.5, 0.5], vehicleHeading: 'north', routingProfile: 'automobile',
};
for (const [kind, entries] of [['saved', saved], ['vehicles', vehicles]]) {
  for (const [id,label] of entries) {
    const entry = { id, label, themeAssets: {} };
    for (const theme of Object.keys(themes)) {
      const base = `${theme}/${kind}/${id}`;
      entry.themeAssets[theme] = { image: `${base}.png`, preview: `${base}@2x.png`, svg: `${base}.svg` };
      await mkdir(path.join(sourceRoot,theme,kind), { recursive: true });
      await writeFile(path.join(sourceRoot,`${base}.svg`),svgFor(kind,id,theme));
    }
    catalog[kind].push(entry);
  }
}
await writeFile(path.join(sourceRoot,'catalog.json'), `${JSON.stringify(catalog,null,2)}\n`);
console.log('Authored 54 original, independently editable navigation SVGs.');
