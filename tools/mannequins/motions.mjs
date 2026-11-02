// How a body moves through a rep, one function per kind of movement. A pose entry gives the top
// and bottom of the rep (`top`, `bottom`); `frameAt` eases between them, takes any angle its trace
// gives instead, and hands the values to the entry's motion with the mannequin's measurements.
// A motion returns the frame `poseRig` poses and the kit is placed from: hip centre, trunk and
// head directions, ankles and the feet's support, poles, and where the hands hold.
//
// Angles are seen side-on, in degrees, as `trace` measures them: `shin` leans the knee forward of
// the ankle, `thigh` puts the hip behind the knee, `trunk` leans the shoulders forward of the hips,
// `upperArm` and `forearm` swing the elbow and the hand forward from hanging straight down.
import * as THREE from 'three';
import { V, X, rad } from './rig.mjs';

const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
const trunkUp = (lean) => V(0, Math.cos(lean), Math.sin(lean));
const forwardOf = (u) => X.clone().cross(u).normalize();
const hanging = (deg) => V(0, -Math.cos(rad(deg)), Math.sin(rad(deg)));
const floor = { point: V(0, 0, 0), normal: V(0, 1, 0) };
// The knee and elbow keep a hair of bend at their straightest, so the IK always knows which way
// they bend.
const STRAIGHT = 0.995;

/** The values `top` and `bottom` give, `d` of the way down the rep (0 at the top, 1 at the bottom). */
export function stateAt(e, d) {
  const t = smooth(d);
  const s = {};
  for (const [k, a] of Object.entries(e.top ?? {})) {
    const b = e.bottom?.[k] ?? a;
    s[k] = Array.isArray(a) ? a.map((v, i) => lerp(v, b[i], t)) : lerp(a, b, t);
  }
  for (const [k, samples] of Object.entries(e.trace?.angles ?? {})) s[k] = sample(samples, d);
  return s;
}

/** A trace's angle `d` of the way down: its samples are even over the lowering, at the lifter's own pace through it. */
export function sample(samples, d) {
  const at = d * (samples.length - 1);
  const i = Math.min(Math.floor(at), samples.length - 2);
  return lerp(samples[i], samples[i + 1], at - i);
}

/** The pose frame `d` of the way down the rep. */
export function frameAt(e, d, L) {
  const motion = MOTIONS[e.motion];
  if (!motion) throw new Error(`No motion "${e.motion}"`);
  return motion(e, stateAt(e, d), L);
}

/**
 * Standing legs from side-on shin and thigh angles: the hip centre over ankles on the floor. With
 * the knees pushed out the legs bend in a plane turned out from the side, so each angle in that
 * plane leans more than it shows side-on, and a segment shows shorter.
 */
function standingHips(e, s, L) {
  const out = Math.atan(e.feet.kneesOut ?? 0);
  const inPlane = (deg) => Math.atan2(Math.sin(rad(deg)), Math.cos(rad(deg)) * Math.cos(out));
  const a = inPlane(s.shin);
  const b = inPlane(s.thigh);
  const kz = L.shin * STRAIGHT * Math.sin(a) * Math.cos(out);
  const ky = L.ankleY + L.shin * STRAIGHT * Math.cos(a);
  return V(0, ky + L.thigh * STRAIGHT * Math.cos(b), kz - L.thigh * STRAIGHT * Math.sin(b) * Math.cos(out));
}

/** Feet flat on the floor, `feet.stance` either side of the middle at `feet.z`, the knees toward `pole`. */
const floorFeet = (e, L, pole) => ({
  ankles: [-1, 1].map((s) => V(s * e.feet.stance, L.ankleY, e.feet.z ?? 0)),
  toeOut: e.feet.toeOut ?? 0,
  kneePole: pole,
  support: () => floor,
});

/** Standing: the knees forward, pushed out by `feet.kneesOut`. */
const standingFeet = (e, L) => floorFeet(e, L, (s) => V(s * (e.feet.kneesOut ?? 0), 0, 1).normalize());

/** In front of a seat: the knees up and a little out. */
const seatedFeet = (e, L) => floorFeet(e, L, (s) => V(s * (e.feet.kneesOut ?? 0.25), 1, 0.6).normalize());

