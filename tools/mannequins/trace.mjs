// Traces a pose's angles from side-on footage of the lift (README, Tracing a pose):
//
//   node trace.mjs <pose> <footage> <from>-<to> [--angles shin,thigh,trunk,upperArm,forearm]
//
// MediaPipe Pose Landmarker finds the lifter in each frame of the time range (`landmarks.py`, on
// the CPU). The angles are measured side-on from the nearer side, smoothed, and one clean rep is
// picked; its lowering, from the top to the bottom, is sampled once per baked frame and written to
// `traces/<pose>.json`. Only the angles are kept: no footage, frame or landmark is written anywhere
// that stays, and the feet, hands, contacts and tempo stay authored in the pose entry.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { resolve, TRACES } from './entries.mjs';
import { FRAMES } from './stage.mjs';
import { ANGLES, sideAngles } from './angles.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

// MediaPipe's landmarks: the left side's, then the right's is one more.
const SHOULDER = 11;
const ELBOW = 13;
const WRIST = 15;
const HIP = 23;
const KNEE = 25;
const ANKLE = 27;
const HEEL = 29;
const TOE = 31;
const SIDE = [SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE, HEEL, TOE];

/** A time range, `<from>-<to>`, each in seconds or minutes and seconds (`1:02.5`). */
export function parseRange(range) {
  const seconds = (s) => s.split(':').reduce((a, v) => a * 60 + Number(v), 0);
  const [from, to] = range.split('-').map(seconds);
  if (!(from >= 0 && to > from)) throw new Error(`"${range}" isn't a time range: <from>-<to>, in seconds or m:ss`);
  return [from, to];
}

const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s[Math.floor(s.length / 2)]; };
const percentile = (xs, p) => { const s = [...xs].sort((a, b) => a - b); return s[Math.round((p / 100) * (s.length - 1))]; };

/** Savitzky–Golay smoothing, quadratic, over `window` samples (odd); near the ends the window shrinks to fit. */
function smooth(xs, window) {
  return xs.map((_, i) => {
    const m = Math.min((window - 1) / 2, i, xs.length - 1 - i);
    if (m < 1) return xs[i];
    const norm = (2 * m - 1) * (2 * m + 1) * (2 * m + 3);
    let sum = 0;
    for (let k = -m; k <= m; k++) sum += ((3 * (3 * m * m + 3 * m - 1) - 15 * k * k) / norm) * xs[i + k];
    return sum;
  });
}

/**
 * Each landmark, frame by frame, where MediaPipe saw it: in a frame where no one was found, or
 * where it sees the landmark much less well than it usually does (behind the plates, say), the
 * landmark takes its neighbours' place, in a straight line between them.
 */
function fillGaps(frames) {
  if (!frames.some((f) => f.image)) throw new Error('MediaPipe found no one in the footage');
  const usual = Array.from({ length: 33 }, (_, n) => median(frames.filter((f) => f.image).map((f) => f.visibility[n])));
  const image = frames.map(() => []);
  for (let n = 0; n < 33; n++) {
    const good = frames.map((f, i) => (f.image && f.visibility[n] >= 0.8 * usual[n] ? i : -1)).filter((i) => i >= 0);
    const near = good.length ? good : frames.map((f, i) => (f.image ? i : -1)).filter((i) => i >= 0);
    let k = 0;
    frames.forEach((_, i) => {
      while (k + 1 < near.length && near[k + 1] <= i) k++;
      const a = near[k] <= i ? near[k] : near[0];
      const b = near[k] >= i ? near[k] : near[Math.min(k + 1, near.length - 1)];
      const t = a === b ? 0 : (i - a) / (b - a);
      const [pa, pb] = [frames[a].image[n], frames[b].image[n]];
      image[i][n] = [pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t];
    });
  }
  return image;
}

/**
 * The rep's lowering from the footage's landmarks (`{ fps, frames: [{ t, image, visibility }] }`,
 * image pixels with y down). `entry` gives the pose's top and bottom, which say which way down is;
 * the `angles` named are measured side-on and sampled once per baked frame.
 */
