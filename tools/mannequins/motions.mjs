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
import { benchHinge } from './recipes/bench.mjs';

const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
const trunkUp = (lean) => V(0, Math.cos(lean), Math.sin(lean));
const forwardOf = (u) => X.clone().cross(u).normalize();
const hanging = (deg) => V(0, -Math.cos(rad(deg)), Math.sin(rad(deg)));
const floor = { point: V(0, 0, 0), normal: V(0, 1, 0) };
// The knee and elbow keep a hair of bend at their straightest, so the IK always knows which way
// they bend.
const STRAIGHT = 0.995;

/** The movements that hold the bar low in the palm: the presses and the squats. */
const PALM_GRIPS = new Set(['bench_press', 'dumbbell_press', 'squat', 'pressdown', 'chest_press']);

/** How far along the palm the motion's grip sits: low in the palm for a press, at the fingers' roots for a pull. */
const depthOf = (e, L) => (PALM_GRIPS.has(e.motion) ? L.gripPalm : L.gripAlong);

/** The values `top` and `bottom` give, `d` of the way down the rep (0 at the top, 1 at the bottom). */
export function stateAt(e, d) {
  const t = smooth(d);
  const s = {};
  for (const [k, a] of Object.entries(e.top ?? {})) {
    if (k === 'offsets') continue;
    const b = e.bottom?.[k] ?? a;
    s[k] = Array.isArray(a) ? a.map((v, i) => lerp(v, b[i], t)) : lerp(a, b, t);
  }
  // A wrist's bend given at the bottom alone eases from straight at the top, and a grip's turn
  // given at one end alone from the entry's own `grip.rotate` at the other.
  if (e.bottom?.wrist && !e.top?.wrist) s.wrist = e.bottom.wrist.map((v) => v * t);
  const turn = e.grip?.rotate ?? 0;
  if (e.top?.rotate === undefined && e.bottom?.rotate !== undefined) s.rotate = lerp(turn, e.bottom.rotate, t);
  if (e.top?.rotate !== undefined && e.bottom?.rotate === undefined) s.rotate = lerp(e.top.rotate, turn, t);
  for (const [k, samples] of Object.entries(e.trace?.angles ?? {})) s[k] = sample(samples, d);
  // Each bone's offset, [x, y, z] in degrees, eased between the ends; a bone turned at one end
  // alone eases from none at the other.
  const bones = new Set([...Object.keys(e.top?.offsets ?? {}), ...Object.keys(e.bottom?.offsets ?? {})]);
  if (bones.size) {
    s.offsets = {};
    for (const n of bones) {
      const a = e.top?.offsets?.[n] ?? [0, 0, 0];
      const b = e.bottom?.offsets?.[n] ?? [0, 0, 0];
      s.offsets[n] = a.map((v, i) => lerp(v, b[i], t));
    }
  }
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
  const s = stateAt(e, d);
  const P = motion(e, s, L);
  // The grip's twist, overhand 0° to underhand 180°, and the handle's tilt in the hand: the entry's
  // own, or the top's and the bottom's eased between.
  const rotate = s.rotate ?? e.grip?.rotate;
  if (rotate) P.gripRotate = rotate;
  const tilt = s.tilt ?? e.grip?.tilt;
  if (tilt) P.gripTilt = tilt;
  // The wrists' bend, [flexion, deviation] in degrees, at the top and the bottom eased between.
  if (s.wrist) P.wrist = s.wrist;
  // The bones turned on top of the solve (`poseRig`).
  if (s.offsets) P.offsets = s.offsets;
  // A press or a squat holds the bar low in the palm, stacked over the forearm; a pull at the
  // fingers' roots.
  if (PALM_GRIPS.has(e.motion)) P.gripDepth = 'palm';
  // An arm traced from footage, or elbows the entry points (`grip.pole`), keep the elbow there.
  if (e.trace?.angles?.upperArm || e.grip?.pole) P.armTraced = true;
  return P;
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
  const ky = (e.feet.y ?? 0) + L.ankleY + L.shin * STRAIGHT * Math.cos(a);
  return V(0, ky + L.thigh * STRAIGHT * Math.cos(b), kz - L.thigh * STRAIGHT * Math.sin(b) * Math.cos(out));
}

