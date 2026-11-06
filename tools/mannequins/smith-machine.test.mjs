// The smith machine (#182, #241), from refs/smith_machine/ref.md: a bar on two guide rods leaning 7°
// with the top toward the back, a carriage at each end whose hook lug rests on a notch in a plate
// behind the rod, safety stops on the same plates, and plate horns on two padded rear uprights. Its
// nine lifts ride the rods, so each solves its body against their line.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit } from './kit.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { V, buildRig, heldAt, rad, wpos } from './rig.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { POSES } from './poses.mjs';
import { openClip, poseFrame } from './glb.mjs';

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'smith_machine', kit: { smith_machine: options } }).kits.smith_machine;
  // With no pose to drive it, the bar hangs on its hooks.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const LEAN = rad(7);
const FOOT_Y = 0.25;
// The ref's hook pitch, and the lowest and highest hooks (m above the floor).
const PITCH = 0.152;
const LOWEST = 0.29;
const HIGHEST = 2.114;
/** The bar's height above the floor, where its shaft crosses the machine's centre line. */
const barHeight = (kit) => {
  let y;
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'shaft') y = new THREE.Box3().setFromObject(o).getCenter(new THREE.Vector3()).y; });
  return y;
};

test('the smith machine is the kit of smith machine equipment, and serves its nine exercises', () => {
  assert.ok(KITS.smith_machine, 'a recipe named smith_machine');
  assert.ok(EQUIPMENT.smith_machine.includes('smith_machine'), 'smith_machine equipment has no smith_machine kit');
  assert.deepEqual(MACHINES.smith_machine ?? null, null, 'it is its own equipment, not a machine kind');
});

test('the bar slides along rods leaning 7° with the top toward the back, from its lowest stop to the top bracket', () => {
  const kit = machine();
  const { bar } = kit.joints;
  assert.equal(bar.type, 'prismatic');
  close(bar.axis.clone().normalize().y, Math.cos(LEAN), 1e-9, 'the rod\'s rise per metre along it');
  close(bar.axis.clone().normalize().z, -Math.sin(LEAN), 1e-9, 'the rod\'s lean toward the back, per metre');
  // The ref's travel, about 250 to 2050 above the floor, along a rod 7° off vertical.
  close(bar.limits[1] - bar.limits[0], (2.05 - LOWEST) / Math.cos(LEAN), 0.03, 'the bar\'s travel along the rods');
});

test('the joint\'s value is the metres the bar has moved along the rods', () => {
  const kit = machine();
  const { bar } = kit.joints;
  const from = barHeight(kit);
  const at = bar.value;
  kit.scrub('bar', at + 0.5);
  kit.group.updateMatrixWorld(true);
  close(barHeight(kit) - from, 0.5 * Math.cos(LEAN), 1e-6, 'how far it rose for half a metre along the rods');
  kit.scrub('bar', 99);
  close(kit.joints.bar.value, bar.limits[1], 1e-9, 'held at its upper limit');
});

test('unposed, the bar hangs on the hook one notch below a standing squat\'s bar, 150 mm between notches', () => {
  const kit = machine();
  // The ref: the notch at about 1.35 m, for a squat's bar at about 1.47 m.
  close(barHeight(kit), LOWEST + 7 * PITCH, 0.005, 'the racked bar\'s height');
  close(machine({ hook: 3 }).joints.bar.value, machine().joints.bar.value - 4 * PITCH / Math.cos(LEAN), 1e-6, 'four notches lower, along the rods');
  assert.ok(HIGHEST > LOWEST + 12 * PITCH - 0.01 && HIGHEST < LOWEST + 12 * PITCH + 0.01, 'thirteen notches span the ref\'s lowest to highest hook');
});

test('its nine exercises are the library\'s smith machine ones, under equipment that has the kit', async () => {
  const library = JSON.parse(await readFile(new URL('../../app/src/main/assets/exercises.json', import.meta.url), 'utf8'));
  const ids = library.exercises.filter((e) => e.equipment === 'smith_machine').map((e) => e.id).sort();
  assert.deepEqual(ids, ['ffe_smith_machine_lunge', 'smith_machine_bench_press', 'smith_machine_incline_press', 'smith_machine_jm_press', 'smith_machine_lunge', 'smith_machine_rdl', 'smith_machine_seated_press', 'smith_machine_squat', 'split_squat_smith']);
  assert.ok(EQUIPMENT.smith_machine.includes('smith_machine'));
});

