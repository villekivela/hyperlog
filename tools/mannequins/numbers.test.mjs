// Every frame's numbers, read from a baked clip. The clip here is a small one made by hand, so each
// expected number is the geometry it was built with and not something the tool worked out.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import * as THREE from 'three';
import { GLTFExporter } from 'three/examples/jsm/exporters/GLTFExporter.js';
import { numbersOf } from './numbers.mjs';
import { scratch } from './tmp.mjs';

const FRAMES = 3;
const times = [0, 0.5, 1];
const about = (axis, degrees) => new THREE.Quaternion().setFromAxisAngle(axis, (degrees * Math.PI) / 180).toArray();
const X = new THREE.Vector3(1, 0, 0);

/** A node called `name` under `parent`, at `at`. */
function node(parent, name, at = [0, 0, 0]) {
  const o = new THREE.Object3D();
  o.name = name;
  o.position.set(...at);
  parent.add(o);
  return o;
}

/** A body standing with its arms hanging, each limb a round length, and one bell. */
function standing() {
  const scene = new THREE.Scene();
  const pelvis = node(scene, 'pelvis', [0, 1, 0]);
  const neck = node(pelvis, 'neck_01', [0, 0.5, 0]);
  node(neck, 'Head', [0, 0.2, 0]);
  for (const [k, s] of [['l', 1], ['r', -1]]) {
    const upper = node(pelvis, `upperarm_${k}`, [s * 0.2, 0.45, 0]);
    const lower = node(upper, `lowerarm_${k}`, [0, -0.3, 0]);
    const hand = node(lower, `hand_${k}`, [0, -0.25, 0]);
    node(hand, `middle_01_${k}`, [0, -0.1, 0]);
    node(hand, `index_01_${k}`, [0.03, -0.1, 0]);
    node(hand, `pinky_01_${k}`, [-0.03, -0.1, 0]);
    const thigh = node(pelvis, `thigh_${k}`, [s * 0.1, 0, 0]);
    const calf = node(thigh, `calf_${k}`, [0, -0.5, 0]);
    const foot = node(calf, `foot_${k}`, [0, -0.45, 0]);
    node(foot, `ball_${k}`, [0, -0.05, 0.1]);
  }
  const kit = node(scene, 'kit');
  node(kit, 'dumbbell_l', [0.2, 1, 0]);
  return scene;
}

/** Writes a clip of `tracks` on the standing body, and reads its numbers. */
async function numbersFrom(tracks, scene = standing()) {
  const clip = new THREE.AnimationClip('lowering', 1, [new THREE.VectorKeyframeTrack('pelvis.position', times, [0, 1, 0, 0, 1, 0, 0, 1, 0]), ...tracks]);
  const glb = await new GLTFExporter().parseAsync(scene, { binary: true, animations: [clip] });
  const file = path.join(await scratch('numbers'), 'clip.glb');
  await writeFile(file, Buffer.from(glb));
  return numbersOf(file);
}

const turning = (name, axis, degrees) => new THREE.QuaternionKeyframeTrack(`${name}.quaternion`, times, degrees.flatMap((d) => about(axis, d)));
const near = (actual, expected, why, within = 0.01) => assert.ok(Math.abs(actual - expected) <= within, `${why}: ${actual} against ${expected}`);

test('a clip gives one row of numbers for each of its frames', async () => {
  const { frames } = await numbersFrom([turning('lowerarm_l', X, [0, 45, 90])]);
  assert.equal(frames.length, FRAMES);
  assert.deepEqual(frames.map((f) => f.t), times);
});

test('an elbow reads 180° straight and closes as the forearm turns', async () => {
  const { frames } = await numbersFrom([turning('lowerarm_l', X, [0, 45, 90])]);
  assert.deepEqual(frames.map((f) => Math.round(f.elbow_l)), [180, 135, 90]);
  assert.deepEqual(frames.map((f) => Math.round(f.elbow_r)), [180, 180, 180]);
});

const Z = new THREE.Vector3(0, 0, 1);

