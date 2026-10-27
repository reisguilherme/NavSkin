#!/usr/bin/env node
/** Convert cached Overpass geometry to local GeoJSON. Uses only Node standard APIs. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { bounds } from './fetch-osm.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const inputDir = path.join(root, 'data', 'osm');
const outputDir = path.join(root, 'public', 'data');
const data = JSON.parse(await fs.readFile(path.join(inputDir, 'extract.json'), 'utf8'));
const discovery = JSON.parse(await fs.readFile(path.join(inputDir, 'places.json'), 'utf8'));
const provenance = JSON.parse(await fs.readFile(path.join(inputDir, 'extract.provenance.json'), 'utf8'));
const discoveryProvenance = JSON.parse(await fs.readFile(path.join(inputDir, 'places.provenance.json'), 'utf8'));
const elements = new Map(data.elements.map(e => [`${e.type}/${e.id}`, e]));
const warnings = [];

const same = (a, b) => a[0] === b[0] && a[1] === b[1];
const coordinates = geom => (geom ?? []).filter(p => Number.isFinite(p?.lon) && Number.isFinite(p?.lat)).map(p => [p.lon, p.lat]);
const closed = coords => coords.length >= 4 && same(coords[0], coords.at(-1));
const signedArea = ring => ring.slice(0, -1).reduce((sum, a, i) => sum + a[0] * ring[i + 1][1] - ring[i + 1][0] * a[1], 0) / 2;
const orient = (ring, ccw) => (signedArea(ring) > 0) === ccw ? ring : [...ring].reverse();
const inRing = (p, ring) => {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[i], b = ring[j];
    if ((a[1] > p[1]) !== (b[1] > p[1]) && p[0] < (b[0] - a[0]) * (p[1] - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
  }
  return inside;
};

function stitch(segments, id, role) {
  const remaining = segments.filter(s => s.length >= 2).map(s => [...s]);
  const rings = [];
  while (remaining.length) {
    let ring = remaining.shift();
    while (!closed(ring)) {
      const index = remaining.findIndex(s => same(ring.at(-1), s[0]) || same(ring.at(-1), s.at(-1)) || same(ring[0], s.at(-1)) || same(ring[0], s[0]));
      if (index === -1) break;
      let part = remaining.splice(index, 1)[0];
      if (same(ring.at(-1), part[0])) ring.push(...part.slice(1));
      else if (same(ring.at(-1), part.at(-1))) ring.push(...part.reverse().slice(1));
      else if (same(ring[0], part.at(-1))) ring = [...part.slice(0, -1), ...ring];
      else ring = [...part.reverse().slice(0, -1), ...ring];
    }
    if (closed(ring)) rings.push(ring);
    else warnings.push(`Skipped incomplete ${role} ring in ${id} (${ring.length} vertices); no artificial closure.`);
  }
  return rings;
}

function polygonGeometry(e) {
  if (e.type === 'way') {
    const coords = coordinates(e.geometry);
    return closed(coords) ? { type: 'Polygon', coordinates: [orient(coords, true)] } : null;
  }
  if (e.type !== 'relation' || !['multipolygon', 'boundary'].includes(e.tags?.type)) return null;
  const members = (e.members ?? []).filter(m => m.type === 'way');
  const outer = stitch(members.filter(m => m.role !== 'inner').map(m => coordinates(m.geometry)), `${e.type}/${e.id}`, 'outer');
  const inner = stitch(members.filter(m => m.role === 'inner').map(m => coordinates(m.geometry)), `${e.type}/${e.id}`, 'inner');
  const polygons = outer.map(r => [orient(r, true)]);
  for (const hole of inner) {
    const hosts = polygons.filter(p => inRing(hole[0], p[0])).sort((a, b) => Math.abs(signedArea(a[0])) - Math.abs(signedArea(b[0])));
    if (hosts.length) hosts[0].push(orient(hole, false));
    else warnings.push(`Unassigned inner ring in ${e.type}/${e.id}`);
  }
  if (!polygons.length) return null;
  return polygons.length === 1 ? { type: 'Polygon', coordinates: polygons[0] } : { type: 'MultiPolygon', coordinates: polygons };
}

function centroid(e) {
  if (e.type === 'node') return [e.lon, e.lat];
  const geom = polygonGeometry(e);
  const rings = geom?.type === 'Polygon' ? [geom.coordinates[0]] : geom?.coordinates.map(p => p[0]);
  const ring = rings?.sort((a, b) => Math.abs(signedArea(b)) - Math.abs(signedArea(a)))[0];
  if (ring) {
    let x = 0, y = 0, area = 0;
    for (let i = 0; i < ring.length - 1; i++) {
      const a = ring[i], b = ring[i + 1];
      const cross = a[0] * b[1] - b[0] * a[1];
      area += cross;
      x += (a[0] + b[0]) * cross;
      y += (a[1] + b[1]) * cross;
    }
    if (area) return [Number((x / (3 * area)).toFixed(7)), Number((y / (3 * area)).toFixed(7))];
  }
  const b = e.bounds;
  return b ? [(b.minlon + b.maxlon) / 2, (b.minlat + b.maxlat) / 2] : null;
}

const highwayKinds = {
  motorway: 'motorway', motorway_link: 'motorway', trunk: 'trunk', trunk_link: 'trunk',
  primary: 'primary', primary_link: 'primary', secondary: 'secondary', secondary_link: 'secondary',
  tertiary: 'tertiary', tertiary_link: 'tertiary', residential: 'residential', living_street: 'residential',
  unclassified: 'residential', service: 'service', busway: 'busway', road: 'residential',
  track: 'track', footway: 'path', cycleway: 'path', pedestrian: 'path', path: 'path',
  steps: 'path', bridleway: 'path', corridor: 'path', raceway: 'track',
};
function areaKind(t) {
  if (t.natural === 'water' || t.water || ['reservoir', 'basin'].includes(t.landuse) || t.waterway === 'riverbank') return 'water';
  if (['park', 'garden', 'nature_reserve'].includes(t.leisure)) return 'park';
  if (t.natural === 'wood' || t.landuse === 'forest') return 'wood';
  if (['grassland', 'heath'].includes(t.natural) || ['grass', 'meadow', 'village_green', 'greenfield'].includes(t.landuse)) return 'grass';
  if (t.natural === 'scrub') return 'scrub';
  if (t.natural === 'wetland') return 'wetland';
  if (t.landuse) return t.landuse;
  if (t.leisure === 'golf_course') return 'grass';
  if (['pitch', 'playground', 'sports_centre', 'recreation_ground'].includes(t.leisure)) return t.leisure;
  if (t.place === 'square') return 'square';
  return null;
}

function properties(e, kind) {
  const t = e.tags ?? {};
  const layer = Number.isFinite(Number(t.layer)) ? Number(t.layer) : 0;
  const affirmative = value => value != null && !['no', 'false', '0'].includes(value);
  return {
    osm_id: `${e.type}/${e.id}`, kind, name: t.name ?? '',
    highway: t.highway ?? null, railway: t.railway ?? null,
    bridge: t.bridge ?? null, tunnel: t.tunnel ?? null,
    layer, grade: layer || (affirmative(t.tunnel) ? -1 : affirmative(t.bridge) ? 1 : 0),
    landuse: t.landuse ?? null, natural: t.natural ?? null,
    leisure: t.leisure ?? null, waterway: t.waterway ?? null,
    water: t.water ?? null, place: t.place ?? null,
    oneway: t.oneway ?? null, junction: t.junction ?? null,
    surface: t.surface ?? null,
  };
}

const roads = [], areas = [], waterways = [], places = [];
for (const e of data.elements) {
  const t = e.tags ?? {};
  const coords = coordinates(e.geometry);
  if (t.building && t.building !== 'no') {
    const geom = polygonGeometry(e);
    if (geom) areas.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: {
      ...properties(e, 'building'), building: t.building,
      height: t.height ?? null, levels: Number.isFinite(Number(t['building:levels'])) ? Number(t['building:levels']) : null,
    }, geometry: geom });
  }
  if (e.type === 'way' && coords.length >= 2 && (t.highway || t.railway)) {
    // Filled pedestrian areas belong to the area collection, never fake road axes.
    if (t.area === 'yes' && closed(coords)) {
      areas.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: properties(e, t.highway === 'pedestrian' ? 'square' : 'road_area'), geometry: polygonGeometry(e) });
    } else {
      roads.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: properties(e, t.railway ? 'rail' : (highwayKinds[t.highway] ?? 'service')), geometry: { type: 'LineString', coordinates: coords } });
    }
  }
  const kind = t.building && t.building !== 'no' ? null : areaKind(t);
  if (kind) {
    const geom = polygonGeometry(e);
    if (geom) areas.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: properties(e, kind), geometry: geom });
  }
  if (e.type === 'way' && t.waterway && t.waterway !== 'riverbank' && coords.length >= 2 && t.area !== 'yes') {
    waterways.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: properties(e, t.waterway), geometry: { type: 'LineString', coordinates: coords } });
  }
  if (t.name && t.place && ['city', 'town', 'suburb', 'quarter', 'neighbourhood', 'square'].includes(t.place)) {
    const coord = centroid(e);
    if (coord) places.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: {
      ...properties(e, ['city', 'town'].includes(t.place) ? 'city' : t.place === 'square' ? 'square' : 'district'),
      rank: ['city', 'town'].includes(t.place) ? 0 : t.place === 'suburb' ? 2 : 3,
      featured: false,
    }, geometry: { type: 'Point', coordinates: coord } });
  }
  if (t.name && ['park', 'garden', 'nature_reserve'].includes(t.leisure)) {
    const coord = centroid(e);
    if (coord) places.push({ type: 'Feature', id: `${e.type}/${e.id}`, properties: {
      ...properties(e, 'park'), rank: 3, featured: false,
    }, geometry: { type: 'Point', coordinates: coord } });
  }
}

const required = [
  { name: 'Centro', match: e => e.tags?.place && ['Setor Central', 'Centro'].includes(e.tags.name), kind: 'district', rank: 1 },
  { name: 'Praça Cívica', match: e => `${e.type}/${e.id}` === 'way/670650221', kind: 'square', rank: 1 },
  { name: 'Setor Bueno', match: e => `${e.type}/${e.id}` === 'node/3561431327', kind: 'district', rank: 1 },
  { name: 'Parque Vaca Brava', match: e => `${e.type}/${e.id}` === 'way/159590062', kind: 'park', rank: 1 },
  { name: 'Parque Flamboyant', match: e => `${e.type}/${e.id}` === 'way/220688361', kind: 'park', rank: 1 },
];
const verifiedLocations = required.map(spec => {
  const e = discovery.elements.find(spec.match);
  if (!e) throw new Error(`Discovery has not verified ${spec.name}. Run node scripts/fetch-osm.mjs places first.`);
  const coord = centroid(e);
  if (!coord || coord[0] < bounds.west || coord[0] > bounds.east || coord[1] < bounds.south || coord[1] > bounds.north) throw new Error(`Required location outside final bounds: ${spec.name}`);
  const id = `${e.type}/${e.id}`;
  const existing = places.findIndex(p => p.id === id);
  const feature = { type: 'Feature', id, properties: {
    ...properties(e, spec.kind), name: spec.name, osm_name: e.tags.name,
    rank: spec.rank, featured: true,
  }, geometry: { type: 'Point', coordinates: coord } };
  if (existing < 0) places.push(feature);
  else places[existing] = feature;
  return {
    name: spec.name, osmName: e.tags.name, osmId: id,
    coordinates: coord, coordinateSource: e.type === 'node' ? 'OSM node coordinate' : 'centroid derived from complete OSM polygon',
    osmUrl: `https://www.openstreetmap.org/${id}`, tags: e.tags,
  };
});

const collections = { roads, areas, waterways, places };
const counts = {};
await fs.mkdir(outputDir, { recursive: true });
for (const [name, features] of Object.entries(collections)) {
  const unique = [...new Map(features.map(f => [f.id, f])).values()].sort((a, b) => a.id.localeCompare(b.id));
  collections[name] = unique;
  const byKind = {};
  for (const f of unique) byKind[f.properties.kind] = (byKind[f.properties.kind] ?? 0) + 1;
  counts[name] = { total: unique.length, byKind };
  await fs.writeFile(path.join(outputDir, `${name}.geojson`), JSON.stringify({ type: 'FeatureCollection', features: unique }) + '\n');
}

const metadata = {
  title: 'Goiânia — OpenStreetMap local vector extract',
  bounds, bbox: [bounds.west, bounds.south, bounds.east, bounds.north],
  center: [-49.264, -16.693], crs: 'EPSG:4326',
  attribution: '© OpenStreetMap contributors',
  attributionUrl: 'https://www.openstreetmap.org/copyright',
  license: 'ODbL-1.0', licenseUrl: 'https://opendatacommons.org/licenses/odbl/1-0/',
  source: 'OpenStreetMap via Overpass API',
  endpoint: provenance.endpoint, fetchedAt: provenance.fetchedAt, dataTimestamp: provenance.dataTimestamp,
  discovery: discoveryProvenance,
  generatedAt: new Date().toISOString(),
  verifiedLocations, counts, warnings,
  processing: {
    coordinates: 'Original full OSM way/member coordinates; longitude,latitude. No road or polygon simplification.',
    clipping: 'Query selects features intersecting the bounding box. Full selected geometries are preserved, so some coordinates extend beyond viewport bounds.',
    labels: 'Named OSM place nodes and centroids derived from selected OSM polygons. Centro is the display alias for OSM Setor Central.',
    areaRelations: 'Multipolygon member segments joined by identical endpoints; inner rings retained. Incomplete rings are omitted and reported.',
    buildings: 'Actual OSM building footprints, without generated blocks or generalization; coverage depends on OSM mapping.',
    unmappedLand: 'No inferred city blocks, generated buildings, or invented land-use polygons.',
  },
};
await fs.writeFile(path.join(outputDir, 'metadata.json'), JSON.stringify(metadata, null, 2) + '\n');
console.log(JSON.stringify({ counts, verifiedLocations, warnings }, null, 2));
