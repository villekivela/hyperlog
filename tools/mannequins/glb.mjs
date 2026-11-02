// Reads and writes binary glTF, and moves the mannequin's body out of each clip into one file the
// clips share. Every clip holds the same mesh and skin, so they are baked once, into
// `mannequin.bin`, and each clip's glTF reads them from there by URI: the phone hands that file to
// gltfio's ResourceLoader, and a clip itself carries only its kit and its animation.
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

// three.js reports a file's progress with ProgressEvent, which Node doesn't have.
globalThis.ProgressEvent ??= class extends Event {
  constructor(type, { lengthComputable = false, loaded = 0, total = 0 } = {}) {
    super(type);
    Object.assign(this, { lengthComputable, loaded, total });
  }
};

const MAGIC = 0x46546c67;
const JSON_CHUNK = 0x4e4f534a;
const BIN_CHUNK = 0x004e4942;
const pad4 = (n) => (n + 3) & ~3;

/** A .glb's JSON and its binary chunk. */
export function readGlb(bytes) {
  if (bytes.readUInt32LE(0) !== MAGIC) throw new Error('Not a binary glTF');
  const jsonLength = bytes.readUInt32LE(12);
  const json = JSON.parse(bytes.subarray(20, 20 + jsonLength).toString('utf8'));
  const at = 20 + jsonLength;
  const bin = at < bytes.length ? bytes.subarray(at + 8, at + 8 + bytes.readUInt32LE(at)) : Buffer.alloc(0);
  return { json, bin };
}

/** A .glb of `json` and its binary chunk, each padded as the format asks. */
export function writeGlb(json, bin) {
  const text = Buffer.from(JSON.stringify(json), 'utf8');
  const jsonChunk = Buffer.concat([text, Buffer.alloc(pad4(text.length) - text.length, 0x20)]);
  const binChunk = Buffer.concat([bin, Buffer.alloc(pad4(bin.length) - bin.length)]);
  const chunk = (type, data) => {
    const head = Buffer.alloc(8);
    head.writeUInt32LE(data.length, 0);
    head.writeUInt32LE(type, 4);
    return Buffer.concat([head, data]);
  };
  const body = Buffer.concat([chunk(JSON_CHUNK, jsonChunk), chunk(BIN_CHUNK, binChunk)]);
  const head = Buffer.alloc(12);
  head.writeUInt32LE(MAGIC, 0);
  head.writeUInt32LE(2, 4);
  head.writeUInt32LE(12 + body.length, 8);
  return Buffer.concat([head, body]);
}

/** The buffer views of a glTF's skinned meshes and their skins: the mannequin's body. */
function bodyViews(json) {
  const views = new Set();
  const meshes = new Set((json.nodes ?? []).filter((n) => n.skin !== undefined).map((n) => n.mesh));
  for (const m of meshes) {
    for (const p of json.meshes[m].primitives) {
      for (const a of [...Object.values(p.attributes), p.indices].filter((x) => x !== undefined)) views.add(json.accessors[a].bufferView);
    }
  }
  for (const s of json.skins ?? []) views.add(json.accessors[s.inverseBindMatrices].bufferView);
  return [...views].sort((a, b) => a - b);
}

/**
 * Moves the mannequin's body out of every clip into one shared buffer, read by `uri`. Each clip's
 * body must be byte for byte the same; the clips keep everything else, repacked.
 */
export function shareBody(glbs, uri) {
  let shared = null;
  const clips = glbs.map(([id, bytes]) => {
    const { json, bin } = readGlb(bytes);
    const body = bodyViews(json);
    const used = new Set(body);
    for (const [i, a] of (json.accessors ?? []).entries()) {
      if (used.has(a.bufferView) && !body.includes(a.bufferView)) throw new Error(`${id}: accessor ${i} shares the body's data`);
    }
    const slice = (v) => bin.subarray(v.byteOffset ?? 0, (v.byteOffset ?? 0) + v.byteLength);
    // The body, as this clip holds it: the same views in the same order in every clip.
    const parts = body.map((i) => slice(json.bufferViews[i]));
    if (!shared) {
      let at = 0;
      const offsets = parts.map((p) => { const o = at; at = pad4(at + p.length); return o; });
      shared = { parts, offsets, bytes: Buffer.concat(parts.flatMap((p) => [p, Buffer.alloc(pad4(p.length) - p.length)])) };
    } else if (parts.length !== shared.parts.length || parts.some((p, i) => !p.equals(shared.parts[i]))) {
      throw new Error(`${id}: its mannequin isn't the same as the other clips'`);
    }
    // The clip's own views, packed in order into its own binary chunk.
    const own = [];
    let at = 0;
    json.bufferViews = json.bufferViews.map((v, i) => {
      const k = body.indexOf(i);
      if (k >= 0) return { ...v, buffer: 1, byteOffset: shared.offsets[k] };
      const o = at;
      own.push(slice(v), Buffer.alloc(pad4(v.byteLength) - v.byteLength));
      at = pad4(at + v.byteLength);
      return { ...v, buffer: 0, byteOffset: o };
    });
    const ownBin = Buffer.concat(own);
    json.buffers = [{ byteLength: ownBin.length }, { uri, byteLength: shared.bytes.length }];
    return [id, writeGlb(json, ownBin)];
  });
  return { clips, shared: shared?.bytes ?? Buffer.alloc(0) };
}

/**
 * Reads a clip with three.js as the phone reads it with gltfio: each external buffer from the
 * file of its name among `resources`.
 */
export async function loadClip(file, resources = []) {
  const byName = Object.fromEntries(await Promise.all(resources.map(async (r) => [path.basename(r), await readFile(r)])));
  const manager = new THREE.LoadingManager();
  manager.setURLModifier((url) => {
    const r = byName[path.basename(url)];
    return r ? `data:application/octet-stream;base64,${r.toString('base64')}` : url;
  });
  const bytes = await readFile(file);
  return new GLTFLoader(manager).parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '');
}
