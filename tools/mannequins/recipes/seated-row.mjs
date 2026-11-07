// A low seated cable row station (refs/seated_row/ref.md): a long low bench on one rear post, two
// foot plates leaning toward the tower, a deck rising from them to the tower's front, and one cable
// leaving the tower at the height the lifts' videos have it, between the lifter's shins.
//
// The attachment is a free body, carried by the hands. The stack rides its guide rods on a prismatic
// joint, its value how far the cable is drawn past where the stack rests, and rises one for one.
// The hips stand at z 0 and the tower ahead; nothing but the cable system moves.
import * as THREE from 'three';
import { recipe, free, prismatic } from '../recipe.mjs';
import {
  ATTACHMENTS, WHEEL, endCap, pad, part, pulley, rod, rodGeometry, span, stackFrame, tube, weightStack,
} from '../parts.mjs';
import { V, UP, gripTarget } from '../rig.mjs';

// The pad's top and its two ends along z; how far above it the hips sit.
const SEAT_Y = 0.43;
const PAD_Z = [-0.416, 0.584];
const SITS = 0.09;
// Narrow enough for the shins to pass beside it, the plates standing below the seat.
const PAD_W = 0.22;
// The foot plates' low and high edges, [y, z], and how far either side of the middle each stands.
const PLATE_LOW = [0.08, 0.7];
const PLATE_HIGH = [0.4, 0.865];
const PLATES_OUT = 0.14;
const PLATE_THICK = 0.02;
// Where the deck meets the tower, the tower's front and back, its height and its posts' half width.
const DECK_END = [0.59, 1.3];
const TOWER_Z = [1.26, 1.87];
const TOWER_Y = 2.413;
// The tower's posts stand this far in from its ends, the ties across them at the front ones.
const TIE_Z = TOWER_Z[0] + 0.04;
const TOWER_X = 0.305;
// The cable's exit, built to the lifts' videos (the ref's Open gaps), and the stack's place.
const EXIT = [0.85, 1.2];
const STACK_Z = 1.56;
const POST_Z = -0.2;

/** The way up the plates and the way their faces look, toward the lifter. */
const PLATE_UP = V(0, PLATE_HIGH[0] - PLATE_LOW[0], PLATE_HIGH[1] - PLATE_LOW[1]).normalize();
const PLATE_OUT = V(0, PLATE_UP.z, -PLATE_UP.y);

/** A flat panel `w` wide from A to B, its top side facing `up`. */
function panel(w, thick, A, B, up, material, opts) {
  const mesh = pad(w, thick, A.distanceTo(B), material, A.clone().lerp(B, 0.5), opts);
  const along = B.clone().sub(A).normalize();
  const square = up.clone().addScaledVector(along, -up.dot(along)).normalize();
  mesh.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(square.clone().cross(along), square, along));
  return mesh;
}

