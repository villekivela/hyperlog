// The mannequin's rig: each bone is aimed at a target from its bind pose, with two-bone IK for the
// legs and arms, a grip solve that lays the bar across the palm, and fingers curled round it.
// Every function takes the rig built by `buildRig`, so nothing here keeps state of its own.
import * as THREE from 'three';
import { closeHand, handModel, relaxHand, seatOf, skinPoints } from './grip.mjs';

export const V = (x, y, z) => new THREE.Vector3(x, y, z);
export const rad = (d) => (d * Math.PI) / 180;
export const X = V(1, 0, 0);
export const UP = V(0, 1, 0);

export const wpos = (b) => b.getWorldPosition(new THREE.Vector3());
const wquat = (b) => b.getWorldQuaternion(new THREE.Quaternion());

/** The orientation of a frame whose first axis is `primary` and second as close to `secondary` as it can be. */
function frame(primary, secondary) {
  const x = primary.clone().normalize();
  const y = secondary.clone().addScaledVector(x, -secondary.dot(x)).normalize();
  return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, x.clone().cross(y)));
}

/** Two-bone IK from A toward T: where the middle joint goes, bent toward `pole`. */
/** Over how much of its reach a limb eases into straight (metres), so a joint never snaps open or shut at lockout. */
const SOFT = 0.004;

/**
 * How far a limb of `l1` and `l2` reaches toward a point `d` away: all the way until the last
 * `SOFT` of its length, then less and less, never quite straight. A straight joint's angle moves
 * as the root of how far the reach shortens, so a hard stop makes it snap open; this eases it.
 */
export function softReach(d, l1, l2) {
  const start = l1 + l2 - SOFT;
  return d <= start ? d : start + SOFT * (1 - Math.exp(-(d - start) / SOFT));
}

/** A two-bone limb from `A` toward `T`, bent toward `pole`; `soft`, it eases into straight (`softReach`). */
function ik(A, T, l1, l2, pole, soft = false) {
  const d0 = T.clone().sub(A);
  let d = d0.length();
  const dir = d0.divideScalar(d || 1);
  d = Math.max(soft ? softReach(d, l1, l2) : Math.min(d, l1 + l2 - 1e-4), Math.abs(l1 - l2) + 1e-4);
  const a = (l1 * l1 - l2 * l2 + d * d) / (2 * d);
  const h = Math.sqrt(Math.max(0, l1 * l1 - a * a));
  const p = pole.clone().addScaledVector(dir, -pole.dot(dir)).normalize();
  return { mid: A.clone().addScaledVector(dir, a).addScaledVector(p, h), end: A.clone().addScaledVector(dir, d), pole: p };
}

function setWorldQuat(bone, q) {
  bone.quaternion.copy(wquat(bone.parent).invert().multiply(q));
  bone.updateMatrixWorld(true);
}

/** Turns a bone by the rotation that takes its rest frame to the target frame. */
function aim(rig, name, primary, secondary) {
  const r = rig.rest[name];
  const delta = frame(primary, secondary).multiply(frame(r.dir, r.sec(r.dir)).invert());
  setWorldQuat(rig.bones[name], delta.multiply(r.wq.clone()));
}

/** `turn`, about the axes a bone had in the bind pose (its world turn there `wq`), as a turn of the bone's own. */
const ownTurn = (wq, turn) => wq.clone().invert().multiply(turn).multiply(wq);

const SPINE = ['pelvis', 'spine_01', 'spine_02', 'spine_03', 'neck_01', 'Head'];

/**
 * Measures the mannequin in its bind pose: each bone's rest frame, the segment lengths the poses
 * use, and the hands as the grip solve sees them (`grip.mjs`).
 */
