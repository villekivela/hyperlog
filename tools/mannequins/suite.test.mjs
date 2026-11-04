// The full suite runs a test file per process, and left alone node runs one fewer than the machine
// has cores: enough at once to run a shared machine out of memory (#191). CI takes the same cap.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOL = path.dirname(fileURLToPath(import.meta.url));
const capOf = (text) => /--test-concurrency=(\d+)/.exec(text)?.[1];

test('the suite runs a few test files at a time, here and in CI', async () => {
  const { scripts } = JSON.parse(await readFile(path.join(TOOL, 'package.json'), 'utf8'));
  const ci = await readFile(path.join(TOOL, '..', '..', '.github', 'workflows', 'mannequins.yml'), 'utf8');
  const cap = Number(capOf(scripts.test));
  assert.ok(cap >= 1 && cap <= 4, `npm test runs ${capOf(scripts.test) ?? 'as many as the machine has cores'} at a time`);
  assert.equal(capOf(ci), String(cap));
});
