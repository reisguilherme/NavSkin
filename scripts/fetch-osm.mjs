#!/usr/bin/env node
/** Download a small, attributable OpenStreetMap extract. Node >= 20; no packages. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cacheDir = path.join(root, 'data', 'osm');
const endpoints = process.env.OSM_OVERPASS_ENDPOINT ? [process.env.OSM_OVERPASS_ENDPOINT] : [
  'https://overpass-api.de/api/interpreter',
  'https://overpass.kumi.systems/api/interpreter',
  'https://overpass.private.coffee/api/interpreter',
];

export const bounds = { west: -49.325, south: -16.755, east: -49.205, north: -16.64 };
const bbox = [bounds.south, bounds.west, bounds.north, bounds.east].join(',');
export const discoveryQuery = `[out:json][timeout:120];
(
  way(id:159590062,220688361,670650221);
  node(3561431327);
  node(-16.82,-49.40,-16.58,-49.17)["place"]["name"~"Central|^Centro$",i];
);
out meta geom;`;

export const extractQuery = `[out:json][timeout:180][maxsize:268435456];
(
  way(${bbox})["highway"]["highway"!~"^(construction|proposed|abandoned)$"];
  way(${bbox})["railway"~"^(rail|light_rail|tram|subway|narrow_gauge|preserved)$"];
  nwr(${bbox})["waterway"];
  nwr(${bbox})["natural"~"^(water|wood|scrub|grassland|wetland|heath)$"];
  nwr(${bbox})["water"];
  nwr(${bbox})["building"]["building"!="no"];
  nwr(${bbox})["landuse"~"^(residential|commercial|retail|industrial|forest|grass|meadow|recreation_ground|cemetery|farmland|farmyard|brownfield|greenfield|allotments|village_green|basin|reservoir|orchard|plant_nursery)$"];
  nwr(${bbox})["leisure"~"^(park|garden|nature_reserve|golf_course|pitch|playground|sports_centre|recreation_ground)$"];
  nwr(${bbox})["place"~"^(city|town|suburb|quarter|neighbourhood|square)$"];
  nwr(${bbox})["name"~"Praça C.vica|Praça Doutor Pedro|Praça Dr.*Pedro|Parque.*Vaca|Parque.*Flamboyant|^Setor Bueno$|^Centro$",i];
);
out meta geom;`;

export async function download(mode) {
  await fs.mkdir(cacheDir, { recursive: true });
  const query = mode === 'places' ? discoveryQuery : extractQuery;
  await fs.writeFile(path.join(cacheDir, `${mode}.overpassql`), query + '\n');
  let lastError;
  for (const endpoint of endpoints) {
    try {
      console.log(`Fetching ${mode} from ${endpoint}`);
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'GoianiaGameMap/1.0 (local educational map; OpenStreetMap attribution included)',
        },
        body: new URLSearchParams({ data: query }),
        signal: AbortSignal.timeout(240_000),
      });
      if (!response.ok) {
        const responseText = await response.text();
        const readable = responseText.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
        throw new Error(`HTTP ${response.status}: ${readable.slice(0, 1200)}`);
      }
      const raw = await response.text();
      const data = JSON.parse(raw);
      if (data.remark) throw new Error(`Overpass returned a partial result: ${data.remark}`);
      if (!Array.isArray(data.elements) || !data.elements.length) throw new Error('Empty Overpass response');
      await fs.writeFile(path.join(cacheDir, `${mode}.json`), raw);
      await fs.writeFile(path.join(cacheDir, `${mode}.provenance.json`), JSON.stringify({
        endpoint,
        fetchedAt: new Date().toISOString(),
        dataTimestamp: data.osm3s?.timestamp_osm_base ?? null,
        queryFile: `data/osm/${mode}.overpassql`,
        elements: data.elements.length,
        copyright: data.osm3s?.copyright,
      }, null, 2) + '\n');
      console.log(`Saved ${data.elements.length} elements (${raw.length} characters).`);
      return;
    } catch (error) {
      lastError = error;
      console.error(`${endpoint}: ${error.message}`);
    }
  }
  throw lastError;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const mode = process.argv[2] ?? 'extract';
  if (!['places', 'extract'].includes(mode)) throw new Error('Usage: node scripts/fetch-osm.mjs [places|extract]');
  await download(mode);
}
