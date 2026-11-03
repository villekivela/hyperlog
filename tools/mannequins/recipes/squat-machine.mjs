// A plate-loaded hack squat, after a Hammer Strength-style machine (#150): a base along the floor
// with feet across both ends; the foot platform low in front, tilted toes up on its legs; two chrome
// rails along the hips' path, from a bracket just behind the platform up to a capped post at the
// rear, braced back to the base and carrying a storage horn a side; and the carriage riding the
// rails on bearings. The carriage holds the back pad, its face `pad` metres behind the hips' line,
// a pad over each shoulder, a handle by each that lies along the hand's knuckles, and a horn a side
// loaded with a 20. It moves as one, along the rails, with the hips.
//
// The carriage is a body on a prismatic joint up the rails, its value how far it is above its stops.
import * as THREE from 'three';
import { recipe, prismatic } from '../recipe.mjs';
import { knob, movableRail, pad, padGeometry, part, plateGeometry, railCap, rodGeometry, span, stretchedTube } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

/** How far either side of the middle a hack squat's rails run. */
const HACK_RAILS = 0.17;
// The hips on the carriage at its stops, [y, z], and the way up the rails: 36.6° from upright.
const STOPS = [0.5416, 0.3977];
const SLED = V(0, 0.8029, -0.5961);
// The platform's face under the ankles, tilted toes up.
const FACE = V(0, 0.2415, 0.545);
const TILT = rad(20);
// The handles on the carriage, from the hips, [y, z], this far either side of the middle.
const HANDLES = [0.4392, -0.4948];
const HANDLES_OUT = 0.3;

