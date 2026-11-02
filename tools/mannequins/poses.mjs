// The pose entries: one per exercise, keyed by its id in app/src/main/assets/exercises.json.
// `equipment` is the library's equipment key; an exercise edited on the phone to other equipment
// loses its clip. `camera` frames the stage: the point it looks at, half the height it shows in
// metres, and the turn and tilt it opens on in degrees.
export const POSES = {
  barbell_squat: {
    equipment: 'barbell',
    motion: 'squat',
    kit: 'barbell',
    // Shin and thigh lean from upright at the top and the bottom of the rep, in degrees, in the
    // plane of the knee's bend: with the knees pushed out, the shin seen from the side leans less.
    // Standing, the body tips forward a touch from the ankles to keep the bar over the feet. The
    // bottom matches the library's technique video (UGwmUJkTIx8): seen from the side, the shin
    // leans about 47° and the trunk, hips to shoulders, about 21°.
    shin: [6, 53],
    thigh: [-6, 94],
    // Ankles this far either side of the middle, toes turned out, knees pushed out toward the toes.
    stance: 0.17,
    toeOut: 18,
    kneesOut: 0.45,
    // The bar on the upper back: up the trunk from the hips to just past the shoulders, and behind
    // the trunk's line, in metres. It sits about 6 cm behind the shoulder joints.
    bar: { up: 0.02, back: 0.17 },
    grip: 0.4,
    camera: { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 },
  },
};
