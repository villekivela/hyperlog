// The automatic checks, run on every frame of a pose before it bakes (README, Checks):
//
// - hands: each hand holds its grip, the point across the palm within 1.5 cm of it, and no finger
//   folds into the next (their joints at least 1.3 cm apart);
// - feet: each foot is planted where the pose puts it, flat on the floor or the plate it stands
//   on, ankle and ball at their standing heights within 2 cm; a body held up by its hands keeps
//   its feet at least 2 cm off the floor;
// - equipment: no part of the body is more than 2 cm inside a solid part of the kit (only the
//   hands close round what they hold), and never through a thin one, nor more than 1.5 cm into the
//   floor; every cable ends within 1 cm of its handle's hook, and stays 2 cm clear of the head and
//   the neck;
// - joints: knees and elbows bend no further than 155°, knees point within 60° of the hips' front,
//   hips flex no more than 140° or extend past 30°, the head turns within 60° of the trunk, the
//   ankle keeps between 55° and 160° from shin to foot, and a wrist bends no more than 5° off the
//   forearm's line unless the pose sets its wrists (an open hand, a hand turned by a bone offset,
//   or `grip.level`, counts as set), and no forearm or hand is more than 1 cm inside the head or
//   the neck;
// - trace: each angle the pose has a trace for is within 10° of it;
// - contact: each hand on a bar or a handle meets it (#162): every finger and the thumb within
//   2 mm of its surface (a wrapped thumb on a bar under 40 mm may rest on the fingers instead), and
//   none more than 2 mm inside it; the palm, the ball of the thumb with it, within 3 mm either way;
// - linkage: each joint stays within its limits, each contact on a jointed body stays on the path
//   its joint gives it (a handle on its arc) within 1 mm, each hand within 1.5 cm of the handle it
//   holds, and each pad, roller or plate within 2 cm of the limb it bears on.
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { sample } from './motions.mjs';
import { between, gripTarget, heldAt, sideOf, turnOf, wpos } from './rig.mjs';
import { moveOf } from './recipe.mjs';
import { ANGLES, deg, sideAngles } from './angles.mjs';
import { contactOf, holdsNothing, partUnder } from './grip.mjs';

export const CHECKS = ['hands', 'feet', 'equipment', 'joints', 'trace', 'contact', 'linkage'];

const LIMITS = {
  grip: 0.015, fingers: 0.013, foot: 0.02, offFloor: 0.02, inside: 0.02, floor: 0.015, wrist: 5, cable: 0.01, rack: 0.015,
  bend: 155, kneeTurn: 60, hipFlex: 140, hipExtend: 30, head: 60, ankle: [55, 160], trace: 10,
  contact: 0.002,
  // The palm's skin is measured on a grid over large triangles, so it is held to a little more.
  palm: 0.003,
  // A machine's contacts: how far a pad may stand off its limb, and a point off its joint's path.
  pad: 0.02, arc: 0.001, neck: 0.01, wire: 0.02,
};
const angle = (a, b) => deg(a.angleTo(b));
const FINGERS = /^(hand|index|middle|ring|pinky|thumb)_/;
/** What can reach the head and the neck, and the bones that are them. */
const FOREARM = /^(lowerarm|hand|index|middle|ring|pinky|thumb)_/;
const NECK = /^(neck_\d+|Head)$/;

/** Each solid part of the kit, with what it takes to test a point against it. */
function solids(kit) {
  const out = [];
  kit.group.traverse((m) => {
    if (!m.isMesh || !m.userData.solid) return;
    m.geometry.computeBoundingBox();
    const box = m.geometry.boundingBox;
    const half = box.getSize(new THREE.Vector3()).multiplyScalar(0.5);
    const centre = box.getCenter(new THREE.Vector3());
    const round = m.geometry.type === 'CylinderGeometry';
    // A cylinder's axis is the extent unlike the other two.
    const h = half.toArray();
    const axis = round ? [0, 1, 2].find((i) => Math.abs(h[(i + 1) % 3] - h[(i + 2) % 3]) < 1e-6) : null;
    out.push({ mesh: m, half: h, centre, round, axis, held: m.userData.held });
  });
  return out;
}

/** How deep a local point is inside a solid part, in its own units: negative when outside. */
function depthIn(s, p) {
  const q = p.clone().sub(s.centre).toArray();
  if (!s.round) return Math.min(...q.map((v, i) => s.half[i] - Math.abs(v)));
  const [a, b] = [(s.axis + 1) % 3, (s.axis + 2) % 3];
  return Math.min(s.half[a] - Math.hypot(q[a], q[b]), s.half[s.axis] - Math.abs(q[s.axis]));
}