/**
 * Feet flat on the floor, or on a platform `feet.y` high (a deficit), `feet.stance` either side of
 * the middle at `feet.z`, the knees toward `pole`.
 */
const floorFeet = (e, L, pole) => ({
  // With `feet.stagger`, the left foot (+x) stands that far ahead of the right (behind, if negative).
  ankles: [-1, 1].map((s) => V(s * e.feet.stance, (e.feet.y ?? 0) + L.ankleY, (e.feet.z ?? 0) + (s * (e.feet.stagger ?? 0)) / 2)),
  toeOut: e.feet.toeOut ?? 0,
  kneePole: pole,
  support: () => (e.feet.y ? { point: V(0, e.feet.y, 0), normal: V(0, 1, 0) } : floor),
});

/** Standing: the knees forward, pushed out by `feet.kneesOut`. */
const standingFeet = (e, L) => floorFeet(e, L, (s) => V(s * (e.feet.kneesOut ?? 0), 0, 1).normalize());

/** In front of a seat: the knees up and a little out. */
const seatedFeet = (e, L) => floorFeet(e, L, (s) => V(s * (e.feet.kneesOut ?? 0.25), 1, 0.6).normalize());

/** Where a cross-arm rack's hand lies on the other shoulder, from its joint: [across toward the middle, up, forward]. */
const SPOT = [0.02, 0.06, 0.07];

/** How far in front of the shin's and the thigh's line a bar running past them must stay: their own depth and the bar's. */
const SHIN_FRONT = 0.075;
const THIGH_FRONT = 0.11;
/** How far below the knee the leg's front turns from the shin's line to the thigh's, round the kneecap. */
const KNEE = 0.2;
/** How many degrees either side of where the bar first touches the knees start to give way. */
const GIVE = 6;

/**
 * A pull's legs with the knees out of the bar's way: where the bar, at its height `s.bar` on its
 * line at `bar.z`, would run into the shins or the thighs, the shins stand back, as far as it takes.
 * The legs' front runs up the shin, round the kneecap and up the thigh with no corner, and the
 * knees start to give a little before the bar reaches them, so they give way smoothly.
 */
function clearOf(e, s, L) {
  const ankleY = (e.feet.y ?? 0) + L.ankleY;
  // How far in front of the legs the bar runs at its height, with the shins at `shin`.
  const room = (shin) => {
    const hip = standingHips(e, { ...s, shin }, L);
    const out = Math.atan(e.feet.kneesOut ?? 0);
    const a = Math.atan2(Math.sin(rad(shin)), Math.cos(rad(shin)) * Math.cos(out));
    const knee = V(0, ankleY + L.shin * STRAIGHT * Math.cos(a), L.shin * STRAIGHT * Math.sin(a) * Math.cos(out));
    // The shin's line and the thigh's, each run on past the knee, and the kneecap between: the
    // leg's front turns from one to the other over the stretch below the knee.
    const shinZ = knee.z * ((s.bar - ankleY) / (knee.y - ankleY));
    const thighZ = knee.z + ((s.bar - knee.y) / (hip.y - knee.y)) * (hip.z - knee.z);
    const k = smooth(Math.max(0, Math.min(1, (s.bar - (knee.y - KNEE)) / KNEE)));
    return e.bar.z - (shinZ + (thighZ - shinZ) * k) - (SHIN_FRONT + (THIGH_FRONT - SHIN_FRONT) * k);
  };
  // The shin that just clears the bar (the room shrinks as the shin leans in), and the traced
  // shin eased onto it: a smooth minimum, never past it.
  let [lo, hi] = [-10, 80];
  if (room(hi) >= 0) return s;
  for (let i = 0; i < 30; i++) {
    const m = (lo + hi) / 2;
    if (room(m) >= 0) lo = m; else hi = m;
  }
  const shin = (s.shin + lo - Math.hypot(s.shin - lo, GIVE)) / 2;
  return { ...s, shin };
}

