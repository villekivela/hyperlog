// The recipe format (ADR 0007): a kit is a fixed frame and moving bodies, each on a joint with its
// limits, with contacts to the mannequin, stacks that rise by a ratio of a joint's travel, and fit
// rules. These build a small recipe of their own and turn, slide and tie it.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { recipe, revolute, prismatic, free } from './recipe.mjs';
import { pad, tube, turnedPart, rollerGeometry, weightStack } from './parts.mjs';
import { V, X, UP } from './rig.mjs';

const PIVOT = V(0, 0.5, 0);

/**
 * A lever hanging from a pivot 0.5 m up, a roller 0.4 m down it; a seat that slides up its post; a
 * stack beside them that rises half the lever's turn. The pose frame's `turn` is the lever's angle
 * and `lift` the seat's.
 */
const swing = recipe({
  name: 'test_swing',
  bodies: {
    lever: revolute({ axis: X, limits: [0, Math.PI / 2] }),
    seat: prismatic({ axis: UP, limits: [0, 0.2] }),
  },
  contacts: [{ body: 'lever', point: 'roller', landmark: 'shins' }],
  stacks: [{ joint: 'lever', ratio: 0.5 }],
  fit: [{ sets: 'seat', rule: 'the knee on the pivot axis' }],
  build(options, M) {
    const lever = new THREE.Group();
    const arm = tube(0.05, V(0, 0, 0), V(0, -0.4, 0), M.frame);
    const roller = turnedPart(rollerGeometry(0.06, 0.36), M.vinyl, V(0, -0.4, 0));
    lever.add(arm, roller);
    const seat = pad(0.36, 0.075, 0.38, M.vinyl, V(0, 0.4, -0.3));
    const weights = weightStack(V(1, 0, 0), M, { rodTop: 1.5, toward: 1 });
    return {
      parts: [lever, seat, ...weights.parts],
      bodies: { lever: [lever], seat: [seat] },
      stacks: { lever: weights },
      drive(P) {
        lever.position.copy(PIVOT);
        lever.rotation.x = P.turn;
        seat.position.y = 0.4 + P.lift;
        return {
          joints: { lever: { value: P.turn, origin: PIVOT }, seat: { value: P.lift, origin: V(0, 0.4, -0.3) } },
          points: { roller: [roller.getWorldPosition(V(0, 0, 0))] },
        };
      },
    };
  },
});

const near = (a, b, why) => assert.ok(a.distanceTo(b) < 1e-6, `${why}: ${a.toArray().map((v) => v.toFixed(4))}, not ${b.toArray().map((v) => v.toFixed(4))}`);
const world = (o) => { o.updateWorldMatrix(true, false); return o.getWorldPosition(V(0, 0, 0)); };
const rollerOf = (kit) => kit.group.children[0].children[1];
const headOf = (kit) => { let head; kit.group.traverse((o) => { if (o.userData.stack) head = o; }); return head; };

test('a revolute joint turns its body about its axis through its origin', () => {
  const kit = swing.make();
  kit.place({ turn: 0, lift: 0 });
  near(world(rollerOf(kit)), V(0, 0.1, 0), 'hanging, the roller');
  kit.scrub('lever', Math.PI / 2);
  assert.equal(kit.joints.lever.value, Math.PI / 2);
  near(world(rollerOf(kit)), V(0, 0.5, -0.4), 'turned a quarter, the roller');
});

test('a joint stops at its limits', () => {
  const kit = swing.make();
  kit.place({ turn: 0, lift: 0 });
  kit.scrub('lever', 3);
  assert.equal(kit.joints.lever.value, Math.PI / 2, 'past its upper limit');
  near(world(rollerOf(kit)), V(0, 0.5, -0.4), 'held at the limit, the roller');
  kit.scrub('lever', -1);
  assert.equal(kit.joints.lever.value, 0, 'past its lower limit');
  near(world(rollerOf(kit)), V(0, 0.1, 0), 'back at rest, the roller');
});

