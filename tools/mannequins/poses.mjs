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
    // The trunk is traced from a true side view (z3Nk7sbHZSU). The knee passes behind the plates
    // there, so the shin and thigh are authored: the knees soften from about 20° to 50° as the hips
    // go back, and the bar runs down the thighs to mid-shin.
    trace: 'barbell_rdl',
    top: { shin: 5, thigh: 14, trunk: 11 },
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
    // On the back along the bench, the head toward -z, the thighs near flat, the feet on the floor.
    body: { hip: [0.55, 0.28], trunk: -89 },
    feet: { stance: 0.3, z: 0.86, toeOut: 25 },
    // From locked out over the shoulders to the lower chest, the elbows tucked about 50°.
    top: { bar: [1.06, -0.19] },
    bottom: { bar: [0.71, -0.05] },
    grip: { width: 0.4, elbows: 0.55 },
    camera: { target: [0, 0.55, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 32 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  lat_pulldown: {
    equipment: 'cable',
    motion: 'pulldown',
    kit: { cable_tower: { column: 0.95, pulley: [2.25, -0.04], attachment: 'lat_bar', seat: [0.47, 0.02], thighPad: [0.71, 0.3] } },
    body: { hip: [0.56, 0] },
    // Knees at about 95°, leaning back a little more as the elbows come down to the sides.
    feet: { stance: 0.16, z: 0.32, toeOut: 12 },
    top: { trunk: -18, bar: [1.62, -0.04] },
    bottom: { trunk: -27, bar: [1.03, 0.04] },
    grip: { width: 0.4 },
    camera: { target: [0, 1.12, 0.38], halfHeight: 1.18, azimuth: 62, elevation: 8 },
  },

  close_grip_lat_pulldown: { like: 'lat_pulldown', grip: { width: 0.24 } },

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
    top: { handle: [0.88, 0.36], press: 0 },
    bottom: { handle: [0.88, -0.01], press: 1 },
    lever: 0.85,
    grip: { width: 0.27 },
    camera: { target: [0, 0.98, 0.1], halfHeight: 1.04, azimuth: 56, elevation: 12 },
  },
};
