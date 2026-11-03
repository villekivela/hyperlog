// A seated chest press: the seat and back pad (its face `pad` metres behind the spine), a column
// behind with a boom out to the pivot, and two levers hung from it, swinging the handles through
// their arc. Behind the column a weight stack in its frame, its cable over a pulley to the column.
// `P.pivot`, `P.handle(s)` and `P.swing` (the levers' angle, radians from hanging straight down)
// place them.
//
// The levers are one body on a revolute joint at the pivot, its value how far they have swung
// forward from where they rest, the handles back at the chest. The stack rises half a metre a
// radian of it.
import { recipe, revolute } from '../recipe.mjs';
import {
  UPRIGHTS, WHEEL, backPad, basePlate, endCap, pad, part, pulley, rod, rodGeometry, rollerGeometry, seatOn, span, stackFrame, stretchedTube,
  tube, turnedPart, weightStack, wheelGeometry,
} from '../parts.mjs';
import { V, rad, spineLean } from '../rig.mjs';

// The levers' pivot, [y, z], how far it is down them to the handles' middle, and how far they hang
// from straight down where they rest, the handles back at the chest (radians).
const PIVOT = [1.8475, 0.185];
const ARM = 0.874;
const REST = -0.2413;
/** How far up from the hips to the shoulders a seated lifter's mid-chest is. */
const MID_CHEST = 0.89;
/** The hips on the seat at [y, z]. */
const hipOn = ([y, z]) => [y + 0.09, z - 0.02];

