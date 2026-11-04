// The studio's page (README, The studio): the sidebar of every exercise and kit, the one item on
// stage, posed live with the tool's own modules, its technique video, and its numbers. Everything
// is a command from keys.json, so a key, a button and the help sheet all do the same thing; the
// mouse is optional.
// The server (plugin.mjs) prepares each entry and writes what is saved, OK'd or noted.
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import KEYS from './keys.json';
import { helpOf, keymap, tokenOf } from './keys.mjs';
import { FRAMES, buildStage, parseMannequin } from '../stage.mjs';
import { checkStage, contactsOf } from '../checks.mjs';
import { HEADINGS, applyTuned, atAngle, baseAt, headingOf, labelOf, rangeOf, tunables, tunedAngles, valueAt, withTuned, withoutChoices } from '../tune.mjs';
import { handlesAt } from '../handles.mjs';
import { tempoOf } from '../tempo.mjs';
import { BENCH_ANGLES } from '../entries.mjs';
import { faster, repAt, slower, spanOf, stepFrames, stepSeconds, synced, timeAt, zoomOf } from './video.mjs';
import { HALF_FOV, closeUpOf, dolly, framingOf, halfHeightOf, orbit, pan, placeOf, roll, showPlates, viewOf } from './view.mjs';
import { mayNote, progressNote, rowAfter } from './checking.mjs';
import { skeletonOf } from './bones.mjs';
import { inUnits, jointsOf, limitOf, limitsOf, numberRows, scrubAll, settled, showAlone, showJoint, statusOf, stepJoint, stepsOf, typedJoint, withoutBack } from './joints.mjs';
import { between, heldAt, turnOf, wpos } from '../rig.mjs';

const keys = keymap(KEYS);
const $ = (id) => document.getElementById(id);
const css = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const copy = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)));
const CHECK_NAMES = { hands: 'Hands', feet: 'Feet', equipment: 'Equipment', joints: 'Joints', trace: 'Trace', contact: 'Contact', linkage: 'Linkage', error: "Can't pose" };
const PANES = ['sidebar', 'stage', 'video', 'numbers'];

// What the page keeps across a reload (a change to a recipe, a part or poses.mjs reloads it): where
// it was, and each item's numbers not yet saved.
const MEMORY = 'mannequin-studio';
const remembered = (() => { try { return JSON.parse(sessionStorage.getItem(MEMORY)) ?? {}; } catch { return {}; } })();

const state = {
  items: null, // { exercises: [{ equipment, items }], kits }
  rows: [], // the sidebar's items as the filter leaves them
  cursor: 0,
  current: remembered.current ?? null, // `exercise/<id>` or `kit/<name>`
  item: null, // what the server says of it (plugin.mjs)
  tuned: {}, // `{ path: value }`, not yet saved
  fit: {}, // what the machine's fit solved for the stage, `{ path: value }`
  unsaved: remembered.unsaved ?? {},
  undo: [],
  redo: [],
  numbers: [],
  selected: remembered.selected ?? null, // a number's path
  focus: remembered.focus ?? 'sidebar',
  show: remembered.show ?? 'loop', // loop, top, bottom or paused
  d: 0, // how far down the rep, when paused
  lifting: false, // on the way up, when paused: the technique video's lift, B to C
  clip: false, // the baked clip on stage, not the live pose
  angle: null,
  filter: remembered.filter ?? '',
  find: null, // { matches, at }
  input: null, // what the bar or a box is taking: find, ex, type or note
  typing: null, // the number the bar's `type` sets: a bone's offset
  typingJoint: null, // or the kit's joint it sets
  video: null, // the item's technique video: { link, start, loop, src, fps, duration } once fetched
  sync: remembered.sync ?? true, // the video and the rep move together, once the loop is set
  speed: remembered.speed ?? 1,
  zoom: remembered.zoom ?? true, // the scrub bar shows the loop, not the whole video
  bone: remembered.bone ?? null, // the bone selected to turn, by name
  axis: remembered.axis ?? 'x', // the axis it turns about: x, y or z
  plates: true, // the plates shown, or hidden to see the hands on the bar: a view setting, kept nowhere
  lock: null, // the hand a close-up follows: l or r
  joints: [], // the joints of the kit on stage (joints.mjs), once it is posed
  joint: remembered.joint ?? null, // the joint selected to scrub, by key (`<kit>.<body>`)
  scrubs: {}, // where each joint is scrubbed to, by key: a view setting, kept nowhere
};

function remember() {
  if (state.item) state.unsaved[state.current] = state.tuned;
  for (const [k, v] of Object.entries(state.unsaved)) if (!Object.keys(v).length) delete state.unsaved[k];
  try {
    sessionStorage.setItem(MEMORY, JSON.stringify({
      current: state.current, unsaved: state.unsaved, selected: state.selected, focus: state.focus, show: state.show, filter: state.filter,
      sync: state.sync, speed: state.speed, zoom: state.zoom, bone: state.bone, axis: state.axis, joint: state.joint,
    }));
  } catch { /* a private window keeps nothing; the page works without it */ }
}

let aboutView = false; // the message says where the camera is, so a move of it clears the message
let said = 0;
function message(text, tone = '') {
  $('message').textContent = text;
  $('message').dataset.tone = tone;
  aboutView = false;
  said = performance.now();
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
const camera = new THREE.PerspectiveCamera(HALF_FOV * 2, 1, 0.02, 50);
// The free camera (view.mjs): it orbits round the selected bone or the body's centre, pans, dollies
// and rolls; a close-up follows its hand.
let view = viewOf({ target: [0, 1, 0], halfHeight: 1.1, azimuth: 62, elevation: 8 });
const markers = new THREE.Group();
scene.add(markers);
const dot = new THREE.SphereGeometry(1, 16, 12);
// In a hand's close-up, a dot wherever the hand stands off its bar or sinks into it past the
// contact check's 2 mm (#162), on the frame shown.
const misses = new THREE.Group();
misses.renderOrder = 13;
scene.add(misses);
// The selected bone: a dot on its joint and its three axes, the one it turns about drawn strong.
const gizmo = new THREE.Group();
gizmo.renderOrder = 11;
scene.add(gizmo);
const AXES = { x: new THREE.Vector3(1, 0, 0), y: new THREE.Vector3(0, 1, 0), z: new THREE.Vector3(0, 0, 1) };
for (const [name, dir] of Object.entries(AXES)) {
  const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(), dir]), new THREE.LineBasicMaterial({ depthTest: false, transparent: true }));
  line.name = name;
  line.renderOrder = 11;
  gizmo.add(line);
}
const joint = new THREE.Mesh(dot, new THREE.MeshBasicMaterial({ depthTest: false, transparent: true, opacity: 0.95 }));
joint.renderOrder = 12;
gizmo.add(joint);