/**
 * The spine's lean for a side-on trunk angle: the line from the hips to the shoulders, which is
 * what a trace sees, runs a little behind the spine.
 */
const spineLean = (trunk, L) => rad(trunk) + Math.atan2(L.trunkBack, L.trunkUp);

/**
 * Where a spine bent by `arch` (degrees, its three links' turn up from `lean`, as `archOf`) puts
 * the shoulders' middle from the hips' middle.
 */
function archedShoulders(lean, arch, L) {
  const turns = [0, ...(arch ?? [0, 0, 0])];
  const out = V(0, 0, 0);
  L.links.forEach(([up, fwd], i) => {
    const a = lean + rad(turns[i]);
    out.y += up * Math.cos(a) - fwd * Math.sin(a);
    out.z += up * Math.sin(a) + fwd * Math.cos(a);
  });
  return out;
}

/** Where the trunk puts the shoulders' middle: up the spine, and a little behind it. */
const shouldersOf = (hipC, u, L) => hipC.clone().addScaledVector(u, L.trunkUp).addScaledVector(forwardOf(u), -L.trunkBack);

/** The hip centre and the spine's lean of a body placed on a seat or a bench: `body.hip` is [y, z]. */
const placed = (e, s, L) => ({ hipC: V(0, ...e.body.hip), lean: spineLean(s.trunk ?? e.body.trunk, L) });

/**
 * A body standing from its side-on angles, or seated at `body.hip` when the entry gives one: the
 * hip centre, the trunk's direction and the feet.
 */
function bodyOf(e, s, L) {
  if (e.body?.hip) {
    const { hipC, lean } = placed(e, s, L);
    return { hipC, u: trunkUp(lean), feet: seatedFeet(e, L) };
  }
  return { hipC: standingHips(e, s, L), u: trunkUp(spineLean(s.trunk, L)), feet: standingFeet(e, L) };
}

/**
 * The hands of a pose where one arm may work alone: with `grip.one` (1 the left hand, on +x; -1
 * the right) the other hand rests at `grip.rest` [x, y, z], and the working one is `work(side)`.
 */
const handsOf = (e, work) => (side) => (e.grip?.one && side !== e.grip.one ? V(...e.grip.rest) : work(side));

/** Elbows toward `grip.pole` [out, up, forward], the out mirrored for each side, or toward `fallback`. */
const elbowsTo = (e, fallback) => {
  const [x, y, z] = e.grip?.pole ?? fallback;
  return (side) => V(side * x, y, z).normalize();
};

/** A direction in the side-on plane `phi` radians round from forward (+z) toward down. */
const sideOn = (phi) => V(0, -Math.sin(phi), Math.cos(phi));

/** Where a knee bends to between the hip joint at `hip` and the ankle, toward `pole`. */
function kneeAt(hip, ankle, L, pole) {
  const d = ankle.clone().sub(hip);
  const len = Math.min(d.length(), (L.thigh + L.shin) * STRAIGHT);
  const along = (L.thigh ** 2 - L.shin ** 2 + len ** 2) / (2 * len);
  const dir = d.normalize();
  const out = pole.clone().addScaledVector(dir, -pole.dot(dir)).normalize();
  return hip.clone().addScaledVector(dir, along).addScaledVector(out, Math.sqrt(Math.max(0, L.thigh ** 2 - along ** 2)));
}

