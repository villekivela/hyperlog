// The smith machine (#182), from refs/smith_machine/ref.md: a bar on two guide rods leaning 7° with
// the top toward the back, a carriage at each end whose hooks turn onto notches in a plate behind
// the rod, safety stops on the same plates, and plate horns on two padded rear uprights.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { EQUIPMENT, KITS, MACHINES, buildKit, shownAlone } from './kit.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { V, buildRig, rad, wpos } from './rig.mjs';

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'smith_machine', kit: { smith_machine: options } }).kits.smith_machine;
  // With no pose to drive it, the bar hangs on its hooks.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const deg = (r) => (r * 180) / Math.PI;
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
  // The ref: the notch at about 1.36 m, for a squat's bar at about 1.47 m.
  close(barHeight(kit), LOWEST + 7 * PITCH, 0.005, 'the racked bar\'s height');
  close(machine({ hook: 3 }).joints.bar.value, machine().joints.bar.value - 4 * PITCH / Math.cos(LEAN), 1e-6, 'four notches lower, along the rods');
  assert.ok(HIGHEST > LOWEST + 12 * PITCH - 0.01 && HIGHEST < LOWEST + 12 * PITCH + 0.01, 'thirteen notches span the ref\'s lowest to highest hook');
});

test('its nine exercises are the library\'s smith machine ones', async () => {
  const library = JSON.parse(await readFile(new URL('../../app/src/main/assets/exercises.json', import.meta.url), 'utf8'));
  const ids = library.exercises.filter((e) => e.equipment === 'smith_machine').map((e) => e.id).sort();
  assert.equal(ids.length, 9);
  assert.ok(Array.isArray(EQUIPMENT[library.exercises.find((e) => e.id === ids[0]).equipment]), 'their equipment has kits');
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
// 0.44 m pad: where each lift has the bar standing and at its lowest. The mannequin's shoulder
// joint plus 2 cm is where a squat's bar rides standing.
test('the bar\'s limits and the hooks span every lift the ref gives, from a squat\'s 1.47 m to a bench\'s chest', async () => {
  const bytes = await readFile(MODEL);
  const rig = buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
  rig.root.updateMatrixWorld(true);
  const shoulder = Math.min(...['l', 'r'].map((k) => wpos(rig.bones[`upperarm_${k}`]).y));
  const bench = 0.44;
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
      assert.ok(u.max.y >= 2.3, 'an upright to the top');
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
test('the studio shows it alone, the bar on its hook through the rep and scrubbed from its stop to the top', async () => {
  const entry = shownAlone('smith_machine');
  assert.equal(entry.equipment, 'smith_machine');
  assert.ok(entry.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  const kit = stage.kit.kits.smith_machine;
  stage.pose(0);
  const racked = kit.joints.bar.value;
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    close(kit.joints.bar.value, racked, 1e-9, `on its hook ${d} of the way down`);
  }
  for (const limit of kit.joints.bar.limits) {
    kit.scrub('bar', limit);
    stage.scene.updateMatrixWorld(true);
    close(kit.joints.bar.value, limit, 1e-9, 'scrubbed to its limit');
  }
});
