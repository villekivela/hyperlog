// The power rack (#189, the issue called it the pull-up and dip station), from refs/power_rack/ref.md: a bolt-together power rack
// with a multi-grip pull-up bar and a pair of dip handles bolted on as options. Nothing on it moves.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, STATIONS, buildKit } from './kit.mjs';
import { prepare } from './entries.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

test('it is the kit of the 16 bodyweight and weighted bodyweight exercises the ref names, each posed on it', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'power_rack', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (.+)\. Not this item/)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.equal(named.length, 16);
  assert.deepEqual(STATIONS.power_rack, { kit: 'power_rack', exercises: named });
  assert.ok(KITS.power_rack, 'a recipe named power_rack');
  for (const id of named) {
    const kind = library.exercises.find((e) => e.id === id)?.equipment;
    assert.ok(['bodyweight', 'weighted_bodyweight'].includes(kind), `${id} is ${kind}`);
    assert.ok(EQUIPMENT[kind].includes('power_rack'), `${kind} has no power_rack kit`);
    assert.ok(prepare(id).kit.power_rack, `${id} isn't posed on it`);
  }
});

const IN = 0.0254;
const station = (options = {}) => {
  const kit = buildKit({ equipment: 'bodyweight', kit: { power_rack: options } }).kits.power_rack;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const meshes = (kit, wanted = () => true) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && wanted(o)) out.push({ mesh: o, box: new THREE.Box3().setFromObject(o) }); });
  return out;
};
const centre = (b) => b.getCenter(new THREE.Vector3());
const size = (b) => b.getSize(new THREE.Vector3());
const held = (kit) => meshes(kit, (o) => o.userData.held);
// The uprights' 5/8 in holes, in inches up: 2 in apart to 11, 1 in apart to 35, 2 in apart above (the published pattern).
const HOLES = [2, 4, 6, 8, 10, ...Array.from({ length: 25 }, (_, i) => 11 + i), ...Array.from({ length: 36 }, (_, i) => 37 + 2 * i)];
const onAHole = (y) => HOLES.some((h) => Math.abs(h - y / IN) < 0.05);
const uprights = (kit) => meshes(kit, (o) => o.material?.name === 'frame' && size(new THREE.Box3().setFromObject(o)).y > 2.5);
// The end plates are the 8 mm plates 22 cm tall.
const plates = (kit) => meshes(kit, (o) => { const z = size(new THREE.Box3().setFromObject(o)); return Math.abs(z.y - 0.22) < 1e-3 && Math.min(z.x, z.z) < 0.01; });

test('four 3 in uprights 108 in tall on 7 in plates: the front pair 43 in apart inside, the rear 24 in behind, 53 in by 34 in on the floor', () => {
  const ups = uprights(station());
  assert.equal(ups.length, 4);
  for (const { box } of ups) {
    close(size(box).x, 3 * IN, 1e-3, 'an upright\'s width');
    close(size(box).z, 3 * IN, 1e-3, 'an upright\'s depth');
    close(box.max.y, 108 * IN, 1e-3, 'an upright\'s height');
  }
  const xs = ups.map(({ box }) => centre(box).x).sort((a, b) => a - b);
  close(xs[3] - xs[0], 46 * IN, 1e-3, 'the uprights\' centres, 43 in apart inside');
  const zs = [...new Set(ups.map(({ box }) => centre(box).z.toFixed(3)))].map(Number).sort((a, b) => a - b);
  close(zs[1] - zs[0], 27 * IN, 1e-3, 'the front and rear uprights\' centres, 24 in apart inside');
  const base = meshes(station(), (o) => o.material?.name === 'frame' && size(new THREE.Box3().setFromObject(o)).y < 0.011);
  const foot = base.reduce((u, { box }) => u.union(box), new THREE.Box3());
  close(size(foot).x, 53 * IN, 0.01, 'the footprint\'s width');
  close(size(foot).z, 34 * IN, 0.01, 'the footprint\'s depth');
});

