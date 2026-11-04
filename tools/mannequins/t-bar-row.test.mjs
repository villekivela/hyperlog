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
import { fitted } from './fit.mjs';
import { checkPose } from './checks.mjs';
import { prepare } from './entries.mjs';
import { applyTuned, tunables } from './tune.mjs';

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

const mannequin = async () => {
  const bytes = await readFile(MODEL);
  return buildRig(await parseMannequin(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)));
};

// The rule, solved (#188): 91 cm down the beam for this mannequin, near the 95 cm the ref measures
// on photo 07's lifter, and further for longer legs, which the pop-pin stops at its lowest hole.
test('the fit rule solves the foot plate from the legs, as far down the beam as its pop-pin goes', async () => {
  const { dims } = await mannequin();
  const entry = { equipment: 'machine', kit: { t_bar_row_machine: {} } };
  const down = (L) => fitted(entry, L).kit.t_bar_row_machine.footPlate;
  close(down(dims), 0.913, 0.005, 'the foot plate, in metres down the beam');
  assert.ok(down({ ...dims, shin: dims.shin - 0.05 }) < down(dims) - 0.03, 'a shorter shin takes the plate up the beam');
  close(down({ ...dims, shin: dims.shin + 0.3 }), 0.95, 1e-9, 'the lowest hole');
  assert.equal(fitted({ ...entry, kit: { t_bar_row_machine: { footPlate: 0.8 } } }, dims).kit.t_bar_row_machine.footPlate, 0.8, 'an entry\'s own setting');
});

// Posed, it stands in the studio as its poses set it up (#188), its lever scrubbed through its limits.
test('the studio shows it as a pose sets it up, the plate rising with its lever', async () => {
  assert.equal(shownAlone('t_bar_row_machine'), null, 'a posed kit has nothing to stand alone by');
  const stage = await buildStage(prepare('chest_supported_t_bar_row'));
  stage.pose(0);
  const kit = stage.kit.kits.t_bar_row_machine;
  close(deg(kit.joints.lever.value), -3, 1e-9, 'on the catch at the top of the clip');
  const plate = () => { let at; kit.group.traverse((o) => { if (o.material?.name === 'plate') at = o.getWorldPosition(new THREE.Vector3()); }); return at; };
  const low = plate();
  kit.scrub('lever', kit.joints.lever.limits[1]);
  stage.scene.updateMatrixWorld(true);
  // The plate's middle, 1.3 m out, rises through the lever's 23°.
  close(plate().y - low.y, 1.3 * (Math.sin(rad(20)) + Math.sin(rad(3))), 0.005, 'how far the plate rose');
});

// The weight and the posts are on the machine's centre line.
const boxes = (kit, wanted) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push(new THREE.Box3().setFromObject(o)); });
  return out;
};
const unite = (bs) => bs.reduce((u, b) => u.union(b), new THREE.Box3());
// The posts stand from the floor to the beam: upright frame tubes taller than 30 cm.
const isPost = (o) => {
  const b = new THREE.Box3().setFromObject(o);
  return o.material?.name === 'frame' && b.min.y < 0.1 && b.max.y - b.min.y > 0.25 && b.max.x - b.min.x < 0.2;
};

test('the lever, its horn and the plate lie on the centre line', () => {
  const kit = machine();
  const plate = boxes(kit, (o) => o.material?.name === 'plate');
  assert.equal(plate.length, 1);
  close(plate[0].getCenter(new THREE.Vector3()).x, 0, 1e-6, 'the plate\'s x');
  const mine = kit.bodies.lever.map((g) => new THREE.Box3().setFromObject(g));
  close(unite(mine).getCenter(new THREE.Vector3()).x, 0, 0.005, 'the lever\'s middle');
});

