// A seated dip machine (refs/dip_machine/ref.md, read again from its photos, build/dipref/notes.md):
// the user sits upright against a tall back pad and pushes two handles down to the hips. The handle
// arms hang on a transverse pivot at the top of a rear upright leaning back, each one tube from the
// pivot forward and down to an elbow, then a grip bar up and forward from it. The seat, back pad and
// footplates are one carriage, rocking back about a low pivot under the seat as the handles go down,
// so the finish is reclined with the feet up. The weight stack stands beside the seat on the user's
// right, in a shroud open toward the seat, its plates facing it.
//
// Two bodies, each on a revolute joint: `arms` (radians the handles have gone down from the start,
// 0 to 40°) and `carriage` (radians it has rocked back, 0 to 16°). A pose drives the arms and the
// carriage follows at COUPLE of their angle, since the machine's linkage ties them; scrubbing either
// in the studio moves only it. The frame stands where the ref puts it whatever the pose: the parts are
// built at the start and laid at their joints' values by a rigid move each frame. `P.linkage.value`
// is the arms' value, none with no pose.
import * as THREE from 'three';
import { recipe, revolute, moveOf } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, along, floorRail, mitered, pad, part, pivotJoint, pulley, rod, rodGeometry, span, stackFrame, tube, weightStack, wire,
} from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

// The arms' pivot, [y, z]: 880 above the floor, 740 behind the seat's front edge.
const PIVOT = [0.88, -0.54];
// The grip's middle is GRIP.reach from the pivot, GRIP.angle degrees above level at the start. The
// grip bar runs BAR.angle degrees up from level at the start (the ref's 40°, level at the finish, as the
// arms turn 40°), 0.1 m of it forward of its collar to the grip's middle; each arm's tube runs from the
// pivot to the collar, the rest of that distance. A bar at that pitch is square enough to the forearm
// through the rep for a straight wrist, and keeps the elbows back, not flared.
const GRIP = { reach: 0.5, angle: -10 };
const BAR = { length: 0.3, angle: 40, grip: 0.1 };
// The grips are 250 mm either side of the middle; the arm tubes run outside them, 330 mm, and jog in at the collar.
const ARMS_X = 0.22;
const TUBE_X = ARMS_X + 0.08;
const ARMS = [0, rad(40)];
// The carriage rocks back about a low pivot under the seat, [y, z], by up to 12°, when the arms go down 40°.
const ROCK = [0.2, -0.3];
const CARRIAGE = [0, rad(12)];
const COUPLE = CARRIAGE[1] / ARMS[1];
// The seat's top 480 above the floor, its cushion 360 wide and 400 deep round the middle; the back pad's face
// starts at the seat's rear edge, leaning back 4° at the start (and 20° at the finish).
const SEAT = { top: 0.48, z: -0.01, size: 0.4, width: 0.36 };
const FACE = { from: [0.5, -0.22], lean: rad(4), length: 0.72, width: 0.26, thick: 0.08 };
// The footplates 150 either side of the middle, 120 up at the start, tilted 55° from level, facing the shins.
const FEET = { y: 0.18, z: 0.62, apart: 0.17, tilt: rad(55), length: 0.22, width: 0.15 };
// The weight stack's tower, centred beside the seat on the user's right (-x), its shroud 0.42 across
// and 0.48 front to back, 1.39 tall, the plates facing the seat.
const STACK = { x: -0.5, z: 0.22, top: 1.3, cap: 1.36, across: 0.34, deep: 0.48 };
const FLOOR = { rear: -0.57, front: 0.74, half: 0.6 };
/** How far the stack's cable draws it up for a radian of the arms. */
const RATIO = 0.3;
/** How far the hips sit above the seat's top. */
const SITS = 0.09;

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** Marks a part only the checks need: the bake leaves it out of the clip. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };

