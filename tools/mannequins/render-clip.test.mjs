// A baked clip rendered at the top, the middle and the bottom of its rep, for the movement reviewer.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { inflateSync } from 'node:zlib';
import { CLIPS } from './bake.mjs';
import { renderClip } from './render.mjs';

/** A PNG's size and its rows of RGB pixels, each row's filter byte taken off. */
function read(png) {
  const [w, h] = [png.readUInt32BE(16), png.readUInt32BE(20)];
  const raw = inflateSync(png.subarray(41, png.length - 16));
  return { w, h, rows: Array.from({ length: h }, (_, y) => raw.subarray(y * (w * 3 + 1) + 1, (y + 1) * (w * 3 + 1))) };
}

test('a clip renders as three panels, and the rep moves across them', async () => {
  const { w, h, rows } = read(await renderClip(path.join(CLIPS, 'db_bench_press.glb'), 'side'));
  const panel = (w - 12) / 3;
  assert.equal(panel, 560);
  assert.equal(h, 700);
  const third = (i) => Buffer.concat(rows.map((r) => r.subarray(i * (panel + 6) * 3, (i * (panel + 6) + panel) * 3)));
  assert.ok(!third(0).equals(third(1)), 'the top and the middle are the same picture');
  assert.ok(!third(1).equals(third(2)), 'the middle and the bottom are the same picture');
});

test('the command refuses an option it doesn\'t know', async () => {
  const cwd = path.dirname(fileURLToPath(import.meta.url));
  await assert.rejects(promisify(execFile)(process.execPath, ['render.mjs', '--clip', 'db_bench_press', '--at acbebfb'], { cwd }), /Unknown option/);
});
