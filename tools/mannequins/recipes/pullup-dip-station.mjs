// A pull-up and dip station (refs/pullup_dip_station/ref.md): a multi-grip bar and a pair of dip
// handles on one rig, both fixed. Two uprights stand 965 mm apart, the lifter facing them. The bar
// is bolted to their faces at 2.30 m: a plate, two outer prongs and two inner ones running toward
// the lifter, a V from the plate's middle to the inner prongs (about 45°), and a cross tube 230 mm
// out joining each inner prong to its outer one and running on past it, its end bent back toward
// the plate; nothing joins the inner prongs but the V. The dip handles are a crossbar between the
// uprights at 1.30 m with a handle either side splaying from 451 mm apart at the crossbar to 630 at
// the tips, 440 mm out.
//
// Nothing moves, and the frame stands where it goes: the cross tube's hang line at the origin, the
// uprights 230 mm behind it. A pose hangs the body from the grips or rests it on the handles.
import { recipe } from '../recipe.mjs';
import { basePlate, floorRail, knob, pad, rod, tube } from '../parts.mjs';
import { V } from '../rig.mjs';

// The uprights' front face, behind the hang line: the bar's plate and the crossbar sit against it.
const WALL = 0.23;
// The hang line is this far out from the uprights' face: the cross tube's distance from the plate.
const HANG = 0.23;
// The dip crossbar's plate against the uprights' face.
const BACKING = 0.04;
const UPRIGHT = { x: 0.4825, w: 0.08, top: 2.55 };
// The bar's grips are all at this height, on the cross tube and the prongs.
const BAR = 2.3;
const GRIP = 0.01588;
// The prongs, from the plate to the centre of the sphere on their end, and their spacing.
const OUTER = { x: 0.4065, reach: 0.32, sphere: 0.0508, radius: 0.0254 };
const INNER = { x: 0.203, reach: 0.405, sphere: 0.0381 };
// The cross tube is 230 mm out, a side of it running from the inner prong to 530 mm out, its end
// bent back toward the plate 60 mm.
const CROSS = { z: WALL - HANG, half: 0.53, bend: 0.06 };
// Where the hands go (half the spacing): wide on the cross tube outside the outer prongs, standard
// on it between the prongs, close on the V's arms, and neutral on the inner prongs, a little out
// along them from the cross tube.
const HANDS = { wide: 0.48, standard: 0.305, close: 0.14, neutral: INNER.x, neutralOut: 0.08 };
// The dip handles: from the crossbar's face to the tip, 47.6 mm across, at 1.30 m; the hands rest
// in the middle, the close grip near the crossbar.
const DIP = { height: 1.3, base: 0.2255, tip: 0.315, length: 0.44, radius: 0.0238, hand: 0.5, close: 0.15, plate: 0.254 };
const FLOOR = 0.035;

