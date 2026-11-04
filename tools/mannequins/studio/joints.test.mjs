// A kit's joints are scrubbed in the studio through their limits (#159): each joint of the kit on
// stage is a row, stepped a degree or a centimetre at a time, typed, or sent to either limit.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from '../entries.mjs';
import { buildStage } from '../stage.mjs';
import { jointsOf, numberRows, settled, showAlone, stepJoint, withoutBack, typedJoint, limitOf, showJoint, scrubAll, inUnits, limitsOf, stepsOf, statusOf } from './joints.mjs';
import { buildKit } from '../kit.mjs';

const rad = (d) => (d * Math.PI) / 180;
const near = (a, b, why) => assert.ok(Math.abs(a - b) < 1e-9, `${why}: ${a}, not ${b}`);

const posed = async (id) => {
  const stage = await buildStage(prepare(id));
  stage.pose(0);
  return stage;
};

test("each joint of the kit on stage is a row: its kit, its body, its type and its limits", async () => {
  const extension = jointsOf((await posed('leg_extension')).kit.kits);
  assert.deepEqual(extension.map(({ key, kit, body, type }) => ({ key, kit, body, type })), [{ key: 'leg_extension.lever', kit: 'leg_extension', body: 'lever', type: 'revolute' }]);
  assert.deepEqual(extension[0].limits, [0, rad(95)]);
  const press = jointsOf((await posed('leg_press')).kit.kits);
  assert.deepEqual(press.map(({ key, type }) => [key, type]), [['leg_press.sled', 'prismatic']]);
  // A barbell is carried, on no joint.
  assert.deepEqual(jointsOf((await posed('barbell_squat')).kit.kits), []);
});

test('a joint steps a degree or a centimetre, a tenth of that fine, and never past its limits', async () => {
  const [lever] = jointsOf((await posed('leg_extension')).kit.kits);
  near(stepJoint(lever, 0, 5), rad(5), 'five steps on');
  near(stepJoint(lever, rad(5), -3, true), rad(4.7), 'three fine steps back');
  near(stepJoint(lever, rad(2), -5), 0, 'past its lower limit');
  near(stepJoint(lever, rad(93), 5), rad(95), 'past its upper limit');
  const [sled] = jointsOf((await posed('leg_press')).kit.kits);
  near(stepJoint(sled, 0.1, 2), 0.12, 'two steps up the rails');
  near(limitOf(sled, 1), 0.45, 'its upper limit');
  near(limitOf(sled, -1), 0, 'its lower limit');
});

test('a joint is typed and shown in degrees or millimetres, a + moving it from where it is', async () => {
  const [lever] = jointsOf((await posed('leg_extension')).kit.kits);
  near(typedJoint(lever, '45', 0), rad(45), 'typed');
  near(typedJoint(lever, '+10', rad(20)), rad(30), 'moved');
  near(typedJoint(lever, '500', 0), rad(95), 'past its limit');
  assert.equal(typedJoint(lever, 'far', 0), null);
  assert.equal(showJoint(lever, rad(30)), '30.0°');
  assert.equal(inUnits(lever, rad(30)), 30);
  assert.equal(limitsOf(lever), '0.0° to 95.0°');
  assert.deepEqual(stepsOf(lever), ['1°', '0.1°']);
  const [sled] = jointsOf((await posed('leg_press')).kit.kits);
  near(typedJoint(sled, '120', 0), 0.12, 'typed in millimetres');
  assert.equal(showJoint(sled, 0.12), '120 mm');
  assert.equal(inUnits(sled, 0.12), 120);
  assert.deepEqual(stepsOf(sled), ['1 cm', '1 mm']);
});

// The leg extension's stack rises 22 cm a radian of its lever.
test('each scrubbed joint is set on its kit after the pose, the stack with it', async () => {
  const stage = await posed('leg_extension');
  const kit = stage.kit.kits.leg_extension;
  let head;
  kit.group.traverse((o) => { if (o.userData.stack) head = o; });
  const height = () => { head.updateWorldMatrix(true, false); return head.getWorldPosition(head.position.clone()).y; };
  scrubAll(stage.kit.kits, { 'leg_extension.lever': 0 });
  const rest = height();
  scrubAll(stage.kit.kits, { 'leg_extension.lever': rad(60) });
  near(kit.joints.lever.value, rad(60), 'the lever');
  near(height() - rest, 0.22 * rad(60), 'the stack, risen');
  stage.pose(0);
  scrubAll(stage.kit.kits, { 'leg_extension.lever': rad(60), 'no_kit.lever': 1 });
  near(kit.joints.lever.value, rad(60), 'set again after the next pose');
});