export default recipe({
  name: 'seated_row',
  node: 'row_station',
  bodies: { handle: free(), stack: prismatic({ axis: UP, limits: [0, 1.5] }) },
  contacts: [
    { body: 'handle', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'plates', landmark: 'feet' },
  ],
  stacks: [{ joint: 'stack', ratio: 1 }],
  linkage: () => ({
    hip: [SEAT_Y + SITS, 0],
    at: () => ({ exit: V(0, ...EXIT), plate: V(0, ...PLATE_LOW).lerp(V(0, ...PLATE_HIGH), 0.5).addScaledVector(PLATE_OUT, PLATE_THICK), plateUp: PLATE_UP, plateOut: PLATE_OUT }),
  }),
  standsAlone: { camera: { target: [0, 0.9, 0.7], halfHeight: 1.4, azimuth: 70, elevation: 12 } },
  build({ attachment = 'handles', spread } = {}, M) {
    const low = V(0, ...PLATE_LOW);
    const high = V(0, ...PLATE_HIGH);
    const exit = V(0, ...EXIT);
    // Where the lifter's run leaves the wheel, on its rim.
    const rim = exit.clone().setZ(EXIT[1] - WHEEL);
    const top = TOWER_Y - 0.012;
    const frameFoot = low.clone().setY(0.1);
    // The top wheel hangs from the stack frame's crossbar, right over the stack.
    const wheelTop = V(0, top - 0.04 - 0.035 - WHEEL - 0.02, STACK_Z);
    // Where a rib's line stands at height `y`.
    const ribAt = (y) => frameFoot.z + (high.z - 0.04 * PLATE_OUT.z - frameFoot.z) * (y - frameFoot.y) / (high.y - 0.04 * PLATE_OUT.y - frameFoot.y);
    const parts = [
      // The bench: the pad on its board, on a rail from the plates' frame to the rear post, a lower
      // beam under it, and the post down to its foot.
      pad(PAD_W, 0.065, PAD_Z[1] - PAD_Z[0], M.vinyl, V(0, SEAT_Y - 0.0325, (PAD_Z[0] + PAD_Z[1]) / 2)),
      pad(PAD_W - 0.04, 0.012, PAD_Z[1] - PAD_Z[0] - 0.04, M.frame, V(0, SEAT_Y - 0.071, (PAD_Z[0] + PAD_Z[1]) / 2), { soft: 0.003, solid: false }),
      tube(0.06, V(0, 0.32, POST_Z), V(0, 0.32, ribAt(0.32)), M.frame),
      tube(0.055, V(0, 0.13, POST_Z), V(0, 0.13, ribAt(0.13)), M.frame),
      tube(0.075, V(0, 0.035, POST_Z), V(0, 0.35, POST_Z), M.frame),
      tube(0.07, V(-0.15, 0.035, POST_Z), V(0.15, 0.035, POST_Z), M.frame),
      ...[-1, 1].map((s) => endCap(s * 0.15, POST_Z, M)),
      // The plates' frame: a beam across their low edge, a rib up each and one up the middle that
      // the bench's rail ends in.
      tube(0.06, V(-0.26, 0.1, low.z), V(0.26, 0.1, low.z), M.frame),
      ...[-1, 1].map((s) => pad(0.016, 0.066, 0.066, M.vinyl, V(s * 0.268, 0.1, low.z), { soft: 0.005, solid: false })),
      ...[-1, 0, 1].map((s) => tube(0.05, frameFoot.clone().setX(s * PLATES_OUT), high.clone().setX(s * PLATES_OUT).addScaledVector(PLATE_OUT, -0.04), M.frame)),
      ...[-1, 1].map((s) => panel(0.19, PLATE_THICK, low.clone().setX(s * PLATES_OUT), high.clone().setX(s * PLATES_OUT), PLATE_OUT, M.frame, { soft: 0.004 })),
      // The deck, rubber on a plate, from the plates' top edge to the tower, and the low beam under it.
      panel(0.5, 0.03, high.clone().addScaledVector(PLATE_OUT, -0.015), V(0, ...DECK_END), V(0, 1, 0), M.vinyl, { soft: 0.006 }),
      tube(0.05, V(-TOWER_X, DECK_END[0] - 0.04, TIE_Z), V(TOWER_X, DECK_END[0] - 0.04, TIE_Z), M.frame),
      tube(0.06, V(0, 0.1, low.z), V(0, 0.1, TIE_Z), M.frame),
      // The tower: a post at each corner on its foot rail, tied at the exit's height and at the top.
      ...[TIE_Z, TOWER_Z[1] - 0.04].flatMap((z) => [-1, 1].map((s) => tube(0.076, V(s * TOWER_X, 0.038, z), V(s * TOWER_X, top, z), M.frame))),
      ...[-1, 1].map((s) => tube(0.07, V(s * TOWER_X, 0.035, TOWER_Z[0]), V(s * TOWER_X, 0.035, TOWER_Z[1]), M.frame)),
      tube(0.06, V(-TOWER_X, 0.1, TIE_Z), V(TOWER_X, 0.1, TIE_Z), M.frame),
      tube(0.05, V(-TOWER_X, EXIT[0] - 0.05, TIE_Z), V(TOWER_X, EXIT[0] - 0.05, TIE_Z), M.frame),
      // The stack's base, under its uprights and guide rods.
      tube(0.07, V(-TOWER_X, 0.035, STACK_Z), V(TOWER_X, 0.035, STACK_Z), M.frame),
      pad(0.686, 0.024, TOWER_Z[1] - TOWER_Z[0], M.frame, V(0, top, (TOWER_Z[0] + TOWER_Z[1]) / 2), { soft: 0.004 }),
      ...stackFrame(STACK_Z, top - 0.04, M),
      // The exit's wheel on an arm from the tie across the tower's front, below the cable's run.
      tube(0.05, V(0, EXIT[0] - 0.05, exit.z - 0.05), V(0, EXIT[0] - 0.05, TIE_Z), M.frame),
      ...pulley(exit, M),
      ...pulley(wheelTop, M),
      // The cable inside the tower, from the exit's wheel up to the one over the stack.
      rod(0.0045, V(0, EXIT[0], EXIT[1] + WHEEL), V(0, wheelTop.y, wheelTop.z - WHEEL), M.metal),
    ];
    const cable = part(rodGeometry(0.0045, 8), M.metal);
    const drop = part(rodGeometry(0.0045, 8), M.metal);
    const weights = weightStack(V(0, 0, STACK_Z), M, { rodTop: top - 0.075, toward: -1 });
    if (!ATTACHMENTS[attachment]) throw new Error(`No cable attachment "${attachment}"`);
    const { parts: grip, hook: above } = ATTACHMENTS[attachment](M, { spread });
    const handle = new THREE.Group();
    handle.name = 'cable_handle';
    grip.forEach((m, i) => { m.name = `cable_handle_${i}`; handle.add(m); });
    // Turned to the cable, which comes from ahead: its hook toward the exit, two handles' grips square to it.
    const toExit = (P) => rim.clone().sub(P.bar).normalize();
    const hook = (P) => P.bar.clone().addScaledVector(toExit(P), above);
    return {
      parts: [...parts, cable, drop, ...weights.parts, handle],
      bodies: { stack: [] },
      cables: [{ cable, hook: () => handle.localToWorld(V(0, above, 0)) }],
      stacks: {
        stack: {
          lift(l) {
            weights.lift(l);
            span(drop, wheelTop, weights.top(l));
          },
        },
      },
      drive(P, P0) {
        handle.position.copy(P.bar);
        handle.quaternion.setFromUnitVectors(UP, toExit(P));
        const at = hook(P);
        span(cable, at, rim);
        return {
          joints: { stack: { value: Math.max(0, at.distanceTo(rim) - hook(P0).distanceTo(rim)), origin: V(0, 0, STACK_Z) } },
          points: { grips: [-1, 1].map((s) => gripTarget(P, s)), plates: [-1, 1].map((s) => P.plate.clone().setX(s * PLATES_OUT)) },
        };
      },
    };
  },
});
