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
import { STRAIGHT, V, X, forwardOf, hanging, kneeAt, rad, shouldersOf, sideOn, spineLean, trunkUp } from './rig.mjs';
import { benchHinge } from './recipes/bench.mjs';
import { heldPair } from './recipes/power-rack.mjs';
import { HOLD as CHAIR } from './recipes/captains-chair.mjs';
import { GRIP_LANDMARKS, holdOf } from './grip.mjs';
import { fitted } from './fit.mjs';

const lerp = (a, b, t) => a + (b - a) * t;
const smooth = (t) => t * t * (3 - 2 * t);
/** The curve through `a` at 0, `m` at a half and `b` at 1. */
const curved = (a, m, b, t) => (1 - t) * (1 - 2 * t) * a + 4 * t * (1 - t) * m + t * (2 * t - 1) * b;
const floor = { point: V(0, 0, 0), normal: V(0, 1, 0) };

/** How far along the palm from the wrist the pose's grip sits: its landmark's (`grip.mjs`). */
const depthOf = (e, L) => L.palmLength * (GRIP_LANDMARKS[holdOf(e).sits] ?? GRIP_LANDMARKS.base);

/**
 * Where a forearm swinging side-on `deg` from hanging, from `elbow`, holds its bar with a straight
 * wrist: along it past the wrist to the grip's landmark, and off its line toward the palm as far as
 * the bar is seated (`L.seatOff`). The palm faces the way the elbow straightens in an overhand grip,
 * the way it bends in an underhand one, and neither in a neutral one (`rotate`, degrees).
 */
function heldPast(e, elbow, deg, rotate, L) {
  const { sits, thumb } = holdOf(e);
  const bends = V(0, Math.sin(rad(deg)), Math.cos(rad(deg)));
  return elbow.clone().addScaledVector(hanging(deg), L.fore + depthOf(e, L)).addScaledVector(bends, -Math.cos(rad(rotate)) * L.seatOff(sits, thumb));
}

