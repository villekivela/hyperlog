// The shrug machine (#223, #241), from refs/shrug_machine/ref.md: two heavy levers, one a side, each
// turning about a transverse axis at the top of a rear A-frame, a hoop rising from each with the grip
// pointing forward and a plate horn near its front end; a raked column carries the back pad and a
// seat, braced back to the cross tube at the pivot. Its one lift is posed standing, the legs either
// side of the lowered seat.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { EQUIPMENT, KITS, MACHINES, buildKit } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { openClip, poseFrame } from './glb.mjs';
import { heldAt, V, wpos } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const machine = (options = {}) => {
  const kit = buildKit({ equipment: 'machine', kit: { shrug_machine: options } }).kits.shrug_machine;
  // With no pose to drive it, the levers rest on their stop.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const grips = (kit) => kit.contacts.find((c) => c.point === 'grips').at;
const box = (kit) => new THREE.Box3().setFromObject(kit.group);

test('the shrug machine is the kit of the shrug machine kind, and serves machine_shrug', () => {
  assert.ok(KITS.shrug_machine, 'a recipe named shrug_machine');
  assert.equal(MACHINES.shrug_machine.kit, 'shrug_machine');
  assert.deepEqual(MACHINES.shrug_machine.exercises, ['machine_shrug']);
  assert.ok(EQUIPMENT.machine.includes('shrug_machine'));
});

test('both levers are one revolute joint about a transverse axis, 7.5° to 26.3° up from level ahead', () => {
  const { levers } = machine().joints;
  assert.equal(levers.type, 'revolute');
  assert.equal(Math.abs(levers.axis.x), 1);
  assert.equal(levers.axis.y, 0);
  assert.equal(levers.axis.z, 0);
  // The ref: the grip 700 above the floor on its stop and 940 at the top of a shrug, on a pivot 600 up.
  close(THREE.MathUtils.radToDeg(levers.limits[0]), 7.5, 0.1, 'the stop, degrees above level');
  close(THREE.MathUtils.radToDeg(levers.limits[1]), 26.3, 0.1, 'the top, degrees above level');
  close(levers.origin.y, 0.6, 1e-9, 'the pivot above the floor');
});

test('the grips rest at 700 mm, 740 apart, 760 ahead of the pivot, and scrub to 940', () => {
  const kit = machine();
  const [left, right] = grips(kit);
  close(left.y, 0.7, 0.005, 'the left grip at rest');
  close(Math.abs(left.x - right.x), 0.74, 1e-6, 'the grips apart');
  close(left.z - kit.joints.levers.origin.z, 0.76, 0.005, 'the grips ahead of the pivot');
  kit.scrub('levers', 99);
  close(grips(kit)[0].y, 0.94, 0.005, 'the grip at the top of the lever');
  kit.scrub('levers', -99);
  close(grips(kit)[0].y, 0.7, 0.005, 'back on its stop');
});

test('scrubbing moves the levers, and the frame stays', () => {
  const kit = machine();
  const centre = (node) => new THREE.Box3().setFromObject(node).getCenter(new THREE.Vector3());
  const [lever] = kit.bodies.levers;
  const frame = kit.group.children.find((o) => o !== lever);
  const [lever0, frame0] = [centre(lever), centre(frame)];
  kit.scrub('levers', 0.4);
  kit.group.updateMatrixWorld(true);
  assert.ok(centre(lever).distanceTo(lever0) > 0.01, 'a lever part did not move');
  assert.ok(centre(frame).distanceTo(frame0) < 1e-9, 'the frame moved with the levers');
});

test('it stands 1.02 m deep, 1.24 m tall and about 1.52 m across, a plate horn on each lever', () => {
  const size = box(machine()).getSize(new THREE.Vector3());
  close(size.z, 1.02, 0.08, 'front to back');
  close(size.y, 1.24, 0.1, 'tall');
  close(size.x, 1.52, 0.02, 'across');
  const horns = [];
  machine().group.traverse((o) => { if (o.isMesh && o.userData.role === 'horn') horns.push(o); });
  assert.equal(horns.length, 2);
});

test('the seat top is 650 mm, and the recipe carries no brand', async () => {
  const kit = machine();
  let seat;
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'seat') seat = new THREE.Box3().setFromObject(o).max.y; });
  close(seat, 0.65, 0.01, 'the seat top');
  const source = await readFile(new URL('./recipes/shrug-machine.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /hammer|life ?fitness|strength|pl-sh/i);
});

