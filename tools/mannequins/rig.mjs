// The mannequin's rig: each bone is aimed at a target from its bind pose, with two-bone IK for the
// legs and arms, a grip solve that lays the bar across the palm, and fingers curled round it.
// Every function takes the rig built by `buildRig`, so nothing here keeps state of its own.
import * as THREE from 'three';

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
function ik(A, T, l1, l2, pole) {
  const d0 = T.clone().sub(A);
  let d = d0.length();
  const dir = d0.divideScalar(d || 1);
  d = Math.max(Math.min(d, l1 + l2 - 1e-4), Math.abs(l1 - l2) + 1e-4);
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

const SPINE = ['pelvis', 'spine_01', 'spine_02', 'spine_03', 'neck_01', 'Head'];
// Each finger joint's curl toward the palm, from the knuckle out; the thumb first swings across
// to the palm, then bends.
const CURL = [70, 75, 55];
// Closed further round a bar held low in the palm (a press, a squat).
const CURL_PALM = [85, 85, 60];
const THUMB = [80, 40, 40];

/**
 * Measures the mannequin in its bind pose: each bone's rest frame, the segment lengths the poses
 * use, and the finger curls that close the hands round a bar.
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
    // Where a bar sits in the hand, along the palm from the wrist: at the fingers' roots for a pull,
    // low in the palm, near its heel, for a press or a squat (`gripDepth: 'palm'`); and how far
    // off the palm's bones it lies.
    gripAlong: palmLength * 0.8, gripPalm: palmLength * 0.45, gripOff: 0.03,
    // A bar low in the palm sits in the hollow of its heel, nearly on the forearm's line.
    gripPalmOff: 0.012,
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
  const curls = [];
  const palmCurls = [];
  const palms = {};
  for (const k of ['l', 'r']) {
    const s = Math.sign(rest['hand_' + k].p.x) || 1;
    const hand = rest['hand_' + k].p;
    const fd = rest['middle_01_' + k].p.clone().sub(hand).normalize();
    const th = rest['thumb_01_' + k].p.clone().sub(hand);
    th.addScaledVector(fd, -th.dot(fd));
    const palm = fd.clone().cross(th).normalize().multiplyScalar(s);
    palms[k] = palm;
    for (const f of ['index', 'middle', 'ring', 'pinky', 'thumb']) {
      CURL.forEach((deg, i) => {
        const n = `${f}_0${i + 1}_${k}`;
        const child = bones[`${f}_0${i + 2}_${k}`] || bones[`${f}_04_leaf_${k}`];
        const dir = child ? rest[child.name].p.clone().sub(rest[n].p).normalize() : fd;
        const thumb = f === 'thumb';
        const axis = thumb && i === 0 ? th.clone().normalize().cross(palm).normalize() : dir.clone().cross(palm).normalize();
        const curl = (d) => {
          const turn = new THREE.Quaternion().setFromAxisAngle(axis, rad(d));
          return rest[n].lq.clone().multiply(rest[n].wq.clone().invert().multiply(turn).multiply(rest[n].wq));
        };
        curls.push([bones[n], curl(thumb ? THUMB[i] : deg)]);
        palmCurls.push([bones[n], curl(thumb ? THUMB[i] : CURL_PALM[i])]);
      });
    }
  }
  const rig = { root, meshes, bones, rest, dims, pelvisToHips: hipMid.clone().sub(rest.pelvis.p), curls, palmCurls, palms, depth: {}, off: {} };
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

/** How far a wrist turns the hand off the forearm's line to lay its knuckles along what it holds. */
const WRIST_REACH = rad(40);

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

/** Where the hand on side `k` holds now: the point across the palm at the base of the fingers. */
export function heldAt(rig, k) {
  const { bones, dims: L } = rig;
  const wrist = wpos(bones['hand_' + k]);
  const fingers = wpos(bones['middle_01_' + k]).sub(wrist).normalize();
  const palm = rig.palms[k].clone().applyQuaternion(turnOf(rig, 'hand_' + k));
  return wrist.addScaledVector(fingers, rig.depth[k] ?? L.gripAlong).addScaledVector(palm, rig.off[k] ?? L.gripOff);
}

/**
 * How many degrees of wrist bend a degree of the elbow off the pose's pole is worth: a little, so
 * the elbow turns where a straight wrist needs it, keeping to the pole's side; much more where the
 * pose's arm was traced from footage or the entry points the elbows itself (`P.armTraced`).
 */
const POLE_PULL = 0.3;
const TRACED_POLE_PULL = 3;

/**
 * An arm for a hand on something lying along `along` (a unit vector): the hand square to it, its
 * knuckles along it, rolling about it; the elbow anywhere round the line from the shoulder to the
 * wrist that the arm's lengths allow. Of all of them, the one with the straightest wrist, the elbow
 * kept toward `pole` (`POLE_PULL`). `palmOf(fingers)` is the palm's normal for a hand pointing that
 * way, `depth` and `off` where the grip sits in the hand. Null where the grip is out of reach.
 */
function straightArm(sh, grip, along, L, pole, palmOf, depth, off, pull = POLE_PULL) {
  const a = (Math.abs(along.y) < 0.9 ? UP : X).clone().cross(along).normalize();
  const b = along.clone().cross(a).normalize();
  const fingersAt = (t) => a.clone().multiplyScalar(Math.cos(t)).addScaledVector(b, Math.sin(t));
  const wristAt = (fingers) => grip.clone().addScaledVector(fingers, -depth).addScaledVector(palmOf(fingers), -off);
  const score = (t, p) => {
    const fingers = fingersAt(t);
    const end = wristAt(fingers);
    const toEnd = end.clone().sub(sh);
    const d = toEnd.length();
    if (d > L.up + L.fore - 1e-4 || d < Math.abs(L.up - L.fore) + 1e-4) return null;
    const axis = toEnd.divideScalar(d);
    const along_ = (L.up * L.up - L.fore * L.fore + d * d) / (2 * d);
    const h = Math.sqrt(Math.max(0, L.up * L.up - along_ * along_));
    // Round the line from the shoulder to the wrist, from the pole's side.
    const u1 = pole.clone().addScaledVector(axis, -pole.dot(axis)).normalize();
    const u2 = axis.clone().cross(u1);
    const mid = sh.clone().addScaledVector(axis, along_).addScaledVector(u1, h * Math.cos(p)).addScaledVector(u2, h * Math.sin(p));
    const bend = end.clone().sub(mid).normalize().angleTo(fingers);
    const offPole = Math.abs(Math.atan2(Math.sin(p), Math.cos(p)));
    return { t, p, fingers, end, mid, bend, cost: ((bend + pull * offPole) * 180) / Math.PI };
  };
  let best = null;
  const consider = (t, p) => {
    const c = score(t, p);
    if (c && (!best || c.cost < best.cost)) best = c;
  };
  for (let i = 0; i < 72; i++) for (let j = 0; j < 48; j++) consider((2 * Math.PI * i) / 72, (2 * Math.PI * j) / 48 - Math.PI);
  if (!best) return null;
  // Finer round the best, three times.
  for (const step of [(2 * Math.PI) / 72 / 6, (2 * Math.PI) / 72 / 36, (2 * Math.PI) / 72 / 216]) {
    const { t, p } = best;
    for (let i = -6; i <= 6; i++) for (let j = -6; j <= 6; j++) consider(t + i * step, p + j * step);
  }
  return best;
}

/**
 * Poses the rig from a pose frame: the hip centre and trunk direction, the head's direction, the
 * ankles, the feet's way, knee and elbow poles, and where the hands hold. A pose can also bend
 * the spine (`spine`) or lift the shoulders (`shrug`).
 */
export function poseRig(rig, P) {
  const { bones, rest, dims: L } = rig;
  for (const [n, b] of Object.entries(bones)) b.quaternion.copy(rest[n].lq);
  // The fingers close round what the hand holds, further for a bar low in the palm; an open hand
  // (`openHands`, lying on a shoulder) keeps them straight.
  if (!P.openHands) for (const [b, q] of P.gripDepth === 'palm' ? rig.palmCurls : rig.curls) b.quaternion.copy(q);
  rig.root.updateMatrixWorld(true);
  // The trunk: the spine rigid along u, the pelvis placed so the hip joints land on hipC.
  const pelvisDelta = frame(P.u, X).multiply(frame(rest.pelvis.dir, X).invert());
  const pelvisWorld = P.hipC.clone().sub(rig.pelvisToHips.clone().applyQuaternion(pelvisDelta));
  bones.pelvis.position.copy(bones.pelvis.parent.worldToLocal(pelvisWorld));
  bones.pelvis.updateMatrixWorld(true);
  // The spine runs rigid along u unless the pose bends it: `spine` gives the direction of a bone's
  // segment (an arched back).
  for (const n of ['pelvis', 'spine_01', 'spine_02', 'spine_03']) aim(rig, n, P.spine?.[n] ?? P.u, X);
  aim(rig, 'neck_01', P.headDir, X);
  // Shoulders let up (`shrug`, degrees) or down: each collarbone turns about the body's front axis.
  if (P.shrug) {
    const front = X.clone().cross(P.u).normalize();
    for (const k of ['l', 'r']) {
      const clavicle = bones['clavicle_' + k];
      setWorldQuat(clavicle, new THREE.Quaternion().setFromAxisAngle(front, sideOf(rig, k) * rad(P.shrug)).multiply(wquat(clavicle)));
    }
  }
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
    // The hand off the forearm: turned at the wrist until its knuckles lie along what it holds, as
    // far as a wrist bends (WRIST_REACH), then bent by `wrist` [flexion toward the palm,
    // deviation toward the index finger], in degrees.
    const [flex, deviate] = P.wrist ?? [0, 0];
    const fixed = !P.hand || (Boolean(P.across) && !P.heldFree);
    const depth = P.gripDepth === 'palm' ? L.gripPalm : L.gripAlong;
    const off = P.gripDepth === 'palm' ? L.gripPalmOff : L.gripOff;
    rig.depth[k] = depth;
    rig.off[k] = off;
    const palmOf = (fingers) => rig.palms[k].clone().applyQuaternion(frame(fingers, twist(fingers)).multiply(handRest));
    const handOf = (fore) => {
      const knuckles = twist(fore).normalize();
      let fingers = fore.clone();
      // Only what lies its own way (a bar, a handle the pose sets) has knuckles to line up with: a
      // free handle turns with the hand. As the forearm comes to run along it, the turn fades out,
      // smoothly, so the solve settles.
      // Only where no straight arm reaches (below), and the pose sets no wrist of its own.
      const square = fore.clone().addScaledVector(knuckles, -fore.dot(knuckles));
      const fade = Math.min(1, Math.max(0, (square.length() - 0.15) / 0.3));
      if (fixed && !P.wrist && fade > 0) fingers = turnToward(fore, square.normalize(), Math.min(fore.angleTo(square), WRIST_REACH) * keep * fade);
      const inward = twist(fingers);
      const palm = rig.palms[k].clone().applyQuaternion(frame(fingers, inward).multiply(handRest));
      if (flex) fingers = turnToward(fingers, palm, rad(flex));
      if (deviate) fingers = turnToward(fingers, inward, rad(deviate));
      return fingers;
    };
    let wrist = grip.clone();
    let arm;
    let straight = null;
    // A hand on a bar or a set handle keeps its wrist straight, the hand rolling about what it
    // holds and the elbow where that puts it (the pole choosing between the two that fit), unless
    // the pose sets its wrist.
    if (fixed && !P.wrist) {
      straight = straightArm(sh, grip, along.clone().normalize(), L, P.armPole(s), palmOf, depth, off, P.armTraced ? TRACED_POLE_PULL : POLE_PULL);
      if (straight) {
        const axis = straight.end.clone().sub(sh).normalize();
        const out = straight.mid.clone().sub(sh);
        arm = { mid: straight.mid, end: straight.end, pole: out.addScaledVector(axis, -out.dot(axis)).normalize() };
      }
    }
    // How much of the fallback's turn the arm can afford: a straight arm gives some back to reach its grip.
    let keep = 1;
    const reach = L.up + L.fore - 1e-3;
    for (let it = 0; !straight && it < 12; it++) {
      arm = ik(sh, wrist, L.up, L.fore, P.armPole(s));
      const fingers = handOf(arm.end.clone().sub(arm.mid).normalize());
      // Halfway to where this pass puts the wrist: the hand's turn follows the forearm, so a full
      // step can swing back and forth.
      wrist.lerp(grip.clone().addScaledVector(fingers, -depth).addScaledVector(palmOf(fingers), -off), it ? 0.5 : 1);
      if (wrist.distanceTo(sh) > reach) keep *= 0.8;
    }
    const a1 = arm.mid.clone().sub(sh);
    const a2 = arm.end.clone().sub(arm.mid);
    aim(rig, 'upperarm_' + k, a1, a1.clone().cross(arm.pole));
    const fingers = straight ? straight.fingers.clone() : handOf(a2.clone().normalize());
    const inward = twist(fingers);
    // What the hand holds lies along the knuckles, tipped in the palm's plane by `gripTilt`: a
    // dumbbell's handle (the left and right tip the same way, so the sign follows the side).
    const palmNormal = rig.palms[k].clone().applyQuaternion(frame(fingers, inward).multiply(handRest)).normalize();
    const handle = inward.clone().normalize();
    // The palm's normal, square to the handle.
    palmNormal.addScaledVector(handle, -palmNormal.dot(handle)).normalize();
    (P.held ??= {})[s] = handle.applyAxisAngle(palmNormal, -s * rad(P.gripTilt ?? 0));
    aim(rig, 'lowerarm_' + k, a2, twist(a2.clone().normalize()));
    aim(rig, 'hand_' + k, fingers, inward);
  }
}
