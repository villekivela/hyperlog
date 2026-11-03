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

  // The starter program's exercises (#127, the starter batch): first poses for the owner to tune.

  // A bench press with the hands about shoulder width apart, the elbows tucked toward the sides.
  close_grip_bench_press: {
    like: 'barbell_bench_press',
    top: { bar: [1.15, -0.21] },
    bottom: { bar: [0.725, -0.05] },
    grip: { width: 0.22, elbows: 0.45 },
  },

  // A pulldown on two handles, palms facing each other, the elbows driving down in front of the body.
  '2_grip_lat_pulldown': {
    like: 'lat_pulldown',
    kit: { cable_tower: { attachment: 'handles', spread: 0.2 } },
    grip: { width: 0.2, rotate: 90, elbows: 0.3 },
    top: { bar: [1.66, 0.02], shrug: 8 },
    bottom: { bar: [1.1, 0.06], shrug: 0 },
  },

  // Seated, the thighs on the seat and the shins under the roller: from straight legs down to the
  // knees bent past a right angle.
  leg_extension: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_extension: { seat: [0.5, 0.1], pad: 0.18 } },
    body: { hip: [0.59, 0], trunk: -8, thigh: 0 },
    top: { knee: 10 },
    bottom: { knee: 95 },
    feet: { stance: 0.1 },
    grip: { width: 0.27, at: [0.52, 0.06] },
    camera: { target: [0, 0.62, 0.2], halfHeight: 0.82, azimuth: 70, elevation: 10 },
  },

  // Seated under the thigh pad, the roller behind the calves: from the knees bent to straight legs.
  seated_leg_curl: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_curl: { seat: [0.5, 0.1], pad: 0.18 } },
    body: { hip: [0.59, 0], trunk: -8, thigh: 0 },
    top: { knee: 95 },
    bottom: { knee: 8 },
    feet: { stance: 0.1 },
    grip: { width: 0.27, at: [0.52, 0.06] },
    camera: { target: [0, 0.62, 0.2], halfHeight: 0.82, azimuth: 70, elevation: 10 },
  },

  // Face down along the bench, the knees at its end and the roller on the backs of the ankles:
  // from the heels drawn up to straight legs.
  lying_leg_curl: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_curl: { lying: true, bench: [-0.42, 0.95], height: 0.62 } },
    body: { prone: true, hip: [0.75, 0], thigh: 2, head: -20 },
    top: { knee: 100 },
    bottom: { knee: 6 },
    feet: { stance: 0.1 },
    grip: { width: 0.24, at: [0.6, 0.86], pole: [1, -1, 0.2] },
    camera: { target: [0, 0.75, 0.1], halfHeight: 0.85, azimuth: 80, elevation: 14 },
    shadow: { size: [0.9, 1.6], at: [0, 0.2] },
  },

  // Seated, the knees under the pad and the balls of the feet on the platform: the heels rise from
  // stretched below it.
  seated_calf_raise: {
    equipment: 'machine',
    motion: 'calf_raise',
    kit: { calf_raise_machine: { seat: [0.48, -0.05] } },
    body: { hip: [0.57, 0], trunk: -2 },
    top: { ankle: 22 },
    bottom: { ankle: -18 },
    feet: { stance: 0.1, ball: [0.2, 0.52] },
    grip: { width: 0.26, at: [0.5, 0.0] },
    camera: { target: [0, 0.55, 0.25], halfHeight: 0.78, azimuth: 72, elevation: 10 },
  },

  // In the sled, the back on the pad and the shoulders under the rollers, the feet on the tilted
  // platform: the hips travel down the rails until the knees are well past a right angle.
  hack_squat: {
    equipment: 'machine',
    motion: 'hack_squat',
    kit: { squat_machine: {} },
    body: { trunk: -40 },
    top: { hip: [0.9, 0.05] },
    bottom: { hip: [0.63, 0.27] },
    feet: { stance: 0.16, y: 0.12, z: 0.36, tilt: 20, toeOut: 15, kneesOut: 0.35 },
    grip: { width: 0.3, at: [-0.05, 0.2] },
    camera: { target: [0, 0.85, 0.15], halfHeight: 1.0, azimuth: 70, elevation: 8 },
  },

  // Chest on the pad, palms facing each other: from the arms out in front, the elbows driving back
  // to the handles by the ribs.
  chest_supported_machine_row: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { row_machine: { seat: [0.5, 0], chest: { up: 0.42, front: 0.13 } } },
    body: { hip: [0.59, 0], trunk: 25 },
    pivot: [1.67, 0.53],
    top: { handle: [1.0, 0.8], press: 0 },
    bottom: { handle: [0.95, 0.45], press: 1 },
    feet: { stance: 0.16, z: 0.45, toeOut: 10 },
    grip: { width: 0.22, rotate: 90, pole: [0.4, -0.6, -1] },
    camera: { target: [0, 0.8, 0.3], halfHeight: 0.92, azimuth: 66, elevation: 10 },
  },

  // Seated under the thigh rollers: the handles from overhead, the arms long, down to the shoulders.
  machine_pulldown: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { pulldown_machine: { seat: [0.47, 0.02], thighPad: [0.71, 0.3] } },
    body: { hip: [0.56, 0], trunk: -15 },
    pivot: [1.45, 0.85],
    top: { handle: [1.6, 0.17], press: 0 },
    bottom: { handle: [1.12, 0.12], press: 1 },
    feet: { stance: 0.16, z: 0.32, toeOut: 12 },
    grip: { width: 0.32, pole: [0.8, -1, -0.3] },
    camera: { target: [0, 1.1, 0.35], halfHeight: 1.15, azimuth: 62, elevation: 8 },
  },

  // Seated, the upper arms down the pad: the bar curls from the arms near straight up toward the
  // shoulders.
  machine_preacher_curl: {
    equipment: 'machine',
    motion: 'arms',
    kit: { preacher_curl_machine: { seat: [0.5, 0] } },
    body: { hip: [0.59, 0], trunk: 5 },
    top: { upperArm: 65, forearm: 160 },
    bottom: { upperArm: 65, forearm: 72 },
    feet: { stance: 0.16, z: 0.4, toeOut: 10 },
    grip: { width: 0.18, rotate: 180, pole: [0.2, -0.7, 0.7] },
    camera: { target: [0, 0.9, 0.3], halfHeight: 0.8, azimuth: 70, elevation: 10 },
  },

  // Standing facing away from a low pulley, the left arm back behind the body: it curls with the
  // upper arm still, palm up; the right hand hangs by the hip.
  bayesian_cable_curl: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_column: { at: [0.15, -0.8], pulley: 0.25, attachment: 'handle', hand: 1 } },
    top: { shin: 10, thigh: 10, trunk: 12, upperArm: -22, forearm: 125 },
    bottom: { shin: 10, thigh: 10, trunk: 12, upperArm: -22, forearm: -20 },
    feet: { stance: 0.14, toeOut: 10, kneesOut: 0.2, stagger: -0.32 },
    grip: { width: 0.2, rotate: 180, one: 1, rest: [-0.2, 0.96, 0.06], pole: [0.15, -1, -0.5] },
    camera: { target: [0, 1.0, -0.2], halfHeight: 1.1, azimuth: 80, elevation: 8 },
  },

  // Standing facing away from the pulley, leaning forward, the rope over the head: from the arms
  // straight overhead, the hands lower behind the head.
  overhead_cable_triceps_extension: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_column: { at: [0, -0.6497], pulley: 1.0655, attachment: 'rope' } },
    top: { shin: 8, thigh: 14, trunk: 20, upperArm: 137.2876, forearm: 154.5043 },
    bottom: { shin: 8, thigh: 14, trunk: 20, upperArm: 163.1089, forearm: 284.2194 },
    feet: { stance: 0.16, toeOut: 10, kneesOut: 0.2 },
    grip: { width: 0.07, rotate: 90, pole: [0.25, 0.6, 1] },
    camera: { target: [0, 1.15, -0.2], halfHeight: 1.15, azimuth: 76, elevation: 8 },
  },

  // Standing side-on to a low pulley on the right, the left arm raising the handle out to the side
  // to shoulder height; the right hand holds the column.
  cable_lateral_raise: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_column: { at: [-0.6, 0.05], pulley: 0.15, attachment: 'handle', hand: 1 } },
    top: { shin: 4, thigh: 6, trunk: 6, hand: [0.754, 1.513, 0.329] },
    bottom: { shin: 4, thigh: 6, trunk: 6, hand: [0.0309, 0.8331, 0.0938] },
    feet: { stance: 0.15, toeOut: 8, kneesOut: 0.2 },
    grip: { arc: true, one: 1, rest: [-0.53, 1.12, 0.07], pole: [1, -0.2505, -0.7094] },
    camera: { target: [0, 1.0, 0], halfHeight: 1.1, azimuth: 20, elevation: 8 },
  },

  // Standing between two pulleys at chest height, palms facing: from the arms wide, the hands meet
  // in front of the chest.
  cable_flye: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_column: { at: [0.808, -0.45], pulley: 1.2049, twin: true } },
    top: { shin: 6, thigh: 8, trunk: 12, hand: [0.2037, 1.5397, 0.7136] },
    bottom: { shin: 6, thigh: 8, trunk: 12, hand: [0.7891, 1.2839, -0.0151] },
    feet: { stance: 0.18, toeOut: 10, kneesOut: 0.2 },
    grip: { arc: true, rotate: 144.7937, pole: [1, -0.4, -0.6] },
    camera: { target: [0, 1.05, 0], halfHeight: 1.15, azimuth: 30, elevation: 10 },
  },

  // Standing behind two high pulleys in front, each hand on the far one's cable: from the arms
  // crossed in front, they sweep out wide to the sides on an arc about the shoulders.
  reverse_cable_crossover: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_column: { at: [0.7729, 0.8181], pulley: 1.8, twin: true, crossed: true } },
    top: { shin: 4, thigh: 6, trunk: 4, hand: [0.78, 1.45, 0.08] },
    bottom: { shin: 4, thigh: 6, trunk: 4, hand: [0.18, 1.4357, 0.6348] },
    feet: { stance: 0.16, toeOut: 8, kneesOut: 0.2 },
    grip: { arc: true, pole: [1, 0, -0.3], rotate: 58.0559 },
    camera: { target: [0, 1.1, 0.1], halfHeight: 1.15, azimuth: 24, elevation: 10 },
  },

  // Seated against an upright bench between two low pulleys: the handles press from the shoulders
  // to overhead.
  cable_shoulder_press: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { bench: { angle: 80 }, cable_column: { at: [0.6691, -0.15], pulley: 0.12, twin: true } },
    body: { hip: [0.53, 0.17], trunk: -10 },
    top: { hand: [0.2882, 1.6383, 0.2743] },
    bottom: { hand: [0.32, 1.13, 0.18] },
    feet: { stance: 0.18, z: 0.5, toeOut: 12 },
    grip: { pole: [1, -1, 0.1] },
    camera: { target: [0, 0.95, 0.15], halfHeight: 1.0, azimuth: 30, elevation: 10 },
  },
};