/** How far a local point is from a solid part's surface, in its own units: 0 inside it. */
function gapTo(s, p) {
  const q = p.clone().sub(s.centre).toArray();
  if (!s.round) return Math.hypot(...q.map((v, i) => Math.max(0, Math.abs(v) - s.half[i])));
  const [a, b] = [(s.axis + 1) % 3, (s.axis + 2) % 3];
  return Math.hypot(Math.max(0, Math.hypot(q[a], q[b]) - s.half[a]), Math.max(0, Math.abs(q[s.axis]) - s.half[s.axis]));
}

/** The solid part nearest world point `p`, which a contact's point lies in or on. */
function partAt(parts, p) {
  const gap = (s) => gapTo(s, s.mesh.worldToLocal(p.clone()));
  return parts.reduce((a, b) => (gap(b) < gap(a) ? b : a));
}

/** The bones of each landmark a pad bears on. */
const LIMBS = { shins: /^calf_/, thighs: /^thigh_/, knees: /^(thigh|calf)_/, feet: /^(foot|ball)_/, shoulders: /^(clavicle|upperarm)_/, chest: /^spine_0[23]$/, back: /^spine_0[23]$/, upperArms: /^upperarm_/ };

/**
 * The head and the neck, each as the ellipsoid inside its vertices' box in its own bone's frame, so
 * it turns with the bone. `skinned` is the vertices posed now.
 */
function headAndNeck(rig, vertices, skinned) {
  const boxes = {};
  vertices.forEach((b, i) => {
    if (!NECK.test(b.bone)) return;
    const local = rig.bones[b.bone].worldToLocal(skinned[i].clone());
    (boxes[b.bone] ??= new THREE.Box3()).expandByPoint(local);
  });
  return Object.entries(boxes).map(([name, box]) => ({
    name, bone: rig.bones[name], centre: box.getCenter(new THREE.Vector3()), half: box.getSize(new THREE.Vector3()).multiplyScalar(0.5),
  }));
}

/** How deep world point `p` is inside ellipsoid `e`: negative when outside. */
function depthInside(e, p) {
  const q = e.bone.worldToLocal(p.clone()).sub(e.centre).divide(e.half);
  return (1 - q.length()) * Math.min(e.half.x, e.half.y, e.half.z);
}

/** The body's vertices, each with the bone that moves it most and whether that's a hand's. */
export function bodyVertices(rig) {
  const out = [];
  for (const m of rig.meshes) {
    const { skinIndex, skinWeight, position } = m.geometry.attributes;
    for (let i = 0; i < position.count; i++) {
      let best = 0;
      for (let j = 1; j < 4; j++) if (skinWeight.getComponent(i, j) > skinWeight.getComponent(i, best)) best = j;
      const bone = m.skeleton.bones[skinIndex.getComponent(i, best)].name;
      out.push({ mesh: m, i, bone, hand: FINGERS.test(bone) });
    }
  }
  return out;
}

/** Side-on angles of the posed body, both sides averaged, as `trace` measures the lifter's. */
function posedAngles(rig) {
  const at = (n) => { const p = wpos(rig.bones[n]); return [p.z, p.y]; };
  const [l, r] = ['l', 'r'].map((k) => sideAngles({
    ankle: at('foot_' + k), knee: at('calf_' + k), hip: at('thigh_' + k),
    shoulder: at('upperarm_' + k), elbow: at('lowerarm_' + k), wrist: at('hand_' + k),
  }));
  return Object.fromEntries(ANGLES.map((k) => [k, (l[k] + r[k]) / 2]));
}

/** How far bone `name` has twisted about its own length since the bind pose, against the pelvis, in degrees. */
function twist(rig, name) {
  const rel = turnOf(rig, 'pelvis').invert().multiply(turnOf(rig, name));
  const axis = rig.rest[name].dir.clone().normalize();
  const along = rel.x * axis.x + rel.y * axis.y + rel.z * axis.z;
  let t = deg(2 * Math.atan2(along, rel.w));
  if (t > 180) t -= 360;
  if (t < -180) t += 360;
  return t;
}

