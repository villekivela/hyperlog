// A 45 degree hyperextension bench (refs/hyperextension_bench/ref.md): a holed rail 1248 mm long on
// two flat feet 666 mm across, a sleeved column of 75 mm tube leaning up from a pivot on the rail and
// a strut holding it from the head end, two hip pads on a carriage at its top, grab handles reaching
// out past the head end, a foot plate square to the body line at the other end, and from the post
// under it one mitred arm up to a cross tube carrying the heel rollers. The frame is rigid; the
// lifter's hip is what turns, so nothing on it moves.
//
// The lifter lies face down along the slope and faces +z: the origin is on the floor under the hip
// pads' top end, the head end ahead at +z and the feet behind at -z. The body line, from the pads'
// top end down the slope to the plate's surface, is 900 mm, the pads' top end 825 mm high.
import * as THREE from 'three';
import { holedTube, knob, mitered, pad, rod, rollerGeometry, tube, turnedPart } from '../parts.mjs';
import { UP, V, X } from '../rig.mjs';
import { recipe } from '../recipe.mjs';

const TUBE = 0.075;
// The slope: down toward the feet at 45 degrees, `S` along it and `N` off it, away from the pads.
const S = V(0, -Math.SQRT1_2, -Math.SQRT1_2);
const N = V(0, Math.SQRT1_2, -Math.SQRT1_2);
const FACE = S.clone().negate();
const TOP = V(0, 0.825, 0);
const BODY_LINE = 0.9;
// The base, from the head end at `head`, the drawing's measuring end, 1248 mm back to the foot end.
const BASE = { head: 0.358, length: 1.248, width: 0.666, foot: 0.09, thick: 0.035 };
const RAIL = BASE.thick + TUBE / 2;
const RAIL_TOP = BASE.thick + TUBE;
const PAD = { width: 0.21, gap: 0.055, length: 0.33, thick: 0.09, board: 0.012 };
const PLATE = { width: 0.47, length: 0.33, thick: 0.03, up: 0.06 };
// The heel rollers: 190 mm long, 130 across, 195 up the body line from the plate and 240 off it.
const ROLLER = { length: 0.19, radius: 0.065, up: 0.195, off: 0.24, gap: 0.11 };
const HANDLE = { height: 0.69, x: 0.29, past: 0.05, radius: 0.016 };
// The column's line stands this far under the pads' top face; `PIVOT_Y` is where it and the strut turn.
const COLUMN = { under: 0.15, sleeve: 0.5, hole: 0.07 };
const PIVOT_Y = 0.181;
// The strut's pivot and top, and where its stub meets the column, from the head end, as the drawing has them.
const STRUT = { foot: 0.218, top: [0.412, 0.408], column: 0.476 };
// The post under the plate's heel end and the stub under its toe end, from the head end.
const POST_X = 1.108;
const STUB_X = 1.024;
// Where the hips and the ankles lie, off the pad's top face and past its top end.
const HIP = { past: 0.05, off: 0.1 };
const ANKLE_OFF = 0.13;

/** A point along the column, `t` down the slope from level with the pads' top end. */
const columnAt = (t) => TOP.clone().addScaledVector(N, -COLUMN.under).addScaledVector(S, t);
/** How far down the column stands at height `y`. */
const columnDown = (y) => (columnAt(0).y - y) / -S.y;
/** A depth `x` from the head end. */
const fromHead = (x) => BASE.head - x;
const sole = TOP.clone().addScaledVector(S, BODY_LINE);