let body = null;
let live = null; // { stage, entry }
let clipScene = null;
let mixer = null;
let duration = 1;
let handles = [];

/** Puts the camera on `framing` (the clip's own), unrolled and following no hand. */
function frame(framing) {
  view = viewOf(framing);
  state.lock = null;
  place();
}

/** The camera moved to `next` by the keys or the mouse: a message about where it was goes. */
function move(next) {
  place(next);
  if (aboutView) message('');
}

/** Sets the camera where `view` is. */
function place(next = view) {
  view = next;
  const { position, up, target } = placeOf(view);
  camera.position.copy(position);
  camera.up.copy(up);
  camera.lookAt(target);
  const back = THREE.MathUtils.degToRad(view.azimuth + 160);
  rim.position.set(target.x + 5 * Math.sin(back), target.y + 3, target.z + 5 * Math.cos(back));
  rim.target.position.copy(target);
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
  for (const line of gizmo.children) if (line.isLine) line.material.color.set(css(`--axis-${line.name}`));
  joint.material.color.set(css('--focus'));
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
  for (const root of [live?.stage.scene, clipScene]) if (root) showPlates(root, state.plates);
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

/**
 * Where in the rep the stage is now, `{ d, lifting }`: how far down it, and whether on the way up
 * (the lift and the hold at the top after it). The entry's own tempo when it loops.
 */
function along(seconds) {
  if (state.show === 'top') return { d: 0, lifting: false };
  if (state.show === 'bottom') return { d: 1, lifting: false };
  if (state.show === 'paused' || !live) return { d: state.d, lifting: state.lifting };
  const { lowering, bottom, lifting, top } = tempoOf(live.entry);
  const p = seconds % (lowering + bottom + lifting + top);
  if (p < lowering) return { d: p / lowering, lifting: false };
  if (p < lowering + bottom) return { d: 1, lifting: false };
  if (p < lowering + bottom + lifting) return { d: 1 - (p - lowering - bottom) / lifting, lifting: true };
  return { d: 0, lifting: true };
}

// The rep's clock: seconds of the loop played, slowed with the video's speed while the two are synced.
let clock = 0;
let lastNow = null;
let loopFrom = 0;
function draw(now) {
  clock += lastNow === null ? 0 : ((now - lastNow) / 1000) * (isSynced() ? state.speed : 1);
  lastNow = now;
  const w = canvas.clientWidth;
  const h = canvas.clientHeight;
  if (w && h && (canvas.width !== Math.round(w * renderer.getPixelRatio()) || canvas.height !== Math.round(h * renderer.getPixelRatio()))) {
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  }
  const rep = along(clock - loopFrom);
  const d = Math.min(rep.d, 1);
  if (state.show === 'loop') { state.d = d; state.lifting = rep.lifting; }
  followVideo({ d, lifting: rep.lifting });
  if (state.clip && mixer) mixer.setTime(Math.min(d, 0.9999) * duration);
  // The live pose is posed under the clip too, for the bone the camera orbits and the hand it follows.
  const posed = live ? live.stage.pose(d) : null;
  // A kit's scrubbed joints are held where they are set, whatever the rep does.
  if (live && isKit()) {
    scrubAll(liveKits(), state.scrubs);
    paintJointValues();
  }
  follow();
  markMisses(d, posed);
  for (const m of [...markers.children, ...misses.children]) m.scale.setScalar(camera.position.distanceTo(m.position) * 0.009);
  paintGizmo();
  renderer.render(scene, camera);
  requestAnimationFrame(draw);
}

// ---- The bones and the camera --------------------------------------------------------------------

let skeleton = null; // the bones' walk (bones.mjs), once the mannequin is loaded
const hasBody = () => Boolean(live && !isKit());
const rigNow = () => live.stage.rig;
/** Where the camera orbits: the selected bone, or the body's centre, or what it looks at. */
function pivot() {
  if (!hasBody()) return new THREE.Vector3(...view.target);
  return state.bone ? wpos(rigNow().bones[state.bone]) : between(rigNow(), 'pelvis', 'neck_01');
}

let held = null; // where the followed hand held a frame ago
/** A close-up's camera moves with its hand through the rep. */
function follow() {
  if (!state.lock || !hasBody()) return;
  const at = heldAt(rigNow(), state.lock);
  if (held) place(moved(at.clone().sub(held)));
  held = at;
}
let missesAt = null; // the stage, frame and hand the misses were marked for
/** Marks where the close-up's hand misses its bar, posed `P` at `d` of the rep; live poses only. */
function markMisses(d, P) {
  const on = Boolean(state.lock && hasBody() && !state.clip && P);
  misses.visible = on;
  if (!on) { missesAt = null; return; }
  if (missesAt?.stage === live.stage && missesAt.d === d && missesAt.k === state.lock) return;
  missesAt = { stage: live.stage, d, k: state.lock };
  misses.clear();
  for (const c of contactsOf(live.stage, P, state.lock)) {
    if (c.past <= 0 || !c.at) continue;
    const m = new THREE.Mesh(dot, new THREE.MeshBasicMaterial({ color: css('--fail'), depthTest: false, transparent: true, opacity: 0.95 }));
    m.renderOrder = 13;
    m.position.copy(c.at);
    m.name = c.found;
    misses.add(m);
  }
}

/** `view` moved by `by` (a vector), the camera with its target. */
const moved = (by) => ({ ...view, target: new THREE.Vector3(...view.target).add(by).toArray() });

/** The selected bone's gizmo, where the bone is now and turned as it is. */
function paintGizmo() {
  gizmo.visible = Boolean(state.bone && hasBody() && !state.clip);
  if (!gizmo.visible) return;
  const rig = rigNow();
  gizmo.position.copy(wpos(rig.bones[state.bone]));
  // The axes the bone had in the bind pose, carried with it: what x, y and z turn it about.
  gizmo.quaternion.copy(turnOf(rig, state.bone));
  const size = camera.position.distanceTo(gizmo.position) * 0.05;
  for (const line of gizmo.children) {
    if (line.isLine) {
      line.scale.setScalar(size * (line.name === state.axis ? 1.6 : 1));
      line.material.opacity = line.name === state.axis ? 1 : 0.7;
    }
  }
  joint.scale.setScalar(size * 0.14);
}

const ENDS = { top: 'the top', bottom: 'the bottom' };
/** The end of the rep the stage holds (`top` or `bottom`), where a bone turns, or null. */
const endHeld = () => (state.show in ENDS ? state.show : null);
/** The selected bone's offset on its axis at the end held, as a number the keys step, or null. */
function boneNumber() {
  const end = endHeld();
  if (!state.bone || !end) return null;
  const path = `${end}.offsets.${state.bone}.${'xyz'.indexOf(state.axis)}`;
  return { path, value: 0, ...rangeOf(path, 0) };
}
/** Whether the keys turn the selected bone: the stage is focused and a bone of the body is selected. */
const turningBone = () => Boolean(state.focus === 'stage' && state.bone && hasBody());
/** What `h` `l` `=` `R` change: on the stage the selected bone's offset, else the selected number. */
function aimed() {
  if (turningBone()) {
    const t = boneNumber();
    if (!t) message(`Hold the top (${keys.keyOf('holdTop')}) or the bottom (${keys.keyOf('holdBottom')}): a bone turns at one of them`);
    return t;
  }
  return selectedNumber();
}

/** Selects bone `name` and focuses the stage, where the keys turn it. */
function selectBone(name) {
  if (!hasBody() || !skeleton) { message('No bones to select here'); return; }
  state.bone = name;
  setFocus('stage');
  paintBone();
  paintStep();
  remember();
}
/** Selects the bone `to(skeleton, bone)` names from the one selected; the pelvis if none is. */
const walkBone = (to) => selectBone(state.bone ? to(skeleton, state.bone) : skeleton?.names[0]);
/** Picks the axis the selected bone turns about: x, y or z. */
function setAxis(axis) {
  if (!state.bone) { message(`Select a bone first (${keys.keyOf('boneParent')}, or ${keys.keyOf('find')} by name)`); return; }
  state.axis = axis;
  paintBone();
  paintStep();
  remember();
}

/** The selected bone's box on the stage: its name, axis, and its offset at the end held. */
function paintBone() {
  const box = $('bone');
  box.hidden = !state.bone || !hasBody();
  if (box.hidden) return;
  const end = endHeld();
  const turned = valueAt(entryNow(), `${end ?? 'top'}.offsets.${state.bone}`) ?? [0, 0, 0];
  $('bone-name').textContent = state.bone;
  $('bone-axis').textContent = state.axis;
  $('bone-axis').dataset.axis = state.axis;
  $('bone-end').textContent = end ? `at ${ENDS[end]} · turned ${turned.map((v) => `${Number(v.toFixed(1))}°`).join(' ')}` : 'hold the top or the bottom to turn it';
}

/** The close-up of the hand on side `k`, following it through the rep. */
function closeUpOn(k) {
  if (!hasBody()) { message('No hands to close up on here'); return; }
  place(closeUpOf(live.stage, k));
  state.lock = k;
  held = null;
  message(`The ${k === 'l' ? 'left' : 'right'} hand, where it holds: the camera follows it (${keys.keyOf('cameraClip')} for the clip's framing)`);
  aboutView = true;
}

/** Degrees for an orbit or a roll: 5, or a count's. */
const degreesOf = (n, counted) => (counted ? n : 5);
/** A pan's step: a tenth of what the view shows top to bottom. */
const panStep = () => halfHeightOf(view) * 0.2;

/** Hides the plates, or shows them again, live and in the clip: nothing is saved. */
function togglePlates() {
  state.plates = !state.plates;
  showWhich();
  paintCommands();
  remember();
  message(state.plates ? 'The plates are shown' : 'The plates are hidden, to see the hands on the bar (the clip keeps them)');
}

/** Saves the view as the clip's framing, at the bench angle shown for an adjustable bench, and re-bakes. */
async function saveFraming() {
  const item = state.item;
  if (!item?.entry || isKit()) { message('Only an exercise has a framing'); return; }
  const framing = framingOf(view);
  const path = item.entry.bench?.cameras ? `bench.cameras.${state.angle}` : 'camera';
  message(`Saving the framing into poses.mjs (${path}) and baking…`, 'busy');
  try {
    await api('/api/save', { id: item.id, tuned: { [path]: framing } });
    await openItem(state.current, { keepTuned: true });
    await loadClip();
    message(`The clip opens on this view now: ${path} in poses.mjs, turned ${framing.azimuth}°, tilted ${framing.elevation}°, ${Math.round(framing.halfHeight * 2000)} mm high`);
  } catch (err) {
    message(err.message, 'error');
  }
  followChecks();
}

// ---- Posing live --------------------------------------------------------------------------------

const isKit = () => state.item?.kind === 'kit';
/** The entry as tuned now, at the bench angle shown. */
const entryNow = () => {
  const entry = applyTuned(state.item.entry, state.tuned);
  return entry.bench ? atAngle(entry, state.angle) : entry;
};
const valueNow = (t) => valueAt(entryNow(), t.path) ?? t.value;
/** The entry as the sliders start from it: with what the fit solved, which it leaves unsaid. */
const baseEntry = () => applyTuned(state.item.entry, state.fit);
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
  else state.tuned = withTuned(baseEntry(), state.tuned, path, value);
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
    if (isKit()) showAlone(stage.kit.kits, state.item.id);
    setLive({ stage, entry });
    // The fit's numbers come with the stage: each is a slider, at what it solved.
    if (!same(stage.fit, state.fit)) {
      state.fit = stage.fit;
      state.numbers = numbersOf(state.tuned);
      paintNumbers();
    }
    if (isKit()) {
      // Posed once, so the kit has its joints to list: its own, not those of a kit beside it.
      stage.pose(state.d);
      state.joints = jointsOf({ [state.item.id]: stage.kit.kits[state.item.id] });
      if (!state.joints.some((j) => j.key === state.joint)) state.joint = state.joints[0]?.key ?? null;
      paintNumbers();
    }
    skeleton ??= skeletonOf(stage.rig.bones);
    paintBone();
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
  $('hint').textContent = `${handles.length ? 'drag a dot to pose it, or ' : ''}drag to orbit, right-drag to pan, wheel to zoom`;
}