test('the hands hold the shaft between the carriages and the upper back rests on it, both at the bar\'s height', () => {
  const kit = machine();
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(new THREE.Box3().setFromObject(o)); });
  assert.equal(held.length, 1, 'one held part, the shaft');
  const [grips, back] = [contact(kit, 'grips'), contact(kit, 'back')];
  assert.deepEqual([grips.body, grips.landmark, back.body, back.landmark], ['bar', 'hands', 'bar', 'shoulders']);
  for (const p of [...grips.at, ...back.at]) assert.ok(held[0].distanceToPoint(p) < 1e-6, `a contact at ${p.toArray().map((v) => v.toFixed(2))} is off the shaft`);
  // They move with the bar.
  const before = grips.at[0].clone();
  kit.scrub('bar', kit.joints.bar.value + 0.4);
  close(contact(kit, 'grips').at[0].distanceTo(before), 0.4, 1e-9, 'how far the grips went with it');
});

test('two fit rules set the hook the bar hangs on and the notch the safety stops lie on, below which the bar can\'t go', () => {
  assert.deepEqual(KITS.smith_machine.fit.map((f) => f.sets), ['hook', 'stops']);
  const [low, high] = [machine({ stops: 0 }), machine({ stops: 4 })];
  close(high.joints.bar.limits[0] - low.joints.bar.limits[0], 4 * PITCH / Math.cos(LEAN), 1e-6, 'how far four notches raised the lowest the bar goes');
  // A bar hung below its stops is held on them.
  const held = machine({ hook: 1, stops: 4 });
  close(held.joints.bar.value, held.joints.bar.limits[0], 1e-9, 'hung below its stops');
});

// The lifts' bar heights (m above the floor) from the ref's Motion, for a mannequin and the bench's
// 0.457 m pad: where each lift has the bar standing and at its lowest. The mannequin's shoulder
// joint plus 2 cm is where a squat's bar rides standing.
test('the bar\'s limits and the hooks span every lift the ref gives, from a squat\'s 1.47 m to a bench\'s chest', async () => {
  const bytes = await readFile(MODEL);
  const rig = buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
  rig.root.updateMatrixWorld(true);
  const shoulder = Math.min(...['l', 'r'].map((k) => wpos(rig.bones[`upperarm_${k}`]).y));
  const bench = 0.457;
  const lifts = {
    squat: [1.0, shoulder + 0.02],
    lunge: [1.15, 1.57],
    ffe_lunge: [1.05, 1.57],
    bench_press: [bench + 0.23, bench + 0.23 + 0.4],
  };
  const kit = machine();
  const { limits } = kit.joints.bar;
  const heightAt = (value) => FOOT_Y + value * Math.cos(LEAN);
  for (const [name, [bottom, top]] of Object.entries(lifts)) {
    assert.ok(heightAt(limits[0]) <= bottom && heightAt(limits[1]) >= top, `${name} from ${bottom.toFixed(2)} to ${top.toFixed(2)} m is outside the bar's ${heightAt(limits[0]).toFixed(2)} to ${heightAt(limits[1]).toFixed(2)} m`);
    // A notch to hang the bar on, within one notch under its top, and one to stop it just below its bottom.
    const hook = Math.floor((top - LOWEST) / PITCH);
    assert.ok(hook >= 0 && hook < 13 && top - (LOWEST + hook * PITCH) < PITCH, `${name}: no hook within a notch below ${top.toFixed(2)} m`);
    const stop = Math.floor((bottom - LOWEST) / PITCH);
    assert.ok(stop >= 0 && stop < 13, `${name}: no stop below ${bottom.toFixed(2)} m`);
  }
  // The default hook is the one below a standing squat's bar.
  const racked = barHeight(kit);
  assert.ok(racked < lifts.squat[1] && lifts.squat[1] - racked < PITCH, `the racked bar at ${racked.toFixed(2)} m, for a squat's bar at ${lifts.squat[1].toFixed(2)} m`);
});

// The owner's lessons from the T-bar row (#179, #180): heavy parts centred on the machine's line,
// supports under what they hold, handles pointing the way the photos show.
const boxes = (kit, wanted) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push(new THREE.Box3().setFromObject(o)); });
  return out;
};
const centre = (b) => b.getCenter(new THREE.Vector3());