export default recipe({
  name: 'pullup_dip_station',
  // Shown alone in the studio until a pose uses it: three-quarter from the front.
  standsAlone: { camera: { target: [0, 1.3, 0.1], halfHeight: 1.5, azimuth: 145, elevation: 12 } },
  bodies: {},
  contacts: [
    { body: 'frame', point: 'wide', landmark: 'hands' },
    { body: 'frame', point: 'standard', landmark: 'hands' },
    { body: 'frame', point: 'close', landmark: 'hands' },
    { body: 'frame', point: 'neutral', landmark: 'hands' },
    { body: 'frame', point: 'dip', landmark: 'hands' },
    { body: 'frame', point: 'dipClose', landmark: 'hands' },
  ],
  build(_options, M) {
    const sides = [-1, 1];
    // Two uprights from the floor, joined at the top, each on a floor beam running back with a
    // cross beam between them, and a plate under each.
    const behind = WALL + UPRIGHT.w / 2;
    const frame = [
      ...sides.flatMap((s) => [
        tube(UPRIGHT.w, V(s * UPRIGHT.x, FLOOR * 2, behind), V(s * UPRIGHT.x, UPRIGHT.top, behind), M.frame),
        ...floorRail(V(s * UPRIGHT.x, FLOOR, behind - 0.04), V(s * UPRIGHT.x, FLOOR, behind + 0.9), M),
        basePlate(s * UPRIGHT.x, behind + 0.9, M),
      ]),
      tube(UPRIGHT.w, V(-UPRIGHT.x, UPRIGHT.top, behind), V(UPRIGHT.x, UPRIGHT.top, behind), M.frame),
      ...floorRail(V(-UPRIGHT.x, FLOOR, behind + 0.9), V(UPRIGHT.x, FLOOR, behind + 0.9), M),
    ];

    // The bar: a plate on the uprights' faces, the prongs out from it to a sphere each, the V, and
    // the cross tube across them with its ends bent.
    const prongEnd = (p) => V(0, BAR, WALL - p.reach);
    const spheres = [OUTER, INNER].flatMap((p) => sides.map((s) => knob(M, prongEnd(p).setX(s * p.x), p.sphere)));
    const bar = [
      pad(2 * UPRIGHT.x, 0.19, 0.0095, M.frame, V(0, BAR, WALL + 0.0048), { soft: 0.002 }),
      ...sides.flatMap((s) => [
        rod(OUTER.radius, V(s * OUTER.x, BAR, WALL), prongEnd(OUTER).setX(s * OUTER.x), M.frame, { held: true }),
        rod(GRIP, V(s * INNER.x, BAR, WALL), prongEnd(INNER).setX(s * INNER.x), M.frame, { held: true }),
        rod(GRIP, V(0, BAR, WALL), V(s * INNER.x, BAR, CROSS.z), M.frame, { held: true }),
        rod(GRIP, V(s * INNER.x, BAR, CROSS.z), V(s * CROSS.half, BAR, CROSS.z), M.frame, { held: true }),
        rod(GRIP, V(s * CROSS.half, BAR, CROSS.z), V(s * CROSS.half, BAR, CROSS.z + CROSS.bend), M.frame),
      ]),
      ...spheres,
    ];

    // The dip handles: a plate against the uprights' faces and a handle either side of the middle,
    // splaying toward their tips.
    const base = (s) => V(s * DIP.base, DIP.height, WALL - BACKING);
    const tip = (s) => V(s * DIP.tip, DIP.height, WALL - BACKING - DIP.length);
    const handles = [
      pad(2 * UPRIGHT.x, DIP.plate, BACKING, M.frame, V(0, DIP.height - DIP.plate / 2 - DIP.radius, WALL - BACKING / 2), { soft: 0.004 }),
      ...sides.flatMap((s) => [
        rod(DIP.radius, base(s), tip(s), M.vinyl, { held: true }),
        knob(M, tip(s), DIP.radius),
      ]),
    ];

    const along = (s, t) => base(s).lerp(tip(s), t);
    const pair = (at) => sides.map(at);
    const points = {
      wide: pair((s) => V(s * HANDS.wide, BAR, CROSS.z)),
      standard: pair((s) => V(s * HANDS.standard, BAR, CROSS.z)),
      // On the V's arm, as far along it from the middle as across.
      close: pair((s) => V(s * HANDS.close, BAR, WALL - ((WALL - CROSS.z) * HANDS.close) / INNER.x)),
      neutral: pair((s) => V(s * HANDS.neutral, BAR, CROSS.z - HANDS.neutralOut)),
      dip: pair((s) => along(s, DIP.hand)),
      dipClose: pair((s) => along(s, DIP.close)),
    };
    return {
      parts: [...frame, ...bar, ...handles],
      drive() {
        const fresh = {};
        for (const [k, v] of Object.entries(points)) fresh[k] = v.map((p) => p.clone());
        return { points: fresh };
      },
    };
  },
});