export default recipe({
  name: 'chest_press',
  // From the handles at the chest to the arms straight.
  bodies: { levers: revolute({ axis: V(-1, 0, 0), limits: [0, rad(26)] }) },
  rep: { joint: 'levers', from: 'max' },
  contacts: [{ body: 'levers', point: 'handles', landmark: 'hands' }],
  stacks: [{ joint: 'levers', ratio: 0.5 }],
  fit: [
    {
      sets: 'seat',
      rule: 'the handles at mid-chest',
      solve: ({ L, entry }) => [PIVOT[0] - ARM * Math.cos(REST) - MID_CHEST * L.trunkUp * Math.cos(spineLean(entry.body.trunk, L)) - 0.09, 0.02],
    },
    { sets: 'pad', rule: 'the back pad against the spine', solve: ({ L }) => L.back },
  ],
  machine: ({ seat = [0.47, 0.02] }, { grip }) => ({
    hip: hipOn(seat),
    at: (value) => {
      const swing = REST + value;
      const pivot = V(0, ...PIVOT);
      const middle = V(0, pivot.y - ARM * Math.cos(swing), pivot.z + ARM * Math.sin(swing));
      return { pivot, swing, handle: (s) => middle.clone().setX(s * grip.width) };
    },
  }),
  build({ seat = [0.47, 0.02], pad: behind = 0.2 } = {}, M) {
    const backRest = backPad(0.36, 0.64, M, { up: 0.36, behind });
    const rear = -0.55;
    const stackZ = -0.71;
    const ux = UPRIGHTS;
    const frameTop = 1.72;
    const wheel = V(0, frameTop - 0.035 - WHEEL - 0.02, stackZ + WHEEL);
    const front = seat[1] + 0.2;
    const parts = [
      ...seatOn(seat[0], seat[1], M, { pin: 1 }),
      backRest.mesh,
      tube(0.07, V(0, 0.035, stackZ - 0.12), V(0, 0.035, front), M.frame),
      tube(0.07, V(-ux - 0.15, 0.035, stackZ), V(ux + 0.15, 0.035, stackZ), M.frame),
      tube(0.07, V(-0.3, 0.035, front), V(0.3, 0.035, front), M.frame),
      ...[-1, 1].flatMap((s) => [endCap(s * (ux + 0.15), stackZ, M), endCap(s * 0.3, front, M)]),
      ...stackFrame(stackZ, frameTop, M),
      // The stack's frame tied to the column at its top.
      tube(0.06, V(0, frameTop, stackZ + 0.035), V(0, frameTop, rear - 0.05), M.frame),
      ...pulley(wheel, M),
      // The cable from the wheel's top forward into the column.
      rod(0.0045, V(0, wheel.y + WHEEL, wheel.z), V(0, wheel.y + WHEEL, rear), M.metal),
      basePlate(0, rear, M),
    ];
    const column = stretchedTube(0.1, M.frame);
    const cap = pad(0.11, 0.012, 0.11, M.frame, V(0, 0, rear), { soft: 0.003, solid: false });
    const boom = stretchedTube(0.08, M.frame);
    const brace = stretchedTube(0.05, M.frame);
    const support = stretchedTube(0.05, M.frame);
    const axle = part(rodGeometry(0.022), M.metal);
    // The levers' pivot: a housing either side of the boom's end, round the axle.
    const housings = [-1, 1].map(() => pad(0.008, 0.14, 0.12, M.pulley, V(0, 0, 0), { soft: 0.003, solid: false }));
    const caps = [0, 1].map(() => turnedPart(wheelGeometry(0.03, 0.02), M.pulley, V(0, 0, 0), { solid: false }));
    const hubs = [0, 1].map(() => part(wheelGeometry(0.05, 0.05), M.pulley));
    // Each lever hangs outside the body and turns at its foot, in a block, into a horizontal handle
    // reaching in across the palm, held like a bar, with a grip on it and a cap on its end.
    const levers = [0, 1].map(() => ({
      arm: stretchedTube(0.045, M.frame),
      elbow: pad(0.06, 0.06, 0.06, M.frame, V(0, 0, 0), { soft: 0.012 }),
      grip: part(rodGeometry(0.017), M.metal, { held: true }),
      sleeve: part(rodGeometry(0.0205), M.vinyl, { held: true }),
      end: turnedPart(rollerGeometry(0.024, 0.02), M.vinyl, V(0, 0, 0), { solid: false }),
    }));
    const drop = part(rodGeometry(0.0045, 8), M.metal);
    const weights = weightStack(V(0, 0, stackZ), M, { rodTop: 1.5, toward: 1 });
    return {
      parts: [
        ...parts, column, cap, boom, brace, support, axle, ...housings, ...caps, ...hubs,
        ...levers.flatMap((l) => [l.arm, l.elbow, l.grip, l.sleeve, l.end]), drop, ...weights.parts,
      ],
      bodies: { levers: [...hubs, ...levers.flatMap((l) => [l.arm, l.elbow, l.grip, l.sleeve, l.end])] },
      stacks: {
        levers: {
          lift(l) {
            weights.lift(l);
            span(drop, V(0, wheel.y, stackZ), weights.top(l));
          },
        },
      },
      drive(P, P0) {
        const back = backRest.fit(P0);
        // The back pad's support runs straight back from its board into the column.
        span(support, back, V(0, back.y, rear - 0.05));
        const top = P.pivot.y + 0.05;
        span(column, V(0, 0.012, rear), V(0, top + 0.04, rear));
        cap.position.y = top + 0.046;
        span(boom, V(0, top, rear - 0.05), V(0, top, P.pivot.z + 0.04));
        // A brace from the column up under the boom.
        span(brace, V(0, top - 0.3, rear + 0.05), V(0, top - 0.04, rear + 0.3));
        span(axle, P.pivot.clone().setX(-0.52), P.pivot.clone().setX(0.52));
        housings.forEach((m, i) => m.position.copy(P.pivot).setX((i ? 1 : -1) * 0.045).add(V(0, 0.03, 0)));
        caps.forEach((m, i) => m.position.copy(P.pivot).setX((i ? 1 : -1) * 0.535));
        levers.forEach((l, i) => {
          const s = i ? 1 : -1;
          const h = P.handle(s);
          const out = 0.5;
          hubs[i].position.copy(P.pivot).setX(s * out);
          span(l.arm, P.pivot.clone().setX(s * out), h.clone().setX(s * out));
          l.elbow.position.copy(h).setX(s * out);
          l.elbow.quaternion.copy(l.arm.quaternion);
          span(l.grip, h.clone().setX(s * out), h.clone().setX(s * (Math.abs(h.x) - 0.1)));
          span(l.sleeve, h.clone().setX(s * (out - 0.04)), h.clone().setX(s * (Math.abs(h.x) - 0.08)));
          l.end.position.copy(h).setX(s * (Math.abs(h.x) - 0.1));
        });
        return {
          joints: { levers: { value: P.machine.value, origin: P.pivot.clone() } },
          points: { handles: [-1, 1].map((s) => P.handle(s).clone()) },
        };
      },
    };
  },
});