test('the bar, its carriages and the machine are symmetric about the centre line', () => {
  const kit = machine();
  const bar = new THREE.Box3();
  for (const g of kit.bodies.bar) bar.union(new THREE.Box3().setFromObject(g));
  close(centre(bar).x, 0, 1e-6, 'the bar\'s middle');
  close(bar.max.x, 1.05, 0.01, 'the bar\'s half length');
  const mirror = (b) => [b.min.x, b.max.x].map((x) => -x).sort((a, c) => a - c);
  const all = boxes(kit, () => true);
  for (const b of all) {
    const m = mirror(b);
    const twin = all.find((o) => Math.abs(o.min.x - m[0]) < 1e-3 && Math.abs(o.max.x - m[1]) < 1e-3 && Math.abs(o.min.y - b.min.y) < 1e-3 && Math.abs(o.min.z - b.min.z) < 1e-3);
    assert.ok(twin, `a part at x ${b.min.x.toFixed(2)}..${b.max.x.toFixed(2)}, y ${b.min.y.toFixed(2)}, has no twin across the line`);
  }
});

const roleOf = (kit, name) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === name) out.push(o); });
  return out;
};
const box = (o) => new THREE.Box3().setFromObject(o);
const size = (b) => b.getSize(new THREE.Vector3());

test('each side stands on a floor beam: both uprights end on it and the hood joins them, so nothing floats', () => {
  const kit = machine();
  for (const s of [-1, 1]) {
    const side = (o) => Math.sign(centre(box(o)).x) === s;
    const [beam] = roleOf(kit, 'beam').filter(side).map(box);
    assert.ok(beam, `no floor beam on side ${s}`);
    close(size(beam).z, 1.24, 0.03, 'the beam\'s length');
    const uprights = roleOf(kit, 'upright').filter(side).map(box);
    assert.equal(uprights.length, 2, 'a front and a rear upright');
    for (const u of uprights) {
      close(u.min.y, beam.max.y, 0.01, 'an upright\'s foot, on the beam\'s top');
      assert.ok(u.max.y >= 2.0, 'an upright up to its top or its hood');
    }
    const [hood] = roleOf(kit, 'hood').filter(side).map(box);
    // The hood runs from the front upright's top to the rear upright, both its ends on them.
    const [front, rear] = uprights.sort((a, c) => c.max.z - a.max.z);
    assert.ok(hood.max.z >= front.min.z && hood.min.z <= rear.max.z, 'the hood doesn\'t reach from the front upright to the rear one');
    assert.ok(hood.max.y > 2.2 && hood.max.y <= 2.36, 'the hood at the top');
  }
});

test('each guide rod is bracketed at both ends, and the hook plate stands from the floor beam to the hood', () => {
  const kit = machine();
  const brackets = roleOf(kit, 'bracket').map(box);
  for (const g of roleOf(kit, 'guide')) {
    const rodBox = box(g);
    const near = (y) => brackets.some((b) => Math.abs(centre(b).x - centre(rodBox).x) < 0.01 && Math.abs(centre(b).y - y) < 0.06);
    assert.ok(near(rodBox.min.y + 0.0125), `no bracket at the rod's foot, ${rodBox.min.y.toFixed(2)} m`);
    assert.ok(near(rodBox.max.y - 0.0125), `no bracket at the rod's top, ${rodBox.max.y.toFixed(2)} m`);
  }
  for (const spine of roleOf(kit, 'spine').map(box)) {
    assert.ok(spine.min.y < 0.15, `the plate's foot at ${spine.min.y.toFixed(2)} m, off the floor beam's level`);
    assert.ok(spine.max.y > 2.2, 'the plate runs up to the hood');
  }
});

