// A selectorized shoulder press (refs/shoulder_press/ref.md): the lifter sits on a seat at 350 mm
// against a back pad reclined 10°, 830 mm long and topping out at 1.27 m. A yoke of two side arms,
// joined by a crossbar behind the pad, pivots about one transverse axis at grip height, 980 mm up;
// each arm runs forward 900 mm past the shoulder to a bar grip (overhand) with a neutral grip beside
// it. The weight stack stands in its own frame beside the lifter, on their right, level with the seat
// and clear of the legs (the ref's photos and video); a crank behind the pivot draws a cable down to the floor,
// along it and out to the stack, so the stack rises as the yoke does. The whole stands 1.5 m deep,
// 1.63 m tall.
//
// The yoke is one body on a revolute joint about the pivot, its value the angle up from level ahead of
// the line from the pivot to the grips: 0° at the bottom of the rep, the grips level with the
// shoulders at 980 mm, to 40° at the top, 580 mm higher and about 200 mm further back.
// `P.yoke` is the joint's value; the yoke rests at the bottom where the pose frame gives none.
import { recipe, revolute } from '../recipe.mjs';
import {
  WHEEL, floorRail, hub, padGeometry, part, pulley, rodGeometry, seatOn, span, stackFrame, stretchedTube, tube,
  weightStack,
} from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The yoke's pivot, [y, z], and the grips' reach from it.
const PIVOT = [0.98, -0.75];
const REACH = 0.9;
const TOP = Math.asin(0.58 / REACH);
// The hands' neutral grips this far either side of the middle (700 apart), the arms outside them.
const GRIPS_OUT = 0.35;
const ARMS_OUT = 0.45;
const NEUTRAL = 0.12;
const BAR = 0.1;
// The seat's top, [y, z]: the grips are level with the shoulders at the start, 0.63 m above it.
const SEAT = [0.35, 0.02];
const PAD = { top: 1.27, long: 0.83, w: 0.44, deep: 0.1, z: -0.27, lean: rad(10) };
// The stack beside the lifter (their right is -x), in its frame up to 1.58 m (1.63 m with its cap), and the crank behind the pivot.
const STACK = [-0.78, 0.12];
const FRONT = 0.55;
const FRAME_TOP = 1.58;
const CRANK = 0.12;

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

