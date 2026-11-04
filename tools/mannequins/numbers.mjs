// Every frame's numbers, read from a baked clip, for what a movement spec says (README, Reviewing a
// movement). They come from the clip and not the pose, so any clip in the history reads the same:
//
//   node numbers.mjs <clip> [--at <commit>] [--only <name,name>] [--json]
//
// `<clip>` is a clip's name, as `db_incline_press_45`, or an exercise's id for its own clip. `--at`
// reads it as that commit baked it, and `--only` keeps those numbers. A point is in metres, and
// everything else in degrees.
import { execFile } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs, promisify } from 'node:util';
import * as THREE from 'three';
import { deg } from './angles.mjs';
import { CLIPS } from './bake.mjs';
import { nodeOfTrack, openClip, poseFrame } from './glb.mjs';
import { readIndex } from './review.mjs';
import { wpos } from './rig.mjs';
import { scratch } from './tmp.mjs';

const REPO = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

const at = (scene, name) => wpos(scene.getObjectByName(name));

/** The angle at `b` on the way from `a` to `c`: 180° is a straight limb. */
const jointAngle = (a, b, c) => deg(a.clone().sub(b).angleTo(c.clone().sub(b)));

/** Where each named point of the body is, by the bone it sits on. */
const POINTS = { pelvis: 'pelvis', head: 'Head' };
for (const k of ['l', 'r']) Object.assign(POINTS, { [`shoulder_${k}`]: `upperarm_${k}`, [`elbow_${k}`]: `lowerarm_${k}`, [`hand_${k}`]: `hand_${k}`, [`ankle_${k}`]: `foot_${k}` });

const UP = new THREE.Vector3(0, 1, 0);

/** The hand's line across the knuckles, square to the forearm `fore`. */
function acrossHand(scene, k, fore) {
  const across = at(scene, `index_01_${k}`).sub(at(scene, `pinky_01_${k}`));
  return across.addScaledVector(fore, -across.dot(fore)).normalize();
}

/** How far the hand rolled about the forearm since `last`, in degrees, carrying the last line across along with the forearm. */
function rollSince(last, { fore, across }) {
  const carried = last.across.clone().applyQuaternion(new THREE.Quaternion().setFromUnitVectors(last.fore, fore));
  return deg(Math.atan2(carried.clone().cross(across).dot(fore), carried.dot(across)));
}

/** The numbers of the body at the frame the scene is posed at. `hands` keeps each hand's roll from frame to frame. */
function measure(scene, hands) {
  const row = {};
  const mid = (a, b) => at(scene, a).add(at(scene, b)).multiplyScalar(0.5);
  const hips = mid('thigh_l', 'thigh_r');
  const spine = mid('upperarm_l', 'upperarm_r').sub(hips);
  row.trunk = deg(spine.angleTo(UP));
  for (const k of ['l', 'r']) {
    const [shoulder, elbow, wrist] = [`upperarm_${k}`, `lowerarm_${k}`, `hand_${k}`].map((b) => at(scene, b));
    const [hip, knee, ankle] = [`thigh_${k}`, `calf_${k}`, `foot_${k}`].map((b) => at(scene, b));
    const fore = wrist.clone().sub(elbow).normalize();
    row[`elbow_${k}`] = jointAngle(shoulder, elbow, wrist);
    row[`knee_${k}`] = jointAngle(hip, knee, ankle);
    row[`hip_${k}`] = deg(spine.angleTo(knee.clone().sub(hip)));
    row[`shoulder_${k}`] = 180 - deg(spine.angleTo(elbow.clone().sub(shoulder)));
    row[`wrist_${k}`] = deg(at(scene, `middle_01_${k}`).sub(wrist).angleTo(fore));
    const hand = { fore, across: acrossHand(scene, k, fore), roll: 0 };
    if (hands[k]) hand.roll = hands[k].roll + rollSince(hands[k], hand);
    hands[k] = hand;
    row[`roll_${k}`] = hand.roll;
  }
  for (const [point, bone] of Object.entries(POINTS)) row[`${point}.at`] = at(scene, bone).toArray();
  return row;
}

