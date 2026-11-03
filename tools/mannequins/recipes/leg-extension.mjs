// A leg extension or, with `curl`, a seated leg curl, laid out as the reference machines (#149):
// the tower beside the seat, level with it, on the far side from the camera; a seat whose top is at
// `seat` [y, z], tilted `tilt` degrees up toward the knees, on crossed braces and a beam out from the
// tower; and on the near side a post up to the cam lever about the knees, its roller on the front
// of the shins for the extension and behind the ankles for the curl. Both have a back pad from the
// seat up to the shoulder blades (its face `pad` behind the spine) on a bar through a sleeve on a
// rear upright, set by a pop-pin. The extension's handles run forward beside the hips; the curl
// adds a pad lying along the thighs on a bar out from the near post, with upright grips on it.
//
// The lever is a body on a revolute joint about the knees, its value how far it has turned from the
// stretch, the bottom of the rep, where the stack rests; the stack rises 22 cm a radian of it.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { UPRIGHTS, backPad, floorRail, kneeDrive, knob, pad, part, popPin, rod, rodGeometry, span, stretchedTube, tube } from '../parts.mjs';
import { V, UP, rad } from '../rig.mjs';

/**
 * The lever's joint, turning from the stretch up to `max`: forward and up for the extension, which
 * stops where the knee is straight, and back for a curl.
 */
export const kneeLever = (axis, max) => revolute({ axis, limits: [0, max] });

