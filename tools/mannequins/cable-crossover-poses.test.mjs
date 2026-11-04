// The cable crossover's poses (#197): body-first, each cable from its pulley to the hand on every
// frame, the carriage at a hole refs/cable_crossover/ref.md gives for the exercise.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { BATCHES } from './review.mjs';
import { V, heldAt, wpos } from './rig.mjs';

/**
 * Each exercise's holes, from the ref's Adjustments: its band, lowest and highest. The ref's rules
 * come from the videos, and the library's note where there is one.
 */
const HOLES = {
  cable_crossover: [15, 16],
  cable_chest_press: [10, 10],
  low_to_high_cable_press: [4, 6],
  high_cable_lateral_raise: [4, 6],
  cable_y_raise: [1, 1],
  behind_the_back_cable_lateral_raise: [1, 1],
  cable_shrug_in: [2, 3],
  '1_arm_cable_press_around': [9, 9],
  '1_arm_katana_cable_triceps_extension': [1, 1],
};
/** Where a hole's cable leaves the lower wheel, from the ref: 350 mm up at the first, 132 mm pitch, 85 mm below the pin. */
const exitOf = (hole) => 0.35 + (hole - 1) * 0.132 - 0.085;
/** The two ends of a cable, in the world. */
const endsOf = (cable) => [-0.5, 0.5].map((y) => cable.localToWorld(V(0, y, 0)));
/** The cable hand `side` holds: its own tower's, or with `crossed` the far one's (the left tower first). */
const cableOf = (entry, kit, side) => kit.cables[(entry.kit.cable_crossover.crossed ? -side : side) > 0 ? 0 : 1];

const BATCH = BATCHES['cable-crossover'];

test('the batch is the exercises the ref gives a hole for', () => {
  assert.deepEqual([...BATCH].sort(), Object.keys(HOLES).sort());
});

for (const [id, [low, high]] of Object.entries(HOLES)) {
  test(`${id} stands on the crossover, its cable running from a pulley the ref gives to the hand on every frame`, async () => {
    const entry = prepare(id);
    assert.deepEqual(Object.keys(entry.kit), ['cable_crossover']);
    const stage = await buildStage(entry);
    const checks = checkStage(entry, stage);
    for (const c of CHECKS) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
    const kit = stage.kit.kits.cable_crossover;
    const { position = 10, hands = [1, -1] } = entry.kit.cable_crossover;
    const holes = Array.isArray(position) ? position : [position, position];
    // The band is for the cable a hand holds: the other tower may stand where a free hand braces.
    for (const side of hands) {
      const h = holes[kit.cables.indexOf(cableOf(entry, kit, side))];
      assert.ok(h >= low && h <= high, `hole ${h}, not ${low} to ${high}`);
    }
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      stage.scene.updateMatrixWorld(true);
      for (const side of hands) {
        const { cable, hook } = cableOf(entry, kit, side);
        const miss = Math.min(...endsOf(cable).map((e) => Math.abs(e.y - exitOf(holes[kit.cables.findIndex((c) => c.cable === cable)]))));
        assert.ok(miss < 1e-3, `frame ${f}: the cable leaves ${miss.toFixed(3)} m off the hole's height`);
        const held = heldAt(stage.rig, side > 0 ? 'l' : 'r');
        assert.ok(hook().distanceTo(held) < 0.2, `frame ${f}: the cable ends ${hook().distanceTo(held).toFixed(3)} m from the hand`);
      }
    }
  });
}

/** The body's bones a cable may not pass through: the arms lie along it near the hand, as a flye's do. */
const BODY = /^(pelvis|spine|neck|Head|clavicle|thigh|calf|foot)/;

