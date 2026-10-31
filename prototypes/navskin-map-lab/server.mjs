import http from 'node:http';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';

const labRoot = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(labRoot, '../..');
const assetRoot = path.join(projectRoot, 'android/app/src/main/assets');
const vendorRoot = path.join(projectRoot, 'node_modules/maplibre-gl/dist');
const vendors = new Set(['maplibre-gl.mjs', 'maplibre-gl-shared.mjs', 'maplibre-gl-worker.mjs', 'maplibre-gl.css']);
const types = { '.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.mjs':'text/javascript; charset=utf-8', '.json':'application/json', '.geojson':'application/geo+json', '.png':'image/png', '.webp':'image/webp', '.svg':'image/svg+xml', '.ttf':'font/ttf', '.pbf':'application/x-protobuf', '.md':'text/plain; charset=utf-8' };
const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  if (!['GET','HEAD'].includes(req.method)) { res.writeHead(405); return res.end(); }
  let url;
  try { url = decodeURIComponent((req.url || '/').split('?')[0]); } catch { res.writeHead(400); return res.end(); }
  if (url === '/health') { res.setHeader('Content-Type','application/json'); return res.end(JSON.stringify({ app:'navskin-map-lab', port:4176 })); }
  if (url.includes('\0') || url.includes('\\') || url.split('/').includes('..')) { res.writeHead(403); return res.end(); }
  let root = labRoot, relative = url === '/' ? 'index.html' : url.slice(1);
  if (url.startsWith('/assets/')) { root = assetRoot; relative = url.slice(8); }
  else if (url.startsWith('/vendor/')) { root = vendorRoot; relative = url.slice(8); if (!vendors.has(relative)) { res.writeHead(404); return res.end(); } }
  else if (relative.split('/').some(segment => segment.startsWith('.')) || /\.(mjs|ps1|cmd)$/.test(relative)) { res.writeHead(404); return res.end(); }
  const file = path.resolve(root, relative);
  if (!file.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
  try {
    const [realFile, realRoot, stat] = await Promise.all([fs.promises.realpath(file), fs.promises.realpath(root), fs.promises.stat(file)]);
    if (!realFile.startsWith(realRoot + path.sep) || !stat.isFile()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type':types[path.extname(file)] || 'application/octet-stream', 'Content-Length':stat.size, 'Cache-Control':root === labRoot ? 'no-store' : 'public, max-age=3600' });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
  } catch { res.writeHead(404); res.end('Recurso local não encontrado.'); }
});
server.listen(4176,'127.0.0.1', () => console.log('NavSkin · laboratório de mapas: http://127.0.0.1:4176/'));
server.on('error', error => { console.error(error.message); process.exitCode = 1; });
