// The studio's half on the owner's machine (README, The studio): a Vite plugin that lists every
// exercise and kit with its status, hands the page each one's prepared entry and checks, and writes
// what the owner does into the repo's files. A save goes into poses.mjs through patch.mjs and
// re-bakes what it changed; an OK goes into the batch's review file and a note into review/notes.md
// (review.mjs).
// An exercise's technique video is fetched with yt-dlp the first time it is opened, into a cache
// outside the repo, and its loop (the marks of one rep) goes into review/loops.json.
//
// The tool's modules are loaded through Vite (`ssrLoadModule`), so an edit to a recipe, a part or
// poses.mjs is posed from at once. The page imports the same modules; what is node's in them (reading
// files) is stubbed there, and never reached, since the page is handed every entry prepared.
import { execFile } from 'node:child_process';
import { createReadStream } from 'node:fs';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
// Nothing here imports poses.mjs, kit.mjs or the recipes and parts, even through another module:
// Vite restarts its server when a file its config imports changes, and those change all the time.
import { patchPoses } from '../patch.mjs';
import { checkQueue } from './checking.mjs';
import { videoOf } from './video.mjs';

const TOOL = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const ASSETS = path.join(TOOL, '..', '..', 'app', 'src', 'main', 'assets');
const LIBRARY = path.join(ASSETS, 'exercises.json');
/** Where the bake writes the clips (bake.mjs's CLIPS). */
const CLIPS = path.join(ASSETS, 'mannequins');

/** Where technique videos are kept once fetched: outside the repo, like footage, so none is ever committed. */
export const VIDEOS = path.join(process.env.XDG_CACHE_HOME || path.join(homedir(), '.cache'), 'hyperlog', 'videos');
const VIDEO_TYPES = { '.mp4': 'video/mp4', '.webm': 'video/webm', '.mkv': 'video/x-matroska' };

/**
 * Fetches YouTube video `id` into `dir` with yt-dlp, the picture only (the studio plays it muted)
 * at 720p or less, and says what it fetched: `{ file, fps, duration }` (null when yt-dlp can't
 * say; the page reads it from the video). A fetch that stalls for ten minutes is given up.
 */
const ytDlp = (id, dir) => new Promise((resolve, reject) => {
  const args = [
    '--no-playlist', '--no-warnings', '--no-progress',
    '-f', 'bv*[height<=720][ext=mp4]/bv*[height<=720]/b[height<=720]/b',
    '-o', path.join(dir, '%(id)s.%(ext)s'),
    '--print', 'after_move:%(filepath)s\t%(fps)s\t%(duration)s',
    `https://youtu.be/${id}`,
  ];
  execFile('yt-dlp', args, { maxBuffer: 1 << 20, timeout: 10 * 60 * 1000 }, (err, stdout, stderr) => {
    if (err) { reject(new Error(err.killed ? 'yt-dlp took over ten minutes' : (stderr || err.message).trim().split('\n').at(-1))); return; }
    const [file, fps, duration] = stdout.trim().split('\n').at(-1).split('\t');
    resolve({ file: path.basename(file), fps: Number(fps) || 30, duration: Number(duration) || null });
  });
});

/** What the page's copies of the tool's modules get for node's: nothing that reads a file. */
const STUBS = {
  'node:fs': "export function readFileSync() { throw new Error('No files in the studio page'); }",
  'node:fs/promises': "export function readFile() { throw new Error('No files in the studio page'); }",
  'node:url': "export const fileURLToPath = () => '';",
  'node:path': "export default { join: (...parts) => parts.join('/'), dirname: () => '' };",
};
const STUB = '\0studio-node:';

