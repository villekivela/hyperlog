// The studio's page (README, The studio): the sidebar of every exercise and kit, the one item on
// stage, posed live with the tool's own modules, and its numbers. Everything is a command from
// keys.json, so a key, a button and the help sheet all do the same thing; the mouse is optional.
// The server (plugin.mjs) prepares each entry and writes what is saved, OK'd or noted.
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import KEYS from './keys.json';
import { helpOf, keymap, tokenOf } from './keys.mjs';
import { FRAMES, buildStage, parseMannequin } from '../stage.mjs';
import { checkStage } from '../checks.mjs';
import { HEADINGS, applyTuned, atAngle, baseAt, headingOf, labelOf, tunables, tunedAngles, valueAt, withTuned } from '../tune.mjs';
import { handlesAt } from '../handles.mjs';
import { tempoOf } from '../tempo.mjs';
import { BENCH_ANGLES } from '../entries.mjs';

const keys = keymap(KEYS);
const $ = (id) => document.getElementById(id);
const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const copy = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)));
const CHECK_NAMES = { hands: 'Hands', feet: 'Feet', equipment: 'Equipment', joints: 'Joints', trace: 'Trace', error: "Can't pose" };
const PANES = ['sidebar', 'stage', 'numbers'];

// What the page keeps across a reload (a change to kit.mjs or poses.mjs reloads it): where it was,
// and each item's numbers not yet saved.
const MEMORY = 'mannequin-studio';
const remembered = (() => { try { return JSON.parse(sessionStorage.getItem(MEMORY)) ?? {}; } catch { return {}; } })();

const state = {
  items: null, // { exercises: [{ equipment, items }], kits }
  rows: [], // the sidebar's items as the filter leaves them
  cursor: 0,
  current: remembered.current ?? null, // `exercise/<id>` or `kit/<name>`
  item: null, // what the server says of it (plugin.mjs)
  tuned: {}, // `{ path: value }`, not yet saved
  unsaved: remembered.unsaved ?? {},
  undo: [],
  redo: [],
  numbers: [],
  selected: remembered.selected ?? null, // a number's path
  focus: remembered.focus ?? 'sidebar',
  show: remembered.show ?? 'loop', // loop, top, bottom or paused
  d: 0, // how far down the rep, when paused
  clip: false, // the baked clip on stage, not the live pose
  angle: null,
  filter: remembered.filter ?? '',
  find: null, // { matches, at }
  input: null, // what the bar or a box is taking: find, ex, type or note
};

function remember() {
  if (state.item) state.unsaved[state.current] = state.tuned;
  for (const [k, v] of Object.entries(state.unsaved)) if (!Object.keys(v).length) delete state.unsaved[k];
  try {
    sessionStorage.setItem(MEMORY, JSON.stringify({
      current: state.current, unsaved: state.unsaved, selected: state.selected, focus: state.focus, show: state.show, filter: state.filter,
    }));
  } catch { /* a private window keeps nothing; the page works without it */ }
}

function message(text, tone = '') {
  $('message').textContent = text;
  $('message').dataset.tone = tone;
}

const api = async (url, body) => {
  const res = await fetch(url, body === undefined ? {} : { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
  const out = await res.json();
  if (!res.ok) throw new Error(out.error ?? res.statusText);
  return out;
};

// ---- The stage ----------------------------------------------------------------------------------

const canvas = $('canvas').querySelector('canvas');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));
const scene = new THREE.Scene();
scene.add(new THREE.HemisphereLight(0xffffff, 0x77777f, 1.7));
const sun = new THREE.DirectionalLight(0xffffff, 1.5);
sun.position.set(2.2, 4, 2.6);
scene.add(sun);
// From behind the kit, wherever the stage is turned: only dark lights it (--clay-rim).
const rim = new THREE.DirectionalLight(0xffffff, 0);
scene.add(rim, rim.target);
const camera = new THREE.PerspectiveCamera(26, 1, 0.05, 50);
const view = { target: new THREE.Vector3(0, 1, 0), distance: 4, azimuth: 62, elevation: 8, framing: null };
const markers = new THREE.Group();
scene.add(markers);
const dot = new THREE.SphereGeometry(1, 16, 12);

let body = null;
let live = null; // { stage, entry }
let clipScene = null;
let mixer = null;
let duration = 1;
let handles = [];

function frame(cam) {
  view.framing = cam;
  view.target.set(...cam.target);
  view.distance = cam.halfHeight / Math.tan(THREE.MathUtils.degToRad(13));
  view.azimuth = cam.azimuth;
  view.elevation = cam.elevation;
  place();
}

function place() {
  const a = THREE.MathUtils.degToRad(view.azimuth);
  const el = THREE.MathUtils.degToRad(Math.min(89.5, view.elevation));
  const t = view.target;
  camera.position.set(t.x + view.distance * Math.cos(el) * Math.sin(a), t.y + view.distance * Math.sin(el), t.z + view.distance * Math.cos(el) * Math.cos(a));
  camera.lookAt(t);
  const back = a + THREE.MathUtils.degToRad(160);
  rim.position.set(t.x + 5 * Math.sin(back), t.y + 3, t.z + 5 * Math.cos(back));
  rim.target.position.copy(t);
}

/** The materials of what is on stage, which the theme colours. */
function materials() {
  const out = new Set();
  for (const root of [live?.stage.scene, clipScene]) root?.traverse((o) => { if (o.material) out.add(o.material); });
  return out;
}

/** Frees what a stage or a clip took on the GPU; the mannequin's body is kept for the next stage. */
function free(root) {
  root.traverse((o) => {
    if (o === body) return;
    if (!o.isMesh || body?.getObjectById(o.id)) return;
    o.geometry?.dispose();
    o.material?.dispose?.();
  });
}