/** A part's turn from `before` to `now`, all of it (`turn`) and about its own length, local x (`spin`). */
function turned(before, now) {
  const step = before.clone().invert().multiply(now);
  const half = Math.abs(Math.atan2(step.x, step.w));
  return { turn: deg(before.angleTo(now)), spin: deg(2 * Math.min(half, Math.PI - half)) };
}

/**
 * The numbers of a moving part: where it is, how far its length stands off level, the end away
 * from the body's middle up, and its turn since the last frame. `seen` keeps its last turn and
 * which way along its length is out, which a bell's handle may lie either way round.
 */
function measurePart(part, seen) {
  const now = part.getWorldQuaternion(new THREE.Quaternion());
  const where = wpos(part);
  const length = new THREE.Vector3(1, 0, 0).applyQuaternion(now);
  const outward = seen?.outward ?? (Math.sign(length.x) * (where.x < 0 ? -1 : 1) || 1);
  const numbers = { at: where.toArray(), tilt: outward * deg(Math.asin(length.y)), ...turned(seen?.now ?? now, now) };
  return { seen: { now, outward }, numbers };
}

/** The parts of the kit that move and keep their size: a bell or a bar, not a cable stretching. */
function movingParts(scene, tracks) {
  const kit = scene.getObjectByName('kit');
  const stretching = new Set(tracks.filter((t) => t.name.endsWith('.scale')).map(nodeOfTrack));
  const names = new Set(tracks.map(nodeOfTrack).filter((n) => !stretching.has(n)));
  return [...names].flatMap((n) => {
    const part = kit?.getObjectByName(n);
    return part ? [part] : [];
  });
}

/** The numbers of each frame of the clip in `file`. */
export async function numbersOf(file) {
  const { scene, tracks } = await openClip(file);
  const parts = movingParts(scene, tracks);
  const last = new Map();
  const hands = {};
  return [...tracks[0].times].map((t, f) => {
    poseFrame(scene, tracks, f);
    const row = { t, ...measure(scene, hands) };
    for (const part of parts) {
      const { seen, numbers } = measurePart(part, last.get(part));
      last.set(part, seen);
      for (const [k, v] of Object.entries(numbers)) row[`${part.name}.${k}`] = v;
    }
    return row;
  });
}

/** The folder holding the clips of `commit`, unpacked into a scratch folder; the clips here without one. */
export async function clipsAt(commit) {
  if (!commit) return CLIPS;
  const dir = await scratch('clips');
  const assets = path.relative(REPO, CLIPS);
  const depth = assets.split(path.sep).length;
  await promisify(execFile)('sh', ['-c', `git archive "$1" "$2" | tar -x -C "$3" --strip-components=${depth}`, 'sh', commit, assets, dir], { cwd: REPO, maxBuffer: 1 << 20 });
  return dir;
}

/** The file of `clip` in `clips`: a clip's name, or an exercise's id. */
export async function clipFile(clip, clips) {
  const index = await readIndex(clips);
  return path.join(clips, index.clips[clip]?.clip ?? `${clip}.glb`);
}

const cell = (v) => (Array.isArray(v) ? v.map((x) => x.toFixed(3)).join(' ') : v.toFixed(1));

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const { values: { at: commit, only, json }, positionals: [clip] } = parseArgs({
    allowPositionals: true,
    options: { at: { type: 'string' }, only: { type: 'string' }, json: { type: 'boolean' } },
  });
  if (!clip) throw new Error('usage: node numbers.mjs <clip> [--at <commit>] [--only <name,name>] [--json]');
  const frames = await numbersOf(await clipFile(clip, await clipsAt(commit)));
  const names = only?.split(',') ?? Object.keys(frames[0]).filter((k) => k !== 't');
  for (const name of names) if (frames[0][name] === undefined) throw new Error(`No ${name} in this clip: ${Object.keys(frames[0]).join(', ')}`);
  if (json) console.log(JSON.stringify(frames.map((f) => Object.fromEntries(['t', ...names].map((k) => [k, f[k]])))));
  else {
    console.log(['frame', ...names].join('\t'));
    frames.forEach((f, i) => console.log([i, ...names.map((k) => cell(f[k]))].join('\t')));
  }
}
