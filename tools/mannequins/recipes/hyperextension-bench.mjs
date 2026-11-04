// A 45 degree hyperextension bench (refs/hyperextension_bench/ref.md): a flat rail 1248 mm long on
// two cross feet 666 mm across, a column of 75 mm tube leaning up from it parallel to the pad, two
// hip pads on a bracket at its top, a foot plate square to the body line at the other end and two
// heel rollers on a post above the plate, with a pair of grab handles reaching back from the pads.
// The frame is rigid; the lifter's hip is what turns, so nothing on it moves.
//
// The lifter lies face down along the slope and faces -z: the origin is on the floor under the
// hip pads' top end, the head end behind it at -z and the feet at +z. The body line, from the pads'
// top end down the slope to the plate's surface, is 900 mm, the pads' top end 825 mm high.
import * as THREE from 'three';
import { pad, rod, tube, turnedPart, knob, rollerGeometry } from '../parts.mjs';
import { V, X } from '../rig.mjs';
import { recipe } from '../recipe.mjs';

const TUBE = 0.075;
// The slope: down toward the feet at 45 degrees, `S` along it and `N` up off it, square to it.
const S = V(0, -Math.SQRT1_2, Math.SQRT1_2);
const N = V(0, Math.SQRT1_2, Math.SQRT1_2);
const TOP = V(0, 0.825, 0);
const BODY_LINE = 0.9;
const BASE = { back: -0.2, front: 1.048, width: 0.666, foot: 0.09, thick: 0.02 };
const RAIL = BASE.thick + TUBE / 2;
const PAD = { width: 0.21, gap: 0.055, length: 0.33, thick: 0.09, board: 0.012 };
const PLATE = { width: 0.47, length: 0.33, thick: 0.012 };
// The heel rollers: 190 mm long, 130 across, 195 up the body line from the plate and 210 off it.
const ROLLER = { length: 0.19, radius: 0.065, up: 0.195, off: 0.21, gap: 0.11 };
const HANDLE = { height: 0.69, x: 0.29, past: 0.05, radius: 0.016 };

export default recipe({
  name: 'hyperextension_bench',
  // Shown alone in the studio: three-quarter from the foot end, a little above.
  standsAlone: { camera: { target: [0, 0.45, 0.4], halfHeight: 0.85, azimuth: 40, elevation: 14 } },
  bodies: {},
  contacts: [
    { body: 'frame', point: 'thighPad', landmark: 'thighs' },
    { body: 'frame', point: 'footPlate', landmark: 'feet' },
    { body: 'frame', point: 'heelRollers', landmark: 'shins' },
  ],
  build(_, M) {
    const sides = [-1, 1];
    const at = (from, ...moves) => moves.reduce((p, [d, k]) => p.addScaledVector(d, k), from.clone());
    const railTop = RAIL + TUBE / 2;

    // The base: a rail between two flat cross feet.
    const frame = [
      ...[BASE.back + BASE.foot / 2, BASE.front - BASE.foot / 2].map((z) => pad(BASE.width, BASE.thick, BASE.foot, M.frame, V(0, BASE.thick / 2, z), { soft: 0.003, solid: false })),
      tube(TUBE, V(0, RAIL, BASE.back), V(0, RAIL, BASE.front), M.frame),
    ];

    // The hip pads, each on a board, their top face on the slope through the pads' top end.
    const padAt = (x) => at(TOP, [S, PAD.length / 2], [N, -PAD.thick / 2]).setX(x);
    const pads = sides.flatMap((s) => {
      const x = s * (PAD.gap + PAD.width) / 2;
      const hip = pad(PAD.width, PAD.thick, PAD.length, M.vinyl, V(0, 0, 0), { soft: 0.03 });
      hip.userData.pad = 'hip';
      const board = pad(PAD.width - 0.02, PAD.board, PAD.length - 0.04, M.frame, V(0, 0, 0), { soft: 0.003 });
      const place = (mesh, centre) => {
        mesh.position.copy(centre);
        mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, N, S));
      };
      place(hip, padAt(x));
      place(board, padAt(x).addScaledVector(N, -(PAD.thick + PAD.board) / 2));
      return [hip, board];
    });

    // The column, from the rail up to the bracket under the pads' top end, parallel to the slope, a
    // short strut up to it from the rail, and the bracket across.
    const bracket = at(TOP, [S, 0.08], [N, -0.14]);
    const down = (bracket.y - railTop) / Math.SQRT1_2;
    const foot = at(bracket, [S, down]);
    const strut = at(bracket, [S, 0.4]);
    const upright = [
      tube(TUBE, foot, bracket, M.frame),
      tube(0.05, V(0, railTop, strut.z), strut, M.frame),
      tube(0.05, bracket.clone().setX(-0.3), bracket.clone().setX(0.3), M.frame),
    ];

    // The grab handles, level, from the bracket's ends back 50 mm past the base's back end.
    const handles = sides.map((s) => rod(HANDLE.radius, V(s * HANDLE.x, HANDLE.height, BASE.back - HANDLE.past), V(s * HANDLE.x, HANDLE.height, bracket.z), M.vinyl, { held: true }));
    const ends = sides.map((s) => knob(M, V(s * HANDLE.x, HANDLE.height, BASE.back - HANDLE.past), HANDLE.radius));

    // The foot plate, square to the body line with its surface at the end of it, on a post from the rail.
    const sole = at(TOP, [S, BODY_LINE]);
    const plate = pad(PLATE.width, PLATE.thick, PLATE.length, M.frame, V(0, 0, 0), { soft: 0.004 });
    plate.userData.pad = 'foot';
    const face = S.clone().negate();
    plate.position.copy(at(sole, [face, -PLATE.thick / 2]));
    plate.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, face, N));
    const under = at(sole, [N, 0.06], [face, -PLATE.thick]);
    const post = tube(TUBE, V(0, railTop, under.z), under, M.frame);

    // The heel rollers on an axle through a post up from the plate's heel end.
    const heel = at(sole, [N, PLATE.length / 2]);
    const axle = at(sole, [face, ROLLER.up], [N, ROLLER.off]);
    const rollers = sides.map((s) => {
      const r = turnedPart(rollerGeometry(ROLLER.radius, ROLLER.length), M.vinyl, axle.clone().setX(s * (ROLLER.gap / 2 + ROLLER.length / 2)));
      r.userData.pad = 'heel';
      return r;
    });
    const roll = [
      tube(0.05, heel, axle, M.frame),
      rod(0.02, axle.clone().setX(-0.25), axle.clone().setX(0.25), M.metal),
    ];

    const contactPoints = {
      thighPad: sides.map((s) => at(TOP, [S, PAD.length / 2]).setX(s * (PAD.gap + PAD.width) / 2)),
      footPlate: [sole.clone()],
      heelRollers: sides.map((s) => axle.clone().setX(s * (ROLLER.gap / 2 + ROLLER.length / 2))),
    };
    return {
      parts: [...frame, ...upright, ...pads, ...handles, ...ends, plate, post, ...roll, ...rollers],
      drive() {
        return { points: Object.fromEntries(Object.entries(contactPoints).map(([k, v]) => [k, v.map((p) => p.clone())])) };
      },
    };
  },
});
