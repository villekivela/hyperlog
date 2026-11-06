// The 45-degree hyperextension bench (#220), from refs/hyperextension_bench/ref.md: a rigid frame
// with an angled hip pad, a foot plate and two heel rollers. Nothing on it moves. Tested at three
// seams: what `kit.mjs` serves, the built kit's measurements against the ref's own numbers (a literal
// each, from the spec sheet's drawing), and the pose on it (#241), read from the real meshes.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, STATIONS, buildKit } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';
import { prepare } from './entries.mjs';
import { checkPose } from './checks.mjs';
import { openClip, poseFrame } from './glb.mjs';
import { V, wpos } from './rig.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

test('it is the kit of the 45-degree hyperextension, the one exercise the ref serves', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'hyperextension_bench', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (`[^.]+?`)\./)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.deepEqual(named, ['45_hyperextension']);
  assert.deepEqual(STATIONS.hyperextension_bench, { kit: 'hyperextension_bench', exercises: named });
  assert.ok(KITS.hyperextension_bench, 'a recipe named hyperextension_bench');
  const kind = library.exercises.find((e) => e.id === '45_hyperextension').equipment;
  assert.ok(EQUIPMENT[kind].includes('hyperextension_bench'), `${kind} has no hyperextension_bench kit`);
  assert.ok(POSES['45_hyperextension'], 'it is posed');
});

const bench = () => {
  const kit = buildKit({ equipment: 'weighted_bodyweight', kit: { hyperextension_bench: {} } }).kits.hyperextension_bench;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o) }); });
  return out;
};
const size = (b) => b.getSize(new THREE.Vector3());
const centre = (b) => b.getCenter(new THREE.Vector3());
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const crossFeet = (kit) => meshes(kit, (o) => o.material?.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o)).x - 0.666) < 0.005);

test('the frame is 1248 mm long and 666 mm across at its feet, 976 mm at most to the top of the pad, the pads\' top end at the chosen 825 mm', () => {
  const kit = bench();
  const all = meshes(kit).reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(size(all).z, 1.248 + 0.05, 0.03, 'the length: the base 1248 mm and the handles 50 mm past its head end');
  assert.ok(size(all).y <= 0.976 + 1e-3, `taller than 976 mm: ${size(all).y}`);
  const feet = crossFeet(kit);
  assert.equal(feet.length, 2, 'two cross feet 666 mm across, one at each end');
  const top = contact(kit, 'thighPad').at[0];
  // The pad's middle is half its 330 mm down the 45 degree slope from the top end.
  close(top.y, 0.825 - 0.165 * Math.SQRT1_2, 0.02, 'the thigh pad\'s middle');
});

test('two hip pads about 210 mm wide with a 55 mm gap, 330 mm long, 90 mm thick, sloping down at 45 degrees toward the feet', () => {
  const pads = meshes(bench(), (o) => o.material?.name === 'vinyl' && o.userData.pad === 'hip');
  assert.equal(pads.length, 2);
  const xs = pads.map(({ box }) => centre(box).x).sort((a, b) => a - b);
  close(xs[1] - xs[0], 0.265, 0.005, 'the pads\' centres, 210 mm wide and 55 mm apart');
  const { box } = pads[0];
  // A pad 330 long and 90 thick tilted 45 degrees fills (330 + 90) / sqrt 2 in both y and z.
  close(size(box).y, (0.33 + 0.09) * Math.SQRT1_2, 0.02, 'the pad\'s height tilted at 45 degrees');
  close(size(box).z, (0.33 + 0.09) * Math.SQRT1_2, 0.02, 'the pad\'s reach tilted at 45 degrees');
});

test('the foot plate is 470 mm wide square to the body line, the body line 900 mm from the pad\'s top end along the slope', () => {
  const kit = bench();
  const plate = meshes(kit, (o) => o.userData.pad === 'foot')[0];
  close(size(plate.box).x, 0.47, 0.005, 'the plate\'s width');
  const feet = contact(kit, 'footPlate').at[0];
  const hip = contact(kit, 'thighPad').at[0];
  const along = feet.clone().sub(hip).setX(0);
  // From the pad's middle, the contact, so 165 mm short of the 900 mm from its top end.
  close(along.length(), 0.9 - 0.165, 0.02, 'the body line from the pad to the plate');
  close(Math.atan2(-along.y, -along.z) * 180 / Math.PI, 45, 3, 'the slope of the body line, the feet toward -z');
});

