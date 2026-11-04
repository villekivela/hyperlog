// A selectorised seated lateral raise (refs/lateral_raise_machine/ref.md): the lifter sits facing
// the machine with the chest on a centre pad. Two arm levers turn about front-to-back axes through
// the shoulders, each a dog-leg from its pivot out and down to a pad under the outside of the upper
// arm, with a grip at the pad's front end. One weight stack stands ahead.
//
// The pivots are fixed on the frame, 1.10 m up and on the mannequin's shoulders, and the seat moves to
// them. Each lever is a body on a revolute joint, its value the upper arm's angle out from hanging,
// from 10° to horizontal. The arms rise together in both of the ref's videos, so the stack hangs
// on the left arm's joint. The grips don't pivot, the cable and its pulleys run only from the stack's
// head to the top pulley, and the standing lateral raise, which the ref gives its own geometry, is
// listed with this kit's exercises but has none yet.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { UPRIGHTS, WHEEL, floorRail, hub, part, pulley, rodGeometry, seatOn, span, stackFrame, stretchedTube, tube, weightStack, wire } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

const LIMITS = [rad(10), rad(90)];
const PIVOT_Y = 1.1;
// The mannequin's shoulders, which the ref lets set the pivots' spacing.
const SHOULDER_X = 0.192;
// The levers lie in a plane this far ahead of the hips, in front of the forearms' hands; the upper
// arms hang this far from it.
const PLANE = 0.2;
const ARM_Z = -0.065;
// Pivot to the pad's middle along the arm, and out of the arm's line; the pad 250 long, 100 across.
const REACH = 0.35;
const OUT = 0.1;
const PAD = { length: 0.25, radius: 0.05 };
const GRIP = 0.06;
// The stack stands in a tower beside the user, on their right, as in the ref's photos, its bridge
// carrying the pivots across ahead of the chest.
const TOWER_X = -0.45;
const STACK_Z = 0.42;
// The base runs from under the seat to 1.07 m ahead of it, as the ref's footprint does.
const REAR = -0.36;
const FRONT = 0.62;
const FRAME_TOP = 1.4;
const SEAT = 0.48;
const SITS = 0.09;
const SIDES = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

/** Where the arm on side `s` runs, and the way out of it, with the upper arm `v` out from hanging. */
const armDir = (s, v) => V(s * Math.sin(v), -Math.cos(v), 0);
const outOf = (s, v) => V(s * Math.cos(v), Math.sin(v), 0);
const pivotOf = (s) => V(s * SHOULDER_X, PIVOT_Y, PLANE);