/**
 * The spine's lean for a side-on trunk angle: the line from the hips to the shoulders, which is
 * what a trace sees, runs a little behind the spine.
 */
const spineLean = (trunk, L) => rad(trunk) + Math.atan2(L.trunkBack, L.trunkUp);

/** Where the trunk puts the shoulders' middle: up the spine, and a little behind it. */
const shouldersOf = (hipC, u, L) => hipC.clone().addScaledVector(u, L.trunkUp).addScaledVector(forwardOf(u), -L.trunkBack);

/** The hip centre and the spine's lean of a body placed on a seat or a bench: `body.hip` is [y, z]. */
const placed = (e, s, L) => ({ hipC: V(0, ...e.body.hip), lean: spineLean(s.trunk ?? e.body.trunk, L) });

export const MOTIONS = {
  /**
   * A barbell on the back, kept over the middle of the feet. Without a trunk angle, the legs bend
   * as given and the trunk leans until the bar is over midfoot. With one (a traced squat), the
   * body keeps its shape, knee and hip as given, and turns about the ankles until it is.
   */
  squat(e, s, L) {
    const shaped = s.trunk !== undefined;
    const at = (turn) => {
      const shape = shaped ? { shin: s.shin + turn, thigh: s.thigh - turn } : s;
      const hipC = standingHips(e, shape, L);
      const lean = shaped ? spineLean(s.trunk + turn, L) : rad(turn);
      const u = trunkUp(lean);
      return { hipC, u, bar: hipC.clone().addScaledVector(u, L.trunk + e.bar.up).addScaledVector(forwardOf(u), -e.bar.back) };
    };
    // A loaded bar stays over the middle of the feet, turned out as they are.
    const midfoot = L.midfoot * Math.cos(rad(e.feet.toeOut));
    let lo = shaped ? -30 : -0.4;
    let hi = shaped ? 30 : 1.4;
    for (let i = 0; i < 40; i++) {
      const m = (lo + hi) / 2;
      if (at(m).bar.z > midfoot) hi = m; else lo = m;
    }
    const { hipC, u, bar } = at((lo + hi) / 2);
    const f = forwardOf(u);
    return {
      hipC, u, bar,
      // The head stays near upright, the eyes a little down, as the trunk leans.
      headDir: u.clone().add(V(0, 1.2, 0.15)).normalize(),
      ...standingFeet(e, L),
      grip: e.grip.width,
      armPole: (side) => u.clone().multiplyScalar(-1).addScaledVector(f, -0.8).addScaledVector(X, side * 0.3).normalize(),
    };
  },

  /**
   * A hinge with the bar hanging in straight arms (an RDL): the legs and trunk set from their
   * angles, and the bar hangs below the shoulders `bar.z` in front of the ankles.
   */
  hinge(e, s, L) {
    const hipC = standingHips(e, s, L);
    const u = trunkUp(spineLean(s.trunk, L));
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const reach = (L.up + L.fore) * STRAIGHT + L.gripAlong;
    const dx = e.grip.width - L.shW;
    const dz = sh.z - e.bar.z;
    return {
      hipC, u,
      bar: V(0, sh.y - Math.sqrt(Math.max(0, reach * reach - dx * dx - dz * dz)), e.bar.z),
      headDir: u.clone().addScaledVector(f, 0.35).add(V(0, 0.25, 0)).normalize(),
      ...standingFeet(e, L),
      grip: e.grip.width,
      armPole: (side) => V(side * 0.4, 0, -1).normalize(),
    };
  },

  /** Lying on a bench, pressing a bar from over the shoulders down to the chest along `bar` [y, z]. */
  bench_press(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    return {
      hipC, u,
      headDir: u.clone().add(V(0, 0.08, 0)).normalize(),
      bar: V(0, ...s.bar),
      ...floorFeet(e, L, (side) => V(side * 0.5, 1, 0.4).normalize()),
      grip: e.grip.width,
      // `elbows` turns the elbows out from the sides: 0 tucked to the trunk, 1 out level with the
      // shoulders.
      armPole: (side) => V(side * e.grip.elbows, -1, 0.7 * (1 - e.grip.elbows)).normalize(),
    };
  },

  /** Seated under a thigh pad, pulling a bar from overhead to the chest along `bar` [y, z]. */
  pulldown(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.25).add(V(0, 0.1, 0)).normalize(),
      ...seatedFeet(e, L),
      bar: V(0, ...s.bar),
      grip: e.grip.width,
      armPole: (side) => V(side * 0.8, -1, -0.3).normalize(),
    };
  },

  /**
   * Standing at a high pulley, the upper arms by the sides and the elbows still, the forearms
   * pressing the bar down from in front of the chest to the thighs.
   */
  pressdown(e, s, L) {
    const hipC = standingHips(e, s, L);
    const u = trunkUp(spineLean(s.trunk, L));
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const elbow = sh.clone().addScaledVector(hanging(s.upperArm), L.up * STRAIGHT);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.35).normalize(),
      ...standingFeet(e, L),
      bar: elbow.clone().addScaledVector(hanging(s.forearm), L.fore + L.gripAlong),
      grip: e.grip.width,
      armPole: (side) => V(side * 0.2, -1, -0.4).normalize(),
    };
  },

  /**
   * Seated in a 45° leg press, the back on its pad and the feet on the sled's plate, which rides
   * down its rails as the knees bend to `knee` degrees.
   */
  leg_press(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const sled = V(0, Math.SQRT1_2, Math.SQRT1_2);
    const plateUp = V(0, Math.SQRT1_2, -Math.SQRT1_2);
    // The plate's centre runs along a line `feet.line` above the hips; the ankles sit `feet.down`
    // below it.
    const line = hipC.clone().addScaledVector(plateUp, e.feet.line);
    const k = rad(180 - s.knee);
    const reach = Math.sqrt(L.thigh ** 2 + L.shin ** 2 - 2 * L.thigh * L.shin * Math.cos(k));
    // How far up the line the ankle is `reach` from the hip joint's middle.
    const ankleOn = (p) => p.clone().addScaledVector(plateUp, -e.feet.down).addScaledVector(sled, -L.ankleY);
    const w = ankleOn(line).sub(hipC);
    const b = w.dot(sled);
    const t = -b + Math.sqrt(b * b - w.lengthSq() + reach * reach);
    const plate = line.clone().addScaledVector(sled, t);
    const toeOut = rad(e.feet.toeOut);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.55).normalize(),
      ankles: [-1, 1].map((side) => ankleOn(plate).addScaledVector(X, side * e.feet.stance)),
      foot: (side) => ({
        heading: plateUp.clone().multiplyScalar(Math.cos(toeOut)).addScaledVector(X, side * Math.sin(toeOut)),
        top: sled.clone().negate(),
      }),
      kneePole: (side) => plateUp.clone().addScaledVector(X, side * (e.feet.kneesOut ?? 0.35)).normalize(),
      support: () => ({ point: plate, normal: sled.clone().negate() }),
      hand: (side) => V(side * e.grip.width, ...e.grip.at),
      across: () => V(0, 0, 1),
      armPole: (side) => V(side, -0.2, -0.5).normalize(),
      plate, sled, plateUp,
    };
  },

  /**
   * Seated against a back pad, pressing two handles that swing on levers hung from a pivot above:
   * from `handle` [y, z] at the top, arms out in front, back to `handle` at the bottom.
   */
  chest_press(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    // The levers' pivot sits `lever` metres above the middle of the handles' path, which is an arc.
    const [a, b] = [V(0, ...e.top.handle), V(0, ...e.bottom.handle)];
    const mid = a.clone().add(b).multiplyScalar(0.5);
    const chord = b.clone().sub(a);
    const r = Math.hypot(e.lever, chord.length() / 2);
    const pivot = mid.clone().add(V(0, e.lever, 0));
    const swingOf = (p) => Math.atan2(p.z - pivot.z, pivot.y - p.y);
    const swing = lerp(swingOf(a), swingOf(b), s.press);
    const middle = V(0, pivot.y - r * Math.cos(swing), pivot.z + r * Math.sin(swing));
    const handle = (side) => middle.clone().setX(side * e.grip.width);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.12).normalize(),
      ...seatedFeet(e, L),
      hand: handle,
      across: () => V(0, 1, 0),
      armPole: (side) => V(side * 0.7, -1, -0.5).normalize(),
      pivot, handle, swing,
    };
  },
};
