// `trace` turns a side-on clip's landmarks into a pose's angle keyframes: one clean rep's
// lowering, smoothed, as angles only.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readdir, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { traceRep, trace, parseRange } from './trace.mjs';

const TOP = { shin: 6, thigh: 4, trunk: 4, upperArm: 30, forearm: 150 };
const BOTTOM = { shin: 47, thigh: 92, trunk: 23, upperArm: 60, forearm: 170 };
const FPS = 30;

/**
 * Landmarks of a lifter squatting side-on, three reps in 9 s: half a second still at the top, 1.2 s
 * down (eased), 0.3 s at the bottom and a second up. The lifter faces the image's left, the near
 * (left) side is the more visible, there's a pixel of noise, and two frames find no one. The
 * second rep can stop halfway down.
 */
function squatClip({ shallowSecond = false } = {}) {
  let seed = 7;
  const noise = () => { seed = (seed * 16807) % 2147483647; return (seed / 2147483647 - 0.5) * 2; };
  const depthAt = (t) => {
    const p = t % 3;
    if (p < 0.5) return 0;
    if (p < 1.7) return (1 - Math.cos(Math.PI * (p - 0.5) / 1.2)) / 2;
    if (p < 2.0) return 1;
    return (1 + Math.cos(Math.PI * (p - 2.0))) / 2;
  };
  const frames = [];
  for (let i = 0; i < 9 * FPS; i++) {
    const t = i / FPS;
    if (i === 100 || i === 101) { frames.push({ t, image: null, visibility: null }); continue; }
    const d = depthAt(t) * (shallowSecond && t >= 3 && t < 6 ? 0.5 : 1);
    const a = Object.fromEntries(Object.keys(TOP).map((k) => [k, ((TOP[k] + (BOTTOM[k] - TOP[k]) * d) * Math.PI) / 180]));
    // The lifter's own frame, x forward and y up, in pixels.
    const ankle = [0, 0];
    const add = (p, len, dx, dy) => [p[0] + len * dx, p[1] + len * dy];
    const knee = add(ankle, 170, Math.sin(a.shin), Math.cos(a.shin));
    const hip = add(knee, 160, -Math.sin(a.thigh), Math.cos(a.thigh));
    const shoulder = add(hip, 200, Math.sin(a.trunk), Math.cos(a.trunk));
    const elbow = add(shoulder, 110, Math.sin(a.upperArm), -Math.cos(a.upperArm));
    const wrist = add(elbow, 110, Math.sin(a.forearm), -Math.cos(a.forearm));
    const heel = [-30, -10];
    const toe = [90, -15];
    const image = Array.from({ length: 33 }, () => [0, 0]);
    const place = (n, p) => { image[n] = [600 - p[0] + noise(), 900 - p[1] + noise()]; };
    for (const [l, r, p] of [[11, 12, shoulder], [13, 14, elbow], [15, 16, wrist], [23, 24, hip], [25, 26, knee], [27, 28, ankle], [29, 30, heel], [31, 32, toe]]) {
      place(l, p);
      place(r, p);
    }
    const visibility = Array.from({ length: 33 }, (_, n) => (n % 2 ? 0.95 : 0.6));
    frames.push({ t, image, visibility });
  }
  return { fps: FPS, size: [1280, 1024], frames };
}

const ENTRY = { top: TOP, bottom: BOTTOM };

test('one rep\'s lowering becomes 31 keyframes from its top to its bottom', () => {
  const rep = traceRep(squatClip(), ENTRY, ['shin', 'thigh', 'trunk']);
  assert.deepEqual(Object.keys(rep.angles), ['shin', 'thigh', 'trunk']);
  for (const [k, samples] of Object.entries(rep.angles)) {
    assert.equal(samples.length, 31, k);
    assert.ok(Math.abs(samples[0] - TOP[k]) < 3, `${k} at the top: ${samples[0]} against ${TOP[k]}`);
    assert.ok(Math.abs(samples[30] - BOTTOM[k]) < 3, `${k} at the bottom: ${samples[30]} against ${BOTTOM[k]}`);
    // Halfway through the lowering's time, the eased rep is halfway down.
    const half = (TOP[k] + BOTTOM[k]) / 2;
    assert.ok(Math.abs(samples[15] - half) < 4, `${k} halfway: ${samples[15]} against ${half}`);
  }
  // The middle rep of the three: its lowering runs from about 3.5 s to 4.7 s.
  assert.ok(Math.abs(rep.rep[0] - 3.5) < 0.2 && Math.abs(rep.rep[1] - 4.7) < 0.2, `rep ${rep.rep}`);
  assert.equal(rep.side, 'left');
});

