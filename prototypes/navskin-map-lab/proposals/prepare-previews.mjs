import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const out=path.dirname(fileURLToPath(import.meta.url)),root=path.resolve(out,'../../..');
const box=[-49.266,-16.6855,-49.257,-16.679],w=900,h=680;
const point=c=>[(c[0]-box[0])/(box[2]-box[0])*w,(box[3]-c[1])/(box[3]-box[1])*h];
function positions(c){return typeof c[0]==='number'?[c]:c.flatMap(positions);}
function intersects(f){const p=positions(f.geometry.coordinates);return Math.min(...p.map(c=>c[0]))<box[2]&&Math.max(...p.map(c=>c[0]))>box[0]&&Math.min(...p.map(c=>c[1]))<box[3]&&Math.max(...p.map(c=>c[1]))>box[1];}
function read(n){return JSON.parse(fs.readFileSync(path.join(root,'public/data',n+'.geojson'),'utf8')).features.filter(intersects);}
const buildings=read('buildings'),roads=read('roads').filter(f=>f.geometry.type==='LineString'),areas=read('areas');
const forest=areas.filter(f=>['park','forest','wood','grass','grassland','garden','recreation_ground','scrub'].includes(f.properties.kind));
const water=areas.filter(f=>['water','reservoir','basin'].includes(f.properties.kind));
const pathData=geometry=>{
 const rings=geometry.type==='Polygon'?geometry.coordinates:geometry.type==='MultiPolygon'?geometry.coordinates.flat():[geometry.coordinates];
 return rings.map(r=>r.map((c,i)=>{const p=point(c);return(i?'L':'M')+p.map(v=>v.toFixed(1)).join(',');}).join('')+(geometry.type.includes('Polygon')?'Z':'')).join('');
};
const paths=(fs,attrs)=>fs.map(f=>'<path d="'+pathData(f.geometry)+'" '+attrs+'/>').join('');
const spec={
 zelda:{bg:'#574c31',park:'#746d43',water:'#213d4b',building:'#403b2c',ink:'#bbae70',road:'#c4b285',edge:'#403a26',label:'#e1d1a2',accent:'#4bdce6',font:'Georgia',title:'SHEIKAH / GOIANIA',mode:'Atlas de exploração'},
 fallout:{bg:'#081a11',park:'#102c1c',water:'#0e251a',building:'none',ink:'#5fcb79',road:'#9aefa0',edge:'#0b2819',label:'#b5ffb5',accent:'#d7ffd2',font:'monospace',title:'PIP-BOY / LOCAL MAP',mode:'Terminal de fósforo'},
 animal:{bg:'#b5daa5',park:'#77b47c',water:'#8fd5e3',building:'#ecb18d',ink:'#a17a55',road:'#f7edc3',edge:'#bba772',label:'#506e55',accent:'#ffeb91',font:'Trebuchet MS',title:'BURITIS / ILHA-CIDADE',mode:'Vila de brinquedo'},
 hollow:{bg:'#1b2434',park:'#303f51',water:'#172132',building:'#263448',ink:'#87929e',road:'#ded7c3',edge:'#364356',label:'#eee9d7',accent:'#c7e5fa',font:'Georgia',title:'CARTOGRAFIA / BURITIS',mode:'Tinta & penumbra'},
 sea:{bg:'#ddc795',park:'#b4b584',water:'#81b2b1',building:'#bd9f72',ink:'#6f583b',road:'#6b553a',edge:'#e9d5a9',label:'#554631',accent:'#ba4c32',font:'Georgia',title:'CARTA DOS BURITIS',mode:'Atlas de navegação'}
};
for(const [id,p] of Object.entries(spec)){
 const defs='<defs><pattern id="grain" width="26" height="26" patternUnits="userSpaceOnUse"><path d="M2 5h2M14 19h4M22 9h1" stroke="'+p.ink+'" stroke-opacity=".12"/></pattern><pattern id="hatch" width="8" height="8" patternUnits="userSpaceOnUse"><path d="M-1 8 8-1M3 12 12 3" stroke="'+p.ink+'" stroke-width=".7" opacity=".5"/></pattern><pattern id="scan" width="8" height="5" patternUnits="userSpaceOnUse"><path d="M0 1h8" stroke="#000" opacity=".26"/></pattern><pattern id="leaves" width="28" height="24" patternUnits="userSpaceOnUse"><path d="m8 13 3-5 3 5m-3-5v8M25 22l-2-4" stroke="'+p.ink+'" stroke-opacity=".3" fill="none"/></pattern><pattern id="grid" width="100" height="100" patternUnits="userSpaceOnUse"><path d="M100 0H0V100" stroke="'+p.ink+'" opacity=".18" fill="none"/></pattern><filter id="glow"><feGaussianBlur stdDeviation="1.6"/></filter><filter id="paper"><feTurbulence type="fractalNoise" baseFrequency=".48" numOctaves="3" stitchTiles="stitch"/><feColorMatrix type="saturate" values="0"/><feComponentTransfer><feFuncA type="linear" slope=".09"/></feComponentTransfer><feBlend in="SourceGraphic" mode="multiply"/></filter></defs>';
 let s='<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '+w+' '+h+'" role="img" aria-label="Estudo '+id+' sobre contornos reais do Bosque dos Buritis, Goiânia">'+defs+'<rect width="100%" height="100%" fill="'+p.bg+'"/>';
 s+='<g stroke-linecap="round" stroke-linejoin="round">';
 if(id==='zelda')s+=paths(forest,'fill="none" stroke="'+p.ink+'" stroke-width="22" opacity=".13"')+paths(forest,'fill="none" stroke="'+p.ink+'" stroke-width="12" opacity=".22"');
 if(id==='sea')s+='<rect width="100%" height="100%" fill="url(#grid)"/>';
 s+=paths(forest,'fill="'+p.park+'" stroke="'+p.ink+'" stroke-width="'+(id==='animal'?4:id==='hollow'?2:1.3)+'"');
 s+=paths(forest,'fill="url(#leaves)"');
 if(id==='animal')s+=paths(water,'fill="none" stroke="#e9edbc" stroke-width="9"');
 if(id==='sea')s+=paths(water,'fill="none" stroke="'+p.ink+'" stroke-width="9" opacity=".35"');
 s+=paths(water,'fill="'+p.water+'" stroke="'+(id==='hollow'?'#849db3':p.ink)+'" stroke-width="1.7"');
 if(id==='zelda'||id==='sea')s+=paths(water,'fill="none" stroke="'+p.bg+'" stroke-width="3" stroke-dasharray="1 7"');
 if(id==='animal')s+='<g transform="translate(3 5)" opacity=".45">'+paths(buildings,'fill="#6d8659" stroke="none"')+'</g>';
 s+=paths(buildings,'fill="'+p.building+'" stroke="'+p.ink+'" stroke-width="'+(id==='fallout'?1.4:id==='hollow'?.9:.8)+'"'+(id==='animal'?' stroke-linejoin="round"':''));
 if(id==='sea'||id==='hollow')s+=paths(buildings,'fill="url(#hatch)" opacity="'+(id==='hollow'?'.35':'.5')+'"');
 if(id==='animal')s+='<g transform="translate(0 -2)">'+paths(buildings,'fill="#f5d6a8" stroke="#fff1cc" stroke-width=".8"')+'</g>';
 for(const road of roads){
  const kind=road.properties.kind;
  const scale=({motorway:14,trunk:13,primary:11,secondary:9,tertiary:7,residential:5,unclassified:4,service:3,footway:1.5,path:1.5,steps:1})[kind]||2;
  const d=pathData(road.geometry),isPath=['footway','path','steps','cycleway'].includes(kind);
  if(id==='animal'||id==='hollow')s+='<path d="'+d+'" fill="none" stroke="'+p.edge+'" stroke-width="'+(scale+3)+'"/>';
  const width=id==='fallout'?Math.max(.8,scale*.28):id==='sea'?Math.max(.7,scale*.25):id==='hollow'?Math.max(.9,scale*.35):scale*.7;
  s+='<path d="'+d+'" fill="none" stroke="'+p.road+'" stroke-width="'+width+'"'+(isPath||id==='zelda'?' stroke-dasharray="'+(isPath?'1 5':'5 2')+'"':'')+'/>';
 }
 s+='</g>';
 if(id==='fallout')s+='<rect width="100%" height="100%" fill="url(#grid)"/><rect width="100%" height="100%" fill="url(#scan)"/><path d="M430 335h40m-20-20v40" stroke="'+p.accent+'" opacity=".65"/>';
 else s+='<rect width="100%" height="100%" fill="url(#grain)"/>';
 if(id==='sea')s+='<g transform="translate(788 562)" stroke="'+p.ink+'" fill="none"><circle r="32"/><path d="m0-45 7 38 38 7-38 7-7 38-7-38-38-7 38-7Z"/><path d="M-26-26 26 26M-26 26 26-26" opacity=".5"/><text x="0" y="-54" text-anchor="middle" fill="'+p.ink+'" stroke="none" font-size="14">N</text></g>';
 if(id==='hollow')s+='<g fill="none" stroke="#e2dbca" opacity=".7"><path d="M40 50q-18-12-13-25q13 0 20 19q-7-26 5-30q9 16-3 33m-5 4 6 12M860 630q18 12 13 25q-13 0-20-19q7 26-5 30q-9-16 3-33m5-4-6-12"/></g>';
 const label=point([-49.2619,-16.6811]);
 s+='<g font-family="'+p.font+'" fill="'+p.label+'" text-anchor="middle"><text x="'+label[0]+'" y="'+label[1]+'" font-size="'+(id==='animal'?15:13)+'" font-weight="bold" letter-spacing="'+(id==='fallout'?2:1)+'">BOSQUE DOS BURITIS</text><text x="450" y="38" font-size="11" letter-spacing="3">'+p.title+'</text></g><rect x="20" y="20" width="860" height="640" rx="'+(id==='animal'?22:0)+'" fill="none" stroke="'+p.ink+'" stroke-width="1" opacity=".4"/><g fill="'+p.label+'" font-family="sans-serif" font-size="10"><text x="35" y="643">MESMO RECORTE REAL · ESTUDO DE DESENHO</text><text x="865" y="643" text-anchor="end">GYN / 16.68° S</text></g></svg>';
 fs.writeFileSync(path.join(out,id+'.svg'),s);
}
fs.writeFileSync(path.join(out,'provenance.json'),JSON.stringify({bounds:box,buildingCount:buildings.length,roadSegments:roads.length,areaPolygons:areas.length,source:'Local NavSkin data / OpenStreetMap, Overture, Google Open Buildings, Microsoft',geometry:'Coordinates are projected into SVG, without snapping or invented terrain. Decorative outlines do not represent elevation.',assets:'Original SVG drawing studies; no copied game art or game fonts.'},null,2));
console.log(JSON.stringify({buildings:buildings.length,roads:roads.length,parks:forest.length,water:water.length,previews:Object.keys(spec)}));
