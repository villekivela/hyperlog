// A kit's joints are scrubbed in the studio through their limits (#159): each joint of the kit on
// stage is a row, stepped a degree or a centimetre at a time, typed, or sent to either limit.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepare } from '../entries.mjs';
import { buildStage } from '../stage.mjs';
import { jointsOf, stepJoint, typedJoint, limitOf, showJoint, scrubAll, inUnits, limitsOf, stepsOf } from './joints.mjs';

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
  assert.deepEqual(extension[0].limits, [0, rad(120)]);
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
  near(stepJoint(lever, rad(118), 5), rad(120), 'past its upper limit');
  const [sled] = jointsOf((await posed('leg_press')).kit.kits);
  near(stepJoint(sled, 0.1, 2), 0.12, 'two steps up the rails');
  near(limitOf(sled, 1), 0.45, 'its upper limit');
  near(limitOf(sled, -1), 0, 'its lower limit');
});

test('a joint is typed and shown in degrees or millimetres, a + moving it from where it is', async () => {
  const [lever] = jointsOf((await posed('leg_extension')).kit.kits);
  near(typedJoint(lever, '45', 0), rad(45), 'typed');
  near(typedJoint(lever, '+10', rad(20)), rad(30), 'moved');
  near(typedJoint(lever, '500', 0), rad(120), 'past its limit');
  assert.equal(typedJoint(lever, 'far', 0), null);
  assert.equal(showJoint(lever, rad(30)), '30.0°');
  assert.equal(inUnits(lever, rad(30)), 30);
  assert.equal(limitsOf(lever), '0.0° to 120.0°');
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