test('a bell reads its handle off level, and how far it turned since the last frame', async () => {
  const { frames } = await numbersFrom([turning('dumbbell_l', Z, [0, 10, 30])]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.tilt'])), [0, 10, 30]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.turn'])), [0, 10, 20]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.spin'])), [0, 0, 0]);
});

test('a bell turning about its own handle spins and stays level', async () => {
  const { frames } = await numbersFrom([turning('dumbbell_l', X, [0, 18, 18])]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.spin'])), [0, 18, 0]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.tilt'])), [0, 0, 0]);
});

test('a part that never moves has no numbers', async () => {
  const { frames } = await numbersFrom([turning('lowerarm_l', X, [0, 45, 90])]);
  assert.equal(frames[0]['dumbbell_l.tilt'], undefined);
});

test('the hand and the elbow give where they are, in metres', async () => {
  const { frames } = await numbersFrom([turning('lowerarm_l', X, [0, 45, 90])]);
  // The shoulder is at 1.45 m, the elbow 0.3 m below it, the wrist 0.25 m past that.
  assert.deepEqual(frames[0]['elbow_l.at'].map((v) => Math.round(v * 100)), [20, 115, 0]);
  assert.deepEqual(frames[0]['hand_l.at'].map((v) => Math.round(v * 100)), [20, 90, 0]);
  // A turn of 90° about x lays the forearm level, along -z.
  assert.deepEqual(frames[2]['hand_l.at'].map((v) => Math.round(v * 100)), [20, 115, -25]);
});

test('a tilt is positive with the end away from the body up, on either side', async () => {
  const scene = standing();
  scene.getObjectByName('dumbbell_l').position.x = -0.2;
  const left = await numbersFrom([turning('dumbbell_l', Z, [0, 10, 20])], scene);
  // At -x a turn about z lifts the +x end, the one toward the body.
  assert.deepEqual(left.frames.map((f) => Math.round(f['dumbbell_l.tilt']) + 0), [0, -10, -20]);
});

test('a handle laid the other way round reads the same tilt', async () => {
  // Half a turn about y, then 10° and 20° about z: the handle's +x now points to -x, its far end at +x down.
  const flipped = (d) => new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), Math.PI).multiply(new THREE.Quaternion().setFromAxisAngle(Z, (d * Math.PI) / 180)).toArray();
  const track = new THREE.QuaternionKeyframeTrack('dumbbell_l.quaternion', times, [0, 10, 20].flatMap(flipped));
  const { frames } = await numbersFrom([track]);
  assert.deepEqual(frames.map((f) => Math.round(f['dumbbell_l.tilt']) + 0), [0, -10, -20]);
});

test('the wrist reads its bend off the forearm, and its roll about it from the top', async () => {
  const { frames } = await numbersFrom([turning('hand_l', X, [0, 20, 30]), turning('lowerarm_r', new THREE.Vector3(0, 1, 0), [0, 40, 90])]);
  assert.deepEqual(frames.map((f) => Math.round(f.wrist_l)), [0, 20, 30]);
  // The forearm of the right arm hangs along y, so a turn about y rolls it and everything past it.
  assert.deepEqual(frames.map((f) => Math.abs(Math.round(f.roll_r))), [0, 40, 90]);
  assert.deepEqual(frames.map((f) => Math.round(f.roll_l)), [0, 0, 0]);
});

test('the knee, the hip and the shoulder read 180°, 180° and 0° standing with the arms down', async () => {
  const { frames } = await numbersFrom([turning('calf_l', X, [0, 45, 90]), turning('upperarm_r', X, [0, 30, 60])]);
  assert.deepEqual(frames.map((f) => Math.round(f.knee_l)), [180, 135, 90]);
  assert.equal(Math.round(frames[0].hip_l), 180);
  assert.deepEqual(frames.map((f) => Math.round(f.shoulder_r)), [0, 30, 60]);
});

test('the trunk reads its lean off straight up', async () => {
  const { frames } = await numbersFrom([turning('pelvis', X, [0, 30, 90])]);
  assert.deepEqual(frames.map((f) => Math.round(f.trunk)), [0, 30, 90]);
});