function colour() {
  scene.background = new THREE.Color(css('--stage'));
  rim.intensity = Number(css('--clay-rim')) || 0;
  for (const m of materials()) {
    if (m.name === 'shadow') m.opacity = Number(css('--clay-shadow')) || 0.65;
    else if (css(`--clay-${m.name}`)) m.color.set(css(`--clay-${m.name}`));
  }
}

function setLive(next) {
  if (live) {
    scene.remove(live.stage.scene);
    free(live.stage.scene);
  }
  live = next;
  if (live) {
    scene.add(live.stage.scene);
    colour();
  }
  showWhich();
}

function showWhich() {
  if (live) live.stage.scene.visible = !state.clip;
  if (clipScene) clipScene.visible = state.clip;
  markers.visible = !state.clip;
}

async function loadClip() {
  if (clipScene) {
    scene.remove(clipScene);
    free(clipScene);
  }
  clipScene = null;
  mixer = null;
  const clip = state.item?.clip;
  if (!clip) return;
  const at = clip.angles?.[state.angle] ?? clip;
  // The fingerprint changes with what the clip shows, so a re-baked clip is never one cached.
  const gltf = await new GLTFLoader().loadAsync(`${at.file}?v=${state.item.fingerprint ?? ''}`);
  clipScene = gltf.scene;
  scene.add(clipScene);
  mixer = new THREE.AnimationMixer(clipScene);
  duration = gltf.animations[0].duration;
  mixer.clipAction(gltf.animations[0]).play();
  colour();
  showWhich();
}

/** How far down the rep the stage is now: the entry's own tempo when it loops. */
function along(seconds) {
  if (state.show === 'top') return 0;
  if (state.show === 'bottom') return 1;
  if (state.show === 'paused' || !live) return state.d;
  const { lowering, bottom, lifting, top } = tempoOf(live.entry);
  const p = seconds % (lowering + bottom + lifting + top);
  if (p < lowering) return p / lowering;
  if (p < lowering + bottom) return 1;
  if (p < lowering + bottom + lifting) return 1 - (p - lowering - bottom) / lifting;
  return 0;
}

