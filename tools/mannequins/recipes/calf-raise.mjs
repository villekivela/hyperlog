// A plate-loaded seated calf raise, after a Hammer Strength-style one (#150): a beam along the floor
// with feet across both ends, the seat on its post at the back, a low foot block across the beam,
// the balls of the feet on its back edge, and a post in front of the toes. The lever pivots on the
// post's head, about as high as the pads, and runs back over the shins to the pads on the thighs
// just behind the knees, a crossbar over them with a U-handle on top; a horn either side behind the
// pivot is loaded with a 20. The lever is flat at the stretch, where a peg on the post sits a few
// centimetres under it, and turns up with the knees, the pads riding the thighs.
//
// The lever is a body on a revolute joint at the pivot, its value how far it has turned up from the
// stretch.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  bent, floorRail, hub, knob, movableRail, pad, padGeometry, part, plateGeometry, popPin, railCap, rodGeometry, seatOn, span, stretchedTube,
} from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

export default recipe({
  name: 'calf_raise_machine',
  node: 'calf_raise',
  bodies: { lever: revolute({ axis: X, limits: [0, rad(25)] }) },
  contacts: [
    { body: 'lever', point: 'pads', landmark: 'thighs' },
    { body: 'lever', point: 'grips', landmark: 'hands' },
  ],
  fit: [{ sets: 'seat', rule: 'the pads on the thighs just behind the knees' }],
  build({ seat = [0.48, -0.05] } = {}, M) {
    const [seatY, seatZ] = seat;
    // What stands still: the base, the seat, the foot block, and the post with the pivot and its peg.
    const beam = stretchedTube(0.07, M.frame);
    const beamCaps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
    const frontFeet = movableRail(M);
    const block = part(padGeometry(0.46, 0.12, 0.14, 0.006), M.frame, { solid: true });
    const deck = part(padGeometry(0.44, 0.006, 0.12, 0.002), M.metal);
    const post = stretchedTube(0.07, M.frame);
    const postPlate = pad(0.12, 0.012, 0.12, M.frame, V(0, 0, 0), { soft: 0.003, solid: false });
    const cheeks = [-1, 1].map(() => part(padGeometry(0.012, 0.13, 0.11, 0.003), M.frame));
    const boss = hub(M, V(0, 0, 0));
    const axle = part(rodGeometry(0.008, 12), M.metal);
    const peg = part(rodGeometry(0.014, 12), M.metal);
    const pegKnob = knob(M, V(0, 0, 0), 0.02);
    // The lever, built flat at the stretch about the pivot and turned there as one.
    const lever = new THREE.Group();
    lever.name = 'calf_raise_lever';
    const arm = stretchedTube(0.07, M.frame);
    const drop = stretchedTube(0.055, M.frame);
    const pin = popPin(V(0, 0, 0), 1, M);
    const crossbar = part(padGeometry(0.46, 0.014, 0.13, 0.003), M.frame);
    const kneePads = [-1, 1].map(() => part(padGeometry(0.19, 0.07, 0.14, 0.025), M.vinyl, { solid: true }));
    const handle = bent('calf_raise_u', [V(-0.13, 0, 0), V(-0.13, 0.1, 0), V(-0.1, 0.14, 0), V(0.1, 0.14, 0), V(0.13, 0.1, 0), V(0.13, 0, 0)], 0.016, M.frame);
    const grips = [-1, 1].map(() => part(rodGeometry(0.0195), M.vinyl, { held: true }));
    const horns = [-1, 1].map(() => ({
      horn: part(rodGeometry(0.025, 20), M.metal, { solid: true }),
      collar: part(rodGeometry(0.045, 24), M.metal),
      plate: part(plateGeometry(0.225, 0.05), M.plate, { solid: true }),
    }));
    lever.add(arm, drop, pin, crossbar, ...kneePads, handle, ...grips, ...horns.flatMap((h) => [h.horn, h.collar, h.plate]));
    return {
      parts: [
        ...seatOn(seatY, seatZ, M, { pin: -1 }),
        ...floorRail(V(-0.3, 0.035, seatZ - 0.17), V(0.3, 0.035, seatZ - 0.17), M),
        beam, ...beamCaps, ...frontFeet.parts, block, deck, post, postPlate, ...cheeks, boss, axle, peg, pegKnob, lever,
      ],
      bodies: { lever: [lever] },
      drive(P, P0, P1) {
        // The foot block, the balls on its back edge and the heels hanging off behind it.
        const ball = P0.platform;
        block.position.set(0, ball.y - 0.006 - 0.06, ball.z + 0.03);
        deck.position.set(0, ball.y - 0.003, ball.z + 0.03);
        // The pads sit 6 cm over the knees at the stretch, on the thighs a hand's breadth back from
        // them; the pivot, the pose's, stands in front of the toes, a little above the pads' undersides.
        const knee = P1.knee;
        const padZ = knee.z - 0.05;
        const pivot = P0.pivot;
        lever.position.copy(pivot);
        // The base, from behind the seat to the front feet under the post, and the post up to the pivot.
        const rear = seatZ - 0.17;
        const front = pivot.z + 0.1;
        span(beam, V(0, 0.035, rear), V(0, 0.035, front));
        railCap(beamCaps[0], V(0, 0, rear - 0.035), V(0, 0, -1));
        railCap(beamCaps[1], V(0, 0, front + 0.035), V(0, 0, 1));
        frontFeet.set(front, 0.4);
        span(post, V(0, 0.07, pivot.z + 0.07), V(0, pivot.y + 0.05, pivot.z + 0.07));
        postPlate.position.set(0, 0.076, pivot.z + 0.07);
        cheeks.forEach((c, i) => c.position.set((i ? 1 : -1) * 0.042, pivot.y, pivot.z + 0.035));
        boss.position.set(0.06, pivot.y, pivot.z);
        span(axle, V(-0.06, pivot.y, pivot.z), V(0.09, pivot.y, pivot.z));
        // The peg out of the post's back, under the lever at the stretch: a working lever is held off it.
        const pegY = pivot.y - 0.035 - 0.04;
        span(peg, V(0, pegY, pivot.z + 0.035), V(0, pegY, pivot.z - 0.09));
        pegKnob.position.set(0, pegY, pivot.z - 0.09);
        // The lever in the pivot's frame: the arm back to the pads, a drop down to their crossbar, the
        // U-handle on it and the horns behind the pivot.
        const local = (y, z) => V(0, y - pivot.y, z - pivot.z);
        const padBottom = knee.y + 0.06;
        const bar = padBottom + 0.077;
        span(arm, local(pivot.y, pivot.z + 0.035), local(pivot.y, padZ + 0.03));
        span(drop, local(pivot.y + 0.035, padZ + 0.03), local(bar, padZ + 0.03));
        pin.position.copy(local(pivot.y - 0.01, padZ + 0.09));
        crossbar.position.copy(local(bar, padZ));
        kneePads.forEach((m, i) => m.position.copy(local(padBottom + 0.035, padZ)).setX((i ? 1 : -1) * 0.11));
        handle.position.copy(local(bar + 0.007, padZ));
        grips.forEach((m, i) => span(m, local(bar + 0.02, padZ).setX((i ? 1 : -1) * 0.13), local(bar + 0.1, padZ).setX((i ? 1 : -1) * 0.13)));
        horns.forEach(({ horn, collar, plate }, i) => {
          const s = i ? 1 : -1;
          const at = local(pivot.y, pivot.z - 0.12);
          span(horn, at, at.clone().setX(s * 0.42));
          span(collar, at.clone().setX(s * 0.2), at.clone().setX(s * 0.22));
          plate.position.copy(at).setX(s * 0.25);
        });
        // The lever turns about the pivot with the knees, as the pose turns it: the pads ride the thighs.
        lever.rotation.x = -P.turn;
        lever.updateWorldMatrix(true, false);
        const onLever = (m) => m.position.clone().applyMatrix4(lever.matrixWorld);
        return {
          joints: { lever: { value: -P.turn, origin: pivot.clone() } },
          points: { pads: kneePads.map(onLever), grips: [-1, 1].map((s) => P.hand(s).clone()) },
        };
      },
    };
  },
});