test('a prismatic joint slides its body along its axis', () => {
  const kit = swing.make();
  kit.place({ turn: 0, lift: 0.05 });
  const seat = kit.group.children[1];
  near(world(seat), V(0, 0.45, -0.3), 'raised 5 cm, the seat');
  kit.scrub('seat', 0.15);
  near(world(seat), V(0, 0.55, -0.3), 'raised 15 cm, the seat');
});

// The stack's head plate stands 0.545 m up at rest (twelve 3.5 cm plates from 10 cm, its middle
// 2.5 cm over them), and a working stack is held 6 cm off its rest.
test("a stack rises by its ratio of its joint's travel, held off its rest", () => {
  const kit = swing.make();
  kit.place({ turn: 0, lift: 0 });
  assert.ok(Math.abs(world(headOf(kit)).y - (0.545 + 0.06)) < 1e-9, `at rest, the head at ${world(headOf(kit)).y.toFixed(4)}`);
  kit.place({ turn: 0.4, lift: 0 });
  assert.ok(Math.abs(world(headOf(kit)).y - (0.545 + 0.06 + 0.2)) < 1e-9, `turned 0.4 rad, the head at ${world(headOf(kit)).y.toFixed(4)}`);
  kit.scrub('lever', 1);
  assert.ok(Math.abs(world(headOf(kit)).y - (0.545 + 0.06 + 0.5)) < 1e-9, `scrubbed to 1 rad, the head at ${world(headOf(kit)).y.toFixed(4)}`);
});

test("a contact ties a body's point to a mannequin landmark, and moves with the body", () => {
  const kit = swing.make();
  kit.place({ turn: 0, lift: 0 });
  const [contact] = kit.contacts;
  assert.equal(contact.body, 'lever');
  assert.equal(contact.landmark, 'shins');
  near(contact.at[0], V(0, 0.1, 0), 'hanging, the contact');
  kit.scrub('lever', Math.PI / 2);
  near(kit.contacts[0].at[0], V(0, 0.5, -0.4), 'turned a quarter, the contact');
});

test("a recipe refuses a contact, stack, limit or fit rule it can't mean", () => {
  const make = (over) => recipe({ ...swing, ...over }).make();
  assert.throws(() => make({ contacts: [{ body: 'arm', point: 'roller', landmark: 'shins' }] }), /test_swing: a contact on "arm", which isn't a body/);
  assert.throws(() => make({ contacts: [{ body: 'lever', point: 'roller', landmark: 'elbows' }] }), /test_swing: no landmark "elbows"/);
  assert.throws(() => make({ stacks: [{ joint: 'arm', ratio: 0.5 }] }), /test_swing: a stack on "arm", which isn't a joint/);
  assert.throws(() => make({ bodies: { ...swing.bodies, bar: free() }, stacks: [{ joint: 'bar', ratio: 0.5 }] }), /test_swing: a stack on "bar", which isn't a joint/);
  assert.throws(() => make({ stacks: [{ joint: 'lever' }] }), /test_swing: the stack on "lever" has no ratio/);
  assert.throws(() => make({ bodies: { ...swing.bodies, lever: revolute({ axis: X, limits: [1, 0] }) } }), /test_swing: lever's limits \[1, 0\] are out of order/);
  assert.throws(() => make({ fit: [{ rule: 'the knee on the pivot axis' }] }), /test_swing: the fit rule "the knee on the pivot axis" sets nothing/);
});

test('only a joint can be scrubbed, and the drive gives every joint its value', () => {
  const carried = recipe({ ...swing, bodies: { ...swing.bodies, bar: free() } }).make();
  carried.place({ turn: 0, lift: 0 });
  assert.throws(() => carried.scrub('bar', 1), /test_swing: bar is a free body, on no joint to scrub/);
  const lame = recipe({ ...swing, build: (o, M) => ({ ...swing.build(o, M), drive: () => ({ joints: {} }) }) }).make();
  assert.throws(() => lame.place({ turn: 0, lift: 0 }), /test_swing: its drive gives lever no value/);
});