function dragTo(h, point) {
  for (const [path, value] of Object.entries(h.drag(point))) {
    // A point's numbers that are Per angle change at the angle shown; the others as ever.
    const leaves = Array.isArray(value) ? value.map((v, i) => [`${path}.${i}`, v]) : [];
    if (leaves.some(([leaf]) => perMap()[leaf])) {
      const rest = copy(state.tuned[path] ?? baseAt(baseEntry(), path));
      leaves.forEach(([leaf, v], i) => { if (perMap()[leaf]) setPer(leaf, { ...perMap()[leaf], [state.angle]: v }); else rest[i] = v; });
      state.tuned = withTuned(baseEntry(), state.tuned, path, rest);
    } else {
      put(path, value);
    }
  }
  changed();
}

// A drag orbits the stage (round the selected bone, or the body's centre), a right-drag pans it, the
// wheel dollies it, and a dot drags its pose numbers.
let turning = null; // { x, y, pan, pivot }
let dragging = null;
const ray = new THREE.Raycaster();
const holder = $('canvas');
holder.addEventListener('contextmenu', (ev) => ev.preventDefault());
holder.addEventListener('pointerdown', (ev) => {
  setFocus('stage');
  holder.setPointerCapture(ev.pointerId);
  const r = canvas.getBoundingClientRect();
  let best = null;
  let nearest = 28;
  for (const h of ev.button === 0 ? handles : []) {
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
  turning = { x: ev.clientX, y: ev.clientY, pan: ev.button === 2, pivot: pivot() };
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
  if (!turning) return;
  const [dx, dy] = [ev.clientX - turning.x, ev.clientY - turning.y];
  turning.x = ev.clientX;
  turning.y = ev.clientY;
  if (turning.pan) {
    // The stage follows the pointer: a pixel is what the view shows over the canvas's height.
    const metres = (2 * halfHeightOf(view)) / (canvas.clientHeight || 1);
    move(pan(view, -dx * metres, dy * metres));
  } else {
    move(orbit(view, -dx * 0.5, dy * 0.5, turning.pivot));
  }
});
const endDrag = () => { turning = null; dragging = null; };
holder.addEventListener('pointerup', endDrag);
holder.addEventListener('pointercancel', endDrag);
holder.addEventListener('wheel', (ev) => {
  ev.preventDefault();
  if (ev.deltaY) move(dolly(view, ev.deltaY < 0 ? 1 : -1));
}, { passive: false });

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

async function loadItems() {
  const items = await api('/api/items');
  // Read here, not before the request: the cursor may have moved while it was under way.
  const under = state.rows[state.cursor] && rowKey(state.rows[state.cursor]);
  state.items = items;
  paintList();
  const at = rowAfter(state.rows.map(rowKey), under, state.current);
  if (at >= 0 && at !== state.cursor) { state.cursor = at; paintList(); }
}

/** What the message line last said of the checks. */
let checkNote = $('message').textContent;
function noteChecks(note) {
  const { textContent: text, dataset: { tone } } = $('message');
  if (!mayNote({ text, tone, own: checkNote, age: performance.now() - said, note })) return;
  checkNote = note;
  message(note, note ? 'busy' : '');
}

let following = false;
/**
 * Follows the server's checks until they have all finished: the message line counts them, and the
 * list fills in as they do, every so often, since making it holds the next pose back.
 */
async function followChecks() {
  if (following) return;
  following = true;
  let done = null;
  let listed = 0;
  for (;;) {
    const progress = (await api('/api/state').catch(() => null))?.checks;
    if (progress) {
      const note = progressNote(progress);
      noteChecks(note);
      const finished = !note;
      if (progress.done !== done && (finished || performance.now() - listed > 2000)) {
        done = progress.done;
        listed = performance.now();
        await loadItems().catch(() => {});
      }
      if (finished) break;
    }
    await new Promise((next) => { setTimeout(next, 500); });
  }
  following = false;
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
    state.tuned = withoutChoices(state.unsaved[key] ?? {});
    if (fresh) { state.undo = []; state.redo = []; }
  }
  state.angle = item.entry?.bench ? (state.angle !== null && fresh === false ? state.angle : item.entry.bench.angle) : null;
  state.clip = false;
  if (fresh) { state.joints = []; state.scrubs = {}; state.fit = {}; }
  state.numbers = numbersOf(state.tuned);
  if (!state.numbers.some((t) => t.path === state.selected)) state.selected = onScreen()[0]?.path ?? null;
  if (fresh && item.entry) frame(cameraAtAngle());
  if (fresh && !item.entry) setLive(null);
  paintItem();
  paintNumbers();
  paintList();
  remember();
  if (fresh) openVideo(item);
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
/** The item's numbers with `tuned` in: a bone turned and not saved yet is a number too. */
const numbersOf = (tuned) => (state.item?.entry && !isKit() ? tunables(applyTuned(baseEntry(), tuned)) : []);

function paintNumbers() {
  const box = $('sliders');
  box.replaceChildren();
  if (isKit() && state.joints.length) { paintJoints(); return; }
  if (!state.item?.entry || isKit()) {
    box.innerHTML = `<p style="padding: 4px 12px; color: var(--quiet)">${isKit() ? `${noJoints()}. The entry that uses it holds its numbers: tune them there.` : 'Nothing to tune without a pose.'}</p>`;
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
  for (const [row, t] of numberRows($('sliders').querySelectorAll('.num'), state.numbers)) {
    const v = valueNow(t);
    const per = perMap()[t.path];
    row.querySelector('.value').textContent = unitOf(t).show(v);
    const range = row.querySelector('input[type="range"]');
    range.min = Math.min(t.min, v);
    range.max = Math.max(t.max, v);
    range.value = v;
    row.dataset.changed = String(per ? !same(per, state.item.entry.bench?.perAngle?.[t.path]) : v !== baseAt(baseEntry(), t.path) && v !== t.value);
    row.dataset.per = String(Boolean(per));
    row.dataset.selected = String(t.path === state.selected);
  }
  const n = Object.keys(state.tuned).filter((k) => k !== '@angles').length + Object.keys(state.tuned['@angles'] ?? {}).length;
  $('changed').textContent = n ? `${n} unsaved` : '';
  paintStep();
  paintAngle();
}

function paintStep() {
  if (isKit()) { paintJointStep(); return; }
  // On the stage with a bone selected, the keys turn it; elsewhere they step the number.
  const bone = turningBone();
  const t = bone ? boneNumber() : selectedNumber();
  const step = $('step');
  if (!t) { step.textContent = bone ? `${state.bone}: hold the top or the bottom to turn it` : ''; return; }
  const u = unitOf(t);
  step.innerHTML = '';
  const add = (html) => step.insertAdjacentHTML('beforeend', html);
  add(`<strong></strong>`);
  step.querySelector('strong').textContent = bone ? labelOf(t.path) : t.path;
  add(`<span><kbd>${keys.keyOf('less')}</kbd><kbd>${keys.keyOf('more')}</kbd> ${u.step(t.step)}</span>`);
  add(`<span><kbd>${keys.keyOf('lessFine')}</kbd><kbd>${keys.keyOf('moreFine')}</kbd> ${u.step(Number((t.step / 10).toPrecision(3)))}</span>`);
  add(`<span><kbd>${keys.keyOf('type')}</kbd> type</span><span><kbd>${keys.keyOf('reset')}</kbd> reset</span>`);
  if (state.angle !== null && !bone) add(`<span><kbd>${keys.keyOf('perAngle')}</kbd> per angle</span>`);
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
  const numbers = numbersOf(state.tuned);
  if (!same(numbers.map((t) => t.path), state.numbers.map((t) => t.path))) {
    state.numbers = numbers;
    paintNumbers();
  }
  paintValues();
  paintBone();
  remember();
  state.clip = false;
  showWhich();
  if (!rebuildFrame) rebuildFrame = requestAnimationFrame(() => { rebuildFrame = 0; rebuild(); });
}

function nudge(direction, fine, count) {
  const t = aimed();
  if (!t || !state.item?.entry) return;
  snapshot();
  const v = valueNow(t) + direction * t.step * (fine ? 0.1 : 1) * count;
  put(t.path, Number(v.toFixed(5)));
  changed();
}

// ---- A kit's joints ------------------------------------------------------------------------------

// With a kit on stage, its numbers are its joints (joints.mjs). A joint scrubbed is held where it is
// set while the rep plays round it, its stack with it; reset, it goes back to where the rep has it.
const selectedJoint = () => state.joints.find((j) => j.key === state.joint);
/** The kits on the live stage, by name. */
const liveKits = () => live?.stage.kit.kits ?? {};
/** Where joint `j` is now: where it is scrubbed to, or where the rep has it. */
const jointNow = (j) => state.scrubs[j.key] ?? liveKits()[j.kit]?.joints[j.body]?.value ?? 0;
const jointLabel = (j) => `${j.kit} ${j.body}`;
/** Why a kit has no joint to scrub. */
const noJoints = () => (state.item?.entry ? 'Nothing on this kit moves: it has no joints' : 'Nothing to scrub without a pose that uses it');

function paintJoints() {
  const box = $('sliders');
  box.replaceChildren();
  const heading = document.createElement('div');
  heading.className = 'heading';
  heading.textContent = 'Joints';
  box.append(heading);
  for (const j of state.joints) {
    const row = document.createElement('div');
    row.className = 'num';
    row.dataset.joint = j.key;
    row.innerHTML = '<label></label><span class="value"></span><input type="range" tabindex="-1">';
    const [label, , range] = row.children;
    label.textContent = jointLabel(j);
    label.title = `${j.type}, ${limitsOf(j)}`;
    range.min = j.limits[0];
    range.max = j.limits[1];
    range.step = 'any';
    range.setAttribute('aria-label', jointLabel(j));
    range.addEventListener('input', () => setJoint(j, settled(j, Number(range.value))));
    row.addEventListener('click', () => { setFocus('numbers'); selectJoint(j.key); });
    box.append(row);
  }
  paintJointValues();
  paintStep();
}

/** The joints' values and selection, without rebuilding the rows. */
function paintJointValues() {
  for (const row of $('sliders').querySelectorAll('[data-joint]')) {
    const j = state.joints.find((x) => x.key === row.dataset.joint);
    const v = jointNow(j);
    row.querySelector('.value').textContent = showJoint(j, v);
    row.querySelector('input[type="range"]').value = v;
    row.dataset.changed = String(j.key in state.scrubs);
    row.dataset.selected = String(j.key === state.joint);
  }
}

function paintJointStep() {
  const j = selectedJoint();
  const step = $('step');
  if (!j) { step.textContent = ''; return; }
  const by = stepsOf(j);
  step.innerHTML = '';
  const add = (html) => step.insertAdjacentHTML('beforeend', html);
  add(`<strong></strong>`);
  step.querySelector('strong').textContent = `${jointLabel(j)}, ${limitsOf(j)}`;
  add(`<span><kbd>${keys.keyOf('less')}</kbd><kbd>${keys.keyOf('more')}</kbd> ${by[0]}</span>`);
  add(`<span><kbd>${keys.keyOf('lessFine')}</kbd><kbd>${keys.keyOf('moreFine')}</kbd> ${by[1]}</span>`);
  add(`<span><kbd>${keys.keyOf('lowest')}</kbd><kbd>${keys.keyOf('highest')}</kbd> limits</span>`);
  add(`<span><kbd>${keys.keyOf('type')}</kbd> type</span><span><kbd>${keys.keyOf('reset')}</kbd> reset</span>`);
}

function selectJoint(key) {
  state.joint = key;
  paintJointValues();
  $('sliders').querySelector(`[data-joint="${CSS.escape(key)}"]`)?.scrollIntoView({ block: 'nearest' });
  paintStep();
  remember();
}

function moveJoint(by, absolute) {
  if (!state.joints.length) return;
  const at = state.joints.findIndex((j) => j.key === state.joint);
  const i = absolute ?? at + by;
  selectJoint(state.joints[Math.max(0, Math.min(state.joints.length - 1, i))].key);
}

/** Scrubs joint `j` to `value`, within its limits, and says where it is. */
function setJoint(j, value) {
  state.scrubs[j.key] = value;
  scrubAll(liveKits(), { [j.key]: value });
  paintJointValues();
  message(statusOf(j, value));
}

/** Runs `fn` on the selected joint, or says there is none. */
function onJoint(fn) {
  const j = selectedJoint();
  if (!j) { message(noJoints()); return; }
  fn(j);
}

const nudgeJoint = (direction, fine, count) => onJoint((j) => setJoint(j, stepJoint(j, jointNow(j), direction * count, fine)));
const limitJoint = (end) => onJoint((j) => setJoint(j, limitOf(j, end)));
const resetJoint = () => onJoint((j) => { delete state.scrubs[j.key]; paintJointValues(); message(`${jointLabel(j)} back where the rep has it`); });
function typeJoint() {
  onJoint((j) => {
    state.typingJoint = j;
    openBar('type', `${jointLabel(j)} = `);
    $('line').value = String(inUnits(j, jointNow(j)));
    $('line').select();
  });
}
function setTypedJoint(j, text) {
  const v = typedJoint(j, text, jointNow(j));
  if (v === null) { message(`Not a number: ${text}`, 'error'); return; }
  setJoint(j, v);
}

// ---- The technique video ------------------------------------------------------------------------

// Fetched by the server the first time an exercise is opened (plugin.mjs) and played muted. With
// sync on and the loop's three marks set, A is the top of the rep, B the bottom and C the top again:
// the lowering is A to B and the lift B to C, both forward. The stage drives the video while it
// plays or steps, and a step or a scrub of the video moves the stage.
const player = $('player');
player.muted = true;
const scrubber = $('scrubber');
/** Whether the video and the rep move together now. */
const isSynced = () => Boolean(live && state.video?.src) && synced(state.video.loop, state.sync);
/** The stretch of the video the loop's marks span, or null before two are set. */
const loopSpan = () => spanOf(state.video?.loop);
/** Whether there is no video to `what`, saying so. */
const noVideoTo = (what) => {
  if (state.video?.src) return false;
  message(`No video to ${what}`);
  return true;
};
const repKey = ({ d, lifting }) => `${d}|${lifting}`;
/** The stage paused where the video put it (`{ d, lifting }`): the video isn't moved back. */
function moveRep(at) {
  state.d = at.d;
  state.lifting = at.lifting;
  pushed = repKey(at);
  setShow('paused');
}
const timeLabel = (t) => `${Math.floor(t / 60)}:${(t % 60).toFixed(2).padStart(5, '0')}`;

let asked = null; // the exercise whose video is being fetched
async function openVideo(item) {
  asked = item.id;
  player.pause();
  player.removeAttribute('src');
  player.load();
  state.video = item.kind === 'exercise' && item.video ? { ...item.video } : null;
  $('link').hidden = !state.video;
  if (state.video) $('link').href = state.video.link;
  if (!state.video) {
    noVideo(item.kind === 'kit' ? 'A kit has no technique video.' : 'No video link for this exercise in exercises.json.');
    return;
  }
  noVideo('Fetching the video… (once: it is kept in the cache)');
  let got;
  try {
    got = await api(`/api/video/${item.id}`);
  } catch (err) {
    if (asked === item.id) noVideo(`${err.message}. The stage works without it.`);
    return;
  }
  if (asked !== item.id) return;
  Object.assign(state.video, got);
  player.addEventListener('loadedmetadata', () => {
    player.playbackRate = state.speed;
    if (Number.isFinite(player.duration)) state.video.duration = player.duration;
    // On its loop's top if it has one, else at the link's timestamp; a synced video follows the stage.
    player.currentTime = state.video.loop?.a ?? state.video.start;
    pushed = null;
    paintVideo();
  }, { once: true });
  player.src = got.src;
  $('no-video').hidden = true;
  paintVideo();
}

function noVideo(text) {
  $('no-video').hidden = false;
  $('no-video').textContent = text;
  paintVideo();
}

let pushed = null; // where in the rep the stage last moved the video to (`repKey`)
let lastT = 0;
/** Each frame: the video follows the stage (`{ d, lifting }`) when synced, else it plays its loop round. */
function followVideo(at) {
  const v = state.video;
  if (!v?.src || player.readyState < 1) return;
  if (isSynced()) {
    if (repKey(at) !== pushed && !player.seeking) {
      player.currentTime = timeAt(v.loop, at);
      pushed = repKey(at);
    }
  } else if (!player.paused && loopSpan()) {
    // Round the loop when it plays from inside it; played from outside, it runs on.
    const [lo, hi] = loopSpan();
    if (lastT >= lo && lastT < hi && player.currentTime >= hi) player.currentTime = lo;
  }
  lastT = player.currentTime;
  paintVideo();
}

/** Puts the video at `t`, paused; when synced, the stage goes where the video is. */
function seekVideo(t) {
  if (noVideoTo('move')) return;
  player.pause();
  player.currentTime = t;
  if (isSynced()) {
    moveRep(repAt(state.video.loop, t));
    message(`the rep at frame ${Math.round(state.d * (FRAMES - 1))} of ${FRAMES - 1}`);
  }
  paintVideo();
}

/** Moves the video to where `to(time, video)` says, from where it is. */
const stepVideo = (to) => noVideoTo('move') || seekVideo(to(player.currentTime, state.video));

function playVideo() {
  if (noVideoTo('play')) return;
  if (isSynced()) { run('play', null); paintCommands(); return; }
  if (!player.paused) { player.pause(); return; }
  // From the loop's end, round to its start.
  if (loopSpan()) {
    const [lo, hi] = loopSpan();
    if (Math.abs(player.currentTime - hi) < 1 / state.video.fps) player.currentTime = lo;
  }
  player.play().catch((err) => message(`The video won't play: ${err.message}`, 'error'));
}

function setSpeed(speed) {
  state.speed = speed;
  player.playbackRate = speed;
  remember();
  paintVideo();
  message(`${speed}×${isSynced() ? ', the rep with it' : ''}`);
}

// Each mark of the loop: what it is, and where the stage holds when it is set.
const MARKS = {
  a: { name: 'A, the top of the rep,', at: { d: 0, lifting: false } },
  b: { name: 'B, the bottom of the rep,', at: { d: 1, lifting: false } },
  c: { name: 'C, back at the top,', at: { d: 0, lifting: true } },
};
/** Marks `mark` of the loop (`a`, `b` or `c`) at the frame shown, and saves it. */
async function setLoopMark(mark) {
  if (noVideoTo('loop')) return;
  const v = state.video;
  const t = Number(player.currentTime.toFixed(3));
  v.loop = { a: null, b: null, c: null, ...v.loop, [mark]: t };
  // Synced now: the stage holds where the mark is, and the video waits on it.
  if (isSynced()) {
    player.pause();
    moveRep(MARKS[mark].at);
  }
  paintVideo();
  try {
    await api('/api/loop', { id: state.item.id, loop: v.loop });
    const marked = ['a', 'b', 'c'].every((m) => v.loop[m] !== null);
    const order = marked && !synced(v.loop, true) ? ': out of order, so not synced (A, B, C in the video)' : '';
    message(`${MARKS[mark].name} at ${timeLabel(t)}: saved in review/loops.json${order}`);
  } catch (err) {
    message(err.message, 'error');
  }
}

function toggleZoom() {
  state.zoom = !state.zoom;
  remember();
  paintCommands();
  paintVideo();
  message(state.zoom ? (loopSpan() ? 'The scrub bar shows the loop' : 'The scrub bar shows the loop, once two marks are set') : 'The scrub bar shows the whole video');
}

function toggleSync() {
  state.sync = !state.sync;
  remember();
  if (state.sync && !synced(state.video?.loop, true)) {
    message(`Sync is on, and waits for the loop: ${keys.keyOf('markA')} at the top of a rep, ${keys.keyOf('markB')} at its bottom, ${keys.keyOf('markC')} at the top again`);
  }
  else message(state.sync ? 'The video follows the rep, and the rep the video' : 'The video plays on its own');
  if (isSynced()) { player.pause(); pushed = null; }
  paintCommands();
  paintVideo();
}

let painted = '';
function paintVideo() {
  const v = state.video;
  const t = player.currentTime || 0;
  const on = isSynced();
  const mark = (m) => `${m.toUpperCase()} ${v.loop?.[m] == null ? '–' : timeLabel(v.loop[m])}`;
  const duration = v?.duration || 1;
  const [from, to] = state.zoom ? zoomOf(v?.loop, duration) : [0, duration];
  const where = !v?.src ? '' : [
    `<strong>${timeLabel(t)}</strong>`, `frame ${Math.floor(t * v.fps)}`, `${state.speed}×`, mark('a'), mark('b'), mark('c'),
    `<span data-on="${on}">sync ${on ? 'on' : state.sync ? 'waits for A, B and C' : 'off'}</span>`,
  ].join(' · ');
  const key = `${where}|${from}|${to}`;
  if (key === painted) return;
  painted = key;
  $('where').innerHTML = where;
  for (const el of document.querySelectorAll('#video [data-command]')) el.disabled = !v?.src && el.dataset.command !== 'sync';
  scrubber.min = from;
  scrubber.max = to;
  scrubber.value = t;
  scrubber.disabled = !v?.src;
  const at = (sec) => `calc(12px + (100% - 24px) * ${(sec - from) / (to - from)})`;
  for (const m of ['a', 'b', 'c']) {
    const el = $(`mark-${m}`);
    const sec = v?.loop?.[m];
    el.hidden = sec == null || !v.src || sec < from || sec > to;
    if (!el.hidden) el.style.left = at(sec);
  }
  const span = $('loop-span');
  const [lo, hi] = loopSpan() ?? [];
  span.hidden = lo === undefined || !v.src;
  if (!span.hidden) {
    span.style.left = at(Math.max(lo, from));
    span.style.width = `calc((100% - 24px) * ${(Math.min(hi, to) - Math.max(lo, from)) / (to - from)})`;
  }
}

scrubber.addEventListener('input', () => seekVideo(Number(scrubber.value)));
player.addEventListener('play', () => paintCommands());
player.addEventListener('pause', () => paintCommands());

// ---- Commands -----------------------------------------------------------------------------------

function setFocus(pane) {
  state.focus = pane;
  for (const p of PANES) $(p).dataset.focused = String(p === pane);
  paintStep();
  remember();
}

function setShow(show) {
  if (show === 'loop' && state.show !== 'loop') loopFrom = clock - loopOffset(state.d, state.lifting);
  state.show = show;
  showHandles();
  paintCommands();
  paintBone();
  paintStep();
  remember();
}
/** Where in the loop the rep is `d` down, on the way down or up: so play resumes from where it was paused. */
function loopOffset(d, lifting) {
  if (!live) return 0;
  const tempo = tempoOf(live.entry);
  return lifting ? tempo.lowering + tempo.bottom + (1 - d) * tempo.lifting : d * tempo.lowering;
}

function stepFrame(by) {
  if (state.show === 'top' || state.show === 'bottom') {
    state.d = state.show === 'top' ? 0 : 1;
    state.lifting = false;
  }
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
  for (const name of hasBody() ? skeleton?.names ?? [] : []) {
    const score = fuzzy(query, name);
    if (score !== null) out.push({ score: score - 0.25, kind: 'bone', name, label: `bone ${name}` });
  }
  return out.sort((a, b) => a.score - b.score).slice(0, 40);
}

function jump(match) {
  if (match.kind === 'bone') { selectBone(match.name); return; }
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
  } catch (err) {
    message(err.message, 'error');
  }
  followChecks();
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
    followChecks();
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
    followChecks();
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

/** Sets number `t` to what `text` says: `12.5` is the value, `+2` (or `+-2`) moves it from where it is. */
function setTyped(t, text) {
  const u = unitOf(t);
  const relative = text.startsWith('+');
  const n = Number(relative ? text.slice(1) : text);
  if (!Number.isFinite(n) || text === '') { message(`Not a number: ${text}`, 'error'); return; }
  snapshot();
  put(t.path, Number(((relative ? valueNow(t) : 0) + n / u.scale).toFixed(5)));
  changed();
}

function typeValue() {
  const t = aimed();
  if (!t) return;
  // A bone's offset is typed on the command line: it may have no row yet.
  if (t.path.includes('.offsets.')) {
    state.typing = t;
    state.typingJoint = null;
    openBar('type', `${labelOf(t.path)} = `);
    $('line').value = String(Number(valueNow(t).toFixed(4)));
    $('line').select();
    return;
  }
  const row = $('sliders').querySelector(`[data-path="${CSS.escape(state.selected ?? '')}"]`);
  if (!row) return;
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
    if (set) setTyped(t, box.value.trim());
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
  const t = aimed();
  if (!t) return;
  snapshot();
  if (perMap()[t.path]) setPer(t.path, copy(state.item.entry.bench?.perAngle?.[t.path]));
  if (!perMap()[t.path]) state.tuned = withTuned(baseEntry(), state.tuned, t.path, baseAt(baseEntry(), t.path));
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
  // The back pad scrubbed by hand would stay where it was.
  state.scrubs = withoutBack(state.scrubs);
  frame(cameraAtAngle());
  paintValues();
  loadClip().catch(() => {});
  rebuild();
}

function turnTo(azimuth, elevation) {
  state.lock = null;
  move({ ...view, azimuth, elevation, roll: 0 });
}

/** A command that does `onKit` with a kit on stage, its joints the numbers, and `otherwise` elsewhere. */
const either = (onKit, otherwise) => (...args) => (isKit() ? onKit(...args) : otherwise(...args));
const noLimits = () => message('Only a kit\'s joints have limits to send them to');

const COMMANDS = {
  down: (n) => (state.focus === 'numbers' ? either(moveJoint, moveSelection)(n) : moveCursor(state.cursor + n)),
  up: (n) => (state.focus === 'numbers' ? either(moveJoint, moveSelection)(-n) : moveCursor(state.cursor - n)),
  first: () => (state.focus === 'numbers' ? either(moveJoint, moveSelection)(0, 0) : moveCursor(0)),
  last: (n, counted) => (state.focus === 'numbers' ? either(moveJoint, moveSelection)(0, counted ? n - 1 : Infinity) : moveCursor(counted ? n - 1 : state.rows.length - 1)),
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
  less: (n) => either(nudgeJoint, nudge)(-1, false, n),
  more: (n) => either(nudgeJoint, nudge)(1, false, n),
  lessFine: (n) => either(nudgeJoint, nudge)(-1, true, n),
  moreFine: (n) => either(nudgeJoint, nudge)(1, true, n),
  type: either(typeJoint, () => typeValue()),
  reset: either(resetJoint, () => reset()),
  lowest: either(() => limitJoint(-1), noLimits),
  highest: either(() => limitJoint(1), noLimits),
  perAngle: () => perAngle(),
  undo: (n) => history(state.undo, state.redo, n),
  redo: (n) => history(state.redo, state.undo, n),
  cameraFront: () => turnTo(0, 6),
  cameraSide: () => turnTo(90, 6),
  cameraTop: () => turnTo(view.azimuth, 89.5),
  turnLeft: (n) => move({ ...view, azimuth: view.azimuth + 10 * n }),
  turnRight: (n) => move({ ...view, azimuth: view.azimuth - 10 * n }),
  cameraClip: () => {
    if (!state.item?.entry) { message('No framing here'); return; }
    frame(cameraAtAngle());
    message('The clip\'s own framing');
    aboutView = true;
  },
  closeUpLeft: () => closeUpOn('l'),
  closeUpRight: () => closeUpOn('r'),
  plates: () => togglePlates(),
  orbitLeft: (n, counted) => move(orbit(view, degreesOf(n, counted), 0, pivot())),
  orbitRight: (n, counted) => move(orbit(view, -degreesOf(n, counted), 0, pivot())),
  orbitDown: (n, counted) => move(orbit(view, 0, -degreesOf(n, counted), pivot())),
  orbitUp: (n, counted) => move(orbit(view, 0, degreesOf(n, counted), pivot())),
  panLeft: (n) => move(pan(view, -panStep() * n, 0)),
  panRight: (n) => move(pan(view, panStep() * n, 0)),
  panDown: (n) => move(pan(view, 0, -panStep() * n)),
  panUp: (n) => move(pan(view, 0, panStep() * n)),
  dollyIn: (n) => move(dolly(view, n)),
  dollyOut: (n) => move(dolly(view, -n)),
  rollLeft: (n, counted) => move(roll(view, -degreesOf(n, counted))),
  rollRight: (n, counted) => move(roll(view, degreesOf(n, counted))),
  boneParent: () => walkBone((k, b) => k.parent(b)),
  boneChild: () => walkBone((k, b) => k.child(b)),
  bonePrevious: () => walkBone((k, b) => k.sibling(b, -1)),
  boneNext: () => walkBone((k, b) => k.sibling(b, 1)),
  boneMirror: () => walkBone((k, b) => k.mirror(b)),
  axisX: () => setAxis('x'),
  axisY: () => setAxis('y'),
  axisZ: () => setAxis('z'),
  saveFraming: () => saveFraming(),
  videoPlay: () => playVideo(),
  videoFrameBack: (n) => stepVideo((t, v) => stepFrames(t, -n, v.fps, v.duration)),
  videoFrameOn: (n) => stepVideo((t, v) => stepFrames(t, n, v.fps, v.duration)),
  videoSecondBack: (n) => stepVideo((t, v) => stepSeconds(t, -n, v.duration)),
  videoSecondOn: (n) => stepVideo((t, v) => stepSeconds(t, n, v.duration)),
  videoSlower: () => setSpeed(slower(state.speed)),
  videoFaster: () => setSpeed(faster(state.speed)),
  markA: () => setLoopMark('a'),
  markB: () => setLoopMark('b'),
  markC: () => setLoopMark('c'),
  zoom: () => toggleZoom(),
  sync: () => toggleSync(),
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
  const pressed = {
    holdTop: state.show === 'top', holdBottom: state.show === 'bottom', play: state.show === 'loop', clip: state.clip,
    videoPlay: isSynced() ? state.show === 'loop' : !player.paused, sync: state.sync, zoom: state.zoom,
    plates: !state.plates,
  };
  for (const el of document.querySelectorAll('[data-command]')) {
    if (el.dataset.command in pressed) el.setAttribute('aria-pressed', String(pressed[el.dataset.command]));
  }
  const kit = isKit();
  for (const c of ['save', 'undo', 'redo', 'reset', 'ok', 'saveFraming']) {
    const el = document.querySelector(`[data-command="${c}"]`);
    // A kit's joint resets; nothing else here is a kit's.
    if (el) el.disabled = (kit && c !== 'reset') || !state.item?.entry;
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
    section.querySelector('h3').textContent = g.focus ? `${g.name}, with the ${g.focus} focused` : g.name;
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
  } else if (kind === 'type') {
    const joint = state.typingJoint;
    state.typingJoint = null;
    if (joint) setTypedJoint(joint, text.trim());
    else setTyped(state.typing, text.trim());
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
  if (ev.key === 'Escape') {
    // Esc clears what is typed, or else lets go of the selected bone.
    if (!keys.pending && state.bone) { state.bone = null; paintBone(); paintStep(); remember(); message('No bone selected: the camera orbits the body'); }
    keys.reset();
    $('pending').textContent = '';
    return;
  }
  const token = tokenOf(ev);
  if (!token) return;
  const done = keys.feed(token, state.focus);
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
followChecks();
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
      followChecks();
    }
  }, 1000);
}
