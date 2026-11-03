// The chest-supported T-bar row machine (#176), from refs/t_bar_row_machine/ref.md: a lever
// pivoting low at the rear, by the foot plate, under an inclined chest pad, its handles on a yoke
// and its plate on a horn at the front.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import { KITS, buildKit, shownAlone } from './kit.mjs';
import { MODEL, buildStage, parseMannequin } from './stage.mjs';
import { V, buildRig, heldAt, rad, wpos } from './rig.mjs';

const machine = () => {
  const kit = buildKit({ equipment: 'machine', kit: { t_bar_row_machine: {} } }).kits.t_bar_row_machine;
  // With no pose to drive it, the lever rests on its catch.
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const deg = (r) => (r * 180) / Math.PI;
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);

test('the lever turns about a pivot 420 mm up at the rear, from its catch 3° below level to 20° above', () => {
  const kit = machine();
  const { lever } = kit.joints;
  assert.equal(lever.type, 'revolute');
  close(deg(lever.limits[0]), -3, 1e-9, 'its lower limit, in degrees');
  close(deg(lever.limits[1]), 20, 1e-9, 'its upper limit, in degrees');
  close(lever.value, lever.limits[0], 1e-9, 'unposed, on its catch');
  close(lever.origin.y, 0.42, 0.005, 'the pivot\'s height');
  // At the rear: behind the chest pad and the handles.
  const pad = contact(kit, 'pad').at[0];
  assert.ok(lever.origin.z < pad.z - 0.3, `the pivot at z ${lever.origin.z.toFixed(2)}, not well behind the pad at ${pad.z.toFixed(2)}`);
});

// The ref measures the grips on the yoke's top, which the lifter in its photos holds: the wide ones.
test('the top grips ride an 860 mm arc: about 790 mm up on the catch, 1060 mm at the top, 210 mm further back', () => {
  const kit = machine();
  const { origin } = kit.joints.lever;
  const bottom = contact(kit, 'wide').at.map((p) => p.clone());
  for (const p of bottom) {
    close(p.y, 0.79, 0.02, 'a top grip\'s height on the catch');
    close(p.z - origin.z, 0.78, 0.02, 'how far ahead of the pivot');
  }
  kit.scrub('lever', rad(20));
  contact(kit, 'wide').at.forEach((p, i) => {
    close(p.y, 1.06, 0.03, 'a top grip\'s height at the top');
    close(bottom[i].z - p.z, 0.21, 0.03, 'how far it came back');
  });
  kit.scrub('lever', rad(40));
  close(deg(kit.joints.lever.value), 20, 1e-9, 'held at its upper limit');
});

test('the hands hold either grip: neutral 445 mm apart, wide 660 mm apart, each on a 31 mm grip', () => {
  const kit = machine();
  const held = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) held.push(o); });
  for (const [point, spread] of [['neutral', 0.445], ['wide', 0.66]]) {
    const c = contact(kit, point);
    assert.equal(c.landmark, 'hands');
    assert.equal(c.body, 'lever');
    const [l, r] = c.at;
    close(l.distanceTo(r), spread, 0.005, `the ${point} grips' spread`);
    for (const p of c.at) {
      const on = held.filter((m) => {
        const box = new THREE.Box3().setFromObject(m);
        return box.distanceToPoint(p) < 1e-6;
      });
      assert.ok(on.length, `no held part at the ${point} grip`);
      for (const m of on) {
        m.geometry.computeBoundingBox();
        const size = m.geometry.boundingBox.getSize(new THREE.Vector3());
        close(Math.min(size.x, size.z) * 1000, 31, 0.5, `the ${point} grip's diameter, in mm`);
      }
    }
  }
});

test('the chest lies on the pad, its top end 1.27 m up, and the feet on the foot plate below and behind it', () => {
  const kit = machine();
  const pad = contact(kit, 'pad');
  assert.equal(pad.body, 'frame');
  assert.equal(pad.landmark, 'chest');
  const feet = contact(kit, 'footPlate');
  assert.equal(feet.body, 'frame');
  assert.equal(feet.landmark, 'feet');
  // The foot plate below and behind the pad, on the inclined beam.
  const [plate] = feet.at;
  const [chest] = pad.at;
  assert.ok(plate.y < chest.y - 0.4 && plate.z < chest.z - 0.4, `the foot plate at ${plate.toArray().map((v) => v.toFixed(2))}, not below and behind the pad`);
  let top = -Infinity;
  kit.group.traverse((o) => { if (o.isMesh) top = Math.max(top, new THREE.Box3().setFromObject(o).max.y); });
  close(top, 1.27, 0.02, 'the machine\'s height, at the pad\'s top end');
});