export function buildRig(root) {
  const meshes = [];
  root.traverse((o) => { if (o.isSkinnedMesh) meshes.push(o); });
  meshes[0].skeleton.pose();
  root.updateMatrixWorld(true);
  const bones = {};
  for (const b of meshes[0].skeleton.bones) bones[b.name] = b;
  const rest = {};
  for (const [name, b] of Object.entries(bones)) rest[name] = { lq: b.quaternion.clone(), wq: wquat(b), p: wpos(b) };
  const dirTo = (a, c) => rest[c].p.clone().sub(rest[a].p);
  // In the bind pose the knees point forward and the elbows back.
  const legSec = (dir) => dir.clone().cross(V(0, 0, 1));
  const armSec = (dir) => dir.clone().cross(V(0, 0, -1));
  SPINE.slice(0, -1).forEach((a, i) => Object.assign(rest[a], { dir: dirTo(a, SPINE[i + 1]), sec: () => X.clone() }));
  for (const k of ['l', 'r']) {
    Object.assign(rest['thigh_' + k], { dir: dirTo('thigh_' + k, 'calf_' + k), sec: legSec });
    Object.assign(rest['calf_' + k], { dir: dirTo('calf_' + k, 'foot_' + k), sec: legSec });
    Object.assign(rest['foot_' + k], { dir: dirTo('foot_' + k, 'ball_' + k), sec: (d) => d.clone().cross(UP) });
    Object.assign(rest['upperarm_' + k], { dir: dirTo('upperarm_' + k, 'lowerarm_' + k), sec: armSec });
    // The hand and forearm twist so the knuckles run along the bar.
    const across = dirTo('pinky_01_' + k, 'index_01_' + k);
    Object.assign(rest['lowerarm_' + k], { dir: dirTo('lowerarm_' + k, 'hand_' + k), sec: () => across });
    Object.assign(rest['hand_' + k], { dir: dirTo('hand_' + k, 'middle_01_' + k), sec: () => across, across });
  }
  const len = (a, c) => dirTo(a, c).length();
  const hipMid = rest.thigh_l.p.clone().add(rest.thigh_r.p).multiplyScalar(0.5);
  const shMid = rest.upperarm_l.p.clone().add(rest.upperarm_r.p).multiplyScalar(0.5);
  const palmLength = len('hand_l', 'middle_01_l');
  // The sole's length along the left foot, from the skinned vertices at the floor.
  let heel = Infinity;
  let toe = -Infinity;
  for (const m of meshes) {
    for (let i = 0; i < m.geometry.attributes.position.count; i++) {
      const v = m.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(m.matrixWorld);
      if (v.y < 0.04 && Math.sign(v.x) === Math.sign(rest.foot_l.p.x)) {
        heel = Math.min(heel, v.z);
        toe = Math.max(toe, v.z);
      }
    }
  }
  const footRest = dirTo('foot_l', 'ball_l');
  const dims = {
    thigh: len('thigh_l', 'calf_l'), shin: len('calf_l', 'foot_l'),
    up: len('upperarm_l', 'lowerarm_l'), fore: len('lowerarm_l', 'hand_l'),
    // From the wrist to the knuckles: a grip's landmarks lie along it (`grip.mjs`).
    palmLength,
    trunk: shMid.y - hipMid.y, shW: Math.abs(rest.upperarm_l.p.x), ankleY: rest.foot_l.p.y,
    // How far the foot pitches down from the ankle to the ball, standing flat.
    footPitch: Math.atan2(-footRest.y, Math.hypot(footRest.x, footRest.z)),
    // How far the middle of the sole is in front of the ankle joint.
    midfoot: (heel + toe) / 2 - rest.foot_l.p.z,
    // The ball of the foot, standing flat: how far in front of the ankle and how high off the floor.
    ballAhead: Math.hypot(footRest.x, footRest.z), ballY: rest.ball_l.p.y,
    // From the collarbone's root to the shoulder joint: how far a shrug lifts the shoulder.
    clavicle: len('clavicle_l', 'upperarm_l'),
  };
  const palms = {};
  // The palm's face, square to the knuckles' line: a bar held across the knuckles lies off it.
  const faces = {};
  const hands = {};
  // Each digit's joints, from the knuckle out, with what it takes to curl one by an angle: for
  // closing the fingers and the thumb round what the hand holds until they touch it (`grip.mjs`).
  // The fingers curl about the knuckles' line, which is the line the hand takes a bar along. The
  // thumb's root turns toward the fingers (`turn`, the fraction of the way from where it lies in the
  // bind pose) and swings out of the palm; its other joints bend about the bar's line, round the
  // bar the other way from the fingers (`sense` -1), or the same way, beside them.
  const digits = { l: {}, r: {} };
  for (const k of ['l', 'r']) {
    const s = Math.sign(rest['hand_' + k].p.x) || 1;
    const hand = rest['hand_' + k].p;
    const fd = rest['middle_01_' + k].p.clone().sub(hand).normalize();
    const th = rest['thumb_01_' + k].p.clone().sub(hand);
    th.addScaledVector(fd, -th.dot(fd));
    const palm = fd.clone().cross(th).normalize().multiplyScalar(s);
    palms[k] = palm;
    // The palm's face square to the knuckles' line.
    const knuckles = rest['hand_' + k].across.clone().addScaledVector(fd, -rest['hand_' + k].across.dot(fd)).normalize();
    const face = fd.clone().cross(knuckles).normalize();
    if (face.dot(palm) < 0) face.negate();
    faces[k] = face;
    hands[k] = { wrist: hand, fingers: fd, normal: face, knuckles, bend: fd.clone().cross(face).normalize() };
    const turnOwn = (n, turn) => rest[n].lq.clone().multiply(ownTurn(rest[n].wq, turn));
    const about = (axis, d) => new THREE.Quaternion().setFromAxisAngle(axis, rad(d));
    for (const f of ['index', 'middle', 'ring', 'pinky']) {
      [0, 1, 2].forEach((i) => {
        const n = `${f}_0${i + 1}_${k}`;
        const child = bones[`${f}_0${i + 2}_${k}`] || bones[`${f}_04_leaf_${k}`];
        const dir = child ? rest[child.name].p.clone().sub(rest[n].p).normalize() : fd;
        const axis = dir.clone().cross(face).normalize();
        (digits[k][f] ??= []).push({ bone: bones[n], curl: (d) => turnOwn(n, about(axis, d)) });
      });
    }
    // The thumb's metacarpal, from its root toward the next joint, and the turn that lays it along the fingers.
    const meta = rest['thumb_02_' + k].p.clone().sub(rest['thumb_01_' + k].p);
    const flat = meta.clone().addScaledVector(face, -meta.dot(face)).normalize();
    const toFingers = Math.atan2(flat.clone().cross(fd).dot(face), flat.dot(fd));
    const thumb = (d, { turn = 0 } = {}) => {
      const spin = about(face, (turn * toFingers * 180) / Math.PI);
      const along = flat.clone().applyQuaternion(spin);
      return about(along.cross(face).normalize(), d).multiply(spin);
    };
    digits[k].thumb = [
      // `turn` is the root's turn in the bind pose's frame, which its other joints are carried by.
      { bone: bones['thumb_01_' + k], curl: (d, mode) => turnOwn('thumb_01_' + k, thumb(d, mode)), turn: thumb },
      ...['thumb_02_', 'thumb_03_'].map((b) => ({
        bone: bones[b + k],
        // About the bar where the grip gives its line (`bar`, in the bind pose's frame), else the knuckles'.
        curl: (d, { sense = 1, bar = null } = {}) => turnOwn(b + k, about(bar ?? fd.clone().cross(face).normalize(), sense * d)),
      })),
    ];
  }
  const skin = skinPoints(meshes);
  const rig = {
    root, meshes, bones, rest, dims, pelvisToHips: hipMid.clone().sub(rest.pelvis.p), palms, faces, digits, depth: {}, off: {},
    skin, hand: handModel(skin, bones, hands),
  };
  // How far off the palm's face a barbell's bar is seated at landmark `sits`, held with `thumb`:
  // where a motion puts a bar a straight wrist holds (`motions.mjs`).
  dims.seatOff = (sits, thumb) => (sits === 'open' ? 0 : seatOf(rig, 'l', sits, DEFAULT_RADIUS, thumb).off);
  // Where the shoulders' middle sits from the hips' with the spine straightened upright, as poses
  // straighten it: up the spine, and behind it. The bind pose's spine curves, so it isn't the same.
  for (const n of SPINE.slice(0, 4)) aim(rig, n, UP, X);
  root.updateMatrixWorld(true);
  const hips = between(rig, 'thigh_l', 'thigh_r');
  const shoulders = between(rig, 'upperarm_l', 'upperarm_r').sub(hips);
  // The same, link by link: from the hips to spine_01, on to spine_02 and spine_03, and from there
  // to the shoulders, each [up, forward], for a spine bent link by link (an arch).
  const joints = [hips, ...['spine_01', 'spine_02', 'spine_03'].map((n) => wpos(bones[n])), between(rig, 'upperarm_l', 'upperarm_r')];
  const links = joints.slice(1).map((p, i) => [p.y - joints[i].y, p.z - joints[i].z]);
  Object.assign(dims, { trunkUp: shoulders.y, trunkBack: -shoulders.z, links });
  for (const [n, b] of Object.entries(bones)) b.quaternion.copy(rest[n].lq);
  root.updateMatrixWorld(true);
  return rig;
}