export default recipe({
  name: 'shoulder_press',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's hero
  // photo is.
  standsAlone: { camera: { target: [0, 0.8, -0.1], halfHeight: 1.25, azimuth: -40, elevation: 14 } },
  bodies: { yoke: revolute({ axis: V(-1, 0, 0), limits: [0, TOP] }) },
  contacts: [{ body: 'yoke', point: 'grips', landmark: 'hands' }],
  stacks: [{ joint: 'yoke', ratio: 0.2 }],
  // Only the seat adjusts (a lever under its front); the grips are fixed to the frame.
  fit: [{
    sets: 'seat',
    rule: "the grips level with the shoulders at the bottom of the rep, the seat top 0.63 m (a seated lifter's shoulder) below them: 350 mm, near the low end of 330 to 500",
  }],
  build({ seat: seatTop = SEAT[0] } = {}, M) {
    const frame = [];
    // The floor beam down the middle from the pivot's cross beam to the front foot, and the stack's
    // own beam out to its frame across the pivot's.
    frame.push(...floorRail(V(0, 0.035, PIVOT[1]), V(0, 0.035, FRONT), M));
    frame.push(...floorRail(V(-0.4, 0.035, PIVOT[1]), V(0.4, 0.035, PIVOT[1]), M));
    frame.push(...floorRail(V(-0.4, 0.035, FRONT), V(0.4, 0.035, FRONT), M));
    frame.push(...floorRail(V(STACK[0], 0.035, STACK[1] - 0.1), V(STACK[0], 0.035, FRONT), M));
    frame.push(...floorRail(V(STACK[0], 0.035, FRONT), V(-0.4, 0.035, FRONT), M));
    // A column up from the cross beam behind the pad to the pivot box, braced to the pad's board.
    frame.push(tube(0.14, V(0, 0.07, PIVOT[1]), V(0, PIVOT[0] + 0.12, PIVOT[1]), M.frame));
    // The raked leg from the column's top down and forward to the seat's base, as the photos show.
    frame.push(tube(0.1, V(0, PIVOT[0] - 0.1, PIVOT[1] + 0.05), V(0, 0.1, SEAT[1] - 0.05), M.frame));
    const padAt = V(0, PAD.top - (PAD.long / 2) * Math.cos(PAD.lean), PAD.z);
    const back = part(padGeometry(PAD.w, PAD.long, PAD.deep, 0.025), M.vinyl, { solid: true });
    back.position.copy(padAt);
    // Reclined: the top further back.
    back.rotation.x = -PAD.lean;
    frame.push(role(back, 'backPad'));
    frame.push(tube(0.05, V(0, padAt.y, PIVOT[1]), V(0, padAt.y, padAt.z - PAD.deep / 2), M.frame));
    const [cushion, ...seatParts] = seatOn(seatTop, SEAT[1], M, { pin: 1 });
    frame.push(role(cushion, 'seat'), ...seatParts);
    // The weight stack in its frame, its plates facing forward.
    const weights = weightStack(V(STACK[0], 0, STACK[1]), M, { rodTop: FRAME_TOP - 0.05, toward: 1 });
    role(weights.parts.find((p) => p.userData.stack), 'stackHead');
    const standing = stackFrame(STACK[1], FRAME_TOP, M);
    for (const part of standing) part.position.x += STACK[0];
    frame.push(...standing, ...weights.parts);
    // The cable's two floor pulleys, under the crank's tip at rest and at the stack's foot, and its
    // three runs: down from the crank, along the floor, and up to the head.
    const low = [V(0, 0.1, PIVOT[1] - CRANK), V(STACK[0], 0.1, STACK[1])];
    frame.push(...low.flatMap((at) => pulley(at, M)));
    const [down, along, rise] = [0, 1, 2].map(() => part(rodGeometry(0.0045, 8), M.metal));
    frame.push(down, along, rise);
    const sides = [-1, 1];
    const crossbar = stretchedTube(0.09, M.metal);
    const crank = stretchedTube(0.06, M.metal);
    const knob = hub(M, V(0, 0, 0));
    const arms = sides.map((s) => ({
      s,
      boss: hub(M, V(s * ARMS_OUT, PIVOT[0], PIVOT[1])),
      arm: stretchedTube(0.09, M.metal),
      // The overhand bar across the arm's end, and the neutral grip beside it, pointing forward.
      bar: role(part(rodGeometry(0.0175), M.vinyl, { held: true }), 'barGrip'),
      neutral: role(part(rodGeometry(0.0175), M.vinyl, { held: true }), 'neutralGrip'),
      stub: part(rodGeometry(0.02), M.metal),
    }));
    const moving = [crossbar, crank, knob, ...arms.flatMap((a) => [a.boss, a.arm, a.bar, a.neutral, a.stub])];
    const pivot = V(0, ...PIVOT);
    return {
      parts: [...frame, ...moving],
      bodies: { yoke: moving },
      stacks: {
        yoke: {
          lift(l) {
            weights.lift(l);
            span(rise, low[1].clone().setY(0.1 + WHEEL), weights.top(l));
          },
        },
      },
      follow() {
        span(down, knob.position, low[0].clone().setY(0.1 + WHEEL));
      },
      drive(P) {
        const value = P.yoke ?? 0;
        const ahead = V(0, Math.sin(value), Math.cos(value));
        // The grips' middle is REACH out; the arm ends at the neutral grip's front.
        const end = pivot.clone().addScaledVector(ahead, REACH + NEUTRAL / 2);
        const points = [];
        span(crossbar, pivot.clone().setX(-ARMS_OUT), pivot.clone().setX(ARMS_OUT));
        const tip = pivot.clone().addScaledVector(ahead, -CRANK);
        span(crank, pivot, tip);
        knob.position.copy(tip);
        for (const a of arms) {
          const at = (p, x) => p.clone().setX(a.s * x);
          const gripMiddle = end.clone().addScaledVector(ahead, -NEUTRAL / 2);
          a.boss.position.copy(at(pivot, ARMS_OUT));
          span(a.arm, at(pivot, ARMS_OUT), at(end.clone().addScaledVector(ahead, 0.05), ARMS_OUT));
          // The neutral grip inboard of the arm, joined to it by a stub at its front end.
          span(a.neutral, at(end.clone().addScaledVector(ahead, -NEUTRAL), GRIPS_OUT), at(end, GRIPS_OUT));
          span(a.stub, at(end, ARMS_OUT), at(end, GRIPS_OUT));
          // The bar grip across the end, beyond the neutral grip.
          const tipAt = end.clone().addScaledVector(ahead, 0.05);
          span(a.bar, at(tipAt, ARMS_OUT), at(tipAt, ARMS_OUT - BAR));
          points.push(at(gripMiddle, GRIPS_OUT));
        }
        span(down, knob.position, low[0].clone().setY(0.1 + WHEEL));
        span(along, low[0].clone().setY(0.1 + WHEEL), low[1].clone().setY(0.1 + WHEEL));
        weights.lift(0);
        span(rise, low[1].clone().setY(0.1 + WHEEL), weights.top(0));
        return { joints: { yoke: { value, origin: pivot.clone() } }, points: { grips: points } };
      },
    };
  },
});
