// A selectorized reclined crunch machine (refs/crunch_machine/ref.md): the user lies back with the hips
// on an inclined seat pad, the ankles hooked between two pairs of rollers on the front frame, the
// upper back on a back pad that is the work arm and the hands on two vertical handles beside the head
// pad. The arm turns about a cam axis beside the waist, curling the shoulders toward the knees, and a
// cable from the cam raises a weight stack at the feet end.
//
// The head is toward +z and the feet toward -z, the cam axis the origin's z. The seat, the frame and
// the stack stand where the ref puts them whatever the pose; the arm is one body on a revolute joint
// at the axis, its value the degrees of curl from its rest, the back pad level.
//
// The ref's seat pad is as long as the photo shows it, and the front frame and the stack are placed
// from the footprint (1.58 m by 890 mm), as the ref's numbers are rough: see its Open gaps.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, basePlate, endCap, hub, padGeometry, part, pulley, rod, rollerGeometry, span, stackFrame, tube,
  turnedPart, weightStack, wheelGeometry, wire,
} from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

// From rest, the back pad level, through 40° of curl (the ref's Motion).
const LIMITS = [0, rad(40)];
// The cam axis, 780 mm up beside the waist.
const PIVOT = V(0, 0.78, 0);
// The seat: 600 mm long and 360 wide, inclined 15° rising toward the head, its high end's top 530 mm up
// 40 mm behind the axis's foot.
const INCLINE = rad(15);
const SEAT = { length: 0.6, width: 0.36, thick: 0.08, highZ: -0.04, highY: 0.53 };
// Up out of the seat's face, and up along it toward the high end.
const SEAT_UP = V(0, Math.cos(INCLINE), -Math.sin(INCLINE));
const SEAT_ALONG = V(0, Math.sin(INCLINE), Math.cos(INCLINE));
/** The seat's face `s` metres down it from its high end. */
const seatAt = (s) => V(0, SEAT.highY, SEAT.highZ).addScaledVector(SEAT_ALONG, -s);
// The arm, built in its own frame about the axis at rest: z toward the head, y up, the back pad's face
// level 120 mm under the axis (660 mm up), 430 mm long from 100 mm out, a head pad 50 mm above it at the far end.
const BACK = { from: 0.1, length: 0.43, width: 0.34, thick: 0.08, top: -0.12 };
const HEAD = { z: 0.61, length: 0.12, width: 0.26, thick: 0.07, raised: 0.05 };
// The arm's tube runs under the pad's board.
const ARM = { y: BACK.top - BACK.thick - 0.012 - 0.035 };
// The handles stand 200 mm tall either side of the head pad, 400 mm apart.
const HANDLE = { x: 0.2, length: 0.2, radius: 0.016, z: 0.6 };
// The cam, a 190 mm disc outside the right upright, on the far side from the camera in the ref's photo; the cable leaves its rim up and toward the feet.
const CAM = { x: 0.4, radius: 0.19, rim: V(0.4, 0.13, -0.14) };
// The uprights either side of the arm carry the axle.
const POSTS = { x: 0.32, top: 0.88 };
// The front frame stands 680 mm from the axis toward the feet, its rollers at 360 and 600 mm.
const FRONT = { z: -0.68, rollers: [0.36, 0.6], roller: 0.045, wide: 0.17, out: 0.14 };
const STACK_Z = -0.86;
const FRAME_TOP = 1.365;
// The cam winds the cable 190 mm a radian.
const RATIO = CAM.radius;

const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