test('a fit rule sets the seat, at its measured 650 mm by default', () => {
  const rules = KITS.shrug_machine.fit;
  assert.deepEqual(rules.map((r) => r.sets), ['seat']);
  const top = (kit) => { let y; kit.group.traverse((o) => { if (o.isMesh && o.userData.role === 'seat') y = new THREE.Box3().setFromObject(o).max.y; }); return y; };
  close(top(machine({ seat: 0.7 })), 0.7, 0.01, 'the seat set to 700 mm');
});

const roles = (kit, name) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.role === name) out.push(o); });
  return out;
};
const bounds = (mesh) => new THREE.Box3().setFromObject(mesh);
const one = (kit, name) => {
  const found = roles(kit, name);
  assert.equal(found.length, 1, `one ${name}`);
  return found[0];
};
const along = (from, way, mesh) => new THREE.Raycaster(from, way.clone().normalize()).intersectObject(mesh, false)[0];
/** Whether any vertex of `moving` lies in the box of `fixed`, in `fixed`'s own frame. */
const reaches = (moving, fixed) => {
  fixed.geometry.computeBoundingBox();
  const local = fixed.matrixWorld.clone().invert().multiply(moving.matrixWorld);
  const p = moving.geometry.attributes.position;
  const v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) if (fixed.geometry.boundingBox.containsPoint(v.fromBufferAttribute(p, i).applyMatrix4(local))) return true;
  return false;
};
/** The vertex of `mesh` that scores highest by `score`, in the world. */
const extreme = (mesh, score) => {
  const p = mesh.geometry.attributes.position;
  let best;
  for (let i = 0; i < p.count; i++) {
    const v = new THREE.Vector3().fromBufferAttribute(p, i).applyMatrix4(mesh.matrixWorld);
    if (!best || score(v) > score(best)) best = v;
  }
  return best;
};
const RAKE = (8.8 * Math.PI) / 180;
const REARWARD = V(0, Math.sin(RAKE), -Math.cos(RAKE));

// The frame, from the photos (refs/shrug_machine/ref.md, part list).
test('a rectangular base of 75 mm tube stands 38 mm off the floor on a foot at each corner', () => {
  const kit = machine();
  const rails = roles(kit, 'rail').map(bounds);
  assert.equal(rails.length, 2, 'a side rail each side');
  for (const b of rails) { close(b.min.y, 0.038, 0.002, 'its underside'); close(b.max.y, 0.113, 0.002, 'its top'); close(b.max.z - b.min.z, 0.9, 0.02, 'its length'); }
  assert.equal(roles(kit, 'foot').length, 4, 'four feet');
  for (const f of roles(kit, 'foot').map(bounds)) { close(f.min.y, 0, 0.001, 'on the floor'); assert.ok(f.max.y >= 0.038, 'up to the rail'); }
  assert.equal(roles(kit, 'crossBeam').length, 2);
});

