// The studio's keymap (README, The studio), read from keys.json, with nothing of the page in it.
// Keys are in vim's notation: a character is itself, <C-r> is Ctrl+r, and a binding of two
// characters (gg) is pressed one after the other. Digits typed first are a count.

/** The names a key event's `key` has in vim's notation. */
const NAMED = {
  ' ': 'Space', Tab: 'Tab', Escape: 'Esc', Enter: 'Enter', Backspace: 'BS',
  ArrowLeft: 'Left', ArrowRight: 'Right', ArrowUp: 'Up', ArrowDown: 'Down',
};
const MODIFIERS = new Set(['Shift', 'Control', 'Alt', 'Meta', 'AltGraph', 'CapsLock']);

/** A key event (`{ key, ctrlKey, altKey, shiftKey }`) as one token: `j`, `L`, `<C-r>`, `<S-Tab>`; null for a modifier alone. */
export function tokenOf({ key, ctrlKey = false, altKey = false, shiftKey = false }) {
  if (MODIFIERS.has(key)) return null;
  const named = NAMED[key];
  // Shift is already in a character it changes (L, ?); only a named key carries it.
  const mods = `${ctrlKey ? 'C-' : ''}${altKey ? 'A-' : ''}${named && shiftKey ? 'S-' : ''}`;
  if (!named && !mods) return key;
  return `<${mods}${named ?? key}>`;
}

/** The tokens of a binding: `gg` is `g`, `g`; `<C-r>` is one. */
const tokensOf = (keys) => keys.match(/<[^>]+>|./g);

const WORDS = { 'C-': 'Ctrl+', 'A-': 'Alt+', 'S-': 'Shift+' };
const ARROWS = { Left: '←', Right: '→', Up: '↑', Down: '↓' };

/** A binding as the UI shows it: `Ctrl+r`, `Shift+Tab`, `Space`, `←`, `gg`. */
export function labelOf(keys) {
  return tokensOf(keys).map((t) => {
    const m = /^<((?:[CAS]-)*)(.+)>$/.exec(t);
    if (!m) return t;
    const mods = (m[1].match(/[CAS]-/g) ?? []).map((x) => WORDS[x]).join('');
    return `${mods}${ARROWS[m[2]] ?? m[2]}`;
  }).join('');
}

/**
 * The keymap of `json` (keys.json's shape). `feed(token, focus)` takes one key and returns the
 * command it completes, `{ command, count }` (the count typed before it, for a command that takes
 * one, else null; `command` null for a key nothing is bound to), or null while a binding is pending,
 * which `pending` shows. A group with a `focus` is bound only while that pane is focused, and there
 * its keys take over the others'. `:` opens the command line, whose text `ex(text)` looks up.
 */
export function keymap(json) {
  // The bindings everywhere (''), and each pane's own.
  const scopes = new Map([['', new Map()]]);
  for (const group of json.groups) {
    const scope = group.focus ?? '';
    if (!scopes.has(scope)) scopes.set(scope, new Map());
    for (const b of group.keys) {
      if (/^[1-9]/.test(b.keys)) throw new Error(`${b.keys} is a count`);
      if (scopes.get(scope).has(b.keys)) throw new Error(`${b.keys} is bound twice`);
      scopes.get(scope).set(b.keys, b);
    }
  }
  const bindingsAt = (focus) => new Map([...scopes.get(''), ...(focus ? scopes.get(focus) ?? [] : [])]);
  // A binding that is the first keys of another would never wait for the rest.
  const starts = (a, b) => a.length < b.length && a.every((t, i) => t === b[i]);
  for (const focus of scopes.keys()) {
    const keys = [...bindingsAt(focus).keys()];
    for (const k of keys) {
      for (const other of keys) {
        if (starts(tokensOf(k), tokensOf(other))) throw new Error(`${k} starts ${other}`);
      }
    }
  }
  const all = [...scopes.values()].flatMap((m) => [...m.values()]);
  const ex = new Map(json.ex.map((c) => [c.name, c.command]));
  let digits = '';
  let keys = '';
  const done = (command, count) => {
    digits = '';
    keys = '';
    return { command, count };
  };
  return {
    get pending() { return digits + keys; },
    feed(token, focus = '') {
      if (!keys && (/^[1-9]$/.test(token) || (digits && token === '0'))) {
        digits += token;
        return null;
      }
      if (!keys && token === ':') return done('ex', null);
      const bindings = bindingsAt(focus);
      const at = keys + token;
      const bound = bindings.get(at);
      if (bound) return done(bound.command, bound.count && digits ? Number(digits) : null);
      if ([...bindings.keys()].some((k) => starts(tokensOf(at), tokensOf(k)))) {
        keys = at;
        return null;
      }
      return done(null, null);
    },
    reset() { digits = ''; keys = ''; },
    ex: (text) => ex.get(text.trim()) ?? null,
    /** The key a command is on, as the UI labels it: `:w` for a command-line one. */
    keyOf(command) {
      for (const b of all) if (b.command === command) return labelOf(b.keys);
      for (const [name, c] of ex) if (c === command) return `:${name}`;
      return null;
    },
  };
}

/** The help sheet: each group of keys.json with its keys labelled (and the pane it is bound to), then the command line's. */
export function helpOf(json) {
  return [
    ...json.groups.map((g) => ({ name: g.name, focus: g.focus ?? null, rows: g.keys.map((b) => ({ keys: b.keys, label: labelOf(b.keys), does: b.does })) })),
    { name: 'Commands', focus: null, rows: json.ex.map((c) => ({ keys: `:${c.name}`, label: `:${c.name}`, does: c.does })) },
  ];
}
