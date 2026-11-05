// Renders a pose to PNGs without a GPU, for pull requests and notes (README, Rendering a pose):
//
//   node render.mjs <id> [out-dir]     <id>-side.png and <id>-front.png, the top and the bottom of the rep
//   node render.mjs --clip <clip> [out-dir] [--at <commit>]     the same of a baked clip, the top, the middle and the bottom
//
// Equipment far in front of the body is cut away. A flat-shaded software rasteriser: orthographic, a depth buffer, the clay colours, drawn at 2x and
// averaged down. It shows the pose and its equipment, not the phone's lighting.
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { deflateSync } from 'node:zlib';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { openClip, poseFrame } from './glb.mjs';
import { clipFile, clipsAt } from './numbers.mjs';
import { wpos } from './rig.mjs';
import { buildStage } from './stage.mjs';

const PANEL = [560, 700];
const SS = 2;
const GROUND = [236, 236, 239];
const STAGE = [227, 227, 232];

/** Every triangle of the scene as three world points and a colour, the floor shadow left out. */
function triangles(scene) {
  const out = [];
  scene.updateMatrixWorld(true);
  scene.traverse((m) => {
    if (!m.isMesh || m.material?.name === 'shadow' || m.material?.transparent) return;
    const colour = m.material?.color ?? new THREE.Color(0x888888);
    const rgb = [colour.r, colour.g, colour.b].map((c) => Math.round(255 * c ** (1 / 2.2)));
    const index = m.geometry.index;
    const n = m.geometry.attributes.position.count;
    const points = [];
    const v = new THREE.Vector3();
    for (let i = 0; i < n; i++) {
      if (m.isSkinnedMesh) m.getVertexPosition(i, v).applyMatrix4(m.matrixWorld);
      else v.fromBufferAttribute(m.geometry.attributes.position, i).applyMatrix4(m.matrixWorld);
      points.push(v.clone());
    }
    const count = index ? index.count : n;
    for (let i = 0; i < count; i += 3) {
      const [a, b, c] = [0, 1, 2].map((k) => points[index ? index.getX(i + k) : i + k]);
      out.push([a, b, c, rgb, m.isSkinnedMesh]);
    }
  });
  return out;
}

/** The view: `right`, `up` and `toward` (to the camera) as unit vectors. */
const VIEWS = {
  side: { right: new THREE.Vector3(0, 0, -1), up: new THREE.Vector3(0, 1, 0), toward: new THREE.Vector3(1, 0, 0) },
  // Along the body's length from the head end for a lying lift, from the front for the rest.
  front: { right: new THREE.Vector3(1, 0, 0), up: new THREE.Vector3(0, 1, 0), toward: new THREE.Vector3(0, 0, 1) },
  // From in front and to the lifter's left, as a product photo is taken, with the whole machine in.
  threeQuarter: { right: new THREE.Vector3(0.82, 0, -0.57), up: new THREE.Vector3(0, 1, 0), toward: new THREE.Vector3(0.57, 0, 0.82), whole: true },
  frontLying: { right: new THREE.Vector3(-1, 0, 0), up: new THREE.Vector3(0, 1, 0), toward: new THREE.Vector3(0, 0, -1) },
};

/** The view called `name`: a lying lift's front is from its head end. */
const viewOf = (name, lying) => VIEWS[name === 'front' && lying ? 'frontLying' : name];