/** `from` turned `angle` radians toward `to`, in the plane of the two. */
function turnToward(from, to, angle) {
  const axis = from.clone().cross(to);
  if (axis.lengthSq() < 1e-12) return from.clone();
  return from.clone().applyAxisAngle(axis.normalize(), angle).normalize();
}

/** The point halfway between bones `a` and `b`, where they are now. */
export const between = (rig, a, b) => wpos(rig.bones[a]).add(wpos(rig.bones[b])).multiplyScalar(0.5);

/** Which side a bone of side `k` ('l' or 'r') is on: -1 for -x, 1 for +x. */
export const sideOf = (rig, k) => Math.sign(rig.rest['thigh_' + k].p.x) || 1;

/** Where the hand on side `s` holds: its own handle, or the bar `grip` metres either side of the middle. */
export const gripTarget = (P, s) => (P.hand ? P.hand(s) : P.bar.clone().addScaledVector(X, s * P.grip));

/** The way the knuckles run along the grip: along the bar toward the middle, or the handle's own way. */
const acrossOf = (P, s) => (P.across ? P.across(s) : X.clone().multiplyScalar(-s));

/** The world rotation that takes bone `name` from its bind pose to where it is now. */
export const turnOf = (rig, name) => wquat(rig.bones[name]).multiply(rig.rest[name].wq.clone().invert());