const started = performance.now();
let loopFrom = 0;
function draw(now) {
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (w && h && (canvas.width !== Math.round(w * renderer.getPixelRatio()) || canvas.height !== Math.round(h * renderer.getPixelRatio()))) {
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  const d = Math.min(along((now - started) / 1000 - loopFrom), 1);
  if (state.show === 'loop') state.d = d;
  if (state.clip && mixer) mixer.setTime(Math.min(d, 0.9999) * duration);
  else if (live) live.stage.pose(d);
  for (const m of markers.children) m.scale.setScalar(camera.position.distanceTo(m.position) * 0.009);
  renderer.render(scene, camera);
  requestAnimationFrame(draw);
}

// ---- Posing live --------------------------------------------------------------------------------

const isKit = () => state.item?.kind === 'kit';
/** The entry as tuned now, at the bench angle shown. */
const entryNow = () => {
  const entry = applyTuned(state.item.entry, state.tuned);
  return entry.bench ? atAngle(entry, state.angle) : entry;
};
const valueNow = (t) => valueAt(entryNow(), t.path) ?? t.value;
// The Per angle settings, `{ path: { angle: value } }`: the tuned ones, else the entry's own. A change
// is kept in `tuned['@angles']` only while it differs from the entry's.
const perOf = (tuned) => tuned['@angles'] ?? state.item.entry.bench?.perAngle ?? {};
const perMap = () => perOf(state.tuned);
function setPer(path, map) {
  const next = copy(perMap());
  if (map === undefined) delete next[path]; else next[path] = map;
  if (same(next, state.item.entry.bench?.perAngle ?? {})) delete state.tuned['@angles']; else state.tuned['@angles'] = next;
}
/** Puts `value` at `path`: at the angle shown for a Per angle setting, everywhere otherwise. */
function put(path, value) {
  const per = perMap()[path];
  if (per) setPer(path, { ...per, [state.angle]: value });
  else state.tuned = withTuned(state.item.entry, state.tuned, path, value);
}

let building = false;
let again = false;
async function rebuild() {
  if (!state.item?.entry) { setLive(null); return; }
  if (building) { again = true; return; }
  building = true;
  try {
    if (!body) {
      const bytes = await (await fetch('/model/mannequin.glb')).arrayBuffer();
      body = await parseMannequin(bytes);
    }
    const entry = entryNow();
    const stage = await buildStage(entry, body);
    // A kit is shown alone, as its exercise sets it up.
    body.visible = !isKit();
    setLive({ stage, entry });
    showHandles();
    checkSoon();
  } catch (err) {
    setLive(null);
    message(`Can't pose this: ${err.message}`, 'error');
  } finally {
    building = false;
    if (again) { again = false; rebuild(); }
  }
}

let checkTimer = 0;
function checkSoon() {
  clearTimeout(checkTimer);
  if (!live || isKit()) { paintChecks(isKit() ? null : state.item?.checks); return; }
  $('checks').dataset.checking = 'true';
  checkTimer = setTimeout(() => {
    const checks = checkStage(live.entry, live.stage);
    $('checks').dataset.checking = 'false';
    paintChecks(checks);
  }, 250);
}

function paintChecks(checks) {
  const list = $('checks');
  list.replaceChildren();
  if (!checks) return;
  for (const [key, c] of Object.entries(checks)) {
    const li = document.createElement('li');
    li.dataset.ok = String(c.ok);
    li.innerHTML = '<span class="mark" aria-hidden="true"></span><span class="name"></span><span class="found"></span>';
    li.querySelector('.name').textContent = `${CHECK_NAMES[key] ?? key}${c.ok ? '' : ' failed'}`;
    li.querySelector('.found').textContent = c.frame === null ? c.found : `frame ${c.frame}: ${c.found}`;
    li.title = li.textContent;
    list.append(li);
  }
}

// The handles of the body held at the top or the bottom; a drag sets the same numbers the keys do.
function showHandles() {
  markers.clear();
  handles = [];
  const held = state.show === 'top' || state.show === 'bottom';
  if (live && held && !isKit() && !state.clip) {
    const d = state.show === 'top' ? 0 : 1;
    const P = live.stage.pose(d);
    handles = handlesAt(live.entry, live.stage, P, d);
    for (const h of handles) {
      const m = new THREE.Mesh(dot, new THREE.MeshBasicMaterial({ color: css('--handle'), depthTest: false, transparent: true, opacity: 0.92 }));
      m.renderOrder = 10;
      m.position.copy(h.at);
      markers.add(m);
    }
  }
  $('hint').textContent = handles.length ? 'drag a dot to pose it, or drag to turn' : 'drag to turn';
}

function dragTo(h, point) {
  for (const [path, value] of Object.entries(h.drag(point))) {
    // A point's numbers that are Per angle change at the angle shown; the others as ever.
    const leaves = Array.isArray(value) ? value.map((v, i) => [`${path}.${i}`, v]) : [];
    if (leaves.some(([leaf]) => perMap()[leaf])) {
      const rest = copy(state.tuned[path] ?? baseAt(state.item.entry, path));
      leaves.forEach(([leaf, v], i) => { if (perMap()[leaf]) setPer(leaf, { ...perMap()[leaf], [state.angle]: v }); else rest[i] = v; });
      state.tuned = withTuned(state.item.entry, state.tuned, path, rest);
    } else {
      put(path, value);
    }
  }
  changed();
}

// The stage turns on a sideways drag, and a dot drags its pose numbers.
let turning = null;
let dragging = null;
const ray = new THREE.Raycaster();
const holder = $('canvas');
holder.addEventListener('pointerdown', (ev) => {
  setFocus('stage');
  holder.setPointerCapture(ev.pointerId);
  const r = canvas.getBoundingClientRect();
  let best = null;
  let nearest = 28;
  for (const h of handles) {
    const p = h.at.clone().project(camera);
    const d = Math.hypot(((p.x + 1) / 2) * r.width - (ev.clientX - r.left), ((1 - p.y) / 2) * r.height - (ev.clientY - r.top));
    if (d < nearest) { nearest = d; best = h; }
  }
  if (best) {
    snapshot();
    dragging = { id: best.id, plane: new THREE.Plane().setFromNormalAndCoplanarPoint(camera.getWorldDirection(new THREE.Vector3()), best.at) };
    $('hint').textContent = `dragging the ${best.label}`;
    ev.preventDefault();
    return;
  }
  turning = ev.clientX;
});
holder.addEventListener('pointermove', (ev) => {
  if (dragging) {
    const r = canvas.getBoundingClientRect();
    ray.setFromCamera(new THREE.Vector2(((ev.clientX - r.left) / r.width) * 2 - 1, -((ev.clientY - r.top) / r.height) * 2 + 1), camera);
    const point = ray.ray.intersectPlane(dragging.plane, new THREE.Vector3());
    const h = handles.find((x) => x.id === dragging.id);
    if (point && h) dragTo(h, point);
    return;
  }
  if (turning === null) return;
  view.azimuth -= (ev.clientX - turning) * 0.5;
  turning = ev.clientX;
  place();
});
const endDrag = () => { turning = null; dragging = null; };
holder.addEventListener('pointerup', endDrag);
holder.addEventListener('pointercancel', endDrag);

// ---- The sidebar --------------------------------------------------------------------------------

/** A sidebar row's item, as `openItem` takes it. */
const rowKey = (row) => `${row.kind}/${row.id}`;

function allRows() {
  if (!state.items) return [];
  return [
    ...state.items.exercises.flatMap((g) => g.items.map((e) => ({ ...e, kind: 'exercise', group: g.equipment.replace(/_/g, ' ') }))),
    ...state.items.kits.map((k) => ({ ...k, kind: 'kit', group: 'kits', equipment: 'kit' })),
  ];
}

/** The rows the filter leaves: every word of it in the name, the id, the status or the equipment. */
function filtered() {
  const words = state.filter.toLowerCase().split(/\s+/).filter(Boolean);
  return allRows().filter((r) => {
    const text = `${r.name} ${r.id} ${r.status} ${r.equipment} ${r.notes ? 'notes' : ''}`.toLowerCase().replace(/_/g, ' ');
    return words.every((w) => text.includes(w.replace(/_/g, ' ')));
  });
}

function paintList() {
  state.rows = filtered();
  const list = $('list');
  list.replaceChildren();
  let group = null;
  state.rows.forEach((r, i) => {
    if (r.group !== group) {
      group = r.group;
      const g = document.createElement('div');
      g.className = 'group';
      g.textContent = group;
      list.append(g);
    }
    const row = document.createElement('div');
    row.className = 'row';
    row.setAttribute('role', 'option');
    row.dataset.key = rowKey(r);
    row.dataset.cursor = String(i === state.cursor);
    row.dataset.current = String(rowKey(r) === state.current);
    row.setAttribute('aria-selected', String(i === state.cursor));
    row.innerHTML = '<span class="name"></span><span class="status"></span>';
    row.querySelector('.name').textContent = r.name.replace(/_/g, ' ');
    row.querySelector('.name').title = r.kind === 'kit' ? (r.uses ? `as in ${r.uses}` : 'no pose uses it') : r.id;
    const status = row.querySelector('.status');
    status.dataset.status = r.checking ? 'checking' : r.status;
    status.textContent = r.checking ? 'checking' : r.status;
    row.addEventListener('click', () => { setFocus('sidebar'); moveCursor(i); });
    list.append(row);
  });
  const total = allRows().length;
  $('tally').textContent = state.rows.length === total ? `${total}` : `${state.rows.length} of ${total}`;
  list.querySelector('[data-cursor="true"]')?.scrollIntoView({ block: 'nearest' });
}

let openTimer = 0;
/** Moves the sidebar's cursor to row `i`, and puts that item on stage once the keys settle. */
function moveCursor(i) {
  if (!state.rows.length) return;
  state.cursor = Math.max(0, Math.min(state.rows.length - 1, i));
  paintList();
  clearTimeout(openTimer);
  openTimer = setTimeout(() => openItem(rowKey(state.rows[state.cursor])), 120);
}

async function loadItems(wait = false) {
  state.items = await api(`/api/items${wait ? '?wait=1' : ''}`);
  paintList();
  const at = state.rows.findIndex((r) => rowKey(r) === state.current);
  if (at >= 0 && at !== state.cursor) { state.cursor = at; paintList(); }
}

// ---- The item on stage --------------------------------------------------------------------------

async function openItem(key, { keepTuned = false } = {}) {
  if (!key) return;
  if (state.item && key !== state.current) remember();
  const [kind, id] = key.split('/');
  let item;
  try {
    item = await api(`/api/item/${kind}/${id}`);
  } catch (err) {
    message(err.message, 'error');
    return;
  }
  // Another item asked for since this one.
  if (state.rows[state.cursor] && rowKey(state.rows[state.cursor]) !== key && state.current !== key) return;
  const fresh = key !== state.current || !state.item;
  state.current = key;
  state.item = item;
  if (!keepTuned) {
    state.tuned = state.unsaved[key] ?? {};
    if (fresh) { state.undo = []; state.redo = []; }
  }
  state.angle = item.entry?.bench ? (state.angle !== null && fresh === false ? state.angle : item.entry.bench.angle) : null;
  state.clip = false;
  state.numbers = item.entry && !isKit() ? tunables(item.entry) : [];
  if (!state.numbers.some((t) => t.path === state.selected)) state.selected = onScreen()[0]?.path ?? null;
  if (fresh && item.entry) frame(cameraAtAngle());
  if (fresh && !item.entry) setLive(null);
  paintItem();
  paintNumbers();
  paintList();
  remember();
  if (item.entry) {
    $('empty').hidden = true;
    loadClip().catch(() => {});
    rebuild();
  } else {
    $('empty').hidden = false;
    $('empty').textContent = item.kind === 'kit' ? 'No pose uses this kit yet.' : 'No pose yet: write its entry in poses.mjs, and it appears here.';
    paintChecks(null);
  }
}

function cameraAtAngle() {
  const at = state.item.clip?.angles?.[state.angle];
  return at?.camera ?? state.item.entry.camera ?? { target: [0, 1, 0], halfHeight: 1.1, azimuth: 62, elevation: 8 };
}

function paintItem() {
  const item = state.item;
  $('name').textContent = item.name.replace(/_/g, ' ');
  const meta = item.kind === 'kit' ? [`kit`, item.uses ? `as in ${item.uses}` : 'unused']
    : [item.equipment.replace(/_/g, ' '), item.id, item.batch ? `batch ${item.batch}` : 'in no batch', item.grips ? `grip ${item.grips[0]} of ${item.grips.join(', ')}` : null];
  $('meta').textContent = meta.filter(Boolean).join(' · ');
  $('status').hidden = false;
  $('status').dataset.status = item.status;
  $('status').textContent = item.status;
  const notes = $('notes');
  notes.replaceChildren(...(item.notes ?? []).map((n) => Object.assign(document.createElement('li'), { textContent: n })));
  for (const el of document.querySelectorAll('[data-bench]')) el.hidden = state.angle === null;
  paintAngle();
  paintCommands();
}

function paintAngle() {
  if (state.angle === null) return;
  const known = new Set(tunedAngles({ bench: { perAngle: perOf(state.tuned) } }));
  $('angle').textContent = `${state.angle}°${known.has(state.angle) ? ' •' : ''}`;
}

// ---- The numbers --------------------------------------------------------------------------------

const UNITS = {
  ' m': { show: (v) => `${(v * 1000).toFixed(1)} mm`, scale: 1000, step: (s) => `${s * 1000} mm` },
  '°': { show: (v) => `${v.toFixed(1)}°`, scale: 1, step: (s) => `${s}°` },
  '': { show: (v) => v.toFixed(3), scale: 1, step: (s) => `${s}` },
};
const unitOf = (t) => UNITS[t.unit] ?? UNITS[''];
const selectedNumber = () => state.numbers.find((t) => t.path === state.selected);

function paintNumbers() {
  const box = $('sliders');
  box.replaceChildren();
  if (!state.item?.entry || isKit()) {
    box.innerHTML = `<p style="padding: 4px 12px; color: var(--quiet)">${isKit() ? 'A kit takes its numbers from the entry that uses it: tune it there.' : 'Nothing to tune without a pose.'}</p>`;
    paintStep();
    return;
  }
  for (const h of HEADINGS) {
    const items = state.numbers.filter((t) => headingOf(t.path).name === h.name);
    if (!items.length) continue;
    const heading = document.createElement('div');
    heading.className = 'heading';
    heading.textContent = h.name;
    box.append(heading);
    for (const t of items) {
      const row = document.createElement('div');
      row.className = 'num';
      row.dataset.path = t.path;
      row.innerHTML = '<label></label><span class="value"></span><input type="range" tabindex="-1">';
      const [label, , range] = row.children;
      label.textContent = labelOf(t.path);
      label.title = `${t.path} (${t.unit.trim() || 'ratio'})`;
      range.min = t.min;
      range.max = t.max;
      range.step = 'any';
      range.setAttribute('aria-label', t.path);
      range.addEventListener('pointerdown', () => snapshot());
      range.addEventListener('input', () => { put(t.path, Number(Number(range.value).toFixed(4))); changed(); });
      row.addEventListener('click', () => { setFocus('numbers'); select(t.path); });
      box.append(row);
    }
  }
  paintValues();
}

/** The numbers' values, marks and selection, without rebuilding the rows. */
function paintValues() {
  for (const row of $('sliders').querySelectorAll('.num')) {
    const t = state.numbers.find((x) => x.path === row.dataset.path);
    const v = valueNow(t);
    const per = perMap()[t.path];
    row.querySelector('.value').textContent = unitOf(t).show(v);
    const range = row.querySelector('input[type="range"]');
    range.min = Math.min(t.min, v);
    range.max = Math.max(t.max, v);
    range.value = v;
    row.dataset.changed = String(per ? !same(per, state.item.entry.bench?.perAngle?.[t.path]) : v !== baseAt(state.item.entry, t.path) && v !== t.value);
    row.dataset.per = String(Boolean(per));
    row.dataset.selected = String(t.path === state.selected);
  }
  const n = Object.keys(state.tuned).filter((k) => k !== '@angles').length + Object.keys(state.tuned['@angles'] ?? {}).length;
  $('changed').textContent = n ? `${n} unsaved` : '';
  paintStep();
  paintAngle();
}

function paintStep() {
  const t = selectedNumber();
  const step = $('step');
  if (!t) { step.textContent = ''; return; }
  const u = unitOf(t);
  step.innerHTML = '';
  const add = (html) => step.insertAdjacentHTML('beforeend', html);
  add(`<strong></strong>`);
  step.querySelector('strong').textContent = t.path;
  add(`<span><kbd>${keys.keyOf('less')}</kbd><kbd>${keys.keyOf('more')}</kbd> ${u.step(t.step)}</span>`);
  add(`<span><kbd>${keys.keyOf('lessFine')}</kbd><kbd>${keys.keyOf('moreFine')}</kbd> ${u.step(Number((t.step / 10).toPrecision(3)))}</span>`);
  add(`<span><kbd>${keys.keyOf('type')}</kbd> type</span><span><kbd>${keys.keyOf('reset')}</kbd> reset</span>`);
  if (state.angle !== null) add(`<span><kbd>${keys.keyOf('perAngle')}</kbd> per angle</span>`);
}

function select(path) {
  state.selected = path;
  paintValues();
  $('sliders').querySelector(`[data-path="${CSS.escape(path)}"]`)?.scrollIntoView({ block: 'nearest' });
  remember();
}

/** The numbers in their order on screen: by heading. */
const onScreen = () => HEADINGS.flatMap((h) => state.numbers.filter((t) => headingOf(t.path).name === h.name));

function moveSelection(by, absolute) {
  if (!state.numbers.length) return;
  const order = onScreen();
  const at = order.findIndex((t) => t.path === state.selected);
  const i = absolute ?? at + by;
  select(order[Math.max(0, Math.min(order.length - 1, i))].path);
}

/** Before a change: what to undo back to. */
function snapshot() {
  state.undo.push(copy(state.tuned));
  state.redo = [];
}

let rebuildFrame = 0;
/** After a change: paint, remember, and pose again on the next frame. */
function changed() {
  paintValues();
  remember();
  state.clip = false;
  showWhich();
  if (!rebuildFrame) rebuildFrame = requestAnimationFrame(() => { rebuildFrame = 0; rebuild(); });
}

function nudge(direction, fine, count) {
  const t = selectedNumber();
  if (!t || !state.item?.entry) return;
  snapshot();
  const v = valueNow(t) + direction * t.step * (fine ? 0.1 : 1) * count;
  put(t.path, Number(v.toFixed(5)));
  changed();
}

// ---- Commands -----------------------------------------------------------------------------------

function setFocus(pane) {
  state.focus = pane;
  for (const p of PANES) $(p).dataset.focused = String(p === pane);
  remember();
}

function setShow(show) {
  if (show === 'loop' && state.show !== 'loop') loopFrom = (performance.now() - started) / 1000 - loopOffset(state.d);
  state.show = show;
  showHandles();
  paintCommands();
  remember();
}
/** Where in the loop the rep is `d` down, on the way down: so play resumes from where it was paused. */
function loopOffset(d) {
  return live ? d * tempoOf(live.entry).lowering : 0;
}

function stepFrame(by) {
  if (state.show === 'loop' || state.show === 'top' || state.show === 'bottom') state.d = state.show === 'top' ? 0 : state.show === 'bottom' ? 1 : state.d;
  state.d = Math.max(0, Math.min(1, Math.round(state.d * (FRAMES - 1) + by) / (FRAMES - 1)));
  setShow('paused');
  message(`frame ${Math.round(state.d * (FRAMES - 1))} of ${FRAMES - 1}`);
}

function openBar(kind, prompt) {
  state.input = kind;
  $('prompt').textContent = prompt;
  const line = $('line');
  line.hidden = false;
  line.value = '';
  line.focus();
  $('matches').textContent = '';
}
function closeBar() {
  state.input = null;
  $('prompt').textContent = '';
  $('line').hidden = true;
  $('line').blur();
  $('matches').textContent = '';
  paintHints();
}

/** How well `query` matches `text`, its letters in order: lower is better, null if not. */
function fuzzy(query, text) {
  const q = query.toLowerCase().replace(/\s+/g, '');
  const s = text.toLowerCase();
  let at = -1;
  let score = 0;
  for (const c of q) {
    const next = s.indexOf(c, at + 1);
    if (next === -1) return null;
    score += next - at - 1;
    at = next;
  }
  return score + (s.startsWith(q[0]) ? 0 : 1);
}

function findMatches(query) {
  if (!query.trim()) return [];
  const out = [];
  for (const r of allRows()) {
    const score = Math.min(fuzzy(query, r.name) ?? Infinity, fuzzy(query, r.id) ?? Infinity);
    if (score < Infinity) out.push({ score, kind: 'item', key: rowKey(r), label: r.name });
  }
  for (const t of state.numbers) {
    const score = Math.min(fuzzy(query, t.path) ?? Infinity, fuzzy(query, labelOf(t.path)) ?? Infinity);
    if (score < Infinity) out.push({ score: score - 0.5, kind: 'number', path: t.path, label: t.path });
  }
  return out.sort((a, b) => a.score - b.score).slice(0, 40);
}

function jump(match) {
  if (match.kind === 'number') {
    setFocus('numbers');
    select(match.path);
    return;
  }
  let i = state.rows.findIndex((r) => rowKey(r) === match.key);
  if (i === -1) {
    // A filter hides it: let the find through.
    state.filter = '';
    $('filter').value = '';
    paintList();
    i = state.rows.findIndex((r) => rowKey(r) === match.key);
  }
  setFocus('sidebar');
  moveCursor(i);
}

function findStep(by) {
  const f = state.find;
  if (!f?.matches.length) { message('Nothing found yet: / to find'); return; }
  f.at = (f.at + by + f.matches.length) % f.matches.length;
  jump(f.matches[f.at]);
  message(`match ${f.at + 1} of ${f.matches.length}: ${f.matches[f.at].label}`);
}

const dirty = () => Object.keys(state.tuned).length > 0;

async function save() {
  const item = state.item;
  if (!item?.entry || isKit()) { message('Nothing to save here'); return; }
  if (!dirty()) { message('Nothing changed to save'); return; }
  message(`Saving ${item.id} into poses.mjs and baking…`, 'busy');
  try {
    const { clip } = await api('/api/save', { id: item.id, tuned: state.tuned });
    delete state.unsaved[state.current];
    state.tuned = {};
    state.undo = [];
    state.redo = [];
    await openItem(state.current, { keepTuned: true });
    // The new clip, as it will ship.
    await loadClip();
    state.clip = true;
    showWhich();
    paintCommands();
    message(`Saved and baked ${item.id}: clip ${clip.slice(0, 8)}, on stage now (${keys.keyOf('clip')} for the live pose)`);
    loadItems(true);
  } catch (err) {
    message(err.message, 'error');
  }
}

async function ok() {
  const item = state.item;
  if (!item || isKit()) { message('Only an exercise is OK\'d'); return; }
  if (!item.entry) { message('No pose to OK'); return; }
  if (dirty()) { message(`Save first (${keys.keyOf('save')}): an OK is for the baked clip`, 'error'); return; }
  const isOk = item.status === 'ok' || item.status === 'phone-checked';
  try {
    const { batch } = await api('/api/ok', { id: item.id, ok: !isOk });
    message(isOk ? `OK taken back (${batch})` : `OK for this clip, in review/${batch}.json`);
    await openItem(state.current, { keepTuned: true });
    loadItems(true);
  } catch (err) {
    message(err.message, 'error');
  }
}

function openNote() {
  if (!state.item) return;
  state.input = 'note';
  const box = $('note');
  box.hidden = false;
  box.value = '';
  box.focus();
  paintHints();
}
async function saveNote() {
  const box = $('note');
  const text = box.value.trim();
  closeNote();
  if (!text) return;
  const item = state.item.kind === 'kit' ? `kit:${state.item.id}` : state.item.id;
  try {
    await api('/api/note', { item, text });
    message(`Note added to review/notes.md under ${item}`);
    await openItem(state.current, { keepTuned: true });
    loadItems(true);
  } catch (err) {
    message(err.message, 'error');
  }
}
function closeNote() {
  state.input = null;
  $('note').hidden = true;
  $('note').blur();
  paintHints();
}

function typeValue() {
  const t = selectedNumber();
  const row = $('sliders').querySelector(`[data-path="${CSS.escape(state.selected ?? '')}"]`);
  if (!t || !row) return;
  setFocus('numbers');
  state.input = 'type';
  const u = unitOf(t);
  const box = document.createElement('input');
  box.className = 'typing';
  box.value = Number((valueNow(t) * u.scale).toFixed(4));
  box.setAttribute('aria-label', `${t.path}, in ${t.unit.trim() || 'ratio'}${u.scale === 1000 ? ' as mm' : ''}`);
  row.querySelector('.value').hidden = true;
  row.append(box);
  box.focus();
  box.select();
  const done = (set) => {
    state.input = null;
    box.remove();
    row.querySelector('.value').hidden = false;
    if (set) {
      // `12.5` is the value; `+2` (or `+-2`) moves it from where it is.
      const text = box.value.trim();
      const relative = text.startsWith('+');
      const n = Number(relative ? text.slice(1) : text);
      if (!Number.isFinite(n) || text === '') { message(`Not a number: ${text}`, 'error'); return; }
      snapshot();
      put(t.path, Number(((relative ? valueNow(t) : 0) + n / u.scale).toFixed(5)));
      changed();
    }
    paintHints();
  };
  box.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter') { ev.preventDefault(); done(true); }
    if (ev.key === 'Escape') { ev.preventDefault(); done(false); }
    ev.stopPropagation();
  });
  box.addEventListener('blur', () => { if (state.input === 'type') done(false); });
  paintHints('Enter sets the value, Esc cancels; +2 moves it by 2');
}