test('its fit rule sets the foot plate along the beam, for the pad\'s top end at the top of the sternum', () => {
  assert.deepEqual(KITS.t_bar_row_machine.fit.map((f) => f.sets), ['footPlate']);
  // The pop-pin moves the foot plate along the beam: further down it, for a taller body.
  const plateAt = (footPlate) => {
    const kit = buildKit({ equipment: 'machine', kit: { t_bar_row_machine: { footPlate } } }).kits.t_bar_row_machine;
    kit.place({}, {}, {});
    return contact(kit, 'footPlate').at[0];
  };
  const [near, far] = [plateAt(0.7), plateAt(0.9)];
  close(near.distanceTo(far), 0.2, 1e-6, 'moved 20 cm along the beam');
  close(deg(Math.atan2(near.y - far.y, near.z - far.z)), 40, 1e-6, 'along the beam, in degrees');
});

// The lever's limits are where the body stops (#159): set up as the fit rule says, the pad's top
// end at the top of the sternum, each grip is within the arms' reach on the catch and at the top.
// The shoulder joints are taken 6 cm off the pad's face at its top end, the chest's half depth
// beneath them, either side by the mannequin's shoulder width.
test("the lever's limits keep every grip within the arms' reach of a body set up by the fit rule", async () => {
  const bytes = await readFile(MODEL);
  const rig = buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
  rig.root.updateMatrixWorld(true);
  const reach = (k) => wpos(rig.bones[`upperarm_${k}`]).distanceTo(wpos(rig.bones[`lowerarm_${k}`]))
    + wpos(rig.bones[`lowerarm_${k}`]).distanceTo(wpos(rig.bones[`hand_${k}`])) + wpos(rig.bones[`hand_${k}`]).distanceTo(heldAt(rig, k));
  const arm = Math.min(reach('l'), reach('r'));
  const kit = machine();
  const top = contact(kit, 'pad').at[0].clone().add(V(0, Math.sin(rad(40)), Math.cos(rad(40))).multiplyScalar(0.255));
  const face = V(0, Math.cos(rad(40)), -Math.sin(rad(40)));
  const shoulders = [-1, 1].map((s) => top.clone().addScaledVector(face, 0.06).setX(s * rig.dims.shW));
  for (const limit of kit.joints.lever.limits) {
    kit.scrub('lever', limit);
    for (const { point, at } of kit.contacts.filter((c) => c.landmark === 'hands')) {
      at.forEach((p, i) => {
        const short = p.distanceTo(shoulders[i]) - arm;
        assert.ok(short < 0.01, `the ${point} grip at ${deg(limit).toFixed(0)}°: ${(short * 100).toFixed(1)} cm past the arms' reach`);
        assert.ok(p.y < shoulders[i].y, `the ${point} grip at ${deg(limit).toFixed(0)}°: above the shoulder`);
      });
    }
  }
});

// No pose uses it yet, so the studio shows it alone (#176): on its catch whatever the rep, its lever
// scrubbed through its limits.
test('the studio shows it alone, its lever on the catch through the rep and scrubbed to either limit', async () => {
  const entry = shownAlone('t_bar_row_machine');
  assert.ok(entry.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  const kit = stage.kit.kits.t_bar_row_machine;
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    close(deg(kit.joints.lever.value), -3, 1e-9, `on the catch ${d} of the way down`);
  }
  const plate = () => { let at; kit.group.traverse((o) => { if (o.material?.name === 'plate') at = o.getWorldPosition(new THREE.Vector3()); }); return at; };
  const low = plate();
  kit.scrub('lever', kit.joints.lever.limits[1]);
  stage.scene.updateMatrixWorld(true);
  // The plate's middle, 1.3 m out, rises through the lever's 23°.
  close(plate().y - low.y, 1.3 * (Math.sin(rad(20)) + Math.sin(rad(3))), 0.005, 'how far the plate rose');
  assert.equal(shownAlone('row_machine'), null, 'a kit set up by its pose has nothing to stand alone by');
});

