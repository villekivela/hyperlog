// The pose entries: one per exercise, keyed by its id in app/src/main/assets/exercises.json. An
// entry is data only, and `like: <id>` takes another entry and changes only what it gives (see
// README, Pose entries). Metres, y up, the mannequin facing +z; angles in degrees, side-on.
export const POSES = {
  barbell_squat: {
    equipment: 'barbell',
    motion: 'squat',
    kit: { barbell: {} },
    // The shin, thigh and trunk are traced side-on from the library's technique video
    // (UGwmUJkTIx8); the body keeps that shape and turns about the ankles until the bar is over
    // the middle of the feet.
    trace: 'barbell_squat',
    top: { shin: 6, thigh: 4 },
    bottom: { shin: 47, thigh: 92 },
    // Ankles this far either side of the middle, toes turned out, knees pushed out toward the toes.
    feet: { stance: 0.17, toeOut: 18, kneesOut: 0.45 },
    // The bar on the upper back: up the trunk from the hips to just past the shoulders, and behind
    // the trunk's line. It sits about 6 cm behind the shoulder joints.
    bar: { up: 0.02, back: 0.2 },
    grip: { width: 0.4 },
    camera: { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 },
  },

  barbell_rdl: {
    equipment: 'barbell',
    motion: 'hinge',
    kit: { barbell: {} },
    // Locked out at the top, hips and knees straight (the owner's review; the traced lifter stood
    // with soft knees and a forward trunk, so the RDL isn't traced). The knees soften to about 50°
    // as the hips go back, and the bar runs down the thighs to mid-shin.
    top: { shin: 1, thigh: 0, trunk: 1 },
    bottom: { shin: 8, thigh: 42, trunk: 76 },
    feet: { stance: 0.12, toeOut: 6, kneesOut: 0 },
    bar: { z: 0.16 },
    grip: { width: 0.25 },
    camera: { target: [0, 0.82, 0.05], halfHeight: 1.0, azimuth: 66, elevation: 8 },
  },

  barbell_bench_press: {
    equipment: 'barbell',
    motion: 'bench_press',
    kit: { barbell: {}, bench: {} },
    // On the back along the bench, the head toward -z, the thighs near flat, the feet on the floor
    // back toward the hips. A slight arch: the upper back and the hips on the pad, the chest up and
    // a gap under the lower back. The head lies back on the pad.
    body: { hip: [0.55, 0.28], trunk: -89, head: -15, arch: [16, -16.5, -8] },
    feet: { stance: 0.325, z: 0.445, toeOut: 25 },
    // A normal bench grip, about 1.5 shoulder widths. From locked out over the shoulders, the arms
    // straight, to the lower chest, the elbows out under the wrists so the forearms stand vertical.
    top: { bar: [1.15, -0.23] },
    bottom: { bar: [0.71, -0.09] },
    grip: { width: 0.4, elbows: 0.9 },
    camera: { target: [0, 0.55, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 32 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  // Pressed on an adjustable bench, baked at 0° to 75° (README, Adjustable-bench poses): the body
  // follows the pad, so one entry fits every angle. From locked out over the shoulders, the arms
  // straight, down to the chest, the forearms upright.
  db_incline_press: {
    equipment: 'dumbbell',
    motion: 'dumbbell_press',
    kit: { dumbbells: {}, bench: { hinge: -0.02 } },
    // The stage's framing at the lying and the upright ends; the angles between move steadily from one to the other.
    bench: {
      typical: 30,
      // Settings tuned per angle, `{ path: { angle: value } }`, and interpolated between (README, Adjustable-bench
      // poses). The top.over pair keeps the arms locking out vertically (0.64 up, 0.09 forward of the shoulders),
      // which in the trunk's frame is a different [out, up the trunk] at each angle.
      perAngle: {
        'top.over.0': { 0: 0.646, 15: 0.624, 30: 0.56, 45: 0.457, 60: 0.323, 75: 0.167 },
        'top.over.1': { 0: 0.1023, 15: 0.167, 30: 0.323, 45: 0.457, 60: 0.56, 75: 0.624 },
        'bottom.chest.1': { 0: 0.0271, 60: -0.0878, 75: -0.1487 },
        'bottom.tilt': { 0: 3.3019 },
        'bottom.rotate': { 0: 18.6834 },
        'top.tilt': { 0: 11.6519, 75: 2.3362 },
        'top.rotate': { 0: 20 },
        'grip.width': { 0: 0.3541, 75: 0.3978 },
        'bottom.chest.0': { 60: 0.1196, 75: 0.1072 },
      },
      cameras: {
        0: { target: [0, 0.78, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 22 },
        75: { target: [0, 1.0, 0.0], halfHeight: 1.22, azimuth: 66, elevation: 10 },
      },
    },
    body: { sit: 0.15, seat: 0.06, lean: 8 },
    feet: { stance: 0.3, z: 0.2266, toeOut: 20 },
    // The grip turns (0 overhand, 90 neutral) and the handle tips in the hand, at the top and the bottom.
    top: { press: 0, over: [0.56, 0.323], rotate: 20, tilt: 11.6519 },
    bottom: { press: 1, chest: [0.2084, 0.1151], rotate: 18.6834, tilt: 3.3019 },
    grip: { width: 0.3541, lockout: 0.26, elbows: 0.7975 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  lat_pulldown: {
    equipment: 'cable',
    motion: 'pulldown',
    kit: { cable_tower: { column: 0.95, pulley: [2.25, 0.02], attachment: 'lat_bar', seat: [0.47, 0.02], thighPad: [0.71, 0.3] } },
    body: { hip: [0.56, 0] },
    // Knees at about 95°, leaning back a little more as the elbows come down to the sides. The bar
    // runs straight down under the pulley, with no drift forward or back.
    feet: { stance: 0.16, z: 0.32, toeOut: 12 },
    top: { trunk: -18, bar: [1.62, 0.02] },
    bottom: { trunk: -27, bar: [1.03, 0.02] },
    grip: { width: 0.4 },
    camera: { target: [0, 1.12, 0.38], halfHeight: 1.18, azimuth: 62, elevation: 8 },
  },

  // A narrower grip, pulled to the upper chest rather than as low. The arms hang fully extended at
  // the top with the shoulders let up into the stretch, and the elbows drive straight down by the
  // sides. Underhand: the palms face the lifter.
  close_grip_lat_pulldown: {
    like: 'lat_pulldown',
    grip: { width: 0.24, elbows: 0.0, rotate: 180 },
    top: { bar: [1.70, 0.02], shrug: 9 },
    bottom: { bar: [1.14, 0.02], shrug: 0 },
  },

  triceps_pressdown: {
    equipment: 'cable',
    motion: 'pressdown',
    kit: { cable_tower: { column: 0.74, pulley: [2.05, 0.56], attachment: 'straight_bar' } },
    // Traced side-on from the library's video (B5S2mbg0g5c): standing straight, the forearms from
    // well above level down to the thighs.
    trace: 'triceps_pressdown',
    top: { shin: 4, thigh: 6, trunk: 8, upperArm: 10, forearm: 117 },
    bottom: { shin: 4, thigh: 6, trunk: 8, upperArm: 10, forearm: 24 },
    feet: { stance: 0.13, toeOut: 10, kneesOut: 0.2 },
    grip: { width: 0.13 },
    camera: { target: [0, 1.05, 0.28], halfHeight: 1.12, azimuth: 68, elevation: 8 },
  },

  leg_press: {
    equipment: 'machine',
    motion: 'leg_press',
    kit: { leg_press: { seat: [0.5, 0.04] } },
    body: { hip: [0.62, 0], trunk: -57 },
    top: { knee: 25 },
    bottom: { knee: 100 },
    feet: { stance: 0.15, toeOut: 12, line: 0.1, down: 0.05 },
    // The hands on the handles beside the hips.
    grip: { width: 0.3, at: [0.6, 0.12] },
    camera: { target: [0, 0.74, 0.4], halfHeight: 0.98, azimuth: 118, elevation: 16 },
    shadow: { size: [1.0, 1.6], at: [0, 0.4] },
  },

  machine_chest_press: {
    equipment: 'machine',
    motion: 'chest_press',
    kit: { chest_press: { seat: [0.47, 0.02] } },
    body: { hip: [0.56, 0], trunk: -23.5 },
    feet: { stance: 0.16, z: 0.48, toeOut: 12 },
    // The handles at mid-chest: out in front at the top, back to the shoulders' line at the bottom.
    top: { handle: [0.94, 0.42], press: 0 },
    bottom: { handle: [1.055, -0.01], press: 1 },
    lever: 0.85,
    grip: { width: 0.385 },
    camera: { target: [0, 0.98, 0.1], halfHeight: 1.04, azimuth: 56, elevation: 12 },
  },
};