test('a rep that turns back before the bottom is not joined to the next one', () => {
  // The second rep stops halfway down and stands back up; the first and third go to the bottom.
  const rep = traceRep(squatClip({ shallowSecond: true }), ENTRY, ['shin']);
  assert.ok(rep.rep[1] - rep.rep[0] < 1.5, `rep ${rep.rep}`);
  assert.ok(Math.abs(rep.angles.shin[30] - BOTTOM.shin) < 3, `${rep.angles.shin[30]}`);
});

test('a landmark MediaPipe barely sees is filled from either side, not followed', () => {
  // Behind the plates mid-rep, the near ankle jumps 80 px for 0.4 s, and MediaPipe is unsure of it.
  const clip = squatClip();
  for (const f of clip.frames) {
    if (f.image && f.t > 3.9 && f.t < 4.3) {
      f.image[27] = [f.image[27][0] + 80, f.image[27][1] - 40];
      f.visibility[27] = 0.6;
    }
  }
  const rep = traceRep(clip, ENTRY, ['shin']);
  const clean = traceRep(squatClip(), ENTRY, ['shin']);
  const worst = Math.max(...rep.angles.shin.map((v, i) => Math.abs(v - clean.angles.shin[i])));
  assert.ok(worst < 2, `the shin strays ${worst.toFixed(1)}° from the clean trace`);
});

test('the arms trace too', () => {
  const rep = traceRep(squatClip(), ENTRY, ['upperArm', 'forearm']);
  assert.ok(Math.abs(rep.angles.forearm[30] - BOTTOM.forearm) < 3, `${rep.angles.forearm[30]}`);
  assert.ok(Math.abs(rep.angles.upperArm[0] - TOP.upperArm) < 3, `${rep.angles.upperArm[0]}`);
});

test('a clip with no full rep is an error', () => {
  const clip = squatClip();
  // A second in, the first rep has only just left the top.
  clip.frames = clip.frames.slice(0, 30);
  assert.throws(() => traceRep(clip, ENTRY, ['shin']), /no full rep/);
});

test('a time range reads as seconds or minutes and seconds', () => {
  assert.deepEqual(parseRange('12.5-18'), [12.5, 18]);
  assert.deepEqual(parseRange('1:02-1:10.5'), [62, 70.5]);
  assert.throws(() => parseRange('18-12'), /time range/);
});

test('trace writes the pose\'s angle keyframes and nothing of the clip', async () => {
  const out = await mkdtemp(path.join(tmpdir(), 'traces-'));
  const asked = [];
  const landmarks = async (clip, from, to) => { asked.push([clip, from, to]); return squatClip(); };
  await trace('barbell_squat', '/clips/squat side.mp4', '0-9', { out, angles: ['shin', 'thigh'], landmarks });
  assert.deepEqual(asked, [['/clips/squat side.mp4', 0, 9]]);
  assert.deepEqual(await readdir(out), ['barbell_squat.json']);
  const written = JSON.parse(await readFile(path.join(out, 'barbell_squat.json'), 'utf8'));
  assert.deepEqual(Object.keys(written).sort(), ['angles', 'clip', 'range', 'rep', 'side']);
  assert.equal(written.clip, 'squat side.mp4');
  assert.deepEqual(written.range, [0, 9]);
  assert.deepEqual(Object.keys(written.angles), ['shin', 'thigh']);
  assert.ok(written.angles.shin.every((v) => typeof v === 'number'));
});
