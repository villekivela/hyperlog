// The mannequin's rig: each bone is aimed at a target from its bind pose, with two-bone IK for the
// legs and arms, a grip solve that lays the bar across the palm, and fingers curled round it.
// Every function takes the rig built by `buildRig`, so nothing here keeps state of its own.
import * as THREE from 'three';

export const V = (x, y, z) => new THREE.Vector3(x, y, z);
export const rad = (d) => (d * Math.PI) / 180;
export const X = V(1, 0, 0);
export const UP = V(0, 1, 0);

export const wpos = (b) => b.getWorldPosition(new THREE.Vector3());
export const wquat = (b) => b.getWorldQuaternion(new THREE.Quaternion());

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
    gripAlong: palmLength * 0.8, gripOff: 0.03,
    trunk: shMid.y - hipMid.y, shW: Math.abs(rest.upperarm_l.p.x), ankleY: rest.foot_l.p.y,
    // How far the foot pitches down from the ankle to the ball, standing flat.
    footPitch: Math.atan2(-footRest.y, Math.hypot(footRest.x, footRest.z)),
    // How far the middle of the sole is in front of the ankle joint.
    midfoot: (heel + toe) / 2 - rest.foot_l.p.z,
  };
  const curls = [];
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
        const turn = new THREE.Quaternion().setFromAxisAngle(axis, rad(thumb ? THUMB[i] : deg));
        const local = rest[n].wq.clone().invert().multiply(turn).multiply(rest[n].wq);
        curls.push([bones[n], rest[n].lq.clone().multiply(local)]);
      });
    }
  }
  const rig = { root, meshes, bones, rest, dims, pelvisToHips: hipMid.clone().sub(rest.pelvis.p), curls, palms };
  // Where the shoulders' middle sits from the hips' with the spine straightened upright, as poses
  // straighten it: up the spine, and behind it. The bind pose's spine curves, so it isn't the same.
  for (const n of SPINE.slice(0, 4)) aim(rig, n, UP, X);
  root.updateMatrixWorld(true);
  const hips = wpos(bones.thigh_l).add(wpos(bones.thigh_r)).multiplyScalar(0.5);
  const shoulders = wpos(bones.upperarm_l).add(wpos(bones.upperarm_r)).multiplyScalar(0.5).sub(hips);
  Object.assign(dims, { trunkUp: shoulders.y, trunkBack: -shoulders.z });
  for (const [n, b] of Object.entries(bones)) b.quaternion.copy(rest[n].lq);
  root.updateMatrixWorld(true);
  return rig;
}

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
  return wrist.addScaledVector(fingers, L.gripAlong).addScaledVector(palm, L.gripOff);
}

/**
 * Poses the rig from a pose frame: the hip centre and trunk direction, the head's direction, the
 * ankles, the feet's way, knee and elbow poles, and where the hands hold.
 */
export function poseRig(rig, P) {
  const { bones, rest, dims: L } = rig;
  for (const [n, b] of Object.entries(bones)) b.quaternion.copy(rest[n].lq);
  for (const [b, q] of rig.curls) b.quaternion.copy(q);
  rig.root.updateMatrixWorld(true);
  // The trunk: the spine rigid along u, the pelvis placed so the hip joints land on hipC.
  const pelvisDelta = frame(P.u, X).multiply(frame(rest.pelvis.dir, X).invert());
  const pelvisWorld = P.hipC.clone().sub(rig.pelvisToHips.clone().applyQuaternion(pelvisDelta));
  bones.pelvis.position.copy(bones.pelvis.parent.worldToLocal(pelvisWorld));
  bones.pelvis.updateMatrixWorld(true);
  for (const n of ['pelvis', 'spine_01', 'spine_02', 'spine_03']) aim(rig, n, P.u, X);
  aim(rig, 'neck_01', P.headDir, X);
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
    const inward = acrossOf(P, s);
    const hr = rest['hand_' + k];
    const handRest = frame(hr.dir, hr.across).invert();
    let wrist = grip.clone();
    let arm;
    for (let it = 0; it < 4; it++) {
      arm = ik(sh, wrist, L.up, L.fore, P.armPole(s));
      const fingers = arm.end.clone().sub(arm.mid).normalize();
      const palm = rig.palms[k].clone().applyQuaternion(frame(fingers, inward).multiply(handRest));
      wrist = grip.clone().addScaledVector(fingers, -L.gripAlong).addScaledVector(palm, -L.gripOff);
    }
    const a1 = arm.mid.clone().sub(sh);
    const a2 = arm.end.clone().sub(arm.mid);
    aim(rig, 'upperarm_' + k, a1, a1.clone().cross(arm.pole));
    aim(rig, 'lowerarm_' + k, a2, inward);
    aim(rig, 'hand_' + k, a2, inward);
  }
}
