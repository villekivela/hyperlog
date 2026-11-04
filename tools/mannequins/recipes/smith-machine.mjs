// A plate-loaded smith machine with its bar on two guide rods (refs/smith_machine/ref.md): a front
// upright either side, leaning 7° with its top toward the back, carries the rod on its inner face
// and, behind the rod, a plate of 13 hook notches 152 mm apart, both from the floor beam to a hood
// at the top; a rear upright either side, leaning with them, wrapped in a pad, carries four plate horns
// through its outer face. Crossbars join the front uprights at the top and the rear ones at the middle.
// Each carriage is a block round its rod, with the bar through it and a hook lug that rests on a
// notch; a safety stop lies on the plate, and the bar can't go below it.
//
// The bar is one body on a prismatic joint along the rods; its value is the metres it has moved
// along them from 250 mm above the floor. The hooks are fixed on the bar, as its carriages are, at
// the notch it hangs on: turning them is the wrists' (the ref gives it, about 45°, with no frame
// of it), and a joint turning about the bar would be left behind by one sliding along the rods.
// The frame stands where the ref puts it, whatever the pose: `P.bar` is the bar's value, or the
// bar hangs on its hook where the pose frame gives none.
import * as THREE from 'three';
import { prismatic, recipe } from '../recipe.mjs';
import { along, floorRail, padGeometry, part, plateGeometry, rod, tube, turnedPart } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

const LEAN = rad(7);
// Up the rods, toward the back.
const UP = V(0, Math.cos(LEAN), -Math.sin(LEAN));
// The rod's foot, 250 mm above the floor, in the middle of the machine; the rods stand 1.3 m apart.
const FOOT = V(0, 0.25, 0.18);
const ROD = 0.65;
const RODS_TOP = 2.3;
// The upright is 90 mm square, beside the rod and the plate and behind the rod's front.
const UPRIGHT = { x: 0.71, w: 0.09, back: 0.085 };
const HEIGHT = 2.357;
// The hook plate stands behind the rod, its notches 152 mm apart from 290 mm up: 13, the highest
// at 2.11 m. The carriage's lug rests on a notch and the stop's top is a carriage's half height below.
const NOTCH = { lowest: 0.29, pitch: 0.152, count: 13 };
const BEHIND = 0.15;
// The plate is welded to the upright's inner face, in line with the rod and the carriage's lug.
const PLATE = UPRIGHT.x - UPRIGHT.w / 2 - 0.006;
const CARRIAGE = 0.12;
const BLOCK = 0.07;
// The bar: 2.1 m, its sleeves outside the rods.
const BAR = { half: 1.05, grip: 0.0125 };
// The rear uprights rise from 300 mm behind the machine's middle, leaning with the rods, with four horns a side.
const REAR = { z: -0.3, pad: 1.8 };
// The horns stand out of the pad's outer face, 200 mm.
const HORN = { x: UPRIGHT.x + UPRIGHT.w / 2 + 0.015, length: 0.2 };
const HORNS = [0.5, 0.93, 1.36, 1.79];
// The floor beams run 1.24 m along the machine.
const FLOOR = { front: 0.62, back: -0.62 };

/** Where the rear upright's middle is, `y` up, behind the middle. */
const rearAt = (y) => REAR.z - (y - 0.07) * Math.tan(LEAN);
const notchY = (k) => NOTCH.lowest + k * NOTCH.pitch;
/** The bar's value, in metres along the rods from 250 mm up, when its height is `y`. */
const valueAt = (y) => (y - FOOT.y) / Math.cos(LEAN);
/** The point `s` metres up the rod's line, on side `x`, and `z` further forward. */
const onRod = (x, s, z = 0) => FOOT.clone().addScaledVector(UP, s).add(V(x, 0, z));
/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
const tilted = (mesh) => { mesh.rotation.x = -LEAN; return mesh; };
/** A leaning pad `w` (x) by `h` (up the lean) by `d` (across it) centred on `at`. */
const leaning = (w, h, d, material, at, { soft = 0.003, solid = false } = {}) => {
  const mesh = part(padGeometry(w, h, d, soft), material, { solid });
  mesh.position.copy(at);
  return tilted(mesh);
};

