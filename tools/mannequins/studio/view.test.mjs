// The studio's free camera (owner, 2026-11-03, #164): it orbits on both axes, over the top and under
// the floor, round the selected bone or the body's centre, pans, dollies and rolls; a hand has a
// close-up; and the view is saved as the clip's framing, which the phone opens on.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { viewOf, placeOf, orbit, pan, dolly, roll, framingOf, closeUp, showPlates } from './view.mjs';
import { prepare } from '../entries.mjs';
import { buildStage } from '../stage.mjs';

const FRAMING = { target: [0, 0.87, 0], halfHeight: 1.02, azimuth: 62, elevation: 8 };
const near = (a, b, eps = 1e-6) => a.distanceTo(b) < eps;
const rad = THREE.MathUtils.degToRad;

/** Where `point` lands on the screen of a camera placed at `view`. */
function onScreen(view, point) {
  const camera = new THREE.PerspectiveCamera(26, 1, 0.05, 50);
  const { position, up, target } = placeOf(view);
  camera.position.copy(position);
  camera.up.copy(up);
  camera.lookAt(target);
  camera.updateMatrixWorld(true);
  return point.clone().project(camera);
}

test('an orbit round a point keeps that point where it is on the screen, as far away, on both axes', () => {
  const view = viewOf(FRAMING);
  const bone = new THREE.Vector3(0.2, 1.4, 0.1);
  const before = onScreen(view, bone);
  const far = placeOf(view).position.distanceTo(bone);
  for (const [yaw, pitch] of [[15, 0], [0, 20], [-40, -30]]) {
    const turned = orbit(view, yaw, pitch, bone);
    assert.ok(near(onScreen(turned, bone), before), `${yaw}°, ${pitch}°: the bone moved on the screen`);
    assert.ok(Math.abs(placeOf(turned).position.distanceTo(bone) - far) < 1e-9);
    assert.equal(turned.azimuth, view.azimuth + yaw);
    assert.equal(turned.elevation, view.elevation + pitch);
  }
  // Round the target, the target stays.
  assert.deepEqual(orbit(view, 30, 10).target, view.target);
});

test('an orbit goes over the top and under the floor without a jump', () => {
  // A degree either side of straight down from above: the camera and its up move a little, never flip.
  for (const across of [90, -90]) {
    const a = placeOf(orbit(viewOf(FRAMING), 0, across - 0.5 - FRAMING.elevation));
    const b = placeOf(orbit(viewOf(FRAMING), 0, across + 0.5 - FRAMING.elevation));
    // A degree's arc at the view's distance.
    const arc = rad(1) * viewOf(FRAMING).distance;
    assert.ok(a.position.distanceTo(b.position) < arc * 1.001, `${across}°: the camera jumped`);
    assert.ok(a.up.angleTo(b.up) < 0.02, `${across}°: up turned ${a.up.angleTo(b.up)}`);
  }
  // Past the top the camera looks down on the far side, upside down.
  const over = placeOf(orbit(viewOf(FRAMING), 0, 100 - FRAMING.elevation));
  assert.ok(over.up.y < 0);
});

test('a pan slides the view across the screen, a dolly closes in, and a roll turns the screen about the line of sight', () => {
  const view = orbit(viewOf(FRAMING), 20, 35);
  const centre = new THREE.Vector3(...view.target);
  // Panned 10 cm right and 5 cm up, what was at the centre is now left of it and below.
  const panned = pan(view, 0.1, 0.05);
  const was = onScreen(panned, centre);
  assert.ok(was.x < -0.01 && was.y < -0.005, `the old centre is at ${was.x.toFixed(3)}, ${was.y.toFixed(3)}`);
  assert.ok(Math.abs(new THREE.Vector3(...panned.target).distanceTo(centre) - Math.hypot(0.1, 0.05)) < 1e-9);
  assert.equal(panned.distance, view.distance);
  // In, the camera nears the target; out, by the same step, it goes back.
  const closer = dolly(view, 2);
  assert.ok(closer.distance < view.distance);
  assert.ok(Math.abs(dolly(closer, -2).distance - view.distance) < 1e-9);
  assert.deepEqual(closer.target, view.target);
  // Rolled 90°, what was up on the screen lies along it sideways; the camera stays where it was.
  const rolled = roll(view, 90);
  assert.ok(near(placeOf(rolled).position, placeOf(view).position));
  assert.ok(Math.abs(placeOf(rolled).up.dot(placeOf(view).up)) < 1e-9);
  // Panned rolled, the pan follows the screen: right on the screen is the rolled right.
  const rolledPan = pan(rolled, 0.1, 0);
  const at = onScreen(rolledPan, centre);
  assert.ok(at.x < -0.01 && Math.abs(at.y) < 1e-6, `rolled, the old centre is at ${at.x.toFixed(3)}, ${at.y.toFixed(3)}`);
});

test('a view is saved as the framing the phone opens on: what it looks at, how much it shows, its turn and tilt', () => {
  assert.deepEqual(framingOf(viewOf(FRAMING)), FRAMING);
  // Moved about, it keeps four places, as the bench's framings do, and its turn within a circle.
  const moved = dolly(pan(orbit(viewOf(FRAMING), 200.123456, -11.987654), 0.0123456, 0), 1);
  const framing = framingOf(moved);
  assert.equal(framing.azimuth, -97.8765);
  assert.equal(framing.elevation, -3.9877);
  assert.equal(framing.halfHeight, Number((FRAMING.halfHeight * 0.9).toFixed(4)));
  for (const v of framing.target) assert.equal(v, Number(v.toFixed(4)));
  // Over the top, it is the same camera from the far side the right way up; a roll the phone hasn't.
  const over = roll(orbit(viewOf(FRAMING), 0, 120 - FRAMING.elevation), 30);
  const back = framingOf(over);
  assert.equal(back.elevation, 60);
  assert.equal(back.azimuth, 62 - 180);
  assert.ok(near(placeOf(viewOf(back)).position, placeOf(over).position, 1e-3));
});

test('a hand\'s close-up looks at where it holds from in front, on its own side, a hand\'s breadth either way', () => {
  const hand = new THREE.Vector3(0.3, 1.1, 0.2);
  for (const side of [1, -1]) {
    const view = closeUp(hand, side);
    assert.deepEqual(view.target, hand.toArray());
    const { position } = placeOf(view);
    assert.equal(Math.sign(position.x - hand.x), side);
    assert.ok(position.z > hand.z, 'from behind');
    assert.ok(Math.abs(framingOf(view).halfHeight - 0.12) < 1e-9);
  }
});

// The owner's toggle (#164 comment): the plates hide, so the hands on the bar can be seen. A view
// setting only: nothing is saved, and the bake doesn't change.
test('the plates hide and show again, every part of the plate tone and nothing else', async () => {
  const { scene } = await buildStage(prepare('barbell_squat'));
  const parts = [];
  scene.traverse((o) => { if (o.isMesh) parts.push(o); });
  const plates = parts.filter((o) => o.material.name === 'plate');
  assert.ok(plates.length >= 4, `${plates.length} plates`);
  showPlates(scene, false);
  assert.ok(plates.every((o) => !o.visible));
  assert.ok(parts.filter((o) => o.material.name !== 'plate').every((o) => o.visible), 'the bar or the body hid too');
  showPlates(scene, true);
  assert.ok(parts.every((o) => o.visible));
});
