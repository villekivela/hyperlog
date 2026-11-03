// The technique video beside the stage (README, The studio): where a link opens, its steps and
// speeds, and the A–B loop that, with sync on, is the rep. Nothing of the page in it.

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

/** Where in the video the rep is `d` down (0 the top, 1 the bottom): A at the top, B at the bottom. */
export const timeAt = ({ a, b }, d) => a + d * (b - a);

/** How far down the rep the video is at `t`: held at the top before A and at the bottom past B. */
export const repAt = ({ a, b }, t) => Math.max(0, Math.min(1, (t - a) / (b - a)));

/** Whether the video and the rep move together: sync on, and a loop with two different ends. */
export const synced = (loop, on) => Boolean(on && loop && loop.a !== null && loop.b !== null && loop.a !== loop.b);
