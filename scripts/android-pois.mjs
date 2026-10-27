#!/usr/bin/env node
/** Prepare an independent, attributable nine-category POI package. Node >= 20. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { bounds } from './fetch-osm.mjs';
import { buildPois, classifyPoi } from './build-pois.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cacheDir = path.join(root, 'data', 'android');
const outputDir = path.join(root, 'android', 'maps');
const hash = text => createHash('sha256').update(text).digest('hex');
const idOf = element => element.type + '/' + element.id;
export const androidKinds = ['parks', 'fuel', 'food', 'parking', 'health', 'shops', 'education', 'culture', 'religion'];
export const categoryMappings = {
  parks: { leisure: ['park', 'garden'] },
  fuel: { amenity: ['fuel'] },
  food: { amenity: ['restaurant', 'cafe', 'fast_food', 'bar', 'pub'] },
  parking: { amenity: ['parking'] },
  health: { amenity: ['hospital', 'clinic', 'doctors', 'pharmacy'] },
  shops: { shop: ['supermarket', 'convenience'] },
  education: { amenity: ['school', 'university', 'college', 'kindergarten'] },
  culture: { amenity: ['theatre', 'cinema', 'arts_centre'], tourism: ['museum'] },
  religion: { amenity: ['place_of_worship'] },
};
// Prefer the site's explicit amenity over a secondary shop/leisure tag.
export const categoryPriority = ['fuel', 'parking', 'health', 'education', 'culture', 'religion', 'food', 'shops', 'parks'];
const matchingCategories = tags => categoryPriority.filter(kind =>
  Object.entries(categoryMappings[kind]).some(([key, values]) => values.includes(tags[key])),
);
export const classifyAndroidPoi = tags => matchingCategories(tags ?? {})[0] ?? null;
const queryBbox = [bounds.south, bounds.west, bounds.north, bounds.east].join(',');
export const androidPoiQuery = [
  '[out:json][timeout:180][maxsize:134217728];',
  '(',
  '  nwr(' + queryBbox + ')["amenity"~"^(fuel|parking|restaurant|cafe|fast_food|bar|pub|hospital|clinic|doctors|pharmacy|school|university|college|kindergarten|theatre|cinema|arts_centre|place_of_worship)$"];',
  '  nwr(' + queryBbox + ')["leisure"~"^(park|garden)$"];',
  '  nwr(' + queryBbox + ')["shop"~"^(supermarket|convenience)$"];',
  '  nwr(' + queryBbox + ')["tourism"="museum"];',
  ');',
  'out meta geom;',
].join('\n');

export function buildAndroidPois(data, bbox = bounds) {
  if (!Array.isArray(data.elements) || data.remark) throw new Error('Expected a complete Overpass elements array');
  const originals = new Map(), adapted = [], categoryConflicts = [];
  for (const element of data.elements) {
    const osmId = idOf(element), tags = element.tags ?? {}, categories = matchingCategories(tags);
    if (originals.has(osmId)) throw new Error('Repeated OSM source element: ' + osmId);
    originals.set(osmId, element);
    if (!categories.length) continue;
    const kind = categories[0], proxyTags = { ...tags };
    if (categories.length > 1) categoryConflicts.push({ osm_id: osmId, categories, selected: kind });
    // Reuse the existing proven geometry/dedup pipeline without changing the web
    // classifier. The bank proxy exists only in memory; original shop tags below
    // are restored before export. Real banks are never selected by this adapter.
    if (kind === 'shops') proxyTags.amenity = 'bank';
    if (kind === 'parks') delete proxyTags.amenity;
    adapted.push({ ...element, tags: proxyTags });
  }
  // The shared geometry/dedup pipeline retains exact subtypes. Its web defaults
  // stay unchanged; only this native package selects the additional OSM tags.
  const extraSubtypes = new Set(['college', 'kindergarten', 'bar', 'pub', 'place_of_worship']);
  const result = buildPois({ elements: adapted }, bbox, {
    classify: tags => classifyPoi(tags) ?? (extraSubtypes.has(tags.amenity) ? tags.amenity : null),
  });
  for (const feature of result.collection.features) {
    const original = originals.get(feature.properties.osm_id);
    const rawTags = { ...(original.tags ?? {}) };
    // Spread raw tags first so reserved output names cannot corrupt our schema;
    // raw_tags retains even conflicting source property names verbatim.
    feature.properties = {
      ...rawTags, ...feature.properties,
      kind: classifyAndroidPoi(rawTags),
      raw_tags: rawTags,
      source_tags: feature.properties.source_ids.map(osmId => ({
        osm_id: osmId, tags: { ...(originals.get(osmId).tags ?? {}) },
      })),
    };
  }
  result.collection.features.sort((a, b) =>
    androidKinds.indexOf(a.properties.kind) - androidKinds.indexOf(b.properties.kind)
    || a.properties.name.localeCompare(b.properties.name, 'pt-BR')
    || a.properties.osm_id.localeCompare(b.properties.osm_id, 'en'),
  );
  result.duplicates = result.duplicates.map(duplicate => ({
    ...duplicate, kind: classifyAndroidPoi(originals.get(duplicate.retained).tags),
  }));
  return { ...result, categoryConflicts };
}

export async function fetchAndroidPois() {
  const endpoints = process.env.OSM_OVERPASS_ENDPOINT ? [process.env.OSM_OVERPASS_ENDPOINT] : [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass.private.coffee/api/interpreter',
  ];
  let lastError;
  for (const endpoint of endpoints) {
    try {
      console.log('Fetching dedicated Android POIs from ' + endpoint);
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'GoianiaGameMap/1.0 (local educational POI map; OSM attribution included)',
        },
        body: new URLSearchParams({ data: androidPoiQuery }),
        signal: AbortSignal.timeout(90_000),
      });
      if (!response.ok) throw new Error('HTTP ' + response.status + ': ' + (await response.text()).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').slice(0, 300));
      const raw = await response.text(), data = JSON.parse(raw);
      if (data.remark) throw new Error('Partial Overpass response: ' + data.remark);
      if (!Array.isArray(data.elements) || !data.elements.length) throw new Error('Empty Overpass response');
      buildAndroidPois(data); // Reject invalid duplicate/partial source structure before replacing the cache.
      const provenance = {
        source: 'OpenStreetMap via Overpass API', endpoint,
        fetchedAt: new Date().toISOString(),
        dataTimestamp: data.osm3s?.timestamp_osm_base ?? null,
        queryFile: 'data/android/pois.overpassql',
        rawFile: 'data/android/pois.json',
        rawSha256: hash(raw), querySha256: hash(androidPoiQuery),
        elements: data.elements.length,
        bbox: [bounds.west, bounds.south, bounds.east, bounds.north],
        attribution: '© OpenStreetMap contributors',
        attributionUrl: 'https://www.openstreetmap.org/copyright',
        license: 'ODbL-1.0',
        licenseUrl: 'https://opendatacommons.org/licenses/odbl/1-0/',
        copyright: data.osm3s?.copyright ?? null,
      };
      await fs.mkdir(cacheDir, { recursive: true });
      await fs.writeFile(path.join(cacheDir, 'pois.json'), raw);
      await fs.writeFile(path.join(cacheDir, 'pois.overpassql'), androidPoiQuery + '\n');
      await fs.writeFile(path.join(cacheDir, 'pois.provenance.json'), JSON.stringify(provenance, null, 2) + '\n');
      console.log('Saved ' + data.elements.length + ' OSM elements; database timestamp ' + provenance.dataTimestamp);
      return provenance;
    } catch (error) {
      lastError = error;
      console.error(endpoint + ': ' + error.message);
    }
  }
  throw lastError ?? new Error('No Overpass endpoints configured');
}

export async function writeAndroidPois() {
  const raw = await fs.readFile(path.join(cacheDir, 'pois.json'), 'utf8');
  const data = JSON.parse(raw);
  const provenance = JSON.parse(await fs.readFile(path.join(cacheDir, 'pois.provenance.json'), 'utf8'));
  const query = (await fs.readFile(path.join(cacheDir, 'pois.overpassql'), 'utf8')).trimEnd();
  if (provenance.rawSha256 !== hash(raw) || provenance.querySha256 !== hash(query)
    || provenance.dataTimestamp !== (data.osm3s?.timestamp_osm_base ?? null)) {
    throw new Error('Android source cache and provenance do not match; fetch the complete package again');
  }
  const result = buildAndroidPois(data);
  const features = result.collection.features;
  const counts = Object.fromEntries(androidKinds.map(kind => [kind, features.filter(f => f.properties.kind === kind).length]));
  const missingTagSelectors = Object.fromEntries(androidKinds.map(kind => [kind,
    Object.entries(categoryMappings[kind]).flatMap(([key, values]) => values.filter(value => !query.includes(value)).map(value => `${key}=${value}`)),
  ]));
  const incompleteCategories = androidKinds.filter(kind => missingTagSelectors[kind].length > 0);
  const notCollectedCategories = incompleteCategories.filter(kind => counts[kind] === 0);
  if (incompleteCategories.length) result.warnings.push(
    'Cached snapshot is preserved from its original query/date. It did not request all subtypes in the current nine-group specification; missing selectors are recorded in dataCollection. No unrelated snapshot was merged.');
  const metadata = {
    title: 'Goiânia — nine semantic POI categories for NavSkin',
    ...provenance, bounds, crs: 'EPSG:4326', query,
    generatedAt: new Date().toISOString(),
    geojsonFile: 'android/maps/pois.geojson',
    activation: 'Prepared data package only. Integrate with the full phone/car UI only after validating the minimal Android Auto Surface map.',
    counts: {
      total: features.length, byKind: counts,
      namedByKind: Object.fromEntries(androidKinds.map(kind => [kind, features.filter(f => f.properties.kind === kind && f.properties.name).length])),
    },
    categoryMappings, categoryPriority,
    dataCollection: {
      completeForCurrentSpecification: incompleteCategories.length === 0,
      incompleteCategories, notCollectedCategories, missingTagSelectors,
      currentRequestedQuery: androidPoiQuery,
      note: 'Source query, fetchedAt, dataTimestamp and hashes above describe the actual single cached snapshot. A failed refresh never changes those values. Empty uncollected offline groups do not imply the places are absent from the worldwide map.',
    },
    sourceIds: features.flatMap(f => f.properties.source_ids).sort(),
    examplesByKind: Object.fromEntries(androidKinds.map(kind => [
      kind, features.filter(f => f.properties.kind === kind && f.properties.name).slice(0, 8)
        .map(f => ({ name: f.properties.name, osm_id: f.properties.osm_id, source_url: f.properties.source_url })),
    ])),
    processing: {
      classification: 'One semantic kind per original element. Explicit amenity takes precedence over shop, then leisure; exact priority is recorded above.',
      geometry: 'Reuses scripts/build-pois.mjs: original node coordinates or a deterministic polygon interior point, outside holes and inside bbox. Incomplete geometry is omitted and reported.',
      deduplication: 'Reuses original OSM subtype rules: same normalized nonempty name and normalized subtype, node/area containment or <= 12 m, or relation/member. Nearby separate nodes remain separate. Hospital and pharmacy remain distinct from clinic/doctors; the original builder normalizes doctors to clinic. Restaurant, cafe and fast_food remain distinct. Supermarket/convenience use the common shops subtype.',
      provenance: 'Original source tags are copied to raw_tags and source_tags for every retained/merged ID; raw tags are also flattened except where reserved output fields override them. Shops are adapted using an in-memory amenity=bank proxy solely for the existing pure builder; that proxy is never exported.',
      freshness: 'A download date is not a database timestamp or a verification of operation, opening hours, access, or current availability. OSM tags remain unverified contributor data.',
      location: 'Coordinates describe OSM places. No device location, GPS, route, distance from user, or navigation data is obtained.',
    },
    duplicates: result.duplicates,
    categoryConflicts: result.categoryConflicts,
    omitted: result.omitted, warnings: result.warnings,
  };
  await fs.mkdir(outputDir, { recursive: true });
  await fs.writeFile(path.join(outputDir, 'pois.geojson'), JSON.stringify(result.collection) + '\n');
  await fs.writeFile(path.join(outputDir, 'pois-metadata.json'), JSON.stringify(metadata, null, 2) + '\n');
  console.log(JSON.stringify({ total: features.length, byKind: counts, merged: result.duplicates.length, categoryConflicts: result.categoryConflicts.length, omitted: result.omitted.length, warnings: result.warnings.length }, null, 2));
  return metadata;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  if (process.argv.includes('--fetch')) await fetchAndroidPois();
  await writeAndroidPois();
}