test('each hook plate has 13 notches opening toward the bar, 152 mm apart, and the bar\'s lug rests on the one it hangs from', () => {
  const kit = machine();
  for (const s of [-1, 1]) {
    const side = (o) => Math.sign(centre(box(o)).x) === s;
    const teeth = roleOf(kit, 'tooth').filter(side);
    assert.equal(teeth.length, 13, 'a tooth under each notch');
    // The teeth stand out of the spine toward the bar, so the notches between them open that way.
    const across = V(0, Math.sin(LEAN), Math.cos(LEAN));
    const [spineAt] = roleOf(kit, 'spine').filter(side).map((o) => o.getWorldPosition(new THREE.Vector3()).dot(across));
    for (const t of teeth) assert.ok(t.getWorldPosition(new THREE.Vector3()).dot(across) > spineAt + 0.01, `a tooth at ${box(t).min.y.toFixed(2)} m stands out the wrong way`);
    const tops = teeth.map((t) => t.getWorldPosition(new THREE.Vector3()).y).sort((a, c) => a - c);
    tops.slice(1).forEach((y, i) => close(y - tops[i], PITCH, 0.001, 'the notches\' pitch'));
    close(tops[0] + 0.05, LOWEST, 0.005, 'the lowest notch');
    // The lug stands at the bar's height, on the tooth of the notch it hangs from.
    const [lug] = roleOf(kit, 'lug').filter(side);
    const hungOn = tops.filter((y) => Math.abs(y + 0.05 - lug.getWorldPosition(new THREE.Vector3()).y) < 0.01);
    assert.equal(hungOn.length, 1, 'one notch under the lug');
    close(lug.getWorldPosition(new THREE.Vector3()).y, LOWEST + 7 * PITCH, 0.005, 'the notch it hangs on');
  }
});

/** The nearest distance from `p` to the axis of tube `mesh` (along its local y), less its half width. */
const outsideTube = (mesh, p) => {
  mesh.geometry.computeBoundingBox();
  const g = mesh.geometry.boundingBox;
  const at = mesh.getWorldPosition(new THREE.Vector3());
  const along = V(0, 1, 0).applyQuaternion(mesh.getWorldQuaternion(new THREE.Quaternion()));
  const half = (g.max.y - g.min.y) / 2 * mesh.getWorldScale(new THREE.Vector3()).y;
  const line = new THREE.Line3(at.clone().addScaledVector(along, -half), at.clone().addScaledVector(along, half));
  return line.closestPointToPoint(p, true, new THREE.Vector3()).distanceTo(p) - (g.max.x - g.min.x) / 2;
};

test('the sleeves and carriages clear every upright, along the rods from the stop to the top bracket', () => {
  const kit = machine();
  const uprights = roleOf(kit, 'upright');
  const parts = [...roleOf(kit, 'sleeve'), ...roleOf(kit, 'carriage')];
  for (const value of [kit.joints.bar.limits[0], 0.5, 1.0, 1.5, kit.joints.bar.limits[1]]) {
    kit.scrub('bar', value);
    kit.group.updateMatrixWorld(true);
    for (const o of parts) {
      const b = box(o);
      // The part's axis points: its ends and middle, wide by its own half width and height.
      const reach = o.userData.role === 'sleeve' ? 0.025 : 0.05;
      for (const t of [0, 0.5, 1]) {
        const p = new THREE.Vector3().lerpVectors(V(b.min.x, centre(b).y, centre(b).z), V(b.max.x, centre(b).y, centre(b).z), t);
        for (const u of uprights) assert.ok(outsideTube(u, p) >= reach - 0.01, `a ${o.userData.role} at value ${value.toFixed(2)} meets an upright by ${(outsideTube(u, p) * 100).toFixed(1)} cm`);
      }
    }
  }
});

test('no brand name appears in the recipe', async () => {
  const source = await readFile(new URL('./recipes/smith-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|strength|life ?fitness|\blogo\b|decal/i);
});

// No pose uses it yet, so the studio shows it alone (#176): the bar on its hook through the rep and
// scrubbed along the rods through its limits.
test('the bar scrubs from its stop to the top, and hangs on its hook with no pose', () => {
  const kit = machine();
  const racked = kit.joints.bar.value;
  assert.ok(racked > 0, 'on its hook');
  for (const limit of kit.joints.bar.limits) {
    kit.scrub('bar', limit);
    close(kit.joints.bar.value, limit, 1e-9, 'scrubbed to its limit');
  }
});

// #241: the frame, from the photos (refs/smith_machine/ref.md, part list), and a body on the bar.
const parts = (kit, name) => {
  const found = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === name) found.push(o); });
  return found;
};
const within = (mesh) => new THREE.Box3().setFromObject(mesh);

test('each rear upright sweeps forward over the top into the front upright in one capped length', () => {
  const kit = machine();
  const hoods = parts(kit, 'hood');
  assert.equal(hoods.length, 2, 'a sweep a side');
  for (const m of hoods) {
    assert.notEqual(m.geometry.type, 'ExtrudeGeometry', 'a curve, not a straight tube');
    const b = within(m);
    assert.ok(b.max.z - b.min.z > 0.3, 'from the rear upright to the front one');
    assert.ok(b.max.y - b.min.y > 0.2, 'rising as it sweeps');
  }
});

