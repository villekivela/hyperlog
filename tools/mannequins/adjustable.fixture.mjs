// An adjustable-bench entry with round numbers of its own, for the tests of what the motion does
// at every angle: they don't move when the owner re-tunes the shipped Incline DB Press.
export const FIXTURE = {
  equipment: 'dumbbell',
  motion: 'dumbbell_press',
  kit: { dumbbells: {}, bench: { hinge: -0.02 } },
  bench: {
    typical: 30,
    // The arms lock out vertically, 0.64 up and 0.09 forward of the shoulders: in the trunk's frame
    // a different [out, up the trunk] at each angle.
    perAngle: {
      'top.over.0': { 0: 0.646, 15: 0.624, 30: 0.56, 45: 0.457, 60: 0.323, 75: 0.167 },
      'top.over.1': { 0: 0, 15: 0.167, 30: 0.323, 45: 0.457, 60: 0.56, 75: 0.624 },
    },
    cameras: {
      0: { target: [0, 0.78, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 22 },
      75: { target: [0, 1.0, 0.0], halfHeight: 1.22, azimuth: 66, elevation: 10 },
    },
  },
  body: { sit: 0.15, seat: 0.06, lean: 8 },
  feet: { stance: 0.3, z: 0.2266, toeOut: 20 },
  top: { press: 0, over: [0.56, 0.323], rotate: 0, tilt: 0 },
  bottom: { press: 1, chest: [0.1592, -0.0137], rotate: 0, tilt: 0 },
  grip: { width: 0.4, lockout: 0.26, elbows: 0.7975 },
  shadow: { size: [0.9, 1.8], at: [0, 0.1] },
};
