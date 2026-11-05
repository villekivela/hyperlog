// A glute-ham developer (refs/glute_ham_raise/ref.md): a pair of half-round hip pads on a post at the
// front of a bolt-together base, and behind them a sliding ankle assembly, two pairs of foam rollers
// one above the other with a foot plate behind them. The lifter kneels on the pads facing forward
// (+z), the origin on the floor under the pads' rear edge, the lower thighs on the pads with the
// kneecaps just behind their crest, the ankles in the gap between the rollers and the soles flat on
// the plate. Nothing on it moves.
//
// The assembly slides back along the spine in 1 in steps, locked by a pop pin: `slide` counts the steps
// from the most forward hole (0 to 12, about 300 mm), where the plate's face is 348 mm behind the pads'
// rear edge (a chosen place, the ref gives none). The fit rule sets it from the shin: the plate a shin
// and the ankle to the sole behind the knee, which rests 60 mm ahead of the pads' rear edge. That is
// 450 mm for the ref's 0.43 m shin, step 4, the default. Back extensions and sit-ups want it further back.
import { recipe } from '../recipe.mjs';
import { halfRoundGeometry, knob, pad, rod, rollerGeometry, tube, turnedPart } from '../parts.mjs';
import { STRAIGHT, V } from '../rig.mjs';

const IN = 0.0254;
const STEP = IN;
const MOST = 12;
// The ref's setting, 450 mm, is the fifth hole.
const DEFAULT = 4;
// The base: two floor rails 45 in apart outside, 1740 mm long, with a rubber cap on each end.
const RAIL = { w: 0.0635, x: 0.5715 - 0.0318, rear: -0.76, front: 0.98, cap: 0.01 };
const BEAM = 0.0635;
// Each upright stands on a crossmember between the rails: the front one under the pads, up to the pad
// plate at 836 mm (the manual's 29 in part with the plate on it, about 30 in over the rails), the rear
// 19 in tall, with the spine bolted to them both.
const FRONT = { z: 0.25, top: 0.836 };
const REAR = { z: -0.7, top: 19 * IN };
const SPINE = { y: 0.43, w: 0.0508 };
// The pads: two, 230 wide with 40 between, 500 long and 230 tall, flat side down on a plate, their
// crest 1092 mm up.
const PAD = { w: 0.23, gap: 0.04, length: 0.5, height: 0.23, top: 1.092, plate: 0.026 };
const PAD_X = (PAD.w + PAD.gap) / 2;
// The kneecaps rest on the pads' rear curve, this far ahead of their rear edge: chosen, at low
// confidence, so the ref's 0.43 m shin and 80 mm ankle give its 450 mm setting.
const KNEE = 0.06;
// A handle each side under the pads, 820 mm up, out past both ends.
const HANDLE = { x: 0.2, y: 0.82, from: -0.1, to: 0.6, radius: 0.016 };
// The ankle assembly: the foot plate 345 tall to 1245 mm, the rollers' centres 120 mm ahead of its middle, the
// lower roller 203 mm under the upper. Its face is `FORWARD` behind the pads' rear edge at the most forward hole.
const FORWARD = 0.45 - DEFAULT * STEP;
const PLATE = { thick: 0.0048, height: 0.345, top: 1.245, width: 0.5 };
const ROLLER = { r: 0.0635, w: 0.216, ahead: 0.12, spacing: 0.203, x: PAD_X };
const GAP_Y = PLATE.top - PLATE.height / 2;
const SLEEVE = { w: 0.07, length: 0.12 };
// From the ankle's bone to the sole.
const ANKLE = 0.08;

