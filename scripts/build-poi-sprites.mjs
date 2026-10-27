import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { POI_CATEGORIES } from '../src/poi-categories.js';

const require = createRequire(import.meta.url);
const root = fileURLToPath(new URL('../', import.meta.url));
const makiRoot = path.dirname(require.resolve('@mapbox/maki/package.json'));
const spriteDirectory = path.join(root, 'public', 'sprites');
const iconDirectory = path.join(root, 'public', 'icons');
const iconSize = 26;
const gutter = 2;
const stride = iconSize + gutter * 2;
const columns = 5;
const rows = Math.ceil(POI_CATEGORIES.length / columns);

function escapeAttribute(value) {
  return value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
}

function makeIcon(source, category) {
  // Maki 8 uses a 15 × 15 viewBox. Keep its original path geometry and holes.
  const content = source.match(/<svg\b[^>]*>([\s\S]*?)<\/svg>/)?.[1]?.trim();
  if (!content || !/<path\b/.test(content)) {
    throw new Error(`Maki icon ${category.maki} has no SVG path geometry`);
  }
  if (!/viewBox="0 0 15 15"/.test(source)) {
    throw new Error(`Unexpected Maki viewBox for ${category.maki}`);
  }

  return `<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${iconSize}" height="${iconSize}" viewBox="0 0 ${iconSize} ${iconSize}" role="img" aria-label="${escapeAttribute(category.label)}">
  <title>${category.label}</title>
  <!-- Maki 8.2.0 (${category.maki}), Mapbox, CC0-1.0. Adapted colors and outline. -->
  <defs><g id="glyph">${content}</g></defs>
  <g transform="translate(4 4) scale(1.2)" stroke-linecap="round" stroke-linejoin="round">
    <use xlink:href="#glyph" fill="#ffffff" stroke="#ffffff" stroke-width="3"/>
    <use xlink:href="#glyph" fill="#171511" stroke="#171511" stroke-width="1.55"/>
    <use xlink:href="#glyph" fill="${category.color}"/>
  </g>
</svg>
`;
}

if (new Set(POI_CATEGORIES.map(({ id }) => id)).size !== POI_CATEGORIES.length) {
  throw new Error('POI category ids must be unique');
}

await Promise.all([
  mkdir(spriteDirectory, { recursive: true }),
  mkdir(iconDirectory, { recursive: true }),
]);

const icons = await Promise.all(POI_CATEGORIES.map(async (category) => {
  const source = await readFile(path.join(makiRoot, 'icons', `${category.maki}.svg`), 'utf8');
  const svg = makeIcon(source, category);
  const name = `poi-${category.id}`;
  await writeFile(path.join(iconDirectory, `${name}.svg`), svg);
  return { name, svg: Buffer.from(svg) };
}));

for (const pixelRatio of [1, 2]) {
  const index = {};
  const composites = await Promise.all(icons.map(async ({ name, svg }, position) => {
    const x = ((position % columns) * stride + gutter) * pixelRatio;
    const y = (Math.floor(position / columns) * stride + gutter) * pixelRatio;
    const size = iconSize * pixelRatio;
    index[name] = { width: size, height: size, x, y, pixelRatio };
    return {
      input: await sharp(svg, { density: 72 * pixelRatio }).resize(size, size).png().toBuffer(),
      left: x,
      top: y,
    };
  }));
  const suffix = pixelRatio === 2 ? '@2x' : '';
  await sharp({
    create: {
      width: columns * stride * pixelRatio,
      height: rows * stride * pixelRatio,
      channels: 4,
      background: { r: 0, g: 0, b: 0, alpha: 0 },
    },
  }).composite(composites).png().toFile(path.join(spriteDirectory, `poi${suffix}.png`));
  await writeFile(path.join(spriteDirectory, `poi${suffix}.json`), `${JSON.stringify(index, null, 2)}\n`);
}

console.log(`Built ${icons.length} local POI icons, ${iconSize}px and ${iconSize * 2}px sprites.`);
