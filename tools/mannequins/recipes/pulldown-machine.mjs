// A plate-loaded iso-lateral pulldown, laid out as the Gymleco 011: a seat with a back pad behind
// the lifter (its face `pad` metres behind the spine) and pads over the thighs at `thighPad`
// [y, z] hanging under their bar, facing a portal ahead whose beam carries the levers' axle at the
// pivot. Each lever runs back over the seat to a handle and on past the axle to a loaded
// horn, whose plate rises as the handles come down. A brace from the beam to the floor
// carries the thigh pads' bar.
//
// The levers are one body on a revolute joint at the pivot, its value the arms' angle up from
// level ahead (they reach back over the seat, so past a quarter turn).
import { recipe, revolute } from '../recipe.mjs';
import { backPad, floorRail, levers, pad, part, placedRail, rodGeometry, seatOn, span, stretchedTube, swungHandles, tube } from '../parts.mjs';
import { V, kneeAt, rad } from '../rig.mjs';

// The levers' pivot on the portal, [y, z], and how far it is back along them to the handles.
const PIVOT = [1.29, 0.85];
const ARM = 0.7484;
// The handles this far either side of the middle.
const HANDLES_OUT = 0.32;
// How far forward of the hips the thigh pads bear, half their thickness, and how far the thigh
// tapers there from its thickest.
const PADS_AT = 0.3;
const PAD_HALF = 0.035;
const TAPER = 0.01;
const SEAT = [0.47, 0.02];
/** The hips on the seat at [y, z]. */
const hipOn = ([y, z]) => [y + 0.09, z - 0.02];

export default recipe({
  name: 'pulldown_machine',
  // From the arms straight overhead to the handles down past the shoulders.
  bodies: { levers: revolute({ axis: V(-1, 0, 0), limits: [rad(154), rad(220)] }) },
  rep: { joint: 'levers', from: 'min' },
  contacts: [
    { body: 'levers', point: 'handles', landmark: 'hands' },
    { body: 'frame', point: 'thighPads', landmark: 'thighs' },
    { body: 'frame', point: 'backPad', landmark: 'back' },
  ],
  fit: [
    {
      sets: 'thighPad',
      rule: 'the pads snug over the thighs',
      solve: ({ L, options: { seat = SEAT }, entry }) => {
        const hip = V(0, ...hipOn(seat));
        const knee = kneeAt(hip, V(0, L.ankleY, entry.feet.z), L, V(0, 1, 0.6).normalize());
        const z = hip.z + PADS_AT;
        return [hip.y + ((knee.y - hip.y) * PADS_AT) / (knee.z - hip.z) + L.thighTop - TAPER + PAD_HALF, z];
      },
    },
    { sets: 'pad', rule: 'the back pad against the spine', solve: ({ L }) => L.back },
  ],
  linkage: ({ seat = SEAT }) => ({ hip: hipOn(seat), at: swungHandles(PIVOT, ARM, HANDLES_OUT) }),
  build({ seat = SEAT, thighPad: [ty, tz] = [0.71, 0.3], pad: behind = 0.13 } = {}, M) {
    const out = 0.42;
    const post = out - 0.07;
    const arms = levers(M, { out, horn: { length: 0.35, turn: 180 } });
    const backRest = backPad(0.32, 0.56, M, { up: 0.5, behind });
    const beam = stretchedTube(0.08, M.frame);
    const uprights = [-1, 1].map(() => stretchedTube(0.08, M.frame));
    const axle = part(rodGeometry(0.02), M.metal);
    const brace = stretchedTube(0.07, M.frame);
    const padArm = stretchedTube(0.05, M.frame);
    const backPost = stretchedTube(0.06, M.frame);
    const spine = placedRail(M);
    const front = placedRail(M);
    const thighPads = [-1, 1].map((s) => pad(0.2, 0.07, 0.11, M.vinyl, V(s * 0.12, ty, tz), { soft: 0.025 }));
    const barY = ty + 0.035 + 0.02;
    return {
      parts: [
        ...seatOn(seat[0], seat[1], M, { pin: -1 }),
        ...floorRail(V(-0.25, 0.035, seat[1] - 0.3), V(0.25, 0.035, seat[1] - 0.3), M),
        ...spine.parts, ...front.parts, ...thighPads,
        // The pads hang under their bar, on the lifter's side of it.
        tube(0.04, V(-0.22, barY, tz), V(0.22, barY, tz), M.frame),
        backRest.mesh, ...arms.parts, beam, ...uprights, axle, brace, padArm, backPost,
      ],
      bodies: { levers: arms.parts },
      drive(P, P0) {
        const joint = arms.place(P);
        const { pivot } = P;
        spine.set(V(0, 0, seat[1] - 0.35), V(0, 0, pivot.z + 0.1));
        front.set(V(-post - 0.15, 0, pivot.z), V(post + 0.15, 0, pivot.z));
        span(beam, pivot.clone().setX(-post - 0.04), pivot.clone().setX(post + 0.04));
        span(axle, pivot.clone().setX(-out - 0.03), pivot.clone().setX(out + 0.03));
        [-1, 1].forEach((s, i) => span(uprights[i], V(s * post, 0.07, pivot.z), pivot.clone().setX(s * post).add(V(0, 0.04, 0))));
        // The brace leans from under the beam down to the floor before the shins, the thigh pads' arm off it.
        const foot = V(0, 0.07, seat[1] + 0.62);
        const under = pivot.clone().setX(0).add(V(0, -0.04, 0));
        span(brace, foot, under);
        span(padArm, V(0, barY, tz), foot.clone().lerp(under, (barY - foot.y) / (under.y - foot.y)));
        const support = backRest.fit(P0);
        span(backPost, support, support.clone().setY(0.07));
        return {
          joints: { levers: joint },
          points: { handles: [-1, 1].map((s) => P.handle(s).clone()), thighPads: thighPads.map((m) => m.position.clone()), backPad: [backRest.mesh.position.clone()] },
        };
      },
    };
  },
});
