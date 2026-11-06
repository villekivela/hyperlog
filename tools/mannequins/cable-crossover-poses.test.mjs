// The cable crossover's poses (#197): body-first, each cable from its pulley to the hand on every
// frame, the carriage at a hole refs/cable_crossover/ref.md gives for the exercise.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { CHECKS, bodyVertices, checkStage } from './checks.mjs';
import { BATCHES } from './review.mjs';
import { V, heldAt, wpos } from './rig.mjs';

/**
 * Each exercise's holes, from the ref's Adjustments: its band, lowest and highest. The ref's rules
 * come from the videos, and the library's note where there is one.
 */
const HOLES = {
  cable_crossover: [12, 12],
  cable_chest_press: [10, 10],
  low_to_high_cable_press: [4, 6],
  high_cable_lateral_raise: [4, 6],
  cable_y_raise: [1, 1],
  behind_the_back_cable_lateral_raise: [1, 1],
  cable_shrug_in: [2, 3],
  '1_arm_cable_press_around': [9, 9],
  '1_arm_katana_cable_triceps_extension': [6, 6],
};
/**
 * The second batch's holes, as above, from the same section, which this batch added the pull-through,
 * the woodchopper, the triceps kickback and the hip abduction to. What each asks of the body is in
 * its own test below.
 */
