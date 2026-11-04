// The studio's checks (#187). A pose's checks hold the server's one thread while they run, so they
// run one at a time, with the server let in between them, the item on stage first: the list's
// `failing` tags need every pose checked, but nothing has to wait for all of them.

/** The time left between one pose and the next, in milliseconds, for the server to answer in. */
const GAP = 25;
/** How long a plain message keeps the line from the count, in milliseconds, to be read. */
const READ = 4000;

/**
 * A queue of poses to check with `check(id)`, which gives their checks. `ask(ids)` adds poses behind
 * those waiting and gives a promise of them all; `first(id)` moves one to the front and gives a
 * promise of its checks. `found(id)` is what a pose's checks found, or null until they finish, and
 * `progress()` how many of those asked for have. `forget()` starts every pose again, keeping the
 * order, for a change to what they check: a promise still waiting gets the new result. `pause()` keeps
 * the next pose from beginning, for a request that reads more than the gap allows, until the function
 * it gives is called.
 */
export function checkQueue(check) {
  const asked = new Map();
  let waiting = [];
  let current = null;
  let version = 0;
  let running = false;
  let pauses = 0;

  const fresh = () => {
    const entry = { found: null };
    entry.promise = new Promise((resolve) => { entry.settle = resolve; });
    return entry;
  };
  const entryOf = (id) => {
    if (!asked.has(id)) {
      asked.set(id, fresh());
      waiting.push(id);
    }
    return asked.get(id);
  };

  async function run() {
    if (running) return;
    running = true;
    while (waiting.length) {
      // A gap, not just a turn of the event loop: a request reads several files, one after the
      // other, and a pose begun between two of them would hold it up for seconds.
      do await new Promise((next) => { setTimeout(next, GAP); }); while (pauses);
      const id = waiting.shift();
      const began = version;
      current = id;
      const found = await check(id);
      current = null;
      if (began !== version) continue;
      const entry = asked.get(id);
      entry.found = found;
      entry.settle(found);
    }
    running = false;
  }

  return {
    ask(ids) {
      const all = Promise.all(ids.map((id) => entryOf(id).promise));
      run();
      return all;
    },
    first(id) {
      const { promise } = entryOf(id);
      if (waiting.includes(id)) waiting = [id, ...waiting.filter((w) => w !== id)];
      run();
      return promise;
    },
    found: (id) => asked.get(id)?.found ?? null,
    pause() {
      pauses++;
      return () => { pauses--; };
    },
    progress() {
      const all = [...asked.values()];
      return { done: all.filter((e) => e.found).length, total: all.length };
    },
    forget() {
      version++;
      const finished = [...asked.keys()].filter((id) => asked.get(id).found);
      for (const id of finished) asked.set(id, fresh());
      waiting = [...new Set([...(current === null ? [] : [current]), ...waiting, ...finished])];
      run();
    },
  };
}

/** What the message line says of the checks' `progress`: nothing once they have all finished. */
export function progressNote(progress) {
  if (!progress?.total) return 'Loading…';
  return progress.done < progress.total ? `Checking ${progress.done} of ${progress.total}` : '';
}

/**
 * Whether `note` may go on the message line, which says `text` in `tone`, said `age` milliseconds
 * ago, `own` being what the checks last put there. An error stays, as does a save under way; a plain
 * message stays to be read; and when the checks finish they clear only their own count.
 */
export function mayNote({ text, tone, own, age, note }) {
  if (!text || text === own) return true;
  return Boolean(note) && !tone && age > READ;
}

/** The row the cursor goes to when the list is made again: `under` it before, or else the item on stage. -1 for neither. */
export function rowAfter(keys, under, current) {
  const at = keys.indexOf(under);
  return at >= 0 ? at : keys.indexOf(current);
}
