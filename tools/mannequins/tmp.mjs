// Scratch folders for the tests. Each is removed when the process ends, also when a test failed,
// so a suite run leaves nothing in the temp directory.
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

const made = new Set();

process.on('exit', () => {
  for (const dir of made) rmSync(dir, { recursive: true, force: true });
});

export async function scratch(name) {
  const dir = mkdtempSync(path.join(tmpdir(), `mannequins-${name}-`));
  made.add(dir);
  return dir;
}
