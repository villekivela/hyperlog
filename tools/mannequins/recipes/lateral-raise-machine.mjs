// A selectorised seated lateral raise (refs/lateral_raise_machine/ref.md): the lifter sits facing
// the machine with the chest on a centre pad. Two arm levers turn about front-to-back axes through
// the shoulders, each a dog-leg from its pivot out and down to a pad under the outside of the upper
// arm, with a grip at the pad's front end. One post leans forward from the floor between the legs and
// carries the seat, the chest pad and the plate the pivots turn on. The weight stack stands in a tower
// at the far end of the base, past the arms' reach, its broad face across the machine, to the right.
//
// The pivots are fixed on the frame, 1.15 m up and on the mannequin's shoulders, and the seat moves to
// them. Each lever is a body on a revolute joint, its value the upper arm's angle out from hanging,
// from 10° to horizontal. The arms rise together in both of the ref's videos, so the stack hangs
// on the left arm's joint. The grips pivot on the levers, holding one line through the rep, and the
// cable and its pulleys run only from the stack's head to the top pulley, and the standing lateral raise, which the ref gives its own geometry, is
// listed with this kit's exercises but has none yet.
import * as THREE from 'three';
import { HELD, recipe, revolute } from '../recipe.mjs';
import { UPRIGHTS, WHEEL, floorRail, mitered, pad, part, pivotJoint, rod, rodGeometry, span, stackFrame, tube, weightStack, wire } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

const LIMITS = [rad(10), rad(90)];
// How far the stack rises, in metres a radian of the left arm.
const STACK_RATIO = 0.17;
const PIVOT_Y = 1.15;
// The mannequin's shoulders, which the ref lets set the pivots' spacing.
const SHOULDER_X = 0.192;
// The levers lie in a plane this far ahead of the hips, in front of the forearms' hands; the upper
// arms hang this far from it.
const PLANE = 0.2;
const ARM_Z = -0.12;
// Pivot to the pad's middle along the arm, and out of the arm's line; the pad 250 long, 100 across.
// The grip hangs at the elbow's distance, so the forearm lies square to the upper arm.
const REACH = 0.3;
const GRIP_REACH = 0.255;
const OUT = 0.11;
const PAD = { length: 0.3, radius: 0.07 };
// The strut runs from the pivot to a knee this far out of the arm's line, flaring about 40°, and a beam
// runs back from the knee along the pad's outside. The wedge stands over the pivot, running fore and
// aft between the bar and the plate, a valley between the two, so it drops in front of the plate.
const KNEE_OUT = 0.195;
const LEVER_WIDTH = 0.06;
const WEDGE = { length: 0.2, width: 0.14, height: 0.15, over: 0.17, in: 0.075, tilt: rad(28) };
const GRIP = 0.075;
// The tower stands ahead of the lifter, its near end about on the centre line, as in the ref's photos.
const TOWER_X = -0.24;
const STACK_Z = 0.87;
// The base's rear foot, under the seat: from it to the tower is the ref's 1.07 m footprint.
const REAR = -0.1;
// The post, from the floor to behind the plate, and where along z it stands at height `y`.
const POST = { foot: V(0, 0.07, 0.06), top: V(0, PIVOT_Y + 0.03, PLANE + 0.06) };
const postZ = (y) => POST.foot.z + ((y - POST.foot.y) / (POST.top.y - POST.foot.y)) * (POST.top.z - POST.foot.z);
// The seat stands behind the post, its front edge just short of it.
const SEAT_Z = -0.08;
const FRAME_TOP = 1.4;
const SEAT = 0.55;
const BEAM_Y = 0.85;
const SITS = 0.09;
// How far along z the hips sit, the chest on its pad.
const HIP_Z = -0.07;
const SIDES = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

/** Where the arm on side `s` runs, and the way out of it, with the upper arm `v` out from hanging. */
const armDir = (s, v) => V(s * Math.sin(v), -Math.cos(v), 0);
const outOf = (s, v) => V(s * Math.cos(v), Math.sin(v), 0);
const pivotOf = (s) => V(s * SHOULDER_X, PIVOT_Y, PLANE);

/** The line side `s`'s grip holds as it pivots on its lever: leaning out at the top, whatever the arm's angle. */
const gripLine = (s) => V(s * 0.55, 0.83, 0).normalize();

