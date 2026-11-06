// A plate-loaded shrug machine (refs/shrug_machine/ref.md): the lifter stands between two levers, the
// legs either side of a lowered seat, or sits on the seat at 650 mm. A rectangular base of 75 mm tube
// stands on a foot at each corner. A column rakes back from a cross beam, carrying the back pad and
// the seat's sleeve, and a brace runs from it back to a cross tube at the pivot, 600 mm up. Each side
// a raked upright runs to the pivot and on as an arm forward and down, on a post, with the lever's
// stop at its end. Each lever is a heavy beam from the pivot, sloping down toward the front: a hoop
// rises from it to a grip pointing forward, a second grip runs on from its end, and a plate horn
// stands out of it near the front.
//
// The two levers are one body on a revolute joint about the pivot, its value the angle up from level
// ahead of the line from the pivot to the grips: 7.5° on the stop, where the grips are 700 mm up,
// and 26.3° at the top of its travel, 940 mm up. The frame stands where the ref puts it, whatever the
// pose. The lifter stands at the origin, 100 mm behind the grips' place on the stop, as the photos have it.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { curvedTube, knob, mitered, pad, padGeometry, part, pivotJoint, plateGeometry, rod, tube } from '../parts.mjs';
import { UP, V, X } from '../rig.mjs';

// The levers' pivot, [y, z], and the grips' reach from it: 760 ahead and 100 above on the stop.
const PIVOT = [0.6, -0.66];
const REACH = Math.hypot(0.76, 0.1);
const STOP = Math.asin(0.1 / REACH);
const TOP = Math.asin(0.34 / REACH);
// The grips this far either side of the middle (740 apart), each 195 long, rising a little to its tip.
const GRIPS_OUT = 0.37;
const GRIP = { behind: 0.095, ahead: 0.1, rise: 0.016 };
// The lever's beam: how far it falls toward the front on the stop, its length, height and width.
const BEAM = { slope: THREE.MathUtils.degToRad(13.3), length: 0.63, tall: 0.1, wide: 0.05 };
// The second grip's length off the beam's end. The horn stands `at` that far along the beam, reaches
// `out` from the middle, and carries a plate at `plateAt`.
const LOWER_GRIP = 0.19;
const HORN = { at: 0.585, out: 0.73, plateAt: 0.45 };
// The base: rails this far out, their ends, the tube, and the height of its underside.
const BASE = { out: 0.45, front: 0.09, rear: -0.81, tube: 0.075, clear: 0.038 };
const RAIL_TOP = BASE.clear + BASE.tube;
// The cross beams: one under the column, one at the rear.
const BEAMS_Z = { column: -0.21, rear: BASE.rear + BASE.tube / 2 };
// Each side frame: the upright's foot along the rail, the arm's end forward of the pivot, and the
// post up to it. The storage horn stands on the upright at `STORE.y`, with a bar between the two.
const SIDE = { rear: BEAMS_Z.rear, armEnd: [0.45, -0.41], post: -0.52, tube: 0.07 };
const STORE = { y: 0.41, out: 0.25 };
// The stop each lever rests on, at the arm's end: how far out and along it stands.
const STOP_BLOCK = { x: 0.38, z: SIDE.armEnd[1], wide: 0.14, tall: 0.06, deep: 0.1 };
// The column's rake back from upright and its top, the brace's height on it, and the back pad.
const RAKE = THREE.MathUtils.degToRad(8.8);
const COLUMN = { top: 1.09, tube: 0.075, brace: 0.98 };
const PAD = { top: 1.24, long: 0.56, w: 0.36, deep: 0.08 };
// The seat's top at 650 mm unless set, and its depth and width.
const SEAT = { top: 0.65, deep: 0.26, w: 0.24 };

const beamWay = V(0, -Math.sin(BEAM.slope), Math.cos(BEAM.slope));
/** Up the column, and square off it toward the lifter. */
const columnWay = V(0, Math.cos(RAKE), -Math.sin(RAKE));
const columnFront = V(0, Math.sin(RAKE), Math.cos(RAKE));
/** The column's line at height `y`. */
const columnAt = (y) => V(0, y, BEAMS_Z.column - (y - RAIL_TOP) * Math.tan(RAKE));
/** The grips' centre on side `side` with the levers at `value`. */
const gripAt = (side, value) => V(side * GRIPS_OUT, PIVOT[0] + REACH * Math.sin(value), PIVOT[1] + REACH * Math.cos(value));

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** A part for the checks alone, a straight stand-in inside a mitred tube: the clip leaves it out. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };

