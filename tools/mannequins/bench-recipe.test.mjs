// The bench's shape, from its reference (refs/bench/ref.md): the published sizes, which end rolls
// and which lifts, and the back pad's range.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import * as THREE from 'three';
import bench, { DECLINE, TOP } from './recipes/bench.mjs';
import { BENCH_ANGLES, benchAngles, prepare } from './entries.mjs';
import { POSES } from './poses.mjs';
import { FIXTURE } from './adjustable.fixture.mjs';
import { buildKit } from './kit.mjs';

const near = (a, b, why, by = 0.002) => assert.ok(Math.abs(a - b) <= by, `${why}: ${a.toFixed(4)}, not ${b}`);
const box = (kit) => { kit.group.updateWorldMatrix(true, true); return new THREE.Box3().setFromObject(kit.group); };

test('it is as long, as wide and as high as the published sizes', () => {
  const b = box(bench.make({ adjustable: true }));
  near(b.max.z - b.min.z, 1.321, 'length');
  near(b.max.x - b.min.x, 0.572, 'footprint width');
  near(b.max.y, 0.457, 'height');
  near(b.min.y, 0, 'on the floor');
});

test('the baked angles run to the back pad\'s highest', () => {
  assert.deepEqual(BENCH_ANGLES, [0, 15, 30, 45, 60, 75, 85]);
  assert.equal(BENCH_ANGLES.at(-1), TOP);
});

/** The world box of the parts in `tone`, the ones made of it. */
const toneBox = (kit, tone) => {
  kit.group.updateWorldMatrix(true, true);
  const b = new THREE.Box3();
  kit.group.traverse((o) => { if (o.isMesh && o.material.name === tone) b.expandByObject(o); });
  return b;
};
const named = (kit, name) => { let found; kit.group.traverse((o) => { if (o.name === name) found = o; }); return found; };

test('it rolls on wheels at the head end, off the floor until the seat end is lifted', () => {
  const kit = bench.make({ adjustable: true });
  const wheels = toneBox(kit, 'pulley');
  const middle = box(kit).getCenter(new THREE.Vector3()).z;
  assert.ok(wheels.max.z < middle - 0.4, `wheels end at z ${wheels.max.z}, the bench's middle at ${middle}`);
  near(wheels.min.y, 0.01, 'wheels off the floor', 0.004);
});

test('the foot to the wheel is the published footprint', () => {
  const kit = bench.make({ adjustable: true });
  const frame = toneBox(kit, 'frame');
  const wheels = toneBox(kit, 'pulley');
  near(frame.max.z - wheels.min.z, 1.302, 'footprint length', 0.004);
});

test('the back pad scrubs from flat to its top and never reaches the seat', () => {
  const kit = bench.make({ adjustable: true });
  kit.place({});
  const { limits: [min, max] } = kit.joints.back;
  near(min, 0, 'from flat');
  near((max * 180) / Math.PI, 85, 'to 85°', 1e-6);
  const pad = named(kit, 'bench_back_pad');
  const seatBack = -0.2105 + 1.321 / 2 - 0.33;
  for (let a = 0; a <= 85; a += 5) {
    kit.scrub('back', (a * Math.PI) / 180);
    kit.group.updateWorldMatrix(true, true);
    const at = new THREE.Box3().setFromObject(pad);
    assert.ok(at.max.z < seatBack, `at ${a}° the pad reaches z ${at.max.z}, the seat's back at ${seatBack}`);
  }
  // The hinge 0.4 m up, the pad's far end 0.903 m from it, turned 85°.
  near(new THREE.Box3().setFromObject(pad).max.y, 1.305, 'upright, its top', 0.005);
});

test('it stays flat, or raised and fixed, with no joint unless adjustable', () => {
  for (const options of [{}, { angle: 80 }]) {
    const kit = bench.make(options);
    kit.place({});
    assert.deepEqual(Object.keys(kit.joints), []);
  }
  const raised = box(bench.make({ angle: 80 }));
  assert.ok(raised.max.y > 1.2, `raised, its top at ${raised.max.y}`);
  near(box(bench.make({})).max.y, 0.457, 'flat, its top');
});

