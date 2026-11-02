// Writes tuned values (`{ path: value }`, from the review page) into poses.mjs as text, so the
// comments around them stay. A number is replaced where it is, a point whole, and a key the entry
// hasn't got is added where it belongs. Anything it can't place is an error that names it.

/** The index just past the string or comment starting at `i`, or `i` if none does. */
function skipOpaque(src, i) {
  const c = src[i];
  if (c === '"' || c === "'" || c === '`') {
    let j = i + 1;
    while (src[j] !== c) j += src[j] === '\\' ? 2 : 1;
    return j + 1;
  }
  if (c === '/' && src[i + 1] === '/') return src.indexOf('\n', i) === -1 ? src.length : src.indexOf('\n', i);
  if (c === '/' && src[i + 1] === '*') return src.indexOf('*/', i) + 2;
  return i;
}

/** The index of the bracket closing the one at `open`. */
function closeOf(src, open) {
  const pairs = { '{': '}', '[': ']', '(': ')' };
  const stack = [pairs[src[open]]];
  for (let i = open + 1; i < src.length;) {
    const skipped = skipOpaque(src, i);
    if (skipped !== i) { i = skipped; continue; }
    const c = src[i];
    if (pairs[c]) stack.push(pairs[c]);
    else if (c === stack.at(-1)) {
      stack.pop();
      if (!stack.length) return i;
    }
    i++;
  }
  throw new Error('Unbalanced brackets in poses.mjs');
}

/** Where the value starting at `start` ends: past its closing bracket, or before the next `,` or `}`. */
function valueEnd(src, start) {
  if ('{[('.includes(src[start])) return closeOf(src, start) + 1;
  let i = start;
  while (i < src.length && !',}]\n'.includes(src[i])) {
    const skipped = skipOpaque(src, i);
    i = skipped !== i ? skipped : i + 1;
  }
  while (/\s/.test(src[i - 1])) i--;
  return i;
}

/** The members of the object literal at `open`..`close`: each key with where its value is. */
function members(src, open, close) {
  const out = [];
  for (let i = open + 1; i < close;) {
    const skipped = skipOpaque(src, i);
    if (skipped !== i) { i = skipped; continue; }
    const m = /^(\w+|'[^']*')\s*:\s*/.exec(src.slice(i, close));
    if (!m) { i++; continue; }
    const start = i + m[0].length;
    out.push({ key: m[1].replace(/'/g, ''), keyStart: i, start, end: valueEnd(src, start) });
    i = valueEnd(src, start);
  }
  return out;
}

const key = (k) => (/^\w+$/.test(k) ? k : `'${k}'`);
const show = (v) => (Array.isArray(v) ? `[${v.map(show).join(', ')}]`
  : typeof v === 'number' ? String(Number(v.toFixed(4)))
  : v && typeof v === 'object' ? `{ ${Object.entries(v).map(([k, x]) => `${key(k)}: ${show(x)}`).join(', ')} }`
  : JSON.stringify(v));

/** `{ a: { b: v } }`'s text for the steps `a.b` and the value `v`. */
const nested = (steps, value) => (steps.length === 1 ? `${steps[0]}: ${show(value)}` : `${steps[0]}: { ${nested(steps.slice(1), value)} }`);

/** Sets `steps` inside the object literal at `open` in `src`; returns the new source. */
function put(src, open, steps, value, where) {
  const close = closeOf(src, open);
  const [head, ...rest] = steps;
  const found = members(src, open, close).find((m) => m.key === head);
  if (found && rest.length === 0) return src.slice(0, found.start) + show(value) + src.slice(found.end);
  if (found) {
    if (src[found.start] !== '{') throw new Error(`${where}: ${head} is not an object in poses.mjs`);
    return put(src, found.start, rest, value, where);
  }
  const body = src.slice(open + 1, close);
  if (!body.includes('\n')) return `${src.slice(0, close).replace(/\s*$/, '')}, ${nested(steps, value)} ${src.slice(close)}`;
  const indent = /\n([ \t]*)\S[^\n]*\s*$/.exec(body)?.[1] ?? '    ';
  const before = src.slice(0, close).replace(/\s*$/, '');
  const comma = /[,{]$/.test(before) ? '' : ',';
  return `${before}${comma}\n${indent}${nested(steps, value)},${src.slice(close).replace(/^/, '\n' + indent.slice(2))}`.replace(/\n\s*\n(\s*\})/, '\n$1');
}

/** `source` (poses.mjs) with the tuned values put into entry `id`'s own literal. */
export function patchPoses(source, id, tuned) {
  const start = new RegExp(`^  ${id}: \\{`, 'm').exec(source);
  if (!start) throw new Error(`No pose entry ${id} in poses.mjs`);
  let out = source;
  for (const [named, value] of Object.entries(tuned)) {
    // `@angles` is an adjustable bench's per-angle settings, which the entry keeps in `bench.perAngle`.
    const p = named === '@angles' ? 'bench.perAngle' : named;
    const open = new RegExp(`^  ${id}: \\{`, 'm').exec(out).index + `  ${id}: `.length;
    try {
      out = put(out, open, p.split('.'), value, `${id}.${p}`);
    } catch (err) {
      throw new Error(`${id}: can't put ${p} into poses.mjs: ${err.message}`);
    }
  }
  return out;
}