/**
 * Where the hand on side `k` holds now: on the axis of a bar seated at its grip's landmark, off the
 * palm's face. A hand not yet posed holds as a pull does, a barbell's bar at the base of its fingers.
 */
export function heldAt(rig, k) {
  const { bones } = rig;
  const wrist = wpos(bones['hand_' + k]);
  const fingers = wpos(bones['middle_01_' + k]).sub(wrist).normalize();
  const palm = rig.faces[k].clone().applyQuaternion(turnOf(rig, 'hand_' + k));
  const { depth, off } = rig.depth[k] === undefined ? seatOf(rig, k, 'base', DEFAULT_RADIUS) : { depth: rig.depth[k], off: rig.off[k] };
  return wrist.addScaledVector(fingers, depth).addScaledVector(palm, off);
}

/**
 * What a degree of the elbow off the pose's pole costs: a little, so the elbow turns where the
 * hand needs it, keeping to the pole's side; much more where the pose's arm was traced from footage
 * or the entry points the elbows itself (`P.armTraced`).
 */
const POLE_PULL = 0.3;
const TRACED_POLE_PULL = 3;
/**
 * What a degree of each costs, against a degree of the elbow off its pole: the wrist bent, most of
 * all, so a wrist the pose doesn't set ends straight (owner, #127); the bar slanting across the
 * palm, little, so a wrist that can't be straight with its knuckles along the bar is straight with
 * the bar slanting across its palm instead, as a lifter's would be; and how far the bar may slant.
 */
const BEND = 10;
const SLANT = 0.2;
const MAX_SLANT = rad(35);
/** What a metre of the hand short of its grip costs: a centimetre, as much as 2° of wrist bend. */
const SHORT_COST = 2000;
/** How far short of its grip a straight arm's hand may fall before the arm bends its wrist to reach instead. */
const SHORT_MAX = 0.01;
/** The radius of what a hand holds when the stage doesn't say: a barbell's. */
const DEFAULT_RADIUS = 0.014;

/**
 * An arm for a hand on something lying along `along` (a unit vector): the hand square to it, its
 * knuckles along it, rolling about it; the elbow anywhere round the line from the shoulder to the
 * wrist that the arm's lengths allow. Of all of them, the one with the straightest wrist, the elbow
 * kept toward `pole` (`POLE_PULL`): the wrist as `aimOf(forearm)` points the fingers, straight unless
 * the pose bends it. `palmOf(fingers)` is the palm's normal for a hand pointing that way, `depth` and
 * `offAt(fingers)` where the grip sits in the hand. Null where the grip is too close
 * to reach.
 * With `near` (the hand's roll `t` and the elbow's turn `p` of a moment before), only the arms
 * close to it, so an arm followed through the rep keeps to one branch; with `near.at`, that arm.
 */