export default recipe({
  name: 'squat_machine',
  bodies: { carriage: prismatic({ limits: [0, 0.7] }) },
  contacts: [
    { body: 'carriage', point: 'shoulderPads', landmark: 'shoulders' },
    { body: 'carriage', point: 'handles', landmark: 'hands' },
  ],
  rep: { joint: 'carriage', from: 'max' },
  fit: [{ sets: 'pad', rule: 'the back on the pad, the shoulders under their pads', solve: ({ L }) => L.back }],
  machine: () => ({
    at: (value) => {
      const hipC = V(0, ...STOPS).addScaledVector(SLED, value);
      return { hipC, sled: SLED, face: FACE, tilt: TILT, hand: (s) => hipC.clone().add(V(s * HANDLES_OUT, ...HANDLES)) };
    },
  }),
  build({ pad: behind = 0.2 } = {}, M) {
    // What stands still: the base, the platform and its legs, the rails, their bracket and cap, and
    // the rear post with its brace and horns.
    const beam = stretchedTube(0.07, M.frame);
    const beamCaps = [0, 1].map(() => pad(0.016, 0.074, 0.074, M.vinyl, V(0, 0, 0), { soft: 0.005, solid: false }));
    const feet = [0, 1].map(() => movableRail(M));
    const platform = part(padGeometry(0.76, 0.035, 0.58, 0.008), M.frame, { solid: true });
    const deck = part(padGeometry(0.7, 0.006, 0.52, 0.002), M.metal);
    const platformLegs = [-1, 1].map(() => stretchedTube(0.06, M.frame));
    const platformBack = stretchedTube(0.05, M.frame);
    const rails = [-1, 1].map(() => part(rodGeometry(0.025, 20), M.metal, { solid: true }));
    const bracket = part(padGeometry(0.46, 0.07, 0.1, 0.006), M.frame, { solid: true });
    const bracketPost = stretchedTube(0.06, M.frame);
    const cap = part(padGeometry(0.48, 0.08, 0.12, 0.008), M.frame, { solid: true });
    const post = stretchedTube(0.08, M.frame);
    const postPlate = pad(0.13, 0.012, 0.13, M.frame, V(0, 0, 0), { soft: 0.003, solid: false });
    const brace = stretchedTube(0.06, M.frame);
    const storage = [-1, 1].map(() => part(rodGeometry(0.025, 20), M.metal));
    // The carriage, placed at the top of the rep and moved as one with the hips.
    const carriage = new THREE.Group();
    carriage.name = 'squat_machine_carriage';
    const cushion = part(padGeometry(0.42, 0.62, 0.08, 0.025), M.vinyl, { solid: true });
    const board = part(padGeometry(0.38, 0.58, 0.012, 0.003), M.frame);
    const members = [-1, 1].map(() => stretchedTube(0.05, M.frame));
    const crossbars = [0, 1].map(() => stretchedTube(0.05, M.frame));
    const bearings = [0, 1, 2, 3].map(() => part(padGeometry(0.075, 0.14, 0.075, 0.01), M.pulley));
    const shoulderPads = [-1, 1].map(() => part(padGeometry(0.12, 0.08, 0.24, 0.025), M.vinyl, { solid: true }));
    const shoulderBoards = [-1, 1].map(() => part(padGeometry(0.1, 0.012, 0.22, 0.003), M.frame));
    const mounts = [-1, 1].map(() => stretchedTube(0.05, M.frame));
    const grips = [-1, 1].map(() => ({
      bar: part(rodGeometry(0.016), M.metal, { held: true }),
      sleeve: part(rodGeometry(0.0195), M.vinyl, { held: true }),
      end: knob(M, V(0, 0, 0), 0.022),
      arm: stretchedTube(0.04, M.frame),
    }));
    const horns = [-1, 1].map(() => ({
      horn: part(rodGeometry(0.025, 20), M.metal, { solid: true }),
      stop: part(rodGeometry(0.045, 24), M.metal),
      plate: part(plateGeometry(0.225, 0.05), M.plate, { solid: true }),
    }));
    carriage.add(cushion, board, ...members, ...crossbars, ...bearings, ...shoulderPads, ...shoulderBoards, ...mounts,
      ...grips.flatMap((h) => [h.bar, h.sleeve, h.end, h.arm]), ...horns.flatMap((h) => [h.horn, h.stop, h.plate]));
    const parts = [
      beam, ...beamCaps, ...feet.flatMap((f) => f.parts), platform, deck, ...platformLegs, platformBack,
      ...rails, bracket, bracketPost, cap, post, postPlate, brace, ...storage, carriage,
    ];
    return {
      parts,
      bodies: { carriage: [carriage] },
      drive(P, P0) {
        // The carriage's frame at the top of the rep: `sled` up the rails, `face` out of the back pad's
        // face, from the `origin` on the pad's face at the hips.
        const sled = P0.sled.clone();
        const face = X.clone().cross(sled).normalize();
        const origin = P0.hipC.clone().addScaledVector(face, -behind);
        const at = (along, out, x = 0) => origin.clone().addScaledVector(sled, along).addScaledVector(face, out).setX(x);
        const facing = new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, sled, face));
        const lay = (mesh, along, out, x = 0) => { mesh.position.copy(at(along, out, x)); mesh.quaternion.copy(facing); };
        const shoulder = P0.shoulders.clone().sub(origin).dot(sled);
        const top = shoulder + 0.12;
        // The rails, behind the carriage, from just above the floor to a cap 30 cm past the carriage's
        // top at the top of the rep.
        const railOut = -0.21;
        const bottom = (0.17 - origin.y - face.y * railOut) / sled.y;
        const end = top + 0.3;
        [-1, 1].forEach((side, i) => span(rails[i], at(bottom, railOut, side * HACK_RAILS), at(end, railOut, side * HACK_RAILS)));
        lay(bracket, bottom - 0.03, railOut);
        lay(cap, end + 0.04, railOut);
        const low = at(bottom - 0.03, railOut);
        span(bracketPost, low, low.clone().setY(0.07));
        // The rear post stands under the cap; a brace runs back from it to the base's rear end.
        const head = at(end + 0.04, railOut - 0.06);
        const rearZ = head.z - 0.3;
        span(post, head, head.clone().setY(0.012));
        postPlate.position.set(0, 0.006, head.z);
        span(brace, V(0, head.y * 0.5, head.z), V(0, 0.07, rearZ + 0.04));
        storage.forEach((h, i) => span(h, V(0, head.y * 0.62, head.z), V((i ? 1 : -1) * 0.26, head.y * 0.62, head.z)));
        // The platform, its face under the feet, its back edge just ahead of the heels; two legs down
        // from its front to the front feet, and a strut from its back to the base.
        const up = P0.plateUp.clone();
        const ahead = X.clone().cross(up).normalize();
        const middle = P0.face.clone().addScaledVector(up, -0.0175).addScaledVector(ahead, 0.13);
        platform.position.copy(middle);
        platform.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, up, ahead));
        deck.position.copy(middle).addScaledVector(up, 0.0205);
        deck.quaternion.copy(platform.quaternion);
        const front = middle.clone().addScaledVector(ahead, 0.18).addScaledVector(up, -0.02);
        const frontZ = front.z;
        platformLegs.forEach((leg, i) => span(leg, front.clone().setX((i ? 1 : -1) * 0.27), V((i ? 1 : -1) * 0.27, 0.07, frontZ)));
        const under = middle.clone().addScaledVector(ahead, -0.12).addScaledVector(up, -0.02);
        span(platformBack, under, V(0, 0.07, under.z));
        // The base: a beam from the rear feet to the front feet, capped at both ends.
        span(beam, V(0, 0.035, rearZ), V(0, 0.035, frontZ));
        railCap(beamCaps[0], V(0, 0, rearZ - 0.035), V(0, 0, -1));
        railCap(beamCaps[1], V(0, 0, frontZ + 0.035), V(0, 0, 1));
        feet[0].set(rearZ, 0.6);
        feet[1].set(frontZ, 0.45);
        // The carriage: the back pad on its board, two members behind it riding the rails on bearings,
        // a pad over each shoulder on a mount off the members' heads, and a loaded horn a side.
        const padMiddle = (shoulder - 0.03 - 0.15) / 2;
        lay(cushion, padMiddle, -0.04);
        lay(board, padMiddle, -0.086);
        [-1, 1].forEach((side, i) => {
          const x = side * HACK_RAILS;
          span(members[i], at(-0.2, -0.14, x), at(top, -0.14, x));
          [-0.12, shoulder].forEach((t, k) => lay(bearings[2 * i + k], t, railOut, x));
          lay(shoulderPads[i], shoulder + 0.11, 0.1, side * 0.155);
          lay(shoulderBoards[i], shoulder + 0.156, 0.1, side * 0.155);
          span(mounts[i], at(top, -0.14, x), at(shoulder + 0.16, -0.01, side * 0.155));
          // The handle runs the way the pose lays the grip at the top, fixed on the carriage: the
          // wrists turn the knuckles along it. It sits on an arm back to the shoulder pad's outer side.
          const { bar, sleeve, end: knobEnd, arm } = grips[i];
          const h = P.hand(side).sub(P.hipC).add(P0.hipC);
          const along = P0.across(side).normalize();
          const mount = at(shoulder + 0.11, 0.02, side * 0.225);
          const [near, far] = [-1, 1].map((k) => h.clone().addScaledVector(along, k * 0.1)).sort((a, b) => a.distanceTo(mount) - b.distanceTo(mount));
          span(bar, near, far);
          span(sleeve, h.clone().addScaledVector(along, -0.055), h.clone().addScaledVector(along, 0.055));
          knobEnd.position.copy(far);
          span(arm, near, mount);
          const { horn, stop, plate } = horns[i];
          const from = at(-0.05, -0.17, x);
          span(horn, from, from.clone().setX(side * 0.52));
          span(stop, from.clone().setX(side * 0.24), from.clone().setX(side * 0.26));
          plate.position.copy(from).setX(side * 0.3);
        });
        [-0.2, top].forEach((t, k) => span(crossbars[k], at(t, -0.14, -HACK_RAILS), at(t, -0.14, HACK_RAILS)));
        carriage.position.copy(P.hipC).sub(P0.hipC);
        const pads = shoulderPads.map((m) => m.position.clone().add(carriage.position));
        return {
          joints: { carriage: { value: P.machine.value, origin: P.hipC.clone(), axis: sled.clone() } },
          points: { shoulderPads: pads, handles: [-1, 1].map((s) => P.hand(s).clone()) },
        };
      },
    };
  },
});
