// A machine exercise is solved from its machine (ADR 0007, #160): the rep drives the joint, the
// limbs follow the contacts, and the recipe's fit rules set the adjustments for the mannequin. A
// pose entry can override any of them.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { heldAt, wpos } from './rig.mjs';
import { POSES } from './poses.mjs';

/** Every posed exercise on a machine, and the joint its rep drives. */
const MACHINES = {
  leg_press: 'sled',
  machine_chest_press: 'levers',
  leg_extension: 'lever',
  seated_leg_curl: 'lever',
  lying_leg_curl: 'lever',
  seated_calf_raise: 'lever',
  hack_squat: 'carriage',
  chest_supported_machine_row: 'levers',
  machine_pulldown: 'levers',
  machine_preacher_curl: 'lever',
};

const kitOf = (stage) => Object.values(stage.kit.kits)[0];
const eased = (t) => t * t * (3 - 2 * t);

test('every machine exercise is listed here', () => {
  assert.deepEqual(Object.keys(POSES).filter((id) => POSES[id].equipment === 'machine').sort(), Object.keys(MACHINES).sort());
});

for (const [id, joint] of Object.entries(MACHINES)) {
  test(`${id}: the rep drives its ${joint}, eased from the top's value to the bottom's`, async () => {
    const stage = await buildStage(prepare(id));
    const value = (d) => { stage.pose(d); return kitOf(stage).joints[joint].value; };
    const [top, bottom] = [value(0), value(1)];
    assert.notEqual(top, bottom, 'the joint moves');
    for (const d of [0.25, 0.5, 0.75]) {
      const want = top + (bottom - top) * eased(d);
      assert.ok(Math.abs(value(d) - want) < 1e-6, `at ${d} the ${joint} at ${value(d).toFixed(4)}, the rep has it at ${want.toFixed(4)}`);
    }
  });

  test(`${id}: the entry names none of what the machine decides`, () => {
    const entry = prepare(id);
    assert.equal(entry.body?.hip, undefined, 'body.hip comes from the seat');
    assert.equal(entry.pivot, undefined, 'the pivot is the recipe\'s');
    assert.equal(entry.lever, undefined, 'the lever is the recipe\'s');
    for (const end of [entry.top, entry.bottom]) {
      for (const k of ['handle', 'knee', 'hip', 'ankle', 'press', 'forearm']) assert.equal(end?.[k], undefined, `${k} comes from the joint`);
    }
  });

  test(`${id}: the hands stay on their contact on every frame`, async () => {
    const stage = await buildStage(prepare(id));
    for (let f = 0; f < FRAMES; f++) {
      stage.pose(f / (FRAMES - 1));
      const points = kitOf(stage).contacts.filter((c) => c.landmark === 'hands').flatMap((c) => c.at);
      for (const k of ['l', 'r']) {
        const miss = Math.min(...points.map((p) => p.distanceTo(heldAt(stage.rig, k))));
        assert.ok(miss < 0.015, `the ${k} hand ${(miss * 100).toFixed(1)} cm off its contact on frame ${f}`);
      }
    }
  });
}

/** The mannequin's measurements with the thighs `by` longer. */
const longerThighs = async (id, by) => {
  const entry = prepare(id);
  const { rig } = await buildStage(entry);
  return { entry, dims: { ...rig.dims, thigh: rig.dims.thigh + by } };
};

test('the seated knee machines put the knee on the lever\'s axis, whatever the thigh\'s length', async () => {
  const { fitted } = await import('./machine.mjs');
  for (const id of ['leg_extension', 'seated_leg_curl']) {
    const name = Object.keys(prepare(id).kit)[0];
    const { entry, dims } = await longerThighs(id, 0.05);
    const short = fitted(entry, { ...dims, thigh: dims.thigh - 0.05 });
    const long = fitted(entry, dims);
    const moved = short.kit[name].seat[1] - long.kit[name].seat[1];
    assert.ok(Math.abs(moved - 0.05 * Math.cos((10 * Math.PI) / 180) * 0.995) < 1e-3, `the seat moved ${moved.toFixed(3)} m back`);
  }
});

test('a knee on the lever\'s axis stays on it through the rep', async () => {
  for (const id of ['leg_extension', 'seated_leg_curl', 'lying_leg_curl']) {
    const stage = await buildStage(prepare(id));
    for (const d of [0, 0.5, 1]) {
      const P = stage.pose(d);
      const { origin } = kitOf(stage).joints.lever;
      const off = Math.hypot(P.knee.y - origin.y, P.knee.z - origin.z);
      assert.ok(off < 0.002, `${id}: the knee ${(off * 1000).toFixed(1)} mm off the axis at ${d}`);
    }
  }
});

test('an entry\'s own seat overrides the fit, and the limbs still follow the machine', async () => {
  const entry = prepare('leg_extension');
  const fit = (await buildStage(entry)).fit;
  const [y, z] = fit['kit.leg_extension.seat'];
  const moved = { ...entry, kit: { leg_extension: { ...entry.kit.leg_extension, seat: [y + 0.03, z - 0.04] } } };
  const stage = await buildStage(moved);
  assert.equal(stage.fit['kit.leg_extension.seat'], undefined, 'nothing solved for what the entry sets');
  const before = await buildStage(entry);
  assert.ok(Math.abs(stage.pose(0).hipC.z - before.pose(0).hipC.z + 0.04) < 1e-6, 'the body sits on the seat where it is');
  // The shin still lies against the roller, its axis as far from the roller's as the lever holds it.
  for (const d of [0, 0.5, 1]) {
    const P = stage.pose(d);
    const roller = kitOf(stage).contacts.find((c) => c.point === 'roller').at[0];
    const along = roller.clone().sub(P.knee);
    const off = along.addScaledVector(P.shin, -along.dot(P.shin)).length();
    assert.ok(Math.abs(off - P.machine.off * Math.sign(P.machine.off)) < 1e-3, `the shin ${(off * 100).toFixed(1)} cm off the roller at ${d}`);
  }
});

