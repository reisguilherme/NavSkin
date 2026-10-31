/** Hollow Knight cartographic study. Browser-only; every contour uses source geometry. */
export const definition = {
  id: 'hollow', title: 'Hollow Knight', kicker: 'TINTA / ATLAS SUBTERRÂNEO',
  description: 'Goiânia no caderno de um cartógrafo: salas hachuradas, raízes a nanquim e caminhos de marfim sobre papel azul profundo.',
  details: ['Contornos de salas sobre edifícios reais', 'Folhagens, raízes e margens desenhadas', 'Símbolos delicados de cartógrafo'],
  accent: '#e1d7bb',
  referenceUrl: 'https://www.teamcherry.com.au/blog/hollow-knight-then-and-now',
  referenceLabel: 'Team Cherry · arte de Hallownest',
};
export const camera = { pitch: 0, bearing: 0, zoom: 16.4 };

const copy = value => structuredClone(value);
const zoom = (a, b, low = 13, high = 18) => ['interpolate', ['linear'], ['zoom'], low, a, high, b];
const imageId = (name, night = false) => `hollow-${name}${night ? '-night' : ''}`;
const palettes = {
  day: { paper:'#252e3d', room:'#303b49', edge:'#9b9b91', ink:'#eee5cc', muted:'#c1baa6', park:'#293c40', water:'#121e2c', shade:'#111a27' },
  night: { paper:'#151d2b', room:'#222d3c', edge:'#81939a', ink:'#e8e3d0', muted:'#b4bbba', park:'#1d3036', water:'#091521', shade:'#080f1b' },
};

function contour(layer, name, paint, minzoom = layer.minzoom ?? 0) {
  return { id:`hollow-${name}-${layer.id}`, type:'line', source:layer.source,
    ...(layer['source-layer'] ? {'source-layer':layer['source-layer']} : {}),
    ...(layer.filter ? {filter:copy(layer.filter)} : {}), minzoom,
    ...(layer.maxzoom != null ? {maxzoom:layer.maxzoom} : {}),
    layout:{'line-join':'round','line-cap':'round', ...(layer.layout?.visibility ? {visibility:layer.layout.visibility} : {})},
    metadata:{navskinLab:{illustration:true,follows:layer.id}}, paint };
}

function mapImage(value) {
  if (typeof value === 'string') return value.startsWith('poi-') ? `hollow-${value}` : value;
  return Array.isArray(value) ? value.map(mapImage) : value;
}

