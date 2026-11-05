// The tempo of a clip's rep, in seconds, in four phases: the lowering, the pause at the bottom,
// the lifting and the pause at the top. A clip bakes only the lowering; the phone plays it forward,
// holds, plays it backward and holds again. An entry gives its own `tempo`, otherwise its motion's.
const tempo = (lowering, bottom, lifting, top) => ({ lowering, bottom, lifting, top });

/**
 * The default tempo of each movement: about 2 s eccentric and 1 s concentric. The clip bakes the
 * lowering, which is the eccentric except where the bar is pulled down (the pulldown and the
 * pressdown), where the clip's first half is the concentric: there the lowering is the 1 s and the
 * lifting the 2 s.
 */
export const MOTION_TEMPOS = {
  squat: tempo(2, 0.25, 1, 0.7),
  hinge: tempo(2, 0.3, 1, 0.7),
  bench_press: tempo(2, 0.3, 1, 0.7),
  dumbbell_press: tempo(2, 0.3, 1, 0.7),
  pulldown: tempo(1, 0.3, 2, 0.5),
  pressdown: tempo(1, 0.2, 2, 0.5),
  leg_press: tempo(2, 0.3, 1, 0.6),
  chest_press: tempo(2, 0.3, 1, 0.6),
  // The top of these is where the muscle is shortest (the curl's heels drawn up, the raise's arm
  // out), so the lowering is the eccentric.
  arms: tempo(2, 0.3, 1, 0.6),
  row: tempo(2, 0.3, 1, 0.6),
  free_arms: tempo(2, 0.3, 1, 0.6),
  knee_lever: tempo(2, 0.3, 1, 0.6),
  calf_raise: tempo(2, 0.5, 1, 0.6),
  hack_squat: tempo(2, 0.3, 1, 0.6),
  // Pulled from arms long (a row, a machine pulldown): the clip's first half is the concentric.
  lever_handles: tempo(1, 0.3, 2, 0.5),
  chest_pad: tempo(1, 0.3, 2, 0.5),
  // Driven up from the bottom: the clip's first half, the lowering, is the eccentric.
  hip_thrust: tempo(2, 0.5, 1, 1),
  bridge: tempo(2, 0.3, 1, 0.6),
  // Lowered from the bar or onto the handles: the clip's first half is the eccentric.
  hang: tempo(2, 0.3, 1, 0.6),
  support: tempo(2, 0.3, 1, 0.6),
  lunge: tempo(2, 0.3, 1, 0.6),
  bench_row: tempo(2, 0.3, 1, 0.6),
  // Lowered slowly, the eccentric, and pulled back up by the hamstrings.
  glute_ham_raise: tempo(3, 0.5, 2, 0.5),
};

/** The entry's tempo: its own, otherwise its movement's. */
export function tempoOf(entry) {
  const own = entry.tempo ?? MOTION_TEMPOS[entry.motion];
  if (!own) throw new Error(`No tempo for motion "${entry.motion}": give the entry a tempo`);
  return { ...own };
}