/** The spine's segments for an arch: each of `spine_01`..`spine_03` turned `arch` degrees up from the spine's lean. */
const archOf = (arch, lean) => arch && Object.fromEntries(
  ['spine_01', 'spine_02', 'spine_03'].map((n, i) => [n, trunkUp(lean + rad(arch[i]))]),
);

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
      // With `grip.crossed` (a front squat's cross-arm rack) the bar rides on the front delts and
      // each hand lies open on the other shoulder, over the bar, not gripping it: at `grip.spot`
      // [across, up, forward] from that shoulder joint, in the trunk's frame.
      ...(e.grip.crossed ? {
        openHands: true,
        hand: (side) => {
          const [across, up, forward] = e.grip.spot ?? SPOT;
          return shouldersOf(hipC, u, L).addScaledVector(X, -side * (L.shW - across)).addScaledVector(u, up).addScaledVector(f, forward);
        },
      } : {}),
      // A bar on the back has the elbows down and back; `grip.pole` points them elsewhere, forward
      // for a bar racked on the front.
      armPole: e.grip.pole ? elbowsTo(e) : (side) => u.clone().multiplyScalar(-1).addScaledVector(f, -0.8).addScaledVector(X, side * 0.3).normalize(),
    };
  },

  /**
   * A hinge with the bar hanging in straight arms (an RDL): the legs and trunk set from their
   * angles, and the bar hangs below the shoulders `bar.z` in front of the ankles.
   */
  hinge(e, s, L) {
    const hipC = standingHips(e, s.bar === undefined ? s : clearOf(e, s, L), L);
    const reach = (L.up + L.fore) * STRAIGHT + depthOf(e, L);
    const dx = e.grip.width - L.shW;
    // A shrug lifts the shoulders, and the bar with them.
    const lift = L.clavicle * Math.sin(rad(s.shrug ?? 0));
    // How high the bar hangs, the arms straight, with the spine leaning `lean`.
    const hangs = (lean) => {
      const sh = shouldersOf(hipC, trunkUp(lean), L);
      const dz = sh.z - e.bar.z;
      return sh.y + lift - Math.sqrt(Math.max(0, reach * reach - dx * dx - dz * dz));
    };
    let lean = spineLean(s.trunk ?? 0, L);
    if (s.bar !== undefined) {
      // With `bar` (its height at the top and the bottom, a deadlift's on the floor) the trunk
      // leans as far as it takes to hang the bar there.
      let [lo, hi] = [rad(-10), rad(100)];
      for (let i = 0; i < 40; i++) {
        const m = (lo + hi) / 2;
        if (hangs(m) > s.bar) lo = m; else hi = m;
      }
      lean = (lo + hi) / 2;
    }
    const u = trunkUp(lean);
    const f = forwardOf(u);
    return {
      hipC, u, shrug: s.shrug,
      bar: V(0, hangs(lean), e.bar.z),
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
      // The head lies back on the pad: `body.head` tips it from the spine's line, toward the floor
      // when negative.
      headDir: trunkUp(lean + rad(e.body.head ?? 0)),
      // An arched back: `body.arch` turns the spine's segments up from the lumbar spine (+), so
      // the lower back lifts off the pad and the chest rises, and back down for the upper back.
      spine: archOf(e.body.arch, lean),
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
      // The shoulders let up into the stretch at the top, down as the elbows drive down.
      shrug: s.shrug,
      // `elbows` turns the elbows out from the sides: 1 out wide, 0 straight down by the sides.
      // `elbowZ` is where they bend toward front to back, with the arm overhead: negative behind
      // the arm, positive in front. Straight overhead the arm's axis is nearly the pole's own (down),
      // so a pole with no front or back to it leaves the elbow's plane to chance, and the elbow swings
      // about the arm as it comes down; a pole that leans forward keeps it in one plane.
      armPole: (side) => V(side * (e.grip.elbows ?? 0.8), -1, e.grip.elbowZ ?? -0.3).normalize(),
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
      bar: elbow.clone().addScaledVector(hanging(s.forearm), L.fore + depthOf(e, L)),
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
      // Horizontal handles, held like a bar: the knuckles run along them.
      hand: handle,
      armPole: (side) => V(side * 0.7, -1, -0.5).normalize(),
      pivot, handle, swing,
    };
  },

  /**
   * Pressing dumbbells on an adjustable bench at `bench.angle`, written in the backrest's frame so
   * one entry fits every angle: the back along the pad, and the hips `body.sit` off its face and
   * `body.seat` down it from the seat-back corner (the body slides down the pad, never into the
   * seat); the trunk leans back as far as the pad does, off it by `body.lean`; the head lies back
   * along the trunk, tilted `body.head`. The hands are measured from the shoulders in the trunk's
   * frame: at the top `top.over` is [out from the chest, up the trunk] (the arms as straight as
   * they reach), at the bottom `bottom.chest` is [out from the chest, down the trunk].
   */
  dumbbell_press(e, s, L) {
    const angle = e.bench.angle;
    const a = rad(angle);
    const { height, z: hingeZ } = benchHinge(e.kit.bench);
    const lean = rad(angle - 90 + (e.body.lean ?? 0));
    // The corner's bisector runs (cos A/2, sin A/2) over [y, z]: `sit` off the face along it is
    // sit / cos(A/2) long. Down the pad from the corner is [-sin A, cos A].
    const hipC = V(0, height + e.body.sit - e.body.seat * Math.sin(a), hingeZ + e.body.sit * Math.tan(a / 2) + e.body.seat * Math.cos(a));
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const reach = (L.up + L.fore) * STRAIGHT + depthOf(e, L);
    // The hands come in to `grip.lockout` apart at the top and go out to `grip.width` at the bottom.
    const handAt = (side) => {
      const dx = e.grip.lockout - L.shW;
      let [out, along] = e.top.over;
      // No further from the shoulder than the arm reaches, on the same line from it.
      const room = Math.sqrt(Math.max(0, reach * reach - dx * dx));
      const far = Math.hypot(out, along);
      if (far > room) [out, along] = [(out * room) / far, (along * room) / far];
      const top = V(side * e.grip.lockout, sh.y, sh.z).addScaledVector(f, out).addScaledVector(u, along);
      const [away, down] = e.bottom.chest;
      const bottom = V(side * e.grip.width, sh.y, sh.z).addScaledVector(f, away).addScaledVector(u, -down);
      return top.lerp(bottom, s.press);
    };
    return {
      hipC, u, shoulders: sh,
      headDir: trunkUp(lean + rad(e.body.head ?? 0)),
      ...floorFeet(e, L, (side) => V(side * 0.5, 1, 0.4).normalize()),
      hand: handAt,
      across: (side) => X.clone().multiplyScalar(-side),
      // A dumbbell turns with the hand: the wrist stays straight and the dumbbell follows it.
      heldFree: true,
      // `elbows` turns the elbows out from the sides: 0 tucked to the trunk, 1 out level with the shoulders.
      armPole: (side) => V(side * e.grip.elbows, -1, 0.7 * (1 - e.grip.elbows)).normalize(),
    };
  },

  /**
   * The arms swinging side-on, the upper arms and the forearms forward from hanging by `upperArm`
   * and `forearm` degrees: a curl, a preacher curl, an extension overhead. Standing from side-on
   * angles, or seated at `body.hip`. The hands hold a bar, a rope or handles `grip.width` either side
   * of the middle; with `grip.one` one arm works and the other rests.
   */
  arms(e, s, L) {
    const { hipC, u, feet } = bodyOf(e, s, L);
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const elbow = sh.clone().addScaledVector(hanging(s.upperArm), L.up * STRAIGHT);
    const bar = elbow.clone().addScaledVector(hanging(s.forearm), L.fore + L.gripAlong);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.25).normalize(),
      ...feet,
      bar, grip: e.grip.width,
      hand: handsOf(e, (side) => bar.clone().setX(side * e.grip.width)),
      armPole: elbowsTo(e, [0.2, -1, -0.4]),
      elbow, upper: hanging(s.upperArm),
    };
  },

  /**
   * The arms moving freely, each hand at a point in the room: `hand` [x, y, z] for the left hand at
   * the top and the bottom of the rep, the right mirroring it: a raise, a flye, a press between two
   * pulleys. Standing from side-on angles, or seated at `body.hip`; with `grip.one` one arm works and
   * the other rests.
   */
  /**
   * A row: the legs and trunk set from their angles, the bar on a line straight up and down at
   * `bar.z`, at its height `bar` at the top and the bottom of the rep, the elbows toward `grip.pole`.
   */
  row(e, s, L) {
    const { hipC, u, feet } = bodyOf(e, s, L);
    const f = forwardOf(u);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.25).normalize(),
      ...feet,
      bar: V(0, s.bar, e.bar.z), grip: e.grip.width,
      armPole: elbowsTo(e, [0.35, 0.2, -1]),
    };
  },

  free_arms(e, s, L) {
    const { hipC, u, feet } = bodyOf(e, s, L);
    const f = forwardOf(u);
    const at = (p, side) => V(side * p[0], p[1], p[2]);
    let work = (side) => at(s.hand, side);
    if (e.grip?.arc) {
      // With `grip.arc` (a flye, a raise) the hand sweeps an arc about its shoulder from the top's
      // point to the bottom's, its reach between theirs, so the elbow holds its bend.
      const [top, bottom] = [V(...e.top.hand), V(...e.bottom.hand)];
      const way = bottom.clone().sub(top);
      const t = Math.min(1, Math.max(0, V(...s.hand).sub(top).dot(way) / Math.max(way.lengthSq(), 1e-9)));
      work = (side) => {
        const sh = shouldersOf(hipC, u, L).addScaledVector(X, side * L.shW);
        const a = at(e.top.hand, side).sub(sh);
        const b = at(e.bottom.hand, side).sub(sh);
        const turn = new THREE.Quaternion().slerp(new THREE.Quaternion().setFromUnitVectors(a.clone().normalize(), b.clone().normalize()), t);
        return sh.add(a.clone().normalize().applyQuaternion(turn).multiplyScalar(a.length() + (b.length() - a.length()) * t));
      };
    }
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.2).normalize(),
      ...feet,
      hand: handsOf(e, work),
      // Both hands on one bar (an upright row): it lies between them, and with a barbell the hands
      // hold it across the palm, knuckles along it.
      ...(e.grip?.one ? {} : { bar: V(0, work(1).y, work(1).z) }),
      ...(e.kit?.barbell && !e.grip?.one ? { across: (side) => X.clone().multiplyScalar(-side) } : {}),
      armPole: elbowsTo(e, [1, -0.3, -0.5]),
    };
  },

  /**
   * The knees bending against a machine's roller: seated with the thighs along the seat (a leg
   * extension, a seated leg curl), or with `body.prone` lying face down along a bench, the head
   * toward +z (a lying leg curl). The thighs leave the hips `body.thigh` degrees below level and the
   * knees bend `knee` degrees from straight; the feet stay square to the shins. The hands hold
   * handles at `grip.at` [y, z], `grip.width` either side of the middle, the knuckles along
   * `grip.across` [out, up, forward] (the out mirrored for each side), or forward.
   */
  knee_lever(e, s, L) {
    const prone = Boolean(e.body.prone);
    const hipC = V(0, ...e.body.hip);
    const lean = prone ? rad(90 + (e.body.trunk ?? 0)) : spineLean(s.trunk ?? e.body.trunk, L);
    const u = trunkUp(lean);
    // The thigh's and the shin's ways in the side-on plane, round from forward toward down.
    const thighPhi = prone ? Math.PI - rad(e.body.thigh ?? 0) : rad(e.body.thigh ?? 0);
    const shinPhi = thighPhi + rad(s.knee);
    const shin = sideOn(shinPhi);
    const front = sideOn(shinPhi - Math.PI / 2);
    const hip = (side) => V(side * e.feet.stance, hipC.y, hipC.z);
    const knee = (side) => hip(side).addScaledVector(sideOn(thighPhi), L.thigh * STRAIGHT);
    const ankles = [-1, 1].map((side) => knee(side).addScaledVector(shin, L.shin * STRAIGHT));
    const top = shin.clone().negate();
    return {
      hipC, u,
      headDir: trunkUp(lean + rad(e.body.head ?? 0)),
      ankles,
      toeOut: 0,
      foot: () => ({ heading: front.clone(), top: top.clone() }),
      kneePole: (side) => sideOn(thighPhi - Math.PI / 2).addScaledVector(X, side * 0.1).normalize(),
      support: (side) => ({ point: ankles[side < 0 ? 0 : 1].clone().addScaledVector(top, -L.ankleY), normal: top.clone() }),
      hand: (side) => V(side * e.grip.width, ...e.grip.at),
      across: (side) => {
        const [x, y, z] = e.grip.across ?? [0, 0, 1];
        return V(side * x, y, z).normalize();
      },
      armPole: elbowsTo(e, [1, -0.2, -0.5]),
      knee: knee(0), shin, front, thigh: sideOn(thighPhi), kneeUp: sideOn(thighPhi - Math.PI / 2),
    };
  },

  /**
   * Seated with the knees under a pad and the balls of the feet on a platform's edge, `feet.ball`
   * [y, z]: the heels rise and fall as the ankles turn `ankle` degrees from flat, heels up positive,
   * and the knees rise and fall with them. A lever over the knees turns about `pivot` [y, z] with
   * them, from where it rests at the stretch, and the hands hold handles on it, `grip.at` [y, z] at
   * the stretch, `grip.width` either side of the middle.
   */
  calf_raise(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    // The sole tilted `deg` with the ankle, about the ball: its way toward the toes, and its up.
    const sole = (deg) => {
      const a = rad(deg);
      return [V(0, -Math.sin(a), Math.cos(a)), V(0, Math.cos(a), Math.sin(a))];
    };
    const [heading, top] = sole(s.ankle);
    const ball = (side) => V(side * e.feet.stance, ...e.feet.ball);
    const anklesAt = ([head, up]) => [-1, 1].map((side) => ball(side).addScaledVector(head, -L.ballAhead).addScaledVector(up, L.ankleY - L.ballY));
    const ankles = anklesAt([heading, top]);
    const pole = V(0, 1, 0.3).normalize();
    const kneeOf = (feet) => kneeAt(V(0, hipC.y, hipC.z), feet[1].clone().setX(0), L, pole);
    const knee = kneeOf(ankles);
    // The lever turns as far about its pivot as the knees under its pads have from the stretch.
    const pivot = V(0, ...e.pivot);
    const angle = (p) => Math.atan2(p.y - pivot.y, p.z - pivot.z);
    const turned = angle(knee) - angle(kneeOf(anklesAt(sole(e.bottom.ankle))));
    const turn = Math.atan2(Math.sin(turned), Math.cos(turned));
    const onLever = (p) => p.sub(pivot).applyAxisAngle(X, -turn).add(pivot);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.25).normalize(),
      ankles,
      toeOut: 0,
      foot: () => ({ heading: heading.clone(), top: top.clone() }),
      kneePole: (side) => pole.clone().addScaledVector(X, side * 0.15).normalize(),
      support: (side) => ({ point: ball(side).addScaledVector(top, -L.ballY), normal: top.clone() }),
      hand: (side) => onLever(V(side * e.grip.width, ...e.grip.at)),
      across: () => V(0, 1, 0).applyAxisAngle(X, -turn),
      armPole: elbowsTo(e, [1, -0.3, -0.4]),
      knee, pivot, turn,
      // The top of the platform, under the balls.
      platform: V(0, e.feet.ball[0] - L.ballY, e.feet.ball[1]),
    };
  },

  /**
   * In a hack squat's sled, the back on its pad and the shoulders under its pads: the hips travel
   * down the rails from `hip` [y, z] at the top to `hip` at the bottom, the trunk keeps the pad's
   * lean, `body.trunk`, and the feet stand on a platform tilted `feet.tilt` degrees toes up, its face
   * at [`feet.y`, `feet.z`] under the ankles. The hands hold the handles by the shoulder pads,
   * `grip.at` [up the trunk, out from the chest] from the shoulders.
   */
  hack_squat(e, s, L) {
    const hipC = V(0, ...s.hip);
    const u = trunkUp(spineLean(e.body.trunk, L));
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const t = rad(e.feet.tilt ?? 0);
    const heading = V(0, Math.sin(t), Math.cos(t));
    const top = V(0, Math.cos(t), -Math.sin(t));
    const face = V(0, e.feet.y, e.feet.z);
    const toeOut = rad(e.feet.toeOut ?? 0);
    const ankles = [-1, 1].map((side) => face.clone().setX(side * e.feet.stance).addScaledVector(top, L.ankleY));
    const sled = V(0, ...e.top.hip).sub(V(0, ...e.bottom.hip)).normalize();
    return {
      hipC, u, shoulders: sh,
      headDir: u.clone().addScaledVector(f, 0.15).normalize(),
      ankles,
      foot: (side) => ({ heading: heading.clone().applyAxisAngle(top, side * toeOut), top: top.clone() }),
      kneePole: (side) => V(side * (e.feet.kneesOut ?? 0.35), 0, 1).normalize(),
      support: () => ({ point: face.clone(), normal: top.clone() }),
      hand: (side) => sh.clone().addScaledVector(X, side * e.grip.width).addScaledVector(u, e.grip.at[0]).addScaledVector(f, e.grip.at[1]),
      across: () => f.clone().negate(),
      armPole: elbowsTo(e, [0.6, -1, 0.3]),
      sled, face, plateUp: top,
    };
  },

  /**
   * Seated, the hands on handles that swing on levers about a pivot at `pivot` [y, z]: from
   * `handle` [y, z] at the top of the rep to `handle` at the bottom, round the pivot, `press` 0 to 1,
   * `grip.width` either side of the middle. A chest-supported row leans the chest onto a pad; a
   * machine pulldown sits under thigh pads.
   */
  lever_handles(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const pivot = V(0, ...e.pivot);
    const polar = (p) => {
      const d = V(0, ...p).sub(pivot);
      return [d.length(), Math.atan2(d.y, d.z)];
    };
    const [r0, a0] = polar(e.top.handle);
    let [r1, a1] = polar(e.bottom.handle);
    // The short way round the pivot.
    if (a1 - a0 > Math.PI) a1 -= 2 * Math.PI;
    if (a0 - a1 > Math.PI) a1 += 2 * Math.PI;
    const r = lerp(r0, r1, s.press);
    const swing = lerp(a0, a1, s.press);
    const middle = pivot.clone().add(V(0, r * Math.sin(swing), r * Math.cos(swing)));
    const handle = (side) => middle.clone().setX(side * e.grip.width);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.15).normalize(),
      ...seatedFeet(e, L),
      hand: handle,
      armPole: elbowsTo(e, [0.7, -1, -0.5]),
      pivot, handle,
    };
  },

  /**
   * Lying face up, the knees bent and the feet flat, the shoulder joints on the floor or across a
   * bench at `body.shoulders` [y, z], the head toward -z: the hips lift from `hip` [y, z] at the
   * bottom of the rep to `hip` at the top, each about a trunk's length from the shoulders, which the
   * trunk turns about, a bar across them `bar.up` above the hips. A glute bridge, a hip thrust.
   */
  bridge(e, s, L) {
    const hipC = V(0, ...s.hip);
    // The trunk turns about the shoulder joints, which stay at `body.shoulders`: aimed past them by
    // as far as they sit behind the spine.
    const at = V(0, ...e.body.shoulders);
    let straight = at.clone().sub(hipC).normalize();
    for (let i = 0; i < 4; i++) straight = at.clone().addScaledVector(forwardOf(straight), L.trunkBack).sub(hipC).normalize();
    // A back arched by `arch` (degrees for each link of the spine) turns about the hips until its
    // shoulders lie where the straight back's do, seen from the hips.
    const angleOf = (p) => Math.atan2(p.z, p.y);
    const aim = angleOf(shouldersOf(hipC, straight, L).sub(hipC));
    let lean = Math.atan2(straight.z, straight.y);
    for (let i = 0; i < 3; i++) lean += aim - angleOf(archedShoulders(lean, s.arch, L));
    const u = trunkUp(lean);
    const f = forwardOf(u);
    return {
      hipC, u,
      ...(s.arch ? { spine: archOf(s.arch, lean) } : {}),
      headDir: trunkUp(lean + rad((s.arch?.[2] ?? 0) + (e.body.head ?? 0))),
      ...floorFeet(e, L, (side) => V(side * (e.feet.kneesOut ?? 0.2), 1, 0.4).normalize()),
      // The bar at `bar` [y, z] at the top and the bottom of the rep, or `bar.up` out from the
      // hips square to the trunk.
      bar: s.bar ? V(0, ...s.bar) : hipC.clone().addScaledVector(f, e.bar.up),
      grip: e.grip.width,
      armPole: elbowsTo(e, [0.6, -1, 0.2]),
    };
  },
};
