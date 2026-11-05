// The owner's OKs and notes, which the studio writes (README, Reviewing a batch), and whether a
// batch is done:
//
//   node review.mjs status <batch>     how the batch stands, from its file and review/notes.md
//   node review.mjs phone <batch>      the owner has swiped the batch in the phone's debug gallery
//
// An OK names the clip it was given on, so a re-baked clip needs a new one. A batch is done when
// every exercise in it is OK for the clip it has now.
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { CLIPS } from './bake.mjs';
import { clipContent } from './glb.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
/** Where each batch's verdicts are, `<batch>.json`, and the notes, `notes.md`. */
export const REVIEW = path.join(HERE, 'review');

/** The batches poses are made and reviewed in, in order (SPEC, The mannequin). */
export const BATCHES = {
  // The prototype's seven exercises, and the close-grip pulldown that shows `like:`.
  prototype: [
    'barbell_squat', 'barbell_bench_press', 'barbell_rdl', 'lat_pulldown', 'close_grip_lat_pulldown',
    'triceps_pressdown', 'leg_press', 'machine_chest_press',
  ],
  // The adjustable-bench pose, baked at seven angles (#125, #195).
  'adjustable-bench': ['db_incline_press'],
  // The starter program's exercises that had no pose (#127), with the machines and the cable
  // column they needed.
  starter: [
    'close_grip_bench_press', '2_grip_lat_pulldown', 'machine_pulldown', 'chest_supported_machine_row', 'hack_squat',
    'leg_extension', 'seated_leg_curl', 'lying_leg_curl', 'seated_calf_raise', 'machine_preacher_curl', 'bayesian_cable_curl',
    'overhead_cable_triceps_extension', 'cable_lateral_raise', 'cable_shoulder_press', 'cable_flye', 'reverse_cable_crossover',
  ],
  // The library's next barbell exercises (#127). The incline and decline presses and the lunge wait
  // for a later batch (SPEC, Generic equipment).
  barbell: [
    'back_squat_high_bar', 'back_squat_low_bar', 'front_squat', 'barbell_curl', 'barbell_glute_bridge', 'barbell_hip_thrust',
    'barbell_row', 'deficit_pendlay_row', 'barbell_shrug', 'barbell_upright_row', 'deadlift', 'deficit_deadlift', 'floor_press',
  ],
  // Every pose above again, on the kits at their full detail and in their tones (#127).
  kits: [
    'barbell_squat', 'barbell_bench_press', 'barbell_rdl', 'db_incline_press', 'lat_pulldown', 'close_grip_lat_pulldown',
    'triceps_pressdown', 'leg_press', 'machine_chest_press',
  ],
  // The T-bar row machine's three exercises, machine-first (#188).
  't-bar-row': ['chest_supported_t_bar_row', 't_bar_row', 'kelso_shrug'],
  // The hip thrust machine's exercise (#229).
  'hip-thrust': ['machine_hip_thrust'],
  // The power rack's pull-ups, chin-ups, hangs and dips, the feet off the floor (#196).
  'power-rack': [
    'pullup', 'pullup_wide_grip', 'close_grip_pullup', 'neutral_grip_pullup', '2_grip_pullup', 'chinup', 'chinup_wide_grip', 'close_grip_chinup',
    'dead_hang', 'hanging_leg_raise', 'dip', 'close_grip_dip', 'weighted_pullup', 'weighted_chinup', '2_grip_pullup_weighted', 'weighted_dip',
  ],
  // The glute-ham developer's one exercise (#230), kneeling on its pads with the ankles in its rollers.
  'glute-ham-raise': ['glute_ham_raise'],
  // The library's first dumbbell exercises (#127), ahead of the rest of the barbell ones (owner).
  dumbbell: [
    'db_bench_press', 'seated_db_shoulder_press', 'db_bicep_curl', 'db_hammer_curl', 'db_lateral_raise', 'db_flye',
    'db_romanian_deadlift', 'single_arm_db_row', 'db_lunge', 'db_shrug',
  ],
  // The second dumbbell batch (#127): variants of the lifts the owner has OK'd.
  'dumbbell-variants': [
    'super_rom_db_lateral_raise', 'bent_over_reverse_db_flye', 'db_guillotine_press', 'db_skull_crusher',
    'dumbbell_shoulder_press_standing', 'standing_db_arnold_press', 'arnold_press_seated', 'db_walking_lunge',
    'db_curtsy_lunge', '1_arm_db_kroc_row', 'db_triceps_kickback', '1_arm_overhead_db_triceps_extension',
  ],
  // The cable crossover's exercises, the first of two batches (#197): those stood with the back to
  // the towers or beside one, a handle in the hand.
  'cable-crossover': [
    'cable_crossover', 'cable_chest_press', 'low_to_high_cable_press', 'high_cable_lateral_raise', 'cable_y_raise',
    'behind_the_back_cable_lateral_raise', 'cable_shrug_in', '1_arm_cable_press_around', '1_arm_katana_cable_triceps_extension',
  ],
  // The second of the two (#197): those that face a tower, kneel, sit or lift a leg, on a rope, a
  // bar or a cuff.
  'cable-crossover-2': [
    '1_leg_cable_glute_kickback', 'cable_crunch', 'cable_face_pull', 'cable_hip_abduction', 'cable_pull_through',
    'cable_triceps_kickback', 'cable_woodchopper', 'cable_wrist_curl', 'cable_wrist_extension', 'seated_cable_flye',
  ],
};