/** Marks a part for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** A part for the checks alone, a straight stand-in inside a mitred tube: the clip leaves it out. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };

export default recipe({
  name: 'hyperextension_bench',
  bodies: {},
  contacts: [
    { body: 'frame', point: 'thighPad', landmark: 'thighs' },
    { body: 'frame', point: 'footPlate', landmark: 'feet' },
    { body: 'frame', point: 'heelRollers', landmark: 'shins' },
  ],
  linkage: (_, L) => ({
    at: () => ({
      hip: TOP.clone().addScaledVector(S, -HIP.past).addScaledVector(N, HIP.off),
      ankle: sole.clone().addScaledVector(FACE, L.ankleY).addScaledVector(N, ANKLE_OFF),
      sole: sole.clone(),
      soleUp: FACE.clone(),
      toes: N.clone().negate(),
    }),
  }),
  build(_, M) {
    const sides = [-1, 1];
    // Across the bench: each pad's middle, and each roller's.
    const padX = (s) => (s * (PAD.gap + PAD.width)) / 2;
    const rollerX = (s) => s * (ROLLER.gap / 2 + ROLLER.length / 2);
    const at = (from, ...moves) => moves.reduce((p, [d, k]) => p.addScaledVector(d, k), from.clone());
    const lay = (mesh, centre, y, z) => {
      mesh.position.copy(centre);
      mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, y, z));
      return mesh;
    };

    // The base: a holed rail on a flat foot at each end.
    const [headEnd, footEnd] = [BASE.head, BASE.head - BASE.length];
    const holes = Array.from({ length: 17 }, (_h, i) => 0.06 + i * COLUMN.hole);
    const frame = [
      ...[headEnd - BASE.foot / 2, footEnd + BASE.foot / 2].map((z) => pad(BASE.width, BASE.thick, BASE.foot, M.frame, V(0, BASE.thick / 2, z), { soft: 0.004 })),
      role(holedTube(TUBE, V(0, RAIL, headEnd), V(0, RAIL, footEnd), holes, M.frame), 'rail'),
      ...[headEnd + 0.003, footEnd - 0.003].map((z) => role(pad(TUBE + 0.002, TUBE + 0.002, 0.006, M.vinyl, V(0, RAIL, z), { soft: 0.002 }), 'railCap')),
    ];

    // A pin across a clevis standing on the rail.
    const clevis = (z, name) => [
      role(pad(TUBE + 0.03, PIVOT_Y + 0.03 - RAIL_TOP, 0.1, M.frame, V(0, (PIVOT_Y + 0.03 + RAIL_TOP) / 2, z), { soft: 0.004 }), `${name}Clevis`),
      role(rod(0.018, V(-0.07, PIVOT_Y, z), V(0.07, PIVOT_Y, z), M.metal), `${name}Pivot`),
    ];

    // The column: a sleeve up from its pivot, the tube running on out of it to the carriage.
    const foot = columnDown(PIVOT_Y);
    const column = [
      ...clevis(columnAt(foot).z, 'column'),
      role(tube(0.09, columnAt(foot), columnAt(foot - COLUMN.sleeve), M.frame), 'columnSleeve'),
      role(tube(TUBE, columnAt(foot - COLUMN.sleeve + 0.05), columnAt(0.02), M.frame), 'column'),
    ];

    // The strut: a sleeve up from its pivot, its knob on it, and a tube out of that, mitred over to the column.
    const strutFoot = V(0, PIVOT_Y, fromHead(STRUT.foot));
    const strutTop = V(0, STRUT.top[1], fromHead(STRUT.top[0]));
    const strutMiddle = strutFoot.clone().lerp(strutTop, 0.55);
    const strutEnd = columnAt(columnDown(STRUT.column));
    const strut = [
      ...clevis(strutFoot.z, 'strut'),
      tube(0.09, strutFoot, strutMiddle, M.frame),
      role(mitered([strutFoot.clone().lerp(strutTop, 0.45), strutTop, strutEnd], 0.065, M.frame, { solid: false }), 'strut'),
      proxy(tube(0.055, strutFoot.clone().lerp(strutTop, 0.45), strutTop, M.frame)),
      proxy(tube(0.055, strutTop, strutEnd, M.frame)),
      knob(M, strutMiddle.clone().setX(0.06), 0.02),
    ];

    // The hip pads, each on a board on the one carriage, their top face on the slope through the
    // pads' top end, a roll at that end.
    const padAt = (x) => at(TOP, [S, PAD.length / 2], [N, -PAD.thick / 2]).setX(x);
    const pads = sides.flatMap((s) => {
      const x = padX(s);
      const hip = pad(PAD.width, PAD.thick, PAD.length, M.vinyl, V(0, 0, 0), { soft: 0.03 });
      hip.userData.pad = 'hip';
      const board = pad(PAD.width - 0.02, PAD.board, PAD.length - 0.04, M.frame, V(0, 0, 0), { soft: 0.003 });
      return [lay(hip, padAt(x), N, FACE), lay(board, padAt(x).addScaledVector(N, -(PAD.thick + PAD.board) / 2), N, FACE)];
    });
    const carriage = pad(2 * PAD.width + PAD.gap - 0.03, 0.01, PAD.length - 0.06, M.frame, V(0, 0, 0), { soft: 0.002 });
    lay(role(carriage, 'carriage'), at(TOP, [S, PAD.length / 2], [N, -PAD.thick - PAD.board - 0.005]), N, FACE);

    // The grab handles, level, from a bar across the column out 50 mm past the base's head end.
    const bar = columnAt(columnDown(HANDLE.height));
    const reach = headEnd + HANDLE.past;
    const handles = sides.map((s) => rod(HANDLE.radius, V(s * HANDLE.x, HANDLE.height, reach), V(s * HANDLE.x, HANDLE.height, bar.z), M.vinyl, { held: true }));
    const ends = sides.map((s) => knob(M, V(s * HANDLE.x, HANDLE.height, reach), HANDLE.radius));
    const crossBar = tube(0.05, bar.clone().setX(-HANDLE.x - 0.02), bar.clone().setX(HANDLE.x + 0.02), M.frame);

    // The foot plate, square to the body line with its surface at the end of it.
    const plateMiddle = at(sole, [N, PLATE.up], [FACE, -PLATE.thick / 2]);
    const plate = lay(pad(PLATE.width, PLATE.thick, PLATE.length, M.frame, V(0, 0, 0), { soft: 0.004 }), plateMiddle, FACE, N.clone().negate());
    plate.userData.pad = 'foot';
    const plateBack = (z) => sole.y + (sole.z - z) - PLATE.thick / Math.SQRT1_2;

    // A stub under its toe end, and the post under its heel end, running on as one mitred arm behind
    // the plate and up the body line. A tube inside each length is what the body meets.
    const stubZ = fromHead(STUB_X);
    const postZ = fromHead(POST_X);
    const postTop = V(0, plateBack(postZ), postZ);
    const elbow = at(sole, [N, ROLLER.off + 0.03], [FACE, -0.04]);
    const armEnd = at(elbow, [FACE, ROLLER.up + 0.1]);
    const postFoot = V(0, RAIL_TOP, postZ);
    const supports = [
      role(mitered([V(0, RAIL_TOP, stubZ), V(0, plateBack(stubZ) + 0.01, stubZ)], 0.05, M.frame, { solid: false, ends: [UP, FACE] }), 'plateStub'),
      role(mitered([postFoot, postTop, elbow, armEnd], 0.06, M.frame, { solid: false, ends: [UP, null] }), 'rollerArm'),
      proxy(tube(0.05, postFoot.clone().setY(RAIL_TOP + 0.01), postTop, M.frame)),
      proxy(tube(0.05, postTop, elbow, M.frame)),
      proxy(tube(0.05, elbow, armEnd, M.frame)),
    ];

    // The heel rollers on an axle through a cross tube off the arm, its knob on the lifter's side.
    const axle = at(sole, [FACE, ROLLER.up], [N, ROLLER.off]);
    const rollers = sides.map((s) => {
      const r = turnedPart(rollerGeometry(ROLLER.radius, ROLLER.length), M.vinyl, axle.clone().setX(rollerX(s)));
      r.userData.pad = 'heel';
      return r;
    });
    const roll = [
      role(tube(0.05, at(axle, [N, -0.03]), at(axle, [N, 0.19]), M.frame), 'rollerCross'),
      rod(0.02, axle.clone().setX(-0.25), axle.clone().setX(0.25), M.metal),
      knob(M, at(axle, [N, 0.1], [FACE, 0.04]), 0.02),
    ];

    const contactPoints = {
      thighPad: sides.map((s) => padAt(padX(s)).addScaledVector(N, PAD.thick / 2)),
      footPlate: [sole.clone()],
      heelRollers: sides.map((s) => axle.clone().setX(rollerX(s))),
    };
    return {
      parts: [...frame, ...column, ...strut, ...pads, carriage, crossBar, ...handles, ...ends, plate, ...supports, ...roll, ...rollers],
      drive() {
        return { points: Object.fromEntries(Object.entries(contactPoints).map(([k, v]) => [k, v.map((p) => p.clone())])) };
      },
    };
  },
});
