import { readFile, mkdir, writeFile } from 'node:fs/promises';
const root=new URL('../',import.meta.url);
const read=async name=>JSON.parse(await readFile(new URL(`public/data/${name}.geojson`,root),'utf8'));
const [roads,areas,waterways,places]=await Promise.all(['roads','areas','waterways','places'].map(read));
const box=[-49.278,-16.697,-49.241,-16.667];
function touches(geometry){
  const points=[];
  const visit=x=>{if(typeof x[0]==='number')points.push(x);else x.forEach(visit);};
  visit(geometry.coordinates);
  return points.some(([x,y])=>x>=box[0]&&x<=box[2]&&y>=box[1]&&y<=box[3]);
}
const data={
  roads:{...roads,features:roads.features.filter(f=>touches(f.geometry)&&!['service','track','path'].includes(f.properties.kind))},
  areas:{...areas,features:areas.features.filter(f=>touches(f.geometry))},
  waterways:{...waterways,features:waterways.features.filter(f=>touches(f.geometry))},
  places:{...places,features:places.features.filter(f=>f.properties.featured&&touches(f.geometry))},
};
const layers=[{id:'background',type:'background',paint:{'background-color':'#999999'}}];
const fill=(id,kinds,color)=>layers.push({id,type:'fill',source:'areas',filter:['in',['get','kind'],['literal',kinds]],paint:{'fill-color':color}});
fill('parks',['park','garden','grass','pitch','sports_centre'],'#748837');
fill('wood',['wood','forest'],'#38682C');
fill('water',['water'],'#7088AB');
fill('buildings',['building'],'#F7FCF8');
layers.push({id:'streams',type:'line',source:'waterways',paint:{'line-color':'#7088AB','line-width':2}});
for(const [kinds,width] of [[['residential','unclassified','road'],2.1],[['tertiary'],3.6],[['secondary','busway'],4.8],[['primary','trunk','motorway'],6.2]]){
  layers.push({id:`roads-${width}`,type:'line',source:'roads',filter:['in',['get','kind'],['literal',kinds]],layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#080A08','line-width':width}});
}
layers.push({id:'verified-place',type:'circle',source:'places',paint:{'circle-color':'#F7FCF8','circle-radius':5,'circle-stroke-color':'#080A08','circle-stroke-width':2}});
const style={version:8,name:'Prova mínima nativa · Goiânia',metadata:{'gamemap:attribution':'© OpenStreetMap contributors · ODbL','gamemap:scope':'Real OSM subset for the Surface integration gate; no fictional geometry.'},sources:Object.fromEntries(Object.entries(data).map(([name,value])=>[name,{type:'geojson',data:value,attribution:'© OpenStreetMap contributors · ODbL'}])),layers};
const output=new URL('android/app/src/main/assets/maps/',root);
await mkdir(output,{recursive:true});
await writeFile(new URL('probe-style.json',output),JSON.stringify(style));
console.log(JSON.stringify({file:'maps/probe-style.json',counts:Object.fromEntries(Object.entries(data).map(([name,value])=>[name,value.features.length])),bytes:Buffer.byteLength(JSON.stringify(style))}));
