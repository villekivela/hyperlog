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
  const beams = roleOf(kit, 'beam').map(box);
  for (const spineMesh of roleOf(kit, 'spine')) {
    const spine = box(spineMesh);
    close(spine.min.y, 0.07, 0.006, 'the plate\'s foot, down on the floor beam\'s level');
    assert.ok(spine.max.y > 2.2, 'the plate runs up to the hood');
    assert.ok(beams.some((b) => b.min.x <= spine.max.x && b.max.x >= spine.min.x), 'the plate over its floor beam');
    const upright = roleOf(kit, 'uprightCore').filter((o) => Math.sign(o.position.x) === Math.sign(centre(spine).x)).sort((a, c) => c.position.z - a.position.z)[0];
    const from = spineMesh.getWorldPosition(new THREE.Vector3()).setX(upright.position.x);
    const hit = new THREE.Raycaster(from, V(0, Math.sin(LEAN), Math.cos(LEAN))).intersectObject(upright, false)[0];
    assert.ok(hit && hit.distance < 0.03, `the plate ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm off the upright's rear face`);
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
    for (const t of teeth) assert.ok(t.getWorldPosition(new THREE.Vector3()).dot(across) < spineAt - 0.01, `a tooth at ${box(t).min.y.toFixed(2)} m stands out the wrong way`);
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

/** Whether any vertex of `moving` lies in the box of `fixed`, in `fixed`'s own frame. */
const reaches = (moving, fixed) => {
  fixed.geometry.computeBoundingBox();
  const local = fixed.matrixWorld.clone().invert().multiply(moving.matrixWorld);
  const p = moving.geometry.attributes.position;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) if (fixed.geometry.boundingBox.containsPoint(v.fromBufferAttribute(p, i).applyMatrix4(local))) return true;
  return false;
};
const sideOf = (s) => (o) => Math.sign(new THREE.Box3().setFromObject(o).getCenter(new THREE.Vector3()).x) === s;

test('each rear upright sweeps forward over the top into the front upright in one capped curve', () => {
  const kit = machine();
  const hoods = parts(kit, 'hood');
  assert.equal(hoods.length, 2, 'a sweep a side');
  for (const [m, s] of [[hoods.find(sideOf(-1)), -1], [hoods.find(sideOf(1)), 1]]) {
    assert.ok(m.geometry.attributes.position.count > 150, 'a curve of many lengths, not one straight tube');
    const b = within(m);
    assert.ok(b.max.z - b.min.z > 0.3, 'from the rear upright to the front one');
    assert.ok(b.max.y - b.min.y > 0.2, 'rising as it sweeps');
    const [front, rear] = parts(kit, 'uprightCore').filter(sideOf(s)).sort((p, q) => q.position.z - p.position.z);
    assert.ok(reaches(m, front), 'its end in the front upright');
    assert.ok(reaches(m, rear), 'its foot in the rear upright');
  }
});

test('side-on it stands as an A: the front upright a quarter of the way along the beam, then plate, rod and shroud, the rear leaning forward to meet it', () => {
  const kit = machine();
  const [beam] = roleOf(kit, 'beam').map(box);
  for (const s of [-1, 1]) {
    const [front, rear] = parts(kit, 'uprightCore').filter(sideOf(s)).sort((p, q) => q.position.z - p.position.z).map((m) => {
      const p = m.geometry.attributes.position;
      const all = Array.from({ length: p.count }, (_, i) => new THREE.Vector3().fromBufferAttribute(p, i).applyMatrix4(m.matrixWorld)).sort((c, d) => c.y - d.y);
      const middle = (vs) => vs.reduce((u, v) => u.add(v), new THREE.Vector3()).multiplyScalar(1 / vs.length);
      return { foot: middle(all.slice(0, 4)), top: middle(all.slice(-4)) };
    });
    close((beam.max.z - front.foot.z) / (beam.max.z - beam.min.z), 0.25, 0.05, 'the front upright along the beam');
    close((rear.foot.z - beam.min.z) / (beam.max.z - beam.min.z), 0.06, 0.05, 'the rear upright at the beam\'s end');
    assert.ok(front.top.z < front.foot.z, 'the front upright leaning back');
    assert.ok(rear.top.z > rear.foot.z + 0.15, 'the rear one leaning forward');
    const z = (m) => m.getWorldPosition(new THREE.Vector3()).z;
    const [spine] = parts(kit, 'spine').filter(sideOf(s));
    const [rod] = parts(kit, 'guide').filter(sideOf(s));
    const at = (m, y) => z(m) + (m.getWorldPosition(new THREE.Vector3()).y - y) * Math.tan(LEAN);
    const uprightZ = front.foot.z + (front.top.z - front.foot.z) * (1.2 - front.foot.y) / (front.top.y - front.foot.y);
    assert.ok(uprightZ > at(spine, 1.2) && at(spine, 1.2) > at(rod, 1.2), 'upright, plate, rod, front to rear');
  }
});

