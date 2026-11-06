// A selectorized shoulder press (refs/shoulder_press/ref.md), rebuilt from where its photos put each
// part (notes in build/shoulder_press, not committed). The lifter sits on a seat at 350 mm against a
// back pad reclined 10°, 830 mm long and topping out at 1.27 m, on a post up from the rear frame. Two
// posts behind the pad carry the yoke's bearings 980 mm up; a rake leg from each runs down and forward
// to the front foot. The yoke is a crossbar behind the pad and two flat arms forward past the
// shoulders, 900 mm to the grips, each arm ending in a level bar across the machine, held overhand, palms
// forward. The weight stack stands beside the lifter, on their right, in a portal frame 1.63 m tall, its
// front level with the grips (the demo front view); a crank behind the pivot draws a cable down to
// the floor, along it and up to the stack, so the stack rises as the yoke does.
//
// The yoke is one body on a revolute joint about the bearings, its value the angle up from level ahead
// of the line from the pivot to the grips: 0° at the bottom of the rep, the grips level with the
// shoulders, to 40° at the top, 580 mm higher and about 200 mm further back. A pose's solve (the
// `linkage`) sets the seat so the shoulders are at the grips' height at the bottom, and hangs the
// body from the grips.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  WHEEL, backPad, floorRail, hub, mitered, padGeometry, part, pivotJoint, pad, pulley, rodGeometry, seatOn, span, stretchedTube, tube, weightStack,
} from '../parts.mjs';
import { V, rad, spineLean } from '../rig.mjs';

// The yoke's pivot, [y, z], and the grips' reach from it.
const PIVOT = [0.98, -0.75];
const REACH = 0.9;
const TOP = Math.asin(0.58 / REACH);
// The grips this far either side of the middle (700 apart), the arms and the bearings outside them.
const GRIPS_OUT = 0.35;
const ARMS_OUT = 0.48;
const BEARING_OUT = 0.3;
const BAR = 0.19;
/** The arms run this far below the grips' line, so the grips stand proud of them. */
const DROP = 0.055;
// The seat's top and its z; the hips sit this far above it and a little behind its middle.
const SEAT = [0.35, 0.02];
/** The seat's top, held to its range, 330 to 500 mm. */
const inRange = (top) => Math.min(0.5, Math.max(0.33, top));
const SITS = 0.09;
const hipOn = (top) => [top + SITS, SEAT[1] - 0.02];
const PAD = { long: 0.86, w: 0.44, lean: rad(-6.5) };
// The stack beside the lifter (their right is -x): the portal's middle [x, z], its half width and depth, and its top.
const TOWER = { at: [-0.75, -0.05], halfW: 0.19, halfD: 0.2, top: 1.56, post: 0.08 };
const FRONT = 0.56;
const REAR = -0.92;
const CRANK = 0.12;

/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

/** Where the grips are with the yoke at `value`: the pivot, the way it points ahead, and each hand's grip. */
const yokeAt = (value) => {
  const pivot = V(0, ...PIVOT);
  const ahead = V(0, Math.sin(value), Math.cos(value));
  const middle = pivot.clone().addScaledVector(ahead, REACH);
  const up = V(0, Math.cos(value), -Math.sin(value));
  return { pivot, ahead, up, handle: (s) => middle.clone().setX(s * GRIPS_OUT), swing: value };
};

