// A 45° leg press: the seat and back pad (its face `pad` metres behind the spine), the rails, and
// the sled with its foot plate, which rides the rails with the feet, loaded with a plate a side.
// `P.plate` is the foot plate's centre, `P.sled` the sled's way up the rails and `P.plateUp` the
// way up the plate's face.
//
// The sled is a body on a prismatic joint up the rails, its value how far its plate is above where
// it is at the bottom of the rep.
import * as THREE from 'three';
import { recipe, prismatic } from '../recipe.mjs';
import {
  backPad, endCap, orient, pad, part, plateGeometry, rodGeometry, span, stretchedTube, tube, tubeGeometry, turnedPart, wheelGeometry,
} from '../parts.mjs';
import { V, X, UP, rad } from '../rig.mjs';

export default recipe({
  name: 'leg_press',
  bodies: { sled: prismatic({ axis: V(0, Math.SQRT1_2, Math.SQRT1_2), limits: [0, 0.45] }) },
  contacts: [
    { body: 'sled', point: 'plate', landmark: 'feet' },
    { body: 'frame', point: 'handles', landmark: 'hands' },
  ],
  fit: [{ sets: 'pad', rule: 'the back pad at the spine, the hips deep in the seat' }],
  build({ seat = [0.5, 0.04], pad: behind = 0.2 } = {}, M) {
    const backRest = backPad(0.42, 0.72, M, { up: 0.33, behind });
    const tilt = rad(-18);
    const seatPad = pad(0.42, 0.09, 0.38, M.vinyl, V(0, seat[0] - 0.03, seat[1]), { soft: 0.025 });
    seatPad.rotation.x = tilt;
    const seatBoard = pad(0.38, 0.012, 0.34, M.frame, V(0, seat[0] - 0.03, seat[1]).addScaledVector(V(0, Math.cos(tilt), Math.sin(tilt)), -0.051), { soft: 0.003, solid: false });
    seatBoard.rotation.x = tilt;
    // The base: a rail either side along the floor, tied across at the ends and under the seat, with
    // caps on the ends.
    const [back, front] = [-0.6, 1.7];
    const parts = [
      seatPad, seatBoard, backRest.mesh,
      ...[-0.3, 0.3].map((x) => tube(0.07, V(x, 0.035, back), V(x, 0.035, front), M.frame)),
      ...[back, seat[1] - 0.05, front].map((z) => tube(0.07, V(-0.38, 0.035, z), V(0.38, 0.035, z), M.frame)),
      ...[back, front].flatMap((z) => [endCap(-0.38, z, M), endCap(0.38, z, M)]),
      tube(0.08, V(0, 0.07, seat[1] - 0.05), V(0, seat[0] - 0.1, seat[1] - 0.05), M.frame),
    ];
    // Along the sled, from 70 cm below the foot plate's start to 45 cm above it: the rails, each on
    // a post at its foot and an upright at its head, with a brace, tied across at both ends.
    const RAIL = 1.15;
    const rails = [0, 1].map(() => part(tubeGeometry(0.06, RAIL), M.frame, { solid: true }));
    const posts = [0, 1].map(() => stretchedTube(0.06, M.frame));
    const uprights = [0, 1].map(() => stretchedTube(0.07, M.frame));
    const braces = [0, 1].map(() => stretchedTube(0.05, M.frame));
    const ties = [0, 1].map(() => stretchedTube(0.06, M.frame));
    // The carriage: a side plate over each rail, riding on two rollers a side, tied across under the
    // foot plate, which has a lip at its heel.
    const sides = [0, 1].map(() => pad(0.012, 0.14, 0.5, M.frame, V(0, 0, 0), { soft: 0.003 }));
    const rollers = [0, 1, 2, 3].map(() => turnedPart(wheelGeometry(0.035, 0.03), M.pulley, V(0, 0, 0), { solid: false }));
    const axles = [0, 1].map(() => part(rodGeometry(0.008, 10), M.metal));
    const cross = stretchedTube(0.06, M.frame);
    const plate = pad(0.62, 0.04, 0.52, M.frame, V(0, 0, 0), { soft: 0.008 });
    const lip = pad(0.62, 0.06, 0.03, M.frame, V(0, 0, 0), { soft: 0.006, solid: false });
    const horns = [0, 1].map(() => part(rodGeometry(0.025), M.metal));
    const weights = [0, 1].map(() => part(plateGeometry(0.17, 0.04), M.plate, { solid: true }));
    const collars = [0, 1].map(() => part(rodGeometry(0.034), M.metal));
    const handles = [0, 1].map(() => part(rodGeometry(0.016), M.metal, { held: true }));
    const grips = [0, 1].map(() => part(rodGeometry(0.0195), M.vinyl, { held: true }));
    const handlePosts = [0, 1].map(() => stretchedTube(0.04, M.frame));
    const support = stretchedTube(0.05, M.frame);
    return {
      parts: [
        ...parts, ...rails, ...posts, ...uprights, ...braces, ...ties, ...sides, ...rollers, ...axles, cross, plate, lip,
        ...horns, ...weights, ...collars, ...handles, ...grips, ...handlePosts, support,
      ],
      bodies: { sled: [...sides, ...rollers, ...axles, cross, plate, lip, ...horns, ...weights, ...collars] },
      drive(P, P0, P1) {
        const board = backRest.fit(P0);
        // The back pad's support down from its board to the base's back tie.
        span(support, board, V(0, 0.07, back));
        const along = P.sled;
        const up = P.plateUp;
        const under = P0.plate.clone().addScaledVector(up, -0.3);
        const lo = under.clone().addScaledVector(along, -0.7);
        const hi = under.clone().addScaledVector(along, 0.45);
        [-1, 1].forEach((s, i) => {
          orient(rails[i], lo.clone().setX(s * 0.24), hi.clone().setX(s * 0.24));
          span(posts[i], lo.clone().setX(s * 0.24), V(s * 0.3, 0.07, lo.z));
          span(uprights[i], hi.clone().setX(s * 0.24).addScaledVector(along, 0.03), V(s * 0.3, 0.07, hi.z));
          span(braces[i], lo.clone().lerp(hi, 0.45).setX(s * 0.24), V(s * 0.3, 0.07, hi.z - 0.35));
          const end = i ? hi : lo;
          span(ties[i], end.clone().setX(-0.27), end.clone().setX(0.27));
          // The carriage rides the rail: its rollers on the rail's top, its side plate above them.
          const on = P.plate.clone().addScaledVector(up, -0.3 + 0.03 + 0.035).setX(s * 0.24);
          rollers[2 * i].position.copy(on).addScaledVector(along, -0.05);
          rollers[2 * i + 1].position.copy(on).addScaledVector(along, 0.32);
          sides[i].position.copy(P.plate).addScaledVector(along, 0.16).addScaledVector(up, -0.17).setX(s * 0.28);
          sides[i].quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, up, along));
          const horn = P.plate.clone().addScaledVector(along, 0.3).addScaledVector(up, -0.22);
          span(horns[i], horn.clone().setX(s * 0.29), horn.clone().setX(s * 0.5));
          weights[i].position.copy(horn).setX(s * 0.36);
          span(collars[i], horn.clone().setX(s * 0.39), horn.clone().setX(s * 0.41));
          const h = P.hand(s);
          span(handles[i], h.clone().setZ(h.z - 0.09), h.clone().setZ(h.z + 0.09));
          span(grips[i], h.clone().setZ(h.z - 0.06), h.clone().setZ(h.z + 0.06));
          span(handlePosts[i], h.clone().setZ(h.z - 0.08), V(s * 0.3, 0.07, h.z - 0.08));
        });
        [-0.05, 0.32].forEach((t, i) => {
          const c = P.plate.clone().addScaledVector(up, -0.235).addScaledVector(along, t);
          span(axles[i], c.clone().setX(-0.3), c.clone().setX(0.3));
        });
        const mid = P.plate.clone().addScaledVector(along, 0.16).addScaledVector(up, -0.17);
        span(cross, mid.clone().setX(-0.28), mid.clone().setX(0.28));
        // The plate's face is square to the sled: its thickness runs along the sled.
        const q = new THREE.Quaternion().setFromUnitVectors(UP, along.clone().negate());
        plate.position.copy(P.plate).addScaledVector(along, 0.02);
        plate.quaternion.copy(q);
        lip.position.copy(P.plate).addScaledVector(along, -0.02).addScaledVector(up, -0.245);
        lip.quaternion.copy(q);
        return {
          joints: { sled: { value: P.plate.clone().sub(P1.plate).dot(along), origin: P.plate.clone(), axis: along.clone() } },
          points: { plate: [P.plate.clone()], handles: [-1, 1].map((s) => P.hand(s).clone()) },
        };
      },
    };
  },
});
