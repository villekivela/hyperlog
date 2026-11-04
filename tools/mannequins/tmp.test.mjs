// The tests' scratch folders: every one is removed when its test file's process ends, whether the
// tests passed or not, and no test file makes one any other way.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { readdir, readFile, stat } from 'node:fs/promises';
import { promisify } from 'node:util';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const run = promisify(execFile);

const exists = (p) => stat(p).then(() => true, () => false);

test('a scratch folder is gone after a process that crashed', async () => {
  const script = `import { scratch } from ${JSON.stringify(path.join(here, 'tmp.mjs'))};
    console.log(await scratch('crash'));
    throw new Error('a failing test');`;
  const made = await run(process.execPath, ['--input-type=module', '-e', script]).catch((e) => e);
  const dir = made.stdout.trim();
  assert.match(path.basename(dir), /^mannequins-crash-/);
  assert.equal(await exists(dir), false, `${dir} survived the process`);
});

test('no test file makes a temp folder except through scratch()', async () => {
  const files = [];
  for (const d of [here, path.join(here, 'studio')]) {
    for (const f of await readdir(d)) if (f.endsWith('.test.mjs')) files.push(path.join(d, f));
  }
  const offenders = [];
  for (const f of files) {
    if (/\bmkdtemp\b/.test(await readFile(f, 'utf8'))) offenders.push(path.relative(here, f));
  }
  assert.deepEqual(offenders, [], 'use scratch() from tmp.mjs, which removes the folder');
});