// A ray along a face's normal passes through a hole of a hollow upright, and meets the wall one inch off it.
test('the uprights are hollow with holes through every face: a ray along x at a hole\'s height passes the rear pair, and meets their walls an inch off it', () => {
  const kit = station({ pullupBar: false, dipHandles: false });
  const rear = uprights(kit).filter(({ box }) => centre(box).z > 0.6);
  const cast = (y) => {
    const ray = new THREE.Raycaster(new THREE.Vector3(-2, y, centre(rear[0].box).z), new THREE.Vector3(1, 0, 0));
    return ray.intersectObjects(rear.map((r) => r.mesh)).length;
  };
  assert.equal(cast(HOLES[HOLES.length - 12] * IN), 0, 'a hole at 85 in: the ray should pass through');
  assert.equal(cast((HOLES[HOLES.length - 12] + 1) * IN) > 0, true, 'no wall one inch off the hole');
});

test('every beam sits on a hole: its end plates and their bolts are at holes, a bolt 4 in above and below each plate\'s middle', () => {
  const kit = station();
  const ps = plates(kit);
  assert.equal(ps.length, 12, 'two plates to each of the six crossmembers');
  for (const { box } of ps) assert.ok(onAHole(centre(box).y), `a plate at ${(centre(box).y / IN).toFixed(2)} in is not on a hole`);
  const heads = meshes(kit, (o) => o.material?.name === 'metal' && Math.abs(Math.min(...size(new THREE.Box3().setFromObject(o)).toArray()) - 0.011) < 1e-3);
  assert.equal(heads.length, 24, 'two bolts to each of the twelve plates');
  for (const { box } of heads) assert.ok(onAHole(centre(box).y), `a bolt at ${(centre(box).y / IN).toFixed(2)} in is not on a hole`);
});

test('a crossmember is hollow, between the uprights\' faces: the back one ties the rear uprights, the multi-grip bar\'s ties the front', () => {
  const kit = station();
  const beams = meshes(kit, (o) => o.material?.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o)).x - (43 * IN - 0.016)) < 0.005 && size(new THREE.Box3().setFromObject(o)).y < 0.1);
  assert.equal(beams.length, 2, 'a front and a back crossmember');
  const zs = beams.map(({ box }) => centre(box).z).sort((a, b) => a - b);
  close(zs[1] - zs[0], 27 * IN, 1e-3, 'the front and back crossmembers, 24 in apart inside');
  for (const { box } of beams) close(centre(box).y, 91 * IN, 1e-3, 'the crossmember\'s height, the 91 in hole');
  assert.equal(held(station({ pullupBar: false, dipHandles: false })).length, 2, 'the supplied pull-up bars, one skinny and one fat, instead of the multi-grip bar');
});

test('the pin pipe safeties run between the uprights\' faces, not through them, and a pin comes out past the front one', () => {
  const kit = station({ pullupBar: false, dipHandles: false });
  const pipes = meshes(kit, (o) => o.material?.name === 'frame' && Math.abs(size(new THREE.Box3().setFromObject(o)).x - 0.0508) < 1e-3 && size(new THREE.Box3().setFromObject(o)).z > 0.3);
  assert.equal(pipes.length, 2);
  for (const { box } of pipes) {
    close(centre(box).y, 24 * IN, 1e-3, 'a pipe\'s height');
    close(size(box).z, 24 * IN, 1e-3, 'a pipe\'s length, between the uprights');
  }
});

test('nothing on it moves, and each hand grip is a contact on the frame: a pair of points', () => {
  const kit = station();
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => c.point), ['wide', 'standard', 'close', 'neutral', 'dip', 'dipClose']);
  for (const c of kit.contacts) {
    assert.equal(c.body, 'frame');
    assert.equal(c.landmark, 'hands');
    assert.equal(c.at.length, 2, `${c.point}: a point a hand`);
    assert.ok(c.at[0].x < c.at[1].x, `${c.point}: left hand first`);
  }
  assert.deepEqual(station({ pullupBar: false, dipHandles: false }).contacts, []);
  assert.deepEqual(station({ dipHandles: false }).contacts.map((c) => c.point), ['wide', 'standard', 'close', 'neutral']);
});