export default recipe({
  name: 'glute_ham_raise',
  // Shown alone in the studio until a pose uses it: three-quarter from the front.
  standsAlone: { camera: { target: [0, 0.65, -0.05], halfHeight: 1.1, azimuth: 145, elevation: 12 } },
  bodies: {},
  contacts: [
    { body: 'frame', point: 'thighPad', landmark: 'thighs' },
    { body: 'frame', point: 'ankleRollers', landmark: 'shins' },
    { body: 'frame', point: 'footPlate', landmark: 'feet' },
    // The hands are free at the top of the rep, and rest on the handles or reach forward at the bottom.
    { body: 'frame', point: 'handles', landmark: 'hands', optional: true },
  ],
  fit: [{
    sets: 'slide',
    rule: 'the knee just behind the pads\' crest with the shin level, the soles flat on the plate',
    solve: ({ L }) => {
      const behind = L.shin * STRAIGHT + ANKLE - KNEE;
      return Math.min(MOST, Math.max(0, Math.round((behind - FORWARD) / STEP)));
    },
  }],
  build({ slide = DEFAULT } = {}, M) {
    if (!Number.isInteger(slide) || slide < 0 || slide > MOST) throw new Error(`glute_ham_raise: slide ${slide} isn't a step from 0 to ${MOST}`);
    const sides = [-1, 1];
    const face = -FORWARD - slide * STEP;
    const plateZ = face - PLATE.thick / 2;
    const rollerZ = plateZ + ROLLER.ahead;
    const rollerY = [GAP_Y + ROLLER.spacing / 2, GAP_Y - ROLLER.spacing / 2];

    const railCore = [RAIL.rear + RAIL.cap, RAIL.front - RAIL.cap];
    const base = [
      ...sides.flatMap((s) => [
        tube(RAIL.w, V(s * RAIL.x, RAIL.w / 2, railCore[0]), V(s * RAIL.x, RAIL.w / 2, railCore[1]), M.frame),
        ...[[RAIL.rear, 1], [RAIL.front, -1]].map(([end, inward]) => pad(RAIL.w, RAIL.w, RAIL.cap, M.vinyl, V(s * RAIL.x, RAIL.w / 2, end + inward * RAIL.cap / 2), { soft: 0.004, solid: false })),
      ]),
      ...[FRONT.z, REAR.z].map((z) => tube(BEAM, V(-RAIL.x + RAIL.w / 2, BEAM / 2, z), V(RAIL.x - RAIL.w / 2, BEAM / 2, z), M.frame)),
      // The uprights, each braced to its crossmember either side.
      tube(BEAM, V(0, BEAM, FRONT.z), V(0, FRONT.top, FRONT.z), M.frame),
      tube(BEAM, V(0, BEAM, REAR.z), V(0, REAR.top, REAR.z), M.frame),
      ...[FRONT, REAR].flatMap(({ z }) => sides.map((s) => tube(0.03, V(0, 0.3, z), V(s * 0.24, BEAM, z), M.frame))),
      // The spine between them.
      tube(SPINE.w, V(0, SPINE.y, FRONT.z), V(0, SPINE.y, REAR.z), M.frame),
    ];

    const pads = [
      pad(PAD.w * 2 + PAD.gap + 0.07, PAD.plate, PAD.length - 0.04, M.frame, V(0, PAD.top - PAD.height - PAD.plate / 2, FRONT.z), { soft: 0.003 }),
      ...sides.map((s) => turnedPart(halfRoundGeometry(PAD.w, PAD.length, PAD.height), M.vinyl, V(s * PAD_X, PAD.top - PAD.height / 2, PAD.length / 2), { solid: true })),
      tube(0.04, V(-HANDLE.x, HANDLE.y - 0.035, FRONT.z), V(HANDLE.x, HANDLE.y - 0.035, FRONT.z), M.frame),
      ...sides.map((s) => rod(HANDLE.radius, V(s * HANDLE.x, HANDLE.y, HANDLE.from), V(s * HANDLE.x, HANDLE.y, HANDLE.to), M.frame, { held: true })),
    ];

    // The sleeve on the spine with its pop pin, the mast up between the roller pairs, an arm back from
    // it to the plate, and each roller on an axle through the mast with a washer on its outer end.
    const ankles = [
      tube(SLEEVE.w, V(0, SPINE.y, rollerZ + SLEEVE.length / 2), V(0, SPINE.y, rollerZ - SLEEVE.length / 2), M.frame),
      rod(0.008, V(0, SPINE.y + SLEEVE.w / 2, rollerZ), V(0, SPINE.y + SLEEVE.w / 2 + 0.035, rollerZ), M.metal),
      knob(M, V(0, SPINE.y + SLEEVE.w / 2 + 0.045, rollerZ), 0.017),
      tube(SPINE.w, V(0, SPINE.y + SLEEVE.w / 2, rollerZ), V(0, rollerY[0], rollerZ), M.frame),
      tube(0.04, V(0, GAP_Y, rollerZ), V(0, GAP_Y, plateZ), M.frame),
      pad(PLATE.width, PLATE.height, PLATE.thick, M.frame, V(0, PLATE.top - PLATE.height / 2, plateZ), { soft: 0.0015 }),
      ...rollerY.flatMap((y) => [
        rod(0.012, V(-ROLLER.x - ROLLER.w / 2 - 0.012, y, rollerZ), V(ROLLER.x + ROLLER.w / 2 + 0.012, y, rollerZ), M.metal),
        ...sides.flatMap((s) => [
          turnedPart(rollerGeometry(ROLLER.r, ROLLER.w), M.vinyl, V(s * ROLLER.x, y, rollerZ)),
          rod(0.03, V(s * (ROLLER.x + ROLLER.w / 2), y, rollerZ), V(s * (ROLLER.x + ROLLER.w / 2 + 0.008), y, rollerZ), M.metal),
        ]),
      ]),
    ];

    // Where the lifter meets it: the thighs on the pads' rear curve at the knee, the ankles between the
    // rollers, the soles on the plate, the hands on the handles' front ends.
    const crest = PAD.top - PAD.height;
    const curve = crest + PAD.height * Math.sqrt(1 - ((PAD.length / 2 - KNEE) / (PAD.length / 2)) ** 2);
    const pair = (at) => sides.map(at);
    const points = {
      thighPad: pair((s) => V(s * PAD_X, curve, KNEE)),
      ankleRollers: pair((s) => V(s * ROLLER.x, GAP_Y, rollerZ)),
      footPlate: pair((s) => V(s * ROLLER.x, GAP_Y - 0.05, face)),
      handles: pair((s) => V(s * HANDLE.x, HANDLE.y, PAD.length)),
    };
    return {
      parts: [...base, ...pads, ...ankles],
      drive() {
        return { points: Object.fromEntries(Object.entries(points).map(([k, v]) => [k, v.map((p) => p.clone())])) };
      },
    };
  },
});