function straightArm(sh, grip, along, L, pole, palmOf, depth, offAt, aimOf, pull = POLE_PULL, near = null) {
  const a = (Math.abs(along.y) < 0.9 ? UP : X).clone().cross(along).normalize();
  const b = along.clone().cross(a).normalize();
  // The fingers square to the bar, rolled `t` round it, then slanted `q` along it: the bar lying
  // across the palm that much off the knuckles' line.
  const fingersAt = (t, q) => a.clone().multiplyScalar(Math.cos(t)).addScaledVector(b, Math.sin(t)).multiplyScalar(Math.cos(q)).addScaledVector(along, Math.sin(q));
  const wristAt = (fingers) => grip.clone().addScaledVector(fingers, -depth).addScaledVector(palmOf(fingers), -offAt(fingers));
  const score = (t, p, q) => {
    if (Math.abs(q) > MAX_SLANT) return null;
    const fingers = fingersAt(t, q);
    const end = wristAt(fingers);
    const toEnd = end.clone().sub(sh);
    const far = toEnd.length();
    if (far < Math.abs(L.up - L.fore) + 1e-4) return null;
    // Near and past the arm's reach, the arm eased toward straight and the hand short of the grip
    // by what it lacks, which costs dearly: so the hand rolls to reach where it can, and the arm
    // stays the same arm as the grip comes back into reach.
    const d = softReach(far, L.up, L.fore);
    const short = far - d;
    const axis = toEnd.divideScalar(far);
    end.copy(sh).addScaledVector(axis, d);
    const along_ = (L.up * L.up - L.fore * L.fore + d * d) / (2 * d);
    const h = Math.sqrt(Math.max(0, L.up * L.up - along_ * along_));
    // Round the line from the shoulder to the wrist, from the pole's side.
    const u1 = pole.clone().addScaledVector(axis, -pole.dot(axis)).normalize();
    const u2 = axis.clone().cross(u1);
    const mid = sh.clone().addScaledVector(axis, along_).addScaledVector(u1, h * Math.cos(p)).addScaledVector(u2, h * Math.sin(p));
    const bend = aimOf(end.clone().sub(mid).normalize()).angleTo(fingers);
    const offPole = Math.abs(Math.atan2(Math.sin(p), Math.cos(p)));
    return { t, p, q, fingers, end, mid, bend, short, cost: ((BEND * bend + SLANT * Math.abs(q) + pull * offPole) * 180) / Math.PI + short * SHORT_COST };
  };
  let best = null;
  const consider = (t, p, q) => {
    const c = score(t, p, q);
    if (c && (!best || c.cost < best.cost)) best = c;
  };
  // Round the best: the roll and the elbow together, then the slant, by turns.
  const refine = (step, n) => {
    for (let round = 0; round < 2; round++) {
      const { t, p } = best;
      for (let i = -n; i <= n; i++) for (let j = -n; j <= n; j++) consider(t + i * step, p + j * step, best.q);
      const { q } = best;
      for (let i = -n; i <= n; i++) consider(best.t, best.p, q + i * step);
    }
  };
  // An arm already chosen (`near.at`) is taken as it is, if it reaches.
  if (near?.at) {
    const at = score(near.t, near.p, near.q);
    return at && at.short <= SHORT_MAX ? at : straightArm(sh, grip, along, L, pole, palmOf, depth, offAt, aimOf, pull);
  }
  if (near) {
    consider(near.t, near.p, near.q);
    if (best) refine(rad(2.5), 8);
  }
  if (!best) {
    for (let i = 0; i < 72; i++) for (let j = 0; j < 48; j++) consider((2 * Math.PI * i) / 72, (2 * Math.PI * j) / 48 - Math.PI, 0);
    if (best) refine(rad(5), 8);
  }
  // A grip that no roll of the hand brings within reach is for an arm that bends its wrist.
  if (!best || best.short > SHORT_MAX) return null;
  // Finer round the best, three times.
  for (const step of [(2 * Math.PI) / 72 / 6, (2 * Math.PI) / 72 / 36, (2 * Math.PI) / 72 / 216]) refine(step, 6);
  return best;
}

/** The turn the pose's offset gives bone `name` (`P.offsets`, [x, y, z] in degrees), as a turn of its own; null for none. */
function offsetOf(rig, P, name) {
  const o = P.offsets?.[name];
  if (!o) return null;
  if (!rig.bones[name]) throw new Error(`No bone ${name} to turn`);
  return ownTurn(rig.rest[name].wq, new THREE.Quaternion().setFromEuler(new THREE.Euler(rad(o[0]), rad(o[1]), rad(o[2]), 'XYZ')));
}