test('two heel rollers 190 mm long and 130 mm across, 195 mm up the body line from the plate and 250 mm off the pad line', () => {
  const kit = bench();
  const rollers = meshes(kit, (o) => o.userData.pad === 'heel');
  assert.equal(rollers.length, 2);
  for (const { box } of rollers) {
    close(size(box).x, 0.19, 0.005, 'a roller\'s length');
    close(size(box).y, 0.13, 0.005, 'a roller\'s diameter');
  }
  const calves = contact(kit, 'heelRollers').at;
  assert.equal(calves.length, 2);
  const plate = contact(kit, 'footPlate').at[0];
  const hip = contact(kit, 'thighPad').at[0];
  // Side-on: the body line and the roller's offset from the plate, both in the y-z plane.
  const line = plate.clone().sub(hip).setX(0).normalize();
  const rel = calves[0].clone().sub(plate).setX(0);
  close(-rel.dot(line), 0.195, 0.02, 'the roller up the body line from the plate');
  const off = rel.clone().addScaledVector(line, -rel.dot(line)).length();
  close(off, 0.25, 0.03, 'the roller off the body line');
});

test('grab handles run horizontal at 690 mm, a pair reaching 50 mm past the base\'s head end', () => {
  const kit = bench();
  const handles = meshes(kit, (o) => o.userData.held);
  assert.equal(handles.length, 2);
  for (const { box } of handles) close(centre(box).y, 0.69, 0.01, 'a handle\'s height');
  const head = Math.max(...crossFeet(kit).map(({ box }) => box.max.z));
  close(Math.max(...handles.map(({ box }) => box.max.z)) - head, 0.05, 0.02, 'the handles past the head end');
});

test('nothing on it moves, and its three contacts are the thigh pad, the foot plate and the heel rollers', () => {
  const kit = bench();
  assert.deepEqual(Object.keys(kit.bodies ?? {}), []);
  assert.deepEqual(kit.contacts.map((c) => [c.point, c.landmark]), [['thighPad', 'thighs'], ['footPlate', 'feet'], ['heelRollers', 'shins']]);
});

test('its recipe names no brand and wears no logo or decal', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'hyperextension-bench.mjs'), 'utf8');
  assert.doesNotMatch(source, /bench ?fitness|revolve|rogue|titan|hoist|hammer|\blogo\b|decal/i);
});

const role = (name) => (o) => o.userData.role === name;
// The slope: `S` down it toward the feet, `N` off it, away from the pads.
const S = V(0, -Math.SQRT1_2, -Math.SQRT1_2);
const N = V(0, Math.SQRT1_2, -Math.SQRT1_2);
/** The first hit on `mesh` from `from` along `way`. */
const along = (from, way, mesh) => new THREE.Raycaster(from, way.clone().normalize()).intersectObject(mesh, false)[0];
/** The lowest vertex of `mesh`, in the world. */
const lowest = (mesh) => {
  const p = mesh.geometry.attributes.position;
  const v = new THREE.Vector3();
  let y = Infinity;
  for (let i = 0; i < p.count; i++) y = Math.min(y, v.fromBufferAttribute(p, i).applyMatrix4(mesh.matrixWorld).y);
  return y;
};
const one = (kit, name) => {
  const found = meshes(kit, role(name));
  assert.equal(found.length, 1, `one ${name}`);
  return found[0];
};

// The frame, from the spec sheet's side drawing (refs/hyperextension_bench/ref.md, part list).
test('the rail is 75 mm tube with a row of holes, 35 mm off the floor on its feet, the whole base long', () => {
  const { box, mesh } = one(bench(), 'rail');
  close(box.min.y, 0.035, 0.002, 'its underside');
  close(box.max.y, 0.11, 0.002, 'its top');
  close(size(box).z, 1.248, 0.01, 'its length');
  assert.ok(mesh.geometry.attributes.position.count > 200, 'holes through it');
  const caps = meshes(bench(), role('railCap')).map((c) => c.box);
  assert.equal(caps.length, 2, 'a cap on each open end');
  for (const end of [box.min.z, box.max.z]) assert.ok(caps.some((c) => Math.abs(centre(c).z - end) < 0.01), `no cap at ${end.toFixed(2)}`);
});

test('a sleeved column rises at 45 degrees from a pivot 790 mm from the head end, and the carriage sits flush on it', () => {
  const kit = bench();
  const sleeve = one(kit, 'columnSleeve');
  const inner = one(kit, 'column');
  const head = Math.max(...crossFeet(kit).map(({ box }) => box.max.z));
  const pivot = one(kit, 'columnPivot');
  close(head - centre(pivot.box).z, 0.79, 0.01, 'the pivot from the head end');
  close(centre(pivot.box).y, 0.181, 0.01, 'the pivot\'s height');
  assert.ok(pivot.box.min.y <= 0.11 + 0.002 || one(kit, 'columnClevis').box.min.y <= 0.11, 'a clevis down to the rail');
  close(size(sleeve.box).y, size(sleeve.box).z, 0.01, 'the sleeve at 45 degrees');
  assert.ok(sleeve.box.max.y > inner.box.min.y, 'the column runs out of the sleeve');
  const carriage = one(kit, 'carriage');
  assert.ok(size(carriage.box).x > 0.44, 'under both pads');
  const hit = along(carriage.mesh.getWorldPosition(new THREE.Vector3()), N.clone().negate(), inner.mesh);
  assert.ok(hit && hit.distance < 0.01, `the carriage ${hit ? (hit.distance * 100).toFixed(1) : 'nowhere'} cm off the column`);
  // From above the carriage's end, so a sleeve run into it can't hide the ray's start.
  const over = carriage.mesh.getWorldPosition(new THREE.Vector3()).addScaledVector(S, 0.12).addScaledVector(N, 0.02);
  const under = along(over, N.clone().negate(), sleeve.mesh);
  assert.ok(!under || under.distance > 0.024, 'the sleeve stops short of the carriage');
});

