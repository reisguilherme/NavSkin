#!/usr/bin/env node
/** Download attributable OSM points of interest. Node >= 20, no packages. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { bounds } from './fetch-osm.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cacheDir = path.join(root, 'data', 'osm');
const bbox = [bounds.south, bounds.west, bounds.north, bounds.east].join(',');
export const poiQuery = `[out:json][timeout:180][maxsize:134217728];
(
  nwr(${bbox})["amenity"~"^(pharmacy|fuel|hospital|clinic|doctors|theatre|cinema|arts_centre|school|university|police|fire_station|bank|atm|parking|restaurant|cafe|fast_food)$"];
  nwr(${bbox})["leisure"~"^(park|garden)$"];
  nwr(${bbox})["tourism"="museum"];
);
out meta geom;`;

export async function downloadPois() {
  const endpoints = process.env.OSM_OVERPASS_ENDPOINT ? [process.env.OSM_OVERPASS_ENDPOINT] : [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass.private.coffee/api/interpreter',
  ];
  await fs.mkdir(cacheDir, { recursive: true });
  await fs.writeFile(path.join(cacheDir, 'pois.overpassql'), poiQuery + '\n');
  let lastError;
  for (const endpoint of endpoints) {
    try {
      console.log(`Fetching POIs from ${endpoint}`);
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'GoianiaGameMap/1.0 (local educational map; OpenStreetMap attribution included)',
        },
        body: new URLSearchParams({ data: poiQuery }),
        signal: AbortSignal.timeout(90_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}: ${(await response.text()).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').slice(0, 600)}`);
      const raw = await response.text();
      const data = JSON.parse(raw);
      if (data.remark) throw new Error(`Partial Overpass response: ${data.remark}`);
      if (!Array.isArray(data.elements) || !data.elements.length) throw new Error('Empty POI response');
      await fs.writeFile(path.join(cacheDir, 'pois.json'), raw);
      const provenance = {
        source: 'OpenStreetMap via Overpass API', endpoint,
        fetchedAt: new Date().toISOString(),
        dataTimestamp: data.osm3s?.timestamp_osm_base ?? null,
        queryFile: 'data/osm/pois.overpassql',
        elements: data.elements.length,
        bbox: [bounds.west, bounds.south, bounds.east, bounds.north],
        attribution: '© OpenStreetMap contributors',
        attributionUrl: 'https://www.openstreetmap.org/copyright',
        license: 'ODbL-1.0',
        licenseUrl: 'https://opendatacommons.org/licenses/odbl/1-0/',
        copyright: data.osm3s?.copyright ?? null,
      };
      await fs.writeFile(path.join(cacheDir, 'pois.provenance.json'), JSON.stringify(provenance, null, 2) + '\n');
      console.log(`Saved ${data.elements.length} OSM POI elements (${raw.length} characters).`);
      return provenance;
    } catch (error) {
      lastError = error;
      console.error(`${endpoint}: ${error.message}`);
    }
  }
  throw lastError;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) await downloadPois();
