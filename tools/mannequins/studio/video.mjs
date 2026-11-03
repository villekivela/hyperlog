// The technique video beside the stage (README, The studio): where a link opens, its steps and
// speeds, and the loop that, with sync on, is the rep. Nothing of the page in it.

/** The speeds the video plays at, slowest first. */
export const SPEEDS = [0.25, 0.5, 1];

/** A `t=` of `278`, `278s` or `1m5s`, in seconds. */
const secondsOf = (t) => {
  const m = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$/.exec(t ?? '');
  return m ? Number(m[1] ?? 0) * 3600 + Number(m[2] ?? 0) * 60 + Number(m[3] ?? 0) : 0;
};

/** A YouTube link's video and the second it opens at, `{ id, start }`; null for any other link. */
export function videoOf(link) {
  let url;
  try {
    url = new URL(link);
  } catch {
    return null;
  }
  const host = url.hostname.replace(/^www\./, '');
  const id = host === 'youtu.be' ? url.pathname.slice(1) : host === 'youtube.com' ? url.searchParams.get('v') : null;
  if (!id || !/^[\w-]{11}$/.test(id)) return null;
  return { id, start: secondsOf(url.searchParams.get('t')) };
}

export const slower = (speed) => SPEEDS[Math.max(0, SPEEDS.indexOf(speed) - 1)];
export const faster = (speed) => SPEEDS[Math.min(SPEEDS.length - 1, SPEEDS.indexOf(speed) + 1)];

/**
 * `n` frames on from `t` (back for a negative `n`) at `fps`, within `duration`: the middle of that
 * frame, so the video shows it whatever the rounding of its time.
 */
export function stepFrames(t, n, fps, duration) {
  const last = Math.max(0, Math.ceil(duration * fps) - 1);
  const frame = Math.max(0, Math.min(last, Math.floor(t * fps) + n));
  return (frame + 0.5) / fps;
}

/** `n` seconds on from `t` (back for a negative `n`), within `duration`. */
export const stepSeconds = (t, n, duration) => Math.max(0, Math.min(duration, t + n));

// A loop is `{ a, b, c }`, seconds in the video: A the top of a rep, B its bottom and C the top
// again, each null until it is marked. A loop saved before C existed has no `c`.

/**
 * Where in the video the rep is, `{ d, lifting }` (`d` 0 at the top, 1 at the bottom): the lowering
 * runs from A to B and the lift from B on to C, so the video only ever plays forward.
 */
export const timeAt = ({ a, b, c }, { d, lifting }) => (lifting ? b + (1 - d) * (c - b) : a + d * (b - a));

/** Where in the rep the video is at `t`, `{ d, lifting }`: at the top before A, and again past C. */
export function repAt({ a, b, c }, t) {
  if (t <= b) return { d: Math.max(0, (t - a) / (b - a)), lifting: false };
  return { d: Math.max(0, 1 - (t - b) / (c - b)), lifting: true };
}

const isSet = (v) => typeof v === 'number';

/** Whether the video and the rep move together: sync on, and A, B and C marked in that order. */
export const synced = (loop, on) => Boolean(on && loop && [loop.a, loop.b, loop.c].every(isSet) && loop.a < loop.b && loop.b < loop.c);

/** The stretch of the video the loop's marks span, `[from, to]`, or null before two are marked. */
export function spanOf(loop) {
  const marks = [loop?.a, loop?.b, loop?.c].filter(isSet);
  return marks.length < 2 ? null : [Math.min(...marks), Math.max(...marks)];
}

/** What the scrub bar shows, `[from, to]`: the loop and a quarter of it either side (0.5 s at least), else the whole video. */
export function zoomOf(loop, duration) {
  const span = spanOf(loop);
  if (!span) return [0, duration];
  const margin = Math.max(0.5, (span[1] - span[0]) / 4);
  return [Math.max(0, span[0] - margin), Math.min(duration, span[1] + margin)];
}