// The owner's review of #176: the weight and the posts belong on the machine's centre line.
const boxes = (kit, wanted) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push(new THREE.Box3().setFromObject(o)); });
  return out;
};
const unite = (bs) => bs.reduce((u, b) => u.union(b), new THREE.Box3());
// The posts stand from the floor to the beam: upright frame tubes taller than 30 cm.
const isPost = (o) => {
  const b = new THREE.Box3().setFromObject(o);
  return o.material?.name === 'frame' && b.min.y < 0.1 && b.max.y - b.min.y > 0.3 && b.max.x - b.min.x < 0.2;
};

test('the lever, its horn and the plate lie on the centre line', () => {
  const kit = machine();
  const plate = boxes(kit, (o) => o.material?.name === 'plate');
  assert.equal(plate.length, 1);
  close(plate[0].getCenter(new THREE.Vector3()).x, 0, 1e-6, 'the plate\'s x');
  const mine = kit.bodies.lever.map((g) => new THREE.Box3().setFromObject(g));
  close(unite(mine).getCenter(new THREE.Vector3()).x, 0, 0.005, 'the lever\'s middle');
});

test('the posts stand under the beam, in pairs either side of the centre line', () => {
  const kit = machine();
  const posts = boxes(kit, isPost);
  assert.ok(posts.length >= 4, `${posts.length} posts: a pair at the pivot and a pair under the pad`);
  close(unite(posts).getCenter(new THREE.Vector3()).x, 0, 1e-6, 'the posts\' middle');
  for (const b of posts) assert.ok(Math.abs(b.getCenter(new THREE.Vector3()).x) > 0.04, 'a post on the centre line is in the lever\'s way');
});

test('the lever runs clear of the posts, from its catch to its top', () => {
  const kit = machine();
  const posts = boxes(kit, isPost);
  for (const limit of kit.joints.lever.limits) {
    kit.scrub('lever', limit);
    kit.group.updateMatrixWorld(true);
    for (const g of kit.bodies.lever) for (const post of posts) {
      g.traverse((o) => {
        if (!o.isMesh || o.material?.name === 'metal') return;
        assert.ok(!new THREE.Box3().setFromObject(o).intersectsBox(post.clone().expandByScalar(-0.002)), `a lever part (${o.material?.name} at ${new THREE.Box3().setFromObject(o).min.toArray().map((v) => v.toFixed(3))}) meets a post at ${deg(limit).toFixed(0)}°`);
      });
    }
  }
});

// The owner's third flaw: the handles hang down and point away from the user, none up. A held part
// is laid from its attached end (A) to its free end (B), so its y axis points to the free end.
test('each upright has two grips as the maker\'s photo shows: one forward and down, one straight out, none up', () => {
  const kit = machine();
  for (const limit of kit.joints.lever.limits) {
    kit.scrub('lever', limit);
    kit.group.updateMatrixWorld(true);
    const grips = [];
    kit.group.traverse((o) => { if (o.isMesh && o.userData.held) grips.push(o); });
    assert.equal(grips.length, 4, 'a neutral and a wide grip on each of two uprights');
    for (const side of [-1, 1]) {
      const mine = grips.filter((g) => Math.sign(g.getWorldPosition(new THREE.Vector3()).x) === side);
      assert.equal(mine.length, 2, `two grips on the ${side < 0 ? 'left' : 'right'} upright`);
      const axes = mine.map((g) => V(0, 1, 0).applyQuaternion(g.getWorldQuaternion(new THREE.Quaternion())));
      for (const axis of axes) assert.ok(axis.y < 1e-6, `a grip points up at ${deg(limit).toFixed(0)}°`);
      assert.ok(axes.some((a) => a.z > 0.5 && a.y < -0.2), `no grip forward and down at ${deg(limit).toFixed(0)}°`);
      assert.ok(axes.some((a) => a.x * side > 0.95), `no grip straight out at ${deg(limit).toFixed(0)}°`);
    }
  }
});