function reset() {
  const t = selectedNumber();
  if (!t) return;
  snapshot();
  if (perMap()[t.path]) setPer(t.path, copy(state.item.entry.bench?.perAngle?.[t.path]));
  if (!perMap()[t.path]) state.tuned = withTuned(state.item.entry, state.tuned, t.path, baseAt(state.item.entry, t.path));
  changed();
}

function perAngle() {
  const t = selectedNumber();
  if (!t || state.angle === null) { message('Per angle is for an adjustable bench\'s numbers'); return; }
  snapshot();
  const v = valueNow(t);
  // Per angle starts at the value shown, at the angle shown; back to every angle keeps that value.
  if (perMap()[t.path]) { setPer(t.path, undefined); put(t.path, v); } else setPer(t.path, { [state.angle]: v });
  changed();
}

function history(from, to, count) {
  let n = 0;
  while (n < count && from.length) {
    to.push(copy(state.tuned));
    state.tuned = from.pop();
    n++;
  }
  if (!n) { message('Nothing to undo or redo'); return; }
  changed();
}

function turnAngle(by) {
  if (state.angle === null) { message('Only an adjustable bench has angles'); return; }
  const i = BENCH_ANGLES.indexOf(state.angle);
  const next = BENCH_ANGLES[Math.max(0, Math.min(BENCH_ANGLES.length - 1, (i === -1 ? 0 : i) + by))];
  if (next === state.angle) return;
  state.angle = next;
  frame(cameraAtAngle());
  paintValues();
  loadClip().catch(() => {});
  rebuild();
}