test('the decline kit carries two pairs of rollers past the back pad\'s end, turning with it', () => {
  const plain = box(bench.make({ adjustable: true }));
  const kit = bench.make({ adjustable: true, decline: true, angle: 0 });
  kit.place({});
  const flat = box(kit);
  assert.ok(flat.min.z < plain.min.z - 0.2, `reaches z ${flat.min.z}, past the plain bench's ${plain.min.z}`);
  const rollers = [];
  kit.group.traverse((o) => { if (o.isMesh && o.name.startsWith('bench_back') && o.material.name === 'vinyl' && o.userData.solid && o.name !== 'bench_back_pad') rollers.push(o); });
  assert.equal(rollers.length, 4, 'two pairs');
  const low = (o) => { o.updateWorldMatrix(true, false); return o.getWorldPosition(new THREE.Vector3()).y; };
  const before = low(rollers[0]);
  kit.scrub('back', Math.PI / 6);
  assert.ok(low(rollers[0]) > before + 0.3, 'the rollers rise with the pad');
});

test('no maker\'s name in the recipe', async () => {
  const source = await readFile(new URL('./recipes/bench.mjs', import.meta.url), 'utf8');
  assert.doesNotMatch(source, /rogue|ab-?[23]\b/i);
});

test('each side of the base is three tubes cut to meet on sharp mitres, the legs butted into the feet', () => {
  const kit = bench.make({});
  kit.group.updateWorldMatrix(true, true);
  const pieces = [];
  kit.group.traverse((o) => { if (o.isMesh && o.name.startsWith('bench_rail')) pieces.push(o); });
  assert.equal(pieces.length, 6, 'a leg, a spine and a leg a side');
  const points = (o) => {
    const at = o.geometry.attributes.position;
    return Array.from({ length: at.count }, (_, i) => new THREE.Vector3().fromBufferAttribute(at, i).applyMatrix4(o.matrixWorld));
  };
  const side = pieces.filter((o) => new THREE.Box3().setFromObject(o).min.x > 0);
  const [rear, spine, front] = side.sort((a, b) => new THREE.Box3().setFromObject(a).getCenter(new THREE.Vector3()).z - new THREE.Box3().setFromObject(b).getCenter(new THREE.Vector3()).z);
  const top = 0.457 - 0.128;
  const spineBox = new THREE.Box3().setFromObject(spine);
  near(spineBox.max.y, top, 'the spine\'s top', 0.001);
  for (const [leg, end] of [[front, spineBox.max.z], [rear, spineBox.min.z]]) {
    const b = new THREE.Box3().setFromObject(leg, true);
    // No tube's corner stands proud of the spine, and the spine's top corner is the leg's: one sharp point.
    near(b.max.y, top, 'the leg\'s top', 0.001);
    const corner = points(leg).reduce((best, p) => (Math.abs(p.z - end) + Math.abs(p.y - top) < Math.abs(best.z - end) + Math.abs(best.y - top) ? p : best));
    assert.ok(Math.hypot(corner.z - end, corner.y - top) < 0.004, `the leg's corner is ${Math.hypot(corner.z - end, corner.y - top)} from the spine's`);
    // The foot's top is 7.4 cm up: the leg ends just inside it, with no lip on top and no gap.
    assert.ok(b.min.y < 0.074 && b.min.y > 0.07, `a leg ends at ${b.min.y}, not just inside the foot`);
  }
});

test('each tube of the base is solid only where it is, for the checks\' box in its own axes', () => {
  const kit = bench.make({});
  const pieces = [];
  kit.group.traverse((o) => { if (o.isMesh && o.name.startsWith('bench_rail')) pieces.push(o); });
  for (const piece of pieces) {
    piece.geometry.computeBoundingBox();
    const size = piece.geometry.boundingBox.getSize(new THREE.Vector3()).toArray().sort((a, b) => a - b);
    near(size[0], 0.051, 'its width');
    near(size[1], 0.051, 'its depth');
  }
});