test('a column rakes back 8.8 degrees from a cross beam, the back pad flush on it, and a brace runs back to the cross tube at the pivot', () => {
  const kit = machine();
  const column = one(kit, 'column');
  const b = bounds(column);
  close(b.min.y, 0.113, 0.002, 'cut flat on the cross beam');
  close(b.max.y, 1.09, 0.02, 'its top');
  const pad = one(kit, 'backPad');
  const hit = along(pad.getWorldPosition(new THREE.Vector3()), REARWARD, column);
  assert.ok(hit && Math.abs(hit.distance - 0.04) < 0.01, `the pad ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm from the middle of it to the column`);
  close(bounds(pad).max.y, 1.24, 0.02, 'the pad\'s top');
  const brace = one(kit, 'brace');
  const crossTube = one(kit, 'crossTube');
  const cross = bounds(crossTube);
  assert.ok(reaches(brace, crossTube), 'the brace ends in the cross tube');
  const top = extreme(brace, (v) => v.y);
  assert.ok(top.y > 0.9, 'high on the column');
  const columnZ = (y) => bounds(roles(kit, 'crossBeam').map((m) => m).sort((p, q) => bounds(q).max.z - bounds(p).max.z)[0]).getCenter(new THREE.Vector3()).z - (y - 0.113) * Math.tan(RAKE);
  close(top.z, columnZ(top.y), 0.04, 'the brace ends in the column');
  close(cross.max.x - cross.min.x, 0.9, 0.02, 'the cross tube from upright to upright');
});

test('an A-frame each side carries the pivot, both legs cut flat on the rail, with a storage horn', () => {
  const kit = machine();
  const frames = roles(kit, 'aFrame');
  assert.equal(frames.length, 2, 'one mitred length a side');
  const pivot = kit.joints.levers.origin;
  for (const m of frames) {
    assert.notEqual(m.geometry.type, 'ExtrudeGeometry', 'mitred at the top, not two lapped tubes');
    const b = bounds(m);
    close(b.min.y, 0.113, 0.002, 'cut flat on the rail');
    assert.ok(b.max.y >= pivot.y - 0.05, 'up into the pivot\'s block');
    assert.ok(b.max.y <= pivot.y + 0.05, 'the mitre\'s tip ending inside it');
    assert.ok(pivot.z > b.min.z + 0.05 && pivot.z < b.max.z - 0.05, 'the pivot between its feet');
    assert.ok(b.max.z - b.min.z > 0.3, 'its feet apart');
  }
  assert.equal(roles(kit, 'storageHorn').length, 2);
  // The rear cross beam stops flush with the rails' ends.
  const rear = Math.min(...roles(kit, 'rail').map((m) => bounds(m).min.z));
  for (const m of roles(kit, 'crossBeam')) assert.ok(bounds(m).min.z >= rear - 1e-6, 'no beam proud of the rails');
});

test('each lever rests on a stop on its A-frame', () => {
  const kit = machine();
  const stops = roles(kit, 'stop');
  assert.equal(stops.length, 2);
  const beams = roles(kit, 'beam');
  for (const stop of stops) {
    const b = bounds(stop);
    const beam = beams.find((m) => Math.sign(bounds(m).min.x) === Math.sign(b.min.x));
    const hit = along(b.getCenter(new THREE.Vector3()), V(0, 1, 0), beam);
    assert.ok(hit, 'under the beam');
    close(hit.point.y, b.max.y, 0.006, 'the beam down on it');
    assert.ok(Math.max(Math.abs(b.min.x), Math.abs(b.max.x)) >= 0.415, 'out to the A-frame');
  }
});

test('the levers clear the frame through their whole travel', () => {
  const kit = machine();
  const [low, high] = kit.joints.levers.limits;
  const moving = new Set();
  kit.bodies.levers[0].traverse((o) => { if (o.isMesh) moving.add(o); });
  const solid = (o) => o.userData.solid;
  const fixed = [];
  kit.group.traverse((o) => { if (o.isMesh && solid(o) && !moving.has(o) && !['crossTube', 'stop'].includes(o.userData.role)) fixed.push(o); });
  assert.ok(fixed.length > 8, 'solid frame parts to meet');
  const arms = [...moving].filter(solid);
  assert.ok(arms.length > 8, 'solid lever parts');
  for (const v of [low + 0.002, (low + high) / 2, high]) {
    kit.scrub('levers', v);
    kit.group.updateMatrixWorld(true);
    for (const m of arms) for (const f of fixed) assert.ok(!reaches(m, f) && !reaches(f, m), `${m.name} ${m.userData.role ?? ''} meets ${f.name} ${f.userData.role ?? ''} at ${(v * 57.3).toFixed(0)}°`);
  }
});