/** Where side `s`'s lever runs with the upper arm `value` out from hanging. */
function legAt(s, value) {
  const pivot = pivotOf(s);
  const corner = pivot.clone().addScaledVector(outOf(s, value), OUT);
  const toGrip = armDir(s, value);
  // Where the lever turns back along the pad, the pad's middle out of the arm's line, and the grip on
  // that line, hung inboard of the pad from a bracket, ahead of it in the levers' plane.
  const root = corner.clone().addScaledVector(toGrip, REACH);
  const knee = pivot.clone().addScaledVector(toGrip, REACH + 0.03).addScaledVector(outOf(s, value), KNEE_OUT);
  const grip = pivot.clone().addScaledVector(toGrip, GRIP_REACH);
  const pad = root.clone().setZ(ARM_Z);
  const [gripFrom, gripTo] = [-GRIP, GRIP].map((d) => grip.clone().addScaledVector(gripLine(s), d));
  const [padBack, padFront] = [-PAD.length / 2, PAD.length / 2].map((d) => pad.clone().setZ(ARM_Z + d));
  return { pivot, corner, root, knee, grip, pad, gripFrom, gripTo, padBack, padFront };
}

export default recipe({
  name: 'lateral_raise_machine',
  bodies: Object.fromEntries(SIDES.map(({ k, s }) => [`arm_${k}`, revolute({ axis: V(0, 0, s), limits: LIMITS })])),
  contacts: SIDES.flatMap(({ k }) => [
    { body: `arm_${k}`, point: `grip_${k}`, landmark: 'hands', hand: k },
    { body: `arm_${k}`, point: `pad_${k}`, landmark: 'upperArms', hand: k },
  ]).concat([{ body: 'frame', point: 'chestPad', landmark: 'chest' }, { body: 'frame', point: 'seatTop', landmark: 'thighs' }]),
  stacks: [{ joint: 'arm_l', ratio: STACK_RATIO }],
  rep: { joint: 'arm_l', from: 'min' },
  fit: [{
    sets: 'seat',
    rule: 'the seat so the shoulder joint is level with the fixed pivots, in its range of 0.44 to 0.62 m',
    solve: ({ L }) => THREE.MathUtils.clamp(PIVOT_Y - L.trunkUp - SITS, 0.44, 0.62),
  }],
  linkage: ({ seat = SEAT }) => ({
    hip: [seat + SITS, HIP_Z],
    at: (value) => ({
      pivot: pivotOf(1),
      handle: (s) => legAt(s, value).grip,
      // A neutral grip, the index finger up its line.
      across: gripLine,
      elbow: (s) => armDir(s, value).add(V(0, 0, -0.3)).normalize(),
    }),
  }),
  build({ seat = SEAT } = {}, M) {
    const weights = weightStack(V(TOWER_X, 0, STACK_Z), M, { rodTop: FRAME_TOP - 0.03, toward: -1 });
    const wheel = V(TOWER_X, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z + WHEEL);
    // The tower's frame is built about the middle, then stood to the side.
    const tower = stackFrame(STACK_Z, FRAME_TOP, M);
    tower.forEach((m) => { m.position.x += TOWER_X; });
    const drop = wire(M);
    const chest = V(0, 0.96, 0.13);
    // The mounting plate's face, where the tubing lands: 14 mm proud of the pad's front.
    const FACE = chest.z + 0.04 + 0.014;
    const chestPad = pad(0.24, 0.3, 0.08, M.vinyl, chest, { soft: 0.02 });
    // The pivots' bar across the post's top, then the wedges' room, then the plate on a stub, and the drum.
    const housings = [];
    const fixed = [
      pad(0.2, 0.22, 0.014, M.frame, V(0, chest.y, FACE - 0.007), { soft: 0.004 }),
      tube(0.05, V(0, chest.y, FACE), V(0, chest.y, postZ(chest.y)), M.frame),
      pad(2 * SHOULDER_X + 0.26, 0.09, 0.02, M.frame, V(0, PIVOT_Y, PLANE + 0.04), { soft: 0.006 }),
      pad(0.36, 0.28, 0.02, M.frame, V(0, PIVOT_Y, PLANE + 0.27), { soft: 0.009 }),
      rod(0.16, V(0, PIVOT_Y, PLANE + 0.28), V(0, PIVOT_Y, PLANE + 0.33), M.frame),
      tube(0.06, V(0, PIVOT_Y - 0.1, postZ(PIVOT_Y - 0.1)), V(0, PIVOT_Y - 0.1, PLANE + 0.26), M.frame),
      ...SIDES.flatMap(({ s }) => {
        const joint = pivotJoint(M, pivotOf(s), V(0, 0, 1));
        housings.push(joint.housing);
        return joint.fixed;
      }),
    ];
    // The lever's tubing is one length bent twice, mitred, built at rest and turned about the pivot.
    const levers = SIDES.map(({ s }) => ({
      arm: ((leg) => {
        const arm = mitered([leg.pivot, leg.knee, leg.knee.clone().setZ(ARM_Z - PAD.length / 2 + 0.02)], LEVER_WIDTH, M.frame, { solid: false });
        const wedge = pad(WEDGE.width, WEDGE.height, WEDGE.length, M.vinyl, leg.pivot.clone().add(V(-s * WEDGE.in, WEDGE.over, 0)).setZ(PLANE + 0.04 + WEDGE.length / 2), { soft: 0.02, solid: false });
        wedge.rotation.z = s * WEDGE.tilt;
        arm.add(wedge);
        return arm;
      })(legAt(s, LIMITS[0])),
      bracket: part(rodGeometry(0.012), M.metal),
      pad: part(rodGeometry(PAD.radius), M.vinyl, { solid: true }),
      grip: part(rodGeometry(0.0175), M.vinyl, { held: true }),
    }));
    const body = (l, i) => [housings[i], l.arm, l.bracket, l.pad, l.grip];
    let posed = false;
    return {
      parts: [
        // The seat, cantilevered back from the post.
        pad(0.38, 0.075, 0.3, M.vinyl, V(0, seat - 0.0375, SEAT_Z), { soft: 0.024 }),
        pad(0.3, 0.012, 0.24, M.frame, V(0, seat - 0.081, SEAT_Z), { soft: 0.003 }),
        tube(0.05, V(0, seat - 0.11, SEAT_Z), V(0, seat - 0.11, postZ(seat - 0.11)), M.frame),
        tube(0.07, POST.foot, POST.top, M.frame),
        // The floor: a spine from the rear foot to the tower, across which the tower stands.
        ...floorRail(V(0, 0.035, REAR), V(0, 0.035, STACK_Z), M),
        ...floorRail(V(-0.1, 0.035, REAR), V(0.1, 0.035, REAR), M),
        ...floorRail(V(TOWER_X - 0.23, 0.035, STACK_Z), V(TOWER_X + 0.27, 0.035, STACK_Z), M),
        // The beam from the post to the tower.
        tube(0.06, V(0, BEAM_Y, postZ(BEAM_Y)), V(TOWER_X + UPRIGHTS, BEAM_Y, STACK_Z), M.frame),
        ...tower,
        // The header across the tower's top.
        pad(2 * UPRIGHTS + 0.07, 0.2, 0.05, M.frame, V(TOWER_X, FRAME_TOP - 0.1, STACK_Z - 0.01), { soft: 0.02 }),
        ...weights.parts, drop,
        chestPad,
        ...fixed, ...housings, ...levers.flatMap((l) => body(l, 0).slice(1)),
      ],
      bodies: Object.fromEntries(SIDES.map(({ k }, i) => [`arm_${k}`, body(levers[i], i)])),
      stacks: {
        arm_l: {
          lift(raised) {
            // A machine no one is on has its stack sat down.
            const l = raised - STACK_RATIO * LIMITS[0] - (posed ? 0 : HELD);
            weights.lift(l);
            span(drop, V(TOWER_X, wheel.y, STACK_Z), weights.top(l));
          },
        },
      },
      drive(P) {
        // Without a pose the arms rest.
        posed = Boolean(P.linkage);
        const value = P.linkage?.value ?? LIMITS[0];
        const state = { joints: {}, points: {} };
        SIDES.forEach(({ k, s }, i) => {
          const l = levers[i];
          const { pivot, knee, grip, pad, gripFrom, gripTo, padBack, padFront } = legAt(s, value);
          // The lever, built at rest, turned about its pivot.
          l.arm.quaternion.setFromAxisAngle(V(0, 0, s), value - LIMITS[0]);
          l.arm.position.copy(pivot).sub(pivot.clone().applyQuaternion(l.arm.quaternion));
          span(l.grip, gripFrom, gripTo);
          span(l.bracket, knee, gripTo);
          span(l.pad, padBack, padFront);
          state.joints[`arm_${k}`] = { value, origin: pivot };
          state.points[`grip_${k}`] = [grip];
          state.points[`pad_${k}`] = [pad];
        });
        state.points.chestPad = [chest.clone()];
        state.points.seatTop = [V(0, seat - 0.0375, SEAT_Z)];
        return state;
      },
    };
  },
});