export function createStyle(base, { night = false } = {}) {
  const style = copy(base), p = palettes[night ? 'night' : 'day'];
  const image = name => imageId(name, night);
  const layers = [];
  // None of the source atlas or the earlier theme's decorative layers survive.
  delete style.sprite;
  for (const layer of style.layers.filter(item => !/^(art-|lab-|hollow-)/.test(item.id))) {
    const paint = layer.paint ??= {}, layout = layer.layout ??= {};
    const building = layer.type === 'fill' && /^building-(footprints(?:-local)?|osm)$/.test(layer.id);
    const vegetation = layer.type === 'fill' && /^(vegetation-|protected-areas)/.test(layer.id);

    if (layer.type === 'background') {
      layer.paint = {'background-color':p.paper,'background-pattern':image('paper')};
    } else if (layer.type === 'fill') {
      delete paint['fill-pattern'];
      paint['fill-color'] = p.paper;
      paint['fill-outline-color'] = p.paper;
      paint['fill-opacity'] = 1;
      if (layer.id === 'landuse-urban') paint['fill-pattern'] = image('paper');
      if (layer.id === 'landuse-commercial') paint['fill-pattern'] = image('commercial');
      if (layer.id === 'landuse-industrial') paint['fill-pattern'] = image('industrial');
      if (layer.id === 'public-squares') {
        paint['fill-color'] = p.room;
        paint['fill-pattern'] = image('square');
      }
      if (vegetation) {
        paint['fill-color'] = p.park;
        paint['fill-pattern'] = image(layer.id === 'vegetation-wood' ? 'roots' : 'foliage');
      }
      if (layer.id === 'water-fill') {
        paint['fill-color'] = p.water;
        paint['fill-pattern'] = image('water');
      }
      if (building) {
        paint['fill-color'] = p.room;
        paint['fill-pattern'] = image('room');
        paint['fill-outline-color'] = p.edge;
        // These broad and fine pen passes are architectural borders, not heights.
        layers.push(contour(layer, 'room-understroke', {
          'line-color':p.shade, 'line-width':zoom(1.4,4.2), 'line-opacity':.9,
        }));
      }
    } else if (layer.type === 'line') {
      delete paint['line-pattern'];
      paint['line-color'] = p.muted;
      if (layer.metadata?.road) {
        const casing = layer.metadata.part === 'casing';
        const arterial = /-(primary|trunk|motorway)-/.test(layer.id);
        const middle = /-(secondary|busway|tertiary)-/.test(layer.id);
        const service = /-service-/.test(layer.id);
        const low = arterial ? 1.2 : middle ? .85 : service ? .4 : .6;
        const high = arterial ? 3.9 : middle ? 2.8 : service ? 1.1 : 1.8;
        paint['line-width'] = zoom(casing ? low + 1.2 : low, casing ? high + 3.2 : high);
        paint['line-color'] = casing ? p.paper : arterial ? p.ink : p.muted;
        paint['line-opacity'] = layer.metadata.grade < 0 ? .48 : casing ? .9 : .88;
        layout['line-cap'] = 'round'; layout['line-join'] = 'round';
        if (layer.metadata.grade >= 0) delete paint['line-dasharray'];
      }
      if (layer.id.startsWith('paths-')) {
        paint['line-width'] = zoom(.45,1.35);
        paint['line-color'] = p.ink;
        paint['line-dasharray'] = [1,2.4];
        paint['line-opacity'] = .78;
      }
      if (layer.id.startsWith('railway-')) {
        paint['line-width'] = zoom(.55,1.4);
        paint['line-dasharray'] = [4,2];
        paint['line-opacity'] = .65;
      }
      if (layer.id.startsWith('motorway-')) {
        paint['line-width'] = zoom(.15,.5);
        paint['line-color'] = p.paper;
        paint['line-dasharray'] = [3,4];
      }
      if (layer.id === 'waterways') {
        paint['line-color'] = '#719098';
        paint['line-width'] = zoom(.6,2.7);
        paint['line-opacity'] = .7;
      }
      if (layer.id === 'water-outline') {
        paint['line-color'] = '#80929a';
        paint['line-width'] = zoom(4,9);
        paint['line-opacity'] = .52;
      }
    }

    if (layer.type === 'symbol') {
      if (layout['text-field']) {
        layout['text-font'] = ['NavSkinRdrMap'];
        paint['text-color'] = p.ink;
        paint['text-halo-color'] = p.shade;
        paint['text-halo-width'] = 1.35;
        paint['text-halo-blur'] = .45;
        layout['text-letter-spacing'] = .045;
      }
      if (layer.id.startsWith('poi-')) {
        layout['icon-image'] = mapImage(layout['icon-image']);
        layout['icon-size'] = zoom(.65,.95);
        layout['icon-padding'] = 5;
        layout['text-size'] = ['step',['zoom'],0,16.3,12.5];
        layout['text-offset'] = [0,1.9];
        layout['text-padding'] = 5;
        paint['icon-opacity'] = .96;
      }
      if (layer.id === 'road-labels') {
        layout['text-size'] = zoom(11.5,15,14,18);
        layout['text-letter-spacing'] = .055;
        layout['symbol-spacing'] = 240;
      }
      if (layer.id === 'district-labels') {
        layout['text-size'] = 15;
        layout['text-letter-spacing'] = .19;
        paint['text-opacity'] = .72;
      }
      if (layer.id === 'place-labels') {
        layout['text-size'] = zoom(17,23);
        layout['text-letter-spacing'] = .13;
        layout['text-padding'] = 7;
        paint['text-halo-width'] = 1.9;
      }
      if (layer.id === 'road-oneway-directions') {
        layout['icon-image'] = 'hollow-oneway';
        layout['icon-size'] = zoom(.35,.6,16.5,18);
        layout['symbol-spacing'] = 180;
        paint['icon-opacity'] = .75;
      }
    }
    layers.push(layer);
    if (building) {
      layers.push(contour(layer,'room-wall', {
        'line-color':p.edge, 'line-width':zoom(.25,.95), 'line-opacity':.79,
      }));
      layers.push(contour(layer,'room-pencil', {
        'line-color':p.ink, 'line-width':zoom(.1,.45), 'line-opacity':.44,
        'line-dasharray':[11,2,2,2], 'line-offset':-.7,
      },15.8));
    }
    if (vegetation || layer.id === 'public-squares') {
      layers.push(contour(layer,'garden-boundary', {
        'line-color':vegetation ? '#91a69c' : p.muted,
        'line-width':zoom(.7,2), 'line-opacity':.7,
      }));
      if (vegetation) layers.push(contour(layer,'garden-pencil', {
        'line-color':'#bdc6b1', 'line-width':zoom(.35,.85),
        'line-dasharray':[6,2,1,2], 'line-offset':-2.5, 'line-opacity':.55,
      },14));
    }
    if (layer.id === 'water-fill') {
      layers.push(contour(layer,'shore-ink', {
        'line-color':'#a3b0ac', 'line-width':zoom(.5,1.4), 'line-opacity':.85,
      }));
      layers.push(contour(layer,'shore-hatching', {
        'line-color':'#9da9a7', 'line-width':zoom(2,5.5),
        'line-dasharray':[.18,1.1,.18,1.8], 'line-opacity':.55,
      }));
      layers.push(contour(layer,'shore-pencil', {
        'line-color':'#748b96', 'line-width':zoom(.35,.7),
        'line-offset':-3.8, 'line-opacity':.5,
      },14));
    }
  }
  style.layers = layers;
  style.name = `Hollow Knight · caderno de Goiânia${night ? ' · noite' : ''}`;
  style.metadata ??= {};
  style.metadata.navskinLab = {theme:'hollow',nativeAssetsChanged:false,
    geography:'Original sources, source geometry, POI categories and road grades preserved.',
    illustration:'Original ink textures and cartographer symbols; contours follow real buildings, park and water polygons.'};
  return style;
}