/**
 * A bent tube through `points`, mitred, and straight tubes just inside it for the checks: a mitred
 * tube is built in the world, so its bounding box would stand in for it, and over a diagonal that fills
 * the space the body is in.
 */
const bent = (points, w, material, name) => [
  role(mitered(points, w, material, { solid: false }), name),
  ...points.slice(1).map((p, i) => proxy(tube(w * 0.7, points[i], p, material))),
];
const dir = (deg) => V(0, Math.sin(rad(deg)), Math.cos(rad(deg)));
/** The arm's tube from the pivot to the collar, at the start: what the grip's place leaves after the bar's reach. */
const LEVER = dir(GRIP.angle).multiplyScalar(GRIP.reach).addScaledVector(dir(BAR.angle), -BAR.grip);

const pivot = V(0, ...PIVOT);
const rockAt = V(0, ...ROCK);
const jointOf = (type, origin, axis) => ({ type, origin, axis });
const armsJoint = jointOf('revolute', pivot, V(1, 0, 0));
const carriageJoint = jointOf('revolute', rockAt, V(-1, 0, 0));

/** The arm's elbow, and the grip bar's middle and its direction, at the start (`dip` degrees down by none). */
const elbow = (s) => pivot.clone().add(LEVER).setX(s * ARMS_X);
const gripBar = (s) => ({ from: elbow(s), dir: dir(BAR.angle) });
/** The grip's middle on side `s` at the start. */
const gripAt = (s) => gripBar(s).from.addScaledVector(dir(BAR.angle), BAR.grip);

/** The pad's unit direction up its face, and out from it, at the start, in [y, z]. */
const padUp = V(0, Math.cos(FACE.lean), -Math.sin(FACE.lean));
const padOut = V(0, Math.sin(FACE.lean), Math.cos(FACE.lean));
const faceFrom = V(0, ...FACE.from);
const plateCentre = (s) => V(s * FEET.apart, FEET.y, FEET.z);
/** The footplate's face's normal, toward the user, and up its face toward the toes, at the start. */
const plateOut = V(0, Math.cos(FEET.tilt), -Math.sin(FEET.tilt));
const plateUp = V(0, Math.sin(FEET.tilt), Math.cos(FEET.tilt));

