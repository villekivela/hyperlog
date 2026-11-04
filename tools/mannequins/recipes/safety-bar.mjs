// A cambered safety squat bar (refs/safety_bar/ref.md), unloaded: a straight 38 mm shaft 1310 mm
// long between two camber bends that carry the sleeves 140 mm below its centre line, a padded yoke
// round the shaft's middle, and two handle arms from the yoke reaching forward and down, each with
// a pad and then a bare steel grip. The lifter faces +z.
//
// Options: `shaft`, the shaft centre's height, 1.50 m by default, which the ref chooses for a
// standing mannequin (the shoulder joint at about 1.45 m plus 50 mm of trapezius); and `roll`, the
// degrees the bar is rolled forward about the shaft from how it hangs in a rack (the cambers
// straight down, the handles about 51° below horizontal), 0 by default. The lifter sets the roll by
// how they hold it: about 25° brings the handles to the 27° of the published 533 mm by 254 mm, and the
// plate centre forward of the shaft by the sine of it. Nothing on the bar moves, and it fits every
// height. Contacts: the hands on the bare grips (`grips`) and the shoulders under the yoke pad (`yoke`).
import * as THREE from 'three';
import { recipe } from '../recipe.mjs';
import { along, bent, knob, rod, turnedPart } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

const SHAFT = { radius: 0.019, half: 0.655 };
// The camber: from the straight shaft's end the tube bends down to the sleeve's centre line, which
// is 140 mm below the shaft's, at the collar face 755 mm out.
const CAMBER = { drop: 0.14, collar: 0.755 };
const SLEEVE = { radius: 0.025, from: 0.775, to: 1.181, ring: { radius: 0.032, from: 0.755 } };
const YOKE = { half: 0.165, radius: 0.05 };
// A handle arm from the shaft at 165 mm out to its tip 155 mm out, 537 mm along at 51° below
// horizontal as the bar hangs in a rack: the pad over 270 mm of it and a bare grip 177 mm long past it.
const REST = rad(51);
const HANDLE = { base: 0.165, tip: 0.155, length: 0.537, pad: { radius: 0.035, from: 0.05, to: 0.32 }, grip: { radius: 0.0127, from: 0.36 }, hand: 0.45 };
const SHAFT_HEIGHT = 1.5;

export default recipe({
  name: 'safety_bar',
  standsAlone: { camera: { target: [0, 1.2, 0.15], halfHeight: 1.6, azimuth: 35, elevation: 12 } },
  bodies: {},
  contacts: [
    { body: 'frame', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'yoke', landmark: 'shoulders' },
  ],
  fit: [{ sets: 'shaft', rule: 'the shaft across the upper trapezius behind the neck: about 1.50 m for a standing mannequin, the shoulder joint plus 50 mm' }],
  build({ shaft: height = SHAFT_HEIGHT, roll = 0 } = {}, M) {
    const sides = [-1, 1];
    const at = (x, y = 0, z = 0) => V(x, height + y, z);
    const camber = (s) => {
      const e = SHAFT.half;
      const c = CAMBER.collar;
      const bend = bent(`safety_camber:${s}`, [
        V(s * (e - 0.04), 0, 0), V(s * e, 0, 0), V(s * (e + 0.025), -0.035, 0), V(s * (e + 0.065), -0.105, 0),
        V(s * c, -CAMBER.drop, 0), V(s * (c + 0.03), -CAMBER.drop, 0),
      ], SHAFT.radius, M.frame, { solid: true });
      bend.position.copy(at(0));
      return bend;
    };
    const place = (mesh, y) => { mesh.position.y = height + y; return mesh; };
    const arm = (s) => {
      const base = at(s * HANDLE.base);
      const tip = base.clone().add(V(s * (HANDLE.tip - HANDLE.base), -HANDLE.length * Math.sin(REST), HANDLE.length * Math.cos(REST)));
      const dir = tip.clone().sub(base);
      const length = dir.length();
      dir.divideScalar(length);
      const point = (t) => base.clone().addScaledVector(dir, t);
      return {
        parts: [
          rod(SHAFT.radius, base, point(HANDLE.grip.from), M.frame, { solid: true }),
          rod(HANDLE.pad.radius, point(HANDLE.pad.from), point(HANDLE.pad.to), M.vinyl, { solid: true }),
          rod(HANDLE.grip.radius, point(HANDLE.grip.from), tip, M.metal, { held: true }),
          knob(M, tip, HANDLE.grip.radius),
        ],
        grip: point(HANDLE.hand),
      };
    };
    const arms = sides.map(arm);
    const parts = [
      place(along(SHAFT.radius, -SHAFT.half, SHAFT.half, M.frame, 24, { solid: true }), 0),
      place(along(YOKE.radius, -YOKE.half, YOKE.half, M.vinyl, 32, { solid: true }), 0),
      ...sides.map(camber),
      ...sides.flatMap((s) => {
        const ends = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
        return [
          place(along(SLEEVE.ring.radius, ...ends(SLEEVE.ring.from, SLEEVE.from), M.pulley, 24, { solid: true }), -CAMBER.drop),
          place(along(SLEEVE.radius, ...ends(SLEEVE.from, SLEEVE.to), M.metal, 24, { solid: true }), -CAMBER.drop),
        ];
      }),
      ...arms.flatMap((a) => a.parts),
    ];
    const yoke = sides.map((s) => at(s * 0.12));
    const grips = arms.map((a) => a.grip);
    // Rolled forward about the shaft: every part and point turns about its centre line.
    const pivot = at(0);
    const turn = new THREE.Quaternion().setFromAxisAngle(X, -rad(roll));
    if (roll) {
      for (const m of parts) {
        m.position.sub(pivot).applyQuaternion(turn).add(pivot);
        m.quaternion.premultiply(turn);
      }
      for (const p of [...grips, ...yoke]) p.sub(pivot).applyQuaternion(turn).add(pivot);
    }
    return {
      parts,
      drive() {
        return { points: { grips: grips.map((p) => p.clone()), yoke: yoke.map((p) => p.clone()) } };
      },
    };
  },
});
