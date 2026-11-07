// The donkey calf raise (#127), on its own machine: the trunk hinged forward onto a front rest, the hips
// under a pad on a lever, the balls of the feet on a bar. Sizes and ranges are from
// refs/donkey_calf/ref.md.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { V, heldAt, wpos } from './rig.mjs';

const ID = 'donkey_calf_raise';
const stage = await buildStage(prepare(ID));
const at = (d) => {
  stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  return (name) => wpos(stage.scene.getObjectByName(name));
};
const bend = (a, b, c) => (a.clone().sub(b).angleTo(c.clone().sub(b)) * 180) / Math.PI;
/** The sole's tilt, heel up positive: the line from the ankle to the ball, less its slope on a level foot. */
const soleOf = (g) => {
  const way = g('ball_l').sub(g('foot_l'));
  const { ankleY, ballY, ballAhead } = stage.rig.dims;
  return ((Math.atan2(-way.y, way.z) - Math.atan2(ankleY - ballY, ballAhead)) * 180) / Math.PI;
};
const byRole = (role) => {
  const found = [];
  stage.kit.group.traverse((o) => { if (o.userData.role === role) found.push(o); });
  return found;
};
const boxes = (role) => byRole(role).map((o) => new THREE.Box3().setFromObject(o));
const stackHead = () => {
  let head;
  stage.kit.group.traverse((o) => { if (o.userData.stack) head = o; });
  return head;
};

test('the lift passes every check', () => {
  const checks = checkStage(prepare(ID), stage);
  assert.deepEqual(CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`), []);
});

test('the machine stands on the floor at its published length and height', () => {
  at(0);
  const box = new THREE.Box3();
  const lever = stage.kit.group.getObjectByName('donkey_calf_lever');
  const onLever = (o) => { for (let p = o; p; p = p.parent) if (p === lever) return true; return false; };
  stage.kit.group.traverse((o) => { if (o.isMesh && !onLever(o)) box.expandByObject(o); });
  const size = box.getSize(new THREE.Vector3());
  assert.ok(Math.abs(box.min.y) < 0.005, `the lowest part ${box.min.y.toFixed(3)} off the floor`);
  assert.ok(Math.abs(size.y - 1.473) < 0.03, `${size.y.toFixed(3)} tall, published 1.473`);
  assert.ok(Math.abs(size.z - 2.21) < 0.13, `${size.z.toFixed(3)} long, published 2.21, the handles reading short`);
});

test('the heels drop at the stretch and rise past level at the top, the knees holding soft: the ankles do the work', () => {
  const top = at(0);
  const up = soleOf(top);
  const kneeTop = bend(top('thigh_l'), top('calf_l'), top('foot_l'));
  const bottom = at(1);
  const down = soleOf(bottom);
  const kneeBottom = bend(bottom('thigh_l'), bottom('calf_l'), bottom('foot_l'));
  assert.ok(up > 7 && up < 20, `the heel ${up.toFixed(0)}° up at the top, about 12.5`);
  assert.ok(down < -22 && down > -37, `the heel ${down.toFixed(0)}° at the stretch, about 29.5 below level`);
  for (const knee of [kneeTop, kneeBottom]) assert.ok(knee > 160 && knee < 174, `the knee ${knee.toFixed(0)}°: soft, neither locked nor sat down`);
  assert.ok(Math.abs(kneeTop - kneeBottom) < 8, `the knee ${kneeTop.toFixed(0)}° at the top and ${kneeBottom.toFixed(0)}° at the stretch: the knees are working`);
});

test('the trunk lies hinged about 20° above level, from the hips to the shoulders', () => {
  for (const d of [0, 0.5, 1]) {
    const g = at(d);
    const spine = g('upperarm_l').sub(g('thigh_l'));
    const rise = (Math.atan2(spine.y, spine.z) * 180) / Math.PI;
    assert.ok(rise > 13 && rise < 28, `the trunk ${rise.toFixed(0)}° above level at ${d}`);
  }
});

test('the balls of both feet stay on the bar', () => {
  for (const k of ['l', 'r']) {
    const balls = [0, 0.5, 1].map((d) => at(d)(`ball_${k}`));
    for (const b of balls) {
      assert.ok(b.distanceTo(balls[0]) < 0.012, `the ${k} ball moves ${b.distanceTo(balls[0]).toFixed(3)}`);
      assert.ok(Math.abs(b.z - 0.13) < 0.02, `the ${k} ball at z ${b.z.toFixed(3)}, the bar at 0.13`);
      assert.ok(b.y > 0.155, `the ${k} ball ${b.y.toFixed(3)} up, the bar's top 0.155`);
    }
  }
});