test('an entry\'s range overrides the limits, and with none the rep runs limit to limit', async () => {
  const entry = prepare('machine_chest_press');
  const stage = await buildStage(entry);
  const { limits } = (stage.pose(0), kitOf(stage).joints.levers);
  assert.ok(Math.abs(kitOf(stage).joints.levers.value - limits[1]) < 1e-9, 'the top of the rep at the upper limit');
  stage.pose(1);
  assert.ok(Math.abs(kitOf(stage).joints.levers.value - limits[0]) < 1e-9, 'the bottom of the rep at the lower limit');
  const short = await buildStage({ ...entry, top: { ...entry.top, levers: 20 } });
  short.pose(0);
  assert.ok(Math.abs(kitOf(short).joints.levers.value - (20 * Math.PI) / 180) < 1e-9, 'the entry\'s top of 20°');
});

test('a free-weight exercise stays body-first: nothing fitted, no joint driven', async () => {
  const stage = await buildStage(prepare('barbell_squat'));
  assert.deepEqual(stage.fit, {});
  assert.equal(stage.pose(0).machine, undefined);
});

test('the calf raise\'s lever turns with the heels: higher on the lever, higher on the toes', async () => {
  const stage = await buildStage(prepare('seated_calf_raise'));
  const heel = (d) => { stage.pose(d); return wpos(stage.rig.bones.foot_l).y; };
  assert.ok(heel(0) > heel(1) + 0.05, 'the heels rise from the bottom of the rep to the top');
  assert.ok(new THREE.Vector3().copy(wpos(stage.rig.bones.ball_l)).y < 0.3, 'the balls stay on the platform');
});

// The checks on the contacts (#160), on every frame.
const checked = async (entry) => {
  const { checkStage } = await import('./checks.mjs');
  const stage = await buildStage(entry);
  return { stage, check: checkStage, machine: checkStage(entry, stage).machine };
};

for (const id of Object.keys(MACHINES)) {
  test(`${id}: its contacts hold on every frame`, async () => {
    const { machine } = await checked(prepare(id));
    assert.ok(machine.ok, machine.found);
  });
}

test('a joint past its limit fails, and names the joint', async () => {
  const entry = prepare('machine_chest_press');
  const { machine } = await checked({ ...entry, top: { ...entry.top, levers: 30 } });
  assert.equal(machine.ok, false);
  assert.match(machine.found, /levers .*past/);
  assert.equal(machine.frame, 0, 'worst at the top of the rep, which it overruns');
});

test('a pad off its limb fails, and names the pad and the limb', async () => {
  const entry = prepare('machine_pulldown');
  const stage = await buildStage(entry);
  const [y, z] = stage.fit['kit.pulldown_machine.thighPad'];
  const { machine } = await checked({ ...entry, kit: { pulldown_machine: { thighPad: [y + 0.06, z] } } });
  assert.equal(machine.ok, false);
  assert.match(machine.found, /thighPads .*off the thighs/);
});

test('a shin leaving its roller fails', async () => {
  const entry = prepare('leg_extension');
  const stage = await buildStage(entry);
  const [y, z] = stage.fit['kit.leg_extension.seat'];
  // The feet so far apart that each shin stands out past the roller's end.
  const { machine } = await checked({ ...entry, feet: { ...entry.feet, stance: 0.34 }, kit: { leg_extension: { seat: [y, z] } } });
  assert.equal(machine.ok, false);
  assert.match(machine.found, /roller .*off the shins/);
});

test('a handle off its arc fails, though the hand follows it', async () => {
  const { checkStage } = await import('./checks.mjs');
  const entry = prepare('chest_supported_machine_row');
  const stage = await buildStage(entry);
  const kit = kitOf(stage);
  // A kit whose handles drift off the lever's arc as the rep goes down.
  let frame = 0;
  const pose = (d) => { frame = d; return stage.pose(d); };
  const drifting = Object.create(kit, {
    contacts: { get: () => kit.contacts.map((c) => (c.point === 'handles' ? { ...c, at: c.at.map((p) => p.clone().add(new THREE.Vector3(0, 0.01 * frame, 0))) } : c)) },
  });
  const { machine } = checkStage(entry, { ...stage, pose, kit: Object.create(stage.kit, { kits: { value: { row_machine: drifting } } }) });
  assert.equal(machine.ok, false);
  assert.match(machine.found, /handles .*off (its|their) arc/);
});

test('a hand off its handle fails', async () => {
  const { checkStage } = await import('./checks.mjs');
  const entry = prepare('chest_supported_machine_row');
  const stage = await buildStage(entry);
  const kit = kitOf(stage);
  const away = Object.create(kit, {
    contacts: { get: () => kit.contacts.map((c) => (c.point === 'handles' ? { ...c, at: c.at.map((p) => p.clone().add(new THREE.Vector3(0.05, 0, 0))) } : c)) },
  });
  const { machine } = checkStage(entry, { ...stage, kit: Object.create(stage.kit, { kits: { value: { row_machine: away } } }) });
  assert.equal(machine.ok, false);
  assert.match(machine.found, /hand .* off the handles/);
});

test('a free-weight exercise has no contacts to check', async () => {
  const { machine } = await checked(prepare('barbell_row'));
  assert.ok(machine.ok, machine.found);
});
