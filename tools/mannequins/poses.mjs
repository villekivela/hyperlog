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
    top: { shin: 6, thigh: 4, wrist: [32.8668, 42.4311] },
    bottom: { shin: 47, thigh: 92 },
    // Ankles this far either side of the middle, toes turned out, knees pushed out toward the toes.
    feet: { stance: 0.17, toeOut: 18, kneesOut: 0.45 },
    // The bar on the upper back: up the trunk from the hips to just past the shoulders, and behind
    // the trunk's line. It sits about 6 cm behind the shoulder joints.
    bar: { up: 0.0693, back: 0.1926 },
    // The thumbs swing further out of the palms than mid-palm's default to wrap the bar behind the
    // neck (`grip.swing`).
    grip: { width: 0.4, swing: 50, pole: [0.6897, -0.2026, -0.6952] },
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
    body: { hip: [0.567, 0.28], trunk: -89, head: -15, arch: [16, -16.5, -8] },
    feet: { stance: 0.325, z: 0.445, toeOut: 25 },
    // A normal bench grip, about 1.5 shoulder widths. From over the shoulders, the elbows soft as the
    // owner tuned them, to the lower chest, the elbows out under the wrists so the forearms stand
    // vertical, the wrist easing straight. Provisional: the owner's morning look settles it (#202).
    top: { bar: [1.1297, -0.2], wrist: [0, 25] },
    bottom: { bar: [0.727, -0.09], wrist: [0, 0] },
    grip: { width: 0.4, elbows: 0.9, swing: 45 },
    camera: { target: [0, 0.55, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 32 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  // Pressed on an adjustable bench, baked at 0° to 85° (README, Adjustable-bench poses): the body
  // follows the pad, so one entry fits every angle. From locked out over the shoulders, the arms
  // straight, down to the chest, the forearms upright.
  db_incline_press: {
    equipment: 'dumbbell',
    motion: 'dumbbell_press',
    kit: { dumbbells: { steady: true }, bench: { hinge: -0.02 } },
    // The stage's framing at the lying and the upright ends; the angles between move steadily from one to the other.
    bench: {
      typical: 45,
      // Settings tuned per angle, `{ path: { angle: value } }`, and interpolated between (README, Adjustable-bench
      // poses). The top.over pair keeps the arms locking out vertically (0.64 up, 0.09 forward of the shoulders),
      // which in the trunk's frame is a different [out, up the trunk] at each angle.
      perAngle: { 'top.over.0': { 0: 0.646, 15: 0.624, 30: 0.56, 45: 0.3949, 60: 0.323, 75: 0.167, 85: 0.056 }, 'top.over.1': { 0: 0.1023, 15: 0.167, 30: 0.323, 45: 0.5057, 60: 0.56, 75: 0.624, 85: 0.643 }, 'bottom.chest.1': { 0: 0.0271, 45: -0.0778, 60: -0.0878, 75: -0.1487 }, 'bottom.tilt': { 0: 3.3019, 45: 14.5264 }, 'bottom.rotate': { 0: 18.6834, 45: 12.5977 }, 'top.tilt': { 0: 11.6519, 45: 14.0308, 75: 2.3362 }, 'top.rotate': { 0: 20, 45: 3.3179 }, 'grip.width': { 0: 0.365, 45: 0.406, 75: 0.3978 }, 'bottom.chest.0': { 45: 0.1001, 60: 0.1196, 75: 0.1072 } },
      cameras: {
        0: { target: [0, 0.78, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 22 },
        75: { target: [0, 1.0, 0.0], halfHeight: 1.22, azimuth: 66, elevation: 10 },
      },
    },
    body: { sit: 0.1253, seat: 0.0495, lean: 8.6694, head: -12.1074 },
    feet: { stance: 0.3, z: 0.2266, toeOut: 20 },
    // The grip turns (0 overhand, 90 neutral) and the handle tips in the hand, at the top and the bottom.
    top: { press: 0, over: [0.56, 0.323], rotate: 20, tilt: 11.6519, wrist: [-17.2888, 7.5586] },
    bottom: { press: 1, chest: [0.2084, 0.1151], rotate: 18.6834, tilt: 3.3019, wrist: [-27.074, 15.5896] },
    grip: { width: 0.3541, lockout: 0.3373, elbows: 0.7975, level: 0.7 },
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
    bottom: { bar: [1.11, 0.02], shrug: 0 },
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
    body: { trunk: -57 },
    // The sled from its stops, the knees past a right angle, up to the knees near straight.
    top: { sled: 0.2771 },
    feet: { stance: 0.15, toeOut: 12, down: 0.05 },
    // The hands on the handles beside the hips.
    grip: {},
    camera: { target: [0, 0.74, 0.4], halfHeight: 0.98, azimuth: 118, elevation: 16 },
    shadow: { size: [1.0, 1.6], at: [0, 0.4] },
  },

  machine_chest_press: {
    equipment: 'machine',
    motion: 'chest_press',
    kit: { chest_press: {} },
    body: { trunk: -23.5 },
    feet: { stance: 0.16, z: 0.48, toeOut: 12 },
    // The levers run limit to limit: out in front at the top, back to the shoulders' line at the bottom.
    grip: {},
    camera: { target: [0, 0.98, 0.1], halfHeight: 1.04, azimuth: 56, elevation: 12 },
  },

  // The starter program's exercises (#127, the starter batch): first poses for the owner to tune.

  // A bench press with the hands about shoulder width apart, the elbows tucked toward the sides:
  // `grip.pole` holds them beside the ribs, where the wrist alone left them inside the chest.
  close_grip_bench_press: {
    like: 'barbell_bench_press',
    top: { bar: [1.147, -0.21] },
    bottom: { bar: [0.7517, -0.063] },
    grip: { width: 0.3005, elbows: 0.2191, pole: [0.05, -1, 0.55] },
  },

  // Two grips on the lat bar (owner): the first set wide and overhand, at 1.5 shoulder widths, the
  // second narrow and underhand, at shoulder width, palms to the face as in a curl, the elbows
  // driving down in front of the body. A clip each, in that order.
  '2_grip_lat_pulldown': {
    like: 'lat_pulldown',
    // The machine and the bar at the top as the owner tuned them, the same for both grips; the
    // rest of what they tuned on the wide grip is the wide grip's own.
    kit: { cable_tower: { seat: [0.48, -0.01], pulley: [2.13, -0.04], column: 0.92, thighPad: [0.6819, 0.1745] } },
    top: { bar: [1.62, -0.05] },
    grips: {
      wide: { grip: { width: 0.4018, rotate: 0 }, top: { rotate: 0, wrist: [0, 29.3072] }, bottom: { bar: [1.119, -0.0434] } },
      narrow: { grip: { width: 0.19, rotate: 180, elbows: 0.2 }, bottom: { bar: [1.08, 0.06] } },
    },
  },

  // Seated, the thighs on the seat and the shins under the roller: from straight legs down to the
  // knees bent past a right angle.
  leg_extension: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_extension: {} },
    body: { trunk: -8, thigh: -10 },
    // The lever from the stretch up to 10° short of straight legs.
    top: { lever: 85 },
    feet: { stance: 0.1 },
    grip: {},
    camera: { target: [0, 0.62, 0.2], halfHeight: 0.82, azimuth: 70, elevation: 10 },
  },

  // Seated under the thigh pad, the roller behind the calves: from the knees bent to straight legs.
  seated_leg_curl: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_curl: {} },
    body: { trunk: -8, thigh: -10 },
    top: { lever: 87 },
    feet: { stance: 0.1 },
    grip: {},
    camera: { target: [0, 0.62, 0.2], halfHeight: 0.82, azimuth: 70, elevation: 10 },
  },

  // Face down along the bench, the knees at its end and the roller on the backs of the ankles:
  // from the heels drawn up to straight legs. The thighs leave the hips 25° down, steeper than the
  // pad's 21°: their front tapers toward the knees, so it lies along the pad.
  lying_leg_curl: {
    equipment: 'machine',
    motion: 'knee_lever',
    kit: { leg_curl: { lying: true } },
    body: { prone: true, trunk: 10, thigh: 25, head: -20 },
    top: { lever: 94 },
    feet: { stance: 0.1 },
    grip: { pole: [1, -1, 0.2] },
    camera: { target: [0, 0.75, 0.1], halfHeight: 0.85, azimuth: 80, elevation: 14 },
    shadow: { size: [0.9, 1.6], at: [0, 0.2] },
  },

  // Seated, the knees under the pad and the balls of the feet on the platform: the heels rise from
  // stretched below it.
  seated_calf_raise: {
    equipment: 'machine',
    motion: 'calf_raise',
    kit: { calf_raise_machine: {} },
    body: { trunk: -2 },
    top: { lever: 11.69 },
    feet: { stance: 0.1 },
    grip: {},
    camera: { target: [0, 0.55, 0.25], halfHeight: 0.78, azimuth: 72, elevation: 10 },
  },

  // In the sled, the back on the pad and the shoulders under their pads, the feet on the tilted
  // platform: the hips travel down the rails until the knees are well past a right angle. The hands
  // hold the handles beside the head, the elbows out, so the knuckles can lie along them.
  hack_squat: {
    equipment: 'machine',
    motion: 'hack_squat',
    kit: { squat_machine: {} },
    body: { trunk: -51 },
    top: { carriage: 0.5833 },
    feet: { stance: 0.148, toeOut: 15, kneesOut: 0.35 },
    grip: { pole: [1, -0.5, 0], rotate: 0 },
    camera: { target: [0, 0.85, 0.15], halfHeight: 1.0, azimuth: 70, elevation: 8 },
  },

  // Chest on the pad, palms facing each other: from the arms out in front, the elbows driving back
  // to the handles by the ribs.
  chest_supported_machine_row: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { row_machine: { seat: [0.5, 0] } },
    body: { trunk: 5 },
    top: { levers: 75.43 },
    bottom: { levers: 116.29 },
    feet: { stance: 0.16, z: 0.45, toeOut: 10 },
    grip: { rotate: 90, pole: [1.5, -0.2397, -0.287] },
    camera: { target: [0, 0.8, 0.3], halfHeight: 0.92, azimuth: 66, elevation: 10 },
  },

  // Standing on the foot plate, the chest on the pad, palms facing each other: from the arms long
  // with the lever on its catch, the elbows driving back past the trunk.
  chest_supported_t_bar_row: {
    equipment: 'machine',
    motion: 'chest_pad',
    kit: { t_bar_row_machine: { handles: 'neutral' } },
    bottom: { offsets: { clavicle_l: [0, 10, 0], clavicle_r: [0, -10, 0] } },
    feet: { stance: 0.12, toeOut: 8 },
    grip: { pole: [0.25, 0.6, -1] },
    camera: { target: [0, 0.78, 0.3], halfHeight: 1.15, azimuth: 70, elevation: 8 },
    shadow: { size: [1.1, 2.1], at: [0, 0.25] },
  },

  // The same row on the wide grips, overhand, the elbows further out; the shoulder blades let
  // forward to reach them on the catch.
  t_bar_row: {
    like: 'chest_supported_t_bar_row',
    kit: { t_bar_row_machine: { handles: 'wide' } },
    top: { offsets: { clavicle_l: [0, -14, 0], clavicle_r: [0, 14, 0] } },
    grip: { pole: [0.7, 0.6, -1] },
  },

  // The arms stay long and the shoulder blades do the work: they let forward on the catch and draw
  // together a short way up the lever, held about a second there.
  kelso_shrug: {
    like: 't_bar_row',
    bottom: { lever: 4.8, offsets: { clavicle_l: [0, 18, 0], clavicle_r: [0, -18, 0] } },
    grip: { pole: [0.7, 0.1, -1] },
    tempo: { lowering: 1, bottom: 1, lifting: 2, top: 0.5 },
  },

  // Back on the leaning pad, the hands on the vertical grips: from together in front of the chest,
  // the arms opening long and a little bent until the hands are level with the shoulders.
  pec_deck: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { pec_deck: {} },
    body: { trunk: -7 },
    top: { left: 104 },
    bottom: { left: 0 },
    feet: { stance: 0.17, z: 0.38, toeOut: 12 },
    grip: { pole: [1, -0.25, -0.3] },
    camera: { target: [0, 1.05, 0.05], halfHeight: 1.12, azimuth: 40, elevation: 10 },
    shadow: { size: [1.5, 1.6], at: [0, -0.2] },
  },

  // Astride the same seat, the chest on the pad, the hands overhand on the short grips: from the arms
  // in line with the shoulders, forward until they reach ahead of the chest.
  reverse_pec_deck: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { pec_deck: { facing: 'pad', hips: -0.02 } },
    body: { trunk: 7, look: 0 },
    top: { left: 57 },
    bottom: { left: 0 },
    feet: { stance: 0.2, z: 0.2, toeOut: 15, kneesOut: 0.4 },
    grip: { pole: [0.4, 0.05, -1] },
    camera: { target: [0, 1.05, 0.05], halfHeight: 1.12, azimuth: 140, elevation: 10 },
    shadow: { size: [1.5, 1.6], at: [0, 0.2] },
  },

  // The same with one arm, the other hand on the thigh.
  '1_arm_reverse_pec_deck': {
    like: 'reverse_pec_deck',
    kit: { pec_deck: { facing: 'pad', hips: -0.02, one: true } },
    grip: { one: 1, rest: [-0.2, 0.74, 0.22] },
  },

  // Seated under the thigh rollers: the handles from overhead, the arms long, down to the shoulders.
  machine_pulldown: {
    equipment: 'machine',
    motion: 'lever_handles',
    kit: { pulldown_machine: {} },
    body: { trunk: -15 },
    top: { levers: 155.49 },
    bottom: { levers: 193.11 },
    feet: { stance: 0.16, z: 0.32, toeOut: 12 },
    grip: { pole: [0.8, -1, -0.3] },
    camera: { target: [0, 1.1, 0.35], halfHeight: 1.15, azimuth: 62, elevation: 8 },
  },

  // Seated, leaning onto the pad, the chest against its back edge and the upper arms down it: the
  // bar curls from the arms near straight up toward the shoulders.
  machine_preacher_curl: {
    equipment: 'machine',
    motion: 'arms',
    kit: { preacher_curl_machine: { pad: 0.0845 } },
    body: { trunk: 20 },
    top: { upperArm: 65, lever: 77.93 },
    bottom: { upperArm: 65, lever: -10.07 },
    feet: { stance: 0.16, z: 0.4, toeOut: 10 },
    grip: { width: 0.18, rotate: 180, pole: [0.2, -0.7, 0.7] },
    camera: { target: [0, 0.9, 0.3], halfHeight: 0.8, azimuth: 70, elevation: 10 },
  },

  // Standing facing away from a low pulley, the left arm back behind the body: it curls with the
  // upper arm still, palm up; the right hand hangs by the hip.
  bayesian_cable_curl: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_column: { at: [0.3522, -0.8], pulley: 0.25, attachment: 'handle', hand: 1 } },
    top: { shin: 10, thigh: 10, trunk: 20.9948, upperArm: -10.3208, forearm: 107.4867 },
    bottom: { shin: 10, thigh: 10, trunk: 20.0502, upperArm: -34.6598, forearm: -11.2509 },
    feet: { stance: 0.14, toeOut: 10, kneesOut: 0.2, stagger: -0.32 },
    grip: { width: 0.2, rotate: 180, one: 1, rest: [-0.2, 0.96, 0.12], pole: [0.15, -1, -0.5] },
    camera: { target: [0, 1.0, -0.2], halfHeight: 1.1, azimuth: 80, elevation: 8 },
  },

  // Standing facing away from the pulley, leaning forward, the rope over the head: from the arms
  // straight overhead, the hands lower behind the head.
  overhead_cable_triceps_extension: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_column: { at: [0, -0.6497], pulley: 1.0655, attachment: 'rope' } },
    top: { shin: 8, thigh: 14, trunk: 20, upperArm: 137.2876, forearm: 154.5043 },
    bottom: { shin: 8, thigh: 14, trunk: 20, upperArm: 143.5581, forearm: 280.8862 },
    feet: { stance: 0.16, toeOut: 10, kneesOut: 0.2 },
    grip: { width: 0.1328, rotate: 90, pole: [0.25, 0.6, 1] },
    camera: { target: [0, 1.15, -0.2], halfHeight: 1.15, azimuth: 76, elevation: 8 },
  },

  // Standing side-on to a low pulley on the right, the left arm raising the handle out to the side
  // to shoulder height; the right hand holds the column.
  cable_lateral_raise: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_column: { at: [-0.6, 0.05], pulley: 0.15, attachment: 'handle', hand: 1 } },
    top: { shin: 4, thigh: 6, trunk: 6, hand: [0.763, 1.513, 0.334] },
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
    kit: { cable_column: { at: [0.808, -0.45], pulley: 0.8679, twin: true } },
    top: { shin: 6, thigh: 8, trunk: 12, hand: [0.2037, 1.5397, 0.7136], rotate: 38.377 },
    bottom: { shin: 6, thigh: 8, trunk: 12, hand: [0.74, 1.295, -0.012], rotate: 210, wrist: [-7.5153, 0.8764] },
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
  // to overhead, low in the palms as a press holds them.
  cable_shoulder_press: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { bench: { angle: 80 }, cable_column: { at: [0.6691, -0.15], pulley: 0.12, twin: true } },
    body: { hip: [0.547, 0.19], trunk: -10 },
    top: { hand: [0.2882, 1.644, 0.2943] },
    bottom: { hand: [0.32, 1.147, 0.2] },
    feet: { stance: 0.18, z: 0.5, toeOut: 12 },
    grip: { pole: [1, -1, 0.1], sits: 'heel' },
    camera: { target: [0, 0.95, 0.15], halfHeight: 1.0, azimuth: 30, elevation: 10 },
  },

  // The cable crossover's exercises (#197): first poses for the owner to tune. Body-first, the body
  // at the origin and the machine at `at`, the carriages at the holes the ref gives.

  // A step ahead of the towers, feet level and deep in the hinge, the pulleys high: the arms, soft at
  // the elbow and riding with the trunk, sweep from wide to the handles together in front of the hips.
  // `armOut` turns about the chest's own axis, so on a hinge it passes 90 to bring the hands up wide.
  cable_crossover: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_crossover: { position: 12, at: [0, -0.0813] } },
    top: { shin: 10, thigh: 34, trunk: 42.4716, upperArm: 8, forearm: 33, armOut: 0, rotate: 90.8789 },
    bottom: { shin: 10, thigh: 34, trunk: 41.9058, upperArm: -30, forearm: -5, armOut: 135, rotate: 17.5049 },
    feet: { stance: 0.15, toeOut: 10, kneesOut: 0.2 },
    grip: { width: 0.06, rotate: 90, pole: [0, 0, -1] },
    camera: { target: [0, 1.0, 0.1], halfHeight: 1.3, azimuth: 30, elevation: 8 },
  },

  // A step ahead of the towers in a split stance, the pulleys at the shoulders and the cables coming
  // from behind: neutral handles press from beside the chest until the hands nearly meet.
  cable_chest_press: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 10, at: [0, -0.6] } },
    top: { shin: 8, thigh: 14, trunk: 12, hand: [0.0925, 1.36, 0.68], rotate: 30.8936, wrist: [-54.8547, 0] },
    bottom: { shin: 8, thigh: 14, trunk: -7.8149, hand: [0.33, 1.3, 0.12], rotate: 41.25 },
    feet: { stance: 0.14, toeOut: 10, kneesOut: 0.2, stagger: 0.3 },
    grip: { rotate: 90, pole: [1, -0.2, -0.6], sits: 'heel' },
    camera: { target: [0, 1.15, -0.1], halfHeight: 1.3, azimuth: 40, elevation: 8 },
  },

  // One arm from the pulley at the thigh: the handle rises from beside the hip, up and across to eye
  // level in front of the opposite shoulder.
  low_to_high_cable_press: {
    like: 'cable_chest_press',
    kit: { cable_crossover: { position: 5, hands: [1] } },
    top: { hand: [-0.19, 1.62, 0.5] },
    bottom: { hand: [0.33, 1.02, 0.1] },
    grip: { arc: true, one: 1, rest: [-0.25, 0.88, 0.14] },
  },

  // Beside the right tower, the left hand on its cable, which crosses in front of the hips from a
  // pulley at hip height: the arm rises from across the body to level with the shoulder, the right
  // hand resting at the carriage's grip bar (a resting hand doesn't close on it).
  high_cable_lateral_raise: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 6, at: [0.4, 0.1], crossed: true, hands: [1] } },
    top: { shin: 4, thigh: 6, trunk: 4, hand: [0.76, 1.46, 0.1] },
    bottom: { shin: 4, thigh: 6, trunk: 4, hand: [-0.06, 0.92, 0.26] },
    feet: { stance: 0.15, toeOut: 8, kneesOut: 0.2 },
    grip: { arc: true, one: 1, rotate: 30, rest: [-0.574, 1.0, 0.19], pole: [1, -0.25, -0.7] },
    camera: { target: [0, 1.1, 0], halfHeight: 1.3, azimuth: 20, elevation: 8 },
  },

  // The raise from a low pulley, up past the shoulder into the arm of a Y, the right hand resting at
  // the tower's post.
  cable_y_raise: {
    like: 'high_cable_lateral_raise',
    kit: { cable_crossover: { position: 1 } },
    top: { hand: [0.6, 1.9, 0.22] },
    grip: { rest: [-0.59, 1.15, 0.1] },
  },

  // Both arms together, a step ahead of the towers, each hand on the far tower's low cable, so the
  // two cross behind the body: the arms, long and held in shape, swing out from beside the hips to
  // level with the shoulders.
  behind_the_back_cable_lateral_raise: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_crossover: { position: 1, at: [0, -0.55], crossed: true } },
    top: { shin: 4, thigh: 6, trunk: 10.293, upperArm: -32, forearm: -12, armOut: 84, rotate: 23.606 },
    bottom: { shin: 4, thigh: 6, trunk: 6.7603, upperArm: -32, forearm: -12, armOut: 14, rotate: 109.6875 },
    feet: { stance: 0.15, toeOut: 8, kneesOut: 0.2 },
    grip: { width: 0.19, rotate: 30, pole: [0, 0, -1] },
    camera: { target: [0, 1.1, -0.1], halfHeight: 1.3, azimuth: 20, elevation: 8 },
  },

  // Leaning a little forward in a split stance, the left hand on a cable from a low pulley behind:
  // the arm stays long and the shoulder shrugs up and in.
  cable_shrug_in: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 2, at: [-0.6, -0.75], hands: [1] } },
    top: { shin: 6, thigh: 18, trunk: 21.7969, hand: [0.315, 0.93, -0.2311], offsets: { clavicle_l: [0, 0, 18] } },
    bottom: { shin: 6, thigh: 18, trunk: 22.9443, hand: [0.32, 0.8373, -0.2472], offsets: { clavicle_l: [0, 0, -10] } },
    feet: { stance: 0.14, toeOut: 10, kneesOut: 0.2, stagger: -0.3 },
    grip: { one: 1, rest: [-0.25, 0.86, 0.14], pole: [1, -0.3, -0.7] },
    camera: { target: [0, 1.0, -0.3], halfHeight: 1.25, azimuth: 55, elevation: 8 },
  },

  // Feet wide and knees bent over the toes, the right palm lying on the thigh, a step behind the
  // towers' line: the left hand presses from beside the chest across the body past the middle, on a cable
  // from the left tower's pulley at the chest, the trunk and head turning with it. The owner's tune.
  '1_arm_cable_press_around': {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 9, at: [-0.0587, 0.2719], hands: [1] } },
    top: {
      shin: 20, thigh: 48, trunk: 6, hand: [-0.2583, 1.3742, 0.3897],
      offsets: { spine_01: [0, -8, 0], spine_02: [0, -8, 0], spine_03: [0, -8, 0], Head: [0, -12, 0], hand_r: [90, 0, 0] },
      rotate: 102.6196,
    },
    bottom: {
      shin: 20, thigh: 48, trunk: 6, hand: [0.2468, 1.3, 0.14],
      offsets: { spine_01: [0, 4, 0], spine_02: [0, 4, 0], spine_03: [0, 4, 0], Head: [0, -60.8313, 0], hand_r: [90, 0, 0] },
      rotate: 91.5454,
    },
    feet: { stance: 0.36, kneesOut: 0.3676, stagger: 0.14, toeOut: 20.6543 },
    grip: { arc: true, rotate: 90, one: 1, rest: [-0.14, 0.72, -0.015], pole: [1, -0.3, -0.5], sits: 'heel' },
    camera: { target: [0, 1.15, 0], halfHeight: 1.3, azimuth: 12, elevation: 8 },
  },

  // The right tower's pulley at the hip, to the side and behind, its cable across the body to the left hand:
  // the upper arm stays up and out to the side, the elbow in place, and the forearm lowers from
  // nearly straight overhead, the arm of a Y, to behind the head.
  '1_arm_katana_cable_triceps_extension': {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_crossover: { position: 6, at: [0.4242, -0.7972], crossed: true, hands: [1] } },
    top: { shin: 4, thigh: 8, trunk: 8, upperArm: 165, forearm: 175, armOut: 20, rotate: -24.6899, wrist: [0, 7.7161] },
    bottom: { shin: 4, thigh: 8, trunk: 8, upperArm: 165, forearm: 290, armOut: 20, rotate: 6.0718 },
    feet: { stance: 0.16, toeOut: 10, kneesOut: 0.2, stagger: 0.3 },
    grip: { width: 0.17, rotate: 90, one: 1, rest: [-0.25, 0.88, 0.12], pole: [0.6925, 1, 0.3] },
    camera: { target: [0, 1.2, -0.2], halfHeight: 1.3, azimuth: 60, elevation: 8 },
  },

  // The crossover's second batch (#197): the lifts that face a tower, kneel, sit or lift a leg, and
  // those on a rope, a bar or a cuff. First poses for the owner to tune.

  // Facing the left tower, leaning forward with both hands flat on its post, a cuff on the left ankle
  // and the pulley at its lowest: the leg kicks back and a little out from under the hips.
  '1_leg_cable_glute_kickback': {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 1, faces: true, attachment: 'cuff', ankle: 1, at: [-1.105, 0.98] } },
    top: { shin: 6, thigh: 18, trunk: 32, hand: [0.07, 1.3, 0.74], leg: [58, 8, 25] },
    bottom: { shin: 6, thigh: 18, trunk: 32, hand: [0.07, 1.3, 0.74], leg: [8, 2, 60] },
    feet: { stance: 0.09, toeOut: 6, lift: 1 },
    grip: { sits: 'open', pole: [1, -0.4, -0.3] },
    camera: { target: [0, 0.85, 0.1], halfHeight: 1.15, azimuth: 70, elevation: 8 },
  },

  // On the knees facing the left tower, the rope from a high pulley coming over the crown, its ends
  // back past the fists: the hands stay at the back and top of the head, the hips hold still and the
  // back rounds, the elbows coming down toward the thighs. The owner's, in the studio.
  cable_crunch: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 15, faces: true, attachment: 'rope', reversed: true, at: [-1.105, 1.25] } },
    body: { kneel: true },
    top: {
      thigh: 6, trunk: 58, hand: [0.125, 0.535, 0.7372],
      offsets: { spine_01: [12, 0, 0], spine_02: [12, 0, 0], spine_03: [12, 0, 0] },
      rotate: -11.2573,
    },
    bottom: { thigh: 2, trunk: 16, hand: [0.1, 1.33, 0.27] },
    feet: { stance: 0.13 },
    grip: { rotate: 90, via: [0.115, 1.0, 0.67], pole: [1, 0.2, 1] },
    camera: { target: [0, 0.65, 0.15], halfHeight: 1.05, azimuth: 70, elevation: 8 },
  },

  // A long step back from the left tower, the rope from a high pulley: the hands pull apart to either
  // side of the face, the elbows high and wide.
  cable_face_pull: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 15, faces: true, attachment: 'rope', at: [-1.105, 1.35] } },
    top: { shin: 6, thigh: 10, trunk: -6, hand: [0.2, 1.62, 0.18] },
    bottom: { shin: 6, thigh: 10, trunk: -6, hand: [0.07, 1.6, 0.5] },
    feet: { stance: 0.14, toeOut: 8, kneesOut: 0.2, stagger: 0.28 },
    grip: { rotate: 90, pole: [1, 0.35, -0.5] },
    camera: { target: [0, 1.25, 0.3], halfHeight: 1.25, azimuth: 55, elevation: 8 },
  },

  // Facing the right tower with the right hand on its post and the left on the hip, leaning in a
  // little, a cuff on the left ankle: the cable crosses in front of the standing leg and the left leg
  // lifts out to its side.
  cable_hip_abduction: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 1, faces: true, attachment: 'cuff', tower: -1, ankle: 1, at: [0.82, 0.75] } },
    top: { shin: 3, thigh: 6, trunk: 26, hand: [0.25, 1.0, 0.12], leg: [-8, 38, 30] },
    bottom: { shin: 3, thigh: 6, trunk: 26, hand: [0.25, 1.0, 0.12], leg: [-16, 8, 58] },
    feet: { stance: 0.09, toeOut: 6, lift: 1 },
    grip: { sits: 'open', one: 1, rest: [-0.27, 1.12, 0.52], pole: [1, -0.2, -0.8] },
    camera: { target: [0, 0.85, 0.1], halfHeight: 1.15, azimuth: 15, elevation: 8 },
  },

  // Back to the left tower, astride the rope from its lowest pulley: the hips hinge back and the
  // hands pass between the thighs, then the hips drive through, the arms long all the way.
  cable_pull_through: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_crossover: { position: 1, attachment: 'rope', reversed: true, at: [-1.105, -1.05] } },
    body: { look: 0.02 },
    top: { shin: 4, thigh: 8, trunk: 6, upperArm: 6, forearm: 8 },
    bottom: { shin: 12, thigh: 44, trunk: 58, upperArm: -30, forearm: -26 },
    feet: { stance: 0.25, toeOut: 16, kneesOut: 0.3 },
    grip: { width: 0.08, rotate: 90, pole: [0.4, 0, -1] },
    camera: { target: [0, 0.8, -0.2], halfHeight: 1.15, azimuth: 65, elevation: 8 },
  },

  // Turned 45° to the left tower, the right hand gripping its post with the arm long, leaning back on it,
  // the left hand on a single-ended rope from the chest-high pulley, the upper arm held behind the
  // trunk and out: the forearm extends from the chest down and back. The owner's turn, lean, rope
  // and hole.
  cable_triceps_kickback: {
    equipment: 'cable',
    motion: 'arms',
    kit: { cable_crossover: { position: 9, faces: true, turn: -45, attachment: 'rope', hands: [1], at: [-0.8105, 0.6427] } },
    top: { shin: 5, thigh: 6, trunk: -17.5977, upperArm: -28, forearm: -26, armOut: 32 },
    bottom: { shin: 5, thigh: 6, trunk: -16.875, upperArm: -28, forearm: 70, armOut: 32 },
    feet: { stance: 0.15, toeOut: 16, kneesOut: 0.2, stagger: 0.12 },
    grip: { width: 0.2, rotate: 90, one: 1, rest: [-0.2801, 1.55, 0.457], restOn: 0.05, pole: [0, 0, -1], restPole: [1, -1, 0.3] },
    camera: { target: [0, 1.05, 0.05], halfHeight: 1.25, azimuth: 65, elevation: 8 },
  },

  // Side-on to the left tower, the rope from a high pulley in both hands: the arms chop down across
  // the body toward the outside of the right knee, the knees bending and the trunk and head turning
  // with them.
  cable_woodchopper: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 15, attachment: 'rope', at: [-0.05, -0.1] } },
    top: {
      shin: 18, thigh: 46, trunk: 34, hand: [-0.24, 0.76, 0.44],
      offsets: { spine_01: [0, -10, 0], spine_02: [0, -10, 0], spine_03: [0, -10, 0], Head: [0, -10, 0] },
    },
    bottom: {
      shin: 6, thigh: 10, trunk: 4, hand: [0.42, 1.58, 0.4],
      offsets: { spine_01: [0, 12, 0], spine_02: [0, 12, 0], spine_03: [0, 12, 0], Head: [0, 10, 0] },
    },
    feet: { stance: 0.24, toeOut: 14, kneesOut: 0.25 },
    grip: { joined: 0.05, rotate: 90, pole: [1, -0.5, -0.4] },
    camera: { target: [0, 1.15, 0.1], halfHeight: 1.3, azimuth: 10, elevation: 8 },
  },

  // Sat low on the heels facing the left tower, the forearms along the thighs, palms up on a straight
  // bar from the lowest pulley: only the wrists move, the bar curling up from the fingers.
  cable_wrist_curl: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { cable_crossover: { position: 1, faces: true, attachment: 'bar', at: [-1.105, 0.78] } },
    top: { shin: 34, thigh: 88, trunk: 34, hand: [0.19, 0.683, 0.423], wrist: [38, 0], rotate: 184.5 },
    bottom: { shin: 34, thigh: 88, trunk: 34, hand: [0.19, 0.58, 0.475], wrist: [-38, 0], rotate: 181 },
    feet: { stance: 0.2, toeOut: 16, kneesOut: 0.45 },
    grip: { rotate: 180, thumb: 'thumbless', pole: [0.1, -1, -0.5] },
    camera: { target: [0, 0.5, 0.2], halfHeight: 0.9, azimuth: 65, elevation: 8 },
  },

  // The curl turned over, the palms down: the backs of the hands rise toward the forearms.
  cable_wrist_extension: {
    like: 'cable_wrist_curl',
    top: { hand: [0.19, 0.598, 0.458], wrist: [-38, 0], rotate: 0 },
    bottom: { hand: [0.19, 0.521, 0.455], wrist: [32, 0], rotate: 0 },
    grip: { rotate: 0, thumb: 'wrapped' },
  },

  // Back on an upright bench between the towers, the pulleys at the seated shoulder: the handles
  // sweep from wide behind the line of the chest to together in front of it.
  seated_cable_flye: {
    equipment: 'cable',
    motion: 'free_arms',
    kit: { bench: { angle: 80 }, cable_crossover: { position: 7, at: [0, -0.75] } },
    body: { hip: [0.547, 0.19], trunk: -10 },
    top: { hand: [0.1, 1.12, 0.7], rotate: 60, wrist: [0, 2.8308] },
    bottom: { hand: [0.72, 1.1, -0.04], rotate: -30, wrist: [0, 13.9563] },
    feet: { stance: 0.18, z: 0.5, toeOut: 12 },
    grip: { arc: true, rotate: 90, pole: [1, -0.8, -0.2] },
    camera: { target: [0, 0.95, 0.2], halfHeight: 1.05, azimuth: 28, elevation: 10 },
  },

  // The library's next barbell exercises (#127, the barbell batch): first poses for the owner to tune.

  // The three squats are the first batch's barbell_squat, which the owner tuned, changed only where
  // the variant differs; they keep its traced legs and trunk, turned about the ankles until the bar
  // is over the middle of the feet, so the bar's place sets the lean.

  // The bar a little higher on the traps; otherwise the squat as tuned.
  back_squat_high_bar: {
    like: 'barbell_squat',
    bar: { up: 0.04, back: 0.2 },
    grip: { pole: [0.35, -1, -0.8], swing: 55 },
  },

  // The bar low across the rear delts, a few centimetres down the back and further behind the
  // trunk's line, so the trunk leans further to keep it over the feet; the hands wider to reach it,
  // the bar in the heels of the palms and the thumbs over it beside the fingers (docs/research/grip.md).
  back_squat_low_bar: {
    like: 'barbell_squat',
    bar: { up: -0.03, back: 0.2021 },
    grip: { width: 0.4114, pole: [0.35, -1, -0.8], sits: 'heel', thumb: 'thumbless' },
    top: { rotate: 5.6724 },
    bottom: { rotate: -8.2238 },
  },

  // The bar racked high on the front of the shoulders, the hands wrapped round it just outside them,
  // the wrists bent back, each elbow down under its hand, as the owner's reference has it. The trunk stands upright, which the traced back squat's doesn't, so it has its own shape: the
  // trace's legs, top and bottom, with the trunk near upright; the body turns about the ankles to
  // keep the bar over the feet, as the back squat's does.
  front_squat: {
    like: 'barbell_squat',
    trace: null,
    top: { shin: 3.388, thigh: -11.6002, trunk: 6.2702, rotate: 0, wrist: [-99.4474, -91.149] },
    bottom: { shin: 31.2548, thigh: 103.7533, trunk: 25.517, rotate: 0, wrist: [-80.6189, -71.8976] },
    bar: { up: 0.0652, back: -0.0296 },
    grip: { width: 0.27, pole: [0.2141, -0.9277, 0.3059], sits: 'base' },
  },

  // Standing, the elbows by the sides: the bar curls from the thighs up to the shoulders, palms up.
  barbell_curl: {
    equipment: 'barbell',
    motion: 'arms',
    kit: { barbell: {} },
    top: { shin: 3, thigh: 3, trunk: 2, upperArm: 10, forearm: 140 },
    bottom: { shin: 3, thigh: 3, trunk: 2, upperArm: 6, forearm: 14 },
    feet: { stance: 0.13, toeOut: 8, kneesOut: 0.15 },
    // The thumbs swing a little further out than mid-palm's default, clear of the bar as it rises.
    grip: { width: 0.2, rotate: 180, swing: 40 },
    camera: { target: [0, 1.0, 0.05], halfHeight: 1.1, azimuth: 62, elevation: 8 },
  },

  // Lying on the floor, the feet flat and the bar across the hips: the hips rise until the body is
  // straight from the shoulders to the knees. The bar is placed at the top and the bottom.
  barbell_glute_bridge: {
    equipment: 'barbell',
    motion: 'bridge',
    kit: { barbell: {} },
    body: { shoulders: [0.12, -0.6085], head: 40 },
    // The hands rest the bar on the hips, the wrists straight on the forearms (set, so the elbows
    // keep out to the sides on the floor).
    top: { hip: [0.3844, 0.08], bar: [0.471, -0.0132], wrist: [0, 0] },
    bottom: { hip: [0.13, 0.06], bar: [0.2241, -0.0434], wrist: [0, 0] },
    feet: { stance: 0.15, z: 0.42, toeOut: 10 },
    grip: { width: 0.3, pole: [1, 0, 0] },
    camera: { target: [0, 0.3, 0.05], halfHeight: 0.75, azimuth: 70, elevation: 14 },
    shadow: { size: [0.9, 1.6], at: [0, 0] },
  },

  // The shoulder blades across a bench, the feet flat and the bar across the hips: the hips rise
  // until the trunk is level. The bar is placed at the top and the bottom, and the back may arch
  // at either (`arch`, degrees for each link of the spine).
  barbell_hip_thrust: {
    equipment: 'barbell',
    motion: 'bridge',
    kit: { barbell: {}, bench: { z: 0, across: -0.62, height: 0.3923 } },
    body: { shoulders: [0.5127, -0.4949], head: 15.6222 },
    top: { hip: [0.52, 0.08], bar: [0.625, 0.1], arch: [0, 0, 0], wrist: [0, 0] },
    bottom: { hip: [0.22, -0.02], bar: [0.3417, -0.0491], arch: [18.3305, -0.1695, -12.3207], wrist: [0, 0] },
    feet: { stance: 0.16, z: 0.42, toeOut: 10 },
    grip: { width: 0.3453, pole: [1, 0.1, 0.3], rotate: 8.4497 },
    camera: { target: [0, 0.4, 0.0], halfHeight: 0.8, azimuth: 70, elevation: 12 },
    shadow: { size: [1.2, 1.6], at: [0, 0] },
  },

  // Hinged forward with the back flat: the bar lowers straight down from the belly to hanging under
  // the shoulders, on a line at `bar.z`; `bar` is its height at the top and the bottom.
  barbell_row: {
    equipment: 'barbell',
    motion: 'row',
    kit: { barbell: {} },
    top: { shin: 8, thigh: 32, trunk: 60, bar: 0.8687, rotate: 0, wrist: [0, 20.2981] },
    bottom: { shin: 8, thigh: 32, trunk: 60, bar: 0.517, wrist: [0, 20.6747] },
    bar: { z: 0.1964 },
    feet: { stance: 0.15, toeOut: 10, kneesOut: 0.25 },
    grip: { width: 0.3436, pole: [0.55, 0.2, -0.8] },
    camera: { target: [0, 0.8, 0.1], halfHeight: 0.95, azimuth: 66, elevation: 8 },
  },

  // Standing on a low platform, the trunk level: the bar lowers straight down from the lower chest
  // to hanging near the floor, where each rep starts.
  deficit_pendlay_row: {
    equipment: 'barbell',
    motion: 'row',
    kit: { barbell: {}, platform: {} },
    top: { shin: 14, thigh: 53, trunk: 80, bar: 0.6654, wrist: [0, 34.8298] },
    bottom: { shin: 14, thigh: 53, trunk: 80, bar: 0.2904, wrist: [0, 21.8233] },
    bar: { z: 0.2039 },
    feet: { stance: 0.16, y: 0.05, toeOut: 10, kneesOut: 0.3 },
    grip: { width: 0.3665, rotate: -6.1337 },
    camera: { target: [0, 0.6, 0.15], halfHeight: 0.85, azimuth: 66, elevation: 8 },
  },

  // Standing tall, the arms straight: the shoulders shrug up toward the ears and down again.
  barbell_shrug: {
    equipment: 'barbell',
    motion: 'hinge',
    kit: { barbell: {} },
    top: { shin: 1, thigh: 0, trunk: 2, shrug: 22 },
    bottom: { shin: 1, thigh: 0, trunk: 2, shrug: -4 },
    feet: { stance: 0.13, toeOut: 6, kneesOut: 0 },
    bar: { z: 0.14 },
    grip: { width: 0.24 },
    camera: { target: [0, 1.0, 0.05], halfHeight: 1.1, azimuth: 62, elevation: 8 },
  },

  // Standing, a close grip: the bar rises along the body to the chest, the elbows leading high.
  barbell_upright_row: {
    equipment: 'barbell',
    motion: 'free_arms',
    kit: { barbell: {} },
    top: { shin: 2, thigh: 2, trunk: 3, hand: [0.11, 1.3, 0.21], rotate: 0, wrist: [0, -44.1518] },
    bottom: { shin: 2, thigh: 2, trunk: 3, hand: [0.11, 0.83, 0.17], rotate: 0, wrist: [0, -6.6144] },
    feet: { stance: 0.13, toeOut: 8, kneesOut: 0.15 },
    grip: { pole: [1.4295, 0.7, -0.2] },
    camera: { target: [0, 1.0, 0.05], halfHeight: 1.1, azimuth: 40, elevation: 8 },
  },

  // From the floor, the bar over the middle of the feet and the back flat, to standing locked out.
  // The bar runs straight up and down at `bar.z`; `bar` is its height at the top and the bottom
  // (its plates on the floor), and the trunk leans as far as it takes the straight arms to hold it.
  deadlift: {
    equipment: 'barbell',
    motion: 'hinge',
    kit: { barbell: {} },
    top: { shin: 2, thigh: 0, bar: 0.808, shrug: -3.7188, wrist: [0, 13.0912] },
    bottom: { shin: 18, thigh: 75, bar: 0.225, shrug: -1.3149, wrist: [0, 16.6123] },
    feet: { stance: 0.1518, toeOut: 8, kneesOut: 0.117 },
    bar: { z: 0.111 },
    grip: { width: 0.25 },
    camera: { target: [0, 0.8, 0.05], halfHeight: 1.0, azimuth: 66, elevation: 8 },
  },

  // A deadlift standing on a low platform, the bar starting further below the feet.
  deficit_deadlift: {
    like: 'deadlift',
    kit: { platform: {} },
    top: { bar: 0.858, shrug: -3, wrist: [0, 20.6606] },
    bottom: { shin: 27, thigh: 70 },
    // Its own knees out: the deadlift's narrower ones (owner, #127) flex its deeper hips too far.
    feet: { y: 0.05, kneesOut: 0.3109 },
    bar: { z: 0.13 },
  },

  // Lying on the floor, the knees bent: the bar presses from the elbows resting on the floor to
  // locked out over the shoulders.
  floor_press: {
    equipment: 'barbell',
    motion: 'bench_press',
    kit: { barbell: {} },
    body: { hip: [0.13, 0.28], trunk: -89, head: 0 },
    feet: { stance: 0.2, z: 0.62, toeOut: 15 },
    top: { bar: [0.6867, -0.2], wrist: [20.1518, 38.967], rotate: -1.9755 },
    bottom: { bar: [0.385, -0.1], rotate: -0.0269, wrist: [-22.4578, 27.202] },
    grip: { width: 0.4127, elbows: 0.4588 },
    camera: { target: [0, 0.3, 0.05], halfHeight: 0.8, azimuth: 58, elevation: 28 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  // Hanging from the rack's bar, a little wider than the shoulders: from straight arms, the
  // shoulders let up under the bar, to the chin over it, the chest leaning back to it.
  pullup: {
    equipment: 'bodyweight',
    motion: 'hang',
    kit: { power_rack: { handles: 'standard' } },
    top: { rise: 0.485, back: 0.13, trunk: -8, thigh: 4, shin: 8, shrug: -12 },
    bottom: { rise: 0.006, back: 0, trunk: 0, thigh: 2, shin: 4, shrug: 13 },
    feet: { stance: 0.09, point: 30 },
    grip: { pole: [0.8, -1, 0.3] },
    camera: { target: [0, 1.4, 0.1], halfHeight: 1.45, azimuth: 150, elevation: 8 },
  },

  // On the cross tube's ends, outside the outer prongs: the hands hang the body higher, so the
  // chin reaches the bar on less of a rise.
  pullup_wide_grip: { like: 'pullup', kit: { power_rack: { handles: 'wide' } }, top: { rise: 0.43 }, grip: { swing: 45 } },
  // On the cross tube hard by the inner prongs, leaning back about 15° (the library's note): the
  // V's arms turn an overhand hand palm out.
  close_grip_pullup: { like: 'pullup', kit: { power_rack: { handles: 'narrow' } }, top: { trunk: -15 } },
  // On the inner prongs, the palms facing.
  neutral_grip_pullup: { like: 'pullup', kit: { power_rack: { handles: 'neutral' } }, top: { rise: 0.495 } },
  // The pull-up's grips turned underhand, the elbows coming down in front.
  chinup: { like: 'pullup', grip: { rotate: 180, pole: [0.3, -1, 0.6] } },
  chinup_wide_grip: { like: 'chinup', kit: { power_rack: { handles: 'wide' } }, top: { rise: 0.43 } },
  close_grip_chinup: { like: 'chinup', kit: { power_rack: { handles: 'close' } }, top: { rise: 0.495 } },

  // A clip a grip, as the 2-grip pulldown: set 1 overhand about 1.5 shoulder widths apart on the
  // cross tube, set 2 underhand on the V, as near shoulder width as the bar has.
  '2_grip_pullup': {
    like: 'pullup',
    grips: {
      wide: {},
      narrow: { kit: { power_rack: { handles: 'close' } }, grip: { rotate: 180, pole: [0.3, -1, 0.6] }, top: { rise: 0.495 } },
    },
  },

  // Held at the bottom of the pull-up: nothing moves but the hands closing.
  dead_hang: {
    like: 'pullup',
    top: { rise: 0.006, back: 0, trunk: 0, thigh: 2, shin: 4, shrug: 13 },
  },

  // The arms straight and the shoulders under the bar: the legs come up straight past level, the
  // hips swinging forward of the shoulders.
  hanging_leg_raise: {
    like: 'pullup',
    top: { rise: 0.006, back: 0, trunk: -10, thigh: 108, shin: -108, shrug: 13 },
    camera: { target: [0, 1.5, 0.3], halfHeight: 1.35, azimuth: 110, elevation: 8 },
  },

  // Between the dip handles, the back to the upright and facing out along them: from locked arms
  // down to the elbows at about 90°, the forearms near upright and the trunk leaning forward, the
  // knees bent and the feet behind.
  dip: {
    equipment: 'bodyweight',
    motion: 'support',
    kit: { power_rack: { handles: 'dip' } },
    top: { sink: -0.004, over: 0, trunk: 8, thigh: 8, shin: 45, shrug: -6 },
    bottom: { sink: 0.285, over: 0.26, trunk: 30, thigh: 12, shin: 60, shrug: 6 },
    feet: { stance: 0.09, point: 30 },
    grip: { pole: [0.25, 0, -1], swing: 80 },
    camera: { target: [0, 1.3, 0], halfHeight: 1.2, azimuth: -60, elevation: 8 },
  },
  // Near the crosshead, the shoulders ahead of the hands so the hips clear it, the legs hanging
  // almost straight and the upper arms behind the trunk.
  close_grip_dip: {
    like: 'dip',
    kit: { power_rack: { handles: 'dipClose' } },
    top: { sink: 0.005, over: 0.1, trunk: 4, thigh: 10, shin: 14 },
    bottom: { over: 0.24, trunk: 22, thigh: 16, shin: 28 },
  },

  // The plain lift with a plate on a dip belt's chain.
  weighted_pullup: { like: 'pullup', equipment: 'weighted_bodyweight', kit: { dip_belt: {} } },
  weighted_chinup: { like: 'chinup', equipment: 'weighted_bodyweight', kit: { dip_belt: {} } },
  '2_grip_pullup_weighted': { like: '2_grip_pullup', equipment: 'weighted_bodyweight', kit: { dip_belt: {} } },
  weighted_dip: { like: 'dip', equipment: 'weighted_bodyweight', kit: { dip_belt: {} } },

  // The library's first dumbbell exercises (#127, the dumbbell batch): first poses for the owner to tune.

  // Standing, the elbows by the sides, both arms together: the palms face the thighs at the bottom
  // and turn up as the dumbbells curl to the shoulders.
  db_bicep_curl: {
    equipment: 'dumbbell',
    motion: 'arms',
    kit: { dumbbells: { steady: true } },
    top: { shin: 3, thigh: 3, trunk: 2, upperArm: 7.71, forearm: 117.7825, rotate: 174.2651, wrist: [0, 0.7141] },
    bottom: { shin: 3, thigh: 3, trunk: 2, upperArm: 4, forearm: 8, rotate: 92.7539, wrist: [0, 8.1226] },
    feet: { stance: 0.157, toeOut: 8, kneesOut: 0.15 },
    grip: { width: 0.2986, pole: [0.1773, -1, -0.1011], sits: 'mid' },
    camera: { target: [0, 1.0, 0.05], halfHeight: 1.1, azimuth: 62, elevation: 8 },
  },

  // The same curl with the palms facing each other all the way.
  db_hammer_curl: {
    like: 'db_bicep_curl',
    top: { rotate: 90, trunk: -7.9492 },
    bottom: { trunk: -3.9966 },
  },

  // Standing tall, the dumbbells by the sides, palms in: the shoulders shrug straight up toward the ears.
  db_shrug: {
    equipment: 'dumbbell',
    motion: 'hinge',
    kit: { dumbbells: { steady: true } },
    top: { shin: 1, thigh: 0, trunk: 2, shrug: 22 },
    bottom: { shin: 1, thigh: 0, trunk: 2, shrug: -4 },
    feet: { stance: 0.13, toeOut: 6, kneesOut: 0 },
    bar: { z: 0.02 },
    grip: { width: 0.29, rotate: 90 },
    // Paused at the top (the library's note).
    tempo: { lowering: 2, bottom: 0.3, lifting: 1, top: 1 },
    camera: { target: [0, 1.0, 0.05], halfHeight: 1.1, azimuth: 62, elevation: 8 },
  },

  // The barbell RDL's hinge, locked out at the top, the dumbbells close in front of the legs and
  // running down them to mid-shin as the hips go back.
  db_romanian_deadlift: {
    equipment: 'dumbbell',
    motion: 'hinge',
    kit: { dumbbells: { steady: true } },
    top: { shin: 1, thigh: 0, trunk: -3.6659, wrist: [-1.8329, 16.4124], rotate: 50.6145, shrug: 1.8364 },
    bottom: { shin: 8, thigh: 42, trunk: 73.7411, rotate: 49.0514, shrug: -1.0467 },
    feet: { stance: 0.12, toeOut: 6, kneesOut: 0 },
    bar: { z: 0.1077 },
    grip: { width: 0.2156 },
    camera: { target: [0, 0.82, 0.05], halfHeight: 1.0, azimuth: 66, elevation: 8 },
  },

  // Standing, the dumbbells by the sides, palms in: they go out to the sides on an arc about the
  // shoulders, the elbows a little soft, the palms down by the top.
  db_lateral_raise: {
    equipment: 'dumbbell',
    motion: 'free_arms',
    kit: { dumbbells: { steady: true } },
    top: { shin: 4, thigh: 6, trunk: -2.5879, hand: [0.7603, 1.4651, 0.2647], wrist: [-3.8196, 16.1682], rotate: 1.7432 },
    bottom: { shin: 4, thigh: 6, trunk: -2.162, hand: [0.2307, 0.8066, 0.1124], wrist: [-3.5596, 0] },
    feet: { stance: 0.15, toeOut: 8, kneesOut: 0.2 },
    grip: { arc: true, across: [-0.0021, -0.0007, 1.0006], pole: [0.6087, 0.1394, 0.1665] },
    camera: { target: [0, 1.0, 0], halfHeight: 1.1, azimuth: 20, elevation: 8 },
  },

  // A split stance held through the rep, the dumbbells by the sides: the hips drop straight down
  // between the feet until the back knee is just off the floor, the front leg doing the work.
  db_lunge: {
    equipment: 'dumbbell',
    motion: 'lunge',
    kit: { dumbbells: { steady: true } },
    top: { hip: [0.83, 0.06], trunk: 6, heel: 25 },
    bottom: { hip: [0.53, 0.06], trunk: 10, heel: 50 },
    feet: { stance: 0.1, front: 0.36, back: -0.3, toeOut: 6 },
    grip: { width: 0.29, rotate: 90 },
    camera: { target: [0, 0.85, 0], halfHeight: 1.05, azimuth: 78, elevation: 8 },
  },

  // The right knee and hand on the flat bench, the left foot on the floor beside it, the trunk near
  // level: the left arm rows the dumbbell from hanging under the shoulder, palm in, the elbow driving
  // back past the trunk.
  single_arm_db_row: {
    equipment: 'dumbbell',
    motion: 'bench_row',
    kit: { dumbbells: { hand: 1, steady: true }, bench: { at: [-0.2166, 0.2573] } },
    body: { hip: [0.9, -0.35], trunk: 78 },
    // The right hand lies flat on the pad, the fingers forward.
    top: { hand: [0.2518, 0.7417, -0.0807], offsets: { hand_r: [9.2944, 7.218, -70] } },
    bottom: { hand: [0.1977, 0.376, 0.2864], offsets: { hand_r: [1.1755, 0, -70] } },
    feet: { stance: 0.05, knee: [0.515, -0.33], point: 50, out: 0.28, z: -0.37, toeOut: 8 },
    grip: { one: 1, rest: [-0.2444, 0.485, 0.15], pole: [0.15, 0.3, -0.2921] },
    camera: { target: [0, 0.7, -0.18], halfHeight: 0.98, azimuth: 66, elevation: 10 },
    shadow: { size: [1.1, 2.0], at: [0, -0.23] },
  },

  // On the back along the flat bench, as the barbell press lies, the slight arch and the feet back:
  // the dumbbells press up and in from beside the chest to over the shoulders, the arms straight.
  db_bench_press: {
    equipment: 'dumbbell',
    motion: 'free_arms',
    kit: { dumbbells: { steady: true }, bench: {} },
    body: { hip: [0.567, 0.28], trunk: -89, look: -0.27, arch: [16, -16.5, -8] },
    feet: { stance: 0.325, z: 0.445, toeOut: 25 },
    top: { hand: [0.3034, 1.1773, -0.2032], rotate: 4.1382, wrist: [-21.7749, -12.3889] },
    bottom: { hand: [0.3335, 0.6795, -0.1601], rotate: 7.7197, wrist: [-20.6689, 5.2405] },
    grip: { sits: 'heel', pole: [-0.0871, -1, -0.0125], level: 0.7 },
    // A second's pause at the bottom, the chest under tension (the library's note).
    tempo: { lowering: 2, bottom: 1, lifting: 1, top: 0.6 },
    camera: { target: [-0.0307, 0.55, 0.0117], halfHeight: 0.98, azimuth: -12.8809, elevation: 6.7559 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },

  // Lying the same way, palms in: the arms open wide on an arc about the shoulders, the elbows
  // holding a soft bend, until the chest stretches.
  db_flye: {
    like: 'db_bench_press',
    top: { hand: [0.1554, 1.1541, -0.2513], rotate: 99.8584, wrist: [17.6294, 9.4958] },
    bottom: { hand: [0.68, 0.737, -0.17], rotate: 90 },
    grip: { arc: true, sits: 'base', pole: [0.5, -1, 0.3], level: 0 },
    tempo: { bottom: 0.3 },
    feet: { stance: 0.2472, z: 0.4924 },
  },

  // The adjustable bench, upright by default: the dumbbells press from beside the ears to overhead,
  // all the way down each rep, the trunk upright against the pad. Its own entry, not `like:` the
  // incline press, whose other settings per angle would come back with the first one saved here.
  seated_db_shoulder_press: {
    equipment: 'dumbbell',
    motion: 'dumbbell_press',
    kit: { dumbbells: { steady: true }, bench: { hinge: -0.02 } },
    bench: {
      typical: 75,
      // The arms lock out straight up at every angle, as the incline press's do: the pair is
      // [out, up the trunk], so a different one at each.
      perAngle: {
        'top.over.0': { 0: 0.646, 15: 0.624, 30: 0.56, 45: 0.457, 60: 0.323, 75: 0.167, 85: 0.056 },
        'top.over.1': { 0: 0.1023, 15: 0.167, 30: 0.323, 45: 0.457, 60: 0.56, 75: 0.624, 85: 0.643 },
      },
      cameras: {
        0: { target: [0, 0.78, 0.05], halfHeight: 0.98, azimuth: 58, elevation: 22 },
        75: { target: [0, 1.0, 0.0], halfHeight: 1.22, azimuth: 66, elevation: 10 },
      },
    },
    body: { sit: 0.145, seat: 0.0741, lean: 8, head: -12.9053 },
    feet: { stance: 0.2859, z: 0.4271, toeOut: 20 },
    top: { press: 0, over: [0.167, 0.624], rotate: 11.272, tilt: 8.6792, wrist: [-57.7759, -18.2336] },
    bottom: { press: 1, chest: [0.1869, -0.0676], rotate: 3.4497, tilt: 12.395, wrist: [-61.5991, -19.2892] },
    grip: { width: 0.3227, lockout: 0.2859, elbows: 0.3622 },
    shadow: { size: [0.9, 1.8], at: [0, 0.1] },
  },
};
