// The side-on angles a pose's entry, its trace and the trace check all speak, measured one way
// for the posed mannequin and for the lifter in the footage alike. Each point is [forward, up].
export const ANGLES = ['shin', 'thigh', 'trunk', 'upperArm', 'forearm'];

export const deg = (r) => (r * 180) / Math.PI;

/** How far `to` leans forward of straight up from `from`. */
const lean = (from, to) => deg(Math.atan2(to[0] - from[0], to[1] - from[1]));
/** How far `to` swings forward of straight down from `from`. */
const swing = (from, to) => deg(Math.atan2(to[0] - from[0], from[1] - to[1]));

/**
 * One side's angles, in degrees: `shin` leans the knee forward of the ankle, `thigh` puts the hip
 * behind the knee (90 is a level thigh), `trunk` leans the shoulder forward of the hip,
 * `upperArm` and `forearm` swing the elbow and the wrist forward from hanging straight down.
 */
export function sideAngles({ ankle, knee, hip, shoulder, elbow, wrist }) {
  return {
    shin: lean(ankle, knee),
    thigh: -lean(knee, hip),
    trunk: lean(hip, shoulder),
    upperArm: swing(shoulder, elbow),
    forearm: swing(elbow, wrist),
  };
}
