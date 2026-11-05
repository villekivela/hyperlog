// The power rack's 16 exercises (#196), from refs/power_rack/ref.md: the body hangs from the bar or
// rests on the dip handles, its feet off the floor, and nothing on the rack moves.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { STATIONS, buildKit, shownAlone } from './kit.mjs';
import { partUnder } from './grip.mjs';
import { buildStage } from './stage.mjs';
import { gripsOf, prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { between, sideOf, wpos } from './rig.mjs';
import { POSES } from './poses.mjs';
import { BATCHES } from './review.mjs';
import { tempoOf } from './tempo.mjs';
import { tunables } from './tune.mjs';

const rack = (options = {}) => {
  const kit = buildKit({ equipment: 'bodyweight', kit: { power_rack: options } }).kits.power_rack;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};

test('a pose holds one pair of grips, its handles: the contacts tie the hands to that pair alone', () => {
  for (const handles of ['wide', 'standard', 'narrow', 'close', 'neutral', 'dip', 'dipClose']) {
    assert.deepEqual(rack({ handles }).contacts.map((c) => c.point), [handles]);
  }
  assert.throws(() => rack({ handles: 'tiny' }), /No handles "tiny"/);
});

test('every grip has a round part under the hand for the fingers to close on', () => {
  const kit = rack();
  for (const c of kit.contacts) {
    for (const p of c.at) assert.ok(partUnder(kit, p), `no part under the ${c.point} grip at x ${p.x.toFixed(3)}`);
  }
});

const IN = 0.0254;
const BAR = 91 * IN;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const degrees = (r) => (r * 180) / Math.PI;
const passes = async (id, options) => {
  const { checks } = await checkPose(id, options);
  for (const [name, c] of Object.entries(checks)) assert.ok(c.ok, `${id}: ${name} on frame ${c.frame}: ${c.found}`);
};
/** The posed body's skin at the bones `wanted` picks, in the world. */
const skinOf = (rig, wanted) => {
  const out = [];
  for (const m of rig.meshes) {
    const { skinIndex, skinWeight, position } = m.geometry.attributes;
    for (let i = 0; i < position.count; i++) {
      let best = 0;
      for (let j = 1; j < 4; j++) if (skinWeight.getComponent(i, j) > skinWeight.getComponent(i, best)) best = j;
      if (wanted.test(m.skeleton.bones[skinIndex.getComponent(i, best)].name)) out.push(m.getVertexPosition(i, new THREE.Vector3()).applyMatrix4(m.matrixWorld));
    }
  }
  return out;
};
const elbowBend = (rig, k) => degrees(wpos(rig.bones[`lowerarm_${k}`]).sub(wpos(rig.bones[`upperarm_${k}`])).angleTo(wpos(rig.bones[`hand_${k}`]).sub(wpos(rig.bones[`lowerarm_${k}`]))));
const shoulders = (rig) => between(rig, 'upperarm_l', 'upperarm_r');

test('a pull-up hangs from the standard grips and passes every check, the hands on them through the rep', async () => {
  await passes('pullup');
  assert.deepEqual(prepare('pullup').kit, { power_rack: { handles: 'standard' } });
});

test('a pull-up rises about 490 mm from straight arms under the bar to the chin over it, the elbows at about 40°', async () => {
  const { rig, pose } = await buildStage(prepare('pullup'));
  pose(1);
  const hang = shoulders(rig);
  close(hang.z, 0, 0.04, 'the shoulders under the bar at the bottom');
  for (const k of ['l', 'r']) assert.ok(elbowBend(rig, k) < 15, `the ${k} elbow bent ${elbowBend(rig, k).toFixed(0)}° at the bottom`);
  pose(0);
  close(shoulders(rig).y - hang.y, 0.49, 0.03, 'the rise');
  for (const k of ['l', 'r']) close(180 - elbowBend(rig, k), 40, 15, `the ${k} elbow at the top, in degrees`);
  const chin = Math.min(...skinOf(rig, /^Head$/).map((p) => p.y));
  assert.ok(chin > BAR, `the chin at ${chin.toFixed(3)}, under the bar at ${BAR.toFixed(3)}`);
});

test('the feet check fails a hanging foot let down to the floor', async () => {
  const poses = { ...POSES, sunk: { like: 'pullup', bottom: { rise: -0.08 } } };
  const { feet } = (await checkPose('sunk', { poses })).checks;
  assert.equal(feet.ok, false);
  assert.match(feet.found, /off the floor/);
});

test('the equipment check fails a body driven into the dip handles', async () => {
  // The legs swept back and out, level with the handles, across the one on the +x side.
  const swept = { rise: 0.185, back: 0, trunk: 0, thigh: -90, shin: 90, shrug: 13 };
  const poses = { ...POSES, swept: { like: 'pullup', feet: { stance: 0.6 }, top: swept, bottom: swept } };
  const { equipment } = (await checkPose('swept', { poses })).checks;
  assert.equal(equipment.ok, false);
  assert.match(equipment.found, /^(thigh|calf)_/);
});

/** How far the thumb's root lies from the little finger's along the way the part runs toward the middle (`P.across`): + overhand, - underhand. */
const thumbIn = (rig, P, k) => wpos(rig.bones[`thumb_01_${k}`]).sub(wpos(rig.bones[`pinky_01_${k}`])).dot(P.across(sideOf(rig, k)));

for (const [id, handles] of [
  ['pullup_wide_grip', 'wide'], ['close_grip_pullup', 'narrow'], ['neutral_grip_pullup', 'neutral'],
  ['chinup', 'standard'], ['chinup_wide_grip', 'wide'], ['close_grip_chinup', 'close'],
]) {
  test(`${id} hangs from the ${handles} grips and passes every check`, async () => {
    assert.equal(prepare(id).kit.power_rack.handles, handles);
    await passes(id);
  });
}

for (const [id, way, turned] of [
  ['pullup', 'overhand', 1], ['pullup_wide_grip', 'overhand', 1], ['close_grip_pullup', 'overhand', 1], ['weighted_pullup', 'overhand', 1],
  ['chinup', 'underhand', -1], ['chinup_wide_grip', 'underhand', -1], ['close_grip_chinup', 'underhand', -1], ['weighted_chinup', 'underhand', -1],
]) {
  test(`${id} is ${way}: the thumbs lie ${turned > 0 ? 'toward' : 'away from'} the middle`, async () => {
    const { rig, pose } = await buildStage(prepare(id));
    for (const d of [0, 1]) {
      const P = pose(d);
      for (const k of ['l', 'r']) assert.ok(turned * thumbIn(rig, P, k) > 0, `the ${k} hand at ${d}: ${thumbIn(rig, P, k).toFixed(3)}`);
    }
  });
}

test('the close-grip pull-up turns its hands as the pull-up does, overhand through the rep, where the V would turn the palms out', async () => {
  const offSquare = async (id) => {
    const { rig, pose } = await buildStage(prepare(id));
    return [0, 0.25, 0.5, 0.75, 1].map((d) => {
      pose(d);
      const thumb = wpos(rig.bones.thumb_01_l).sub(wpos(rig.bones.pinky_01_l));
      return degrees(Math.atan2(Math.abs(thumb.z), -sideOf(rig, 'l') * thumb.x));
    });
  };
  const pull = await offSquare('pullup');
  (await offSquare('close_grip_pullup')).forEach((off, i) => close(off, pull[i], 15, `the hand off square at step ${i}, against the pull-up's`));
});

test('the neutral grip turns the palms to each other: the thumbs lie back toward the lifter', async () => {
  const { rig, pose } = await buildStage(prepare('neutral_grip_pullup'));
  pose(1);
  for (const k of ['l', 'r']) assert.ok(wpos(rig.bones[`thumb_01_${k}`]).z < wpos(rig.bones[`pinky_01_${k}`]).z, `the ${k} hand's thumb is on the rack's side`);
});

test('the 2-grip pull-up is a clip a grip, as the 2-grip pulldown: set 1 wide and overhand, set 2 narrow and underhand', async () => {
  assert.deepEqual(gripsOf('2_grip_pullup'), ['wide', 'narrow']);
  const wide = prepare('2_grip_pullup', undefined, undefined, 'wide');
  const narrow = prepare('2_grip_pullup', undefined, undefined, 'narrow');
  assert.equal(wide.kit.power_rack.handles, 'standard');
  assert.equal(narrow.kit.power_rack.handles, 'close');
  assert.ok(!wide.grip.rotate, 'set 1 is overhand');
  assert.equal(narrow.grip.rotate, 180);
  await passes('2_grip_pullup', { grip: 'wide' });
  await passes('2_grip_pullup', { grip: 'narrow' });
});

test('a dead hang is held: arms straight, the shoulders under the bar on every frame', async () => {
  await passes('dead_hang');
  const { rig, pose } = await buildStage(prepare('dead_hang'));
  pose(0);
  const held = shoulders(rig);
  for (const d of [0, 0.5, 1]) {
    pose(d);
    assert.ok(shoulders(rig).distanceTo(held) < 1e-3, `the shoulders moved at ${d}`);
    for (const k of ['l', 'r']) assert.ok(elbowBend(rig, k) < 15, `the ${k} elbow bent ${elbowBend(rig, k).toFixed(0)}°`);
  }
});

const hipFlexion = (rig, k) => {
  const trunk = shoulders(rig).sub(between(rig, 'thigh_l', 'thigh_r')).negate();
  return degrees(wpos(rig.bones[`calf_${k}`]).sub(wpos(rig.bones[`thigh_${k}`])).angleTo(trunk));
};

test('a hanging leg raise flexes the hip from straight to 90-110°, the legs at or above level and straight, the arms straight', async () => {
  await passes('hanging_leg_raise');
  const { rig, pose } = await buildStage(prepare('hanging_leg_raise'));
  pose(1);
  for (const k of ['l', 'r']) assert.ok(hipFlexion(rig, k) < 15, `the ${k} hip at the bottom: ${hipFlexion(rig, k).toFixed(0)}°`);
  pose(0);
  for (const k of ['l', 'r']) {
    close(hipFlexion(rig, k), 100, 10, `the ${k} hip at the top, in degrees`);
    assert.ok(wpos(rig.bones[`foot_${k}`]).y >= wpos(rig.bones[`thigh_${k}`]).y, `the ${k} leg is under level`);
    assert.ok(elbowBend(rig, k) < 15, `the ${k} elbow bent ${elbowBend(rig, k).toFixed(0)}°`);
  }
});

test('a dip faces out along the handles, its back to the upright (close-grip dip video, 0:05): the rack stands turned behind the lifter', async () => {
  for (const id of ['dip', 'close_grip_dip', 'weighted_dip']) {
    const { rig, kit, pose } = await buildStage(prepare(id));
    const rack = kit.kits.power_rack;
    for (const d of [0, 1]) {
      pose(d);
      const [l, r] = rack.contacts[0].at;
      close(l.x + r.x, 0, 1e-6, `${id}: the grips either side of the middle`);
      assert.ok(l.x < r.x, `${id}: left hand first`);
      const behind = new THREE.Box3().setFromObject(rack.group).min.z;
      const front = new THREE.Box3().setFromObject(rack.group).max.z;
      assert.ok(shoulders(rig).z - behind > 0.2, `${id}: the rack's back ${behind.toFixed(2)} too near the shoulders`);
      assert.ok(front < 0.4, `${id}: the rack runs on ahead of the lifter to ${front.toFixed(2)}`);
    }
  }
});

test('the close grips are at the handles\' inner end, under 500 mm apart, and the dip\'s further out', () => {
  const kit = rack();
  const spread = (name) => { const [l, r] = kit.contacts.find((c) => c.point === name).at; return r.x - l.x; };
  assert.ok(spread('dipClose') < 0.5, `the close grips ${spread('dipClose').toFixed(3)} apart`);
  assert.ok(spread('dip') - spread('dipClose') > 0.04, 'the two pairs barely differ');
});

test('a close-grip dip keeps the upper arms behind the trunk (the library\'s note)', async () => {
  const { rig, pose } = await buildStage(prepare('close_grip_dip'));
  pose(1);
  for (const k of ['l', 'r']) assert.ok(wpos(rig.bones[`lowerarm_${k}`]).z < wpos(rig.bones[`upperarm_${k}`]).z, `the ${k} elbow is ahead of the shoulder`);
});

for (const id of ['dip', 'close_grip_dip', 'weighted_dip']) {
  test(`${id} holds the handles neutral, the thumbs forward: out along them, the way the lifter faces`, async () => {
    const { rig, pose } = await buildStage(prepare(id));
    for (const d of [0, 1]) {
      pose(d);
      for (const k of ['l', 'r']) {
        const ahead = wpos(rig.bones[`thumb_01_${k}`]).z - wpos(rig.bones[`pinky_01_${k}`]).z;
        assert.ok(ahead > 0.05, `the ${k} thumb ${ahead.toFixed(3)} ahead of the little finger at ${d}`);
      }
    }
  });
}

test('the rack stands turned only for a dip: a pull-up hangs in front of it, as it is built', () => {
  const pull = rack({ handles: 'standard' });
  assert.deepEqual(pull.group.position.toArray(), [0, 0, 0]);
  assert.equal(pull.group.rotation.y, 0);
  const dip = rack({ handles: 'dip' });
  close(Math.abs(dip.group.rotation.y), Math.PI, 1e-9, 'half a turn');
});

const leanOf = (rig) => {
  const up = shoulders(rig).sub(between(rig, 'thigh_l', 'thigh_r'));
  return degrees(Math.atan2(up.z, up.y));
};
const elbowsY = (rig) => (wpos(rig.bones.lowerarm_l).y + wpos(rig.bones.lowerarm_r).y) / 2;

for (const [id, handles, lean] of [['dip', 'dip', 30], ['close_grip_dip', 'dipClose', 22]]) {
  test(`${id} rests on the ${handles} grips and passes every check`, async () => {
    assert.equal(prepare(id).kit.power_rack.handles, handles);
    await passes(id);
  });

  test(`${id} drops about 290 mm from locked arms to the elbows at about 90°, the shoulders about level with them, leaning about ${lean}°`, async () => {
    const { rig, pose } = await buildStage(prepare(id));
    pose(0);
    const top = shoulders(rig);
    close(top.x, 0, 0.005, 'the body midway between the handles');
    for (const k of ['l', 'r']) assert.ok(elbowBend(rig, k) < 15, `the ${k} elbow bent ${elbowBend(rig, k).toFixed(0)}° at the top`);
    pose(1);
    close(top.y - shoulders(rig).y, 0.29, 0.03, 'the drop');
    for (const k of ['l', 'r']) close(elbowBend(rig, k), 90, 15, `the ${k} elbow at the bottom, in degrees`);
    assert.ok(shoulders(rig).y < elbowsY(rig) + 0.03, `the shoulders ${(shoulders(rig).y - elbowsY(rig)).toFixed(3)} above the elbows at the bottom`);
    close(leanOf(rig), lean, 8, 'the trunk\'s lean at the bottom, in degrees');
  });
}

const plateOf = (kit) => {
  let plate;
  kit.kits.dip_belt.group.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') plate = o; });
  return plate;
};

for (const [id, like, grip] of [
  ['weighted_pullup', 'pullup'], ['weighted_chinup', 'chinup'], ['2_grip_pullup_weighted', '2_grip_pullup', 'wide'],
  ['2_grip_pullup_weighted', '2_grip_pullup', 'narrow'], ['weighted_dip', 'dip'],
]) {
  test(`${id}${grip ? ` (${grip})` : ''} is ${like} with a plate on a dip belt's chain, plumb in front of the thighs at about mid-thigh`, async () => {
    await passes(id, { grip });
    const entry = prepare(id, undefined, undefined, grip);
    const plain = prepare(like, undefined, undefined, grip);
    assert.equal(entry.equipment, 'weighted_bodyweight');
    assert.deepEqual(entry.kit.power_rack, plain.kit.power_rack);
    assert.deepEqual(entry.top, plain.top);
    assert.deepEqual(entry.bottom, plain.bottom);
    const { rig, kit, pose } = await buildStage(entry);
    for (const d of [0, 0.5, 1]) {
      pose(d);
      const at = new THREE.Box3().setFromObject(plateOf(kit)).getCenter(new THREE.Vector3());
      const hip = between(rig, 'thigh_l', 'thigh_r');
      const knee = between(rig, 'calf_l', 'calf_r');
      close(at.y, (hip.y + knee.y) / 2, 0.08, `the plate's height at ${d}, against mid-thigh`);
      close(at.x, hip.x, 1e-3, `the plate between the legs at ${d}`);
      // The thigh's front stands about 80 mm ahead of its line at mid-thigh.
      assert.ok(at.z > (hip.z + knee.z) / 2 + 0.08, `the plate at z ${at.z.toFixed(3)} at ${d}, not in front of the thighs`);
    }
  });
}

test('the chain runs from the belt round the hips down to the plate, on every frame', async () => {
  const { rig, kit, pose } = await buildStage(prepare('weighted_pullup'));
  const belt = kit.kits.dip_belt;
  const chains = [];
  let ring;
  belt.group.traverse((o) => {
    if (o.isMesh && o.material?.name === 'metal') chains.push(o);
    if (o.isMesh && o.material?.name === 'vinyl') ring = o;
  });
  assert.equal(chains.length, 2, 'a chain from each side of the belt');
  assert.ok(ring, 'a belt');
  for (const d of [0, 0.5, 1]) {
    pose(d);
    const hip = between(rig, 'thigh_l', 'thigh_r');
    const box = new THREE.Box3().setFromObject(ring);
    assert.ok(box.containsPoint(hip.clone().setY(box.getCenter(new THREE.Vector3()).y)), `the belt isn't round the hips at ${d}`);
    close(box.getCenter(new THREE.Vector3()).y, hip.y + 0.05, 0.03, `the belt's height at ${d}`);
    const plate = new THREE.Box3().setFromObject(plateOf(kit)).getCenter(new THREE.Vector3());
    for (const chain of chains) {
      const ends = [-0.5, 0.5].map((y) => chain.localToWorld(new THREE.Vector3(0, y, 0)));
      assert.ok(Math.min(...ends.map((e) => e.distanceTo(plate))) < 1e-3, `a chain's end off the plate at ${d}`);
      assert.ok(Math.min(...ends.map((e) => box.distanceToPoint(e))) < 0.02, `a chain's end off the belt at ${d}`);
    }
  }
});

test('a plate hangs plumb: its face square to the floor however the body leans', async () => {
  const { kit, pose } = await buildStage(prepare('weighted_dip'));
  for (const d of [0, 1]) {
    pose(d);
    const size = new THREE.Box3().setFromObject(plateOf(kit)).getSize(new THREE.Vector3());
    assert.ok(size.z < 0.05, `the plate ${size.z.toFixed(3)} deep at ${d}: not plumb`);
  }
});

/** Every clip of the rack's exercises, as [id, grip]. */
const CLIPS = STATIONS.power_rack.exercises.flatMap((id) => (gripsOf(id) ?? [undefined]).map((grip) => [id, grip]));

for (const [id, grip] of CLIPS.filter(([id]) => /pullup|chinup/.test(id))) {
  test(`${grip ? `${id} (${grip})` : id}: the chin comes over the bar, from straight arms`, async () => {
    const { rig, pose } = await buildStage(prepare(id, undefined, undefined, grip));
    pose(1);
    const hang = shoulders(rig).y;
    for (const k of ['l', 'r']) assert.ok(elbowBend(rig, k) < 15, `the ${k} elbow bent ${elbowBend(rig, k).toFixed(0)}° at the bottom`);
    pose(0);
    const chin = Math.min(...skinOf(rig, /^Head$/).map((p) => p.y));
    assert.ok(chin > BAR, `the chin at ${chin.toFixed(3)}, under the bar at ${BAR.toFixed(3)}`);
    // Wide hands hang higher, so the chin reaches the bar sooner.
    assert.ok(shoulders(rig).y - hang > 0.43, `the rise ${(shoulders(rig).y - hang).toFixed(3)}`);
  });
}

test('the batch is the rack\'s 16 exercises, 18 clips with the 2-grip pull-ups\' two each', () => {
  assert.deepEqual(BATCHES['power-rack'], STATIONS.power_rack.exercises);
  assert.equal(STATIONS.power_rack.exercises.length, 16);
  assert.equal(CLIPS.length, 18);
});

test('the rack no longer stands alone: its exercises show it', () => {
  assert.equal(shownAlone('power_rack'), null);
});

for (const [id, grip] of CLIPS) {
  const name = grip ? `${id} (${grip})` : id;
  test(`${name}: the lowest of the feet stays off the floor on every frame`, async () => {
    const { rig, pose } = await buildStage(prepare(id, undefined, undefined, grip));
    for (let f = 0; f <= 30; f += 3) {
      pose(f / 30);
      const lowest = Math.min(...skinOf(rig, /^(foot|ball)_/).map((p) => p.y));
      assert.ok(lowest > 0.02, `${lowest.toFixed(3)} m up on frame ${f}`);
    }
  });
}

for (const [id, grip] of CLIPS.filter(([id]) => prepare(id).motion === 'hang')) {
  test(`${grip ? `${id} (${grip})` : id}: the hanging body clears the dip handles on every frame`, async () => {
    const { rig, kit, pose } = await buildStage(prepare(id, undefined, undefined, grip));
    const handles = [];
    // The bar's end knobs are vinyl too, up at the bar.
    kit.kits.power_rack.group.traverse((o) => { if (o.isMesh && o.material?.name === 'vinyl' && new THREE.Box3().setFromObject(o).max.y < 1.5) handles.push(o); });
    assert.equal(handles.length, 4, 'the two handles and their end knobs');
    for (let f = 0; f <= 30; f += 3) {
      pose(f / 30);
      const skin = skinOf(rig, /./);
      for (const handle of handles) {
        const box = new THREE.Box3().setFromObject(handle);
        assert.ok(!skin.some((p) => box.containsPoint(p)), `the body in ${handle.name} on frame ${f}`);
      }
    }
  });
}

test('each new motion has a tempo, and the studio offers its numbers', () => {
  for (const id of ['pullup', 'dip']) {
    assert.deepEqual(tempoOf(prepare(id)), { lowering: 2, bottom: 0.3, lifting: 1, top: 0.6 });
  }
  const paths = (id) => tunables(prepare(id)).map((n) => n.path);
  for (const path of ['top.rise', 'bottom.rise', 'top.back', 'feet.point', 'body.look', 'grip.pole.0']) assert.ok(paths('pullup').includes(path), `pullup: no ${path}`);
  for (const path of ['top.sink', 'bottom.sink', 'bottom.over', 'feet.point', 'body.look']) assert.ok(paths('dip').includes(path), `dip: no ${path}`);
});
