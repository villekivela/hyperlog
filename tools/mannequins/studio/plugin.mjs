// The studio's half on the owner's machine (README, The studio): a Vite plugin that lists every
// exercise and kit with its status, hands the page each one's prepared entry and checks, and writes
// what the owner does into the repo's files. A save goes into poses.mjs through patch.mjs and
// re-bakes; an OK goes into the batch's review file and a note into review/notes.md (review.mjs).
//
// The tool's modules are loaded through Vite (`ssrLoadModule`), so an edit to kit.mjs or poses.mjs
// is posed from at once. The page imports the same modules; what is node's in them (reading
// files) is stubbed there, and never reached, since the page is handed every entry prepared.
import { execFile } from 'node:child_process';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
// Nothing here imports poses.mjs or kit.mjs, even through another module: Vite restarts its server
// when a file its config imports changes, and those change all the time.
import { patchPoses } from '../patch.mjs';

const TOOL = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const ASSETS = path.join(TOOL, '..', '..', 'app', 'src', 'main', 'assets');
const LIBRARY = path.join(ASSETS, 'exercises.json');
/** Where the bake writes the clips (bake.mjs's CLIPS). */
const CLIPS = path.join(ASSETS, 'mannequins');

/** What the page's copies of the tool's modules get for node's: nothing that reads a file. */
const STUBS = {
  'node:fs': "export function readFileSync() { throw new Error('No files in the studio page'); }",
  'node:fs/promises': "export function readFile() { throw new Error('No files in the studio page'); }",
  'node:url': "export const fileURLToPath = () => '';",
  'node:path': "export default { join: (...parts) => parts.join('/'), dirname: () => '' };",
};
const STUB = '\0studio-node:';

/** The full bake, in a process of its own: this one has the poses it started with loaded. */
const bakeInChild = (clips) => new Promise((resolve, reject) => {
  execFile(process.execPath, [path.join(TOOL, 'bake.mjs'), clips], { maxBuffer: 1 << 24 }, (err, stdout, stderr) => {
    if (err) reject(new Error((stderr || err.message).trim().split('\n').filter((l) => !/^\s+at /.test(l)).slice(-3).join('\n')));
    else resolve();
  });
});

/** A request's JSON body. */
const bodyOf = (req) => new Promise((resolve, reject) => {
  let text = '';
  req.on('data', (c) => { text += c; });
  req.on('end', () => { try { resolve(text ? JSON.parse(text) : {}); } catch (err) { reject(err); } });
  req.on('error', reject);
});

class Refused extends Error {}
/** A refusal of what `run` throws: the owner's mistake, not the studio's. */
const refusing = async (run) => {
  try {
    return await run();
  } catch (err) {
    throw err instanceof Refused ? err : new Refused(err.message);
  }
};
const readLibrary = async () => JSON.parse(await readFile(LIBRARY, 'utf8')).exercises;
/** A kit's status: an open note on it, or whether a pose uses it. */
const kitStatus = (notes, uses) => (notes ? 'notes' : uses ? 'kit' : 'unused');

/**
 * Whether a write comes from the studio's own page: as JSON (which a page elsewhere can't send
 * without asking first) and, if the browser names its origin, from this server's.
 */
function refusal(req) {
  if (!/^application\/json\b/.test(req.headers['content-type'] ?? '')) return [415, 'A write is JSON'];
  const origin = req.headers.origin;
  if (origin && new URL(origin).host !== req.headers.host) return [403, `Not from the studio: ${origin}`];
  return null;
}

/**
 * The studio plugin. `posesFile`, `reviewDir` and `clips` are where the poses, the review files
 * and the baked clips are (the repo's own by default); `bake()` re-bakes every clip.
 */