/** The worst joint of a posed body against its range, as how far past it: 0 or less is within. */
function jointsPast(rig) {
  const at = (n) => wpos(rig.bones[n]);
  const hips = between(rig, 'thigh_l', 'thigh_r');
  const shoulders = between(rig, 'upperarm_l', 'upperarm_r');
  const u = shoulders.clone().sub(hips).normalize();
  const front = new THREE.Vector3(0, 0, 1).applyQuaternion(turnOf(rig, 'pelvis'));
  const f = front.clone().addScaledVector(u, -front.dot(u)).normalize();
  const past = [];
  for (const k of ['l', 'r']) {
    const thigh = at('calf_' + k).sub(at('thigh_' + k));
    const shin = at('foot_' + k).sub(at('calf_' + k));
    past.push([`knee ${k} bends ${angle(thigh, shin).toFixed(0)}°`, angle(thigh, shin) - LIMITS.bend]);
    const upper = at('lowerarm_' + k).sub(at('upperarm_' + k));
    const fore = at('hand_' + k).sub(at('lowerarm_' + k));
    past.push([`elbow ${k} bends ${angle(upper, fore).toFixed(0)}°`, angle(upper, fore) - LIMITS.bend]);
    // The thigh's twist about itself against the hips: how far the knee turns in or out.
    const turn = twist(rig, 'thigh_' + k);
    past.push([`knee ${k} turns ${turn.toFixed(0)}° from the hips' front`, Math.abs(turn) - LIMITS.kneeTurn]);
    const flex = deg(Math.atan2(thigh.dot(f), -thigh.dot(u)));
    past.push([`hip ${k} flexes ${flex.toFixed(0)}°`, Math.max(flex - LIMITS.hipFlex, -flex - LIMITS.hipExtend)]);
    const foot = at('ball_' + k).sub(at('foot_' + k));
    const ankle = angle(shin.clone().negate(), foot);
    past.push([`ankle ${k} at ${ankle.toFixed(0)}°`, Math.max(LIMITS.ankle[0] - ankle, ankle - LIMITS.ankle[1])]);
  }
  const head = angle(at('Head').sub(at('neck_01')), u);
  past.push([`head turns ${head.toFixed(0)}° from the trunk`, head - LIMITS.head]);
  return past.reduce((a, b) => (b[1] > a[1] ? b : a));
}

/**
 * How the hand on side `k` meets what it holds, posed at frame `P` (#162): for each digit, and for
 * the deepest point of the hand inside its bar, how far past the 2 mm the contact check allows
 * (`past`, 0 or less is within), where (`at`), and what that is in words. None for a hand that
 * holds nothing. The studio's hand close-up marks the ones past.
 */
export function contactsOf({ rig, kit }, P, k) {
  const s = sideOf(rig, k);
  if (holdsNothing(P, s)) return [];
  const part = partUnder(kit, gripTarget(P, s));
  if (!part) return [];
  const { digits, inside } = contactOf(rig.skin, k, part, P.hold?.thumb);
  const mm = (x) => (x * 1000).toFixed(1);
  const name = part.mesh.name || 'its bar';
  return [
    ...Object.entries(digits).map(([digit, { gap, at }]) => ({
      past: gap - (digit === 'palm' ? LIMITS.palm : LIMITS.contact), at, found: `the ${k} ${digit === 'thumb' || digit === 'palm' ? digit : `${digit} finger`} ${mm(gap)} mm off ${name}`,
    })),
    { past: inside.depth - (/^(hand|thumb_01)_/.test(inside.bone) ? LIMITS.palm : LIMITS.contact), at: inside.at, found: `${inside.bone} ${mm(inside.depth)} mm into ${name}` },
  ];
}

/**
 * A check of the contacts of every kit on a joint or a frame, on one posed frame: `note(past, found)`
 * for each. `started` keeps where each contact began, so each is held to its joint's path from there.
 */