/** The values `top` and `bottom` give, `d` of the way down the rep (0 at the top, 1 at the bottom). */
export function stateAt(e, d) {
  const t = smooth(d);
  const s = {};
  for (const [k, a] of Object.entries(e.top ?? {})) {
    if (k === 'offsets') continue;
    const b = e.bottom?.[k] ?? a;
    s[k] = Array.isArray(a) ? a.map((v, i) => lerp(v, b[i], t)) : lerp(a, b, t);
  }
  // With `grip.via` the hand curves through that point at the middle of the rep, for one that rides
  // something swinging on an arc, as the head does in a crunch.
  if (e.grip?.via) s.hand = e.grip.via.map((m, i) => curved(e.top.hand[i], m, e.bottom.hand[i], t));
  // A wrist's bend given at the bottom alone eases from straight at the top, and a grip's turn
  // given at one end alone from the entry's own `grip.rotate` at the other.
  if (e.bottom?.wrist && !e.top?.wrist) s.wrist = e.bottom.wrist.map((v) => v * t);
  const turn = e.grip?.rotate ?? 0;
  if (e.top?.rotate === undefined && e.bottom?.rotate !== undefined) s.rotate = lerp(turn, e.bottom.rotate, t);
  if (e.top?.rotate !== undefined && e.bottom?.rotate === undefined) s.rotate = lerp(e.top.rotate, turn, t);
  // An arm's lean out to the side, given at one end alone, eases from none at the other.
  if (e.top?.armOut !== undefined || e.bottom?.armOut !== undefined) s.armOut = lerp(e.top?.armOut ?? 0, e.bottom?.armOut ?? 0, t);
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

/**
 * The pose frame `d` of the way down the rep. An entry in a machine is fitted to the mannequin, and
 * its joint's value (`s.linkage`, what the recipe has there) drives the motion.
 */
export function frameAt(entry, d, L) {
  const e = fitted(entry, L);
  const motion = MOTIONS[e.motion];
  if (!motion) throw new Error(`No motion "${e.motion}"`);
  const s = stateAt(e, d);
  if (e.linkage) s.linkage = e.linkage.at(s[e.linkage.joint]);
  const P = motion(e, s, L);
  if (e.linkage) P.linkage = s.linkage;
  // The grip's twist, overhand 0° to underhand 180°, and the handle's tilt in the hand: the entry's
  // own, or the top's and the bottom's eased between.
  const rotate = s.rotate ?? e.grip?.rotate;
  if (rotate) P.gripRotate = rotate;
  const tilt = s.tilt ?? e.grip?.tilt;
  if (tilt) P.gripTilt = tilt;
  // The wrists' bend, [flexion, deviation] in degrees, at the top and the bottom eased between.
  if (s.wrist) P.wrist = s.wrist;
  // The bones turned on top of the solve (`poseRig`).
  if (s.offsets) P.offsets = { ...P.offsets, ...s.offsets };
  // Dumbbells hang from the hands wherever a motion puts them, a bar's two grips or its own points,
  // and turn with them.
  if (e.kit?.dumbbells) {
    P.hand ??= (side) => P.bar.clone().addScaledVector(X, side * P.grip);
    P.across ??= (side) => X.clone().multiplyScalar(-side);
    P.heldFree = true;
    // `grip.level` is the wrists' share of the forearms' lean, 0 to 1: the handles tip with the rest.
    if (e.grip?.level) P.level = e.grip.level;
  }
  // Where the bar sits in the hand and the thumb (`grip.mjs`); with `grip.one` the other hand rests.
  P.hold = holdOf(e);
  if (e.grip?.one) P.resting = -e.grip.one;
  // `grip.restOn` is the radius of what that hand rests on, to close round it.
  if (e.grip?.restOn) P.restOn = e.grip.restOn;
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

/** The hip centre and the spine's lean of a body placed on a seat or a bench: `body.hip` is [y, z]. */
const placed = (e, s, L) => ({ hipC: V(0, ...e.body.hip), lean: spineLean(s.trunk ?? e.body.trunk, L) });

/** How high a kneeling knee's joint sits off the floor. */
const KNEE_Y = 0.07;
/** Which way a tucked foot runs from the ankle to the ball and its top faces, in the side-on plane. */
const TOES = V(0, -1, -0.25).normalize();
const INSTEP = V(0, -0.25, 1).normalize();
/** How far a tucked foot's toes bend back from it, to lie on the floor. */
const TUCK = 72;

/**
 * Kneeling: the knees on the floor at `feet.z`, `feet.stance` either side, the hips over them, back
 * by `s.thigh` degrees, the shins lying back along the floor and the toes tucked under.
 */
function kneeling(e, s, L) {
  const z = e.feet.z ?? 0;
  const hipC = V(0, KNEE_Y + L.thigh * STRAIGHT * Math.cos(rad(s.thigh)), z - L.thigh * STRAIGHT * Math.sin(rad(s.thigh)));
  const ankleY = L.ballY - TOES.y * Math.hypot(L.ballAhead, L.ankleY - L.ballY);
  const ankles = [-1, 1].map((side) => V(side * e.feet.stance, ankleY, z - Math.sqrt((L.shin * STRAIGHT) ** 2 - (ankleY - KNEE_Y) ** 2)));
  return {
    hipC,
    feet: {
      ankles,
      toeOut: 0,
      // The foot's own pitch taken out, so its toes point where they're tucked.
      foot: () => ({
        heading: TOES.clone().multiplyScalar(Math.cos(L.footPitch)).addScaledVector(INSTEP, Math.sin(L.footPitch)),
        top: INSTEP.clone().multiplyScalar(Math.cos(L.footPitch)).addScaledVector(TOES, -Math.sin(L.footPitch)),
      }),
      kneePole: () => V(0, -0.6, 1).normalize(),
      support: () => null,
      offsets: { ball_l: [-TUCK, 0, 0], ball_r: [-TUCK, 0, 0] },
    },
  };
}

/**
 * With `feet.lift` (1 the left foot, -1 the right) that leg leaves the floor and `s.leg` [back, out,
 * bend] sets it, in degrees: the thigh from straight down, behind the hip and out to its side, and
 * the knee's bend. The other foot stands as it did.
 */
function liftedFeet(e, s, L, hipC, feet) {
  const side = e.feet.lift;
  if (!side) return feet;
  const [back, out, bend] = s.leg.map(rad);
  const thigh = V(side * Math.sin(out) * Math.cos(back), -Math.cos(out) * Math.cos(back), -Math.sin(back));
  const behind = V(-side * Math.sin(out) * Math.sin(back), Math.cos(out) * Math.sin(back), -Math.cos(back));
  const shin = thigh.clone().multiplyScalar(Math.cos(bend)).addScaledVector(behind, Math.sin(bend));
  const front = thigh.clone().multiplyScalar(Math.sin(bend)).addScaledVector(behind, -Math.cos(bend));
  const knee = hipC.clone().addScaledVector(X, side * L.hipW).addScaledVector(thigh, L.thigh * STRAIGHT);
  const ankles = [...feet.ankles];
  ankles[side < 0 ? 0 : 1] = knee.addScaledVector(shin, L.shin * STRAIGHT);
  return {
    ...feet,
    ankles,
    foot: (k) => (k === side ? { heading: front.clone(), top: shin.clone().negate() } : feet.foot?.(k)),
    kneePole: (k) => (k === side ? behind.clone().negate() : feet.kneePole(k)),
    support: (k) => (k === side ? null : feet.support(k)),
  };
}

/**
 * A body standing from its side-on angles, seated at `body.hip` when the entry gives one, or with
 * `body.kneel` on its knees: the hip centre, the trunk's direction and the feet.
 */
function bodyOf(e, s, L) {
  if (e.body?.hip) {
    const { hipC, lean } = placed(e, s, L);
    return { hipC, u: trunkUp(lean), lean, feet: seatedFeet(e, L) };
  }
  const lean = spineLean(s.trunk, L);
  if (e.body?.kneel) return { ...kneeling(e, s, L), u: trunkUp(lean), lean };
  const hipC = standingHips(e, s, L);
  return { hipC, u: trunkUp(lean), lean, feet: liftedFeet(e, s, L, hipC, standingFeet(e, L)) };
}

/**
 * The hands of a pose where one arm may work alone: with `grip.one` (1 the left hand, on +x; -1
 * the right) the other hand rests at `grip.rest` [x, y, z], and the working one is `work(side)`.
 */
const handsOf = (e, work) => (side) => (e.grip?.one && side !== e.grip.one ? V(...e.grip.rest) : work(side));

/**
 * Elbows toward `grip.pole` [out, up, forward], the out mirrored for each side, or toward `fallback`.
 * With `grip.one` the resting arm's goes toward `grip.restPole`, where it gives one.
 */
const elbowsTo = (e, fallback) => {
  const pole = e.grip?.pole ?? fallback;
  return (side) => {
    const [x, y, z] = e.grip?.one && side !== e.grip.one ? e.grip.restPole ?? pole : pole;
    return V(side * x, y, z).normalize();
  };
};

/** The spine's segments for an arch: each of `spine_01`..`spine_03` turned `arch` degrees up from the spine's lean. */
const archOf = (arch, lean) => arch && Object.fromEntries(
  ['spine_01', 'spine_02', 'spine_03'].map((n, i) => [n, trunkUp(lean + rad(arch[i]))]),
);

/**
 * Legs off the floor, from the hips: `thigh` swings the knee forward of the hip and `shin` the knee
 * forward of the ankle, as standing, and `feet.point` turns the feet down from square to the shin.
 * A foot's `support` is the plane under its own sole, since nothing bears on it.
 */
function freeLegs(e, s, hipC, L) {
  const [thigh, shin] = [s.thigh ?? 0, s.shin ?? 0];
  const point = rad(e.feet.point ?? 30);
  const down = hanging(-shin);
  const front = hanging(90 - shin);
  const heading = front.clone().multiplyScalar(Math.cos(point)).addScaledVector(down, Math.sin(point));
  const top = front.clone().multiplyScalar(Math.sin(point)).addScaledVector(down, -Math.cos(point));
  const ankles = [-1, 1].map((side) => hipC.clone().addScaledVector(X, side * e.feet.stance)
    .addScaledVector(hanging(thigh), L.thigh * STRAIGHT).addScaledVector(down, L.shin * STRAIGHT));
  return {
    ankles,
    foot: () => ({ heading: heading.clone(), top: top.clone() }),
    kneePole: (side) => hanging(90 + thigh).addScaledVector(X, side * (e.feet.kneesOut ?? 0)).normalize(),
    support: (side) => ({ point: ankles[side < 0 ? 0 : 1].clone().addScaledVector(top, -L.ankleY), normal: top.clone() }),
    offFloor: true,
  };
}

/**
 * Where a body held up by its hands stands, with its shoulder joints at `sh`: the hip centre, the
 * trunk's way and the legs. `s.shrug` lifts the joints off the trunk.
 */
function heldBody(e, s, sh, L) {
  const u = trunkUp(spineLean(s.trunk ?? 0, L));
  const f = forwardOf(u);
  const hipC = sh.clone().addScaledVector(u, -L.trunkUp).addScaledVector(f, L.trunkBack).add(V(0, -L.clavicle * Math.sin(rad(s.shrug ?? 0)), 0));
  return {
    hipC, u, shrug: s.shrug,
    headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.1).normalize(),
    ...freeLegs(e, s, hipC, L),
  };
}

/**
 * The pair of grips a body held up by its hands holds (`kit.power_rack.handles`): the frame's hands,
 * the point midway between them, and how far straight arms reach up or down to that point.
 */
function rackHold(e, L) {
  const { at, across } = heldPair(e.kit.power_rack.handles);
  const reach = (L.up + L.fore) * STRAIGHT + depthOf(e, L);
  const dx = Math.abs(at(1).x - at(-1).x) / 2 - L.shW;
  return { hands: { hand: at, across }, middle: at(1).add(at(-1)).multiplyScalar(0.5), arms: Math.sqrt(Math.max(0, reach * reach - dx * dx)) };
}

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
      // shoulders. The hand on the bar outweighs it; `grip.pole` points them firmly.
      armPole: elbowsTo(e, [e.grip.elbows, -1, 0.7 * (1 - e.grip.elbows)]),
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
      bar: heldPast(e, elbow, s.forearm, s.rotate ?? e.grip.rotate ?? 0, L),
      grip: e.grip.width,
      armPole: (side) => V(side * 0.2, -1, -0.4).normalize(),
    };
  },

  /**
   * Seated in a 45° leg press, the back on its pad and the feet on the sled's plate: the sled rides
   * its rails with the joint, and the ankles sit `feet.down` below the plate's centre, on its face.
   */
  leg_press(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const { plate, sled, plateUp, hand } = s.linkage;
    const ankle = plate.clone().addScaledVector(plateUp, -e.feet.down).addScaledVector(sled, -L.ankleY);
    const toeOut = rad(e.feet.toeOut);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.55).normalize(),
      ankles: [-1, 1].map((side) => ankle.clone().addScaledVector(X, side * e.feet.stance)),
      foot: (side) => ({
        heading: plateUp.clone().multiplyScalar(Math.cos(toeOut)).addScaledVector(X, side * Math.sin(toeOut)),
        top: sled.clone().negate(),
      }),
      kneePole: (side) => plateUp.clone().addScaledVector(X, side * (e.feet.kneesOut ?? 0.35)).normalize(),
      support: () => ({ point: plate, normal: sled.clone().negate() }),
      hand,
      across: () => V(0, 0, 1),
      armPole: (side) => V(side, -0.2, -0.5).normalize(),
      plate, sled, plateUp,
    };
  },

  /** Seated against a back pad, pressing two handles that swing on levers hung from a pivot above. */
  chest_press(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const { pivot, handle, swing } = s.linkage;
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.12).normalize(),
      ...seatedFeet(e, L),
      // Horizontal handles, held like a bar: the knuckles run along them.
      hand: handle,
      across: (side) => X.clone().multiplyScalar(-side),
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
    const { height, y: hingeY, z: hingeZ } = benchHinge(e.kit.bench);
    const lean = rad(angle - 90 + (e.body.lean ?? 0));
    // The corner's bisector runs (cos A/2, sin A/2) over [y, z]: `sit` off the face along it is
    // sit / cos(A/2) long. Down the pad from the corner is [-sin A, cos A]. The hinge is on the pad's
    // underside, so its face swings out by the pad's thickness along the same line.
    const hipC = V(0, height + e.body.sit - e.body.seat * Math.sin(a), hingeZ + (e.body.sit + height - hingeY) * Math.tan(a / 2) + e.body.seat * Math.cos(a));
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
   * of the middle; with `grip.one` one arm works and the other rests. `armOut` leans the arm out to
   * the side from that plane, in degrees about the shoulder: a Y overhead.
   */
  arms(e, s, L) {
    const { hipC, u, lean, feet } = bodyOf(e, s, L);
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const elbow = sh.clone().addScaledVector(hanging(s.upperArm), L.up * STRAIGHT);
    // On a machine the bar is the machine's, and the arm reaches it from the shoulder.
    const bar = s.linkage?.bar ?? heldPast(e, elbow, s.forearm, s.rotate ?? e.grip.rotate ?? 0, L);
    // Out is away from the body, which turns a hanging arm the other way from a raised one.
    const out = rad(s.armOut ?? 0) * (Math.abs(s.upperArm) > 90 ? 1 : -1);
    const pole = elbowsTo(e, [0.2, -1, -0.4]);
    const leaned = (p, side) => {
      const shoulder = sh.clone().addScaledVector(X, side * L.shW);
      return p.sub(shoulder).applyAxisAngle(f, -side * out).add(shoulder);
    };
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.25).normalize(),
      spine: archOf(e.body?.arch, lean),
      ...feet,
      bar, grip: e.grip.width,
      hand: handsOf(e, (side) => (out ? leaned(bar.clone().setX(side * e.grip.width), side) : bar.clone().setX(side * e.grip.width))),
      armPole: out ? (side) => pole(side).applyAxisAngle(f, -side * out) : pole,
      elbow, upper: hanging(s.upperArm),
    };
  },

  /**
   * A split stance, the feet planted through the rep: the left foot flat `feet.front` ahead, the
   * right `feet.back` behind (its ball's place), up on its ball with the heel `heel` degrees off the
   * floor, heels up positive, its toes bent back as far so they stay down; `feet.cross` steps it that
   * far across behind the front foot (a curtsy), and `feet.hips` moves the hips and arms that far toward it.
   * The hips drop between them along `hip` [y, z], the trunk at `trunk`, and the arms hang straight down `grip.width` either side of the middle.
   */
  lunge(e, s, L) {
    const hipC = V(e.feet.hips ?? 0, ...s.hip);
    const lean = spineLean(s.trunk, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const a = rad(s.heel);
    const heading = V(0, -Math.sin(a), Math.cos(a));
    const top = V(0, Math.cos(a), Math.sin(a));
    const ball = V(-e.feet.stance + (e.feet.cross ?? 0), L.ballY, e.feet.back);
    const sh = shouldersOf(hipC, u, L);
    const reach = (L.up + L.fore) * STRAIGHT + depthOf(e, L);
    const drop = Math.sqrt(Math.max(0, reach * reach - (e.grip.width - L.shW) ** 2));
    const toeOut = rad(e.feet.toeOut ?? 0);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.2).add(V(0, 0.25, 0)).normalize(),
      ankles: [ball.clone().addScaledVector(heading, -L.ballAhead).addScaledVector(top, L.ankleY - L.ballY), V(e.feet.stance, L.ankleY, e.feet.front)],
      foot: (side) => (side < 0 ? { heading: heading.clone(), top: top.clone() } : { heading: V(Math.sin(toeOut), 0, Math.cos(toeOut)), top: V(0, 1, 0) }),
      kneePole: (side) => V(side * (e.feet.kneesOut ?? 0), 0, 1).normalize(),
      support: (side) => (side < 0 ? { point: ball.clone().addScaledVector(top, -L.ballY), normal: top.clone() } : floor),
      hand: (side) => V(hipC.x + side * e.grip.width, sh.y - drop, sh.z),
      armPole: elbowsTo(e, [0.4, 0, -1]),
      offsets: { ball_r: [-s.heel, 0, 0] },
    };
  },

  /**
   * One knee and the same-side hand on a flat bench, the other foot on the floor: a 1-arm row. The
   * right shin lies along the pad from `feet.knee` [y, z], its foot off the pad's end; the left foot
   * stands `feet.out` to the side at `feet.z`. The hips are at `body.hip`, the trunk at `body.trunk`;
   * the left hand rows between `hand` [x, y, z] at the top and the bottom, palm in, the right rests
   * at `grip.rest`.
   */
  bench_row(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const knee = V(-e.feet.stance, ...e.feet.knee);
    const ankle = knee.clone().add(V(0, 0, -L.shin * STRAIGHT));
    const p = rad(e.feet.point ?? 20);
    const heading = V(0, -Math.sin(p), -Math.cos(p));
    const top = V(0, -Math.cos(p), Math.sin(p));
    const toeOut = rad(e.feet.toeOut ?? 0);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, e.body?.look ?? 0.25).normalize(),
      ankles: [ankle, V(e.feet.out, L.ankleY, e.feet.z)],
      foot: (side) => (side < 0 ? { heading: heading.clone(), top: top.clone() } : { heading: V(Math.sin(toeOut), 0, Math.cos(toeOut)), top: V(0, 1, 0) }),
      kneePole: (side) => V(side * 0.15, 0, 1).normalize(),
      support: (side) => (side < 0 ? { point: ankle.clone().addScaledVector(top, -L.ankleY), normal: top.clone() } : floor),
      hand: handsOf(e, (side) => V(side * s.hand[0], s.hand[1], s.hand[2])),
      across: (side) => (side === e.grip.one ? V(0, 0, 1) : X.clone().multiplyScalar(-side)),
      armPole: elbowsTo(e, [0.15, 0.3, -1]),
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
    const { hipC, u, lean, feet } = bodyOf(e, s, L);
    const f = forwardOf(u);
    // With `grip.joined` both hands hold one thing, that far either side of the point `hand` gives.
    const joined = e.grip?.joined;
    const at = (p, side) => (joined ? V(p[0] + side * joined, p[1], p[2]) : V(side * p[0], p[1], p[2]));
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
      spine: archOf(e.body?.arch, lean),
      ...feet,
      hand: handsOf(e, work),
      // Both hands on one bar (an upright row): it lies between them, and with a barbell the hands
      // hold it across the palm, knuckles along it.
      ...(e.grip?.one ? {} : { bar: V(joined ? s.hand[0] : 0, work(1).y, work(1).z) }),
      ...(e.kit?.barbell && !e.grip?.one ? { across: (side) => X.clone().multiplyScalar(-side) } : {}),
      // `grip.across` [x, y, z] lays the knuckles along one line through the rep: front to back for
      // a raise, palms in by the sides and down with the arms out.
      ...(e.grip?.across ? { across: () => V(...e.grip.across) } : {}),
      armPole: elbowsTo(e, [1, -0.3, -0.5]),
    };
  },

  /**
   * The knees bending against a machine's roller: seated with the thighs along the seat (a leg
   * extension, a seated leg curl), or with `body.prone` lying face down along a bench, the head
   * toward +z (a lying leg curl). The thighs leave the hips `body.thigh` degrees below level, and the
   * shin lies against the roller, as far from its axis as the lever holds it: along the lever with
   * the knee on the pivot, and turned from it with a knee off it. The feet stay square to the shins,
   * and the hands hold the machine's handles.
   */
  knee_lever(e, s, L) {
    const prone = Boolean(e.body.prone);
    const hipC = V(0, ...e.body.hip);
    const lean = prone ? rad(90 + (e.body.trunk ?? 0)) : spineLean(s.trunk ?? e.body.trunk, L);
    const u = trunkUp(lean);
    // The thigh's and the shin's ways in the side-on plane, round from forward toward down.
    const thighPhi = prone ? Math.PI - rad(e.body.thigh ?? 0) : rad(e.body.thigh ?? 0);
    const hip = (side) => V(side * e.feet.stance, hipC.y, hipC.z);
    const knee = (side) => hip(side).addScaledVector(sideOn(thighPhi), L.thigh * STRAIGHT);
    const toRoller = s.linkage.roller.clone().sub(knee(0));
    const shinPhi = Math.atan2(-toRoller.y, toRoller.z) + Math.asin(s.linkage.off / toRoller.length());
    const shin = sideOn(shinPhi);
    const front = sideOn(shinPhi - Math.PI / 2);
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
      hand: s.linkage.hand,
      across: s.linkage.across,
      armPole: elbowsTo(e, [1, -0.2, -0.5]),
      knee: knee(0), shin, front, thigh: sideOn(thighPhi), kneeUp: sideOn(thighPhi - Math.PI / 2),
    };
  },

  /**
   * Seated with the knees under a pad and the balls of the feet on the platform's edge, `ball`: the
   * lever carries the pad, and the knees rise with it. The heels rise as far as it takes the knee to
   * reach the lever: the ankles turn `ankle` degrees from flat, heels up positive, and the knee stands
   * between the hip and the ankle. The hands hold the handles on the lever.
   */
  calf_raise(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const { value, pivot, ball, rest, hand, across } = s.linkage;
    // The sole tilted `deg` with the ankle, about the ball: its way toward the toes, and its up.
    const sole = (deg) => {
      const a = rad(deg);
      return [V(0, -Math.sin(a), Math.cos(a)), V(0, Math.cos(a), Math.sin(a))];
    };
    const onBall = (side) => V(side * e.feet.stance, ...ball);
    const anklesAt = ([head, up]) => [-1, 1].map((side) => onBall(side).addScaledVector(head, -L.ballAhead).addScaledVector(up, L.ankleY - L.ballY));
    const pole = V(0, 1, 0.3).normalize();
    const kneeOf = (feet) => kneeAt(V(0, hipC.y, hipC.z), feet[1].clone().setX(0), L, pole);
    // How far the lever has turned to lie over the knee, up from its stretch.
    const turned = (p) => Math.atan2(p.y - pivot.y, pivot.z - p.z) - rest;
    let [lo, hi] = [-45, 60];
    for (let i = 0; i < 40; i++) {
      const m = (lo + hi) / 2;
      if (turned(kneeOf(anklesAt(sole(m)))) < value) lo = m; else hi = m;
    }
    const [heading, top] = sole((lo + hi) / 2);
    const ankles = anklesAt([heading, top]);
    const knee = kneeOf(ankles);
    return {
      hipC, u,
      headDir: u.clone().addScaledVector(f, 0.25).normalize(),
      ankles,
      toeOut: 0,
      foot: () => ({ heading: heading.clone(), top: top.clone() }),
      kneePole: (side) => pole.clone().addScaledVector(X, side * 0.15).normalize(),
      support: (side) => ({ point: onBall(side).addScaledVector(top, -L.ballY), normal: top.clone() }),
      hand,
      across,
      armPole: elbowsTo(e, [1, -0.3, -0.4]),
      knee, pivot,
      // The top of the platform, under the balls.
      platform: V(0, ball[0] - L.ballY, ball[1]),
    };
  },

  /**
   * In a hack squat's sled, the back on its pad and the shoulders under its pads: the carriage rides
   * the rails with the joint, the hips and the handles on it; the trunk keeps the pad's lean,
   * `body.trunk`, and the feet stand on the machine's platform.
   */
  hack_squat(e, s, L) {
    const { hipC, hand, face, tilt, sled } = s.linkage;
    const u = trunkUp(spineLean(e.body.trunk, L));
    const f = forwardOf(u);
    const sh = shouldersOf(hipC, u, L);
    const heading = V(0, Math.sin(tilt), Math.cos(tilt));
    const top = V(0, Math.cos(tilt), -Math.sin(tilt));
    const toeOut = rad(e.feet.toeOut ?? 0);
    const ankles = [-1, 1].map((side) => face.clone().setX(side * e.feet.stance).addScaledVector(top, L.ankleY));
    return {
      hipC, u, shoulders: sh,
      headDir: u.clone().addScaledVector(f, 0.15).normalize(),
      ankles,
      foot: (side) => ({ heading: heading.clone().applyAxisAngle(top, side * toeOut), top: top.clone() }),
      kneePole: (side) => V(side * (e.feet.kneesOut ?? 0.35), 0, 1).normalize(),
      support: () => ({ point: face.clone(), normal: top.clone() }),
      hand,
      across: () => f.clone().negate(),
      armPole: elbowsTo(e, [0.6, -1, 0.3]),
      sled, face, plateUp: top,
    };
  },

  /**
   * Seated, the hands on handles that swing on the machine's levers. A chest-supported row leans the
   * chest onto a pad; a machine pulldown sits under thigh pads.
   */
  lever_handles(e, s, L) {
    const { hipC, lean } = placed(e, s, L);
    const u = trunkUp(lean);
    const f = forwardOf(u);
    const { pivot, handle } = s.linkage;
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
   * Standing on a machine's foot plate, the spine along its inclined chest pad, the hands on grips
   * its lever carries: a chest-supported T-bar row, a Kelso shrug.
   */
  chest_pad(e, s, L) {
    const { handle, across, spine, sole, soleUp, toes, ankle } = s.linkage;
    const f = forwardOf(spine);
    const toeOut = rad(e.feet.toeOut ?? 0);
    return {
      hipC: V(0, ...e.body.hip), u: spine,
      headDir: spine.clone().addScaledVector(f, e.body?.look ?? -0.3).normalize(),
      ankles: [-1, 1].map((side) => ankle.clone().setX(side * e.feet.stance)),
      foot: (side) => ({ heading: toes.clone().applyAxisAngle(soleUp, side * toeOut), top: soleUp.clone() }),
      kneePole: (side) => f.clone().addScaledVector(X, side * (e.feet.kneesOut ?? 0.2)).normalize(),
      support: () => ({ point: sole.clone(), normal: soleUp.clone() }),
      hand: handle, across,
      armPole: elbowsTo(e, [0.5, 0.4, -1]),
    };
  },

  /**
   * Hanging from a rack's bar by the hands, the feet off the floor: a pull-up, a chin-up, a hang, a
   * leg raise. The shoulders hang straight-armed under `kit.power_rack.handles`, the pair of grips,
   * and `rise` lifts them from there, `back` behind the grips.
   */
  hang(e, s, L) {
    const { hands, middle, arms } = rackHold(e, L);
    const sh = V(middle.x, middle.y - arms + s.rise, middle.z - (s.back ?? 0));
    return { ...heldBody(e, s, sh, L), ...hands, armPole: elbowsTo(e, [0.8, -1, 0.3]) };
  },

  /**
   * Resting on a rack's dip handles by the hands, the feet off the floor: a dip. The shoulders stand
   * straight-armed over `kit.power_rack.handles`, the pair of grips, midway between them, and `sink`
   * lowers them from there, `over` ahead of the grips.
   */
  support(e, s, L) {
    const { hands, middle, arms } = rackHold(e, L);
    const sh = V(middle.x, middle.y + arms - s.sink, middle.z + (s.over ?? 0));
    return { ...heldBody(e, s, sh, L), ...hands, armPole: elbowsTo(e, [0.25, 0, -1]) };
  },

  /**
   * On a captain's chair: the forearms on the arm pads, the upper arms upright, the hands closed on
   * the grips ahead of the elbows, the back to the back pad and the legs hanging free. The elbows sit
   * on the pads as far behind the grips as the forearms reach, and the shoulders stand over them.
   */
  chair(e, s, L) {
    const fore = L.fore * STRAIGHT + depthOf(e, L);
    const upper = L.up * STRAIGHT;
    const grip = CHAIR.at(1);
    const ey = CHAIR.padTop + CHAIR.elbowLift;
    const along = Math.sqrt(Math.max(0, fore * fore - (grip.y - ey) ** 2 - (grip.x - CHAIR.padX) ** 2));
    const ez = grip.z - along + (s.back ?? 0);
    const sh = V(0, ey + Math.sqrt(Math.max(0, upper * upper - (L.shW - CHAIR.padX) ** 2)), ez);
    return { ...heldBody(e, s, sh, L), hand: CHAIR.at, across: CHAIR.across, armPole: elbowsTo(e, [0.3, -1, -0.1]) };
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
