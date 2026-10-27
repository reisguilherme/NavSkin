import { mkdir, writeFile } from 'node:fs/promises';
import { buildStyle } from '../src/style.js';
const dir=new URL('../public/styles/',import.meta.url);
await mkdir(dir,{recursive:true});
for(const [mode,file] of [['gta','gta-sa.json'],['neutral','neutral.json']]) await writeFile(new URL(file,dir),JSON.stringify(buildStyle(mode),null,2)+'\n');
