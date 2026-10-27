import { mkdir, copyFile, readdir } from 'node:fs/promises';
await mkdir(new URL('../public/docs/',import.meta.url),{recursive:true});
for(const name of ['references.md','data.md','style.md','validation.md','buildings.md','pois.md','icons.md']) {
  try {await copyFile(new URL(`../docs/${name}`,import.meta.url),new URL(`../public/docs/${name}`,import.meta.url));}
  catch(error){if(error.code!=='ENOENT')throw error;}
}
// Publish the local, static comparison gallery alongside the app when present.
const screenshots=new URL('../screenshots/',import.meta.url);
const comparisons=new URL('../public/comparacoes/',import.meta.url);
await mkdir(comparisons,{recursive:true});
try {
  for(const name of await readdir(screenshots)) if(/\.(jpg|json|html)$/.test(name)) await copyFile(new URL(name,screenshots),new URL(name,comparisons));
} catch(error){if(error.code!=='ENOENT')throw error;}