test('a strut stands from a pivot 218 mm from the head end, mitred over at its top in one length to the column', () => {
  const kit = bench();
  const strut = one(kit, 'strut');
  const pivot = one(kit, 'strutPivot');
  const sleeve = one(kit, 'columnSleeve');
  close(centre(pivot.box).y, 0.181, 0.01, 'the pivot\'s height');
  close(Math.max(...crossFeet(kit).map(({ box }) => box.max.z)) - centre(pivot.box).z, 0.218, 0.01, 'the pivot from the head end');
  assert.notEqual(strut.mesh.geometry.type, 'ExtrudeGeometry', 'one mitred length');
  assert.ok(strut.box.intersectsBox(sleeve.box), 'it reaches the column');
  close(strut.box.max.y, 0.476, 0.04, 'its end on the column, by the drawing');
});

test('the foot plate stands on a post and a stub from the rail, and one mitred arm runs on from the post to the rollers\' cross tube', () => {
  const kit = bench();
  const plate = meshes(kit, (o) => o.userData.pad === 'foot')[0];
  const stub = one(kit, 'plateStub');
  close(stub.box.min.y, 0.11, 0.002, 'the stub cut flat on the rail');
  const arm = one(kit, 'rollerArm');
  close(arm.box.min.y, 0.11, 0.002, 'the arm cut flat on the rail');
  assert.notEqual(arm.mesh.geometry.type, 'ExtrudeGeometry', 'one mitred length');
  // Through the plate from the foot's side: each support starts on the plate's back, not in it.
  const sole = contact(kit, 'footPlate').at[0];
  const thick = plate.mesh.geometry.boundingBox?.max.y * 2 || 0.02;
  const stubUp = stub.box.getCenter(new THREE.Vector3());
  const surface = (z) => sole.clone().addScaledVector(N, (z - sole.z) / N.z);
  for (const [support, z] of [[stub, stubUp.z], [arm, -0.75]]) {
    const hit = along(surface(z).addScaledVector(S, -0.001), S, support.mesh);
    assert.ok(hit, 'a support behind the plate');
    close(hit.distance, thick, 0.006, 'from the plate\'s surface to the support');
  }
  assert.ok(lowest(plate.mesh) >= 0.109, `the plate's toe end in the rail, at ${lowest(plate.mesh).toFixed(3)}`);
  const cross = one(kit, 'rollerCross');
  const axle = contact(kit, 'heelRollers').at[0];
  close(cross.box.distanceToPoint(axle.clone().setX(0)), 0, 0.01, 'the axle through the cross tube');
  const up = along(axle.clone().setX(0).addScaledVector(N, -0.1), N, arm.mesh);
  assert.ok(up && up.distance < 0.15, 'the cross tube reaches the arm');
});

