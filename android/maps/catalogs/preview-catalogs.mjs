import { mkdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = fileURLToPath(new URL('./', import.meta.url));
const runtime = fileURLToPath(new URL('../../app/src/main/assets/navigation-icons/', import.meta.url));
const catalog = JSON.parse(await readFile(path.join(here, 'catalog.json'), 'utf8'));
const palettes = {
  gta: ['GTA SA', '#91b36e', '#303a2a', '#ffe2a0', '#8644ec'],
  rosa: ['Rosa Pop', '#f3dbeb', '#89366c', '#fff8fa', '#aa39d1'],
  rdr2: ['RDR2', '#d6ba81', '#493825', '#f5e1af', '#a5422e'],
};
await mkdir(path.join(here, 'previews'), { recursive: true });
for (const [theme, [name, bg, ink, road, route]] of Object.entries(palettes)) {
  const overlays = [];
  let board = `<svg xmlns="http://www.w3.org/2000/svg" width="864" height="466"><rect width="864" height="466" fill="${bg}"/><g font-family="sans-serif" fill="${ink}"><text x="24" y="32" font-size="22" font-weight="700">${name} — original navigation catalog</text><text x="24" y="54" font-size="13">12 saved places · 6 vehicle markers · front points north ↑ · transparent assets</text>`;
  for (let index = 0; index < 18; index++) {
    const entry = [...catalog.saved, ...catalog.vehicles][index];
    const col = index % 6; const row = Math.floor(index / 6);
    const x = col * 144; const y = 72 + row * 126;
    board += `<rect x="${x+7}" y="${y}" width="130" height="118" rx="10" fill="${road}" fill-opacity=".48"/>`;
    if (index >= 12) {
      board += `<path d="M${x+25} ${y+94}L${x+72} ${y+63}L${x+103} ${y+15}" stroke="${road}" stroke-width="18" fill="none"/><path d="M${x+25} ${y+94}L${x+72} ${y+63}L${x+103} ${y+15}" stroke="${route}" stroke-width="8" fill="none"/>`;
    }
    board += `<text x="${x+72}" y="${y+106}" text-anchor="middle" font-size="12" font-weight="600">${entry.label}</text>`;
    overlays.push({ input: await sharp(path.join(runtime,entry.themeAssets[theme].preview)).resize(64,64).toBuffer(), left:x+40, top:y+15 });
  }
  board += `<text x="24" y="458" font-size="12">Image previews use the same offline PNG files selected by the app. Source: editable SVG, CC0.</text></g></svg>`;
  await sharp(Buffer.from(board)).composite(overlays).png().toFile(path.join(here, 'previews', `${theme}.png`));
}
console.log('Wrote three icon catalog contact sheets.');
