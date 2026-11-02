// How a body moves through a rep, one function per kind of movement. Each takes a pose entry, how
// far down the rep it is (0 at the top, 1 at the bottom) and the mannequin's measurements, and
// returns the frame `poseRig` poses: hip centre, trunk and head directions, ankles, poles and bar.
import { V, X, rad } from './rig.mjs';

const lerp = (a, b, t) => a + (b - a) * t;
const trunkUp = (lean) => V(0, Math.cos(lean), Math.sin(lean));
const forwardOf = (u) => X.clone().cross(u).normalize();
// The knee keeps a hair of bend at the top, so the IK always knows which way it bends.
const STRAIGHT = 0.995;

export const MOTIONS = {
  /** A barbell on the back: the legs bend, and the trunk leans until the bar is over midfoot. */
  squat(e, d, L) {
    const a = rad(lerp(e.shin[0], e.shin[1], d));
    const b = rad(lerp(e.thigh[0], e.thigh[1], d));
    const kz = L.shin * STRAIGHT * Math.sin(a);
    const ky = L.ankleY + L.shin * STRAIGHT * Math.cos(a);
    const hipC = V(0, ky + L.thigh * STRAIGHT * Math.cos(b), kz - L.thigh * STRAIGHT * Math.sin(b));
    const barAt = (lean) => {
      const u = trunkUp(lean);
      return hipC.clone().addScaledVector(u, L.trunk + e.bar.up).addScaledVector(forwardOf(u), -e.bar.back);
    };
    // A loaded bar stays over the middle of the feet, turned out as they are.
    const midfoot = L.midfoot * Math.cos(rad(e.toeOut));
    let lo = -0.4;
    let hi = 1.4;
    for (let i = 0; i < 32; i++) {
      const m = (lo + hi) / 2;
      if (barAt(m).z > midfoot) hi = m; else lo = m;
    }
    const lean = (lo + hi) / 2;
    const u = trunkUp(lean);
    const f = forwardOf(u);
    return {
      hipC, u, bar: barAt(lean),
      // The head stays near upright, the eyes a little down, as the trunk leans.
      headDir: u.clone().add(V(0, 1.2, 0.15)).normalize(),
      ankles: [V(-e.stance, L.ankleY, 0), V(e.stance, L.ankleY, 0)], toeOut: e.toeOut,
      kneePole: (s) => V(s * e.kneesOut, 0, 1).normalize(),
      grip: e.grip,
      armPole: (s) => u.clone().multiplyScalar(-1).addScaledVector(f, -0.8).addScaledVector(X, s * 0.3).normalize(),
    };
  },
};
