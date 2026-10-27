#!/usr/bin/env node
/** Convert cached OSM POIs into attributable local Point GeoJSON. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { bounds } from './fetch-osm.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const poiKinds = ['park', 'pharmacy', 'fuel', 'hospital', 'clinic', 'theatre', 'cinema', 'culture', 'museum', 'school', 'university', 'police', 'fire_station', 'bank', 'atm', 'parking', 'restaurant', 'cafe', 'fast_food'];
const amenityKinds = Object.fromEntries(poiKinds.filter(k => !['park', 'culture', 'museum'].includes(k)).map(k => [k, k]));
Object.assign(amenityKinds, { doctors: 'clinic', arts_centre: 'culture' });
export const classifyPoi = tags => amenityKinds[tags.amenity] ?? (['park', 'garden'].includes(tags.leisure) ? 'park' : tags.tourism === 'museum' ? 'museum' : null);
const idOf = element => `${element.type}/${element.id}`;
const same = (a, b) => a[0] === b[0] && a[1] === b[1];
const ringClosed = ring => ring.length >= 4 && same(ring[0], ring.at(-1));
const coordinates = geometry => (geometry ?? []).filter(p => Number.isFinite(p?.lon) && Number.isFinite(p?.lat)).map(p => [p.lon, p.lat]);
const signedArea = ring => {
  const [ox, oy] = ring[0];
  return ring.slice(0, -1).reduce((sum, a, i) => sum + (a[0] - ox) * (ring[i + 1][1] - oy) - (ring[i + 1][0] - ox) * (a[1] - oy), 0) / 2;
};
const inBounds = (point, bbox) => !bbox || (point[0] >= bbox.west && point[0] <= bbox.east && point[1] >= bbox.south && point[1] <= bbox.north);

export function pointInRing(point, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const a = ring[i], b = ring[j];
    if ((a[1] > point[1]) !== (b[1] > point[1]) && point[0] < (b[0] - a[0]) * (point[1] - a[1]) / (b[1] - a[1]) + a[0]) inside = !inside;
  }
  return inside;
}
export const pointInPolygon = (point, rings) => pointInRing(point, rings[0]) && !rings.slice(1).some(ring => pointInRing(point, ring));

function stitch(segments, warnings, id) {
  const remaining = segments.filter(s => s.length >= 2).map(s => [...s]);
  const rings = [];
  while (remaining.length) {
    let ring = remaining.shift();
    while (!ringClosed(ring)) {
      const index = remaining.findIndex(segment => same(ring.at(-1), segment[0]) || same(ring.at(-1), segment.at(-1)) || same(ring[0], segment.at(-1)) || same(ring[0], segment[0]));
      if (index < 0) break;
      const part = remaining.splice(index, 1)[0];
      if (same(ring.at(-1), part[0])) ring.push(...part.slice(1));
      else if (same(ring.at(-1), part.at(-1))) ring.push(...part.reverse().slice(1));
      else if (same(ring[0], part.at(-1))) ring = [...part.slice(0, -1), ...ring];
      else ring = [...part.reverse().slice(0, -1), ...ring];
    }
    if (ringClosed(ring) && Math.abs(signedArea(ring)) > 0) rings.push(ring);
    else warnings.push(`Incomplete or zero-area polygon ring omitted: ${id}.`);
  }
  return rings;
}

export function polygonParts(element, warnings = []) {
  if (element.type === 'way') {
    const ring = coordinates(element.geometry);
    return ringClosed(ring) && Math.abs(signedArea(ring)) > 0 ? [[ring]] : [];
  }
  if (element.type !== 'relation' || !['multipolygon', 'boundary'].includes(element.tags?.type)) return [];
  const members = (element.members ?? []).filter(member => member.type === 'way');
  const outers = stitch(members.filter(m => m.role !== 'inner').map(m => coordinates(m.geometry)), warnings, idOf(element));
  const inners = stitch(members.filter(m => m.role === 'inner').map(m => coordinates(m.geometry)), warnings, idOf(element));
  const polygons = outers.map(ring => [ring]);
  for (const hole of inners) {
    const host = polygons.filter(polygon => pointInRing(hole[0], polygon[0])).sort((a, b) => Math.abs(signedArea(a[0])) - Math.abs(signedArea(b[0])))[0];
    if (host) host.push(hole);
    else warnings.push(`Unassigned polygon hole omitted: ${idOf(element)}.`);
  }
  return polygons;
}

function polygonCentroid(rings) {
  // Translate before summing to avoid cancellation at longitude -49/latitude -16.
  const [ox, oy] = rings[0][0];
  let totalWeight = 0, x = 0, y = 0;
  for (let r = 0; r < rings.length; r++) {
    const ring = rings[r];
    let crossSum = 0, cx = 0, cy = 0;
    for (let i = 0; i < ring.length - 1; i++) {
      const a = [ring[i][0] - ox, ring[i][1] - oy];
      const b = [ring[i + 1][0] - ox, ring[i + 1][1] - oy];
      const cross = a[0] * b[1] - b[0] * a[1];
      crossSum += cross;
      cx += (a[0] + b[0]) * cross;
      cy += (a[1] + b[1]) * cross;
    }
    if (!crossSum) continue;
    const weight = Math.abs(crossSum) * (r === 0 ? 1 : -1);
    x += cx / (3 * crossSum) * weight;
    y += cy / (3 * crossSum) * weight;
    totalWeight += weight;
  }
  return totalWeight > 0 ? [x / totalWeight + ox, y / totalWeight + oy] : null;
}

/** Centroid when valid; otherwise widest interior horizontal interval, including holes. */
export function representativePoint(polygons, bbox = null) {
  const ordered = [...polygons].sort((a, b) => Math.abs(signedArea(b[0])) - Math.abs(signedArea(a[0])));
  for (const rings of ordered) {
    const center = polygonCentroid(rings);
    if (center && pointInPolygon(center, rings) && inBounds(center, bbox)) return center;
    const levels = [...new Set(rings.flat().map(p => p[1]).filter(y => !bbox || y >= bbox.south && y <= bbox.north))];
    if (bbox) levels.push(bbox.south, bbox.north);
    levels.sort((a, b) => a - b);
    let best = null, bestWidth = 0;
    for (let i = 1; i < levels.length; i++) {
      const y = (levels[i - 1] + levels[i]) / 2;
      const crossings = [];
      for (const ring of rings) for (let j = 0; j < ring.length - 1; j++) {
        const a = ring[j], b = ring[j + 1];
        if ((a[1] > y) !== (b[1] > y)) crossings.push(a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
      }
      crossings.sort((a, b) => a - b);
      for (let j = 1; j < crossings.length; j += 2) {
        const left = Math.max(crossings[j - 1], bbox?.west ?? -Infinity);
        const right = Math.min(crossings[j], bbox?.east ?? Infinity);
        const point = [(left + right) / 2, y];
        if (right - left > bestWidth && inBounds(point, bbox) && pointInPolygon(point, rings)) {
          best = point;
          bestWidth = right - left;
        }
      }
    }
    if (best) return best;
  }
  return null;
}

const normalizedName = name => name.normalize('NFKD').replace(/\p{M}/gu, '').toLocaleLowerCase('pt-BR').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
const distanceMeters = (a, b) => Math.hypot((a[0] - b[0]) * Math.cos((a[1] + b[1]) * Math.PI / 360), a[1] - b[1]) * 111_195;
const featureInPolygon = (candidate, polygonCandidate) => polygonCandidate.polygons.some(rings => pointInPolygon(candidate.feature.geometry.coordinates, rings));

export function buildPois(data, bbox = bounds) {
  const warnings = [], candidates = [], omitted = [];
  for (const element of data.elements) {
    const tags = element.tags ?? {}, kind = classifyPoi(tags);
    if (!kind) continue;
    const polygons = polygonParts(element, warnings);
    const point = element.type === 'node' && Number.isFinite(element.lon) && Number.isFinite(element.lat) ? [element.lon, element.lat] : representativePoint(polygons, bbox);
    if (!point || !inBounds(point, bbox)) {
      omitted.push({ osm_id: idOf(element), reason: point ? 'outside bbox' : 'no complete polygon or node geometry' });
      continue;
    }
    const rounded = point.map(n => Number(n.toFixed(7)));
    const outputPoint = inBounds(rounded, bbox) && (!polygons.length || polygons.some(rings => pointInPolygon(rounded, rings))) ? rounded : point;
    const osmId = idOf(element), sourceUrl = `https://www.openstreetmap.org/${osmId}`;
    const address = [tags['addr:street'], tags['addr:housenumber'], tags['addr:suburb'], tags['addr:city'], tags['addr:postcode']].filter(Boolean).join(', ');
    const website = tags.website ?? tags['contact:website'];
    const properties = {
      osm_id: osmId, source_id: osmId, source_ids: [osmId],
      source_url: sourceUrl, source_urls: [sourceUrl], kind, name: tags.name ?? tags['name:pt'] ?? '',
      coordinate_source: element.type === 'node' ? 'OSM node' : 'Interior point from OSM polygon',
      ...(address ? { address } : {}),
      ...(website && /^https?:\/\//i.test(website) ? { website } : {}),
      ...(tags.brand ? { brand: tags.brand } : {}),
      ...(tags.access ? { access: tags.access } : {}),
    };
    candidates.push({ element, polygons, feature: { type: 'Feature', id: osmId, properties, geometry: { type: 'Point', coordinates: outputPoint } } });
  }
  // Prefer the exact node coordinate. Only merge the same named/category node + area,
  // or a relation's identically named tagged member; nearby chain branches remain separate.
  candidates.sort((a, b) => (a.element.type === 'node' ? 0 : a.element.type === 'relation' ? 1 : 2) - (b.element.type === 'node' ? 0 : b.element.type === 'relation' ? 1 : 2) || idOf(a.element).localeCompare(idOf(b.element), 'en'));
  const kept = [], byName = new Map(), duplicates = [];
  for (const candidate of candidates) {
    const props = candidate.feature.properties;
    const nameKey = normalizedName(props.name);
    const key = `${props.kind}:${nameKey}`;
    const duplicate = nameKey && (byName.get(key) ?? []).find(other => {
      const nodeAndArea = (other.element.type === 'node') !== (candidate.element.type === 'node') && (other.polygons.length || candidate.polygons.length);
      if (nodeAndArea) return featureInPolygon(other.element.type === 'node' ? other : candidate, other.element.type === 'node' ? candidate : other) || distanceMeters(other.feature.geometry.coordinates, candidate.feature.geometry.coordinates) <= 12;
      const relation = other.element.type === 'relation' ? other : candidate.element.type === 'relation' ? candidate : null;
      const way = other.element.type === 'way' ? other : candidate.element.type === 'way' ? candidate : null;
      return relation && way && (relation.element.members ?? []).some(member => member.type === 'way' && member.ref === way.element.id);
    });
    if (duplicate) {
      duplicate.feature.properties.source_ids.push(props.osm_id);
      duplicate.feature.properties.source_urls.push(props.source_url);
      // Keep useful details on either representation without changing its OSM name.
      for (const field of ['address', 'website', 'brand', 'access']) if (!duplicate.feature.properties[field] && props[field]) duplicate.feature.properties[field] = props[field];
      duplicates.push({ retained: duplicate.feature.properties.osm_id, merged: props.osm_id, name: props.name, kind: props.kind });
    } else {
      kept.push(candidate);
      byName.set(key, [...(byName.get(key) ?? []), candidate]);
    }
  }
  const features = kept.map(candidate => candidate.feature).sort((a, b) => poiKinds.indexOf(a.properties.kind) - poiKinds.indexOf(b.properties.kind) || a.properties.name.localeCompare(b.properties.name, 'pt-BR') || a.properties.osm_id.localeCompare(b.properties.osm_id, 'en'));
  return { collection: { type: 'FeatureCollection', features }, warnings: [...new Set(warnings)], omitted, duplicates };
}

export async function writePois() {
  const inputDir = path.join(root, 'data', 'osm'), outputDir = path.join(root, 'public', 'data');
  const data = JSON.parse(await fs.readFile(path.join(inputDir, 'pois.json'), 'utf8'));
  const provenance = JSON.parse(await fs.readFile(path.join(inputDir, 'pois.provenance.json'), 'utf8'));
  const query = await fs.readFile(path.join(inputDir, 'pois.overpassql'), 'utf8');
  const result = buildPois(data);
  const counts = Object.fromEntries(poiKinds.map(kind => [kind, result.collection.features.filter(feature => feature.properties.kind === kind).length]));
  const namedCounts = Object.fromEntries(poiKinds.map(kind => [kind, result.collection.features.filter(feature => feature.properties.kind === kind && feature.properties.name).length]));
  const metadata = {
    title: 'Goiânia — OpenStreetMap points of interest', ...provenance,
    bounds, crs: 'EPSG:4326', query, generatedAt: new Date().toISOString(),
    geojsonFile: 'public/data/pois.geojson',
    counts: { total: result.collection.features.length, byKind: counts, namedByKind: namedCounts },
    sourceIds: result.collection.features.flatMap(feature => feature.properties.source_ids).sort(),
    examplesByKind: Object.fromEntries(poiKinds.map(kind => [kind, result.collection.features.filter(feature => feature.properties.kind === kind && feature.properties.name).slice(0, 8).map(feature => ({ name: feature.properties.name, osm_id: feature.properties.osm_id, osmUrl: feature.properties.source_url }))])),
    processing: {
      classification: 'amenity categories preserved; doctors/clinic → clinic; leisure park/garden → park; arts_centre → culture; tourism museum → museum.',
      points: 'Original node coordinates, or a deterministic interior point from complete OSM polygon rings. Centroid accepted only inside the polygon, outside holes and inside bbox; fallback chooses an interior horizontal interval. Incomplete geometry is omitted and reported.',
      bounds: 'Output Point coordinates must fall inside bbox. Crossing polygons receive an interior point inside bbox when possible.',
      deduplication: 'Same normalized nonempty name + kind, node/area containment or at most 12 m; additionally a relation and its identically named tagged member way. Exact node position takes precedence; all original IDs and links are retained. Separate same-brand nodes and unnamed features are not merged.',
      freshness: 'OSM snapshot only. Presence, names and categories reflect contributor tags; business operation and opening hours have not been checked.',
    },
    duplicates: result.duplicates, omitted: result.omitted, warnings: result.warnings,
  };
  await fs.mkdir(outputDir, { recursive: true });
  await fs.writeFile(path.join(outputDir, 'pois.geojson'), JSON.stringify(result.collection) + '\n');
  await fs.writeFile(path.join(outputDir, 'pois-metadata.json'), JSON.stringify(metadata, null, 2) + '\n');
  console.log(JSON.stringify({ total: metadata.counts.total, byKind: counts, merged: result.duplicates.length, omitted: result.omitted.length, warnings: result.warnings.length }, null, 2));
  return metadata;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await writePois();