test('one post at the rear on the centre line and one under the pad to the side, each from the floor to the beam', () => {
  const kit = machine();
  const posts = boxes(kit, isPost).map((b) => b.getCenter(new THREE.Vector3())).sort((a, b) => a.z - b.z);
  assert.equal(posts.length, 2, 'a rear post and a tall one');
  const [rear, tall] = posts;
  close(rear.x, 0, 1e-6, 'the rear post\'s x');
  assert.ok(Math.abs(tall.x) > 0.05, 'the tall post on the centre line is in the lever\'s way');
  const reach = boxes(kit, isPost).map((b) => b.max.y).sort((a, b) => a - b);
  assert.ok(reach[0] > 0.5, `the rear post stops at ${reach[0].toFixed(2)} m, short of the inclined beam above the pivot`);
  assert.ok(reach[1] > reach[0] + 0.3, 'the front post reaches the beam higher up it');
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

// The grips point away from the user, none up. A held part
// is laid from its attached end (A) to its free end (B), so its y axis points to the free end.
test('each upright has two grips as the maker\'s photo shows: one back toward the feet, one straight out, none up', () => {
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
      for (const axis of axes) assert.ok(axis.y < Math.sin(rad(3)) + 1e-6, `a grip points up at ${deg(limit).toFixed(0)}°`);
      assert.ok(axes.some((a) => a.z < -0.9 || a.z < -0.6 && a.y < -0.3), `no grip back toward the feet at ${deg(limit).toFixed(0)}°`);
      assert.ok(axes.some((a) => a.x * side > 0.95), `no grip straight out at ${deg(limit).toFixed(0)}°`);
    }
  }
});

// Posed on it (#188): each exercise holds one pair of grips, machine-first.
const failed = (checks) => Object.entries(checks).filter(([, c]) => !c.ok).map(([name, c]) => `${name} on frame ${c.frame}: ${c.found}`);

test('chest_supported_t_bar_row: passes every check, on the neutral grips', async () => {
  assert.equal(prepare('chest_supported_t_bar_row').kit.t_bar_row_machine.grip, 'neutral');
  const { checks } = await checkPose('chest_supported_t_bar_row');
  assert.deepEqual(failed(checks), []);
});

/** The elbow's angle on side `k`, in degrees, and how far the elbow is behind the shoulder and out from it. */
const arm = (rig, k) => {
  const [shoulder, elbow, wrist] = ['upperarm_', 'lowerarm_', 'hand_'].map((b) => wpos(rig.bones[b + k]));
  return { bend: deg(shoulder.clone().sub(elbow).angleTo(wrist.clone().sub(elbow))), back: shoulder.z - elbow.z, out: Math.abs(elbow.x) - Math.abs(shoulder.x) };
};

// The ref's Fit rules: the arms hang long on the catch, and at the top of the pull (the clip's
// bottom) the elbows have gone back past the trunk, not out to the sides.
test('chest_supported_t_bar_row: from the arms long, the elbows drive back past the trunk', async () => {
  const stage = await buildStage(prepare('chest_supported_t_bar_row'));
  stage.pose(0);
  assert.ok(arm(stage.rig, 'l').bend > 150, `the elbow bent to ${arm(stage.rig, 'l').bend.toFixed(0)}° with the arms long`);
  stage.pose(1);
  const { back, out } = arm(stage.rig, 'l');
  assert.ok(back > 0.1, `the elbow ${(back * 100).toFixed(1)} cm behind the shoulder`);
  assert.ok(out < 0.1, `the elbow ${(out * 100).toFixed(1)} cm out from the shoulder`);
});

// The T-bar row's video has the hands wider than the shoulders: on this machine, the wide grips, overhand.
test('t_bar_row: passes every check, on the wide grips', async () => {
  assert.equal(prepare('t_bar_row').kit.t_bar_row_machine.grip, 'wide');
  const { checks } = await checkPose('t_bar_row');
  assert.deepEqual(failed(checks), []);
});

// The ref's Motion: the Kelso shrug's hands rise about 100 mm against the row's 270, the arms near
// straight, the shoulder blades pulling together; the library's note pauses it about a second.
test('kelso_shrug: passes every check, on the wide grips', async () => {
  assert.equal(prepare('kelso_shrug').kit.t_bar_row_machine.grip, 'wide');
  const { checks } = await checkPose('kelso_shrug');
  assert.deepEqual(failed(checks), []);
});

