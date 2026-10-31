import http from 'node:http';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const prototypeRoot = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(prototypeRoot, '../..');
const assetRoot = path.join(projectRoot, 'android/app/src/main/assets');
const vendorRoot = path.join(projectRoot, 'node_modules/maplibre-gl/dist');
const vendorFiles = new Set(['maplibre-gl.mjs', 'maplibre-gl-shared.mjs', 'maplibre-gl-worker.mjs', 'maplibre-gl.css']);
const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.geojson': 'application/geo+json; charset=utf-8', '.png': 'image/png', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.otf': 'font/otf', '.pbf': 'application/x-protobuf', '.md': 'text/plain; charset=utf-8' };

function inside(root, relative) {
  const resolved = path.resolve(root, relative);
  return resolved.startsWith(root + path.sep) ? resolved : null;
}

const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  if (!['GET', 'HEAD'].includes(req.method)) { res.writeHead(405, { Allow: 'GET, HEAD' }); res.end(); return; }
  let raw;
  try { raw = decodeURIComponent((req.url || '/').split('?')[0]); }
  catch { res.writeHead(400); res.end('Invalid URL'); return; }
  if (raw.includes('\0') || raw.includes('\\') || raw.split('/').includes('..')) { res.writeHead(403); res.end('Forbidden'); return; }
  let root = prototypeRoot, relative = raw === '/' ? 'index.html' : raw.slice(1);
  if (raw.startsWith('/assets/')) { root = assetRoot; relative = raw.slice(8); }
  else if (raw.startsWith('/vendor/')) {
    root = vendorRoot; relative = raw.slice(8);
    if (!vendorFiles.has(relative)) { res.writeHead(404); res.end('Not found'); return; }
  } else if (relative.split('/').some(segment => segment.startsWith('.')) || relative === 'server.mjs') {
    res.writeHead(404); res.end('Not found'); return;
  }
  const file = inside(root, relative);
  if (!file) { res.writeHead(403); res.end('Forbidden'); return; }
  try {
    const [realFile, realRoot, stat] = await Promise.all([fs.promises.realpath(file), fs.promises.realpath(root), fs.promises.stat(file)]);
    if (!realFile.startsWith(realRoot + path.sep) || !stat.isFile()) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': mime[path.extname(file).toLowerCase()] || 'application/octet-stream', 'Content-Length': stat.size, 'Cache-Control': root === prototypeRoot ? 'no-store' : 'public, max-age=3600' });
    if (req.method === 'HEAD') { res.end(); return; }
    fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
  } catch { res.writeHead(404); res.end('Not found'); }
});

server.listen(4175, '127.0.0.1', () => console.log('NavSkin prototype: http://127.0.0.1:4175/'));
server.on('error', error => { console.error(error.message); process.exitCode = 1; });