export function studio({ posesFile = path.join(TOOL, 'poses.mjs'), reviewDir, clips = CLIPS, bake = () => bakeInChild(clips) } = {}) {
  let server;
  const load = (file) => server.ssrLoadModule(path.isAbsolute(file) ? file : path.join(TOOL, file));
  const tool = async () => {
    const [entries, checks, kit, review, poses] = await Promise.all(
      ['entries.mjs', 'checks.mjs', 'kit.mjs', 'review.mjs', posesFile].map(load),
    );
    return { entries, checks, kit, review, POSES: poses.POSES, dir: reviewDir ?? review.REVIEW };
  };

  // Each posed exercise's checks, run once per version of the tool's code and poses.
  let checked = new Map();
  const checksOf = (t, id) => {
    if (!checked.has(id)) checked.set(id, t.checks.checkPose(id, { poses: t.POSES }).then((r) => r.checks, (err) => ({ error: { ok: false, frame: null, found: err.message } })));
    return checked.get(id);
  };
  const failing = (checks) => Object.values(checks).some((c) => !c.ok);
  // What the studio last wrote to poses.mjs itself: the page already has it, so it needn't reload.
  let own = null;
  const changed = () => {
    checked = new Map();
    for (const m of server.moduleGraph.getModulesByFile(posesFile) ?? []) server.moduleGraph.invalidateModule(m);
  };

  async function items(wait) {
    const t = await tool();
    const library = await readLibrary();
    const notes = await t.review.readNotes({ dir: t.dir });
    const order = Object.keys(t.kit.EQUIPMENT);
    const rank = (e) => (order.includes(e) ? order.indexOf(e) : order.length);
    const groups = new Map();
    for (const e of [...library].sort((a, b) => rank(a.equipment) - rank(b.equipment) || a.name.localeCompare(b.name))) {
      if (!groups.has(e.equipment)) groups.set(e.equipment, []);
      groups.get(e.equipment).push(e);
    }
    const exercises = [];
    for (const [equipment, list] of groups) {
      const rows = await Promise.all(list.map(async (e) => {
        const { batch, state, notes: mine } = await t.review.reviewOf(e.id, { dir: t.dir, clips, notes });
        const row = { id: e.id, name: e.name, equipment, status: t.POSES[e.id] ? state : 'no pose', batch, notes: mine.length };
        if (!t.POSES[e.id]) return row;
        const pending = checksOf(t, e.id);
        const done = wait ? await pending : await Promise.race([pending, Promise.resolve(null)]);
        if (!done) row.checking = true;
        else if (failing(done)) row.status = 'failing';
        return row;
      }));
      exercises.push({ equipment, items: rows });
    }
    const uses = usesOf(t);
    const kits = Object.keys(t.kit.KITS).sort().map((name) => {
      const n = notes[`kit:${name}`]?.length ?? 0;
      return { id: name, name, uses: uses[name] ?? null, status: kitStatus(n, uses[name]), notes: n };
    });
    return { exercises, kits };
  }

  /** The first posed exercise, by id, that each kit is used in. */
  function usesOf(t) {
    const out = {};
    for (const id of Object.keys(t.POSES).sort()) {
      for (const name of Object.keys(t.entries.resolve(id, t.POSES).kit ?? {})) out[name] ??= id;
    }
    return out;
  }

  /** The baked clip of `id`, from the index: its file and framing, and each bench angle's. */
  async function clipOf(id) {
    const index = JSON.parse(await readFile(path.join(clips, 'mannequins.json'), 'utf8')).clips[id];
    if (!index) return null;
    const url = (f) => `/clips/${f}`;
    return {
      file: url(index.clip), camera: index.camera,
      ...(index.bench ? { angles: Object.fromEntries(Object.entries(index.bench.clips).map(([a, c]) => [a, { file: url(c.clip), camera: c.camera }])) } : {}),
    };
  }

  async function exercise(id) {
    const t = await tool();
    const library = (await readLibrary()).find((e) => e.id === id);
    if (!library) throw new Refused(`No exercise ${id} in the library`);
    const { batch, state, notes } = await t.review.reviewOf(id, { dir: t.dir, clips });
    const base = { kind: 'exercise', id, name: library.name, equipment: library.equipment, batch, notes };
    if (!t.POSES[id]) return { ...base, status: 'no pose', entry: null, checks: null, clip: null };
    const checks = await checksOf(t, id);
    return {
      ...base, status: failing(checks) ? 'failing' : state, checks,
      entry: t.entries.prepare(id, t.POSES), grips: t.entries.gripsOf(id, t.POSES), clip: await clipOf(id),
      fingerprint: await t.review.fingerprint(id, clips).catch(() => null),
    };
  }

  async function kit(name) {
    const t = await tool();
    if (!t.kit.KITS[name]) throw new Refused(`No kit ${name}`);
    const uses = usesOf(t)[name] ?? null;
    const notes = (await t.review.readNotes({ dir: t.dir }))[`kit:${name}`] ?? [];
    return { kind: 'kit', id: name, name, uses, notes, status: kitStatus(notes.length, uses), entry: uses && t.entries.prepare(uses, t.POSES) };
  }

  // One save at a time: each patches the file as the last one left it, and bakes.
  let saving = Promise.resolve();
  let baking = { baking: null, last: null };
  function save(id, tuned) {
    const run = saving.then(async () => {
      baking = { ...baking, baking: id };
      const before = await readFile(posesFile, 'utf8');
      const after = patchPoses(before, id, tuned);
      own = after;
      await writeFile(posesFile, after);
      changed();
      try {
        await bake();
      } catch (err) {
        // A pose that fails its checks doesn't bake, and the file never holds one that didn't.
        own = before;
        await writeFile(posesFile, before);
        changed();
        throw new Refused(`Not saved: the bake failed. ${err.message}`);
      }
      const t = await tool();
      return { id, clip: await t.review.fingerprint(id, clips) };
    });
    saving = run.then((r) => { baking = { baking: null, last: { ...r, ok: true } }; }, (err) => { baking = { baking: null, last: { id, ok: false, error: err.message } }; });
    return run;
  }

  const routes = {
    'GET /api/items': (url) => items(url.searchParams.has('wait')),
    'GET /api/state': () => baking,
    'POST /api/save': ({ id, tuned }) => save(id, tuned),
    'POST /api/ok': async ({ id, ok }) => {
      const t = await tool();
      if (!t.POSES[id]) throw new Refused(`${id} has no pose to OK`);
      return refusing(() => t.review.setOk(id, ok, { dir: t.dir, clips }));
    },
    'POST /api/note': async ({ item, text }) => {
      const t = await tool();
      await refusing(() => t.review.addNote(item, text ?? '', { dir: t.dir, clips }));
      return { item };
    },
  };

  return {
    name: 'mannequin-studio',
    enforce: 'pre',
    resolveId(id) {
      if (this.environment?.name === 'client' && STUBS[id]) return STUB + id;
      return null;
    },
    load(id) {
      return id.startsWith(STUB) ? STUBS[id.slice(STUB.length)] : null;
    },
    // A save the studio made itself is already on the page; anything else reloads it (the page
    // keeps its place).
    async hotUpdate({ file, read }) {
      if (path.resolve(file) === path.resolve(posesFile) || file.endsWith('.mjs')) checked = new Map();
      if (this.environment.name !== 'client' || path.resolve(file) !== path.resolve(posesFile)) return;
      if ((await read()) === own) return [];
    },
    configureServer(s) {
      server = s;
      server.watcher.add(posesFile);
      server.middlewares.use(async (req, res, next) => {
        const url = new URL(req.url, 'http://studio');
        if (url.pathname === '/') {
          res.statusCode = 302;
          res.setHeader('location', '/studio/');
          res.end();
          return;
        }
        const clip = /^\/clips\/([\w.-]+)$/.exec(url.pathname);
        const item = /^\/api\/item\/(exercise|kit)\/([\w-]+)$/.exec(url.pathname);
        const route = routes[`${req.method} ${url.pathname}`];
        if (!clip && !item && !route) return next();
        const refused = req.method === 'POST' && refusal(req);
        if (refused) {
          res.statusCode = refused[0];
          res.setHeader('content-type', 'application/json');
          res.end(JSON.stringify({ error: refused[1] }));
          return;
        }
        try {
          if (clip) {
            const bytes = await readFile(path.join(clips, clip[1]));
            res.setHeader('content-type', clip[1].endsWith('.json') ? 'application/json' : 'application/octet-stream');
            res.setHeader('cache-control', 'no-store');
            res.end(bytes);
            return;
          }
          const out = item ? await (item[1] === 'kit' ? kit(item[2]) : exercise(item[2]))
            : await route(req.method === 'POST' ? await bodyOf(req) : url);
          res.setHeader('content-type', 'application/json');
          res.end(JSON.stringify(out));
        } catch (err) {
          res.statusCode = err instanceof Refused || err.code === 'ENOENT' ? 422 : 500;
          res.setHeader('content-type', 'application/json');
          res.end(JSON.stringify({ error: err.message }));
        }
      });
    },
  };
}