test('each lever is a heavy beam sloping down toward the front, a hoop up from it to the grip, the horn near its front end', () => {
  const kit = machine();
  const beams = roles(kit, 'beam');
  assert.equal(beams.length, 2);
  const pivot = kit.joints.levers.origin;
  for (const m of beams) {
    const b = bounds(m);
    close(b.max.z - pivot.z, 0.63, 0.04, 'its reach');
    assert.ok(b.min.y < pivot.y - 0.15, 'its front end down toward the plate');
    assert.ok(b.max.x - b.min.x < 0.06, 'narrow across');
  }
  const hoops = roles(kit, 'hoop');
  assert.equal(hoops.length, 2);
  const [left] = grips(kit);
  for (const h of hoops) {
    assert.notEqual(h.geometry.type, 'TubeGeometry', 'capped');
    const b = bounds(h);
    assert.ok(reaches(h, beams.find((m) => Math.sign(bounds(m).min.x) === Math.sign(b.min.x))), 'its foot in the beam');
    assert.ok(b.max.y >= left.y - 0.02, 'up to the grip');
  }
  for (const m of roles(kit, 'horn')) { const b = bounds(m); close(b.getCenter(new THREE.Vector3()).z - pivot.z, 0.57, 0.04, 'near the front end'); }
});

test('the seat rides a sleeve on the column, and stands where `seat` sets it', () => {
  const low = machine({ seat: 0.45 });
  close(bounds(one(low, 'seat')).max.y, 0.45, 0.01, 'the seat lowered');
  const sleeve = bounds(one(low, 'seatSleeve'));
  const middle = sleeve.getCenter(new THREE.Vector3());
  const beam = roles(low, 'crossBeam').map(bounds).sort((a, b) => b.max.z - a.max.z)[0];
  close(middle.z, beam.getCenter(new THREE.Vector3()).z - (middle.y - 0.113) * Math.tan(RAKE), 0.005, 'the sleeve round the column');
  assert.ok(sleeve.max.y > 0.4 && sleeve.min.y < 0.45, 'at the seat');
});

// The owner's bar (#241): no open tube end.
test('no tube on it stands open', () => {
  const rims = [];
  machine().group.traverse((o) => {
    if (!o.isMesh || ['CylinderGeometry', 'SphereGeometry'].includes(o.geometry.type) || o.userData.bored) return;
    const seen = new Map();
    const p = o.geometry.attributes.position;
    const i = o.geometry.index?.array ?? Array.from({ length: p.count }, (_, n) => n);
    const key = (n) => `${p.getX(n).toFixed(3)},${p.getY(n).toFixed(3)},${p.getZ(n).toFixed(3)}`;
    for (let t = 0; t < i.length; t += 3) for (const [a, b] of [[i[t], i[t + 1]], [i[t + 1], i[t + 2]], [i[t + 2], i[t]]]) {
      if (key(a) === key(b)) continue;
      const e = [key(a), key(b)].sort().join('|');
      seen.set(e, (seen.get(e) ?? 0) + 1);
    }
    if ([...seen.values()].some((n) => n === 1)) rims.push(`${o.name} ${o.userData.role ?? ''}`);
  });
  assert.deepEqual(rims, []);
});