export default recipe({
  name: 'dip_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, the tower on the left, as the ref's photo has it.
  standsAlone: { camera: { target: [0, 0.75, 0.1], halfHeight: 1.1, azimuth: 40, elevation: 14 } },
  bodies: {
    arms: revolute({ axis: V(1, 0, 0), limits: ARMS }),
    carriage: revolute({ axis: V(-1, 0, 0), limits: CARRIAGE }),
  },
  // The top of the rep is the arms straight down at the hips, the bottom the handles at the chest.
  rep: { joint: 'arms', from: 'max' },
  contacts: [
    { body: 'arms', point: 'handles', landmark: 'hands' },
    { body: 'carriage', point: 'backPad', landmark: 'back' },
    { body: 'carriage', point: 'footplates', landmark: 'feet' },
  ],
  stacks: [{ joint: 'arms', ratio: RATIO }],
  linkage: (_options, L) => {
    // The hips on the seat, the spine's line `L.back` off the pad's face.
    const hipY = SEAT.top + SITS;
    const hipZ = faceFrom.z + (L.back - (hipY - faceFrom.y) * Math.sin(FACE.lean)) / Math.cos(FACE.lean);
    const hip = V(0, hipY, hipZ);
    return {
      hip: [hipY, hipZ],
      at: (value) => {
        const rock = moveOf(carriageJoint, value * COUPLE);
        const swing = moveOf(armsJoint, value);
        const turned = (p) => p.clone().applyMatrix4(rock);
        const bent = (v) => v.clone().transformDirection(rock);
        return {
          value,
          rocked: value * COUPLE,
          hipC: turned(hip),
          // How far the pad has leant back from upright, radians.
          lean: FACE.lean + value * COUPLE,
          hand: (s) => gripAt(s).applyMatrix4(swing),
          across: () => dir(BAR.angle).transformDirection(swing),
          plate: turned(plateCentre(0)),
          plateOut: bent(plateOut),
          plateUp: bent(plateUp),
        };
      },
    };
  },
  build(_options, M) {
    const sides = [-1, 1];
    const rear = FLOOR.rear + 0.05;
    const wheel = V(0, STACK.top - 0.035 - WHEEL - 0.02, 0);

    // The frame: a floor beam along the machine, a rear cross beam, the rear upright leaning back to
    // the arms' pivot with a brace to the beam, the housing across the pivot.
    const frame = [];
    frame.push(...floorRail(V(0, 0.035, FLOOR.rear), V(0, 0.035, FLOOR.front), M));
    frame.push(...floorRail(V(-FLOOR.half + 0.016, 0.035, rear), V(FLOOR.half - 0.016, 0.035, rear), M));
    // The rear upright runs up into the crosshead, which it ends inside: no square end shows under it.
    frame.push(...bent([V(0, 0.07, -0.5), V(0, PIVOT[0] - 0.01, PIVOT[1] - 0.005)], 0.08, M.frame, 'upright'));
    frame.push(role(tube(0.08, V(-0.34, PIVOT[0], PIVOT[1]), V(0.34, PIVOT[0], PIVOT[1]), M.frame), 'crosshead'));
    // The carriage's own pivot is a block on the floor beam, the axle through it fixed.
    frame.push(tube(0.08, V(0, 0.07, ROCK[1]), V(0, ROCK[0] - 0.04, ROCK[1]), M.frame));

    // The stack's tower, built about its own middle and stood with the plates facing the seat: the
    // shroud's rear, front and outer walls, a cap on top, the frame and the pulley inside, a wire down.
    const tower = new THREE.Group();
    tower.position.set(STACK.x, 0, STACK.z);
    tower.rotation.y = Math.PI / 2;
    const weights = weightStack(V(0, 0, 0), M, { rodTop: STACK.top - 0.07, toward: 1 });
    const across = STACK.across / 2;
    const deep = STACK.deep / 2;
    tower.add(
      ...stackFrame(0, STACK.top, M), ...pulley(wheel, M), ...weights.parts,
      pad(0.02, 1.25, STACK.across, M.pulley, V(deep, 0.66, 0), { soft: 0.004, solid: true }),
      pad(0.02, 1.25, STACK.across, M.pulley, V(-deep, 0.66, 0), { soft: 0.004, solid: true }),
      pad(STACK.deep, 1.25, 0.02, M.pulley, V(0, 0.66, -across), { soft: 0.004, solid: true }),
      pad(STACK.deep + 0.04, 0.07, STACK.across + 0.04, M.frame, V(0, STACK.cap, 0), { soft: 0.02, solid: true }),
      tube(0.07, V(0, 0.035, 0), V(0, 0.035, -STACK.x), M.frame),
    );
    const drop = wire(M);
    tower.add(drop);

    // The carriage: a heavy tube from the pad's foot down and forward under the seat to the foot beam,
    // the seat on its post, the pad on its board, a footplate each side on a stem off the beam.
    const seatFront = SEAT.z + SEAT.size / 2;
    // The foot beam lies just behind the plates' backs (the side away from the feet) and holds them both.
    const beam = plateCentre(0).addScaledVector(plateOut, -0.06);
    const spine = [V(0, 0.5, -0.36), V(0, 0.3, -0.34), V(0, 0.2, -0.2), V(0, 0.17, 0.2), V(0, beam.y, beam.z)];
    const carriage = [
      ...bent(spine, 0.07, M.frame, 'spine'),
      ...pivotJoint(M, rockAt, X).fixed,
      role(pad(SEAT.width, 0.075, SEAT.size, M.vinyl, V(0, SEAT.top - 0.0375, SEAT.z), { soft: 0.035 }), 'seat'),
      pad(SEAT.width - 0.06, 0.012, SEAT.size - 0.06, M.frame, V(0, SEAT.top - 0.081, SEAT.z), { soft: 0.003 }),
      tube(0.06, V(0, 0.2, SEAT.z), V(0, SEAT.top - 0.087, SEAT.z), M.frame),
      tube(0.05, beam.clone().setX(-FEET.apart - 0.02), beam.clone().setX(FEET.apart + 0.02), M.frame),
    ];
    const padAt = faceFrom.clone().addScaledVector(padUp, FACE.length / 2 + 0.02).addScaledVector(padOut, -FACE.thick / 2);
    const cushion = role(pad(FACE.width, FACE.length, FACE.thick, M.vinyl, padAt, { soft: 0.025 }), 'backPad');
    cushion.rotation.x = -FACE.lean;
    carriage.push(cushion);
    for (const s of sides) {
      const plate = role(pad(FEET.width, 0.02, FEET.length, M.vinyl, plateCentre(s), { soft: 0.008, solid: true }), 'footplate');
      plate.rotation.x = -FEET.tilt;
      carriage.push(plate);
    }

    // The arms: one mitred tube each from the pivot, to the elbow, to the grip bar's end, with a grip
    // and a sleeve held in the hand along the bar and a cap on its end.
    const levers = sides.map((s) => {
      const bar = gripBar(s);
      const at = (d) => bar.from.clone().addScaledVector(bar.dir, d);
      const joint = pivotJoint(M, V(s * TUBE_X, ...PIVOT), X);
      return {
        joint,
        tube: bent([V(s * TUBE_X, ...PIVOT), bar.from.clone().setX(s * TUBE_X), bar.from], 0.045, M.frame, 'lever'),
        collar: rod(0.03, bar.from, at(0.025), M.metal, { solid: true }),
        grip: proxy(rod(0.02, at(BAR.grip - 0.075), at(BAR.length - 0.02), M.metal, { held: true })),
        sleeve: rod(0.0235, at(BAR.grip - 0.075), at(BAR.length - 0.02), M.vinyl, { held: true }),
      };
    });
    const armParts = levers.flatMap((l) => [l.joint.housing, ...l.tube, l.collar, l.grip, l.sleeve]);
    // The pivot's axles are the frame's.
    levers.forEach((l) => frame.push(...l.joint.fixed));

    // Where each moving part rests at the start: a joint's value moves it from there, whole.
    const rest = new Map();
    for (const node of [...armParts, ...carriage]) { node.updateMatrix(); rest.set(node, node.matrix.clone()); }
    const lay = (nodes, move) => nodes.forEach((n) => rest.get(n).clone().premultiply(move).decompose(n.position, n.quaternion, n.scale));
    const start = {
      handles: sides.map((s) => gripAt(s)),
      backPad: [padAt.clone()],
      footplates: sides.map((s) => plateCentre(s)),
    };

    return {
      parts: [...frame, tower, ...carriage, ...armParts],
      bodies: { arms: armParts, carriage },
      stacks: {
        arms: {
          lift(l) {
            weights.lift(l);
            span(drop, V(0, wheel.y, 0), weights.top(l));
          },
        },
      },
      drive(P) {
        const value = P.linkage?.value ?? 0;
        const arms = moveOf(armsJoint, value);
        const rocked = moveOf(carriageJoint, value * COUPLE);
        lay(armParts, arms);
        lay(carriage, rocked);
        const on = (points, move) => points.map((p) => p.clone().applyMatrix4(move));
        return {
          joints: { arms: { value, origin: pivot.clone() }, carriage: { value: value * COUPLE, origin: rockAt.clone() } },
          points: { handles: on(start.handles, arms), backPad: on(start.backPad, rocked), footplates: on(start.footplates, rocked) },
        };
      },
    };
  },
});
