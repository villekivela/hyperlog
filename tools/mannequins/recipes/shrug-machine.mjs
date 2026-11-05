// A plate-loaded shrug machine (refs/shrug_machine/ref.md): the lifter stands, or sits on a seat at
// 650 mm, between two levers, a handle at each side. Each lever turns about one transverse axis at
// the top of a rear upright, 600 mm up and 760 mm behind the grips, and carries a handle post rising
// 270 mm with the grip pointing forward, and a plate horn under the post. A pad on a column at the
// back, topping out at 1.24 m, takes the lifter's back; the floor frame is two side beams and a rear
// cross beam, the whole 1.02 m deep.
//
// The two levers are one body on a revolute joint about the pivot, its value the angle up from level
// ahead of the line from the pivot to the grips: 7.5° on the stop, where the grips are 700 mm up,
// and 26.3° at the top of a shrug, 940 mm up. The frame stands where the ref puts it, whatever the
// pose: `P.levers` is the joint's value, or the levers rest on their stop where the pose frame
// gives none.
import { recipe, revolute } from '../recipe.mjs';
import { floorRail, hub, padGeometry, part, plateGeometry, rod, rodGeometry, seatOn, span, stretchedTube, tube } from '../parts.mjs';
import { V } from '../rig.mjs';

// The levers' pivot, [y, z], and the grips' reach from it: 760 ahead and 100 above on the stop.
const PIVOT = [0.6, -0.76];
const REACH = Math.hypot(0.76, 0.1);
const STOP = Math.asin(0.1 / REACH);
const TOP = Math.asin(0.34 / REACH);
// The grips this far either side of the middle (740 apart), a grip 14 cm long, on a post of 27 cm.
const GRIPS_OUT = 0.37;
const GRIP = 0.14;
const POST = 0.27;
// The rear uprights and the side beams stand this far either side of the middle.
const FRAME_OUT = 0.45;
// The seat's top at 650 mm, a seat's depth behind the grips' line, the back pad's face behind it.
const SEAT = [0.65, -0.1];
const FRONT = 0.22;
const PAD = { top: 1.24, long: 0.5, w: 0.36, deep: 0.08, z: -0.36 };
// The horns reach to 760 either side of the middle, with a plate on each at `plateAt`.
const HORN = { out: 0.76, plateAt: 0.45 };

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

export default recipe({
  name: 'shrug_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's
  // photos are.
  standsAlone: { camera: { target: [0, 0.65, -0.3], halfHeight: 1.0, azimuth: -40, elevation: 14 } },
  bodies: { levers: revolute({ axis: V(-1, 0, 0), limits: [STOP, TOP] }) },
  contacts: [{ body: 'levers', point: 'grips', landmark: 'hands' }],
  // Only the seat adjusts (the ref's page FAQ); the handles are fixed. Seated, the shoulders sit
  // about 0.63 m above the grips, which puts the seat near its top; standing needs none.
  fit: [{
    sets: 'seat',
    rule: "the seat top at 650 mm, the ref's measured seat, the one height no photo shows the travel of; a seated lifter's shoulders 0.63 m above the grips",
  }],
  build({ seat: seatTop = SEAT[0] } = {}, M) {
    const sides = [-1, 1];
    const frame = [];
    // The rear cross beam and a side beam each way, forward to the front feet; a post up from the
    // cross beam at each end carries the pivot.
    frame.push(...floorRail(V(-FRAME_OUT, 0.035, PIVOT[1]), V(FRAME_OUT, 0.035, PIVOT[1]), M));
    for (const s of sides) {
      frame.push(...floorRail(V(s * FRAME_OUT, 0.035, PIVOT[1]), V(s * FRAME_OUT, 0.035, FRONT), M));
      frame.push(role(tube(0.07, V(s * FRAME_OUT, 0.07, PIVOT[1]), V(s * FRAME_OUT, PIVOT[0] + 0.05, PIVOT[1]), M.frame), 'upright'));
    }
    // The pad's column up the middle of the cross beam, braced forward to the pad's board.
    const padY = PAD.top - PAD.long / 2;
    frame.push(tube(0.07, V(0, 0.07, PIVOT[1]), V(0, PAD.top - 0.1, PIVOT[1]), M.frame));
    frame.push(tube(0.05, V(0, PAD.top - 0.1, PIVOT[1]), V(0, PAD.top - 0.1, PAD.z - PAD.deep / 2), M.frame));
    const back = part(padGeometry(PAD.w, PAD.long, PAD.deep, 0.025), M.vinyl, { solid: true });
    back.position.set(0, padY, PAD.z);
    frame.push(role(back, 'backPad'));
    // The seat on its post, a beam under it along the middle.
    const [cushion, ...seat] = seatOn(seatTop, SEAT[1], M, { pin: 1 });
    frame.push(role(cushion, 'seat'), ...seat);
    frame.push(...floorRail(V(0, 0.035, PIVOT[1]), V(0, 0.035, SEAT[1] + 0.1), M));
    // The pivot's axle between the uprights, behind the levers.
    frame.push(role(rod(0.022, V(-FRAME_OUT, PIVOT[0], PIVOT[1]), V(FRAME_OUT, PIVOT[0], PIVOT[1]), M.metal), 'axle'));

    const levers = sides.map((s) => ({
      s,
      boss: hub(M, V(s * GRIPS_OUT, PIVOT[0], PIVOT[1])),
      arm: stretchedTube(0.05, M.frame),
      post: stretchedTube(0.04, M.frame),
      grip: part(rodGeometry(0.0175), M.vinyl, { held: true }),
      horn: role(part(rodGeometry(0.025), M.metal), 'horn'),
      plate: part(plateGeometry(0.17, 0.04), M.plate, { solid: true }),
    }));
    const moving = levers.flatMap((l) => [l.boss, l.arm, l.post, l.grip, l.horn, l.plate]);
    return {
      parts: [...frame, ...moving],
      bodies: { levers: moving },
      drive(P) {
        const value = P.levers ?? STOP;
        const pivot = V(0, ...PIVOT);
        const ahead = V(0, Math.sin(value), Math.cos(value));
        const up = V(0, Math.cos(value), -Math.sin(value));
        const centre = pivot.clone().addScaledVector(ahead, REACH);
        const points = [];
        for (const l of levers) {
          const at = (p) => p.clone().setX(l.s * GRIPS_OUT);
          const grip = at(centre);
          const heel = grip.clone().addScaledVector(ahead, -GRIP / 2);
          const foot = heel.clone().addScaledVector(up, -POST);
          l.boss.position.copy(at(pivot));
          span(l.arm, at(pivot), foot);
          span(l.post, foot, heel);
          span(l.grip, heel, grip.clone().addScaledVector(ahead, GRIP / 2));
          span(l.horn, foot, foot.clone().setX(l.s * HORN.out));
          l.plate.position.copy(foot).setX(l.s * HORN.plateAt);
          points.push(grip);
        }
        return { joints: { levers: { value, origin: pivot } }, points: { grips: points } };
      },
    };
  },
});