/**
 * Turns each of `names` by the pose's offset for it, on top of where the solve put it: about the
 * axes the bone had in the bind pose (x across the body, y up, z forward), carried with it, so its
 * children turn with it. A turn multiplies the bone's own, so the order the names come in doesn't
 * matter; only that nothing aims them after. Returns the names done.
 */
function offset(rig, P, names) {
  const done = [];
  for (const name of names) {
    const turn = offsetOf(rig, P, name);
    if (!turn) continue;
    rig.bones[name].quaternion.multiply(turn);
    rig.bones[name].updateMatrixWorld(true);
    done.push(name);
  }
  return done;
}

/**
 * Turns a limb (`[upper, lower, end]`: the thigh, calf and foot, or the upper arm, forearm and hand)
 * by the pose's offsets and solves it again to its contact (#164). The upper bone's turn swings the
 * knee or elbow round the line to the contact, the lower's keeps only its twist (the contact sets
 * how far the joint bends), and the end bone keeps its whole turn on the solve's. `endAt(end)` is
 * where the ankle or the wrist must be with the end bone turned so. Returns the names turned.
 */
function offsetLimb(rig, P, [upper, lower, end], lengths, endAt, soft) {
  if (![upper, lower, end].some((n) => P.offsets?.[n])) return [];
  const { bones, rest } = rig;
  const endTurn = wquat(bones[end]).multiply(offsetOf(rig, P, end) ?? new THREE.Quaternion());
  const done = offset(rig, P, [upper, lower]);
  setWorldQuat(bones[end], endTurn);
  // Each bone keeps the twist it has now about its length.
  const twistOf = (n) => rest[n].sec(rest[n].dir).clone().applyQuaternion(turnOf(rig, n));
  const twists = [twistOf(upper), twistOf(lower)];
  const root = wpos(bones[upper]);
  const limb = ik(root, endAt(end), ...lengths, wpos(bones[lower]).sub(root), soft);
  aim(rig, upper, limb.mid.clone().sub(root), twists[0]);
  aim(rig, lower, limb.end.clone().sub(limb.mid), twists[1]);
  setWorldQuat(bones[end], endTurn);
  return [...done, ...(P.offsets[end] ? [end] : [])];
}

/**
 * Poses the rig from a pose frame: the hip centre and trunk direction, the head's direction, the
 * ankles, the feet's way, knee and elbow poles, and where the hands hold. A pose can also bend
 * the spine (`spine`) or lift the shoulders (`shrug`). `near` is each side's straight arm and
 * fingers' closing of a moment before (`{ l, r }`, as this returns them), for a hand followed
 * through the rep; with `at`, they're taken as they are. It returns this moment's. `holding(s)`
 * is what the hand on side `s` holds as the kit is placed for the arms (`stage.mjs`).
 */
