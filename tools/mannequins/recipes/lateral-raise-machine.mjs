// A selectorised seated lateral raise (refs/lateral_raise_machine/ref.md): the lifter sits facing
// the machine with the chest on a centre pad. Two arm levers turn about front-to-back axes through
// the shoulders, each a strut flaring from its pivot to a knee, and a beam back along a pad on the
// outside of the upper arm, with a grip hung inboard of the pad. One post leans forward from the
// floor between the legs and carries the seat, the chest pad and the bar the pivots turn on. The
// weight stack stands in a tower at the far end of the base, past the arms' reach, its broad face
// across the machine, to the right.
//
// The pivots are fixed on the frame, 1.15 m up and on the mannequin's shoulders, and the seat moves to
// them. Each lever is a body on a revolute joint, its value the upper arm's angle out from hanging,
// from 10° to horizontal. The arms rise together in both of the ref's videos, so the stack hangs on
// the left arm's joint. The grips pivot on the levers, holding one line through the rep. The pulley
// block rises with the stack. The standing lateral raise, which the ref
// gives its own geometry, is listed with this kit's exercises but needs a kit of its own.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { WHEEL, bent, floorRail, mitered, pad, part, pivotJoint, rod, rodGeometry, span, tube, weightStack, wheelAt, wire } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

