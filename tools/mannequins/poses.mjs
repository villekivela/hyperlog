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
    // The thumbs swing further out of the palms than mid-palm's default to wrap the bar behind the
    // neck (`grip.swing`).
    grip: { width: 0.4, swing: 50 },
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
    top: { bar: [1.1265, -0.2] },
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

  // A bench press with the hands about shoulder width apart, the elbows tucked toward the sides.
  close_grip_bench_press: {
    like: 'barbell_bench_press',
    top: { bar: [1.13, -0.21], wrist: [0, 27.7209] },
    bottom: { bar: [0.7347, -0.063], wrist: [-53.1261, 18.2864], rotate: 17.6573 },
    grip: { width: 0.3005, elbows: 0.2191 },
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
    kit: { t_bar_row_machine: { grip: 'neutral' } },
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
    kit: { t_bar_row_machine: { grip: 'wide' } },
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
    body: { hip: [0.53, 0.17], trunk: -10 },
    top: { hand: [0.2882, 1.627, 0.2743] },
    bottom: { hand: [0.32, 1.13, 0.18] },
    feet: { stance: 0.18, z: 0.5, toeOut: 12 },
    grip: { pole: [1, -1, 0.1], sits: 'heel' },
    camera: { target: [0, 0.95, 0.15], halfHeight: 1.0, azimuth: 30, elevation: 10 },
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

  // The bar racked on the front delts, the arms crossed over it, each hand lying open on the other
  // shoulder (`grip.spot`), not gripping the bar, the elbows high and forward (`grip.pole`). The
  // trunk stands upright, which the traced back squat's doesn't, so it has its own shape: the
  // trace's legs, top and bottom, with the trunk near upright; the body turns about the ankles to
  // keep the bar over the feet, as the back squat's does.
  front_squat: {
    like: 'barbell_squat',
    trace: null,
    top: { shin: 3.388, thigh: -11.6002, trunk: 6.2702, rotate: 141.329 },
    bottom: { shin: 31.2548, thigh: 103.7533, trunk: 25.517, rotate: 144.2005 },
    bar: { up: 0.074, back: 0.06 },
    grip: { crossed: true, pole: [0.35, 0.0896, 1.3], rotate: 210, spot: [0.1755, 0.0695, 0.1018] },
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
};
