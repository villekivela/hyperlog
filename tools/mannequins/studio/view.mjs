// The studio's camera (README, The studio), with nothing of the page in it. A view looks at its
// `target` from `distance` away, turned `azimuth` degrees round the vertical (0 in front, on +z) and
// tilted `elevation` up (any angle: past 90° it is over the top, below 0 under the floor), rolled
// `roll` degrees about the line of sight. A framing is what an entry keeps of it, which the phone
// opens on: the target, how much it shows, the turn and the tilt.
import * as THREE from 'three';
import { heldAt, sideOf } from '../rig.mjs';

/** Half the camera's field of view, top to bottom, in degrees: a framing's `halfHeight` is shown at its distance. */
export const HALF_FOV = 13;
const rad = THREE.MathUtils.degToRad;
const Y = new THREE.Vector3(0, 1, 0);

/** Half the height `view` shows at its target, in metres. */
export const halfHeightOf = (view) => view.distance * Math.tan(rad(HALF_FOV));

/** The view a framing (`{ target, halfHeight, azimuth, elevation }`) opens on, not rolled. */
export function viewOf({ target, halfHeight, azimuth, elevation }) {
  return { target: [...target], distance: halfHeight / Math.tan(rad(HALF_FOV)), azimuth, elevation, roll: 0 };
}

/** The way from the target to the camera, and the camera's up before its roll. */
function axesOf({ azimuth, elevation }) {
  const a = rad(azimuth);
  const e = rad(elevation);
  return {
    back: new THREE.Vector3(Math.cos(e) * Math.sin(a), Math.sin(e), Math.cos(e) * Math.cos(a)),
    up: new THREE.Vector3(-Math.sin(e) * Math.sin(a), Math.cos(e), -Math.sin(e) * Math.cos(a)),
    // What tilting the camera up turns it about (it doesn't move with the tilt).
    tilt: new THREE.Vector3(-Math.cos(a), 0, Math.sin(a)),
  };
}

/** Where the camera of `view` is, which way is up on its screen, and what it looks at. */
export function placeOf(view) {
  const target = new THREE.Vector3(...view.target);
  const { back, up } = axesOf(view);
  return { position: target.clone().addScaledVector(back, view.distance), up: up.applyAxisAngle(back, -rad(view.roll ?? 0)), target };
}

/**
 * `view` orbited `yaw` degrees round the vertical and `pitch` up, about `pivot` (a point; the
 * target by default): the camera swings round the pivot, which stays where it is on the screen.
 */
export function orbit(view, yaw, pitch, pivot = new THREE.Vector3(...view.target)) {
  const turn = new THREE.Quaternion().setFromAxisAngle(Y, rad(yaw));
  const turned = { ...view, azimuth: view.azimuth + yaw };
  turn.premultiply(new THREE.Quaternion().setFromAxisAngle(axesOf(turned).tilt, rad(pitch)));
  const target = new THREE.Vector3(...view.target).sub(pivot).applyQuaternion(turn).add(pivot);
  return { ...turned, elevation: view.elevation + pitch, target: target.toArray() };
}

/** `view` slid `right` and `up` metres across its screen, as it is rolled: the camera and its target together. */
export function pan(view, right, up) {
  const { position, up: screenUp, target } = placeOf(view);
  const across = target.clone().sub(position).cross(screenUp).normalize();
  return { ...view, target: target.addScaledVector(across, right).addScaledVector(screenUp, up).toArray() };
}

/** How much nearer a step of a dolly takes the camera: a tenth of the way in. */
const DOLLY = 0.9;

/** `view` with the camera `steps` steps nearer its target (out, for fewer than none). */
export const dolly = (view, steps) => ({ ...view, distance: view.distance * DOLLY ** steps });

/** `view` rolled `degrees` about its line of sight. */
export const roll = (view, degrees) => ({ ...view, roll: (view.roll ?? 0) + degrees });

const round = (v) => Math.round(v * 1e4) / 1e4;
/** An angle in degrees as the same turn within (-180, 180]. */
const within = (deg) => {
  const d = ((deg % 360) + 360) % 360;
  return d > 180 ? d - 360 : d;
};

/**
 * The framing `view` keeps for the phone, which opens on it: its target, how much it shows at that
 * distance, its turn and its tilt, to four places. A view over the top or under the floor is the
 * same camera from the far side, the right way up; a roll the phone hasn't.
 */
export function framingOf(view) {
  let elevation = within(view.elevation);
  let azimuth = view.azimuth;
  if (Math.abs(elevation) > 90) {
    elevation = Math.sign(elevation) * 180 - elevation;
    azimuth += 180;
  }
  return {
    target: view.target.map(round),
    halfHeight: round(halfHeightOf(view)),
    azimuth: round(within(azimuth)),
    elevation: round(elevation),
  };
}

/** How much a hand's close-up shows either way of the hand, in metres. */
const CLOSE_UP = 0.12;
/**
 * The ways a close-up may look at a hand from, in turn, [azimuth out to the hand's side, elevation]:
 * in front and outside, in front, outside, above, below, and behind and outside.
 */
const CLOSE_UP_WAYS = [[55, 15], [20, 10], [90, 10], [45, 65], [45, -40], [135, 15]];

/** The close-up of a hand holding at `at` (a point), on side `side` of the body (+1 for +x, its left), from the first way. */
export const closeUp = (at, side, [azimuth, elevation] = CLOSE_UP_WAYS[0]) => viewOf({ target: at.toArray(), halfHeight: CLOSE_UP, azimuth: side * azimuth, elevation });

/** The points of a hand a close-up wants to see: the last joint and the end of each finger, where they close round the bar. */
const TIPS = ['index', 'middle', 'ring', 'pinky', 'thumb'].flatMap((f) => [`${f}_03`, `${f}_04_leaf`]);

/**
 * The close-up of the hand on side `k` of `stage` (the body and its kit), as posed now: from the way
 * that sees most of its fingertips, the first of the ways on a tie. A tip is seen when nothing of
 * the body or the kit shown stands between it and the camera: the deadlift's hanging hand, seen from
 * in front, shows only its back.
 */
export function closeUpOf({ rig, kit }, k) {
  const solid = [...rig.meshes];
  kit.group.traverse((o) => { if (o.isMesh && o.visible) solid.push(o); });
  for (const m of rig.meshes) m.computeBoundingSphere();
  const tips = TIPS.map((n) => rig.bones[`${n}_${k}`].getWorldPosition(new THREE.Vector3()));
  // From each tip out toward the camera, so a camera inside a part is hidden by it: the tip's own
  // finger is left from the inside, which a ray doesn't meet.
  const seen = (view) => {
    const from = placeOf(view).position;
    return tips.filter((tip) => {
      const out = from.clone().sub(tip);
      return !new THREE.Raycaster(tip, out.clone().normalize(), 0, out.length()).intersectObjects(solid, false).length;
    }).length;
  };
  const views = CLOSE_UP_WAYS.map((way) => closeUp(heldAt(rig, k), sideOf(rig, k), way));
  const counts = views.map(seen);
  return views[counts.indexOf(Math.max(...counts))];
}

/** Hides or shows every part of `root` in the plate tone (barbell plates, dumbbell heads, a stack's plates), so the hands on the bar can be seen. */
export function showPlates(root, shown) {
  root.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') o.visible = shown; });
}