export default recipe({
  name: 'smith_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's
  // photos are.
  standsAlone: { camera: { target: [0, 1.1, 0], halfHeight: 1.5, azimuth: -40, elevation: 14 } },
  bodies: ({ stops = 0 } = {}) => ({
    bar: prismatic({ axis: UP, limits: [valueAt(notchY(stops)), valueAt(RODS_TOP - 0.25)] }),
  }),
  // A pose holds the bar in its hands, or on its upper back.
  contacts: [
    { body: 'bar', point: 'grips', landmark: 'hands' },
    { body: 'bar', point: 'back', landmark: 'shoulders' },
  ],
  fit: [
    {
      sets: 'hook',
      rule: 'the notch one below the bar\'s standing height, for a short push to unrack it: about 1.35 m for a squat or a lunge, numbered from the lowest notch',
    },
    {
      sets: 'stops',
      rule: 'the notch just below the bottom of the lift, numbered from the lowest: the bar can go no lower',
    },
  ],
  // `hook`: the notch the bar hangs on; `stops`: the notch the safety stops lie on.
  build({ hook = 7, stops = 0 } = {}, M) {
    const frame = [];
    const sides = [-1, 1];
    const topAt = HEIGHT - 0.035;
    // The floor beams, with the front uprights on them about 530 mm from their front ends.
    for (const s of sides) {
      const [beam, ...caps] = floorRail(V(s * UPRIGHT.x, 0.035, FLOOR.back), V(s * UPRIGHT.x, 0.035, FLOOR.front), M);
      frame.push(role(beam, 'beam'), ...caps);
      const base = onRod(s * UPRIGHT.x, valueAt(0.07), -UPRIGHT.back);
      const top = onRod(s * UPRIGHT.x, valueAt(HEIGHT), -UPRIGHT.back);
      frame.push(role(tube(UPRIGHT.w, base, top, M.frame), 'upright'));
      // The rod on the upright's inner face, bracketed at each end, with a rubber stop at its foot.
      const rodAt = (along) => onRod(s * ROD, along);
      frame.push(role(rod(0.0125, rodAt(valueAt(0.2)), rodAt(valueAt(RODS_TOP)), M.metal), 'guide'));
      for (const y of [0.2, RODS_TOP]) {
        frame.push(role(leaning(0.07, 0.05, 0.1, M.pulley, onRod(s * ROD, valueAt(y), -0.03), { solid: true }), 'bracket'));
      }
      frame.push(leaning(0.05, 0.03, 0.05, M.vinyl, onRod(s * ROD, valueAt(0.235)), { soft: 0.006 }));
      // The hook plate behind it, on the floor beam and up to the hood: a spine and a tooth under each lug.
      frame.push(role(leaning(0.012, (2.25 - 0.13) / Math.cos(LEAN), 0.04, M.metal, onRod(s * PLATE, valueAt(1.19), -BEHIND - 0.02)), 'spine'));
      for (let k = 0; k < NOTCH.count; k++) {
        frame.push(role(leaning(0.012, 0.07, 0.04, M.metal, onRod(s * PLATE, valueAt(notchY(k) - 0.05), -BEHIND + 0.02)), 'tooth'));
      }
      // The safety stop on the plate: a block under the carriage, its top a carriage's half height below the bar.
      frame.push(role(leaning(0.03, 0.03, 0.24, M.vinyl, onRod(s * PLATE, valueAt(notchY(stops) - CARRIAGE / 2 - 0.015), -0.07), { soft: 0.006, solid: true }), 'stop'));
      // The hood from the top of the front upright back to the rear upright.
      const hoodFrom = onRod(s * UPRIGHT.x, valueAt(topAt), -UPRIGHT.back);
      frame.push(role(tube(0.07, hoodFrom, V(s * UPRIGHT.x, hoodFrom.y, rearAt(hoodFrom.y)), M.frame), 'hood'));
      // The rear upright, its pad inside it and four horns outside.
      frame.push(role(tube(UPRIGHT.w, V(s * UPRIGHT.x, 0.07, REAR.z), V(s * UPRIGHT.x, HEIGHT, rearAt(HEIGHT)), M.frame), 'upright'));
      const middle = 0.18 + REAR.pad / 2;
      frame.push(role(leaning(UPRIGHT.w + 0.03, REAR.pad / Math.cos(LEAN), 0.24, M.vinyl, V(s * UPRIGHT.x, middle, rearAt(middle)), { soft: 0.012, solid: true }), 'cushion'));
      for (const y of HORNS) {
        frame.push(role(rod(0.025, V(s * (HORN.x), y, rearAt(y)), V(s * (HORN.x + HORN.length), y, rearAt(y)), M.metal), 'horn'));
        frame.push(turnedPart(plateGeometry(0.04, 0.02), M.pulley, V(s * (HORN.x + 0.01), y, rearAt(y))));
      }
    }
    // The crossbars: between the front uprights at the top, between the rear ones at the middle.
    const topFront = onRod(0, valueAt(topAt - 0.03), -UPRIGHT.back);
    frame.push(tube(0.07, topFront.clone().setX(-UPRIGHT.x), topFront.clone().setX(UPRIGHT.x), M.frame));
    frame.push(tube(0.07, V(-UPRIGHT.x, 1.95, rearAt(1.95)), V(UPRIGHT.x, 1.95, rearAt(1.95)), M.frame));

    // The bar and its carriages, in the bar's own frame, about its middle: the shaft between the
    // carriages, a sleeve beyond each, a block round each rod and a lug back to the plate.
    const bar = new THREE.Group();
    const shaft = along(BAR.grip, -ROD, ROD, M.metal, 24, { held: true });
    role(shaft, 'shaft');
    bar.add(shaft);
    for (const s of sides) {
      const outward = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
      bar.add(role(along(0.025, ...outward(ROD, BAR.half), M.metal, 24, { solid: true }), 'sleeve'));
      bar.add(role(leaning(BLOCK, CARRIAGE, BLOCK, M.pulley, V(s * ROD, 0, 0), { soft: 0.01, solid: true }), 'carriage'));
      bar.add(role(leaning(0.02, 0.03, BEHIND - 0.02, M.pulley, V(s * PLATE, 0, -(BEHIND - 0.02) / 2 - 0.02)), 'lug'));
    }

    const hung = Math.max(valueAt(notchY(hook)), valueAt(notchY(stops)));
    return {
      parts: [...frame, bar],
      bodies: { bar: [bar] },
      drive(P) {
        const value = P.bar ?? hung;
        bar.position.copy(FOOT).addScaledVector(UP, value);
        bar.updateMatrix();
        const onBar = (xs) => xs.map((x) => V(x, 0, 0).applyMatrix4(bar.matrix));
        return {
          joints: { bar: { value } },
          points: { grips: onBar([-0.3, 0.3]), back: onBar([-0.2, 0.2]) },
        };
      },
    };
  },
});