const idsOf = (name) => {
  const ids = BATCHES[name];
  if (!ids) throw new Error(`No batch "${name}": ${Object.keys(BATCHES).join(', ')}`);
  return ids;
};

/** The batch exercise `id` is reviewed in now: the latest in BATCHES that lists it, or null. */
export const batchReviewing = (id) => Object.keys(BATCHES).findLast((name) => BATCHES[name].includes(id)) ?? null;

/** The index of the clips in `clips`. */
export const readIndex = async (clips) => JSON.parse(await readFile(path.join(clips, 'mannequins.json'), 'utf8'));

/**
 * The files of the clip `id`, from the index in `clips`: its own `clip` (an adjustable bench's
 * typical angle), the clip of each bench angle (`angles`, by degrees, none for another pose), the
 * files they read (`shared`), and every clip file an OK names (`all`).
 */
export async function filesOf(clips, id) {
  const index = await readIndex(clips);
  const entry = index.clips[id];
  const at = (f) => path.join(clips, f);
  const angles = Object.fromEntries(Object.entries(entry.bench?.clips ?? {}).map(([a, c]) => [a, { file: at(c.clip), camera: c.camera }]));
  const shared = (entry.resources ?? []).map(at);
  // An OK covers every clip the exercise has: each bench angle's, or each grip's.
  const all = entry.bench ? Object.values(angles).map((c) => c.file) : entry.grips ? entry.grips.map((g) => at(g.clip)) : [at(entry.clip)];
  return { clip: at(entry.clip), angles, shared, all };
}

/**
 * A clip's fingerprint, which a verdict names: what each of its files shows, with the parts it
 * reads from the files it shares (`clipContent`), so a kit file that grows for another exercise
 * leaves it as it was.
 */
async function hashOf({ all, shared }) {
  const files = Object.fromEntries(await Promise.all(shared.map(async (f) => [path.basename(f), await readFile(f)])));
  const hash = createHash('sha256');
  for (const f of all) hash.update(clipContent(await readFile(f), files));
  return hash.digest('hex').slice(0, 16);
}

/** The fingerprint of the clips exercise `id` has now in `clips`. */
export const fingerprint = async (id, clips = CLIPS) => hashOf(await filesOf(clips, id));