export function poseRig(rig, P, near = {}, holding = null) {
  const arms = {};
  // The bones turned on top of the solve so far: the trunk's and the shoulders' before the limbs
  // reach from them, so the hands and feet still hold; a limb's after it is solved; the rest last.
  const turned = new Set();
  const { bones, rest, dims: L } = rig;
  for (const [n, b] of Object.entries(bones)) b.quaternion.copy(rest[n].lq);
  rig.root.updateMatrixWorld(true);
  // The grip: where the bar sits in the hand and the thumb (`grip.mjs`); an open hand (`openHands`,
  // lying on a shoulder) holds nothing.
  const hold = P.openHands ? { sits: 'open', thumb: 'open' } : P.hold ?? { sits: 'base', thumb: 'wrapped' };
  // The trunk: the spine rigid along u, the pelvis placed so the hip joints land on hipC.
  const pelvisDelta = frame(P.u, X).multiply(frame(rest.pelvis.dir, X).invert());
  const pelvisWorld = P.hipC.clone().sub(rig.pelvisToHips.clone().applyQuaternion(pelvisDelta));
  bones.pelvis.position.copy(bones.pelvis.parent.worldToLocal(pelvisWorld));
  bones.pelvis.updateMatrixWorld(true);
  // The spine runs rigid along u unless the pose bends it: `spine` gives the direction of a bone's
  // segment (an arched back).
  for (const n of ['pelvis', 'spine_01', 'spine_02', 'spine_03']) aim(rig, n, P.spine?.[n] ?? P.u, X);
  aim(rig, 'neck_01', P.headDir, X);
  for (const n of offset(rig, P, [...SPINE])) turned.add(n);
  // Shoulders let up (`shrug`, degrees) or down: each collarbone turns about the body's front axis.
  if (P.shrug) {
    const front = X.clone().cross(P.u).normalize();
    for (const k of ['l', 'r']) {
      const clavicle = bones['clavicle_' + k];
      setWorldQuat(clavicle, new THREE.Quaternion().setFromAxisAngle(front, sideOf(rig, k) * rad(P.shrug)).multiply(wquat(clavicle)));
    }
  }
  for (const n of offset(rig, P, ['clavicle_l', 'clavicle_r'])) turned.add(n);
  for (const k of ['l', 'r']) {
    const s = sideOf(rig, k);
    const i = s < 0 ? 0 : 1;
    // The leg, from the hip joint to the ankle, the knee toward its pole.
    const hip = wpos(bones['thigh_' + k]);
    const leg = ik(hip, P.ankles[i], L.thigh, L.shin, P.kneePole(s));
    const n1 = leg.mid.clone().sub(hip);
    const n2 = leg.end.clone().sub(leg.mid);
    aim(rig, 'thigh_' + k, n1, n1.clone().cross(leg.pole));
    aim(rig, 'calf_' + k, n2, n1.clone().cross(leg.pole));
    // The foot flat on what it stands on, the sole as in the bind pose: heading along the floor,
    // turned out, or along the plate it's on.
    const t = rad(P.toeOut ?? 0) * s;
    const { heading, top } = P.foot ? P.foot(s) : { heading: V(Math.sin(t), 0, Math.cos(t)), top: UP };
    const toe = heading.clone().multiplyScalar(Math.cos(L.footPitch)).addScaledVector(top, -Math.sin(L.footPitch));
    aim(rig, 'foot_' + k, toe, toe.clone().cross(top));
    const legBones = ['thigh_', 'calf_', 'foot_'].map((b) => b + k);
    for (const n of offsetLimb(rig, P, legBones, [L.thigh, L.shin], () => P.ankles[i], false)) turned.add(n);
    // The arm: the bar lies across the palm at the base of the fingers, so the wrist sits short of
    // it along the fingers and off it on the back of the hand. The forearm sets both, so iterate.
    const sh = wpos(bones['upperarm_' + k]);
    const grip = gripTarget(P, s);
    const along = acrossOf(P, s);
    const hr = rest['hand_' + k];
    const handRest = frame(hr.dir, hr.across).invert();
    // The forearm twists the knuckle line about the fingers, from overhand (0°) through neutral to
    // underhand (180°); the palm turns with it, and so does the grip's offset.
    const twist = (fingers) => along.clone().applyAxisAngle(fingers, -s * rad(P.gripRotate ?? 0));
    // The pose's own bend of the wrist, [flexion toward the palm, deviation toward the index
    // finger], in degrees.
    const [flex, deviate] = P.wrist ?? [0, 0];
    const fixed = !P.hand || (Boolean(P.across) && !P.heldFree);
    // The bar's own radius (the kit's part under the hand), seated on the palm's skin at the grip's landmark.
    const radius = P.radius?.[s] ?? DEFAULT_RADIUS;
    const seat = seatOf(rig, k, hold.sits, radius, hold.thumb);
    const { depth } = seat;
    rig.depth[k] = depth;
    // How far off the palm the bar is seated with the hand pointing `fingers`: as it lies in the
    // hand, along the knuckles, slanting across the palm toward the fingers, tilting toward its face.
    // A free handle lies along the knuckles.
    const offAt = (fingers) => {
      if (!fixed) return seat.off;
      const line = along.clone().normalize();
      const knuckles = twist(fingers);
      knuckles.addScaledVector(fingers, -knuckles.dot(fingers)).normalize();
      return seat.offAt([line.dot(knuckles), line.dot(fingers), line.dot(palmOf(fingers))]);
    };
    const palmOf = (fingers) => rig.faces[k].clone().applyQuaternion(frame(fingers, twist(fingers)).multiply(handRest));
    // The hand in line with the forearm, then bent by the pose's own `wrist`: where no straight
    // arm reaches, what it holds slants across the palm rather than the wrist bending to it.
    const handOf = (fore) => {
      let fingers = fore.clone();
      const inward = twist(fingers);
      const palm = rig.palms[k].clone().applyQuaternion(frame(fingers, inward).multiply(handRest));
      if (flex) fingers = turnToward(fingers, palm, rad(flex));
      if (deviate) fingers = turnToward(fingers, inward, rad(deviate));
      return fingers;
    };
    let arm;
    let straight = null;
    // A hand on a bar or a set handle keeps its wrist straight, or as the pose bends it, the hand
    // rolling about what it holds and the elbow where that puts it (the pole choosing between the
    // two that fit).
    if (fixed) {
      straight = straightArm(sh, grip, along.clone().normalize(), L, P.armPole(s), palmOf, depth, offAt, P.wrist ? handOf : (fore) => fore, P.armTraced ? TRACED_POLE_PULL : POLE_PULL, near[k]?.t === undefined ? null : near[k]);
      if (straight) {
        arms[k] = { t: straight.t, p: straight.p, q: straight.q, cost: straight.cost };
        const axis = straight.end.clone().sub(sh).normalize();
        const out = straight.mid.clone().sub(sh);
        arm = { mid: straight.mid, end: straight.end, pole: out.addScaledVector(axis, -out.dot(axis)).normalize() };
      }
    }
    // Otherwise the arm reaches for the wrist, which sits short of the grip along the fingers and
    // off it on the back of the hand; the forearm sets the fingers, so iterate.
    if (!straight) {
      const at = grip.clone();
      for (let it = 0; it < 12; it++) {
        arm = ik(sh, at, L.up, L.fore, P.armPole(s), true);
        const fingers = handOf(arm.end.clone().sub(arm.mid).normalize());
        // Halfway to where this pass puts the wrist: the hand's turn follows the forearm, so a full
        // step can swing back and forth.
        at.lerp(grip.clone().addScaledVector(fingers, -depth).addScaledVector(palmOf(fingers), -offAt(fingers)), it ? 0.5 : 1);
      }
    }
    const a1 = arm.mid.clone().sub(sh);
    const a2 = arm.end.clone().sub(arm.mid);
    aim(rig, 'upperarm_' + k, a1, a1.clone().cross(arm.pole));
    const fingers = straight ? straight.fingers.clone() : handOf(a2.clone().normalize());
    // Off the palm as far as the bar's slant across it seats it.
    rig.off[k] = offAt(fingers);
    const inward = twist(fingers);
    // What the hand holds, and the line it lies along (`P.held`, which a kit lays a held part along):
    // a bar or a set handle lies its own way, slanting across the palm or not; a free handle lies
    // along the knuckles, tipped in the palm's plane by `gripTilt` (a dumbbell's: the left and right
    // tip the same way, so the sign follows the side).
    const palmNormal = rig.palms[k].clone().applyQuaternion(frame(fingers, inward).multiply(handRest)).normalize();
    const handle = inward.clone().addScaledVector(fingers, -inward.dot(fingers) / fingers.lengthSq()).normalize();
    // The palm's normal, square to the handle.
    palmNormal.addScaledVector(handle, -palmNormal.dot(handle)).normalize();
    (P.held ??= {})[s] = fixed ? along.clone().normalize() : handle.applyAxisAngle(palmNormal, -s * rad(P.gripTilt ?? 0));
    aim(rig, 'lowerarm_' + k, a2, twist(a2.clone().normalize()));
    aim(rig, 'hand_' + k, fingers, inward);
    // A turned arm before the fingers close, so they close round what the hand holds where it is now:
    // the wrist where the grip lies in the turned hand.
    const armBones = ['upperarm_', 'lowerarm_', 'hand_'].map((b) => b + k);
    const wristAt = (hand) => grip.clone().sub(heldAt(rig, k).sub(wpos(bones[hand])));
    for (const n of offsetLimb(rig, P, armBones, [L.up, L.fore], wristAt, true)) turned.add(n);
    // The fingers and the thumb close round what the hand holds, joint by joint, as far as they
    // touch it: the part the stage placed under it (`holding(s)`, along its own line, in pieces), or
    // a bar of the radius the hand was seated for, along the line it lies in the hand. A hand that
    // holds nothing lies open, or relaxed where it rests (`P.resting`).
    const bar = hold.sits === 'open' || P.resting === s ? null
      : holding ? holding(s) : { at: grip, along: P.held[s].clone(), radius };
    if (bar) (arms[k] ??= {}).fingers = closeHand(rig, k, bar, hold.thumb, near[k]?.at ? near[k].fingers : null);
    else relaxHand(rig, k, hold.sits === 'open');
  }
  offset(rig, P, Object.keys(P.offsets ?? {}).filter((n) => !turned.has(n)));
  return arms;
}
