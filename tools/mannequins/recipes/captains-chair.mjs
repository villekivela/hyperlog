// A captain's chair (refs/captains_chair/ref.md): two floor rails 640 apart and 1054 long, an X of two
// tubes in each side plane above them (a straight one from the rail's front half up to a rear post, a bent one
// from its rear half up and forward and upright under the arm pad's front), a foot brace between the rails, an
// upright back pad reclined 10° on a tube across the rear posts, two flat arm pads on the side frames, and a
// knee raise grip rising from a hub at each pad's front end, leaning back and inward, with a dip handle
// pointing forward from the hub (served by the power rack, not by this kit). A foot peg points forward from
// each side's straight tube. Nothing on it moves.
//
// The kit's z is the ref's "from the rear" less ELBOW: the elbows rest on the arm pads' rear halves at z 0,
// under the shoulders, and the lifter faces +z with the back against the back pad.
import * as THREE from 'three';
import { recipe } from '../recipe.mjs';
import { knob, mitered, pad, part, rod, tube } from '../parts.mjs';
import { V } from '../rig.mjs';

const ELBOW = 0.41;
/** A point `r` metres from the rear, `y` up, `x` across. */
const at = (x, r, y) => V(x, y, r - ELBOW);
const SIDE = 0.32;
const RAIL = { length: 1.054, width: 0.1, height: 0.06 };
// The arm pads: 250 long from 330 from the rear, 120 wide, 80 thick, tops at 1380, their middles 560 apart.
const ARM = { from: 0.33, length: 0.25, width: 0.12, thick: 0.08, top: 1.38, x: 0.28 };
// The back pad: 290 wide and 550 tall from 1000 up, 70 thick, reclined 10°, its face 360 from the rear at its bottom edge.
const BACK = { width: 0.29, height: 0.55, bottom: 1.0, face: 0.36, thick: 0.07, recline: (10 * Math.PI) / 180 };
// The grip: 33 mm across, from a hub 700 from the rear and 1320 up, 230 long, leaning back 15° and inward (base 570 apart, top 500).
const GRIP = { radius: 0.0165, hub: { r: 0.70, y: 1.32, x: 0.285 }, top: { r: 0.64, y: 1.55, x: 0.25 }, hand: 0.478 };
const DIP = { r: 0.93, y: 1.2, x: 0.30 };
// A rubber cap closes each rail's end.
const CAP = 0.016;
const PEG = { x: 0.325, y: 0.28, from: 0.58, to: 0.76, radius: 0.025 };
// Both tubes stand on the rail, their flat ends cut level and sunk 15 mm into it, side by side across its width.
const SUNK = RAIL.height - 0.015;
const LANE = 0.0175;
// The straight tube's ends: on the rail's front half and the rear post's top, behind the back pad's face.
const POST = { foot: { r: 0.74, y: SUNK }, top: { r: 0.20, y: 1.27 } };
// The bent tube, from the rail's rear half up and forward, round to upright under the hub.
const BEND = [{ r: 0.14, y: SUNK }, { r: 0.58, y: 0.88 }, { r: 0.65, y: 1.0 }, { r: 0.69, y: 1.1 }, { r: 0.70, y: 1.2 }, { r: 0.70, y: 1.27 }];
// The frame's tubes are 65 mm across (the photos), the top rails and the back support's cross tube as thick.
const TUBE = 0.065;

/**
 * What the lifter meets: where an elbow rests on each pad (`elbow(side)`, the pad's middle across, its
 * top plus the joint's lift, the way along the pad left to the pose, which finds it from the hands),
 * the grips (`at(side)`, where the hand closes, and `across(side)`, up the grip, which the thumb lies
 * along), the chair's back pad's lean, and the elbow's lift above a pad's top.
 */
export const HOLD = {
  padX: ARM.x,
  padTop: ARM.top,
  elbowLift: 0.04,
  at: (side) => at(side * (GRIP.hub.x + (GRIP.top.x - GRIP.hub.x) * GRIP.hand), GRIP.hub.r + (GRIP.top.r - GRIP.hub.r) * GRIP.hand, GRIP.hub.y + (GRIP.top.y - GRIP.hub.y) * GRIP.hand),
  across: (side) => at(side * GRIP.top.x, GRIP.top.r, GRIP.top.y).sub(at(side * GRIP.hub.x, GRIP.hub.r, GRIP.hub.y)).normalize(),
};