/** The seated knee machines' parts, the extension's or with `curl` the curl's. */
export function seatedKnees({ seat = [0.59, 0.1], tilt = 10, pad: behind = 0.18, curl = false } = {}, M) {
  const [top, sz] = seat;
  const drive = kneeDrive(M, { z: sz, toward: 1, down: curl ? 0.38 : 0.35, behind: curl });
  const { side, tx, z0 } = drive;
  // The seat, its cushion and pan on a beam, tilted about its middle.
  const cushion = new THREE.Group();
  cushion.add(
    pad(0.36, 0.075, 0.4, M.vinyl, V(0, -0.0375, 0), { soft: 0.024 }),
    pad(0.3, 0.012, 0.34, M.frame, V(0, -0.081, 0), { soft: 0.003 }),
    tube(0.06, V(0, -0.117, -0.18), V(0, -0.117, 0.18), M.frame),
  );
  cushion.position.set(0, top, sz);
  cushion.rotation.x = -rad(tilt);
  cushion.updateMatrixWorld();
  // A point on the seat, `along` it toward the knees; the beam is 0.117 under the cushion's top.
  const seatAt = (x, y, along) => cushion.localToWorld(V(x, y, along));
  const beam = (along) => seatAt(0, -0.117, along);
  const parts = [
    cushion, ...drive.parts,
    ...floorRail(V(0, 0.035, sz - 0.62), V(0, 0.035, z0 - 0.035), M),
    ...floorRail(V(tx, 0.035, sz - 0.3), V(tx, 0.035, z0 - 0.035), M),
    // Crossed braces from the floor rail to the beam, and the beam's front out from the tower.
    tube(0.05, V(0, 0.07, sz - 0.24), beam(0.12), M.frame),
    tube(0.05, V(0, 0.07, sz + 0.2), beam(-0.12), M.frame),
    tube(0.06, V(tx + 0.035, beam(0.17).y, sz + UPRIGHTS), V(0, beam(0.17).y, sz + UPRIGHTS), M.frame),
  ];
  // The back pad, from the seat up to the shoulder blades, on a bar back from its board through a
  // sleeve on a rear upright, set by a pop-pin.
  const backRest = backPad(0.36, 0.58, M, { up: 0.21, behind });
  const upright = stretchedTube(0.06, M.frame);
  const bar = stretchedTube(0.04, M.frame);
  const end = part(rodGeometry(0.0195), M.vinyl);
  const sleeve = pad(0.08, 0.06, 0.1, M.frame, V(0, 0, 0), { soft: 0.008, solid: false });
  const pin = popPin(V(0, 0, 0), 1, M);
  parts.push(backRest.mesh, upright, bar, end, sleeve, pin);
  const fitBack = (P0) => {
    // Tipped back a little more than the spine, onto the shoulder blades and off the small of the back.
    const board = backRest.fit(P0);
    backRest.mesh.rotateX(-rad(7));
    const uz = board.z - 0.14;
    span(upright, V(0, 0.07, uz), V(0, board.y - 0.03, uz));
    span(bar, board, V(0, board.y, uz - 0.18));
    span(end, V(0, board.y, uz - 0.18), V(0, board.y, uz - 0.3));
    sleeve.position.set(0, board.y, uz);
    pin.position.set(0, board.y + 0.045, uz);
    pin.rotation.x = -Math.PI / 2;
  };
  let fit = () => {};
  // The frame's contact: the handles in the hands for the extension, the thigh pad on the thighs for the curl.
  let held = () => ({});
  if (!curl) {
    // The handles: a bar across under the seat's rear, risers up its sides and grips running forward
    // beside the hips, level with the cushion's top.
    parts.push(
      tube(0.04, seatAt(-0.25, -0.117, -0.16), seatAt(0.25, -0.117, -0.16), M.frame),
      ...[-1, 1].flatMap((s) => [
        tube(0.04, seatAt(s * 0.25, -0.117, -0.16), seatAt(s * 0.25, 0.02, -0.16), M.frame),
        rod(0.016, seatAt(s * 0.25, 0.02, -0.16), seatAt(s * 0.25, 0.02, 0.04), M.metal, { held: true }),
        rod(0.0195, seatAt(s * 0.25, 0.02, -0.12), seatAt(s * 0.25, 0.02, 0.03), M.vinyl, { held: true }),
      ]),
    );
    held = (P) => ({ handles: [-1, 1].map((s) => P.hand(s).clone()) });
  } else {
    const thighPad = pad(0.36, 0.06, 0.14, M.vinyl, V(0, 0, 0), { soft: 0.02 });
    const thighBar = stretchedTube(0.05, M.frame);
    const riser = stretchedTube(0.06, M.frame);
    const plate = pad(0.012, 0.22, 0.16, M.frame, V(0, 0, 0), { soft: 0.004, solid: false });
    const plateKnob = knob(M, V(0, 0, 0));
    const grips = [-1, 1].map(() => [part(rodGeometry(0.016), M.metal, { held: true }), part(rodGeometry(0.0195), M.vinyl, { held: true })]);
    parts.push(thighPad, thighBar, riser, plate, plateKnob, ...grips.flat());
    fit = (P0, pivot) => {
      // Over the lower half of the thighs, lying along them.
      const above = P0.knee.clone().addScaledVector(P0.thigh, -0.18).addScaledVector(P0.kneeUp, 0.125);
      thighPad.position.copy(above);
      thighPad.quaternion.setFromUnitVectors(UP, P0.kneeUp);
      const by = above.y + 0.06;
      span(thighBar, V(side + 0.03, by, above.z), V(-0.2, by, above.z));
      span(riser, pivot, V(side, by + 0.03, above.z));
      plate.position.set(side + 0.04, (pivot.y + by) / 2, (pivot.z + above.z) / 2);
      plateKnob.position.set(side + 0.06, (pivot.y + by) / 2 + 0.05, (pivot.z + above.z) / 2);
      grips.forEach(([grip, sleeveGrip], i) => {
        const x = (i ? 1 : -1) * 0.09;
        span(grip, V(x, by, above.z), V(x, by + 0.15, above.z));
        span(sleeveGrip, V(x, by + 0.04, above.z), V(x, by + 0.15, above.z));
      });
    };
    held = () => ({ thighPad: [thighPad.position.clone()] });
  }
  return {
    parts,
    bodies: { lever: drive.lever },
    stacks: { lever: drive.tower },
    follow: drive.follow,
    drive(P, P0, P1) {
      const { pivot, roller, joint } = drive.place(P, P0, P1);
      fitBack(P0);
      fit(P0, pivot);
      return { joints: { lever: joint }, points: { roller: [roller], ...held(P) } };
    },
  };
}

/** The fit rules both seated knee machines share. */
export const SEATED_FIT = [
  { sets: 'seat', rule: 'the knees on the pivot axis' },
  { sets: 'pad', rule: 'the back pad on the shoulder blades' },
];

export default recipe({
  name: 'leg_extension',
  // From the stretch, the knee bent about 95°, to the knee straight.
  bodies: { lever: kneeLever(V(-1, 0, 0), rad(95)) },
  contacts: [
    { body: 'lever', point: 'roller', landmark: 'shins' },
    { body: 'frame', point: 'handles', landmark: 'hands' },
  ],
  stacks: [{ joint: 'lever', ratio: 0.22 }],
  fit: SEATED_FIT,
  build: (options, M) => seatedKnees({ ...options, curl: false }, M),
});