const verdictsFile = (dir, batch) => path.join(dir, `${batch}.json`);
const readVerdicts = (dir, batch) => readFile(verdictsFile(dir, batch), 'utf8').then(JSON.parse, () => ({}));
const sorted = (o) => Object.fromEntries(Object.entries(o).sort(([a], [b]) => (a < b ? -1 : 1)));
const writeVerdicts = (dir, batch, verdicts) => writeFile(verdictsFile(dir, batch), JSON.stringify(sorted(verdicts), null, 2) + '\n');

/**
 * Gives exercise `id` the owner's OK (or takes it back) for the clip it has now, in `batch`'s file,
 * the latest batch that lists it by default. Returns the batch, the OK and the clip's fingerprint.
 */
export async function setOk(id, ok, { dir = REVIEW, clips = CLIPS, batch = batchReviewing(id), at = new Date().toISOString() } = {}) {
  if (!batch) throw new Error(`${id} is in no batch: add it to one in BATCHES (review.mjs) first`);
  const clip = await fingerprint(id, clips);
  const verdicts = await readVerdicts(dir, batch);
  verdicts[id] = { ...verdicts[id], ok, clip, at };
  await writeVerdicts(dir, batch, verdicts);
  return { batch, ok, clip };
}

/**
 * The owner has swiped `batch` in the phone's debug gallery: each exercise in it is phone-checked
 * for the clip it has now. Only a batch that adds or changes a kit or a part needs it.
 */
export async function phoneChecked(batch, { dir = REVIEW, clips = CLIPS } = {}) {
  const verdicts = await readVerdicts(dir, batch);
  for (const id of idsOf(batch)) verdicts[id] = { ...verdicts[id], phone: await fingerprint(id, clips) };
  await writeVerdicts(dir, batch, verdicts);
}

const notesFile = (dir) => path.join(dir, 'notes.md');
const NOTES_HEAD = `# Notes

The owner's notes from the studio, under the exercise or the kit (\`kit:<name>\`) each is on, with
the day and the clip it was left on. Fix what a note asks, then delete it.
`;

/**
 * The open notes in `dir`'s notes.md, by exercise id or `kit:<name>`: each note's text, its lines
 * joined by newlines.
 */
export async function readNotes({ dir = REVIEW } = {}) {
  const text = await readFile(notesFile(dir), 'utf8').catch(() => '');
  const notes = {};
  let at = null;
  for (const line of text.split('\n')) {
    const heading = /^## (\S+)\s*$/.exec(line);
    if (heading) { at = heading[1]; continue; }
    if (!at) continue;
    if (line.startsWith('- ')) (notes[at] ??= []).push(line.slice(2));
    else if (line.startsWith('  ') && notes[at]?.length) notes[at][notes[at].length - 1] += `\n${line.slice(2)}`;
  }
  return notes;
}

/**
 * Adds the owner's note on `item` (an exercise id, or `kit:<name>`) to notes.md, under its heading,
 * with the day and, for a posed exercise, the clip it has now.
 */
