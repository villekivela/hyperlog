// The pull-up and dip station (#189), from refs/pullup_dip_station/ref.md: a multi-grip bar and a
// pair of dip handles, fixed on one rig. Nothing on it moves.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as THREE from 'three';
import { EQUIPMENT, KITS, STATIONS, buildKit, shownAlone } from './kit.mjs';
import { buildStage } from './stage.mjs';
import { POSES } from './poses.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const library = JSON.parse(await readFile(path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'exercises.json'), 'utf8'));

test('it is the kit of the 16 bodyweight and weighted bodyweight exercises the ref names, none of them posed', async () => {
  const ref = await readFile(path.join(HERE, 'refs', 'pullup_dip_station', 'ref.md'), 'utf8');
  const named = [...ref.match(/Serves (.+)\. Not this item/)[1].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  assert.equal(named.length, 16);
  assert.deepEqual(STATIONS.pullup_dip_station, { kit: 'pullup_dip_station', exercises: named });
  assert.ok(KITS.pullup_dip_station, 'a recipe named pullup_dip_station');
  for (const id of named) {
    const kind = library.exercises.find((e) => e.id === id)?.equipment;
    assert.ok(['bodyweight', 'weighted_bodyweight'].includes(kind), `${id} is ${kind}`);
    assert.ok(EQUIPMENT[kind].includes('pullup_dip_station'), `${kind} has no pullup_dip_station kit`);
    assert.equal(POSES[id], undefined, `${id} is posed already`);
  }
});

const station = () => {
  const kit = buildKit({ equipment: 'bodyweight', kit: { pullup_dip_station: {} } }).kits.pullup_dip_station;
  kit.place({}, {}, {});
  kit.group.updateMatrixWorld(true);
  return kit;
};
const contact = (kit, point) => kit.contacts.find((c) => c.point === point);
const close = (a, b, within, why) => assert.ok(Math.abs(a - b) <= within, `${why}: ${a.toFixed(3)}, not ${b} ± ${within}`);
const held = (kit) => {
  const out = [];
  kit.group.traverse((o) => { if (o.isMesh && o.userData.held) out.push(o); });
  return out;
};

test('nothing on it moves, and each hand grip is a contact on the frame: a pair of points', () => {
  const kit = station();
  assert.deepEqual(Object.keys(kit.joints), []);
  assert.deepEqual(kit.contacts.map((c) => c.point), ['wide', 'standard', 'close', 'neutral', 'dip', 'dipClose']);
  for (const c of kit.contacts) {
    assert.equal(c.body, 'frame');
    assert.equal(c.landmark, 'hands');
    assert.equal(c.at.length, 2, `${c.point}: a point a hand`);
    close(c.at[0].x + c.at[1].x, 0, 1e-6, `${c.point} about the centre line`);
    assert.ok(c.at[0].x < c.at[1].x, `${c.point}: left hand first`);
  }
});

test('the bar grips are 2.30 m up, wide 960 mm apart, standard 610, close 280 on the V, neutral 406 on the inner prongs', () => {
  const kit = station();
  for (const [point, spread] of [['wide', 0.96], ['standard', 0.61], ['close', 0.28], ['neutral', 0.406]]) {
    const [l, r] = contact(kit, point).at;
    close(r.x - l.x, spread, 0.002, `the ${point} grips' spread`);
    close(l.y, 2.3, 0.002, `the ${point} grips' height`);
  }
  for (const point of ['wide', 'standard']) close(contact(kit, point).at[0].z, 0, 1e-6, `${point} hangs on the cross tube, at the origin`);
  assert.ok(contact(kit, 'close').at[0].z > 0.05, 'the close grips are on the V, nearer the plate than the cross tube');
  assert.ok(contact(kit, 'neutral').at[0].z < 0, 'the neutral grips are further out the prongs, toward the lifter');
});

test('the dip handles splay from 451 mm apart at the crossbar to 630 at their tips, 1.30 m up, and the close grip is the inner end', () => {
  const kit = station();
  const [dl, dr] = contact(kit, 'dip').at;
  const [cl, cr] = contact(kit, 'dipClose').at;
  close(dl.y, 1.3, 0.002, 'the handles\' height');
  assert.ok(dr.x - dl.x > 0.451 && dr.x - dl.x < 0.63, `the grips' spread ${dr.x - dl.x}, between the two`);
  assert.ok(cr.x - cl.x < dr.x - dl.x && cr.x - cl.x >= 0.451, 'the close grip, nearer the crossbar, is narrower');
  assert.ok(cl.z > dl.z, 'the close grip is nearer the rack (greater z)');
  const handles = held(kit).filter((m) => new THREE.Box3().setFromObject(m).distanceToPoint(dl) < 1e-6);
  assert.equal(handles.length, 1, 'a held handle at the dip grip');
  const box = new THREE.Box3().setFromObject(handles[0]);
  close(box.max.z - box.min.z, 0.44, 0.03, 'the handle\'s length');
});

test('every grip is on a held part, the bar\'s 32 mm and the dip handles\' 48', () => {
  const kit = station();
  const parts = held(kit);
  for (const [point, mm] of [['wide', 31.75], ['standard', 31.75], ['close', 31.75], ['neutral', 31.75], ['dip', 47.6], ['dipClose', 47.6]]) {
    for (const p of contact(kit, point).at) {
      const on = parts.filter((m) => new THREE.Box3().setFromObject(m).distanceToPoint(p) < 1e-6);
      assert.ok(on.length, `no held part at the ${point} grip`);
      for (const m of on) {
        m.geometry.computeBoundingBox();
        const size = m.geometry.boundingBox.getSize(new THREE.Vector3());
        close(Math.min(size.x, size.z) * 1000, mm, 0.6, `the ${point} grip's diameter, in mm`);
      }
    }
  }
});

test('the rig stands on the floor under the bar and the plate, with no fit rule', () => {
  const kit = station();
  assert.deepEqual(KITS.pullup_dip_station.fit ?? [], []);
  const all = new THREE.Box3().setFromObject(kit.group);
  close(all.min.y, 0, 0.01, 'the rig\'s foot');
  assert.ok(all.max.y > 2.4 && all.max.y < 2.7, `the rig\'s height ${all.max.y}`);
});

test('its recipe names no brand', async () => {
  const source = await readFile(path.join(HERE, 'recipes', 'pullup-dip-station.mjs'), 'utf8');
  assert.doesNotMatch(source, /rogue|crown|matador|monster|titan|hoist|hammer|\blogo\b|decal/i);
});

test('the studio shows it alone, with nothing to scrub', async () => {
  const entry = shownAlone('pullup_dip_station');
  assert.ok(entry?.camera, 'a framing to open on');
  const stage = await buildStage(entry);
  stage.pose(0);
  assert.ok(stage.kit.kits.pullup_dip_station);
});
