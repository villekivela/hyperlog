// A selectorized reclined crunch machine (refs/crunch_machine/ref.md): the user lies back with the hips
// on an inclined seat pad, the ankles hooked between two pairs of rollers on the front frame, the
// upper back on a back pad that is the work arm and the hands on two vertical handles beside the head
// pad. The arm turns about a cam axis beside the waist, curling the shoulders toward the knees, and a
// cable from the cam raises a weight stack beside the seat.
//
// The mannequin lies face up with its head toward -z, so the head end of the machine is toward -z and
// the feet end, with the rollers and the stack, toward +z. Everything below is laid out with `z` in
// metres toward the head (`at` flips it), the cam axis at the origin's z. The seat, the frame and the
// stack stand where the ref puts them whatever the pose. The arm is one body on a revolute joint at
// the axis, its value the degrees of curl from its rest, the back pad level.
//
// The seat pad is inclined as the photo shows it, and the pads and the handles stand where the demo's
// lifter lies on them (see the ref's Open gaps); the front frame and the stack are placed from the
// footprint, 1.58 m long; the stack stands beside the seat on the cam side, turned 90°.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, basePlate, endCap, hub, mitered, pad, padGeometry, part, pivotJoint, pulley, rod, rollerGeometry, span, tube,
  turnedPart, weightStack, wheelGeometry, wire,
} from '../parts.mjs';
import { V, X, forwardOf, rad } from '../rig.mjs';

/** A point `z` metres toward the head: the head is toward -z in the world. */
const at = (x, y, z) => V(x, y, -z);

// From rest, the back pad level, through 40° of curl (the ref's Motion).
const LIMITS = [0, rad(40)];
// At rest the back pad is tilted 12° up toward the head, carrying on the seat's incline as the demo's rest frame shows it,
// and curls to about 52° from level: the ref's 40° is the arm's travel from there.
const REST = rad(12);
// The cam axis, 780 mm up beside the waist, on the machine's left, the far side from the demo's camera: the arm turns on a hub 270 mm out from the
// middle, outside the lifter's hips, and reaches in under the back pad (the photo and the demo show the arm and
// the cam on one side only, the far one).
const SIDE = -0.27;
const PIVOT = at(0, 0.78, 0);
const HUB = at(SIDE, 0.78, 0);
// The seat: 600 mm long and 360 wide, inclined 15° rising toward the head, its high end's top 530 mm up
// 40 mm toward the feet from the axis.
const INCLINE = rad(15);
const SEAT = { length: 0.74, width: 0.36, thick: 0.1, highZ: 0.14, highY: 0.53 };
// Up out of the seat's face, and up along it toward the head.
const SEAT_UP = V(0, Math.cos(INCLINE), Math.sin(INCLINE));
const SEAT_ALONG = V(0, Math.sin(INCLINE), -Math.cos(INCLINE));
/** The seat's face `s` metres down it from its high end. */
const seatAt = (s) => at(0, SEAT.highY, SEAT.highZ).addScaledVector(SEAT_ALONG, -s);
/** How far down the seat from its high end the hips lie. */
const HIPS_ON = 0.17;
/**
 * How far the hip centre stands off the seat's face, lying on it: the skin's depth behind the spine's line,
 * less the 80 mm the pelvis stands nearer than the back does (measured on the mannequin, `crunch-machine.test.mjs`).
 */