/** The bones of the body `cable` passes through now, by the triangles its line crosses. */
function throughTheBody(stage, cable) {
  const [a, b] = endsOf(cable);
  const ray = new THREE.Ray(a, b.clone().sub(a).normalize());
  const hit = new THREE.Vector3();
  const bones = new Set();
  stage.scene.traverse((m) => {
    if (!m.isSkinnedMesh) return;
    const { index, attributes: { position, skinIndex, skinWeight } } = m.geometry;
    const at = [];
    const point = (i) => (at[i] ??= m.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(m.matrixWorld));
    for (let t = 0; t < index.count; t += 3) {
      const [p, q, r] = [0, 1, 2].map((k) => index.getX(t + k));
      if (!ray.intersectTriangle(point(p), point(q), point(r), false, hit) || hit.distanceTo(a) > a.distanceTo(b)) continue;
      const main = [0, 1, 2, 3].reduce((best, n) => (skinWeight.getComponent(p, n) > skinWeight.getComponent(p, best) ? n : best), 0);
      const { name } = m.skeleton.bones[skinIndex.getComponent(p, main)];
      if (BODY.test(name)) bones.add(name);
    }
  });
  return [...bones];
}

// The wires aren't solid, so no other check stops a cable running through the body.
for (const id of Object.keys(HOLES)) {
  test(`${id}'s cable runs clear of the body on every frame`, async () => {
    const entry = prepare(id);
    const stage = await buildStage(entry);
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      stage.scene.updateMatrixWorld(true);
      for (const { cable } of stage.kit.kits.cable_crossover.cables) assert.deepEqual(throughTheBody(stage, cable), [], `frame ${f}`);
    }
  });
}

/** Where the cable runs past the middle of the body, against the hips' z. */
function pastTheHips(stage, cable) {
  const [a, b] = endsOf(cable);
  const t = a.x / (a.x - b.x);
  return t > 0 && t < 1 ? a.clone().lerp(b, t).z - wpos(stage.rig.bones.pelvis).z : null;
}

// Which side it passes is what these raises are.
for (const [id, side] of [['high_cable_lateral_raise', 1], ['cable_y_raise', 1], ['behind_the_back_cable_lateral_raise', -1]]) {
  test(`${id}'s cable passes ${side > 0 ? 'in front of' : 'behind'} the hips`, async () => {
    const entry = prepare(id);
    const stage = await buildStage(entry);
    const { cable } = cableOf(entry, stage.kit.kits.cable_crossover, 1);
    const past = [];
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      stage.scene.updateMatrixWorld(true);
      past.push(pastTheHips(stage, cable));
    }
    const met = past.filter((z) => z !== null);
    assert.ok(met.length, 'the cable never passes the middle of the body');
    for (const z of met) assert.ok(z * side > 0, `the cable ${z.toFixed(3)} m ahead of the hips`);
  });
}

// The owner, #197: 'the wrist rotates in the middle'. A smooth twist is no jump, so the clips' test
// passes it, but the eye catches a hand rolling fast about the forearm. The shipped lateral raise's
// fastest is 5°.
const ROLL = 7;

/** How far hand `k` rolls about its forearm from frame to frame, in degrees. */
function rollsOf(stage, k) {
  const { bones } = stage.rig;
  const steps = [];
  let last;
  for (let f = 0; f < FRAMES; f++) {
    stage.pose(f / (FRAMES - 1));
    stage.scene.updateMatrixWorld(true);
    const fore = wpos(bones[`hand_${k}`]).sub(wpos(bones[`lowerarm_${k}`])).normalize();
    const across = wpos(bones[`index_01_${k}`]).sub(wpos(bones[`pinky_01_${k}`]));
    across.addScaledVector(fore, -across.dot(fore)).normalize();
    if (last) {
      const carried = last.across.clone().applyQuaternion(new THREE.Quaternion().setFromUnitVectors(last.fore, fore));
      steps.push((Math.atan2(carried.clone().cross(across).dot(fore), carried.dot(across)) * 180) / Math.PI);
    }
    last = { fore, across };
  }
  return steps;
}

for (const id of Object.keys(HOLES)) {
  test(`${id}'s hand turns steadily through the rep`, async () => {
    const entry = prepare(id);
    const stage = await buildStage(entry);
    const { hands = [1, -1] } = entry.kit.cable_crossover;
    for (const side of hands) {
      const steps = rollsOf(stage, side > 0 ? 'l' : 'r').map(Math.abs);
      const worst = Math.max(...steps);
      assert.ok(worst <= ROLL, `the hand rolls ${worst.toFixed(1)}° at frame ${steps.indexOf(worst) + 1}`);
    }
  });
}

// The library's notes, the exercises' own words.
const posed = async (id, d) => {
  const stage = await buildStage(prepare(id));
  const frame = stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  return { stage, frame, hand: (k) => heldAt(stage.rig, k), at: (bone) => wpos(stage.rig.bones[bone]) };
};