test('each leg meets the middle of its foot beam, where it crosses the beam\'s top', () => {
  const kit = bench.make({});
  kit.group.updateWorldMatrix(true, true);
  const legs = [];
  kit.group.traverse((o) => { if (o.isMesh && o.name.startsWith('bench_rail') && Math.abs(o.rotation.x) > 0.1 && Math.abs(Math.abs(o.rotation.x) - Math.PI) > 0.1) legs.push(o); });
  assert.equal(legs.length, 4, 'two legs a side');
  const beamTop = 0.074;
  // Where the leg's edges cross the beam's top.
  const crossing = (leg) => {
    const at = leg.geometry.toNonIndexed().attributes.position;
    const p = Array.from({ length: at.count }, (_, i) => new THREE.Vector3().fromBufferAttribute(at, i).applyMatrix4(leg.matrixWorld));
    const zs = [];
    for (let i = 0; i < p.length; i += 3) {
      for (const [a, b] of [[p[i], p[i + 1]], [p[i + 1], p[i + 2]], [p[i + 2], p[i]]]) {
        if ((a.y - beamTop) * (b.y - beamTop) < 0) zs.push(a.z + ((beamTop - a.y) / (b.y - a.y)) * (b.z - a.z));
      }
    }
    return [Math.min(...zs), Math.max(...zs)];
  };
  // The beams' middles, for the default bench: its hinge 3.2 cm ahead of the middle of the body.
  const beams = [0.032 - 0.782, 0.032 + 0.347];
  for (const leg of legs) {
    const [from, to] = crossing(leg);
    const beam = beams.reduce((n, b) => (Math.abs(b - (from + to) / 2) < Math.abs(n - (from + to) / 2) ? b : n));
    near((from + to) / 2, beam, 'the leg\'s middle on the beam', 0.001);
    assert.ok(to - from < 0.076, `the leg is ${to - from} along the beam's 0.076`);
    // Its end at the beam stays within the beam's 0.076, on the side away from the spine.
    const { min, max } = new THREE.Box3().setFromObject(leg, true);
    const end = Math.abs(min.z - beam) < Math.abs(max.z - beam) ? min.z : max.z;
    assert.ok(Math.abs(end - beam) <= 0.039, `the leg's end is ${Math.abs(end - beam)} off the beam's middle`);
  }
});

test('only a decline movement has the decline kit', () => {
  const plain = bench.make({ adjustable: true });
  const rollers = [];
  plain.group.traverse((o) => { if (o.isMesh && o.material.name === 'vinyl' && o.userData.solid && o.name.startsWith('bench_back') && o.name !== 'bench_back_pad') rollers.push(o); });
  assert.deepEqual(rollers, [], 'off unless asked for');
  for (const id of Object.keys(POSES)) {
    const options = prepare(id).kit?.bench;
    if (options?.decline) assert.match(id, /^decline_/, `${id} isn't a decline movement`);
  }
});

test('with the decline kit the bench stands at its decline setting, the pad raised for a lifter lying the other way round', () => {
  assert.equal(DECLINE, 15);
  const value = (options) => { const kit = bench.make(options); kit.place({}); return (kit.joints.back.value * 180) / Math.PI; };
  near(value({ adjustable: true, decline: true }), 15, 'decline', 1e-9);
  near(value({ adjustable: true }), 0, 'without it, flat', 1e-9);
  near(value({ adjustable: true, decline: true, angle: 20 }), 20, 'an angle of its own', 1e-9);
});

test('a decline movement is baked at the decline setting, the rest at every angle', () => {
  assert.deepEqual(benchAngles('decline_bench_press'), [DECLINE]);
  assert.deepEqual(benchAngles('decline_db_press'), [DECLINE]);
  assert.deepEqual(benchAngles('db_incline_press'), BENCH_ANGLES);
});