export default recipe({
  name: 'shoulder_press',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's hero
  // photo is.
  standsAlone: { camera: { target: [-0.1, 0.8, -0.1], halfHeight: 1.25, azimuth: -40, elevation: 14 } },
  bodies: { yoke: revolute({ axis: V(-1, 0, 0), limits: [0, TOP] }) },
  // From the arms near straight overhead at the top down to the grips level with the shoulders.
  rep: { joint: 'yoke', from: 'max' },
  contacts: [
    { body: 'yoke', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'backPad', landmark: 'back' },
  ],
  stacks: [{ joint: 'yoke', ratio: 0.2 }],
  // The seat is the one adjustment (a lever under its front); the grips are fixed to the frame.
  fit: [
    {
      sets: 'seat',
      rule: "the shoulders level with the grips at the bottom of the rep, the seat's top that far below them (350 mm for the mannequin, in a range of 330 to 500)",
      solve: ({ L, entry }) => {
        const lean = spineLean(entry.body.trunk, L);
        return inRange(PIVOT[0] - (L.trunkUp * Math.cos(lean) + L.trunkBack * Math.sin(lean)) - SITS);
      },
    },
    { sets: 'pad', rule: 'the back pad against the spine', solve: ({ L }) => L.back },
  ],
  linkage: ({ seat = SEAT[0] }) => ({
    hip: hipOn(inRange(seat)),
    at: (value) => yokeAt(value),
  }),
  build({ seat = SEAT[0], pad: behind = 0.2 } = {}, M) {
    const seatTop = inRange(seat);
    const frame = [];
    const [tx, tz] = TOWER.at;
    // The rear frame's floor: a cross beam under the posts, a cross beam at the front foot, and a
    // beam down the middle between them for the seat and the pad's posts.
    frame.push(...floorRail(V(-0.45, 0.035, REAR), V(0.45, 0.035, REAR), M));
    frame.push(...floorRail(V(0, 0.035, REAR), V(0, 0.035, FRONT - 0.2), M));
    for (const s of [-1, 1]) frame.push(...floorRail(V(s * BEARING_OUT, 0.035, REAR), V(s * BEARING_OUT, 0.035, FRONT), M));
    // The bearing posts stand in the side rails, and the rake leg from each runs down and forward, cut
    // square inside the post at its top and mitered into the rail at its foot, both sunk below
    // the rail's top (70 mm) so no tube end shows.
    for (const s of [-1, 1]) {
      frame.push(role(tube(0.09, V(s * BEARING_OUT, 0.04, PIVOT[1]), V(s * BEARING_OUT, PIVOT[0] - 0.09, PIVOT[1]), M.frame), 'post'));
      const top = V(s * BEARING_OUT, PIVOT[0] - 0.3, PIVOT[1]);
      const foot = V(s * BEARING_OUT, 0.04, SEAT[1] + 0.05);
      frame.push(role(pad(0.13, 0.04, 0.26, M.frame, V(s * BEARING_OUT, 0.07, foot.z - 0.06), { soft: 0.004 }), 'footPlate'));
      frame.push(role(mitered([top, foot], 0.06, M.frame, { ends: [V(0, 0, 1), V(0, 1, 0)] }), 'rakeLeg'));
    }
    // The back pad on its post: it follows the trunk, as a fixed pad meets the back on the gym floor,
    // and with no pose stands reclined `PAD.lean` from the hips on the seat.
    const hip = V(0, ...hipOn(seatTop));
    const rest = { hipC: hip, u: V(0, Math.cos(PAD.lean), Math.sin(PAD.lean)) };
    const backRest = backPad(PAD.w, PAD.long, M, { up: PAD.long / 2 - 0.04, behind });
    role(backRest.mesh.children[0], 'backPad');
    const support = stretchedTube(0.07, M.frame);
    // The pad's support stands on a post sunk into the middle rail behind the seat.
    frame.push(backRest.mesh, support, tube(0.07, V(0, 0.04, SEAT[1] - 0.14), V(0, seatTop - 0.1, SEAT[1] - 0.14), M.frame));
    // The seat on its post, in a housing at its front.
    const [cushion, ...seatParts] = seatOn(seatTop, SEAT[1], M, { w: 0.44, d: 0.4, pin: 1, floor: 0.04 });
    frame.push(role(cushion, 'seat'), ...seatParts);
    frame.push(pad(0.2, 0.12, 0.16, M.frame, V(0, seatTop - 0.16, SEAT[1] + 0.12), { soft: 0.012 }));

    // The tower: a closed frame. Two portals, front and rear, each an inverted U with a rounded top, tied
    // along the floor and across the top, a panel each side and behind, and a rounded header over it
    // all carrying the placard.
    const arc = (x, y, z, sign) => Array.from({ length: 5 }, (_, i) => {
      const t = (i / 4) * (Math.PI / 2);
      return V(x + sign * 0.12 * (1 - Math.cos(t)), y + 0.12 * Math.sin(t), z);
    });
    const portal = (z) => {
      const x0 = tx - TOWER.halfW;
      const x1 = tx + TOWER.halfW;
      const turn = TOWER.top - 0.12;
      return mitered([V(x0, 0.04, z), ...arc(x0, turn, z, 1), ...arc(x1, turn, z, -1).reverse(), V(x1, 0.04, z)], TOWER.post, M.frame);
    };
    frame.push(portal(tz + TOWER.halfD), portal(tz - TOWER.halfD));
    for (const s of [-1, 1]) {
      frame.push(...floorRail(V(tx + s * TOWER.halfW, 0.035, tz - TOWER.halfD), V(tx + s * TOWER.halfW, 0.035, tz + TOWER.halfD), M));
      // The side facing the lifter is open, as the photos show, the stack and its rods in view.
      if (s > 0) continue;
      const side = pad(0.012, TOWER.top - 0.2, 2 * TOWER.halfD - 0.04, M.frame, V(tx + s * TOWER.halfW, (TOWER.top + 0.1) / 2, tz), { soft: 0.004, solid: false });
      frame.push(role(side, 'towerPanel'));
    }
    frame.push(role(pad(2 * TOWER.halfW - 0.04, TOWER.top - 0.2, 0.012, M.frame, V(tx, (TOWER.top + 0.1) / 2, tz - TOWER.halfD), { soft: 0.004, solid: false }), 'towerPanel'));
    frame.push(role(pad(2 * TOWER.halfW + 0.1, 0.16, 2 * TOWER.halfD + 0.1, M.frame, V(tx, TOWER.top - 0.01, tz), { soft: 0.05, solid: false }), 'header'));
    frame.push(pad(0.3, 0.1, 0.012, M.vinyl, V(tx, TOWER.top - 0.01, tz + TOWER.halfD + 0.056), { soft: 0.003, solid: false }));
    frame.push(...floorRail(V(tx, 0.035, tz + TOWER.halfD), V(BEARING_OUT, 0.035, FRONT - 0.2), M));
    const weights = weightStack(V(tx, 0, tz), M, { rodTop: TOWER.top - 0.12, toward: 1 });
    role(weights.parts.find((p) => p.userData.stack), 'stackHead');
    frame.push(...weights.parts);
    // The cable's two floor pulleys, under the crank's tip at rest and at the stack's foot, and its
    // three runs: down from the crank, along the floor, and up to the head.
    const low = [V(0, 0.1, PIVOT[1] - CRANK + 0.06), V(tx, 0.1, tz)];
    frame.push(...low.flatMap((at) => pulley(at, M)));
    const [down, along, rise] = [0, 1, 2].map(() => part(rodGeometry(0.0045, 8), M.metal));
    frame.push(down, along, rise);

    // The yoke: a bearing housing a side, the crossbar behind the pad between them, a crank under
    // it, and on each side an arm forward to its bar grip and neutral grip.
    const bearings = [-1, 1].map((s) => pivotJoint(M, V(s * BEARING_OUT, PIVOT[0], PIVOT[1]), V(1, 0, 0)));
    frame.push(...bearings.flatMap((b) => b.fixed));
    // The yoke's Y: a beam out along the crossbar behind the pad and angled forward at each end, built at
    // the bottom of the rep and turned about the pivot, then an arm a side straight ahead to the grips.
    // The bent beam is behind the lifter and stands clear of the body, so only the arms are solid.
    const [y0, z0] = PIVOT;
    const yb = y0 - DROP;
    const beam = role(mitered([
      V(-ARMS_OUT, yb, z0 + 0.45), V(-ARMS_OUT, yb, z0 + 0.3), V(-BEARING_OUT, yb, z0), V(BEARING_OUT, yb, z0), V(ARMS_OUT, yb, z0 + 0.3), V(ARMS_OUT, yb, z0 + 0.45),
    ], 0.07, M.metal, { solid: false, tall: 0.12 }), 'yokeBeam');
    const crank = stretchedTube(0.06, M.metal);
    const knob = hub(M, V(0, 0, 0));
    const arms = [-1, 1].map((s) => ({
      s,
      arm: role(part(padGeometry(0.07, 1, 0.12, 0.012), M.metal, { solid: true }), 'yokeArm'),
      // The one handle a side: a prominent bar beside the arm's end, running across the machine, held overhand, palm forward.
      neutral: role(part(rodGeometry(0.016), M.vinyl, { held: true }), 'neutralGrip'),
    }));
    const moving = [...bearings.map((b) => b.housing), beam, crank, knob, ...arms.flatMap((a) => [a.arm, a.neutral])];
    const place = (value) => {
      const { pivot, ahead, up } = yokeAt(value);
      const middle = pivot.clone().addScaledVector(ahead, REACH);
      const points = [];
      // The beam turned about the pivot, `value` up from level ahead (a turn about x of -value).
      const turn = new THREE.Quaternion().setFromAxisAngle(V(1, 0, 0), -value);
      beam.quaternion.copy(turn);
      beam.position.copy(pivot).sub(pivot.clone().applyQuaternion(turn));
      const tip = pivot.clone().addScaledVector(ahead, -CRANK);
      span(crank, pivot, tip);
      knob.position.copy(tip);
      for (const a of arms) {
        const at = (p, x) => p.clone().setX(a.s * x);
        const drop = up.clone().multiplyScalar(-DROP);
        // The arm runs on past the bar, and the bar lies level across the machine, its outer end butting the
        // arm's inner face a little below the arm's top edge.
        const reach = middle.clone().add(drop).addScaledVector(ahead, 0.03);
        span(a.arm, at(pivot.clone().addScaledVector(ahead, 0.45).add(drop), ARMS_OUT), at(reach, ARMS_OUT));
        span(a.neutral, at(middle, GRIPS_OUT - BAR / 2), at(middle, GRIPS_OUT + BAR / 2 + 0.005));
        points.push(at(middle, GRIPS_OUT));
      }
      span(down, knob.position, low[0].clone().setY(0.1 + WHEEL));
      span(along, low[0].clone().setY(0.1 + WHEEL), low[1].clone().setY(0.1 + WHEEL));
      span(rise, low[1].clone().setY(0.1 + WHEEL), weights.top(0));
      return { value, points };
    };
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
      drive(P, P0) {
        const { value, points } = place(P.linkage?.value ?? P.yoke ?? 0);
        const board = backRest.fit(P0?.hipC ? P0 : rest);
        span(support, V(0, seatTop - 0.1, SEAT[1] - 0.14), board.clone().addScaledVector(rest.u, -0.22));
        weights.lift(0);
        return {
          joints: { yoke: { value, origin: V(0, ...PIVOT) } },
          points: { grips: points, backPad: [backRest.mesh.position.clone()] },
        };
      },
    };
  },
});
