/** Prepare a small browser-only 3D study. Never writes mobile resources. */
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const project = path.resolve(here, '../..');
const output = path.join(here, 'data');
const input = await readFile(path.join(project, 'public/data/buildings.geojson'));
const original = JSON.parse(input);
const bounds = [-49.2485, -16.6817, -49.2358, -16.6720];
const route = [
  [-49.241999, -16.6789193], [-49.2423778, -16.6777175], [-49.2424604, -16.6774921],
  [-49.2424696, -16.6774179], [-49.2424619, -16.6773357], [-49.2424435, -16.677311],
  [-49.242298, -16.6772164], [-49.2421639, -16.6771445], [-49.2419701, -16.6769102],
  [-49.2418883, -16.6765066], [-49.2418726, -16.6764487], [-49.2418203, -16.6764327],
  [-49.2411407, -16.6762287], [-49.2407741, -16.6761233], [-49.2406792, -16.676096],
  [-49.2405367, -16.6760542], [-49.2399123, -16.6758722],
];
const rings = geometry => geometry.type === 'Polygon' ? geometry.coordinates : geometry.coordinates.flat();
const intersects = geometry => rings(geometry).some(ring => ring.some(([x, y]) =>
  x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3]));
const hash = text => createHash('sha256').update(text).digest().readUInt32LE(0);
let reported = 0;
const features = original.features.filter(feature => ['Polygon', 'MultiPolygon'].includes(feature.geometry.type)
  && intersects(feature.geometry)).map((feature, index) => {
  const p = feature.properties;
  const id = p.gers_id || p.osm_id || String(index);
  const height = Number(p.height);
  const hasHeight = Number.isFinite(height) && height >= 2 && height <= 160;
  if (hasHeight) reported++;
  // Stable illustrative height, not inferred measurement or a building survey.
  const sample = hash(id);
  const mock = 6 + (sample % 8) * 3 + ((sample >>> 12) % 6 === 0 ? 6 : 0);
  return { type: 'Feature', id: index, geometry: feature.geometry, properties: {
    id, name: p.name || '', source: p.source || 'Overture Maps',
    source_datasets: p.source_datasets || '', lab_height: hasHeight ? height : mock,
    lab_height_source: hasHeight ? 'source' : 'illustrative',
    ...(hasHeight ? { reported_height: height } : {}),
  } };
});
if (features.length < 100 || features.length > 5000) throw new Error(`Unexpected scene size: ${features.length}`);
const data = { type: 'FeatureCollection', features };
const metadata = {
  schemaVersion: 1, kind: 'Browser design study; not an installable app or live navigation',
  bounds, buildingCount: features.length,
  source: { file: 'public/data/buildings.geojson', sha256: createHash('sha256').update(input).digest('hex'),
    attribution: '© OpenStreetMap contributors · Overture Maps Foundation · Google Open Buildings · Microsoft Global ML Building Footprints',
    coordinatesModified: false },
  heights: { fromSource: reported, illustrative: features.length - reported,
    note: 'Alturas disponíveis na fonte foram mantidas. As demais são volumes ilustrativos estáveis, sem precisão de levantamento.',
    mockRangeMeters: [6, 33] },
  route: { demonstration: true, source: 'Local OSM road coordinates reused from prototypes/navskin-glass/map.js; not a routing API response', coordinates: route },
  camera: { center: route[0], zoom: 17.0, bearing: -17, pitch: 48 },
};
await mkdir(output, { recursive: true });
await writeFile(path.join(output, 'scene-buildings.geojson'), JSON.stringify(data));
await writeFile(path.join(output, 'scene.json'), JSON.stringify(metadata, null, 2) + '\n');
console.log(`Browser scene: ${features.length} real footprints, ${reported} source heights, ${features.length - reported} illustrative heights.`);