test('the multi-grip bar is bolted on at the 91 in hole: wide 960 mm apart, standard 610, close 280 on the V, neutral 406 on the inner prongs', () => {
  const kit = station();
  for (const [point, spread] of [['wide', 0.96], ['standard', 0.61], ['close', 0.28], ['neutral', 0.406]]) {
    const [l, r] = contact(kit, point).at;
    close(r.x - l.x, spread, 0.002, `the ${point} grips' spread`);
    close(l.y, 91 * IN, 1e-3, `the ${point} grips' height, the 91 in hole`);
    close(l.x + r.x, 0, 1e-6, `${point} about the centre line`);
  }
  for (const point of ['wide', 'standard']) close(contact(kit, point).at[0].z, 0, 1e-6, `${point} hangs on the cross tube, at the origin`);
  assert.ok(contact(kit, 'close').at[0].z > 0.05, 'the close grips are on the V, nearer the rack than the cross tube');
  assert.ok(contact(kit, 'neutral').at[0].z < 0, 'the neutral grips are further out the prongs, toward the lifter');
});

test('the dip handles hang on the +x front upright from a bracket at the 51 in hole: 451 mm apart at the crosshead to 630 at the tips, the close grip nearer it', () => {
  const kit = station();
  const [dl, dr] = contact(kit, 'dip').at;
  const [cl, cr] = contact(kit, 'dipClose').at;
  close(dl.y, 51 * IN + 0.01, 1e-3, 'the handles\' height');
  close((dl.x + dr.x) / 2, 23 * IN, 1e-3, 'the handles about the +x front upright\'s centre line');
  assert.ok(dr.x - dl.x > 0.451 && dr.x - dl.x < 0.63, `the grips' spread ${dr.x - dl.x}, between the two`);
  assert.ok(cr.x - cl.x < dr.x - dl.x && cr.x - cl.x >= 0.451, 'the close grip, nearer the crosshead, is narrower');
  assert.ok(cl.z > dl.z, 'the close grip is nearer the rack (greater z)');
  const handles = held(kit).filter(({ box }) => box.distanceToPoint(dl) < 1e-6);
  assert.equal(handles.length, 1, 'a held handle at the dip grip');
  close(size(handles[0].box).z, 0.44, 0.03, 'the handle\'s length');
  const bracket = meshes(kit, (o) => Math.abs(size(new THREE.Box3().setFromObject(o)).y - 0.254) < 1e-3);
  assert.ok(bracket.length >= 1);
  for (const { box } of bracket) close(centre(box).y, 51 * IN, 0.011, 'the bracket on the 51 in hole');
});

test('every grip is on a held part: the bar\'s 31.75 mm, the dip handles\' 47.6', () => {
  const kit = station();
  const parts = held(kit);
  for (const [point, mm] of [['wide', 31.75], ['standard', 31.75], ['close', 31.75], ['neutral', 31.75], ['dip', 47.6], ['dipClose', 47.6]]) {
    for (const p of contact(kit, point).at) {
      const on = parts.filter(({ box }) => box.distanceToPoint(p) < 1e-6);
      assert.ok(on.length, `no held part at the ${point} grip`);
      // A cylinder gives its radius; a bent tube lies level, so its height is its diameter.
      for (const { mesh, box } of on) {
        const r = mesh.geometry.parameters?.radiusTop;
        close(r === undefined ? size(box).y * 1000 : 2000 * r, mm, 0.6, `the ${point} grip's diameter, in mm`);
      }
    }
  }
});

test('the outer prongs are 2 in fat grips and the inner ones 1.25 in', () => {
  const kit = station();
  const widths = (x) => held(kit).filter(({ box }) => Math.abs(centre(box).x - x) < 0.01 && size(box).z > 0.3).map(({ box }) => size(box).x * 1000);
  for (const w of widths(0.4065)) close(w, 50.8, 0.6, 'the outer prong\'s diameter, in mm');
  for (const w of widths(0.203)) close(w, 31.75, 0.6, 'the inner prong\'s diameter, in mm');
  assert.ok(widths(0.4065).length && widths(0.203).length, 'no prongs found');
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'power-rack.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|crown|matador|monster|titan|hoist|hammer|\blogo\b|decal/i);
});