function turnTo(azimuth, elevation) {
  view.azimuth = azimuth;
  view.elevation = elevation;
  place();
}

const COMMANDS = {
  down: (n) => (state.focus === 'numbers' ? moveSelection(n) : moveCursor(state.cursor + n)),
  up: (n) => (state.focus === 'numbers' ? moveSelection(-n) : moveCursor(state.cursor - n)),
  first: () => (state.focus === 'numbers' ? moveSelection(0, 0) : moveCursor(0)),
  last: (n, counted) => (state.focus === 'numbers' ? moveSelection(0, counted ? n - 1 : Infinity) : moveCursor(counted ? n - 1 : state.rows.length - 1)),
  focusNext: () => setFocus(PANES[(PANES.indexOf(state.focus) + 1) % PANES.length]),
  focusPrevious: () => setFocus(PANES[(PANES.indexOf(state.focus) + PANES.length - 1) % PANES.length]),
  find: () => openBar('find', '/'),
  findNext: (n) => findStep(n),
  findPrevious: (n) => findStep(-n),
  filter: () => { state.input = 'filter'; $('filter').focus(); $('filter').select(); paintHints(); },
  holdTop: () => setShow('top'),
  holdBottom: () => setShow('bottom'),
  play: () => setShow(state.show === 'loop' ? 'paused' : 'loop'),
  frameBack: (n) => stepFrame(-n),
  frameOn: (n) => stepFrame(n),
  clip: () => {
    if (!state.item?.clip) { message('No baked clip for this yet'); return; }
    state.clip = !state.clip;
    showWhich();
    showHandles();
    paintCommands();
    message(state.clip ? 'The baked clip, as it ships' : 'Posed live');
  },
  anglePrevious: () => turnAngle(-1),
  angleNext: () => turnAngle(1),
  less: (n) => nudge(-1, false, n),
  more: (n) => nudge(1, false, n),
  lessFine: (n) => nudge(-1, true, n),
  moreFine: (n) => nudge(1, true, n),
  type: () => typeValue(),
  reset: () => reset(),
  perAngle: () => perAngle(),
  undo: (n) => history(state.undo, state.redo, n),
  redo: (n) => history(state.redo, state.undo, n),
  cameraFront: () => turnTo(0, 6),
  cameraSide: () => turnTo(90, 6),
  cameraTop: () => turnTo(view.azimuth, 89.5),
  turnLeft: (n) => { view.azimuth += 10 * n; place(); },
  turnRight: (n) => { view.azimuth -= 10 * n; place(); },
  ok: () => ok(),
  note: () => openNote(),
  help: () => openHelp(),
  save: () => save(),
  ex: () => openBar('ex', ':'),
};