test('the low-to-high press is one arm at a time, up and across to eye level in front of the opposite shoulder', async () => {
  assert.deepEqual(prepare('low_to_high_cable_press').kit.cable_crossover.hands, [1]);
  const { frame, hand, at } = await posed('low_to_high_cable_press', 0);
  assert.equal(frame.resting, -1, 'the right hand rests');
  const [left, shoulder, head] = [hand('l'), at('upperarm_r'), at('Head')];
  assert.ok(Math.abs(left.x - shoulder.x) < 0.1, `the hand at x ${left.x.toFixed(3)}, the opposite shoulder at ${shoulder.x.toFixed(3)}`);
  assert.ok(Math.abs(left.y - head.y) < 0.12, `the hand at ${left.y.toFixed(3)} m, the head at ${head.y.toFixed(3)} m`);
});

test('the chest press holds neutral handles and presses until the hands nearly meet', async () => {
  assert.equal(prepare('cable_chest_press').grip.rotate, 90, 'neutral');
  const { hand } = await posed('cable_chest_press', 0);
  const apart = hand('l').distanceTo(hand('r'));
  assert.ok(apart < 0.14, `the hands ${apart.toFixed(3)} m apart`);
});

test('the crossover steps forward of the pulleys and brings the handles together in front of the hips', async () => {
  const { stage, hand, at } = await posed('cable_crossover', 0);
  const pulley = Math.min(...stage.kit.kits.cable_crossover.cables.flatMap(({ cable }) => endsOf(cable).map((e) => e.z)));
  assert.ok(at('pelvis').z > pulley + 0.05, `the hips at z ${at('pelvis').z.toFixed(3)}, the pulley at ${pulley.toFixed(3)}`);
  assert.ok(hand('l').distanceTo(hand('r')) < 0.14, 'the handles together');
  assert.ok(hand('l').z > at('pelvis').z + 0.15, 'in front of the hips');
});

/**
 * The gap from the right palm's skin to the thigh's, along the palm's normal, on a ray from the
 * middle of the palm: null when it meets no thigh, which is a palm that faces away. Below 0 it sinks in.
 */
function palmToThigh(stage) {
  const { bones } = stage.rig;
  const [wrist, knuckle] = [wpos(bones.hand_r), wpos(bones.middle_01_r)];
  const across = wpos(bones.index_01_r).sub(wpos(bones.pinky_01_r));
  const normal = across.cross(knuckle.clone().sub(wrist)).normalize();
  const ray = new THREE.Ray(wrist.clone().lerp(knuckle, 0.6), normal);
  const hit = new THREE.Vector3();
  const at = { hand_r: [], thigh_r: [] };
  stage.scene.traverse((m) => {
    if (!m.isSkinnedMesh) return;
    const { index, attributes: { skinIndex, skinWeight } } = m.geometry;
    const points = [];
    const point = (i) => (points[i] ??= m.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(m.matrixWorld));
    for (let t = 0; t < index.count; t += 3) {
      const [p, q, r] = [0, 1, 2].map((k) => index.getX(t + k));
      const main = [0, 1, 2, 3].reduce((best, n) => (skinWeight.getComponent(p, n) > skinWeight.getComponent(p, best) ? n : best), 0);
      const { name } = m.skeleton.bones[skinIndex.getComponent(p, main)];
      if (at[name] && ray.intersectTriangle(point(p), point(q), point(r), false, hit)) at[name].push(hit.distanceTo(ray.origin));
    }
  });
  return at.thigh_r.length ? Math.min(...at.thigh_r) - Math.max(0, ...at.hand_r) : null;
}

// The owner, #197: the free palm lies on the thigh, not its edge and not in the air.
test('the press-around lays the free palm on the thigh all through the rep, the other hand pressing past the middle', async () => {
  const { stage, hand } = await posed('1_arm_cable_press_around', 0);
  assert.ok(hand('l').x < -0.15, `the working hand at x ${hand('l').x.toFixed(3)}`);
  for (const d of [0, 0.25, 0.5, 0.75, 1]) {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    const gap = palmToThigh(stage);
    assert.ok(gap !== null, `the palm faces away from the thigh, ${d} of the way down`);
    assert.ok(Math.abs(gap) < 0.01, `the palm ${(gap * 100).toFixed(1)} cm off the thigh, ${d} of the way down`);
  }
});