test('a gusset each side runs from the middle crossbar down to the rear upright', () => {
  const kit = machine();
  const gussets = parts(kit, 'gusset').map(within);
  assert.equal(gussets.length, 2);
  for (const b of gussets) {
    assert.ok(b.max.y > 1.9 && b.min.y < 1.7, 'from the crossbar down');
    assert.ok(Math.max(Math.abs(b.min.x), Math.abs(b.max.x)) > 0.6, 'out to the upright');
  }
});

// A body on the bar: each lift rides the rods, so its bar stays on their line through the rep.
const fails = async (id, angle) => Object.entries((await checkPose(id, { angle })).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found ?? ''}`);
const staged = (id, angle) => buildStage(prepare(id, POSES, angle));
const barOf = (stage) => stage.kit.kits.smith_machine.bodies.bar[0];
const rodZ = (y) => 0.18 - (y - FOOT_Y) * Math.tan(LEAN);

const onTheRods = async (id, angle) => {
  const stage = await staged(id, angle);
  const zero = stage.kit.kits.smith_machine.group.position.z;
  for (const d of [0, 0.25, 0.5, 0.75, 1]) {
    stage.pose(d);
    stage.kit.group.updateMatrixWorld(true);
    const at = barOf(stage).getWorldPosition(new THREE.Vector3());
    close(at.z, rodZ(at.y) + zero, 0.001, `${id}: the bar on the rods' line, ${d} of the way`);
    for (const k of ['l', 'r']) {
      if (stage.entry?.grip?.one) continue;
      const hand = heldAt(stage.rig, k);
      close(Math.hypot(hand.y - at.y, hand.z - at.z), 0, 0.03, `${id}: the ${k} hand on the bar, ${d} of the way`);
    }
  }
  return stage;
};
const travel = async (id) => {
  const stage = await staged(id);
  const y = (d) => { stage.pose(d); stage.kit.group.updateMatrixWorld(true); return barOf(stage).getWorldPosition(new THREE.Vector3()).y; };
  return [y(0), y(1)];
};

test('smith_machine_squat: the body fits through the whole rep, the bar on the rods, from standing to about parallel', async () => {
  assert.deepEqual(await fails('smith_machine_squat'), []);
  await onTheRods('smith_machine_squat');
  const [top, bottom] = await travel('smith_machine_squat');
  close(top, 1.47, 0.08, 'the bar standing');
  // Straight forward from the bar, the skin of the upper back close by, at the top and the bottom.
  const { pose, rig, kit } = await staged('smith_machine_squat');
  for (const d of [0, 1]) {
    pose(d);
    kit.group.updateMatrixWorld(true);
    const bar = kit.kits.smith_machine.bodies.bar[0].getWorldPosition(new THREE.Vector3());
    const ray = new THREE.Raycaster(bar, V(0, 0, 1));
    const [hit] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).sort((a, b) => a.distance - b.distance);
    assert.ok(hit && hit.distance < 0.06, `the bar ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm off the back, ${d} of the way`);
  }
  assert.ok(top - bottom > 0.35, `a squat's depth, ${(top - bottom).toFixed(2)} m`);
});

test('smith_machine_rdl: the body fits through the whole rep, the bar on the rods, from the thighs down the shins', async () => {
  assert.deepEqual(await fails('smith_machine_rdl'), []);
  await onTheRods('smith_machine_rdl');
  const [top, bottom] = await travel('smith_machine_rdl');
  assert.ok(top > 0.7 && bottom < 0.55, `the bar from ${top.toFixed(2)} to ${bottom.toFixed(2)} m`);
});

test('smith_machine_bench_press: the body fits through the whole rep, the bar on the rods, from locked out to the chest', async () => {
  assert.deepEqual(await fails('smith_machine_bench_press'), []);
  await onTheRods('smith_machine_bench_press');
  const [top, bottom] = await travel('smith_machine_bench_press');
  assert.ok(top - bottom > 0.3, `a press's travel, ${(top - bottom).toFixed(2)} m`);
});

test('smith_machine_jm_press: the body fits through the whole rep, the bar on the rods, down to the upper chest', async () => {
  assert.deepEqual(await fails('smith_machine_jm_press'), []);
  await onTheRods('smith_machine_jm_press');
  const [top, bottom] = await travel('smith_machine_jm_press');
  close(top - bottom, 0.2, 0.05, 'the press\'s travel');
});

