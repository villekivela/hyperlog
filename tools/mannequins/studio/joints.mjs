// A kit's joints as the studio scrubs them (README, The studio), with nothing of the page in it: a
// row for each joint of the kit on stage, stepped a degree (a revolute joint) or a centimetre (a
// prismatic one) at a time, typed in degrees or millimetres, and held within its limits.

const DEGREE = Math.PI / 180;

/** How a joint's value is stepped, typed and shown, by its type: `steps` names a step and a fine one. */
const UNITS = {
  revolute: { step: DEGREE, scale: 1 / DEGREE, show: (v) => `${(v / DEGREE).toFixed(1)}°`, steps: ['1°', '0.1°'] },
  prismatic: { step: 0.01, scale: 1000, show: (v) => `${Math.round(v * 1000)} mm`, steps: ['1 cm', '1 mm'] },
};

/**
 * Each joint of `kits` (a built kit's `kits`, posed at least once), in order: `{ key, kit, body,
 * type, limits }`, its key `<kit>.<body>`. A free body is on no joint, and has none.
 */
export function jointsOf(kits) {
  return Object.entries(kits).flatMap(([kit, built]) => Object.keys(built.bodies).map((body) => {
    const { type, limits } = built.joints[body];
    return { key: `${kit}.${body}`, kit, body, type, limits };
  }));
}

const within = ({ limits: [min, max] }, v) => Math.min(max, Math.max(min, v));

/** `value` moved `by` steps of `row`'s joint (a tenth of one, `fine`), within its limits. */
export const stepJoint = (row, value, by, fine = false) => within(row, value + by * UNITS[row.type].step * (fine ? 0.1 : 1));

/** `row`'s joint at its lower limit (`end` -1) or its upper one (1). */
export const limitOf = (row, end) => row.limits[end < 0 ? 0 : 1];

/** What `text` sets `row`'s joint to, from `value`: degrees or millimetres, `+2` moving it; null if it isn't a number. */
export function typedJoint(row, text, value) {
  const relative = text.startsWith('+');
  const n = Number(relative ? text.slice(1) : text);
  if (text === '' || !Number.isFinite(n)) return null;
  return within(row, (relative ? value : 0) + n / UNITS[row.type].scale);
}

/** `value` of `row`'s joint as the studio shows it. */
export const showJoint = (row, value) => UNITS[row.type].show(value);

/** `value` of `row`'s joint in what it is typed in: degrees or millimetres. */
export const inUnits = (row, value) => Number((value * UNITS[row.type].scale).toFixed(4));

/** What a step of `row`'s joint is, and a fine one, as the studio names them. */
export const stepsOf = (row) => UNITS[row.type].steps;

/** `row`'s joint's limits as the studio shows them. */
export const limitsOf = (row) => `${showJoint(row, row.limits[0])} to ${showJoint(row, row.limits[1])}`;

/** What the status line says of `row`'s joint at `value`: where it is, and the limit it is at. */
export function statusOf(row, value) {
  const at = value <= row.limits[0] ? ', at its lower limit' : value >= row.limits[1] ? ', at its upper limit' : '';
  return `${row.kit} ${row.body} ${showJoint(row, value)}${at}`;
}

/** Sets each scrubbed joint (`scrubs`, values by key) on `kits`, after they are posed; a key no kit has is passed over. */
export function scrubAll(kits, scrubs) {
  for (const [key, value] of Object.entries(scrubs)) {
    const [kit, body] = key.split('.');
    if (kits[kit]?.bodies[body]) kits[kit].scrub(body, value);
  }
}
