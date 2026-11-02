// The tempo of a clip's rep, in seconds, in four phases: the lowering, the pause at the bottom,
// the lifting and the pause at the top. A clip bakes only the lowering; the phone plays it forward,
// holds, plays it backward and holds again. An entry gives its own `tempo`, otherwise its motion's.
const tempo = (lowering, bottom, lifting, top) => ({ lowering, bottom, lifting, top });

/** The default tempo of each movement. */
export const MOTION_TEMPOS = {
  squat: tempo(1.5, 0.25, 1.1, 0.7),
  hinge: tempo(1.6, 0.3, 1.2, 0.7),
  bench_press: tempo(1.5, 0.3, 1, 0.7),
  pulldown: tempo(1.3, 0.3, 1.5, 0.5),
  pressdown: tempo(1.2, 0.2, 1.2, 0.5),
  leg_press: tempo(1.5, 0.3, 1.1, 0.6),
  chest_press: tempo(1.4, 0.3, 1.1, 0.6),
};

/** The entry's tempo: its own, otherwise its movement's. */
export function tempoOf(entry) {
  const own = entry.tempo ?? MOTION_TEMPOS[entry.motion];
  if (!own) throw new Error(`No tempo for motion "${entry.motion}": give the entry a tempo`);
  return { ...own };
}