/** The bake of poses `ids`, or of them all, in a process of its own: this one has the poses it started with loaded. */
const bakeInChild = (clips, ids) => new Promise((resolve, reject) => {
  execFile(process.execPath, [path.join(TOOL, 'bake.mjs'), clips, ...(ids ? ['--only', ids.join(',')] : [])], { maxBuffer: 1 << 24 }, (err, stdout, stderr) => {
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
/** Exercise `id` as the library has it, or undefined. */
const libraryEntry = async (id) => (await readLibrary()).find((e) => e.id === id);
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

/** Pose `id` and every pose that takes it by `like:`, however far along: what a change to it changes. */
const takenFrom = (id, poses) => Object.keys(poses).filter((pose) => {
  const seen = new Set();
  for (let at = pose; at !== undefined && !seen.has(at); at = poses[at]?.like) {
    if (at === id) return true;
    seen.add(at);
  }
  return false;
});

/**
 * The studio plugin. `posesFile`, `reviewDir` and `clips` are where the poses, the review files
 * and the baked clips are (the repo's own by default); `bake(ids)` re-bakes those poses' clips, or
 * every clip if there are none. `videos` is the technique videos' cache, and `fetchVideo(id, dir)` fetches one into it (`ytDlp`). `check(id, t)`
 * runs a pose's checks with the tool's modules `t`.
 */
export function studio({
  posesFile = path.join(TOOL, 'poses.mjs'), reviewDir, clips = CLIPS, bake = (ids) => bakeInChild(clips, ids),
  videos = VIDEOS, fetchVideo = ytDlp,
  check = (id, t) => t.checks.checkPose(id, { poses: t.POSES }).then((r) => r.checks),
} = {}) {
  let server;
  const load = (file) => server.ssrLoadModule(path.isAbsolute(file) ? file : path.join(TOOL, file));
  const tool = async () => {
    const [entries, checks, kit, review, poses] = await Promise.all(
      ['entries.mjs', 'checks.mjs', 'kit.mjs', 'review.mjs', posesFile].map(load),
    );
    return { entries, checks, kit, review, POSES: poses.POSES, dir: reviewDir ?? review.REVIEW };
  };

  // Each posed exercise's checks, run once per version of the tool's code and poses. A plain list
  // starts none: opening an item does, that item first, so what is on stage is never behind the rest.
  const checks = checkQueue(async (id) => {
    try {
      return await check(id, await tool());
    } catch (err) {
      return { error: { ok: false, frame: null, found: err.message } };
    }
  });
  const checkAll = (t) => checks.ask(Object.keys(t.POSES));
  const failing = (found) => Object.values(found).some((c) => !c.ok);
  // What the studio last wrote to poses.mjs itself: the page already has it, so it needn't reload.
  let own = null;
  // Whether anything but a save has changed the tool since the last bake: the next one bakes every clip.
  let edited = false;
  // The checks of what a save of `id` changed start again, `id` first, as the page will open it; the
  // rest keep theirs. Gives the poses it changed.
  const changed = async (id) => {
    for (const m of server.moduleGraph.getModulesByFile(posesFile) ?? []) server.moduleGraph.invalidateModule(m);
    const ids = takenFrom(id, (await tool()).POSES);
    checks.forget(ids);
    checks.first(id);
    return ids;
  };

  async function items(wait) {
    const t = await tool();
    if (wait) await checkAll(t);
    // It reads a file or two for each exercise, a pose under way holding up each read.
    const resume = checks.pause();
    try {
      return await listOf(t);
    } finally {
      resume();
    }
  }

  /** Every exercise, by equipment, and every kit, each with its status as far as the checks have got. */
  async function listOf(t) {
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
        const done = checks.found(e.id);
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

  /**
   * The posed exercise each kit is shown as: the one that gives it the most joints (an adjustable
   * bench's back, a twin column's two stacks), so each can be scrubbed, and the first by id of those.
   */
  function usesOf(t) {
    const out = {};
    const most = {};
    for (const id of Object.keys(t.POSES).sort()) {
      let kits;
      try {
        kits = t.kit.buildKit(t.entries.prepare(id, t.POSES)).kits;
      } catch {
        continue;
      }
      for (const [name, kit] of Object.entries(kits)) {
        const joints = Object.keys(kit.bodies).length;
        if (!(name in out) || joints > most[name]) { out[name] = id; most[name] = joints; }
      }
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
    const posed = Boolean(t.POSES[id]);
    // Its checks first, then everything else it needs while they run: the rest start behind them,
    // and a pose under way holds up every file read here.
    const found = posed ? checks.first(id) : null;
    checkAll(t);
    const library = await libraryEntry(id);
    if (!library) throw new Refused(`No exercise ${id} in the library`);
    const [{ batch, state, notes }, loops, clip, fingerprint, mine] = await Promise.all([
      t.review.reviewOf(id, { dir: t.dir, clips }), t.review.readLoops({ dir: t.dir }),
      posed ? clipOf(id) : null, posed ? t.review.fingerprint(id, clips).catch(() => null) : null, found,
    ]);
    const loop = loops[id] ?? null;
    const video = library.video ? { link: library.video, start: videoOf(library.video)?.start ?? 0, loop } : null;
    const base = { kind: 'exercise', id, name: library.name, equipment: library.equipment, batch, notes, video };
    if (!posed) return { ...base, status: 'no pose', entry: null, checks: null, clip: null };
    return {
      ...base, status: failing(mine) ? 'failing' : state, checks: mine,
      entry: t.entries.prepare(id, t.POSES), grips: t.entries.gripsOf(id, t.POSES), clip, fingerprint,
    };
  }

  async function kit(name) {
    const t = await tool();
    checkAll(t);
    if (!t.kit.KITS[name]) throw new Refused(`No kit ${name}`);
    const uses = usesOf(t)[name] ?? null;
    const notes = (await t.review.readNotes({ dir: t.dir }))[`kit:${name}`] ?? [];
    // A kit no pose uses yet is shown alone, where its recipe can stand without one.
    const entry = uses ? t.entries.prepare(uses, t.POSES) : t.kit.shownAlone(name);
    return { kind: 'kit', id: name, name, uses, notes, status: kitStatus(notes.length, uses), entry };
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
      const ids = await changed(id);
      try {
        await bake(edited ? undefined : ids);
        edited = false;
      } catch (err) {
        // A pose that fails its checks doesn't bake, and the file never holds one that didn't.
        own = before;
        await writeFile(posesFile, before);
        await changed(id);
        throw new Refused(`Not saved: the bake failed. ${err.message}`);
      }
      const t = await tool();
      return { id, clip: await t.review.fingerprint(id, clips) };
    });
    saving = run.then((r) => { baking = { baking: null, last: { ...r, ok: true } }; }, (err) => { baking = { baking: null, last: { id, ok: false, error: err.message } }; });
    return run;
  }

  // Each video fetched once: what the cache holds, or the fetch under way, which every exercise
  // from the same video waits on.
  const fetching = new Map();
  async function video(exerciseId) {
    const link = (await libraryEntry(exerciseId))?.video;
    if (!link) throw new Refused(`${exerciseId} has no video link`);
    const id = videoOf(link)?.id;
    if (!id) throw new Refused(`Not a YouTube link: ${link}`);
    if (!fetching.has(id)) {
      const run = (async () => {
        const meta = path.join(videos, `${id}.json`);
        const cached = await readFile(meta, 'utf8').then(JSON.parse, () => null);
        if (cached && await stat(path.join(videos, cached.file)).catch(() => null)) return cached;
        await mkdir(videos, { recursive: true });
        let got;
        try {
          got = await fetchVideo(id, videos);
        } catch (err) {
          throw new Refused(`Couldn't fetch the video: ${err.message}`);
        }
        await writeFile(meta, JSON.stringify(got));
        return got;
      })();
      fetching.set(id, run);
      // A failed fetch (offline) is tried again next time.
      run.catch(() => fetching.delete(id));
    }
    const { file, fps, duration } = await fetching.get(id);
    return { src: `/videos/${file}`, fps, duration };
  }

  /** A cached video's bytes, or the range of them a seek asks for. */
  async function sendVideo(req, res, file) {
    const at = path.join(videos, file);
    const { size } = await stat(at);
    res.setHeader('content-type', VIDEO_TYPES[path.extname(file)] ?? 'application/octet-stream');
    res.setHeader('accept-ranges', 'bytes');
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? '');
    let [start, end] = [0, size - 1];
    if (range) {
      // `-500` is the last 500 bytes, or all of them.
      start = range[1] ? Number(range[1]) : Math.max(0, size - Number(range[2]));
      end = range[1] && range[2] ? Math.min(Number(range[2]), size - 1) : size - 1;
      if (start > end || start >= size) {
        res.statusCode = 416;
        res.setHeader('content-range', `bytes */${size}`);
        res.end();
        return;
      }
      res.statusCode = 206;
      res.setHeader('content-range', `bytes ${start}-${end}/${size}`);
    }
    res.setHeader('content-length', end - start + 1);
    // A file gone since `stat` ends the response short rather than the server.
    createReadStream(at, { start, end }).on('error', () => res.destroy()).pipe(res);
  }

  const isTime = (v) => v === null || (typeof v === 'number' && Number.isFinite(v) && v >= 0);
  // One loop written at a time: each reads the file as the last one left it.
  let looping = Promise.resolve();

  const routes = {
    'GET /api/items': (url) => items(url.searchParams.has('wait')),
    'GET /api/state': () => ({ ...baking, checks: checks.progress() }),
    'POST /api/save': ({ id, tuned }) => save(id, tuned),
    'POST /api/ok': async ({ id, ok }) => {
      const t = await tool();
      if (!t.POSES[id]) throw new Refused(`${id} has no pose to OK`);
      return refusing(() => t.review.setOk(id, ok, { dir: t.dir, clips }));
    },
    'POST /api/loop': async ({ id, loop }) => {
      const t = await tool();
      if (!(await libraryEntry(id))?.video) throw new Refused(`${id} has no video to loop`);
      if (loop && ![loop.a, loop.b, loop.c ?? null].every(isTime)) throw new Refused('A loop\'s marks are times in seconds');
      const write = looping.then(() => t.review.setLoop(id, loop ?? null, { dir: t.dir }));
      looping = write.catch(() => {});
      await write;
      return { id, loop: loop ?? null };
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
    // A save the studio made itself is already on the page, and has its checks in hand (`save`);
    // anything else reloads the page (it keeps its place) and starts them again.
    async hotUpdate({ file, read }) {
      // A seat a bake added is no change to what anything does, and a reload would lose the save.
      if (path.basename(file) === 'seats.json') return [];
      const isPoses = path.resolve(file) === path.resolve(posesFile);
      const saved = isPoses && (await read()) === own;
      if (!saved && (isPoses || file.endsWith('.mjs'))) {
        checks.forget();
        edited = true;
      }
      if (saved && this.environment.name === 'client') return [];
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
        const fetchIt = /^\/api\/video\/([\w-]+)$/.exec(url.pathname);
        const served = ['GET', 'HEAD'].includes(req.method) && /^\/videos\/([\w-]+\.(?:mp4|webm|mkv))$/.exec(url.pathname);
        const route = routes[`${req.method} ${url.pathname}`];
        if (!clip && !item && !fetchIt && !served && !route) return next();
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
          if (served) {
            await sendVideo(req, res, served[1]);
            return;
          }
          const out = item ? await (item[1] === 'kit' ? kit(item[2]) : exercise(item[2]))
            : fetchIt ? await video(fetchIt[1])
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