/** Draws the triangles into a width x height RGB buffer, `scale` pixels per metre, centred on `centre`. */
function draw(tris, view, centre, scale, [w, h]) {
  const W = w * SS;
  const H = h * SS;
  const rgb = new Uint8Array(W * H * 3);
  for (let i = 0; i < W * H; i++) rgb.set(STAGE, i * 3);
  const depth = new Float32Array(W * H).fill(-Infinity);
  const light = view.toward.clone().add(view.up.clone().multiplyScalar(0.8)).add(view.right.clone().multiplyScalar(-0.5)).normalize();
  const project = (p) => {
    const d = p.clone().sub(centre);
    return [W / 2 + d.dot(view.right) * scale * SS, H / 2 - d.dot(view.up) * scale * SS, d.dot(view.toward)];
  };
  for (const [a, b, c, base] of tris) {
    const normal = b.clone().sub(a).cross(c.clone().sub(a)).normalize();
    if (normal.dot(view.toward) < 0) normal.negate();
    const shade = 0.55 + 0.45 * Math.max(0, normal.dot(light));
    const colour = base.map((v) => Math.min(255, v * shade));
    const [p, q, r] = [a, b, c].map(project);
    const x0 = Math.max(0, Math.floor(Math.min(p[0], q[0], r[0])));
    const x1 = Math.min(W - 1, Math.ceil(Math.max(p[0], q[0], r[0])));
    const y0 = Math.max(0, Math.floor(Math.min(p[1], q[1], r[1])));
    const y1 = Math.min(H - 1, Math.ceil(Math.max(p[1], q[1], r[1])));
    const area = (q[0] - p[0]) * (r[1] - p[1]) - (r[0] - p[0]) * (q[1] - p[1]);
    if (Math.abs(area) < 1e-9) continue;
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const px = x + 0.5;
        const py = y + 0.5;
        const l1 = ((px - p[0]) * (r[1] - p[1]) - (r[0] - p[0]) * (py - p[1])) / area;
        const l2 = ((q[0] - p[0]) * (py - p[1]) - (px - p[0]) * (q[1] - p[1])) / area;
        const l0 = 1 - l1 - l2;
        if (l0 < 0 || l1 < 0 || l2 < 0) continue;
        const z = l0 * p[2] + l1 * q[2] + l2 * r[2];
        const at = y * W + x;
        if (z <= depth[at]) continue;
        depth[at] = z;
        rgb.set(colour, at * 3);
      }
    }
  }
  const out = new Uint8Array(w * h * 3);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      for (let k = 0; k < 3; k++) {
        let sum = 0;
        for (let j = 0; j < SS; j++) for (let i = 0; i < SS; i++) sum += rgb[((y * SS + j) * W + x * SS + i) * 3 + k];
        out[(y * w + x) * 3 + k] = Math.round(sum / (SS * SS));
      }
    }
  }
  return out;
}