const hipDepth = (L) => L.back - 0.08;
// The arm, built in its own frame about the axis at rest: z toward the head, y up, the back pad's face
// 120 mm under the axis (660 mm up), 430 mm long from 100 mm out, a head pad 50 mm above it at the far end.
const BACK = { from: 0.10, length: 0.38, width: 0.34, thick: 0.11, top: -0.26 };
const HEAD = { z: 0.57, length: 0.12, width: 0.26, thick: 0.09, raised: 0.06 };
// The arm's tube runs under the pad's board.
const ARM = { y: BACK.top - BACK.thick - 0.012 - 0.035 };
// The handles: a dark foam grip 200 mm long, 32 mm across, on a 70 mm silver stem from the pad's corner, either side of the head
// pad, 400 mm apart, leaning 18° from upright toward the head, as the photo and the demo show them. The fist closes on the
// grip's upper half.
const HANDLE = { x: 0.2, stem: 0.07, length: 0.2, radius: 0.016, z: 0.6, from: BACK.top - 0.03, lean: rad(18) };
/** Up the handle's axis, toward the head as it leans. */
const UP_HANDLE = at(0, Math.cos(HANDLE.lean), Math.sin(HANDLE.lean));
/** The point `d` metres up the axis of the handle on side `s`, in the arm's frame. */
const onHandle = (s, d) => at(s * HANDLE.x, HANDLE.from, HANDLE.z).addScaledVector(UP_HANDLE, d);
// The cam, a 250 mm disc outside the upright, on the far side from the camera in the ref's photo;
// the cable leaves its rim up and toward the feet.
const CAM = { x: -0.5, radius: 0.25, rim: at(-0.5, 0.17, -0.18) };
// The upright beside the hub carries the axle, between the arm and the cam.
const POST = { x: -0.43, top: 0.88 };
// The front frame stands 680 mm from the axis toward the feet, its rollers at 360 and 600 mm.
const FRONT = { z: -0.68, rollers: [0.36, 0.6], roller: 0.045, wide: 0.17, out: 0.14 };
// The stack stands beside the seat on the cam's side, in the cam's plane, turned 90° so its broad face looks out from the lifter:
// its column 560 mm toward the feet, clear of the cam and of the knees.
const STACK_Z = -0.56;
const STACK_X = CAM.x + 0.04;
// The cable runs in the cam's plane, 40 mm outside the column, clear of the guide rods, and drops onto the head plate's outer end.
const CABLE_X = CAM.x;
// Its frame stands a hand's breadth inboard of the column, so the cable, in the cam's plane, passes outside it to the pulley;
// the floor runner sits between, under the frame's feet and the rods'.
const PORTAL_X = STACK_X + 0.03;
const FLOOR_X = STACK_X + 0.03;
const FRAME_TOP = 1.365;
// The cam winds the cable 250 mm a radian.
const RATIO = CAM.radius;
// The bar lies up the handle in the hand, tipped a little toward the feet end, which keeps the wrist straight on a handle that
// leans toward the head from forearms that come down beside it; the pose turns the hold 180° about the forearm (`grip.rotate`).
const HOLD = V(0.15, 1, 0.38).normalize();
/** Where the ankles hook, between the pairs of rollers, just toward the hips from the front frame. */
const ANKLES = at(0, (FRONT.rollers[0] + FRONT.rollers[1]) / 2, FRONT.z + FRONT.roller * 2 + 0.01);

/** A point in the arm's frame, in the world with the arm turned `value` about the axis. */
const turned = (p, value) => p.clone().applyAxisAngle(X, value + REST).add(PIVOT);
/** The arm's back pad's face, at `value`: a point on it at `along` metres from the axis along the pad, and its up. */
const padAt = (along, value) => turned(at(0, BACK.top, along), value);
const padUp = (value) => V(0, 1, 0).applyAxisAngle(X, value + REST);

/** How far the upper back's skin stands off the spine's line, lying on the pad: the rig's depth and the 40 mm the shoulder blades add (measured, `crunch-machine.test.mjs`). */
const backDepth = (L) => L.back + 0.02;

/**
 * The side-on direction of the trunk, hips to head, for a body whose hips are at `hipC` lying on the
 * back pad turned to `value`: the shoulders sit on the pad's face, their back skin `L.back` behind
 * the spine, and slide along it as it turns.
 */
function trunkAt(hipC, value, L) {
  const face = padUp(value);
  const reach = L.trunkUp;
  // The signed distance of the back skin at the top of the trunk (an angle `phi` up from the +z axis in
  // the side-on plane) from the pad's face: bisected from lying flat to the pad's own angle.
  const dir = (phi) => V(0, Math.sin(phi), -Math.cos(phi));
  const off = (phi) => {
    const u = dir(phi);
    const skin = hipC.clone().addScaledVector(u, reach).addScaledVector(forwardOf(u), -backDepth(L));
    return skin.clone().sub(padAt(0, value)).dot(face);
  };
  let [lo, hi] = [rad(-10), rad(80)];
  for (let i = 0; i < 40; i++) {
    const mid = (lo + hi) / 2;
    if (off(mid) > 0) hi = mid; else lo = mid;
  }
  const u = dir((lo + hi) / 2);
  return { u, skin: hipC.clone().addScaledVector(u, reach).addScaledVector(forwardOf(u), -backDepth(L)) };
}