test('kelso_shrug: the hands rise about 100 mm on long arms, the shoulder blades doing the work', async () => {
  const entry = prepare('kelso_shrug');
  const stage = await buildStage(entry);
  const { rig } = stage;
  const shoulder = () => wpos(rig.bones.upperarm_l);
  stage.pose(0);
  const [start, hand] = [shoulder(), heldAt(rig, 'l')];
  for (const d of [0, 0.5, 1]) {
    stage.pose(d);
    assert.ok(arm(rig, 'l').bend > 145, `the elbow bent to ${arm(rig, 'l').bend.toFixed(0)}° ${d} of the way`);
  }
  close(heldAt(rig, 'l').y - hand.y, 0.1, 0.015, 'how far the hands rose');
  // Back off the pad's line, out of its face: the blades drawn together.
  const face = V(0, Math.cos(rad(40)), -Math.sin(rad(40)));
  const drawn = shoulder().sub(start).dot(face);
  assert.ok(drawn > 0.07, `the shoulder drawn back ${(drawn * 100).toFixed(1)} cm`);
  // Not an upward shrug: nothing up the spine.
  const lifted = shoulder().sub(start).dot(V(0, Math.sin(rad(40)), Math.cos(rad(40))));
  assert.ok(Math.abs(lifted) < 0.02, `the shoulder ${(lifted * 100).toFixed(1)} cm up the spine`);
  assert.equal(entry.tempo.bottom, 1, 'held about a second at the squeeze');
});

// The library's note on the row: squeeze the shoulder blades at the top of the pull.
test('the rows draw the shoulder blades together as the elbows come back', async () => {
  for (const id of ['chest_supported_t_bar_row', 't_bar_row']) {
    const stage = await buildStage(prepare(id));
    const spread = (d) => { stage.pose(d); return wpos(stage.rig.bones.upperarm_l).distanceTo(wpos(stage.rig.bones.upperarm_r)); };
    const [long, squeezed] = [spread(0), spread(1)];
    assert.ok(squeezed < long - 0.03, `${id}: the shoulders ${(long * 100).toFixed(1)} cm apart with the arms long, ${(squeezed * 100).toFixed(1)} at the squeeze`);
  }
});

// The machine decides (ADR 0007): whatever an entry does, the lever turns about its own pivot, and
// the hands ride the arc its grips have.
test('every exercise on it leaves the lever its pivot and its path', async () => {
  const bare = machine();
  const points = (kit, at) => { kit.scrub('lever', at); return Object.fromEntries(kit.contacts.filter((c) => c.landmark === 'hands').map((c) => [c.point, c.at.map((p) => p.clone())])); };
  const free = points(bare, rad(10));
  for (const id of ['chest_supported_t_bar_row', 't_bar_row', 'kelso_shrug']) {
    const stage = await buildStage(prepare(id));
    stage.pose(0.5);
    const kit = stage.kit.kits.t_bar_row_machine;
    assert.ok(kit.joints.lever.origin.distanceTo(bare.joints.lever.origin) < 1e-9, `${id}: the pivot moved`);
    for (const [point, at] of Object.entries(points(kit, rad(10)))) {
      at.forEach((p, i) => assert.ok(p.distanceTo(free[point][i]) < 1e-6, `${id}: the ${point} grip off its path`));
    }
  }
});

// The studio's slider for the fit is the entry's own setting, under the path `fit` names (#160).
test('the foot plate\'s slider moves the plate and the feet with it, and nothing the pad decides', async () => {
  const entry = prepare('chest_supported_t_bar_row');
  const fit = await buildStage(entry);
  const path = 'kit.t_bar_row_machine.footPlate';
  const solved = fit.fit[path];
  assert.equal(typeof solved, 'number', 'the fit offers the foot plate');
  assert.ok(tunables(applyTuned(entry, fit.fit)).some((t) => t.path === path), 'and the studio has a slider for it');
  const moved = await buildStage(applyTuned(entry, { [path]: solved - 0.1 }));
  assert.equal(moved.fit[path], undefined, 'nothing solved for what the slider sets');
  const [a, b] = [fit.pose(0), moved.pose(0)];
  const plate = (s) => contact(s.kit.kits.t_bar_row_machine, 'footPlate').at[0];
  close(plate(fit).distanceTo(plate(moved)), 0.1, 1e-6, 'how far the plate moved');
  close(a.ankles[0].distanceTo(b.ankles[0]), 0.1, 1e-6, 'how far the ankles moved with it');
  close(a.hipC.distanceTo(b.hipC), 0, 1e-9, 'the hips stay on the pad');
  const { checks } = await checkPose('chest_supported_t_bar_row', { poses: { chest_supported_t_bar_row: applyTuned(entry, { [path]: solved - 0.1 }) } });
  assert.ok(checks.feet.ok, checks.feet.found);
});