function run(command, count) {
  const fn = COMMANDS[command];
  if (!fn) return;
  fn(count ?? 1, count !== null && count !== undefined);
}

// Every control runs its command, and shows its key beside it.
for (const el of document.querySelectorAll('[data-command]')) {
  const key = keys.keyOf(el.dataset.command);
  if (key) el.insertAdjacentHTML('beforeend', ` <kbd></kbd>`), (el.querySelector('kbd').textContent = key);
  el.title = KEYS.groups.flatMap((g) => g.keys).find((b) => b.command === el.dataset.command)?.does ?? KEYS.ex.find((c) => c.command === el.dataset.command)?.does ?? '';
  el.addEventListener('click', (ev) => { ev.currentTarget.blur(); run(el.dataset.command, null); });
}

// A list or a field shows the key that moves through it or opens it.
for (const el of document.querySelectorAll('[data-key]')) el.textContent = keys.keyOf(el.dataset.key);

function paintCommands() {
  const pressed = { holdTop: state.show === 'top', holdBottom: state.show === 'bottom', play: state.show === 'loop', clip: state.clip };
  for (const el of document.querySelectorAll('[data-command]')) {
    if (el.dataset.command in pressed) el.setAttribute('aria-pressed', String(pressed[el.dataset.command]));
  }
  const kit = isKit();
  for (const c of ['save', 'undo', 'redo', 'reset', 'ok']) {
    const el = document.querySelector(`[data-command="${c}"]`);
    if (el) el.disabled = kit || !state.item?.entry;
  }
}