// The owner's bar (#241): no open tube end, mitred joints, every support reaching the floor or its frame.
test('no tube on it stands open', () => {
  const rims = [];
  bench().group.traverse((o) => {
    if (!o.isMesh) return;
    // Cylinders and spheres close by construction, a roller's bore holds its axle, and the rail's ends have their caps.
    if (['CylinderGeometry', 'SphereGeometry'].includes(o.geometry.type) || o.userData.pad === 'heel' || o.userData.role === 'rail') return;
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

// #241: a body fits it. The checks hold the feet on the plate, the thighs on the pads, the calves
// under the rollers and nothing through the body, on every frame of the rep.
const ID = '45_hyperextension';
const fails = async () => Object.entries((await checkPose(ID)).checks).filter(([, c]) => !c.ok).map(([k, c]) => `${k}: ${c.found ?? ''}`);
const staged = () => buildStage(prepare(ID));
const skinDown = (rig, from, to) => {
  const ray = new THREE.Raycaster(from, to.clone().sub(from).normalize());
  return rig.meshes.flatMap((m) => { m.computeBoundingSphere?.(); m.computeBoundingBox?.(); return ray.intersectObject(m, false); }).map((h) => h.point).sort((a, b) => a.distanceTo(from) - b.distanceTo(from));
};

test('45_hyperextension: the body fits the bench through the whole rep', async () => {
  assert.deepEqual(await fails(), []);
});

test('45_hyperextension: from a straight line down the slope, the trunk hinges at the hips to below level, the hips and legs staying put', async () => {
  const { pose, rig } = await staged();
  const line2 = () => wpos(rig.bones.upperarm_l).add(wpos(rig.bones.upperarm_r)).multiplyScalar(0.5).sub(wpos(rig.bones.pelvis));
  pose(0);
  const hips = wpos(rig.bones.pelvis);
  const legLine = wpos(rig.bones.pelvis).sub(wpos(rig.bones.foot_l)).setX(0).normalize();
  const line = line2().setX(0).normalize();
  assert.ok(line.angleTo(legLine) * 180 / Math.PI < 12, `the trunk ${(line.angleTo(legLine) * 180 / Math.PI).toFixed(0)}° off the legs' line at the top`);
  pose(1);
  assert.ok(line2().y < -0.02, `the trunk below level at the bottom, ${line2().y.toFixed(2)} m`);
  assert.ok(wpos(rig.bones.pelvis).distanceTo(hips) < 0.08, 'the hips stay on the pads');
});

test('45_hyperextension: the thighs lie on the pads, the whole foot on the plate, the rollers on the calves above the heel', async () => {
  const { pose, rig, kit } = await staged();
  const k = kit.kits.hyperextension_bench;
  const pads = meshes(k, (o) => o.userData.pad === 'hip').map((p) => p.mesh);
  const plate = meshes(k, (o) => o.userData.pad === 'foot')[0].mesh;
  plate.geometry.computeBoundingBox();
  for (const d of [0, 0.5, 1]) {
    pose(d);
    kit.group.updateMatrixWorld(true);
    for (const side of ['l', 'r']) {
      const padMesh = pads.find((m) => Math.sign(m.getWorldPosition(new THREE.Vector3()).x) === (side === 'l' ? 1 : -1));
      const at = padMesh.getWorldPosition(new THREE.Vector3());
      const out = padMesh.localToWorld(V(0, 0.2, 0)).sub(at).normalize();
      const face = at.clone().addScaledVector(out, 0.045);
      const [skin] = skinDown(rig, face.clone().addScaledVector(out, -0.01), face.clone().addScaledVector(out, 1));
      assert.ok(skin, `a thigh over the ${side} pad, ${d} of the way`);
      close(skin.distanceTo(face), 0, 0.03, `the ${side} thigh on its pad, ${d} of the way`);
      // Square off the plate, under the ball of the foot: the sole's skin on its surface.
      const ball = wpos(rig.bones[`ball_${side}`]);
      const sole = contact(k, 'footPlate').at[0];
      const onPlate = ball.clone().addScaledVector(S, ball.clone().sub(sole).dot(S.clone().negate()));
      const [skin2] = skinDown(rig, onPlate.clone().addScaledVector(S, 0.002), onPlate.clone().addScaledVector(S, -1));
      assert.ok(skin2, `a sole over the plate, ${d} of the way`);
      close(skin2.distanceTo(onPlate), 0, 0.008, `the ${side} sole on the plate, ${d} of the way`);
      const toLocal = plate.matrixWorld.clone().invert();
      const { min, max } = plate.geometry.boundingBox;
      for (const bone of [`foot_${side}`, `ball_${side}`, `ball_leaf_${side}`]) {
        const p = wpos(rig.bones[bone]).applyMatrix4(toLocal);
        assert.ok(p.x > min.x && p.x < max.x && p.z > min.z && p.z < max.z, `${bone} off the plate, ${d} of the way`);
      }
      const roller = contact(k, 'heelRollers').at.find((c) => Math.sign(c.x) === (side === 'l' ? 1 : -1));
      const [calf] = skinDown(rig, roller.clone().setX(Math.sign(roller.x) * 0.09), roller.clone().setX(Math.sign(roller.x) * 0.09).addScaledVector(N, -1));
      assert.ok(calf, `a calf under the ${side} roller, ${d} of the way`);
      close(calf.distanceTo(roller.clone().setX(calf.x)), 0.065, 0.02, `the ${side} roller on the calf, ${d} of the way`);
      const ankle = wpos(rig.bones[`foot_${side}`]);
      const knee = wpos(rig.bones[`calf_${side}`]);
      const t = roller.clone().sub(ankle).dot(knee.clone().sub(ankle).normalize()) / knee.distanceTo(ankle);
      assert.ok(t > 0.1 && t < 0.5, `the ${side} roller at ${t.toFixed(2)} of the calf, above the heel`);
    }
  }
});

test('45_hyperextension: the baked clip shows every part of the kit where the pose has it', async () => {
  const { pose, kit } = await staged();
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