export default recipe({
  name: 'lateral_raise_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from behind the seat, the tower on the right, as the ref's hero photo has it.
  standsAlone: { camera: { target: [0, 0.8, 0.1], halfHeight: 1.1, azimuth: 215, elevation: 12 } },
  bodies: Object.fromEntries(SIDES.map(({ k, s }) => [`arm_${k}`, revolute({ axis: V(0, 0, s), limits: LIMITS })])),
  contacts: SIDES.flatMap(({ k }) => [
    { body: `arm_${k}`, point: `grip_${k}`, landmark: 'hands' },
    { body: `arm_${k}`, point: `pad_${k}`, landmark: 'upperArms' },
  ]).concat([{ body: 'frame', point: 'chestPad', landmark: 'chest' }]),
  stacks: [{ joint: 'arm_l', ratio: 0.2 }],
  fit: [{
    sets: 'seat',
    rule: 'the seat so the shoulder joint is level with the fixed pivots, in its range of 0.40 to 0.56 m',
    solve: ({ L }) => THREE.MathUtils.clamp(PIVOT_Y - L.trunkUp - SITS, 0.4, 0.56),
  }],
  build({ seat = SEAT } = {}, M) {
    const weights = weightStack(V(TOWER_X, 0, STACK_Z), M, { rodTop: FRAME_TOP - 0.07, toward: -1 });
    const wheel = V(TOWER_X, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z + WHEEL);
    // The tower's frame and pulley are built about the middle, then stood to the side.
    const tower = [...stackFrame(STACK_Z, FRAME_TOP, M), ...pulley(V(0, wheel.y, wheel.z), M)];
    tower.forEach((m) => { m.position.x += TOWER_X; });
    const drop = wire(M);
    const chest = V(0, 0.83, 0.13);
    const chestPad = part(new THREE.BoxGeometry(0.3, 0.3, 0.08), M.vinyl, { solid: true });
    chestPad.position.copy(chest);
    // Hubs on the pivots, each on a bracket from a bridge across the machine at the tower's depth,
    // out of the tower's near upright, with a strut down to the chest pad.
    const bridgeAt = (x) => V(x, PIVOT_Y, STACK_Z);
    const fixed = [
      tube(0.06, bridgeAt(TOWER_X + UPRIGHTS), bridgeAt(SHOULDER_X), M.frame),
      tube(0.05, bridgeAt(0), V(0, 0.95, 0.17), M.frame),
      ...SIDES.flatMap(({ s }) => {
        const at = pivotOf(s);
        const boss = hub(M, at);
        boss.rotation.y = Math.PI / 2;
        return [boss, tube(0.05, bridgeAt(s * SHOULDER_X), at, M.frame)];
      }),
    ];
    const levers = SIDES.map(() => ({
      out: stretchedTube(0.04, M.frame),
      down: stretchedTube(0.04, M.frame),
      back: stretchedTube(0.04, M.frame),
      pad: part(rodGeometry(PAD.radius), M.vinyl, { solid: true }),
      grip: part(rodGeometry(0.0175), M.vinyl, { held: true }),
    }));
    const body = (l) => [l.out, l.down, l.back, l.pad, l.grip];
    return {
      parts: [
        ...seatOn(seat, 0, M, { pin: -1 }),
        ...floorRail(V(0, 0.035, REAR), V(0, 0.035, FRONT), M),
        ...floorRail(V(-0.25, 0.035, REAR), V(0.25, 0.035, REAR), M),
        ...floorRail(V(TOWER_X - 0.2, 0.035, FRONT), V(0.23, 0.035, FRONT), M),
        ...floorRail(V(TOWER_X, 0.035, STACK_Z), V(TOWER_X, 0.035, FRONT), M),
        ...floorRail(V(TOWER_X - 0.2, 0.035, STACK_Z), V(TOWER_X + 0.2, 0.035, STACK_Z), M),
        ...tower,
        ...weights.parts, drop,
        chestPad, tube(0.07, V(0, 0.75, chest.z + 0.04), V(0, 0.07, 0.36), M.frame),
        ...fixed, ...levers.flatMap(body),
      ],
      bodies: Object.fromEntries(SIDES.map(({ k }, i) => [`arm_${k}`, body(levers[i])])),
      stacks: {
        arm_l: {
          lift(l) {
            weights.lift(l);
            span(drop, V(TOWER_X, wheel.y, STACK_Z), weights.top(l));
          },
        },
      },
      drive(P) {
        // Without a pose the arms rest.
        const value = P.linkage?.value ?? LIMITS[0];
        const state = { joints: {}, points: {} };
        SIDES.forEach(({ k, s }, i) => {
          const l = levers[i];
          const pivot = pivotOf(s);
          const corner = pivot.clone().addScaledVector(outOf(s, value), OUT);
          // The pad's middle, on the arm, and the grip's, ahead of it in the levers' plane.
          const toGrip = armDir(s, value);
          const grip = corner.clone().addScaledVector(toGrip, REACH);
          const pad = grip.clone().setZ(ARM_Z);
          const [gripFrom, gripTo] = [-GRIP, GRIP].map((d) => grip.clone().addScaledVector(toGrip, d));
          const [padBack, padFront] = [-PAD.length / 2, PAD.length / 2].map((d) => pad.clone().setZ(ARM_Z + d));
          span(l.out, pivot, corner);
          span(l.down, corner, gripFrom);
          span(l.grip, gripFrom, gripTo);
          span(l.back, grip, padFront);
          span(l.pad, padBack, padFront);
          state.joints[`arm_${k}`] = { value, origin: pivot };
          state.points[`grip_${k}`] = [grip];
          state.points[`pad_${k}`] = [pad];
        });
        state.points.chestPad = [chest.clone()];
        return state;
      },
    };
  },
});