function paintHints(text) {
  const hints = $('keys');
  hints.replaceChildren();
  if (text) { hints.textContent = text; return; }
  for (const c of ['find', 'filter', 'focusNext', 'ex', 'help']) {
    const b = KEYS.groups.flatMap((g) => g.keys).find((x) => x.command === c);
    const span = document.createElement('span');
    span.innerHTML = '<kbd></kbd> ';
    span.querySelector('kbd').textContent = c === 'ex' ? ':' : keys.keyOf(c);
    span.append(c === 'ex' ? 'command' : b.does.split(':')[0].replace(/ an exercise.*$/, '').toLowerCase());
    hints.append(span);
  }
}

function openHelp() {
  const sheet = $('sheet');
  sheet.replaceChildren();
  for (const g of helpOf(KEYS)) {
    const section = document.createElement('section');
    section.innerHTML = '<h3></h3><dl></dl>';
    section.querySelector('h3').textContent = g.name;
    const dl = section.querySelector('dl');
    for (const r of g.rows) {
      const dt = document.createElement('dt');
      dt.innerHTML = '<kbd></kbd>';
      dt.querySelector('kbd').textContent = r.label;
      const dd = document.createElement('dd');
      dd.textContent = r.does;
      dl.append(dt, dd);
    }
    sheet.append(section);
  }
  $('help').showModal();
}