export default recipe({
  name: 'captains_chair',
  bodies: {},
  contacts: [
    { body: 'frame', point: 'armPads', landmark: 'forearms' },
    { body: 'frame', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'backPad', landmark: 'back' },
  ],
  build(_options, M) {
    const sides = [-1, 1];
    const lean = Math.tan(BACK.recline);
    // The back pad's face at height y, from the rear.
    const faceAt = (y) => BACK.face - (y - BACK.bottom) * lean;
    const middle = BACK.bottom + BACK.height / 2;
    const normal = V(0, Math.sin(BACK.recline), Math.cos(BACK.recline));
    const back = pad(BACK.width, BACK.height, BACK.thick, M.vinyl, at(0, faceAt(middle), middle).addScaledVector(normal, -BACK.thick / 2), { soft: 0.025 });
    back.rotation.x = -BACK.recline;
    const backBoard = pad(BACK.width - 0.04, BACK.height - 0.04, 0.012, M.frame, at(0, faceAt(middle), middle).addScaledVector(normal, -(BACK.thick + 0.006)), { soft: 0.003 });
    backBoard.rotation.x = -BACK.recline;
    // The upright's middle line, behind the pad's board, at height y.
    const upright = (y) => at(0, faceAt(y) - BACK.thick / Math.cos(BACK.recline) - 0.012 - 0.03, y);
    const hand = HOLD.at;
    const armMiddle = ARM.from + ARM.length / 2;
    const parts = [
      back, backBoard,
      // The back support: a cross tube between the rear posts' tops and one upright from its middle, leaning
      // back with the pad bolted to its face.
      tube(TUBE, at(-SIDE, POST.top.r, POST.top.y), at(SIDE, POST.top.r, POST.top.y), M.frame),
      mitered([upright(BACK.bottom + 0.28), upright(BACK.bottom + 0.44)], 0.05, M.frame),
      // The foot brace.
      tube(0.05, at(-SIDE, 0.55, 0.06), at(SIDE, 0.55, 0.06), M.frame),
      ...sides.flatMap((s) => {
        const x = s * SIDE;
        const hub = at(s * GRIP.hub.x, GRIP.hub.r, GRIP.hub.y);
        const top = at(s * GRIP.top.x, GRIP.top.r, GRIP.top.y);
        const tip = at(s * DIP.x, DIP.r, DIP.y);
        const peg = rod(PEG.radius, at(s * PEG.x, PEG.from, PEG.y), at(s * PEG.x, PEG.to, PEG.y), M.vinyl);
        peg.userData.peg = true;
        return [
          pad(RAIL.width, RAIL.height, RAIL.length - 2 * CAP, M.frame, at(x, RAIL.length / 2, RAIL.height / 2), { soft: 0.02 }),
          ...[0, RAIL.length].map((r) => pad(0.016, RAIL.height, RAIL.width, M.vinyl, at(x, r ? RAIL.length - CAP / 2 : CAP / 2, RAIL.height / 2), { soft: 0.005, solid: false }).rotateY(Math.PI / 2)),
          // The straight tube, up and back to the rear post.
          mitered([at(x + s * LANE, POST.foot.r, POST.foot.y), at(x + s * LANE, POST.top.r, POST.top.y)], TUBE, M.frame, { ends: [V(0, 1, 0)] }),
          // The bent tube, inside it, up and forward and upright under the arm pad's front end.
          mitered(BEND.map(({ r, y }) => at(x - s * LANE, r, y)), TUBE, M.frame, { ends: [V(0, 1, 0)] }),
          // The pad's support, from the rear post to the hub.
          mitered([at(x, POST.top.r, ARM.top - ARM.thick - TUBE / 2), at(x, GRIP.hub.r, ARM.top - ARM.thick - TUBE / 2)], TUBE, M.frame),
          pad(ARM.width, ARM.thick, ARM.length, M.vinyl, at(s * ARM.x, armMiddle, ARM.top - ARM.thick / 2), { soft: 0.02 }),
          peg,
          // The knee raise grip, and the dip handle forward from the hub.
          rod(GRIP.radius, hub, top, M.vinyl, { held: true }),
          knob(M, top, GRIP.radius * 1.15),
          rod(GRIP.radius, hub, tip, M.vinyl, { held: true }),
          knob(M, tip, GRIP.radius * 1.15),
          part(new THREE.SphereGeometry(0.03, 14, 10), M.pulley).translateX(hub.x).translateY(hub.y).translateZ(hub.z),
        ];
      }),
    ];
    const points = {
      armPads: sides.map((s) => at(s * ARM.x, ARM.from + 0.12, ARM.top)),
      grips: sides.map(hand),
      backPad: [at(0, faceAt(middle), middle)],
    };
    return {
      parts,
      drive() {
        return { points: Object.fromEntries(Object.entries(points).map(([k, v]) => [k, v.map((p) => p.clone())])) };
      },
    };
  },
});