// #241: a body fits it. The checks hold the hands on the grips, the feet on the floor, the elbows
// straight and nothing through the body, on every frame of the rep.
const ID = 'machine_shrug';
const fails = async () => Object.entries((await checkPose(ID)).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found ?? ''}`);

test('machine_shrug: the body fits the machine through the whole rep', async () => {
  assert.deepEqual(await fails(), []);
});

test('machine_shrug: the shoulders rise and the hands rise with them, the elbows staying straight', async () => {
  const { pose, rig, kit } = await buildStage(prepare(ID));
  const k = kit.kits.shrug_machine;
  const bend = (side) => {
    const [shoulder, elbow, hand] = ['upperarm', 'lowerarm', 'hand'].map((b) => wpos(rig.bones[`${b}_${side}`]));
    return THREE.MathUtils.radToDeg(elbow.clone().sub(shoulder).angleTo(hand.clone().sub(elbow)));
  };
  const at = (d) => {
    pose(d);
    for (const side of ['l', 'r']) assert.ok(bend(side) < 15, `the ${side} elbow bent ${bend(side).toFixed(0)}°, ${d} of the way`);
    return { hand: heldAt(rig, 'l').y, shoulder: wpos(rig.bones.upperarm_l).y, value: k.joints.levers.value };
  };
  at(0.5);
  const [high, low] = [at(0), at(1)];
  assert.ok(high.shoulder - low.shoulder > 0.05, `the shoulders' rise, ${(high.shoulder - low.shoulder).toFixed(3)} m`);
  close(high.hand - low.hand, high.shoulder - low.shoulder, 0.012, 'the hands rising as far as the shoulders');
  assert.ok(high.value > low.value, 'the levers rising with them');
});

test('machine_shrug: the legs stand either side of the lowered seat, the feet flat on the floor ahead of the cross beam', async () => {
  const { pose, rig, kit } = await buildStage(prepare(ID));
  const k = kit.kits.shrug_machine;
  const seatMesh = one(k, 'seat');
  const seat = bounds(seatMesh);
  const beam = roles(k, 'crossBeam').map(bounds).sort((a, b) => b.max.z - a.max.z)[0];
  assert.ok(seat.max.y < 0.56, `the seat lowered, at ${seat.max.y.toFixed(2)}`);
  for (const d of [0, 0.5, 1]) {
    pose(d);
    for (const side of ['l', 'r']) {
      const heel = wpos(rig.bones[`foot_${side}`]);
      assert.ok(heel.z - 0.07 > beam.max.z, `the ${side} heel ahead of the cross beam, at ${heel.z.toFixed(2)}`);
      assert.ok(Math.abs(heel.x) < 0.42, 'between the rails');
      // Straight up from the floor under the ball of the foot: the sole's skin on it.
      const ball = wpos(rig.bones[`ball_${side}`]).setY(-0.01);
      const ray = new THREE.Raycaster(ball, V(0, 1, 0));
      const [skin] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).map((h) => h.point.y).sort((a, b) => a - b);
      assert.ok(skin !== undefined, `a sole over the floor, ${d} of the way`);
      close(skin, 0, 0.008, `the ${side} sole on the floor, ${d} of the way`);
      // Out from the middle at the seat's height, level with the leg: the leg's skin clear of its edge.
      const sign = side === 'l' ? 1 : -1;
      for (const y of [seat.max.y, seat.min.y]) {
        const out = new THREE.Raycaster(V(0, y, wpos(rig.bones[`calf_${side}`]).z), V(sign, 0, 0));
        const [leg] = rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return out.intersectObject(m, false); }).map((h) => Math.abs(h.point.x)).sort((a, b) => a - b);
        assert.ok(leg > seat.max.x + 0.005, `the ${side} leg ${(leg - seat.max.x).toFixed(3)} m clear of the seat, ${d} of the way`);
      }
    }
  }
});

test('machine_shrug: the baked clip shows every part of the kit where the pose has it', async () => {
  const { pose, kit } = await buildStage(prepare(ID));
  const { scene, tracks } = await openClip(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'mannequins', `${ID}.glb`));
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
