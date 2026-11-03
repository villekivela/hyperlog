// A cable station for a high pulley: two uprights either side of a weight stack `column` metres in
// front (z, less 12 cm), a boom forward to the pulley at `pulley` [y, z], whose front the cable
// meets, and a second wheel over the stack. The cable runs from the attachment in the hands over
// both wheels and down to the stack. `attachment` is `lat_bar`, `straight_bar`, `v_handle` or
// `handles` (two stirrups `spread` either side); `seat` [y, z] adds a seat and `thighPad` [y, z] the
// rollers over the knees, for a pulldown.
//
// The attachment is a free body, carried by the hands. The stack rides its guide rods on a
// prismatic joint, its value how far the cable is drawn out past where the stack rests, and rises
// one for one with it, up to 1.2 m, short of its guide rods' tops.
import * as THREE from 'three';
import { recipe, free, prismatic } from '../recipe.mjs';
import {
  ATTACHMENTS, UPRIGHTS, WHEEL, clevis, endCap, pad, part, pulley, rod, rodGeometry, seatOn, span, stackFrame, thighRollers, tube, weightStack,
} from '../parts.mjs';
import { V, UP, gripTarget } from '../rig.mjs';

export default recipe({
  name: 'cable_tower',
  node: 'cable',
  bodies: { handle: free(), stack: prismatic({ axis: UP, limits: [0, 1.2] }) },
  contacts: [{ body: 'handle', point: 'grips', landmark: 'hands' }],
  stacks: [{ joint: 'stack', ratio: 1 }],
  build({ column = 0.9, pulley: [py, pz] = [2.2, 0], attachment = 'straight_bar', spread, seat = null, thighPad = null } = {}, M) {
    if (py < 1.5) throw new Error(`The cable tower's pulley at ${py} m is low; it has only a high pulley yet`);
    // The stack stands behind the pulley, far enough back for its own wheel.
    const stackZ = Math.max(column - 0.12, pz + 4 * WHEEL + 0.08);
    const ux = UPRIGHTS;
    const wheelAt = V(0, py, pz);
    const frontWheel = V(0, py, pz + WHEEL);
    const backWheel = V(0, py, stackZ - WHEEL);
    const boomY = py + WHEEL + 0.02 + 0.035;
    const rodTop = Math.min(2.0, boomY - 0.3);
    const frontZ = seat ? seat[1] - 0.25 : stackZ - 0.32;
    const parts = [
      ...stackFrame(stackZ, boomY, M),
      tube(0.07, V(0, boomY, stackZ + 0.07), V(0, boomY, Math.min(pz, stackZ) - 0.02), M.frame),
      tube(0.05, V(-ux, rodTop + 0.025, stackZ), V(ux, rodTop + 0.025, stackZ), M.frame),
      tube(0.07, V(-ux - 0.15, 0.035, stackZ), V(ux + 0.15, 0.035, stackZ), M.frame),
      tube(0.07, V(0, 0.035, stackZ - 0.035), V(0, 0.035, frontZ - 0.035), M.frame),
      tube(0.07, V(-0.3, 0.035, frontZ), V(0.3, 0.035, frontZ), M.frame),
      ...pulley(frontWheel, M),
      ...pulley(backWheel, M),
      // The cable over the wheels' tops, from the front one back to the one over the stack.
      rod(0.0045, V(0, py + WHEEL, frontWheel.z), V(0, py + WHEEL, backWheel.z), M.metal),
    ];
    const cable = part(rodGeometry(0.0045, 8), M.metal);
    const drop = part(rodGeometry(0.0045, 8), M.metal);
    const weights = weightStack(V(0, 0, stackZ), M, { rodTop, toward: -1 });
    if (seat) parts.push(...seatOn(seat[0], seat[1], M));
    else {
      // Standing at it, the tower has its low pulley too: a wheel in its housing on the floor rail in
      // front of the stack, its cable back under the stack and a ring at its front to hook on to, and
      // caps on the front rail's ends.
      const low = V(0, 0.07 + WHEEL + 0.012, stackZ - 0.17);
      parts.push(
        ...pulley(low, M),
        pad(0.06, 0.012, 0.12, M.frame, V(0, 0.076, low.z), { soft: 0.003, solid: false }),
        rod(0.0045, V(0, low.y - WHEEL, low.z), V(0, low.y - WHEEL, stackZ - 0.06), M.metal),
        clevis(M, 0).translateY(low.y).translateZ(low.z - WHEEL - 0.016).rotateY(Math.PI / 2),
        ...[-0.3, 0.3].map((x) => endCap(x, frontZ, M)),
      );
    }
    if (thighPad) parts.push(...thighRollers(thighPad[0], thighPad[1], M));
    if (!ATTACHMENTS[attachment]) throw new Error(`No cable attachment "${attachment}"`);
    const { parts: grip, hook: above } = ATTACHMENTS[attachment](M, { spread });
    const handle = new THREE.Group();
    handle.name = 'cable_handle';
    grip.forEach((m, i) => { m.name = `cable_handle_${i}`; handle.add(m); });
    const hook = (P) => P.bar.clone().add(V(0, above, 0));
    return {
      parts: [...parts, cable, drop, ...weights.parts, handle],
      // The stack's plates are raised by its own lift, not moved as a body.
      bodies: { stack: [] },
      cables: [{ cable, hook: () => handle.localToWorld(V(0, above, 0)) }],
      stacks: {
        stack: {
          lift(l) {
            weights.lift(l);
            span(drop, V(0, py, stackZ), weights.top(l));
          },
        },
      },
      drive(P, P0) {
        handle.position.copy(P.bar);
        const at = hook(P);
        span(cable, at, wheelAt);
        return {
          joints: { stack: { value: Math.max(0, at.distanceTo(wheelAt) - hook(P0).distanceTo(wheelAt)), origin: V(0, 0, stackZ) } },
          points: { grips: [-1, 1].map((s) => gripTarget(P, s)) },
        };
      },
    };
  },
});