test('the status line says where the joint is after every move, and which limit it is at', async () => {
  const [sled] = jointsOf((await posed('leg_press')).kit.kits);
  assert.equal(statusOf(sled, 0.45), 'leg_press sled 450 mm, at its upper limit');
  assert.equal(statusOf(sled, stepJoint(sled, 0.45, -5)), 'leg_press sled 400 mm');
  assert.equal(statusOf(sled, 0), 'leg_press sled 0 mm, at its lower limit');
});

test('a kit is shown alone: the bench without the dumbbells its exercise holds', () => {
  const { kits } = buildKit(prepare('db_incline_press'));
  assert.deepEqual(Object.keys(kits).sort(), ['bench', 'dumbbells']);
  showAlone(kits, 'bench');
  assert.equal(kits.bench.group.visible, true);
  assert.equal(kits.dumbbells.group.visible, false);
});

test('a joint\'s row has no number behind it: repainting the numbers passes it by', () => {
  const number = { dataset: { path: 'grip.width' } };
  const joint = { dataset: { joint: 'bench.back' } };
  const width = { path: 'grip.width', value: 0.4 };
  assert.deepEqual(numberRows([joint], []), []);
  assert.deepEqual(numberRows([number, joint], [width]), [[number, width]]);
});

test('a scrubbed bench holds still through the studio\'s frames, its pads where they are set', async () => {
  const stage = await posed('db_incline_press');
  const { bench } = stage.kit.kits;
  const nodes = { back: bench.bodies.back[0], seat: bench.bodies.seat[0] };
  const turned = (node) => node.quaternion.clone();
  const frame = (d) => { stage.pose(d); scrubAll(stage.kit.kits, { 'bench.back': rad(60), 'bench.seat': rad(20) }); };
  frame(0);
  const held = { back: turned(nodes.back), seat: turned(nodes.seat) };
  for (let f = 0; f < 6; f++) frame(f / 6);
  // acos near 1 leaves rounding of about 1e-8 where a spin would show a quarter turn.
  for (const body of ['back', 'seat']) assert.ok(turned(nodes[body]).angleTo(held[body]) < 1e-6, `${body} turned on`);
  near(bench.joints.back.value, rad(60), 'the back');
  near(bench.joints.seat.value, rad(20), 'the seat');
});

const benchRows = async () => {
  const rows = jointsOf((await posed('db_incline_press')).kit.kits);
  return { back: rows.find((r) => r.body === 'back'), seat: rows.find((r) => r.body === 'seat') };
};
const degrees = (list) => list.map((v) => Math.round((v * 180) / Math.PI));

test('the bench\'s pads have the settings its holes give them', async () => {
  const { back, seat } = await benchRows();
  assert.deepEqual(degrees(back.settings), [0, 8, 18, 28, 38, 48, 58, 68, 78, 85]);
  assert.deepEqual(degrees(seat.settings), [-15, 0, 10, 20, 30, 40]);
});

test('a dragged pad settles on the nearest hole', async () => {
  const { back, seat } = await benchRows();
  near(settled(back, rad(31)), rad(28), 'the back, down to 28');
  near(settled(back, rad(34)), rad(38), 'the back, up to 38');
  near(settled(seat, rad(-9)), rad(-15), 'the seat, down to -15');
  near(settled(seat, rad(4)), 0, 'the seat, to flat');
});

test('a step moves a pad a hole at a time, and stops at the last', async () => {
  const { back, seat } = await benchRows();
  near(stepJoint(seat, 0, 1), rad(10), 'up a hole');
  near(stepJoint(seat, 0, -1), rad(-15), 'down a hole');
  near(stepJoint(seat, 0, 2), rad(20), 'two holes');
  near(stepJoint(back, rad(78), 1), rad(85), 'to the top');
  near(stepJoint(back, rad(85), 3), rad(85), 'no further');
  near(stepJoint(back, 0, 1, true), rad(8), 'a fine step is still a hole');
  assert.deepEqual(stepsOf(back), ['a hole', 'a hole']);
});

test('a typed angle lands on the nearest hole', async () => {
  const { seat } = await benchRows();
  near(typedJoint(seat, '23', 0), rad(20), 'typed');
  near(typedJoint(seat, '+7', rad(20)), rad(30), 'moved');
  near(typedJoint(seat, '500', 0), rad(40), 'past its limit');
});

test('a joint with no holes stays free', async () => {
  const [lever] = jointsOf((await posed('leg_extension')).kit.kits);
  assert.equal(lever.settings, undefined);
  near(settled(lever, rad(31.5)), rad(31.5), 'where it is put');
});

test('stepping the bench angle gives the back pad back, and keeps a seat set by hand', () => {
  assert.deepEqual(withoutBack({ 'bench.back': 1, 'bench.seat': 0.3 }), { 'bench.seat': 0.3 });
});