// The owner, #197: the torso and head turn with the press, on a firm stance.
test('the press-around turns the trunk and head into the press, on a wide stance', async () => {
  const turn = (a, b) => (Math.atan2(a.z - b.z, a.x - b.x) * 180) / Math.PI;
  const { at, frame } = await posed('1_arm_cable_press_around', 0);
  const squeeze = turn(at('upperarm_l'), at('upperarm_r'));
  assert.ok(squeeze > 15, `the shoulders turned ${squeeze.toFixed(1)}° toward the press`);
  const bottom = await posed('1_arm_cable_press_around', 1);
  const stretch = turn(bottom.at('upperarm_l'), bottom.at('upperarm_r'));
  assert.ok(stretch < -5, `the shoulders turned ${stretch.toFixed(1)}° at the stretch`);
  const [right, left] = frame.ankles;
  assert.ok(left.x - right.x >= 0.7, `the feet ${(left.x - right.x).toFixed(2)} m apart`);
  assert.ok(Math.abs(left.z - right.z) > 0.1, 'one foot ahead');
});

// The owner, #197: it is a triceps extension, so the elbow stays and the forearm swings.
test('the katana keeps its elbow in place while the forearm extends', async () => {
  const stage = await buildStage(prepare('1_arm_katana_cable_triceps_extension'));
  const at = (d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return ['upperarm_l', 'lowerarm_l', 'hand_l'].map((b) => wpos(stage.rig.bones[b]));
  };
  const bendAt = ([shoulder, elbow, hand]) => 180 - (shoulder.sub(elbow).angleTo(hand.sub(elbow)) * 180) / Math.PI;
  const [, start] = at(0);
  for (let f = 1; f < FRAMES; f++) {
    const [, elbow] = at(f / (FRAMES - 1));
    assert.ok(elbow.distanceTo(start) < 0.03, `the elbow ${(elbow.distanceTo(start) * 100).toFixed(1)} cm from where it started, on frame ${f}`);
  }
  const [top, bottom] = [bendAt(at(0)), bendAt(at(1))];
  assert.ok(bottom - top > 70, `the elbow bends from ${top.toFixed(0)}° to ${bottom.toFixed(0)}°`);
});

// The owner, #197: the toes point the way the knees do, and the knees bend well.
test('the press-around\'s toes point where its knees do, both knees bent', async () => {
  const { at } = await posed('1_arm_cable_press_around', 0);
  const heading = (from, to) => { const v = to.sub(from); return (Math.atan2(v.x, v.z) * 180) / Math.PI; };
  for (const k of ['l', 'r']) {
    const [toes, thigh] = [heading(at(`foot_${k}`), at(`ball_${k}`)), heading(at(`thigh_${k}`), at(`calf_${k}`))];
    assert.ok(Math.abs(toes - thigh) < 20, `the ${k} toes at ${toes.toFixed(0)}°, the knee at ${thigh.toFixed(0)}°`);
    const knee = at(`calf_${k}`);
    const bend = 180 - (at(`thigh_${k}`).sub(knee).angleTo(at(`foot_${k}`).sub(knee)) * 180) / Math.PI;
    assert.ok(bend > 40, `the ${k} knee bent ${bend.toFixed(0)}°`);
  }
});

test('the high lateral raise lets the hand go slightly past the midline at the bottom', async () => {
  const { hand } = await posed('high_cable_lateral_raise', 1);
  assert.ok(hand('l').x < -0.02 && hand('l').x > -0.15, `the hand at x ${hand('l').x.toFixed(3)}`);
});

test('the katana takes a low cable from the side, across the body, and extends overhead', async () => {
  const entry = prepare('1_arm_katana_cable_triceps_extension');
  assert.equal(entry.kit.cable_crossover.crossed, true);
  const { hand, at } = await posed('1_arm_katana_cable_triceps_extension', 0);
  assert.ok(hand('l').y > at('Head').y + 0.15, `the hand at ${hand('l').y.toFixed(3)} m, the head at ${at('Head').y.toFixed(3)} m`);
});