export default recipe({
  name: 'crunch_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's photo is.
  standsAlone: { camera: { target: [0, 0.75, -0.1], halfHeight: 1.1, azimuth: -40, elevation: 14 } },
  bodies: { arm: revolute({ axis: V(-1, 0, 0), limits: LIMITS }) },
  // The hands on the handles, the upper back on the back pad, the hips on the seat's high end and the
  // ankles between the rollers. The ref has no adjustment to fit.
  contacts: [
    { body: 'arm', point: 'handles', landmark: 'hands' },
    { body: 'arm', point: 'backPad', landmark: 'back' },
    { body: 'frame', point: 'seat', landmark: 'thighs' },
    { body: 'frame', point: 'rollers', landmark: 'feet' },
  ],
  stacks: [{ joint: 'arm', ratio: RATIO }],
  build(_options, M) {
    // The seat and its board, along the incline.
    const alongSeat = (mesh, at) => {
      mesh.position.copy(at);
      mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, SEAT_UP, SEAT_ALONG));
      return mesh;
    };
    const middle = seatAt(SEAT.length / 2);
    const seat = role(alongSeat(part(padGeometry(SEAT.width, SEAT.thick, SEAT.length, 0.025), M.vinyl, { solid: true }), middle.clone().addScaledVector(SEAT_UP, -SEAT.thick / 2)), 'seat');
    const seatBoard = alongSeat(part(padGeometry(SEAT.width - 0.04, 0.012, SEAT.length - 0.04, 0.003), M.frame), middle.clone().addScaledVector(SEAT_UP, -SEAT.thick - 0.006));
    /** The seat's underside, the board's face, at z. */
    const under = (z) => seatAt((SEAT.highZ - z) / SEAT_ALONG.z).addScaledVector(SEAT_UP, -SEAT.thick - 0.012).y;

    const base = [
      // A beam down the middle from the stack to the axis's crossbar, a crossbar there and one at the front frame.
      tube(0.07, V(0, 0.035, STACK_Z), V(0, 0.035, 0), M.frame),
      tube(0.07, V(-0.42, 0.035, 0), V(0.42, 0.035, 0), M.frame),
      tube(0.07, V(-0.3, 0.035, FRONT.z), V(0.3, 0.035, FRONT.z), M.frame),
      tube(0.07, V(-UPRIGHTS - 0.15, 0.035, STACK_Z), V(UPRIGHTS + 0.15, 0.035, STACK_Z), M.frame),
      ...[-1, 1].flatMap((s) => [endCap(s * 0.42, 0, M), endCap(s * 0.3, FRONT.z, M), endCap(s * (UPRIGHTS + 0.15), STACK_Z, M)]),
    ];
    // The seat on two rear posts and a diagonal from the front crossbar's middle up under it.
    const seatFrame = [
      ...[-1, 1].flatMap((s) => [tube(0.06, V(s * 0.15, 0.07, -0.12), V(s * 0.15, under(-0.12) + 0.01, -0.12), M.frame), basePlate(s * 0.15, -0.12, M)]),
      tube(0.08, V(0, 0.07, FRONT.z + 0.04), V(0, under(-0.4) + 0.01, -0.4), M.frame),
    ];
    // The two uprights at the axis, and the axle through them.
    const posts = [-1, 1].flatMap((s) => [tube(0.08, V(s * POSTS.x, 0.07, 0), V(s * POSTS.x, POSTS.top, 0), M.frame), basePlate(s * POSTS.x, 0, M)]);
    // The front frame: a post to its rollers' axles, each carrying a roller either side of it.
    const front = [tube(0.08, V(0, 0.07, FRONT.z), V(0, FRONT.rollers[1] + 0.12, FRONT.z), M.frame), basePlate(0, FRONT.z, M)];
    const rollers = FRONT.rollers.flatMap((y) => [
      rod(0.012, V(-0.28, y, FRONT.z), V(0.28, y, FRONT.z), M.metal),
      ...[-1, 1].map((s) => role(turnedPart(rollerGeometry(FRONT.roller, FRONT.wide), M.vinyl, V(s * FRONT.out, y, FRONT.z), { solid: true }), 'roller')),
    ]);
    // The stack and its frame, a wheel at the top and the cable down to the stack's head plate.
    const wheel = V(0, FRAME_TOP - 0.035 - WHEEL - 0.02, STACK_Z + WHEEL);
    const weights = weightStack(V(0, 0, STACK_Z), M, { rodTop: 1.3, toward: 1 });
    const drop = wire(M);
    const camCable = role(wire(M), 'cable');
    const stackParts = [...stackFrame(STACK_Z, FRAME_TOP, M), ...pulley(wheel, M), basePlate(0, STACK_Z, M), ...weights.parts, drop, camCable];

    // The arm, built in its frame about the axis, at rest.
    const arm = new THREE.Group();
    const padAt = (w, h, d, at, material, soft) => { const m = part(padGeometry(w, h, d, soft), material, { solid: true }); m.position.copy(at); return m; };
    const backMiddle = V(0, BACK.top - BACK.thick / 2, BACK.from + BACK.length / 2);
    const backPad = role(padAt(BACK.width, BACK.thick, BACK.length, backMiddle, M.vinyl, 0.025), 'backPad');
    const backBoard = padAt(BACK.width - 0.04, 0.012, BACK.length - 0.04, V(0, BACK.top - BACK.thick - 0.006, backMiddle.z), M.frame, 0.003);
    const headPad = role(padAt(HEAD.width, HEAD.thick, HEAD.length, V(0, BACK.top + HEAD.raised - HEAD.thick / 2, HEAD.z), M.vinyl, 0.02), 'headPad');
    const handles = [-1, 1].map((s) => role(rod(HANDLE.radius, V(s * HANDLE.x, BACK.top - 0.05, HANDLE.z), V(s * HANDLE.x, BACK.top - 0.05 + HANDLE.length, HANDLE.z), M.vinyl, { held: true }), 'handle'));
    const crossTube = tube(0.05, V(-HANDLE.x, ARM.y + 0.03, HANDLE.z - 0.02), V(HANDLE.x, ARM.y + 0.03, HANDLE.z - 0.02), M.frame);
    arm.add(
      // The axle, through the uprights to the cam on the right, and its hub.
      rod(0.015, V(-POSTS.x - 0.05, 0, 0), V(CAM.x + 0.02, 0, 0), M.metal),
      hub(M, V(0, 0, 0)),
      part(wheelGeometry(CAM.radius, 0.03), M.metal, { solid: true }).translateX(CAM.x),
      // A swan neck down from the hub to the arm under the back pad, then along it to the head pad.
      tube(0.07, V(0, -0.02, 0.0), V(0, ARM.y, 0.06), M.metal),
      tube(0.07, V(0, ARM.y, 0.06), V(0, ARM.y, HEAD.z + HEAD.length / 2 - 0.02), M.metal),
      backPad, backBoard, headPad, crossTube, ...handles,
      ...handles.map((h, i) => tube(0.045, V((i ? 1 : -1) * HANDLE.x, ARM.y + 0.03, HANDLE.z - 0.02), V((i ? 1 : -1) * HANDLE.x, BACK.top - 0.03, HANDLE.z), M.frame)),
    );
    const frameParts = [seat, seatBoard, ...seatFrame, ...base, ...posts, ...front, ...rollers, ...stackParts];

    /** A point in the arm's frame, in the world with the arm as it stands. */
    const onArm = (p) => p.clone().applyMatrix4(arm.matrix);
    const layCable = () => {
      arm.updateMatrix();
      span(camCable, V(0, wheel.y + WHEEL, wheel.z), onArm(CAM.rim));
    };

    return {
      parts: [...frameParts, arm],
      bodies: { arm: [arm] },
      stacks: {
        arm: {
          lift(l) {
            weights.lift(l);
            span(drop, V(0, wheel.y, STACK_Z), weights.top(l));
          },
        },
      },
      follow: layCable,
      drive(P) {
        const value = P.linkage?.value ?? LIMITS[0];
        arm.position.copy(PIVOT);
        arm.rotation.set(-value, 0, 0);
        layCable();
        return {
          joints: { arm: { value, origin: PIVOT.clone() } },
          points: {
            handles: [-1, 1].map((s) => onArm(V(s * HANDLE.x, BACK.top - 0.05 + HANDLE.length / 2, HANDLE.z))),
            backPad: [onArm(V(0, BACK.top, backMiddle.z))],
            seat: [seatAt(0.08)],
            rollers: [V(0, (FRONT.rollers[0] + FRONT.rollers[1]) / 2, FRONT.z + FRONT.roller * 2 + 0.01)],
          },
        };
      },
    };
  },
});