test('the stack stands where the lever puts it: 390 mm of reach times the tangent of its angle', () => {
  const headAt = (d) => { at(d); return stackHead().position.y; };
  const [top, stretch] = [headAt(0), headAt(1)];
  const expected = 0.39 * (Math.tan((17.6 * Math.PI) / 180) - Math.tan((8.7 * Math.PI) / 180));
  assert.ok(Math.abs(top - stretch - expected) < 0.006, `the stack rises ${(top - stretch).toFixed(3)}, the lever ${expected.toFixed(3)}`);
  const [low] = byRole('rollerPost').map((o) => o.scale.y);
  at(0);
  const [high] = byRole('rollerPost').map((o) => o.scale.y);
  assert.ok(Math.abs(high - low) < 0.02, `the post stretches from ${low.toFixed(3)} to ${high.toFixed(3)}, a rigid post`);
});

test('the hips carry the pad up on the lever and the stack rises with it', () => {
  const pad = () => wpos(byRole('hipPad')[0]);
  const hipsLow = at(1)('pelvis');
  const padLow = pad();
  const stackLow = stackHead().position.y;
  const hipsHigh = at(0)('pelvis');
  const padHigh = pad();
  const stackHigh = stackHead().position.y;
  const rise = padHigh.y - padLow.y;
  // More than the video's 70 mm: a leg of the mannequin's length needs it for the knees to hold still.
  assert.ok(rise > 0.09 && rise < 0.14, `the pad rises ${rise.toFixed(3)}, about 0.12`);
  assert.ok(Math.abs(hipsHigh.y - hipsLow.y - rise) < 0.02, `the hips rise ${(hipsHigh.y - hipsLow.y).toFixed(3)} with it`);
  assert.ok(stackHigh - stackLow > 0.025, `the stack rises ${(stackHigh - stackLow).toFixed(3)}`);
});

test('the elbows and the hands hold still on the rest, the palms in', () => {
  const place = [0, 1].map((d) => { const g = at(d); return [g('lowerarm_l'), heldAt(stage.rig, 'l').clone()]; });
  assert.ok(place[0][1].distanceTo(place[1][1]) < 0.01, `the hand moves ${place[0][1].distanceTo(place[1][1]).toFixed(3)} on its grip`);
  // The hips rise about twice as far: the trunk pivots on the shoulders.
  assert.ok(place[0][0].distanceTo(place[1][0]) < 0.065, `the elbow moves ${place[0][0].distanceTo(place[1][0]).toFixed(3)}`);
  for (const d of [0, 1]) {
    const g = at(d);
    for (const [k, side] of [['l', 1], ['r', -1]]) {
      const fingers = g(`middle_01_${k}`).sub(g(`hand_${k}`)).normalize();
      const fold = g(`middle_04_leaf_${k}`).sub(g(`middle_01_${k}`)).projectOnPlane(fingers);
      assert.ok(fold.x * side < 0, `the ${k} palm faces ${fold.x.toFixed(3)} across at ${d}, away from the other hand`);
    }
  }
});

test('the hip pad joins the lever: its rod runs arm to arm', () => {
  at(0);
  const [rod] = boxes('padRod');
  assert.ok(rod, 'a rod for the pad');
  const arms = boxes('leverArm');
  assert.ok(arms.every((a) => a.intersectsBox(rod)), `the rod ${rod.min.x.toFixed(3)} to ${rod.max.x.toFixed(3)} stops short of an arm`);
  assert.ok(boxes('padLug').every((l) => l.intersectsBox(rod) && l.intersectsBox(boxes('hipPad')[0])), 'a lug stops short of the rod or the pad');
  assert.equal(boxes('padLug').length, 2, 'two lugs');
});

test('the rollers ride under the lever, and every support reaches the floor or its frame', () => {
  for (const d of [0, 1]) {
    at(d);
    // Square to the arm's line, from the roller's middle: its radius and the arm's half depth.
    const [from, to] = [V(0, 0, 0), V(0, 0, 1)].map((p) => byRole('leverArm')[0].parent.localToWorld(p));
    const way = to.clone().sub(from).normalize();
    for (const roller of byRole('roller')) {
      const c = wpos(roller);
      const off = c.clone().sub(from).projectOnPlane(way).setX(0).length();
      assert.ok(Math.abs(off - 0.0555) < 0.006, `a roller ${off.toFixed(3)} off the arm's line at ${d}, 0.0555 to touch it`);
    }
  }
  assert.equal(boxes('roller').length, 2, 'two rollers');
  // The guide rods stand in the stack's own plane, so the axle crossing them has to stand clear of it.
  const [axle] = boxes('rollerAxle');
  const stackZ = stackHead().getWorldPosition(new THREE.Vector3()).z;
  assert.ok(axle.min.z - stackZ > 0.012, `the axle ${(axle.min.z - stackZ).toFixed(3)} off the guide rods' plane`);
  const sides = boxes('sideFrame');
  assert.equal(sides.length, 2, 'two side frames');
  for (const side of sides) assert.ok(side.min.y < 0.005, `a side frame stops ${side.min.y.toFixed(3)} off the floor`);
});