// ---- The keyboard -------------------------------------------------------------------------------

$('line').addEventListener('input', () => {
  if (state.input !== 'find') return;
  const matches = findMatches($('line').value);
  $('matches').textContent = matches.length ? matches.slice(0, 6).map((m) => m.label).join(' · ') + (matches.length > 6 ? ` · +${matches.length - 6}` : '') : 'no match';
});
$('line').addEventListener('keydown', (ev) => {
  ev.stopPropagation();
  if (ev.key === 'Escape') { ev.preventDefault(); closeBar(); return; }
  if (ev.key !== 'Enter') return;
  ev.preventDefault();
  const text = $('line').value;
  const kind = state.input;
  closeBar();
  if (kind === 'find') {
    const matches = findMatches(text);
    if (!matches.length) { message(`Nothing matches ${text}`, 'error'); return; }
    state.find = { matches, at: 0 };
    jump(matches[0]);
    message(`match 1 of ${matches.length}: ${matches[0].label} (${keys.keyOf('findNext')} next, ${keys.keyOf('findPrevious')} previous)`);
  } else if (kind === 'ex') {
    const command = keys.ex(text);
    if (command) run(command, null); else message(`Not a command: :${text}`, 'error');
  }
});
$('filter').addEventListener('input', () => {
  state.filter = $('filter').value;
  state.cursor = 0;
  paintList();
  remember();
});
$('filter').addEventListener('keydown', (ev) => {
  ev.stopPropagation();
  if (ev.key === 'Escape' || ev.key === 'Enter') {
    ev.preventDefault();
    if (ev.key === 'Escape') { state.filter = ''; $('filter').value = ''; paintList(); remember(); }
    state.input = null;
    $('filter').blur();
    setFocus('sidebar');
    paintHints();
  }
});
$('filter').addEventListener('focus', () => { state.input = 'filter'; });
$('filter').addEventListener('blur', () => { if (state.input === 'filter') state.input = null; });
$('note').addEventListener('keydown', (ev) => {
  ev.stopPropagation();
  if (ev.key === 'Escape') { ev.preventDefault(); closeNote(); }
  if (ev.key === 'Enter' && !ev.shiftKey) { ev.preventDefault(); saveNote(); }
});
$('help').addEventListener('keydown', (ev) => {
  if (ev.key === 'Escape' || ev.key === '?' || ev.key === 'q') { ev.preventDefault(); $('help').close(); }
  ev.stopPropagation();
});
for (const p of PANES) $(p).addEventListener('pointerdown', () => setFocus(p));

document.addEventListener('keydown', (ev) => {
  if (state.input || ev.isComposing || ev.metaKey) return;
  if (ev.key === 'Escape') { keys.reset(); $('pending').textContent = ''; return; }
  const token = tokenOf(ev);
  if (!token) return;
  const done = keys.feed(token);
  $('pending').textContent = keys.pending;
  if (done === null || done.command) ev.preventDefault();
  if (done?.command) run(done.command, done.count);
});

// ---- Start --------------------------------------------------------------------------------------

matchMedia('(prefers-color-scheme: dark)').addEventListener('change', colour);
new MutationObserver(colour).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
setFocus(state.focus);
paintHints();
paintCommands();
$('filter').value = state.filter;
colour();
place();
requestAnimationFrame(draw);

await loadItems().catch((err) => message(`The studio's server didn't answer: ${err.message}`, 'error'));
const first = state.current ?? (state.rows[0] && rowKey(state.rows[0]));
if (first) {
  state.cursor = Math.max(0, state.rows.findIndex((r) => rowKey(r) === first));
  paintList();
  await openItem(first);
}
// The checks of every pose, which the first list didn't wait for.
loadItems(true).catch(() => {});
// A save still baking when the page reloaded.
const pending = await api('/api/state').catch(() => null);
if (pending?.baking) {
  message(`Baking ${pending.baking}…`, 'busy');
  const poll = setInterval(async () => {
    const s = await api('/api/state').catch(() => null);
    if (s && !s.baking) {
      clearInterval(poll);
      message(s.last?.ok ? `Baked ${s.last.id}` : `The bake failed: ${s.last?.error}`, s.last?.ok ? '' : 'error');
      openItem(state.current, { keepTuned: true });
      loadItems(true);
    }
  }, 1000);
}