const crc = (() => {
  const table = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
  return (buf) => { let c = 0xffffffff; for (const b of buf) c = table[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
})();

/** A PNG of RGB pixels. */
export function png(rgb, w, h) {
  const chunk = (type, data) => {
    const body = Buffer.concat([Buffer.from(type), data]);
    const out = Buffer.alloc(body.length + 8);
    out.writeUInt32BE(data.length, 0);
    body.copy(out, 4);
    out.writeUInt32BE(crc(body), body.length + 4);
    return out;
  };
  const head = Buffer.alloc(13);
  head.writeUInt32BE(w, 0);
  head.writeUInt32BE(h, 4);
  head.set([8, 2, 0, 0, 0], 8);
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) Buffer.from(rgb.buffer, y * w * 3, w * 3).copy(raw, y * (w * 3 + 1) + 1);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', head), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

/**
 * A cutaway: equipment standing well in front of the body (a cable tower's column) is left out of
 * the view, so it doesn't hide the lifter.
 */
function cutaway(tris, view) {
  if (view.whole) return tris;
  const near = Math.max(...tris.filter((t) => t[4]).flatMap(([a, b, c]) => [a, b, c].map((p) => p.dot(view.toward))));
  return tris.filter(([a, b, c, , body]) => body || (a.dot(view.toward) + b.dot(view.toward) + c.dot(view.toward)) / 3 < near + 0.3);
}

/** The `shots` side by side, seen from `view`, in one frame. */
function sideBySide(shots, view) {
  // One frame for all the panels: the bounds of the body and the kit in every shot.
  const box = new THREE.Box3();
  for (const tris of shots) for (const [a, b, c] of tris) if (a.y > 0.01 || b.y > 0.01) box.expandByPoint(a).expandByPoint(b).expandByPoint(c);
  // How far the box reaches along the view's right and up, from its corners, so a turned view holds it whole.
  const corners = [0, 1, 2, 3, 4, 5, 6, 7].map((i) => new THREE.Vector3(i & 1 ? box.max.x : box.min.x, i & 2 ? box.max.y : box.min.y, i & 4 ? box.max.z : box.min.z));
  const reach = (axis) => Math.max(...corners.map((c) => c.dot(axis))) - Math.min(...corners.map((c) => c.dot(axis)));
  const span = [reach(view.right), reach(view.up)];
  const scale = Math.min((PANEL[0] * 0.9) / span[0], (PANEL[1] * 0.9) / span[1]);
  const centre = box.getCenter(new THREE.Vector3());
  const panels = shots.map((tris) => draw(tris, view, centre, scale, PANEL));
  const [w, h] = PANEL;
  const gap = 6;
  const width = panels.length * w + (panels.length - 1) * gap;
  const image = new Uint8Array(width * h * 3);
  for (let i = 0; i < image.length / 3; i++) image.set(GROUND, i * 3);
  panels.forEach((p, k) => { for (let y = 0; y < h; y++) image.set(p.subarray(y * w * 3, (y + 1) * w * 3), (y * width + k * (w + gap)) * 3); });
  return png(image, width, h);
}

/** The rep's top and bottom side by side, seen from `view`. */
export async function renderPose(id, viewName, angle) {
  const entry = prepare(id, undefined, angle);
  const { scene, pose } = await buildStage(entry);
  const view = viewOf(viewName, entry.body?.trunk < -45);
  return sideBySide([0, 1].map((d) => { pose(d); return cutaway(triangles(scene), view); }), view);
}

/** The top, the middle and the bottom of the clip in `file`, side by side, seen from `view`. */
export async function renderClip(file, viewName) {
  const { scene, tracks } = await openClip(file);
  const last = tracks[0].times.length - 1;
  const shot = (f) => {
    poseFrame(scene, tracks, f);
    scene.traverse((m) => m.skeleton?.update());
    return triangles(scene);
  };
  poseFrame(scene, tracks, 0);
  // A lying lift's head lies toward -z, the way a pose's trunk says.
  const spine = wpos(scene.getObjectByName('Head')).sub(wpos(scene.getObjectByName('pelvis')));
  const view = viewOf(viewName, spine.z < -Math.abs(spine.y));
  return sideBySide([0, Math.round(last / 2), last].map((f) => cutaway(shot(f), view)), view);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  if (args.includes('--clip')) {
    const { values: { clip, at: commit }, positionals: [out = '.'] } = parseArgs({
      allowPositionals: true,
      options: { clip: { type: 'string' }, at: { type: 'string' } },
    });
    const file = await clipFile(clip, await clipsAt(commit));
    await mkdir(out, { recursive: true });
    for (const view of ['side', 'front']) {
      const to = path.join(out, `${clip}-${view}.png`);
      await writeFile(to, await renderClip(file, view));
      console.log(`rendered ${to}`);
    }
    process.exit(0);
  }
  // `--angle <degrees>` renders an adjustable-bench pose at that bench angle, its typical one by default.
  const at = args.indexOf('--angle');
  const angle = at < 0 ? undefined : Number(args.splice(at, 2)[1]);
  const viewAt = args.indexOf('--view');
  const only = viewAt < 0 ? null : args.splice(viewAt, 2)[1];
  const [id, out = '.'] = args;
  if (!id) throw new Error('usage: node render.mjs <id> [out-dir] [--angle <degrees>] [--view side|front|threeQuarter]');
  await mkdir(out, { recursive: true });
  for (const view of only ? [only] : ['side', 'front']) {
    const file = path.join(out, `${id}${angle === undefined ? '' : `-${angle}`}-${view}.png`);
    await writeFile(file, await renderPose(id, view, angle));
    console.log(`rendered ${file}`);
  }
}