test('both uprights stand cut flat on the floor beam', () => {
  const kit = machine();
  for (const m of parts(kit, 'upright')) {
    const p = m.geometry.attributes.position;
    const v = new THREE.Vector3();
    const low = [];
    for (let i = 0; i < p.count; i++) { v.fromBufferAttribute(p, i).applyMatrix4(m.matrixWorld); if (v.y < 0.1) low.push(v.y); }
    assert.ok(low.length, 'a foot');
    close(Math.max(...low) - Math.min(...low), 0, 0.001, 'its foot level');
  }
});

test('a gusset each side runs from the middle crossbar down to the rear upright, ending in each', () => {
  const kit = machine();
  const gussets = parts(kit, 'gusset');
  assert.equal(gussets.length, 2);
  const [bar] = parts(kit, 'crossbar');
  for (const s of [-1, 1]) {
    const g = gussets.find(sideOf(s));
    const b = within(g);
    assert.ok(b.max.y > 1.9 && b.min.y < 1.7, 'from the crossbar down');
    assert.ok(reaches(g, bar), 'its top in the crossbar');
    const rear = parts(kit, 'uprightCore').filter(sideOf(s)).sort((p, q) => p.position.z - q.position.z)[0];
    assert.ok(reaches(g, rear), 'its foot in the rear upright');
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
    for (const k of ['l', 'r']) {
      // The kit puts the bar on the line whatever the pose: the hands show the body solved to it.
      const hand = heldAt(stage.rig, k);
      close(hand.z, rodZ(hand.y) + zero, 0.03, `${id}: the ${k} hand on the rods' line, ${d} of the way`);
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
  close(top, 1.53, 0.03, 'the bar standing');
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

// The hands close round the bar in every lift: from the bar, the fingertips lie across it from the palm.
for (const id of ['smith_machine_squat', 'smith_machine_lunge', 'ffe_smith_machine_lunge', 'split_squat_smith']) {
  test(`${id}: the fingers wrap round the bar`, async () => {
    const stage = await staged(id);
    for (const d of [0, 1]) {
      stage.pose(d);
      stage.kit.group.updateMatrixWorld(true);
      const bar = barOf(stage).getWorldPosition(new THREE.Vector3());
      for (const side of ['l', 'r']) {
        const palm = wpos(stage.rig.bones[`hand_${side}`]);
        const on = bar.clone().setX(palm.x);
        const wrap = palm.clone().sub(on).angleTo(wpos(stage.rig.bones[`middle_03_${side}`]).sub(on)) * 180 / Math.PI;
        assert.ok(wrap > 150, `the ${side} fingers ${wrap.toFixed(0)}° round the bar, ${d} of the way`);
      }
    }
  });
}

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
      const [shoulder, elbow, hand] = ['upperarm_l', 'lowerarm_l', 'hand_l'].map((n) => wpos(stage.rig.bones[n]));
      const bend = THREE.MathUtils.radToDeg(elbow.clone().sub(shoulder).angleTo(hand.clone().sub(elbow)));
      // A soft lockout, as the bench press's 19° is: a held bar stops the arm short of dead straight.
      assert.ok(bend < 16, `the elbow bent ${bend.toFixed(0)}° at the top`);
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
    kit.group.updateMatrixWorld(true);
    const knee = wpos(rig.bones.calf_r).y;
    assert.ok(knee < (step ? step : 0.2), `the back knee down near the floor${step ? ', below the step' : ''}, at ${knee.toFixed(2)}`);
    const bar = barOf(stage).getWorldPosition(new THREE.Vector3());
    const front = wpos(rig.bones.foot_l);
    assert.ok(Math.abs(front.z - bar.z) < 0.2, `the front foot under the bar, ${(front.z - bar.z).toFixed(2)} m ahead of it`);
    assert.ok(front.z - wpos(rig.bones.foot_r).z > 0.65, `a long stride, ${(front.z - wpos(rig.bones.foot_r).z).toFixed(2)} m`);
    const [top, bottom] = await travel(id);
    assert.ok(top - bottom > 0.25, `the lunge's drop, ${(top - bottom).toFixed(2)} m`);
  });
}

test('the raised front foot takes the bar further down than the plain lunge', async () => {
  const drop = async (id) => { const [top, bottom] = await travel(id); return top - bottom; };
  const [raised, plain] = [await drop('ffe_smith_machine_lunge'), await drop('smith_machine_lunge')];
  assert.ok(raised > plain + 0.05, `${raised.toFixed(2)} against ${plain.toFixed(2)} m`);
});

const LIFTS = [
  ...['smith_machine_squat', 'smith_machine_rdl', 'smith_machine_bench_press', 'smith_machine_jm_press', 'smith_machine_lunge', 'ffe_smith_machine_lunge', 'split_squat_smith'].map((id) => [id]),
  ...['smith_machine_incline_press', 'smith_machine_seated_press'].flatMap((id) => [0, 15, 30, 45, 60, 75, 85].map((angle) => [id, angle])),
];

for (const [id, angle] of LIFTS) {
  test(`${id}${angle === undefined ? '' : ` at ${angle}°`}: the stops lie on the notch just below the bottom of the lift, and the hooks stay off the teeth`, async () => {
    const stage = await staged(id, angle);
    const k = stage.kit.kits.smith_machine;
    stage.pose(1);
    const lowest = k.joints.bar.limits[0];
    const value = k.joints.bar.value;
    assert.ok(value >= lowest, 'above the stops');
    assert.ok((value - lowest) * Math.cos(LEAN) < PITCH, `${((value - lowest) * 100).toFixed(0)} cm above the stops, more than a notch`);
    stage.kit.group.updateMatrixWorld(true);
    for (const lug of parts(k, 'lug')) for (const tooth of parts(k, 'tooth')) assert.ok(!reaches(lug, tooth) && !reaches(tooth, lug), 'a hook through a tooth');
  });
}

const forearmOff = (rig, side) => THREE.MathUtils.radToDeg(wpos(rig.bones[`hand_${side}`]).sub(wpos(rig.bones[`lowerarm_${side}`])).angleTo(V(0, 1, 0)));
for (const [id, angle] of LIFTS.filter(([id]) => /bench_press|incline|seated/.test(id))) {
  test(`${id}${angle === undefined ? '' : ` at ${angle}°`}: at the bottom the forearms stand under the bar and it comes down to the body`, async () => {
    const { pose, rig, kit } = await staged(id, angle);
    pose(1);
    kit.group.updateMatrixWorld(true);
    for (const side of ['l', 'r']) assert.ok(forearmOff(rig, side) < 25, `the ${side} forearm ${forearmOff(rig, side).toFixed(0)}° off upright`);
    const bar = kit.kits.smith_machine.bodies.bar[0].getWorldPosition(new THREE.Vector3());
    // Square back at the trunk from the bar.
    const up = wpos(rig.bones.upperarm_l).add(wpos(rig.bones.upperarm_r)).multiplyScalar(0.5).sub(wpos(rig.bones.pelvis)).setX(0).normalize();
    const forward = V(1, 0, 0).cross(up).normalize();
    // From a little out, as the bar may rest on the skin.
    const ray = new THREE.Raycaster(bar.clone().addScaledVector(forward, 0.1), forward.clone().negate());
    const [hit] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).sort((a, b) => a.distance - b.distance);
    assert.ok(hit && hit.distance - 0.1 < 0.18, `the bar ${hit ? ((hit.distance - 0.1) * 100).toFixed(1) : 'nowhere'} cm off the body`);
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