/**
 * A part as points along its centre line and a radius. A box round a tilted tube stands well clear of
 * the tube, so two parts a few centimetres apart would pass as touching.
 */
const lineOf = (mesh) => {
  mesh.geometry.computeBoundingBox();
  const { min, max } = mesh.geometry.boundingBox;
  const scale = new THREE.Vector3().setFromMatrixScale(mesh.matrixWorld);
  const size = max.clone().sub(min).multiply(scale);
  const axes = ['x', 'y', 'z'].sort((a, b) => size[b] - size[a]);
  const middle = min.clone().add(max).multiplyScalar(0.5);
  const n = Math.max(1, Math.ceil(size[axes[0]] / 0.02));
  const points = Array.from({ length: n + 1 }, (_, i) => {
    const p = middle.clone();
    p[axes[0]] = min[axes[0]] + ((max[axes[0]] - min[axes[0]]) * i) / n;
    return p.applyMatrix4(mesh.matrixWorld);
  });
  return { points, radius: size[axes[1]] / 2 };
};

// Any part of the frame that touches nothing shows here, whichever part it is: each must join, through
// parts that touch, one that stands on the floor. Parts on the lever must join the lever's own.
test('nothing floats: every fixed part joins the floor, every lever part joins the lever', () => {
  at(0.5);
  const lever = stage.kit.group.getObjectByName('donkey_calf_lever');
  const under = (o, top) => { for (let p = o; p; p = p.parent) if (p === top) return true; return false; };
  // The mitred lengths go by the straight stand-ins along them: their own box is the whole frame's.
  const moving = new Set(['rollerPost', 'rollerAxle', 'roller', 'sideFrame', 'leverLoop']);
  const parts = { frame: [], lever: [] };
  stage.kit.group.traverse((o) => {
    if (!o.isMesh || moving.has(o.userData.role) || o.userData.stack) return;
    const part = { name: o.name, ...lineOf(o) };
    (under(o, lever) ? parts.lever : parts.frame).push(part);
  });
  const touching = (a, b) => a.points.some((p) => b.points.some((q) => p.distanceTo(q) < a.radius + b.radius + 0.006));
  const unjoined = (list, seed) => {
    const reached = new Set(list.filter(seed));
    for (let grew = true; grew;) {
      grew = false;
      for (const p of list) if (!reached.has(p) && [...reached].some((r) => touching(r, p))) { reached.add(p); grew = true; }
    }
    return list.filter((p) => !reached.has(p)).map((p) => p.name);
  };
  assert.deepEqual(unjoined(parts.frame, (p) => p.points.some((q) => q.y - p.radius < 0.01)), []);
  assert.deepEqual(unjoined(parts.lever, (p) => p === parts.lever[0]), []);
});

// The owner's bar (#241): no open tube end.
test('no tube on it stands open', () => {
  at(0);
  const rims = [];
  stage.kit.group.traverse((o) => {
    if (!o.isMesh || !o.geometry.index) return;
    if (['ExtrudeGeometry', 'CylinderGeometry', 'SphereGeometry'].includes(o.geometry.type)) return;
    const seen = new Map();
    const i = o.geometry.index.array;
    const p = o.geometry.attributes.position;
    const key = (n) => `${p.getX(n).toFixed(4)},${p.getY(n).toFixed(4)},${p.getZ(n).toFixed(4)}`;
    for (let t = 0; t < i.length; t += 3) for (const [a, b] of [[i[t], i[t + 1]], [i[t + 1], i[t + 2]], [i[t + 2], i[t]]]) {
      const e = [key(a), key(b)].sort().join('|');
      seen.set(e, (seen.get(e) ?? 0) + 1);
    }
    if ([...seen.values()].some((n) => n === 1)) rims.push(o.name || o.geometry.type);
  });
  assert.deepEqual(rims, []);
});