test('an incline press sits a bit upright by default', () => {
  assert.equal(prepare('db_incline_press').bench.typical, 45);
});

const seatFront = (kit) => {
  kit.group.updateWorldMatrix(true, true);
  return new THREE.Box3().setFromObject(named(kit, 'bench_seat_pad'), true);
};

test('the seat tilts about the hinge, from 15° down to 40° up, on a joint of its own', () => {
  const kit = bench.make({ adjustable: true });
  kit.place({});
  const { limits: [min, max] } = kit.joints.seat;
  near((min * 180) / Math.PI, -15, 'down to', 1e-6);
  near((max * 180) / Math.PI, 40, 'up to', 1e-6);
  const flat = seatFront(kit).max.y;
  near(flat, 0.45, 'flat, its top');
  kit.scrub('seat', max);
  assert.ok(seatFront(kit).max.y > flat + 0.2, `at 40° its top reaches ${seatFront(kit).max.y}`);
  kit.scrub('seat', min);
  assert.ok(seatFront(kit).min.y < 0.393 - 0.05, `at -15° its front drops to ${seatFront(kit).min.y}`);
});

test('the seat and the back pad turn apart, and the handle goes with the seat', () => {
  const kit = bench.make({ adjustable: true });
  kit.place({});
  const back = () => { kit.group.updateWorldMatrix(true, true); return new THREE.Box3().setFromObject(named(kit, 'bench_back_pad'), true).max.y; };
  const before = back();
  kit.scrub('seat', Math.PI / 6);
  near(back(), before, 'the back pad stays', 1e-9);
  const moved = kit.bodies.seat.flatMap((b) => { const all = []; b.traverse((o) => { if (o.isMesh) all.push(o.material.name); }); return all; });
  assert.ok(moved.includes('metal'), 'the handle is on the seat');
});

test('a pose can set the seat, which is flat by default', () => {
  const value = (options) => { const kit = bench.make(options); kit.place({}); return (kit.joints.seat.value * 180) / Math.PI; };
  near(value({ adjustable: true }), 0, 'flat', 1e-9);
  near(value({ adjustable: true, seat: 20 }), 20, 'set', 1e-9);
  const fixed = box(bench.make({ seat: 20 }));
  assert.ok(fixed.max.y > 0.5, `a fixed bench's seat stands at 20° too: ${fixed.max.y}`);
});

test('every part of the bench has a name of its own, which the animation finds it by', () => {
  const names = [];
  bench.make({ adjustable: true, decline: true }).group.traverse((o) => names.push(o.name));
  const twice = names.filter((n, i) => names.indexOf(n) !== i);
  assert.deepEqual(twice, []);
});

test('a scrubbed pad holds still while the studio poses the kit every frame', () => {
  for (const [body, part, value] of [['seat', 'bench_seat_pad', 0.5], ['back', 'bench_back_pad', 0.5]]) {
    const kit = bench.make({ adjustable: true });
    const at = () => { kit.group.updateWorldMatrix(true, true); return named(kit, part).getWorldPosition(new THREE.Vector3()); };
    const frame = () => { kit.place({}); kit.scrub(body, value); };
    frame();
    const first = at();
    for (let f = 0; f < 5; f++) frame();
    assert.ok(at().distanceTo(first) < 1e-9, `${body} moved ${at().distanceTo(first)} in five frames`);
    near(kit.joints[body].value, value, `${body}'s value`, 1e-9);
  }
});

test('a decline pose stands its bench at the decline setting, through to the kit it builds', () => {
  const poses = { decline_db_press: { ...FIXTURE, kit: { dumbbells: {}, bench: { hinge: -0.02, decline: true } } } };
  const entry = prepare('decline_db_press', poses);
  assert.equal(entry.bench.angle, DECLINE);
  const { bench: built } = buildKit(entry).kits;
  built.place({});
  near((built.joints.back.value * 180) / Math.PI, DECLINE, 'the kit\'s back pad', 1e-9);
});
