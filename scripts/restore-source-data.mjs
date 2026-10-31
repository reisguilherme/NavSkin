/** Restore the optional full-resolution geography inputs; app builds use prepared tiles. */
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { gunzipSync } from 'node:zlib';

const root = path.resolve(import.meta.dirname, '..');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
function within(directory, relative) {
  const destination = path.resolve(directory, relative);
  if (!destination.startsWith(path.resolve(directory) + path.sep)) throw new Error(`Path outside restore directory: ${relative}`);
  return destination;
}

export async function restoreSourceData({
  manifestPath = path.join(root, 'data/overture/archive/manifest.json'),
  outputRoot = root,
  verifyOnly = false,
} = {}) {
  const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  if (manifest.schemaVersion !== 1) throw new Error('Unsupported source archive manifest');
  const results = [];
  for (const entry of manifest.files) {
    const compressed = await fs.readFile(within(path.dirname(manifestPath), entry.archive));
    if (compressed.length !== entry.compressedBytes || hash(compressed) !== entry.compressedSha256) {
      throw new Error(`Source archive checksum mismatch: ${entry.archive}`);
    }
    const original = gunzipSync(compressed);
    if (original.length !== entry.bytes || hash(original) !== entry.sha256) throw new Error(`Restored content checksum mismatch: ${entry.originalPath}`);
    let status = 'verified';
    if (!verifyOnly) {
      const destination = within(outputRoot, entry.originalPath);
      const existing = await fs.readFile(destination).catch(error => { if (error.code === 'ENOENT') return null; throw error; });
      if (existing) {
        if (hash(existing) !== entry.sha256) throw new Error(`Refusing to replace different content: ${entry.originalPath}`);
        status = 'unchanged';
      } else {
        await fs.mkdir(path.dirname(destination), { recursive: true });
        await fs.writeFile(destination, original, { flag: 'wx' });
        status = 'restored';
      }
    }
    results.push({ path: entry.originalPath, status, bytes: entry.bytes, sha256: entry.sha256 });
  }
  return results;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  if (args.some(arg => arg !== '--verify')) throw new Error('Usage: node scripts/restore-source-data.mjs [--verify]');
  console.log(JSON.stringify(await restoreSourceData({ verifyOnly: args.includes('--verify') }), null, 2));
}
