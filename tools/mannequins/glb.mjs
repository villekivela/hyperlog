// Reads and writes binary glTF, and moves the mannequin's body out of each clip into one file the
// clips share. Every clip holds the same mesh and skin, so they are baked once, into
// `mannequin.bin`, and each clip's glTF reads them from there by URI: the phone hands that file to
// gltfio's ResourceLoader, and a clip itself carries only its kit and its animation.
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

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

/** The buffer views of the meshes on `nodes` (indices), and of their skins: their geometry. */
function meshViews(json, nodes) {
  const views = new Set();
  for (const n of nodes) {
    const node = json.nodes[n];
    if (node.mesh !== undefined) {
      for (const p of json.meshes[node.mesh].primitives) {
        for (const a of [...Object.values(p.attributes), p.indices].filter((x) => x !== undefined)) views.add(json.accessors[a].bufferView);
      }
    }
    if (node.skin !== undefined) views.add(json.accessors[json.skins[node.skin].inverseBindMatrices].bufferView);
  }
  return views;
}

/** The nodes under `n` (indices), `n` included. */
function under(json, n) {
  return [n, ...(json.nodes[n].children ?? []).flatMap((c) => under(json, c))];
}

/** The file each buffer view of a glTF is shared in, by view: the mannequin's body to `body`, each kit's parts to `kit(name)`. */
function sharedViews(json, { body, kit }) {
  const files = new Map();
  const skinned = (json.nodes ?? []).flatMap((node, i) => (node.skin !== undefined ? [i] : []));
  for (const v of meshViews(json, skinned)) files.set(v, body);
  (json.nodes ?? []).forEach((node, i) => {
    if (!node.extras?.kit) return;
    for (const v of meshViews(json, under(json, i))) if (!files.has(v)) files.set(v, kit(node.extras.kit));
  });
  return files;
}

/**
 * Moves what the clips have in common out of each into files they share, read by URI: the
 * mannequin's mesh and skin into `body`, and each kit's parts (a node with `extras.kit`, and every
 * mesh under it) into `kit(name)`. A shared file holds each distinct view once, however many clips
 * read it. The body must be byte for byte the same in every clip; the clips keep everything else,
 * repacked. `shared` is each file's bytes, by URI.
 */
export function shareParts(glbs, { body, kit }) {
  const pools = new Map();
  const pool = (uri) => {
    if (!pools.has(uri)) pools.set(uri, { parts: [], at: 0, offsets: new Map() });
    return pools.get(uri);
  };
  // Each shared view once in its file, found again by its bytes.
  const placeIn = (uri, data) => {
    const p = pool(uri);
    const key = data.toString('base64');
    if (!p.offsets.has(key)) {
      p.offsets.set(key, p.at);
      p.parts.push(data, Buffer.alloc(pad4(data.length) - data.length));
      p.at = pad4(p.at + data.length);
    }
    return p.offsets.get(key);
  };
  // The first clip's body, which every other clip's must match.
  let firstBody = null;
  const clips = glbs.map(([id, bytes]) => {
    const { json, bin } = readGlb(bytes);
    const files = sharedViews(json, { body, kit });
    const slice = (v) => bin.subarray(v.byteOffset ?? 0, (v.byteOffset ?? 0) + v.byteLength);
    // The body, as this clip holds it: the same views in the same order in every clip.
    const bodyParts = [...files].filter(([, f]) => f === body).map(([v]) => v).sort((a, b) => a - b).map((v) => slice(json.bufferViews[v]));
    if (!firstBody) firstBody = bodyParts;
    else if (bodyParts.length !== firstBody.length || bodyParts.some((p, i) => !p.equals(firstBody[i]))) {
      throw new Error(`${id}: its mannequin isn't the same as the other clips'`);
    }
    // The files it reads: the mannequin's, then its kits', by name.
    const uris = [body, ...new Set([...files.values()].filter((f) => f !== body).sort())];
    // The clip's own views, packed in order into its own binary chunk.
    const own = [];
    let at = 0;
    json.bufferViews = json.bufferViews.map((v, i) => {
      const uri = files.get(i);
      if (uri) return { ...v, buffer: 1 + uris.indexOf(uri), byteOffset: placeIn(uri, slice(v)) };
      const o = at;
      own.push(slice(v), Buffer.alloc(pad4(v.byteLength) - v.byteLength));
      at = pad4(at + v.byteLength);
      return { ...v, buffer: 0, byteOffset: o };
    });
    const ownBin = Buffer.concat(own);
    return { id, json, ownBin, uris };
  });
  const shared = Object.fromEntries([...pools].map(([uri, p]) => [uri, Buffer.concat(p.parts)]));
  // A file's length is known once every clip has added its parts to it.
  return {
    clips: clips.map(({ id, json, ownBin, uris }) => {
      json.buffers = [{ byteLength: ownBin.length }, ...uris.map((uri) => ({ uri, byteLength: shared[uri].length }))];
      return [id, writeGlb(json, ownBin)];
    }),
    shared,
  };
}

/**
 * A clip made whole: each buffer it reads by URI, from `files` (its bytes by URI), appended to its
 * own binary chunk. The tests parse a clip whole, from memory, and `bakeOnly` shares it again.
 */
export function inlineResources(bytes, files) {
  const { json, bin } = readGlb(bytes);
  const parts = [bin, Buffer.alloc(pad4(bin.length) - bin.length)];
  const base = [0];
  let at = pad4(bin.length);
  for (const b of json.buffers.slice(1)) {
    const data = files[b.uri];
    if (!data) throw new Error(`No file for ${b.uri}`);
    base.push(at);
    parts.push(data, Buffer.alloc(pad4(data.length) - data.length));
    at = pad4(at + data.length);
  }
  const whole = Buffer.concat(parts);
  json.bufferViews = json.bufferViews.map((v) => ({ ...v, buffer: 0, byteOffset: (v.byteOffset ?? 0) + base[v.buffer] }));
  json.buffers = [{ byteLength: whole.length }];
  return writeGlb(json, whole);
}

/**
 * What a clip shows, whatever the files it shares are packed like: its glTF with each view's place
 * left out, then each view's bytes, from its own chunk or the file it reads (`files`, bytes by
 * URI). A clip reading the same parts from a kit file that has since grown gives the same bytes.
 */
export function clipContent(bytes, files) {
  const { json, bin } = readGlb(bytes);
  const sources = [bin, ...json.buffers.slice(1).map((b) => {
    if (!files[b.uri]) throw new Error(`No file for ${b.uri}`);
    return files[b.uri];
  })];
  const views = json.bufferViews.map((v) => sources[v.buffer].subarray(v.byteOffset ?? 0, (v.byteOffset ?? 0) + v.byteLength));
  const { buffers, bufferViews, ...rest } = json;
  const shape = { ...rest, bufferViews: bufferViews.map(({ buffer, byteOffset, ...v }) => v) };
  return Buffer.concat([Buffer.from(JSON.stringify(shape), 'utf8'), ...views]);
}

/** Reads a clip with three.js, each buffer it reads by URI from the file of that name among `resources`. */
export async function loadClip(file, resources = []) {
  const files = Object.fromEntries(await Promise.all(resources.map(async (r) => [path.basename(r), await readFile(r)])));
  const bytes = inlineResources(await readFile(file), files);
  return new GLTFLoader().parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '');
}