function checkLinkage(kits, { rig, parts, vertices, skinned, started, limbs }, note) {
  for (const [name, kit] of Object.entries(kits)) {
    for (const [body, joint] of Object.entries(kit.joints)) {
      const [min, max] = joint.limits;
      const past = Math.max(min - joint.value, joint.value - max);
      const shown = joint.type === 'revolute' ? `${deg(joint.value).toFixed(1)}°` : `${joint.value.toFixed(3)} m`;
      note(past - 1e-6, `${body} at ${shown}, ${past > 1e-6 ? 'past' : 'within'} its limits`);
    }
    for (const c of kit.contacts) {
      const joint = kit.joints[c.body];
      if (c.body !== 'frame' && !joint) continue;
      const key = `${name}.${c.point}`;
      started[key] ??= { at: c.at.map((p) => p.clone()), value: joint?.value };
      c.at.forEach((p, i) => {
        const want = started[key].at[i].clone();
        if (joint) want.applyMatrix4(moveOf(joint, joint.value - started[key].value));
        note(p.distanceTo(want) - LIMITS.arc, `${c.point} ${(p.distanceTo(want) * 1000).toFixed(1)} mm off their arc`);
      });
      for (const k of ['l', 'r']) {
        if (c.landmark === 'hands') {
          // A grip a free hand may steady itself on holds no hand to it, and one arm's grip the other hand.
          if (c.optional || (c.hand && c.hand !== k)) continue;
          const miss = Math.min(...c.at.map((p) => p.distanceTo(heldAt(rig, k))));
          note(miss - LIMITS.grip, `the ${k} hand ${(miss * 100).toFixed(1)} cm off the ${c.point}`);
          continue;
        }
        // One arm's pad bears on that arm alone.
        if (c.hand && c.hand !== k) continue;
        // The limb on this side: a spine's bones, on neither, count for both.
        const limb = limbs[`${c.landmark}_${k}`] ??= vertices.flatMap((b, i) => (
          LIMBS[c.landmark].test(b.bone) && (!/_[lr]$/.test(b.bone) || b.bone.endsWith(`_${k}`)) ? [i] : []));
        let gap = Infinity;
        for (const s of c.at.map((p) => partAt(parts, p))) {
          const inverse = s.mesh.matrixWorld.clone().invert();
          for (const i of limb) gap = Math.min(gap, gapTo(s, skinned[i].clone().applyMatrix4(inverse)));
        }
        note(gap - LIMITS.pad, `${c.point} ${(gap * 100).toFixed(1)} cm off the ${c.landmark}`);
      }
    }
  }
}

/**
 * Checks a pose on every frame of its lowering. Each check reports whether it passed, its worst
 * frame, and what it found there.
 */
export async function checkPose(id, { poses, angle, grip } = {}) {
  const entry = prepare(id, poses, angle, grip);
  return { id, checks: checkStage(entry, await buildStage(entry)) };
}

/**
 * The same checks over a built stage (`buildStage`), on `frames` frames of the lowering. The
 * studio runs them live on what it poses.
 */
