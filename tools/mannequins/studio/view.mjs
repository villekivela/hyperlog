// The studio's camera (README, The studio), with nothing of the page in it. A view looks at its
// `target` from `distance` away, turned `azimuth` degrees round the vertical (0 in front, on +z) and
// tilted `elevation` up (any angle: past 90° it is over the top, below 0 under the floor), rolled
// `roll` degrees about the line of sight. A framing is what an entry keeps of it, which the phone
// opens on: the target, how much it shows, the turn and the tilt.
import * as THREE from 'three';

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

/** How much a hand's close-up shows either way of the hand, in metres, and where it looks from: in front, out to the hand's side and a little above. */
const CLOSE_UP = { halfHeight: 0.12, azimuth: 55, elevation: 15 };

/** The close-up of a hand holding at `at` (a point), on side `side` of the body (+1 for +x, its left). */
export const closeUp = (at, side) => viewOf({ target: at.toArray(), halfHeight: CLOSE_UP.halfHeight, azimuth: side * CLOSE_UP.azimuth, elevation: CLOSE_UP.elevation });

/** Hides or shows every part of `root` in the plate tone (barbell plates, dumbbell heads, a stack's plates), so the hands on the bar can be seen. */
export function showPlates(root, shown) {
  root.traverse((o) => { if (o.isMesh && o.material?.name === 'plate') o.visible = shown; });
}