export function traceRep(footage, entry, angles = ANGLES) {
  const { fps, frames } = footage;
  // The nearer side is the one MediaPipe sees better.
  const seen = (off) => frames.filter((f) => f.visibility).reduce((a, f) => a + SIDE.reduce((b, n) => b + f.visibility[n + off], 0), 0);
  const off = seen(0) >= seen(1) ? 0 : 1;
  const image = fillGaps(frames);
  // In the lifter's frame: y up, and x the way the toes point.
  const facing = Math.sign(median(image.map((p) => p[TOE + off][0] - p[HEEL + off][0]))) || 1;
  const window = Math.max(5, Math.round(0.25 * fps) | 1);
  const track = (n) => {
    const x = smooth(image.map((p) => facing * p[n + off][0]), window);
    const y = smooth(image.map((p) => -p[n + off][1]), window);
    return x.map((v, i) => [v, y[i]]);
  };
  const [shoulder, elbow, wrist, hip, knee, ankle] = [SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE].map(track);
  const measured = frames.map((_, i) => sideAngles({
    ankle: ankle[i], knee: knee[i], hip: hip[i], shoulder: shoulder[i], elbow: elbow[i], wrist: wrist[i],
  }));
  const series = Object.fromEntries(ANGLES.map((k) => [k, measured.map((m) => m[k])]));

  // How far down the rep each frame is: along the pose's own way from its top to its bottom.
  const keys = ANGLES.filter((k) => entry.top?.[k] !== undefined && entry.bottom?.[k] !== undefined && entry.top[k] !== entry.bottom[k]);
  if (!keys.length) throw new Error('The pose gives no top and bottom angles to tell its reps by');
  const way = keys.map((k) => entry.bottom[k] - entry.top[k]);
  const along = frames.map((_, i) => keys.reduce((a, k, j) => a + series[k][i] * way[j], 0));
  const lo = percentile(along, 3);
  const hi = percentile(along, 97);
  const depth = along.map((v) => (v - lo) / (hi - lo || 1));
  // A full rep travels at least half as far as the pose does from its top to its bottom.
  const travel = way.reduce((a, w) => a + w * w, 0);
  const full = hi - lo >= 0.5 * travel;

  // The reps: each time the depth leaves the top and reaches the bottom. Take the middle one, from
  // the frame it leaves the top to the frame it reaches the bottom.
  const reps = [];
  for (let i = 1; i < depth.length; i++) {
    if (!(depth[i - 1] < 0.15 && depth[i] >= 0.15)) continue;
    // It must reach the bottom before it comes back up to the top: a rep that turns back halfway
    // isn't one.
    let bottom = i;
    while (bottom + 1 < depth.length && depth[bottom] < 0.9 && depth[bottom] >= 0.15) bottom++;
    if (depth[bottom] < 0.9) continue;
    let start = i;
    while (start > 0 && depth[start] > 0.02 && depth[start - 1] < depth[start]) start--;
    // The bottom is where it first reaches its deepest, within 2%.
    let end = bottom;
    while (end + 1 < depth.length && depth[end + 1] > depth[end]) end++;
    const deepest = depth[end];
    end = bottom;
    while (depth[end] < deepest - 0.02) end++;
    reps.push([start, end]);
  }
  if (!full || !reps.length) throw new Error('The footage has no full rep in its time range');
  const [start, end] = reps[Math.floor(reps.length / 2)];

  // Sample the lowering evenly in time, as the footage's frames are.
  const sampled = {};
  for (const k of angles) {
    sampled[k] = Array.from({ length: FRAMES }, (_, f) => {
      const at = start + ((end - start) * f) / (FRAMES - 1);
      const i = Math.min(Math.floor(at), end - 1);
      const v = series[k][i] + (series[k][i + 1] - series[k][i]) * (at - i);
      return Math.round(v * 10) / 10;
    });
  }
  const time = (i) => Math.round(frames[i].t * 100) / 100;
  return { side: off ? 'right' : 'left', rep: [time(start), time(end)], angles: sampled };
}

/** Runs `landmarks.py` over the footage's time range and reads what it found; the file it writes is removed. */
async function mediapipe(footage, from, to) {
  const python = process.env.MANNEQUIN_PYTHON ?? 'python3';
  const model = process.env.MANNEQUIN_POSE_MODEL ?? path.join(HERE, 'trace', 'pose_landmarker_heavy.task');
  const dir = await mkdtemp(path.join(tmpdir(), 'mannequin-trace-'));
  try {
    const out = path.join(dir, 'landmarks.json');
    await new Promise((ok, fail) => {
      const p = spawn(python, [path.join(HERE, 'trace', 'landmarks.py'), footage, String(from), String(to), model, out], { stdio: 'inherit' });
      p.on('error', fail);
      p.on('exit', (code) => (code === 0 ? ok() : fail(new Error(`landmarks.py exited with ${code}`))));
    });
    return JSON.parse(await readFile(out, 'utf8'));
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

/**
 * Traces `pose`'s angles from `footage` in `range` and writes `<out>/<pose>.json`: the footage's
 * file name, the range, the rep's times in it, the side traced and the angles' samples.
 */
export async function trace(pose, footage, range, { out = TRACES, angles = ANGLES, landmarks = mediapipe } = {}) {
  const [from, to] = parseRange(range);
  const rep = traceRep(await landmarks(footage, from, to), resolve(pose), angles);
  await mkdir(out, { recursive: true });
  const file = path.join(out, `${pose}.json`);
  const written = { footage: path.basename(footage), range: [from, to], rep: rep.rep, side: rep.side, angles: rep.angles };
  await writeFile(file, JSON.stringify(written, null, 1) + '\n');
  return file;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const flag = args.indexOf('--angles');
  const angles = flag >= 0 ? (args.splice(flag, 2)[1] ?? '').split(',').filter(Boolean) : ANGLES;
  const [pose, footage, range] = args;
  if (!range || !angles.length) {
    console.error('usage: node trace.mjs <pose> <footage> <from>-<to> [--angles shin,thigh,trunk,upperArm,forearm]');
    process.exit(2);
  }
  const unknown = angles.filter((a) => !ANGLES.includes(a));
  if (unknown.length) throw new Error(`No angle ${unknown.join(', ')}: trace measures ${ANGLES.join(', ')}`);
  console.log(`traced ${pose} → ${path.relative(process.cwd(), await trace(pose, path.resolve(footage), range, { angles }))}`);
}