export function checkStage(entry, { rig, kit, pose }, frames = FRAMES) {
  const parts = solids(kit);
  const vertices = bodyVertices(rig);
  const restAnkle = rig.rest.foot_l.p.y;
  const restBall = rig.rest.ball_l.p.y;
  let neck = null;
  const worst = Object.fromEntries(CHECKS.map((c) => [c, { past: -Infinity, frame: null, found: 'nothing to check' }]));
  const note = (check, past, frame, found) => {
    if (past > worst[check].past) worst[check] = { past, frame, found };
  };
  const v = new THREE.Vector3();
  const started = {};
  const limbs = {};
  for (let f = 0; f < frames; f++) {
    const P = pose(f / (frames - 1));
    for (const k of ['l', 'r']) {
      const s = sideOf(rig, k);
      const miss = heldAt(rig, k).distanceTo(gripTarget(P, s));
      note('hands', miss - LIMITS.grip, f, `the ${k} hand ${(miss * 100).toFixed(1)} cm from its grip`);
      const order = ['index', 'middle', 'ring', 'pinky'];
      for (let i = 0; i < 3; i++) {
        for (const j of ['02', '03', '04_leaf']) {
          const gap = wpos(rig.bones[`${order[i]}_${j}_${k}`]).distanceTo(wpos(rig.bones[`${order[i + 1]}_${j}_${k}`]));
          note('hands', LIMITS.fingers - gap, f, `the ${k} ${order[i]} and ${order[i + 1]} fingers ${(gap * 100).toFixed(1)} cm apart`);
        }
      }
      const target = P.ankles[s < 0 ? 0 : 1];
      const plane = P.support(s);
      const height = (p) => p.clone().sub(plane.point).dot(plane.normal);
      const ankle = wpos(rig.bones['foot_' + k]);
      const ball = wpos(rig.bones['ball_' + k]);
      // A foot on nothing, lifted or lying back from a knee, only has its place to keep.
      const off = plane
        ? Math.max(ankle.distanceTo(target), Math.abs(height(ankle) - restAnkle), Math.abs(height(ball) - restBall))
        : ankle.distanceTo(target);
      note('feet', off - LIMITS.foot, f, `the ${k} foot ${(off * 100).toFixed(1)} cm off where it stands`);
    }
    const skinned = vertices.map((b) => b.mesh.getVertexPosition(b.i, new THREE.Vector3()).applyMatrix4(b.mesh.matrixWorld));
    neck ??= headAndNeck(rig, vertices, skinned);
    // The floor is solid too: the soles may press into it a little, nothing more.
    vertices.forEach((b, i) => {
      if (skinned[i].y < 0) note('equipment', -skinned[i].y - LIMITS.floor, f, `${b.bone} ${(-skinned[i].y * 100).toFixed(1)} cm into the floor`);
    });
    // A body held up by its hands bears on nothing: its feet stay clear of the floor.
    if (P.offFloor) {
      vertices.forEach((b, i) => {
        if (LIMBS.feet.test(b.bone)) note('feet', LIMITS.offFloor - skinned[i].y, f, `${b.bone} ${(skinned[i].y * 100).toFixed(1)} cm off the floor`);
      });
    }
    for (const s of parts) {
      const near = new THREE.Box3().setFromObject(s.mesh).expandByScalar(0.01);
      const inverse = s.mesh.matrixWorld.clone().invert();
      // Thin parts, like a bar, count from any way in; others from 2 cm in. A bar racked on open
      // hands (a cross-arm front squat) rests on the arms, up to its own thickness.
      const allowed = Math.min(LIMITS.inside, 0.5 * Math.min(...s.half));
      const racked = P.openHands && s.held ? LIMITS.rack : 0;
      vertices.forEach((b, i) => {
        if ((s.held && b.hand) || !near.containsPoint(skinned[i])) return;
        const depth = depthIn(s, v.copy(skinned[i]).applyMatrix4(inverse));
        const allow = racked && /^(upperarm|lowerarm)_/.test(b.bone) ? Math.max(allowed, racked) : allowed;
        if (depth > 0) note('equipment', depth - allow, f, `${b.bone} ${(depth * 100).toFixed(1)} cm into ${s.mesh.name}`);
      });
    }
    for (const { cable, hook } of kit.cables ?? []) {
      const at = hook();
      const miss = Math.min(...[-0.5, 0.5].map((y) => cable.localToWorld(new THREE.Vector3(0, y, 0)).distanceTo(at)));
      note('equipment', miss - LIMITS.cable, f, `${cable.name} ends ${(miss * 100).toFixed(1)} cm off its handle`);
      // The wires aren't solid, so the parts above don't stop one running through the head.
      const wire = new THREE.Line3(...[-0.5, 0.5].map((y) => cable.localToWorld(new THREE.Vector3(0, y, 0))));
      vertices.forEach((b, i) => {
        if (!NECK.test(b.bone)) return;
        const gap = wire.closestPointToPoint(skinned[i], true, v).distanceTo(skinned[i]);
        note('equipment', LIMITS.wire - gap, f, `${cable.name} ${(gap * 100).toFixed(1)} cm from the ${b.bone}`);
      });
    }
    vertices.forEach((b, i) => {
      if (!FOREARM.test(b.bone)) return;
      for (const e of neck) {
        const depth = depthInside(e, skinned[i]);
        note('joints', depth - LIMITS.neck, f, `${b.bone} ${(depth * 100).toFixed(1)} cm into the ${e.name}`);
      }
    });
    const [joint, past] = jointsPast(rig);
    note('joints', past, f, joint);
    // A wrist the pose doesn't set stays straight (owner, #127); a hand the entry turns (a bone
    // offset, #164) is set.
    if (!entry.top?.wrist && !entry.bottom?.wrist && !P.openHands && !P.level) {
      for (const k of ['l', 'r']) {
        if (entry.top?.offsets?.['hand_' + k] || entry.bottom?.offsets?.['hand_' + k]) continue;
        const fore = wpos(rig.bones['hand_' + k]).sub(wpos(rig.bones['lowerarm_' + k]));
        const bend = deg(wpos(rig.bones['middle_01_' + k]).sub(wpos(rig.bones['hand_' + k])).angleTo(fore));
        note('joints', bend - LIMITS.wrist, f, `wrist ${k} bends ${bend.toFixed(0)}°`);
      }
    }
    for (const k of ['l', 'r']) {
      for (const c of contactsOf({ rig, kit }, P, k)) note('contact', c.past, f, c.found);
    }
    checkLinkage(kit.kits, { rig, parts, vertices, skinned, started, limbs }, (past, found) => note('linkage', past, f, found));
    if (entry.trace) {
      const measured = posedAngles(rig);
      for (const [k, samples] of Object.entries(entry.trace.angles)) {
        const traced = sample(samples, f / (frames - 1));
        const off = Math.abs(measured[k] - traced);
        note('trace', off - LIMITS.trace, f, `${k} ${measured[k].toFixed(0)}° against the trace's ${traced.toFixed(0)}°`);
      }
    }
  }
  if (worst.equipment.frame === null) worst.equipment = { past: -Infinity, frame: null, found: 'nothing inside the kit' };
  const checks = Object.fromEntries(CHECKS.map((c) => [c, { ok: !(worst[c].past > 0), frame: worst[c].frame, found: worst[c].found }]));
  return checks;
}