export default recipe({
  name: 'shrug_machine',
  bodies: { levers: revolute({ axis: V(-1, 0, 0), limits: [STOP, TOP] }) },
  contacts: [{ body: 'levers', point: 'grips', landmark: 'hands' }],
  rep: { joint: 'levers', from: 'max' },
  linkage: () => ({
    at: (value) => ({
      pivot: V(0, ...PIVOT),
      handle: (side) => gripAt(side, value),
      // Along the grip, the index finger forward.
      across: () => V(0, Math.sin(value - STOP), Math.cos(value - STOP)),
    }),
  }),
  // Only the seat adjusts (the ref's page FAQ); the handles are fixed. Standing, it goes down out of
  // the legs' way.
  fit: [{
    sets: 'seat',
    rule: "the seat top at 650 mm, the ref's measured seat, the one height no photo shows the travel of; lowered for a standing lifter, the legs either side of it",
  }],
  build({ seat: seatTop = SEAT.top } = {}, M) {
    const sides = [-1, 1];
    const frame = [];
    // The base: a rail each side, two cross beams between them, and a foot under each corner.
    const railY = BASE.clear + BASE.tube / 2;
    for (const s of sides) {
      frame.push(role(tube(BASE.tube, V(s * BASE.out, railY, BASE.rear), V(s * BASE.out, railY, BASE.front), M.frame), 'rail'));
      for (const z of [BASE.front - 0.05, BASE.rear + 0.05]) {
        frame.push(role(pad(0.17, BASE.clear + 0.002, 0.1, M.frame, V(s * (BASE.out + 0.02), (BASE.clear + 0.002) / 2, z), { soft: 0.004 }), 'foot'));
      }
    }
    for (const z of Object.values(BEAMS_Z)) frame.push(role(tube(BASE.tube, V(-BASE.out, railY, z), V(BASE.out, railY, z), M.frame), 'crossBeam'));

    // Each side a raked upright, cut flat on the rail, mitred at the pivot into an arm reaching forward
    // and down, a post up to the arm and a stop at its end, the beam lying on it. A tube inside each
    // length is what the body meets.
    const pivotAt = (s) => V(s * BASE.out, PIVOT[0], PIVOT[1]);
    const uprightAt = (y) => BEAMS_Z.rear + (y - RAIL_TOP) / (PIVOT[0] - RAIL_TOP) * (PIVOT[1] - BEAMS_Z.rear);
    const armAt = (z) => PIVOT[0] + (z - PIVOT[1]) / (SIDE.armEnd[1] - PIVOT[1]) * (SIDE.armEnd[0] - PIVOT[0]);
    const beamUnder = PIVOT[0] - (STOP_BLOCK.z - PIVOT[1]) * Math.tan(BEAM.slope) - BEAM.tall / 2 / Math.cos(BEAM.slope);
    for (const s of sides) {
      const foot = V(s * BASE.out, RAIL_TOP, SIDE.rear);
      const armEnd = V(s * BASE.out, ...SIDE.armEnd);
      frame.push(
        role(mitered([foot, pivotAt(s), armEnd], SIDE.tube, M.frame, { solid: false, ends: [UP, null] }), 'sideFrame'),
        proxy(tube(SIDE.tube - 0.01, foot, pivotAt(s), M.frame)),
        proxy(tube(SIDE.tube - 0.01, pivotAt(s), armEnd, M.frame)),
        role(mitered([V(s * BASE.out, RAIL_TOP, SIDE.post), V(s * BASE.out, armAt(SIDE.post), SIDE.post)], SIDE.tube, M.frame, { solid: false, ends: [UP, null] }), 'post'),
        proxy(tube(SIDE.tube - 0.01, V(s * BASE.out, RAIL_TOP + 0.01, SIDE.post), V(s * BASE.out, armAt(SIDE.post), SIDE.post), M.frame)),
        role(pad(STOP_BLOCK.wide, STOP_BLOCK.tall, STOP_BLOCK.deep, M.frame, V(s * STOP_BLOCK.x, beamUnder - STOP_BLOCK.tall / 2, STOP_BLOCK.z), { soft: 0.006 }), 'stop'),
        pad(0.09, 0.1, 0.12, M.frame, pivotAt(s), { soft: 0.01 }),
      );
      const [from, to] = [V(s * BASE.out, STORE.y, uprightAt(STORE.y)), V(s * (BASE.out + STORE.out), STORE.y, uprightAt(STORE.y))];
      frame.push(role(rod(0.025, from, to, M.metal), 'storageHorn'), knob(M, to, 0.03, M.pulley));
    }
    frame.push(role(tube(0.05, V(-BASE.out, STORE.y, uprightAt(STORE.y)), V(BASE.out, STORE.y, uprightAt(STORE.y)), M.frame), 'rearBar'));
    frame.push(role(tube(0.06, pivotAt(-1), pivotAt(1), M.frame), 'crossTube'));

    // The column, cut flat on its cross beam, the pad flush on its front, and the brace back from it.
    const foot = columnAt(RAIL_TOP);
    const top = columnAt(COLUMN.top);
    const padOff = COLUMN.tube / 2 + PAD.deep / 2;
    const padMiddle = columnAt(PAD.top - (PAD.long / 2) * columnWay.y - padOff * columnFront.y).addScaledVector(columnFront, padOff);
    const back = part(padGeometry(PAD.w, PAD.long, PAD.deep, 0.025), M.vinyl, { solid: true });
    back.position.copy(padMiddle);
    back.rotation.x = -RAKE;
    frame.push(
      role(mitered([foot, top], COLUMN.tube, M.frame, { solid: false, ends: [UP, null] }), 'column'),
      proxy(tube(COLUMN.tube - 0.01, foot, top, M.frame)),
      role(back, 'backPad'),
      role(tube(0.05, columnAt(COLUMN.brace), V(0, ...PIVOT), M.frame), 'brace'),
    );

    // The seat on a bracket from a sleeve on the column, its pin on the sleeve.
    const seatZ = columnAt(seatTop).z + COLUMN.tube / 2 + SEAT.deep / 2 + 0.01;
    const under = seatTop - 0.09;
    frame.push(
      role(pad(SEAT.w, 0.075, SEAT.deep, M.vinyl, V(0, seatTop - 0.0375, seatZ), { soft: 0.024 }), 'seat'),
      pad(SEAT.w - 0.06, 0.012, SEAT.deep - 0.06, M.frame, V(0, seatTop - 0.081, seatZ), { soft: 0.003 }),
      role(tube(0.1, columnAt(seatTop - 0.25), columnAt(seatTop + 0.02), M.frame), 'seatSleeve'),
      tube(0.05, columnAt(under), V(0, under, seatZ), M.frame),
      knob(M, columnAt(under - 0.08).setX(0.07), 0.02),
    );

    // Each lever on the stop, from the pivot: the beam, the hoop and strut up from it to the grip, the
    // second grip off its end, and the horn out of it with a plate on.
    const levers = new THREE.Group();
    levers.position.set(0, ...PIVOT);
    const grips = sides.flatMap((s) => {
      const x = s * GRIPS_OUT;
      const local = (y, z) => V(x, y, z);
      const beam = pad(BEAM.wide, BEAM.tall, BEAM.length, M.frame, local(0, 0).addScaledVector(beamWay, BEAM.length / 2), { soft: 0.006 });
      beam.rotation.x = BEAM.slope;
      const end = local(0, 0).addScaledVector(beamWay, BEAM.length);
      const lowerEnd = end.clone().addScaledVector(beamWay, LOWER_GRIP);
      const hornAt = local(0, 0).addScaledVector(beamWay, HORN.at);
      const hornEnd = hornAt.clone().setX(s * HORN.out);
      const centre = local(REACH * Math.sin(STOP), REACH * Math.cos(STOP));
      const heel = centre.clone().add(V(0, -GRIP.rise / 2, -GRIP.behind));
      const tip = centre.clone().add(V(0, GRIP.rise / 2, GRIP.ahead));
      const plate = part(plateGeometry(0.17, 0.04), M.plate, { solid: true });
      plate.position.copy(hornAt).setX(s * HORN.plateAt);
      const bends = [local(-0.075, 0.47), local(0.02, 0.495), local(0.075, 0.55), local(heel.y, 0.62), heel];
      const hoop = curvedTube(bends, 0.03, M.frame);
      return [
        // A tube inside each length of the hoop is what the body meets.
        ...bends.slice(1).map((p, i) => proxy(tube(0.025, bends[i], p, M.frame))),
        role(beam, 'beam'),
        role(hoop, 'hoop'),
        tube(0.035, local(-0.14, 0.6), heel, M.frame),
        rod(0.0175, heel, tip, M.vinyl, { held: true }),
        knob(M, heel, 0.0225, M.frame),
        knob(M, tip, 0.0175),
        rod(0.0175, end, lowerEnd, M.vinyl, { solid: true }),
        knob(M, lowerEnd, 0.0175),
        role(rod(0.025, hornAt, hornEnd, M.metal, { solid: true }), 'horn'),
        knob(M, hornEnd, 0.03, M.pulley),
        plate,
      ];
    });
    levers.add(...grips);
    // Each lever turns in a housing on the cross tube.
    const pivots = sides.map((s) => pivotJoint(M, V(s * GRIPS_OUT, 0, 0), X));
    levers.add(...pivots.map((p) => p.housing));
    return {
      parts: [...frame, levers],
      bodies: { levers: [levers] },
      drive(P) {
        const value = P.linkage?.value ?? P.levers ?? STOP;
        levers.rotation.x = -(value - STOP);
        return {
          joints: { levers: { value, origin: V(0, ...PIVOT) } },
          points: { grips: sides.map((s) => gripAt(s, value)) },
        };
      },
    };
  },
});