const svg = (width,height,body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${body}</svg>`;
const path = (d, stroke, width = 1, opacity = 1, fill = 'none') => `<path d="${d}" fill="${fill}" stroke="${stroke}" stroke-width="${width}" opacity="${opacity}" stroke-linecap="round" stroke-linejoin="round"/>`;

function material(name, night) {
  const p = palettes[night ? 'night' : 'day'];
  const size = name === 'foliage' || name === 'roots' ? 256 : 128;
  const bg = ['foliage','roots'].includes(name) ? p.park : name === 'water' ? p.water : ['room','square'].includes(name) ? p.room : p.paper;
  let body = `<rect width="${size}" height="${size}" fill="${bg}"/>`;
  // Light, irregular paper fibres: deterministic and seamless, without noise filters.
  for (let i = 0; i < (size === 256 ? 300 : 100); i++) {
    const x=(i*37+i*i*11)%size, y=(i*53+i*i*7)%size;
    body += path(`M${x} ${y}l${1+(i%5)} ${i%3-1}`,i%2 ? p.ink : '#050e1a',.5,i%2 ? .035 : .1);
  }
  if (name === 'room' || name === 'industrial') {
    for(let i=-128;i<256;i+=14) {
      body += path(`M${i} 0l-34 128`, p.muted, .8, name === 'room' ? .2 : .09);
      body += path(`M${i+2} 50l-11 41`, p.muted, .55, .11);
    }
  }
  if (name === 'commercial' || name === 'square') {
    for(let y=10;y<128;y+=25) for(let x=9+(y%3)*7;x<128;x+=29) body += path(`M${x} ${y}l4 1m-2-3 0 5`,p.muted,.65,.12);
  }
  if (name === 'water') {
    for(let y=14;y<128;y+=26) {
      const shift=(y%7)*4;
      body+=path(`M${-20+shift} ${y}q13-3 26 0t26 0m9 0q13-3 26 0t26 0`, '#9eafba',.8,.19);
      body+=path(`M${12+shift} ${y+5}q8-2 17 0m36 1q5-2 13 0`, '#9eafba',.6,.12);
    }
  }
  if (name === 'foliage' || name === 'roots') {
    const stems = [
      ['M24 94Q35 67 25 30Q22 17 30 2','M25 31Q7 17 8 3Q24 8 25 31M30 55Q48 37 48 26Q29 34 30 55M27 72Q12 60 6 44Q27 48 27 72'],
      ['M108 203Q103 169 119 136Q128 119 121 103','M111 158Q92 143 94 127Q111 132 111 158M119 137Q138 133 144 116Q125 115 119 137M106 181Q126 173 133 156Q111 160 106 181'],
      ['M211 98Q191 71 203 38Q207 21 204 12','M201 45Q218 35 225 19Q204 19 201 45M199 62Q181 57 174 41Q194 42 199 62M204 82Q225 79 234 62Q215 61 204 82'],
      ['M215 251Q226 224 216 198Q209 179 220 164','M217 203Q233 193 239 181Q221 181 217 203M220 224Q203 215 199 199Q219 203 220 224'],
      ['M20 239Q27 214 19 191Q16 181 23 165','M22 206Q40 192 39 182Q23 184 22 206M20 192Q8 189 3 175Q18 173 20 192'],
    ];
    for (const [stem,leaves] of stems) {
      body+=path(stem,'#a3b3a3',1,.42);
      body+=path(leaves,'#a3b3a3',.85,.32,'#33494a');
    }
    if (name === 'roots') {
      body+=path('M-8 119Q34 133 62 108T122 75T179 115T264 131M58 111Q67 145 52 172M83 93Q61 62 67 39M153 85Q160 63 146 46M192 119Q178 151 187 175','#829c94',2.2,.32);
      body+=path('M-4 124Q35 137 63 112T120 79T179 119T262 135M53 172l-8 9m10-21 8 13M67 39l-7-8m8 19 9-11M187 175l-4 16m3-27 12 12','#b7c0aa',.75,.32);
    }
    for (const [x,y] of [[67,215],[158,39],[158,229],[68,74],[171,165]]) {
      body+=path(`M${x} ${y}q-7-10-5-15m5 15q1-14 8-18m-8 18q-11-5-14-1`,'#c0c3a9',.85,.4);
      body+=`<circle cx="${x+8}" cy="${y-18}" r="1.5" fill="#c0c3a9" opacity=".35"/>`;
    }
  }
  return svg(size,size,body);
}

const drawings = {
  religion:'M23 51V31Q32 14 41 31V51M19 51H45M19 32V22M45 32V22M29 51V40Q32 35 35 40V51M28 15Q32 10 36 15',
  worship:'M23 51V31Q32 14 41 31V51M19 51H45M32 19V9M27 13H37M29 51V40Q32 35 35 40V51',
  culture:'M18 49V27L32 17L46 27V49M14 50H50M22 29H42M24 32V44M32 32V44M40 32V44',
  museum:'M15 27L32 15L49 27Z M19 31V46M27 31V46M37 31V46M45 31V46M15 49H49M13 53H51',
  theatre:'M17 20Q32 26 47 20V35Q43 49 32 52Q21 47 17 35ZM23 31Q26 28 29 31M35 31Q38 28 41 31M25 39Q32 46 39 39',
  education:'M17 23Q26 20 32 26Q38 20 47 23V47Q38 44 32 50Q26 44 17 47ZM32 26V50M21 29L27 30M21 35L27 36M37 30L43 29M37 36L43 35',
  school:'M17 23Q26 20 32 26Q38 20 47 23V47Q38 44 32 50Q26 44 17 47ZM32 26V50M21 29L27 30M21 35L27 36M37 30L43 29M37 36L43 35',
  shops:'M20 28H44L47 49Q32 54 17 49ZM25 28V23Q25 14 32 14Q39 14 39 23V28M22 34Q32 38 42 34M32 39V46',
  health:'M27 18H37V29H48V39H37V50H27V39H16V29H27ZM21 23L17 19M43 23L47 19',
  hospital:'M27 18H37V29H48V39H37V50H27V39H16V29H27ZM21 23L17 19M43 23L47 19',
  clinic:'M27 18H37V29H48V39H37V50H27V39H16V29H27Z',
  pharmacy:'M24 19H40M27 19V28L19 44Q17 51 25 52H39Q47 51 45 44L37 28V19M25 37H39M32 39V47M28 43H36',
  parking:'M24 51V19H34Q48 19 47 31Q46 41 32 40H24M29 24H34Q42 24 42 31Q42 35 34 35H29M17 54H41',
  food:'M18 43Q18 28 32 27Q46 28 46 43ZM15 47H49M32 23V19M28 19H36M22 51H42',
  restaurant:'M20 16V29Q20 34 25 34V52M25 16V34M30 16V29Q30 34 25 34M43 52V16Q35 26 38 35H43',
  cafe:'M18 28H40V39Q39 49 29 49Q19 48 18 39ZM40 30Q52 28 48 39Q45 44 39 40M15 53H44M24 23Q18 18 25 12M33 23Q27 18 34 12',
  'fast-food':'M18 34Q18 23 32 23Q46 23 46 34ZM17 39H47M19 44H45L43 50H21ZM25 29L26 28M34 28L35 29M40 30L39 29',
  bar:'M19 20H45L41 36Q32 46 23 36ZM32 41V51M23 53H41M23 28H41',
  fuel:'M21 20H38V51H19V25ZM24 25H34V34H24ZM38 28Q47 26 46 37V45Q46 51 42 47V38M46 28L42 21M16 54H42',
  parks:'M27 53Q35 39 33 17M32 34Q16 32 16 17Q31 18 32 34M33 25Q43 24 48 11Q33 10 33 25M30 45Q14 45 13 32Q29 32 30 45M31 40Q46 42 50 29Q36 26 31 40',
};

function pin(name) {
  const outline = path('M15 12Q7 23 11 40Q14 51 26 58L32 63L38 58Q51 51 54 39Q57 23 49 12', '#aeb4ac', .9,.6);
  const d = drawings[name] || drawings.culture;
  const body = `<path d="M32 5Q61 9 60 35Q60 55 32 68Q4 55 4 35Q3 9 32 5Z" fill="#182330" fill-opacity=".9"/>${outline}${path(d,'#f0e8d1',2,1)}${path('M26 58Q32 61 38 58','#d5cdb7',.8,.7)}`;
  return svg(80,88,`<g transform="translate(8 5)">${body}</g>`);
}

async function rasterize(markup) {
  const blob = new Blob([markup],{type:'image/svg+xml'});
  const url = URL.createObjectURL(blob);
  try {
    const image = new Image(); image.decoding = 'async'; image.src = url;
    await image.decode();
    const canvas = document.createElement('canvas');
    canvas.width = image.naturalWidth; canvas.height = image.naturalHeight;
    const context = canvas.getContext('2d',{willReadFrequently:true});
    context.drawImage(image,0,0);
    return {width:canvas.width,height:canvas.height,data:new Uint8Array(context.getImageData(0,0,canvas.width,canvas.height).data),pixelRatio:2};
  } finally { URL.revokeObjectURL(url); }
}

let imagePromise;
export async function getImages() {
  imagePromise ??= (async () => {
    const entries = [];
    for (const night of [false,true]) for (const name of ['paper','commercial','industrial','square','room','water','foliage','roots']) {
      entries.push([imageId(name,night),material(name,night)]);
    }
    for (const name of Object.keys(drawings)) entries.push([`hollow-poi-${name}`,pin(name)]);
    // MapLibre rotates line symbols relative to an east-pointing source drawing.
    entries.push(['hollow-oneway',svg(64,32,path('M9 16H50M40 8L51 16L40 24','#e7dec5',2.8))]);
    return Object.fromEntries(await Promise.all(entries.map(async ([id,markup]) => [id,await rasterize(markup)])));
  })();
  return imagePromise;
}