// The two lifts on the adjustable bench are baked at each of its angles.
for (const id of ['smith_machine_incline_press', 'smith_machine_seated_press']) {
  for (const angle of [0, 15, 30, 45, 60, 75, 85]) {
    test(`${id} at ${angle}°: the body fits through the whole rep, the bar on the rods, the arms straight at the top`, async () => {
      assert.deepEqual(await fails(id, angle), []);
      const stage = await onTheRods(id, angle);
      stage.pose(0);
      const arm = wpos(stage.rig.bones.upperarm_l).distanceTo(heldAt(stage.rig, 'l'));
      assert.ok(arm > 0.5, `the arm long at the top, ${arm.toFixed(2)} m`);
      stage.pose(1);
      assert.ok(wpos(stage.rig.bones.upperarm_l).distanceTo(heldAt(stage.rig, 'l')) < arm - 0.1, 'bent at the bottom');
    });
  }
}

const footOn = (rig, side, y) => {
  const ball = wpos(rig.bones[`ball_${side}`]).setY(y - 0.01);
  const ray = new THREE.Raycaster(ball, V(0, 1, 0));
  const [skin] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).map((h) => h.point.y).sort((a, b) => a - b);
  return skin;
};

for (const [id, step] of [['smith_machine_lunge', 0], ['split_squat_smith', 0], ['ffe_smith_machine_lunge', 0.1]]) {
  test(`${id}: the body fits through the whole rep, the bar on the rods and the back, the front foot flat, the back knee down near the floor`, async () => {
    assert.deepEqual(await fails(id), []);
    const stage = await onTheRods(id);
    const { pose, rig, kit } = stage;
    for (const d of [0, 1]) {
      pose(d);
      kit.group.updateMatrixWorld(true);
      const bar = barOf(stage).getWorldPosition(new THREE.Vector3());
      const ray = new THREE.Raycaster(bar, V(0, 0, 1));
      const [hit] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).sort((a, b) => a.distance - b.distance);
      assert.ok(hit && hit.distance < 0.07, `the bar ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm off the back, ${d} of the way`);
      const sole = footOn(rig, 'l', step);
      assert.ok(sole !== undefined, 'a front sole');
      close(sole, step, 0.01, `the front foot on its ${step ? 'step' : 'floor'}, ${d} of the way`);
    }
    pose(1);
    assert.ok(wpos(rig.bones.calf_r).y < 0.3, `the back knee down, at ${wpos(rig.bones.calf_r).y.toFixed(2)}`);
    const [top, bottom] = await travel(id);
    assert.ok(top - bottom > 0.25, `the lunge's drop, ${(top - bottom).toFixed(2)} m`);
  });
}

// The posed stage isn't what ships: a part a track can't bind to stays at its rest in the clip.
for (const [id, angle] of [
  ...['smith_machine_squat', 'smith_machine_rdl', 'smith_machine_bench_press', 'smith_machine_jm_press', 'smith_machine_lunge', 'ffe_smith_machine_lunge', 'split_squat_smith'].map((n) => [n]),
  ['smith_machine_incline_press', 45], ['smith_machine_incline_press', 85], ['smith_machine_seated_press', 75], ['smith_machine_seated_press', 0],
]) {
  test(`${id}${angle === undefined ? '' : ` at ${angle}°`}: the baked clip shows every part of the kit where the pose has it`, async () => {
    const { pose, kit } = await staged(id, angle);
    const file = `${id}${angle === undefined ? '' : `_${angle}`}.glb`;
    const { scene, tracks } = await openClip(fileURLToPath(new URL(`../../app/src/main/assets/mannequins/${file}`, import.meta.url)));
    const last = tracks[0].times.length - 1;
    const live = [];
    kit.group.traverse((o) => { if (o.isMesh && !o.userData.proxy) live.push(o); });
    for (const f of [Math.round(last / 2), last]) {
      pose(f / last);
      kit.group.updateMatrixWorld(true);
      poseFrame(scene, tracks, f);
      for (const o of live) {
        const baked = scene.getObjectByName(o.name);
        assert.ok(baked, `${o.name} is in the clip`);
        const off = baked.getWorldPosition(new THREE.Vector3()).distanceTo(o.getWorldPosition(new THREE.Vector3()));
        assert.ok(off < 0.005, `${o.name} ${(off * 100).toFixed(1)} cm off the pose at frame ${f}`);
      }
    }
  });
}