const SECOND_HOLES = {
  '1_leg_cable_glute_kickback': [1, 1],
  cable_crunch: [15, 16],
  cable_face_pull: [15, 16],
  cable_hip_abduction: [1, 1],
  cable_pull_through: [1, 2],
  cable_triceps_kickback: [9, 9],
  cable_woodchopper: [15, 16],
  cable_wrist_curl: [1, 1],
  cable_wrist_extension: [1, 1],
  seated_cable_flye: [7, 7],
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

test('the second batch is the exercises the ref gives a hole for', () => {
  assert.deepEqual([...BATCHES['cable-crossover-2']].sort(), Object.keys(SECOND_HOLES).sort());
});

/** Where the far end of the cable `entry` works should end, on the posed body. */
const endOf = (entry, rig) => {
  const { attachment, ankle = 1, hands = [1, -1] } = entry.kit.cable_crossover;
  if (attachment === 'cuff') return wpos(rig.bones[`foot_${ankle < 0 ? 'r' : 'l'}`]);
  const held = hands.map((side) => heldAt(rig, side > 0 ? 'l' : 'r'));
  return held.reduce((sum, p) => sum.add(p), V(0, 0, 0)).multiplyScalar(1 / held.length);
};

for (const [id, [low, high]] of Object.entries(SECOND_HOLES)) {
  test(`${id} stands on the crossover, its cable running from a pulley the ref gives to what it pulls on every frame`, async () => {
    const entry = prepare(id);
    assert.ok(entry.kit.cable_crossover, 'on the crossover');
    const stage = await buildStage(entry);
    const checks = checkStage(entry, stage);
    for (const c of CHECKS) assert.ok(checks[c].ok, `${c}: frame ${checks[c].frame}, ${checks[c].found}`);
    const { position, attachment, tower = 1, hands = [1, -1], crossed } = entry.kit.cable_crossover;
    assert.ok(position >= low && position <= high, `hole ${position}, not ${low} to ${high}`);
    const { cables } = stage.kit.kits.cable_crossover;
    const working = attachment ? [cables[tower > 0 ? 0 : 1]] : hands.map((side) => cables[(crossed ? -side : side) > 0 ? 0 : 1]);
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      stage.scene.updateMatrixWorld(true);
      for (const { cable } of working) {
        const miss = Math.min(...endsOf(cable).map((e) => Math.abs(e.y - exitOf(position))));
        assert.ok(miss < 1e-3, `frame ${f}: the cable leaves ${miss.toFixed(3)} m off the hole's height`);
      }
      if (attachment) {
        const gap = working[0].hook().distanceTo(endOf(entry, stage.rig));
        assert.ok(gap < 0.25, `frame ${f}: the cable ends ${gap.toFixed(3)} m from what it pulls`);
      }
    }
  });
}

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
for (const id of [...Object.keys(HOLES), ...Object.keys(SECOND_HOLES)]) {
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

test('the chest press presses until the hands nearly meet', async () => {
  const { hand } = await posed('cable_chest_press', 0);
  const apart = hand('l').distanceTo(hand('r'));
  assert.ok(apart < 0.2, `the hands ${apart.toFixed(3)} m apart`);
});

test('the crossover brings the handles together in front of the hips', async () => {
  const { hand, at } = await posed('cable_crossover', 0);
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

// The owner, #197: it is a triceps extension, so the forearm swings much further than the elbow
// moves. The limit is what the owner's OK'd pose does, its pole tilting the arm's plane.
test('the katana keeps its elbow near its place while the forearm extends', async () => {
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
    assert.ok(elbow.distanceTo(start) < 0.15, `the elbow ${(elbow.distanceTo(start) * 100).toFixed(1)} cm from where it started, on frame ${f}`);
  }
  const [top, bottom] = [bendAt(at(0)), bendAt(at(1))];
  assert.ok(bottom - top > 70, `the elbow bends from ${top.toFixed(0)}° to ${bottom.toFixed(0)}°`);
});

// The owner, #197, from the video: from the front the arm finishes in a Y, leaning out to the side,
// with the elbow out wide.
test('the katana leans its arm out to the side, the elbow wide', async () => {
  const { hand, at } = await posed('1_arm_katana_cable_triceps_extension', 0);
  const [shoulder, elbow] = [at('upperarm_l'), at('lowerarm_l')];
  assert.ok(elbow.x - shoulder.x > 0.05, `the elbow ${(elbow.x - shoulder.x).toFixed(3)} m out from the shoulder`);
  assert.ok(hand('l').x - shoulder.x > 0.15, `the hand ${(hand('l').x - shoulder.x).toFixed(3)} m out from the shoulder`);
});

// The owner, #197, as its video has it: both arms rise together, each on the far tower's cable.
test('the behind-the-back raise works both arms, mirrored, their cables crossing behind the body', async () => {
  const entry = prepare('behind_the_back_cable_lateral_raise');
  assert.ok(!entry.grip.one, 'one arm works alone');
  assert.equal(entry.kit.cable_crossover.crossed, true);
  const { stage, hand } = await posed('behind_the_back_cable_lateral_raise', 0);
  assert.ok(Math.abs(hand('l').x + hand('r').x) < 0.01, `the hands at x ${hand('l').x.toFixed(3)} and ${hand('r').x.toFixed(3)}`);
  assert.ok(Math.abs(hand('l').y - hand('r').y) < 0.01, 'the hands at different heights');
  assert.ok(hand('l').x > 0.3, `the left hand at x ${hand('l').x.toFixed(3)}, not out on its own side`);
  for (const { cable } of stage.kit.kits.cable_crossover.cables) assert.ok(pastTheHips(stage, cable) < 0, 'a cable in front of the hips');
  // Long arms all through, as in its video: #206's movement check found 72° at the top, the price
  // of an earlier fix to the wrist.
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    const [shoulder, elbow, wrist] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((n) => wpos(stage.rig.bones[n]));
    const bend = 180 - (shoulder.sub(elbow).angleTo(wrist.sub(elbow)) * 180) / Math.PI;
    assert.ok(bend < 35, `the elbow bent ${bend.toFixed(0)}°, ${d} of the way down`);
  }
  // The owner, #197: 'the wrist rotates in the middle', here at 5.6° a frame, under the limit for all.
  const steps = rollsOf(stage, 'l');
  const worst = Math.max(...steps.map(Math.abs));
  assert.ok(worst <= 5, `the hand rolls ${worst.toFixed(1)}° in a frame`);
  const net = steps.reduce((a, b) => a + b, 0);
  // A wide swing turns the forearm some way; the lateral raises turn it about 50°.
  assert.ok(Math.abs(net) <= 40, `the hand rolls ${net.toFixed(0)}° through the rep`);
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

test('the katana takes a cable from the side, across the body, and extends overhead', async () => {
  const entry = prepare('1_arm_katana_cable_triceps_extension');
  assert.equal(entry.kit.cable_crossover.crossed, true);
  const { hand, at } = await posed('1_arm_katana_cable_triceps_extension', 0);
  assert.ok(hand('l').y > at('Head').y + 0.15, `the hand at ${hand('l').y.toFixed(3)} m, the head at ${at('Head').y.toFixed(3)} m`);
});

/** Where each named bone is at the top and the bottom of `id`'s rep. */
async function ends(id, ...names) {
  const stage = await buildStage(prepare(id));
  return [0, 1].map((d) => {
    stage.pose(d);
    stage.scene.updateMatrixWorld(true);
    return Object.fromEntries(names.map((n) => [n, wpos(stage.rig.bones[n]).clone()]));
  });
}

test('the kickback drives the cuffed leg back and a little out, the other foot planted', async () => {
  const [top, bottom] = await ends('1_leg_cable_glute_kickback', 'foot_l', 'foot_r');
  assert.ok(top.foot_l.z < bottom.foot_l.z - 0.25, `back ${top.foot_l.z.toFixed(2)} from ${bottom.foot_l.z.toFixed(2)}`);
  assert.ok(top.foot_l.x > bottom.foot_l.x, 'a little out');
  assert.ok(top.foot_r.distanceTo(bottom.foot_r) < 0.01, 'the other foot stays');
});

test('the crunch rounds the back on the knees: the hips hold, the head drops toward the thighs', async () => {
  const [top, bottom] = await ends('cable_crunch', 'pelvis', 'Head', 'calf_l', 'hand_l');
  assert.ok(top.calf_l.y < 0.12, 'on the knees');
  assert.ok(top.pelvis.distanceTo(bottom.pelvis) < 0.05, 'the hips hold');
  assert.ok(bottom.Head.y - top.Head.y > 0.25, `the head drops ${(bottom.Head.y - top.Head.y).toFixed(2)}`);
});

// The owner, #197: the hands stay at the back and top of the head all the way through.
test('the crunch keeps the hands on the back and top of the head on every frame', async () => {
  const stage = await buildStage(prepare('cable_crunch'));
  const { bones } = stage.rig;
  const heights = [];
  for (let f = 0; f < FRAMES; f++) {
    stage.pose(f / (FRAMES - 1));
    stage.scene.updateMatrixWorld(true);
    const [neck, head] = [wpos(bones.neck_01), wpos(bones.Head)];
    const up = head.clone().sub(neck).normalize();
    const hand = wpos(bones.hand_l).sub(head);
    heights.push(hand.dot(up));
    assert.ok(hand.dot(up) > 0.05, `frame ${f}: the hand ${hand.dot(up).toFixed(3)} m above the head's base`);
    assert.ok(hand.length() < 0.36, `frame ${f}: the hand ${hand.length().toFixed(3)} m from the head`);
  }
  // Sliding between its two places on the head, never sinking past them behind the neck on the way.
  const lowest = Math.min(heights[0], heights.at(-1));
  assert.ok(Math.min(...heights) > lowest - 0.01, `the hand sinks to ${Math.min(...heights).toFixed(3)} on the way, below ${lowest.toFixed(3)}`);
});

test('the face pull brings the hands apart to the sides of the face', async () => {
  const [top, bottom] = await ends('cable_face_pull', 'Head', 'hand_l', 'hand_r');
  assert.ok(top.hand_l.x - top.hand_r.x > bottom.hand_l.x - bottom.hand_r.x + 0.15, 'the hands pull apart');
  assert.ok(Math.abs(top.hand_l.y - top.Head.y) < 0.2, 'at the height of the face');
  assert.ok(top.hand_l.z < bottom.hand_l.z - 0.2, 'toward the face');
});

test('the hip abduction lifts the cuffed leg out to its side, away from the tower', async () => {
  const [top, bottom] = await ends('cable_hip_abduction', 'foot_l', 'foot_r');
  assert.ok(top.foot_l.x > bottom.foot_l.x + 0.3, `out ${top.foot_l.x.toFixed(2)} from ${bottom.foot_l.x.toFixed(2)}`);
  assert.ok(top.foot_r.distanceTo(bottom.foot_r) < 0.01, 'the other foot stays');
});

test('the pull-through hinges back, the hands passing between the thighs', async () => {
  const [top, bottom] = await ends('cable_pull_through', 'pelvis', 'hand_l', 'hand_r', 'calf_l', 'calf_r', 'Head');
  assert.ok(bottom.pelvis.z < top.pelvis.z - 0.1, 'the hips go back');
  assert.ok(top.Head.y - bottom.Head.y > 0.4, 'the trunk hinges');
  assert.ok(bottom.hand_l.x < bottom.calf_l.x && bottom.hand_r.x > bottom.calf_r.x, 'the hands between the knees');
  assert.ok(bottom.hand_l.z < bottom.calf_l.z && bottom.hand_l.y > bottom.calf_l.y, 'and back between the thighs, above the knees');
});

test('the triceps kickback keeps the upper arm behind the torso, only the forearm moving', async () => {
  const [top, bottom] = await ends('cable_triceps_kickback', 'upperarm_l', 'lowerarm_l', 'hand_l');
  for (const end of [top, bottom]) assert.ok(end.lowerarm_l.z < end.upperarm_l.z - 0.05, 'the elbow behind the shoulder');
  assert.ok(top.lowerarm_l.distanceTo(bottom.lowerarm_l) < 0.04, 'the elbow holds');
  assert.ok(top.hand_l.z < bottom.hand_l.z - 0.3, 'the forearm extends back');
});

test('the triceps kickback\'s free hand holds the tower, high up', async () => {
  const { at, turn } = prepare('cable_triceps_kickback').kit.cable_crossover;
  const [top] = await ends('cable_triceps_kickback', 'middle_04_leaf_r', 'Head');
  const post = V(at[0] + 1.105, top.middle_04_leaf_r.y, at[1] - 0.089).applyAxisAngle(V(0, 1, 0), turn * Math.PI / 180);
  // The owner's OK'd place, by the post rather than on it.
  assert.ok(top.middle_04_leaf_r.distanceTo(post) < 0.15, `the fingers ${top.middle_04_leaf_r.distanceTo(post).toFixed(3)} m from the post`);
  assert.ok(top.middle_04_leaf_r.y > top.Head.y - 0.1, 'high up');
});

test('the woodchopper chops both hands from high on the left to low on the right', async () => {
  const [top, bottom] = await ends('cable_woodchopper', 'hand_l', 'hand_r');
  for (const k of ['hand_l', 'hand_r']) {
    assert.ok(bottom[k].x > 0.15 && top[k].x < -0.05, `${k} across, ${bottom[k].x.toFixed(2)} to ${top[k].x.toFixed(2)}`);
    assert.ok(bottom[k].y - top[k].y > 0.6, `${k} down`);
    assert.ok(top[k].y < 0.85, `${k} down toward the knee, at ${top[k].y.toFixed(2)}`);
  }
});

for (const id of ['cable_wrist_curl', 'cable_wrist_extension']) {
  test(`${id} moves the wrists alone, the forearms held`, async () => {
    const entry = prepare(id);
    assert.ok(Math.abs(entry.top.wrist[0] - entry.bottom.wrist[0]) > 60, 'the wrists turn through the rep');
    const [top, bottom] = await ends(id, 'lowerarm_l', 'upperarm_l');
    assert.ok(top.lowerarm_l.distanceTo(bottom.lowerarm_l) < 0.05, 'the elbow holds');
    assert.ok(top.upperarm_l.distanceTo(bottom.upperarm_l) < 0.01, 'the body holds');
  });
}

test('the seated flye sweeps wide and back to together in front of the chest, on the bench', async () => {
  const entry = prepare('seated_cable_flye');
  assert.ok(entry.kit.bench, 'on the bench');
  const [top, bottom] = await ends('seated_cable_flye', 'hand_l', 'lowerarm_l', 'upperarm_l');
  assert.ok(top.hand_l.x < 0.2 && top.hand_l.z > top.upperarm_l.z + 0.3, 'together in front of the chest');
  assert.ok(bottom.hand_l.x > 0.5 && bottom.hand_l.z < bottom.upperarm_l.z + 0.1, 'wide and back for the stretch');
  const bend = (end) => end.upperarm_l.clone().sub(end.lowerarm_l).angleTo(end.hand_l.clone().sub(end.lowerarm_l)) * 180 / Math.PI;
  for (const end of [top, bottom]) assert.ok(bend(end) > 115, `a sweep, not a press: the elbow at ${bend(end).toFixed(0)}°`);
});

// The owner's verdicts on the cold specs (#127).
const wristAt = async (id) => {
  const stage = await buildStage(prepare(id));
  stage.pose(0.5);
  stage.scene.updateMatrixWorld(true);
  return (name) => wpos(stage.scene.getObjectByName(name));
};

test('the cable wrist curl is one hand, the other knee on the ground', async () => {
  const at = await wristAt('cable_wrist_curl');
  assert.equal(prepare('cable_wrist_curl').grip.one, 1, 'both hands work');
  assert.ok(at('calf_r').y < 0.12, `the right knee ${at('calf_r').y.toFixed(3)} up`);
  assert.ok(at('calf_l').y > 0.35, `the left knee ${at('calf_l').y.toFixed(3)} up`);
});

test('the cable wrist extension is a deep squat', async () => {
  const at = await wristAt('cable_wrist_extension');
  assert.ok(at('pelvis').y < 0.42, `the hips ${at('pelvis').y.toFixed(3)} up`);
});

/**
 * How far the skin of bones `part` goes into the leg's on side `k`, at the worst frame: negative is
 * the gap left between them. A vertex is inside where it lies behind the nearest triangle's face.
 */
const skinInto = async (id, k, part) => {
  const stage = await buildStage(prepare(id));
  const vertices = bodyVertices(stage.rig);
  const starts = new Map();
  vertices.forEach((v, i) => { if (!starts.has(v.mesh)) starts.set(v.mesh, i); });
  const leg = new RegExp(`^(thigh|calf)_${k}$`);
  const faces = [...starts].flatMap(([mesh, start]) => {
    const order = mesh.geometry.index;
    return Array.from({ length: order.count / 3 }, (_, t) => [0, 1, 2].map((c) => start + order.getX(t * 3 + c)))
      .filter((face) => face.every((i) => leg.test(vertices[i].bone)));
  });
  const [closest, normal] = [new THREE.Vector3(), new THREE.Vector3()];
  let worst = { depth: -Infinity };
  for (let f = 0; f < FRAMES; f++) {
    stage.pose(f / (FRAMES - 1));
    stage.scene.updateMatrixWorld(true);
    const at = vertices.map((v) => v.mesh.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.mesh.matrixWorld));
    const triangles = faces.map(([a, b, c]) => new THREE.Triangle(at[a], at[b], at[c]));
    vertices.forEach((v, i) => {
      if (!part.test(v.bone)) return;
      let gap = Infinity;
      let inside = false;
      for (const t of triangles) {
        t.closestPointToPoint(at[i], closest);
        const d = closest.distanceTo(at[i]);
        if (d >= gap) continue;
        gap = d;
        inside = t.getNormal(normal).dot(at[i].clone().sub(closest)) < 0;
      }
      const depth = inside ? gap : -gap;
      if (depth > worst.depth) worst = { depth, frame: f, bone: v.bone };
    });
  }
  return worst;
};

test('the cable wrist extension keeps its elbows by its knees and out of them, on every frame', async () => {
  for (const k of ['l', 'r']) {
    const { depth, frame, bone } = await skinInto('cable_wrist_extension', k, new RegExp(`^(upperarm|lowerarm|hand)_${k}$`));
    const cm = (Math.abs(depth) * 100).toFixed(1);
    assert.ok(depth <= 0, `${bone} ${cm} cm into the ${k} leg at frame ${frame}`);
    assert.ok(depth > -0.03, `the ${k} arm ${cm} cm off its knee at its closest`);
  }
});

test('the cable wrist curl rests a flat palm on the kneeling thigh, on every frame', async () => {
  const { depth, frame, bone } = await skinInto('cable_wrist_curl', 'r', /^hand_r$/);
  const mm = (Math.abs(depth) * 1000).toFixed(1);
  assert.ok(depth <= 0.003, `${bone} ${mm} mm into the thigh at frame ${frame}`);
  assert.ok(depth > -0.006, `the palm ${mm} mm off the thigh at frame ${frame}`);
  const at = await wristAt('cable_wrist_curl');
  const fingers = at('middle_01_r').sub(at('hand_r'));
  const curl = (at('middle_04_leaf_r').sub(at('middle_01_r')).angleTo(fingers) * 180) / Math.PI;
  assert.ok(curl < 15, `the fingers curl ${curl.toFixed(1)}°`);
});