export async function addNote(item, note, { dir = REVIEW, clips = CLIPS, at = new Date().toISOString() } = {}) {
  const words = note.trim();
  if (!words) throw new Error('A note needs some words');
  const clip = item.startsWith('kit:') ? null : await fingerprint(item, clips).catch(() => null);
  const bullet = `- ${at.slice(0, 10)}${clip ? `, clip ${clip.slice(0, 8)}` : ''}: ${words.split('\n').join('\n  ')}\n`;
  const text = await readFile(notesFile(dir), 'utf8').catch(() => NOTES_HEAD);
  const heading = new RegExp(`^## ${item.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`, 'm').exec(text);
  let out;
  if (heading) {
    // At the end of its section: before the next heading, or the end of the file.
    const next = text.slice(heading.index + heading[0].length).search(/^## /m);
    const end = next === -1 ? text.length : heading.index + heading[0].length + next;
    const before = text.slice(0, end).replace(/\n*$/, '\n');
    out = `${before}${bullet}${next === -1 ? '' : '\n'}${text.slice(end)}`;
  } else {
    out = `${text.replace(/\n*$/, '\n')}\n## ${item}\n\n${bullet}`;
  }
  await writeFile(notesFile(dir), out);
}

const loopsFile = (dir) => path.join(dir, 'loops.json');

/** A loop as it is kept: each mark, null until it is set (a loop saved before C has none). */
const loopOf = (loop) => ({ a: loop.a ?? null, b: loop.b ?? null, c: loop.c ?? null });

/**
 * Each exercise's loop on its technique video, `{ a, b, c }` in seconds (A the top of the rep, B the
 * bottom, C the top again), which the studio saves so the video reopens on the same rep.
 */
export async function readLoops({ dir = REVIEW } = {}) {
  const loops = await readFile(loopsFile(dir), 'utf8').then(JSON.parse, () => ({}));
  return Object.fromEntries(Object.entries(loops).map(([id, loop]) => [id, loopOf(loop)]));
}

/** Saves exercise `id`'s loop in `dir`'s loops.json, or clears it for a null `loop`. */
export async function setLoop(id, loop, { dir = REVIEW } = {}) {
  const loops = await readLoops({ dir });
  if (loop) loops[id] = loopOf(loop); else delete loops[id];
  await writeFile(loopsFile(dir), JSON.stringify(sorted(loops), null, 2) + '\n');
}

/** Where one exercise stands: from its verdict, the fingerprint of its clip now and its open notes. */
function stateOf(verdict, now, notes) {
  if (verdict?.ok && verdict.clip === now) return verdict.phone === now ? 'phone-checked' : 'ok';
  if (notes.length) return 'notes';
  if (verdict?.ok) return 'changed';
  return 'waiting';
}

/**
 * How each exercise of the batch stands, from the verdicts and the notes in `dir`: `ok` for the
 * clip it has now (`phone-checked` when the batch was swiped on the phone on that clip too),
 * `notes` when it has an open note and no such OK, `changed` when the OK was for an earlier clip,
 * or `waiting`. Each lists its open notes.
 */
export async function batchStatus(batch, { dir = REVIEW, clips = CLIPS } = {}) {
  const verdicts = await readVerdicts(dir, batch);
  const notes = await readNotes({ dir });
  const exercises = [];
  for (const id of idsOf(batch)) {
    const mine = notes[id] ?? [];
    exercises.push({ id, state: stateOf(verdicts[id], await fingerprint(id, clips), mine), notes: mine });
  }
  return { done: exercises.every((e) => e.state === 'ok' || e.state === 'phone-checked'), exercises };
}

/**
 * Where exercise `id` stands in the batch it is reviewed in now (`batchReviewing`), with its open
 * notes (`notes`, from `readNotes`, or read here).
 */
export async function reviewOf(id, { dir = REVIEW, clips = CLIPS, notes } = {}) {
  const batch = batchReviewing(id);
  const verdict = batch ? (await readVerdicts(dir, batch))[id] : undefined;
  const mine = (notes ?? await readNotes({ dir }))[id] ?? [];
  // A pose not baked yet has no clip to be OK for.
  const now = await fingerprint(id, clips).catch(() => null);
  return { batch, state: stateOf(verdict, now, mine), notes: mine };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const [verb, name] = process.argv.slice(2);
  if (verb === 'status' && name) {
    const { done, exercises } = await batchStatus(name);
    for (const e of exercises) console.log(`${e.state.padEnd(13)} ${e.id}${e.notes.map((n) => `\n              - ${n.replace(/\n/g, ' ')}`).join('')}`);
    console.log(done ? `${name} is done` : `${name} isn't done`);
    process.exit(done ? 0 : 1);
  } else if (verb === 'phone' && name) {
    await phoneChecked(name);
    console.log(`${name} is phone-checked on the clips it has now`);
  } else {
    console.error('usage: node review.mjs status <batch> | node review.mjs phone <batch>');
    process.exit(2);
  }
}