const LIMITS = [rad(10), rad(90)];
// How far the stack rises, in metres a radian of the left arm.
const STACK_RATIO = 0.17;
const PIVOT_Y = 1.15;
// The mannequin's shoulders, which the ref lets set the pivots' spacing.
const SHOULDER_X = 0.192;
// The levers lie in the plane z = PLANE, in front of the hands, and the arm pads' middles at z = ARM_Z,
// by the upper arms.
const PLANE = 0.2;
const ARM_Z = -0.06;
// Pivot to the pad's middle along the arm, and out of the arm's line; the pad 360 long, 150 across.
// The grip hangs at the elbow's distance, so the forearm lies square to the upper arm.
const REACH = 0.3;
const GRIP_REACH = 0.255;
const MOUNT = 0.13;
const OUT = 0.115;
const PAD = { length: 0.36, radius: 0.075 };
// The strut runs from the pivot to a knee this far out of the arm's line, flaring about 40°, and a beam
// runs back from the knee along the pad's outside. The wedge stands over the pivot, running fore and
// aft between the bar and the plate, a valley between the two, so it drops in front of the plate.
const KNEE_OUT = 0.195;
// The strut rakes toward the tower on its way down to the knee.
const KNEE_Z = PLANE + 0.17;
const LEVER_WIDTH = 0.06;
const WEDGE_Z = PLANE + 0.04;
const WEDGE = { length: 0.33, width: 0.1, height: 0.12, over: 0.125, in: 0.1, tilt: rad(28) };
const GRIP = 0.075;
// The tower stands ahead of the lifter, its near end about on the centre line, as in the ref's photos.
const TOWER_X = -0.225;
// Each upright's centre from the tower's, and the corner of the arch that joins them at the top.
const TOWER_HALF = 0.215;
const ARCH_FROM = 1.15;
const STACK_Z = 0.87;
// The base's rear foot, under the seat: from it to the tower is the ref's 1.07 m footprint.
const REAR = -0.1;
// The post, from the floor to behind the bar, and where along z it stands at height `y`.
const POST = { foot: V(0, 0.07, 0.06), top: V(0, PIVOT_Y + 0.03, PLANE + 0.06) };
const postZ = (y) => POST.foot.z + ((y - POST.foot.y) / (POST.top.y - POST.foot.y)) * (POST.top.z - POST.foot.z);
// The seat stands behind the post, its front edge just short of it.
const SEAT_Z = -0.08;
const FRAME_TOP = 1.4;
const SEAT = 0.55;
const BEAM_Y = 0.85;
// The pulley block out in the gap between the post and the tower: its two wheels' heights, where it
// stands, its plate, and its share of the stack's rise.
const BLOCK = { y: [0.33, 0.48], z: 0.46, width: 0.18, height: 0.34, rise: 0.5 };
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
  const knee = pivot.clone().addScaledVector(toGrip, REACH + 0.03).addScaledVector(outOf(s, value), KNEE_OUT).setZ(KNEE_Z);
  const grip = pivot.clone().addScaledVector(toGrip, GRIP_REACH);
  // The bracket's end, on the lever and clear of the thumb, and a stem down to the grip, which pivots
  // on it and so holds its own line.
  const mount = pivot.clone().addScaledVector(toGrip, GRIP_REACH - MOUNT);
  const pad = root.clone().setZ(ARM_Z);
  const [gripFrom, gripTo] = [-GRIP, GRIP].map((d) => grip.clone().addScaledVector(gripLine(s), d));
  const [padBack, padFront] = [-PAD.length / 2, PAD.length / 2].map((d) => pad.clone().setZ(ARM_Z + d));
  return { pivot, knee, grip, mount, pad, gripFrom, gripTo, padBack, padFront };
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
    // The cable's top, under the tower's top bar.
    const cableTop = V(TOWER_X, FRAME_TOP - 0.1, STACK_Z);
    // The tower: the inner upright straight up, and the outer one swept round into the top in one arch.
    const [outer, inner] = [TOWER_X - TOWER_HALF, TOWER_X + TOWER_HALF];
    const archY = FRAME_TOP - 0.035;
    const arch = bent('lateral_raise_arch', [
      V(outer, ARCH_FROM, STACK_Z), V(outer, ARCH_FROM + 0.08, STACK_Z), V(outer + 0.03, archY - 0.09, STACK_Z), V(outer + 0.1, archY - 0.02, STACK_Z),
      V(outer + 0.2, archY, STACK_Z), V(inner, archY, STACK_Z),
    ], 0.035, M.frame);
    arch.name = 'lateral_raise_arch';
    const tower = [
      tube(0.07, V(inner, 0.07, STACK_Z), V(inner, archY, STACK_Z), M.frame),
      tube(0.07, V(outer, 0.07, STACK_Z), V(outer, ARCH_FROM, STACK_Z), M.frame),
      arch,
    ];
    const drop = wire(M);
    // The block, and its cables straight up to the levers' stub and straight down to the spine.
    const block = new THREE.Group();
    block.add(
      // A bowtie: a broad plate at each wheel, and a narrow waist between them.
      ...BLOCK.y.map((y, i) => pad(BLOCK.width, BLOCK.height / 2 - 0.03, 0.014, M.pulley, V(0, y + (i ? 0.02 : -0.02), BLOCK.z), { soft: 0.005, solid: false })),
      pad(0.07, BLOCK.height, 0.014, M.pulley, V(0, (BLOCK.y[0] + BLOCK.y[1]) / 2, BLOCK.z), { soft: 0.005, solid: false }),
      ...BLOCK.y.map((y) => wheelAt(M, V(0, y, BLOCK.z + 0.02), { aboutZ: true })),
    );
    const [toLevers, toBase] = [wire(M), wire(M)];
    const blockAt = (l) => {
      block.position.y = BLOCK.rise * l;
      span(toLevers, V(0, BLOCK.y[1] + WHEEL + block.position.y, BLOCK.z), V(0, PIVOT_Y - 0.1, BLOCK.z));
      span(toBase, V(0, BLOCK.y[0] - WHEEL + block.position.y, BLOCK.z), V(0, 0.07, BLOCK.z));
    };
    const chest = V(0, 0.96, 0.13);
    // The mounting plate's face, where the tubing lands: 14 mm proud of the pad's front.
    const FACE = chest.z + 0.04 + 0.014;
    const chestPad = pad(0.24, 0.3, 0.08, M.vinyl, chest, { soft: 0.02 });
    // The pivots' bar across the post's top, then the wedges' room, then the plate on a stub, and the drum.
    const housings = [];
    const fixed = [
      pad(0.2, 0.22, 0.014, M.frame, V(0, chest.y, FACE - 0.007), { soft: 0.004 }),
      tube(0.05, V(0, chest.y, FACE), V(0, chest.y, postZ(chest.y)), M.frame),
      pad(2 * SHOULDER_X + 0.12, 0.09, 0.02, M.frame, V(0, PIVOT_Y, PLANE + 0.04), { soft: 0.006 }),
      pad(0.36, 0.22, 0.02, M.frame, V(0, PIVOT_Y, PLANE + 0.39), { soft: 0.009 }),
      rod(0.16, V(0, PIVOT_Y, PLANE + 0.4), V(0, PIVOT_Y, PLANE + 0.45), M.frame),
      tube(0.06, V(0, PIVOT_Y - 0.1, postZ(PIVOT_Y - 0.1)), V(0, PIVOT_Y - 0.1, PLANE + 0.38), M.frame),
      ...SIDES.flatMap(({ s }) => {
        const joint = pivotJoint(M, pivotOf(s), V(0, 0, 1));
        housings.push(joint.housing);
        return joint.fixed;
      }),
    ];
    // The lever's tubing is one length bent twice, mitred, built at rest and turned about the pivot.
    const levers = SIDES.map(({ s }) => ({
      arm: ((leg) => {
        const beamEnd = leg.knee.clone().setZ(ARM_Z - PAD.length / 2 + 0.02);
        const arm = mitered([leg.pivot, leg.knee, beamEnd], LEVER_WIDTH, M.frame, { solid: false });
        const wedgeRoot = leg.pivot.clone().add(V(-s * WEDGE.in, WEDGE.over, 0)).setZ(WEDGE_Z);
        const wedge = pad(WEDGE.width, WEDGE.height, WEDGE.length, M.vinyl, wedgeRoot.clone().setZ(WEDGE_Z + WEDGE.length / 2), { soft: 0.02 });
        wedge.name = `lateral_raise_wedge_${s < 0 ? 'r' : 'l'}`;
        wedge.rotation.z = s * WEDGE.tilt;
        // The strut carries on past the pivot into the wedge.
        arm.add(wedge, tube(0.05, leg.pivot.clone().setZ(WEDGE_Z), wedgeRoot, M.frame));
        // A box round the whole bent tube would stand in the body's way, so a tube inside each
        // length is what the body meets; the clip leaves them out.
        arm.add(...[[leg.pivot, leg.knee], [leg.knee, beamEnd]].map(([a, b]) => {
          const core = tube(LEVER_WIDTH - 0.01, a, b, M.frame);
          core.userData.proxy = true;
          return core;
        }));
        return arm;
      })(legAt(s, LIMITS[0])),
      bracket: part(rodGeometry(0.012), M.frame, { solid: true }),
      stem: part(rodGeometry(0.008), M.frame),
      pad: part(rodGeometry(PAD.radius), M.vinyl, { solid: true }),
      grip: part(rodGeometry(0.0175), M.vinyl, { held: true }),
    }));
    levers.forEach((l, i) => { l.bracket.name = `lateral_raise_bracket_${SIDES[i].k}`; });
    const body = (l, i) => [housings[i], l.arm, l.bracket, l.stem, l.pad, l.grip];
    return {
      parts: [
        // The seat, cantilevered back from the post.
        pad(0.32, 0.075, 0.3, M.vinyl, V(0, seat - 0.0375, SEAT_Z), { soft: 0.024 }),
        pad(0.26, 0.012, 0.24, M.frame, V(0, seat - 0.081, SEAT_Z), { soft: 0.003 }),
        tube(0.05, V(0, seat - 0.11, SEAT_Z), V(0, seat - 0.11, postZ(seat - 0.11)), M.frame),
        tube(0.07, POST.foot, POST.top, M.frame),
        // The floor: a spine from the rear foot to the tower, across which the tower stands.
        ...floorRail(V(0, 0.035, REAR), V(0, 0.035, STACK_Z), M),
        ...floorRail(V(-0.1, 0.035, REAR), V(0.1, 0.035, REAR), M),
        ...floorRail(V(outer - 0.03, 0.035, STACK_Z), V(inner + 0.03, 0.035, STACK_Z), M),
        block, toLevers, toBase,
        // The beam from the post to the tower.
        tube(0.06, V(0, BEAM_Y, postZ(BEAM_Y)), V(inner, BEAM_Y, STACK_Z), M.frame),
        ...tower,
        // The header across the tower's top.
        pad(inner - outer - 0.12, 0.19, 0.05, M.frame, V((inner + outer + 0.12) / 2, FRAME_TOP - 0.115, STACK_Z - 0.01), { soft: 0.02 }),
        ...weights.parts, drop,
        chestPad,
        ...fixed, ...housings, ...levers.flatMap((l) => body(l, 0).slice(1)),
      ],
      bodies: Object.fromEntries(SIDES.map(({ k }, i) => [`arm_${k}`, body(levers[i], i)])),
      stacks: {
        arm_l: {
          lift(raised) {
            // The arms' rest is 10° out of hanging, not 0.
            const l = raised - STACK_RATIO * LIMITS[0];
            weights.lift(l);
            span(drop, cableTop, weights.top(l));
            blockAt(l);
          },
        },
      },
      drive(P) {
        // Without a pose the arms rest.
        const value = P.linkage?.value ?? LIMITS[0];
        const state = { joints: {}, points: {} };
        SIDES.forEach(({ k, s }, i) => {
          const l = levers[i];
          const { pivot, knee, grip, mount, pad, gripFrom, gripTo, padBack, padFront } = legAt(s, value);
          // The lever, built at rest, turned about its pivot.
          l.arm.quaternion.setFromAxisAngle(V(0, 0, s), value - LIMITS[0]);
          l.arm.position.copy(pivot).sub(pivot.clone().applyQuaternion(l.arm.quaternion));
          span(l.grip, gripFrom, gripTo);
          span(l.bracket, knee.clone().setZ(PLANE), mount);
          span(l.stem, mount, grip);
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