export default recipe({
  name: 'crunch_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the feet end, as the ref's photo is.
  standsAlone: { camera: { target: [-0.12, 0.7, 0.12], halfHeight: 1.1, azimuth: 58, elevation: 12 } },
  bodies: { arm: revolute({ axis: V(1, 0, 0), limits: LIMITS }) },
  // From lying back, the back pad level, to the shoulders curled up toward the knees.
  rep: { joint: 'arm', from: 'min' },
  // The hands on the handles, the upper back on the back pad, the hips on the seat's high end and the
  // ankles between the rollers. The ref has no adjustment to fit.
  contacts: [
    { body: 'arm', point: 'handles', landmark: 'hands' },
    { body: 'arm', point: 'backPad', landmark: 'back' },
    { body: 'frame', point: 'seat', landmark: 'hips' },
    { body: 'frame', point: 'rollers', landmark: 'feet' },
  ],
  stacks: [{ joint: 'arm', ratio: RATIO }],
  linkage: (_options, L) => {
    // The hip centre: down the seat from its high end, off its face by the skin's depth behind the spine's line.
    const hipC = seatAt(HIPS_ON).addScaledVector(SEAT_UP, hipDepth(L));
    return {
      hip: [hipC.y, hipC.z],
      at: (value) => {
        const { u } = trunkAt(hipC, value, L);
        const grip = (s) => turned(onHandle(s, HANDLE.stem + HANDLE.length * 0.65), value);
        return {
          handle: grip,
          // The hand lies up the handle, thumb end up as the arm turns.
          across: (side) => HOLD.clone().multiply(V(side, 1, 1)).applyAxisAngle(X, value + REST),
          spine: u,
          ankle: ANKLES.clone(),
        };
      },
    };
  },
  build(_options, M) {
    // The seat and its board, along the incline, the mesh's length toward the feet end.
    const alongSeat = (mesh, centre) => {
      mesh.position.copy(centre);
      mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, SEAT_UP, SEAT_ALONG.clone().negate()));
      return mesh;
    };
    const middle = seatAt(SEAT.length / 2);
    const seat = alongSeat(part(padGeometry(SEAT.width, SEAT.thick, SEAT.length, 0.025), M.vinyl, { solid: true }), middle.clone().addScaledVector(SEAT_UP, -SEAT.thick / 2));
    seat.userData.role = 'seat';
    const seatBoard = alongSeat(part(padGeometry(SEAT.width - 0.04, 0.012, SEAT.length - 0.04, 0.003), M.frame), middle.clone().addScaledVector(SEAT_UP, -SEAT.thick - 0.006));
    /** The seat's underside, the board's face, at z (toward the head). */
    const under = (z) => seatAt((SEAT.highZ - z) / -SEAT_ALONG.z).addScaledVector(SEAT_UP, -SEAT.thick - 0.012).y;

    const base = [
      // A beam down the middle from the stack to the axis's crossbar, a crossbar there and one at the front frame.
      tube(0.07, at(0, 0.035, FRONT.z), at(0, 0.035, 0), M.frame),
      // The crossbar at the axis turns, mitered, along the floor under the stack's two uprights.
      mitered([at(0.42, 0.035, 0), at(FLOOR_X, 0.035, 0), at(FLOOR_X, 0.035, STACK_Z - UPRIGHTS - 0.04)], 0.07, M.frame),
      tube(0.07, at(-0.3, 0.035, FRONT.z), at(0.3, 0.035, FRONT.z), M.frame),
      endCap(0.42, 0, M), endCap(-0.3, -FRONT.z, M), endCap(0.3, -FRONT.z, M),
      pad(0.07, 0.074, 0.016, M.vinyl, V(FLOOR_X, 0.037, -STACK_Z + UPRIGHTS + 0.04 + 0.008), { soft: 0.005, solid: false }),
    ];
    // The seat on a runner across the floor under its legs: each leg runs from the runner up to the seat's underside and
    // bends there, mitered, into a rail along the incline; a diagonal from the front crossbar's middle meets a cross rail.
    const legZ = -0.12;
    const railEnd = -0.5;
    const seatFrame = [
      tube(0.07, at(-0.2, 0.035, legZ), at(0.2, 0.035, legZ), M.frame),
      ...[-1, 1].map((s) => mitered([
        at(s * 0.15, 0.035, legZ), at(s * 0.15, under(legZ) - 0.03, legZ), at(s * 0.15, under(railEnd) - 0.03, railEnd),
      ], 0.06, M.frame)),
      tube(0.06, at(-0.15, under(railEnd) - 0.03, railEnd), at(0.15, under(railEnd) - 0.03, railEnd), M.frame),
      tube(0.08, at(0, 0.035, FRONT.z + 0.04), at(0, under(railEnd) - 0.03, railEnd), M.frame),
    ];
    // The two uprights at the axis, and the axle through them, fixed; the arm turns on it.
    const posts = [tube(0.08, at(POST.x, 0.035, 0), at(POST.x, POST.top, 0), M.frame)];
    const joint = pivotJoint(M, HUB, X);
    const axle = rod(0.014, HUB.clone().setX(SIDE + 0.05), HUB.clone().setX(CAM.x - 0.03), M.metal);
    // The front frame: a post that curves forward at its foot in two bends, like the photo's, its rollers' axles through it, a roller either side of each.
    const front = [
      mitered([at(0, 0.035, FRONT.z + 0.3), at(0, 0.22, FRONT.z + 0.1), at(0, 0.4, FRONT.z), at(0, FRONT.rollers[1] + 0.12, FRONT.z)], 0.09, M.frame),
      basePlate(0, -FRONT.z, M),
    ];
    const rollers = FRONT.rollers.flatMap((y) => [
      rod(0.012, at(-0.28, y, FRONT.z), at(0.28, y, FRONT.z), M.metal),
      ...[-1, 1].map((s) => {
        const r = turnedPart(rollerGeometry(FRONT.roller, FRONT.wide), M.vinyl, at(s * FRONT.out, y, FRONT.z), { solid: true });
        r.userData.role = 'roller';
        return r;
      }),
    ]);
    // The stack and its frame, a wheel at the top and the cable down to the stack's head plate.
    const wheel = at(STACK_X, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z + WHEEL);
    // The plates, rods and pin are built square to z at the origin, then the group is turned 90° onto the stack's column so
    // the plates' broad face looks out along x, the pin's knob out from the lifter.
    const column = new THREE.Group();
    const weights = weightStack(V(0, 0, 0), M, { rodTop: 1.33, toward: -1 });
    column.add(...weights.parts);
    column.rotation.y = Math.PI / 2;
    column.position.set(STACK_X, 0, -STACK_Z);
    column.updateMatrix();
    const drop = wire(M);
    const camCable = wire(M);
    camCable.userData.role = 'cable';
    // The stack's frame: an upright either side of the column joined across the top, one mitred length.
    const sz = -STACK_Z;
    const frameOfStack = [
      mitered([V(PORTAL_X, 0.035, sz - UPRIGHTS), V(PORTAL_X, FRAME_TOP, sz - UPRIGHTS), V(PORTAL_X, FRAME_TOP, sz + UPRIGHTS), V(PORTAL_X, 0.035, sz + UPRIGHTS)], 0.07, M.frame),
      // A top plate out from the frame's bar over the column: the rods end in it and the pulley hangs from it.
      pad(0.135, 0.035, 0.36, M.frame, V(STACK_X - 0.0025, FRAME_TOP - 0.0175, sz), { soft: 0.004 }),
    ];
    // The pulley is laid out about x = 0 (its housing's sides stand either side of it), then carried onto the stack's column.
    const topPulley = new THREE.Group();
    topPulley.add(...pulley(wheel.clone().setX(0), M));
    topPulley.position.x = CABLE_X;
    const stackParts = [...frameOfStack, topPulley, column, drop, camCable];

    // The arm, built in its frame about the axis, at rest.
    const arm = new THREE.Group();
    const padPart = (w, h, d, centre, material, soft, role) => {
      const m = part(padGeometry(w, h, d, soft), material, { solid: true });
      m.position.copy(centre).add(PIVOT);
      m.userData.role = role;
      return m;
    };
    const backMiddle = at(0, BACK.top - BACK.thick / 2, BACK.from + BACK.length / 2);
    const backPad = padPart(BACK.width, BACK.thick, BACK.length, backMiddle, M.vinyl, 0.025, 'backPad');
    const backBoard = padPart(BACK.width - 0.04, 0.012, BACK.length - 0.04, at(0, BACK.top - BACK.thick - 0.006, BACK.from + BACK.length / 2), M.frame, 0.003);
    backBoard.userData.role = undefined;
    const headPad = padPart(HEAD.width, HEAD.thick, HEAD.length, at(0, BACK.top + HEAD.raised - HEAD.thick / 2, HEAD.z), M.vinyl, 0.02, 'headPad');
    const handles = [-1, 1].map((s) => {
      const h = rod(HANDLE.radius, PIVOT.clone().add(onHandle(s, HANDLE.stem)), PIVOT.clone().add(onHandle(s, HANDLE.stem + HANDLE.length)), M.vinyl, { held: true });
      h.userData.role = 'handle';
      return h;
    });
    const stems = [-1, 1].flatMap((s) => [
      rod(0.012, PIVOT.clone().add(onHandle(s, 0)), PIVOT.clone().add(onHandle(s, HANDLE.stem)), M.metal),
      rod(0.019, PIVOT.clone().add(onHandle(s, HANDLE.stem + HANDLE.length)), PIVOT.clone().add(onHandle(s, HANDLE.stem + HANDLE.length + 0.012)), M.metal),
    ]);
    const cross = ARM.y + 0.03;
    arm.add(
      // The housing on the axle, a shaft out to the cam on the right, and the cam.
      joint.housing,
      part(wheelGeometry(CAM.radius, 0.03), M.metal, { solid: true }).translateX(CAM.x).translateY(PIVOT.y),
      hub(M, HUB.clone()),
      // The arm: down from the hub, in under the back pad, then along it to the head pad.
      // Down from the hub, along beside the seat's end (outside its width), then in under the pad's near end and on to the head pad.
      mitered([HUB.clone().add(at(0, -0.02, 0)), HUB.clone().add(at(0, ARM.y, 0.04)), HUB.clone().add(at(0, ARM.y, 0.2))], 0.07, M.metal),
      mitered([HUB.clone().add(at(0, ARM.y, 0.2)), PIVOT.clone().add(at(0, ARM.y, 0.27)), PIVOT.clone().add(at(0, ARM.y, HEAD.z + HEAD.length / 2 - 0.02))], 0.07, M.metal),
      backPad, backBoard, headPad, ...handles, ...stems,
      // The handles' bracket, a U under the head pad from one handle's foot to the other's.
      mitered([
        at(-HANDLE.x, BACK.top - 0.03, HANDLE.z), at(-HANDLE.x, cross, HANDLE.z - 0.02), at(HANDLE.x, cross, HANDLE.z - 0.02), at(HANDLE.x, BACK.top - 0.03, HANDLE.z),
      ].map((p) => p.add(PIVOT)), 0.05, M.frame),
    );
    // Everything on the arm was laid out at its rest in the world: move it into the arm's frame, about the axis.
    arm.children.forEach((child) => child.position.sub(PIVOT));
    arm.position.copy(PIVOT);
    const frameParts = [seat, seatBoard, ...seatFrame, ...base, ...posts, axle, ...joint.fixed, ...front, ...rollers, ...stackParts];

    const layCable = () => {
      arm.updateMatrix();
      // The cable meets the pulley's upper head-side tangent, where it wraps over the wheel.
      const rim = CAM.rim.clone().applyMatrix4(arm.matrix);
      const from = Math.atan2(rim.y - wheel.y, rim.z - wheel.z);
      const off = Math.acos(WHEEL / Math.hypot(rim.y - wheel.y, rim.z - wheel.z));
      const tangent = [from + off, from - off].map((a) => V(CABLE_X, wheel.y + WHEEL * Math.sin(a), wheel.z + WHEEL * Math.cos(a))).sort((a, b) => b.y - a.y)[0];
      span(camCable, tangent, rim);
    };
    layCable();

    return {
      parts: [...frameParts, arm],
      bodies: { arm: [arm] },
      stacks: {
        arm: {
          lift(l) {
            weights.lift(l);
            span(drop, V(CABLE_X, wheel.y, -STACK_Z), weights.top(l).applyMatrix4(column.matrix).setX(CABLE_X));
          },
        },
      },
      follow: layCable,
      drive(P) {
        const value = P.linkage?.value ?? LIMITS[0];
        arm.position.copy(PIVOT);
        arm.rotation.set(value + REST, 0, 0);
        layCable();
        return {
          joints: { arm: { value, origin: PIVOT.clone() } },
          points: {
            handles: P.linkage ? [-1, 1].map((s) => P.linkage.handle(s)) : [-1, 1].map((s) => turned(onHandle(s, HANDLE.stem + HANDLE.length * 0.65), value)),
            backPad: [padAt(BACK.from + BACK.length / 2, value)],
            seat: [seatAt(HIPS_ON)],
            rollers: [ANKLES.clone()],
          },
        };
      },
    };
  },
});
