// The standing calf raise (#127), on its own machine: the shoulders under pads on a lever, the balls of
// the feet on a bar, the heels dropping below it and rising as high as they go. Sizes and ranges are
// from refs/standing_calf/ref.md.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage } from './stage.mjs';
import { CHECKS, checkStage } from './checks.mjs';
import { wpos } from './rig.mjs';

const ID = 'standing_calf_raise';
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
const stackHead = () => {
  let head;
  stage.kit.group.traverse((o) => { if (o.userData.stack) head = o; });
  return head;
};

test('the lift passes every check', () => {
  const checks = checkStage(prepare(ID), stage);
  assert.deepEqual(CHECKS.filter((c) => !checks[c].ok).map((c) => `${c}: ${checks[c].found}`), []);
});

test('the machine stands on the floor at its published size', () => {
  at(0);
  // The published height is the posts' tops: the arms rise past them in use.
  const box = new THREE.Box3();
  const arms = ['standing_calf_upper_arm', 'standing_calf_lower_arm'].map((n) => stage.kit.group.getObjectByName(n));
  const onArm = (o) => arms.some((a) => { for (let p = o; p; p = p.parent) if (p === a) return true; return false; });
  stage.kit.group.traverse((o) => { if (o.isMesh && !onArm(o)) box.expandByObject(o); });
  const size = box.getSize(new THREE.Vector3());
  assert.ok(Math.abs(box.min.y) < 0.005, `the lowest part ${box.min.y.toFixed(3)} off the floor`);
  assert.ok(Math.abs(size.y - 1.829) < 0.03, `${size.y.toFixed(3)} tall, published 1.829`);
  assert.ok(Math.abs(size.z - 1.473) < 0.02, `${size.z.toFixed(3)} long, published 1.473`);
  assert.ok(Math.abs(size.x - 1.143) < 0.02, `${size.x.toFixed(3)} wide, published 1.143`);
});

// The owner's bar (#241): no open tube end.
test('no tube on it stands open', () => {
  at(0);
  // An open length shows by its rim: an edge on one face. Extrusions, cylinders and spheres close by
  // construction: only the seam of their vertices shows here.
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

test('the heels drop below the bar at the stretch and rise as high as they go, the knees straight', () => {
  const top = at(0);
  const up = soleOf(top);
  const kneeTop = bend(top('thigh_l'), top('calf_l'), top('foot_l'));
  const bottom = at(1);
  const down = soleOf(bottom);
  assert.ok(up > 33 && up < 47, `the heel ${up.toFixed(0)}° up at the top, about 40`);
  assert.ok(down < -21 && down > -35, `the heel ${down.toFixed(0)}° at the stretch, about 28 below level`);
  for (const knee of [kneeTop, bend(bottom('thigh_l'), bottom('calf_l'), bottom('foot_l'))]) {
    assert.ok(knee > 172, `the knee ${knee.toFixed(0)}°: straight through the rep`);
  }
});

test('the legs stack over the feet: the shins lean about 11° at the stretch and near upright at the top', () => {
  const lean = (g) => {
    const shin = g('calf_l').sub(g('foot_l'));
    return (Math.atan2(shin.z, shin.y) * 180) / Math.PI;
  };
  const [top, stretch] = [lean(at(0)), lean(at(1))];
  assert.ok(Math.abs(top) < 7, `the shin ${top.toFixed(0)}° forward at the top, upright in the video`);
  assert.ok(stretch > 6 && stretch < 16, `the shin ${stretch.toFixed(0)}° forward at the stretch, about 11`);
});

test('every moving joint meets: the link pins to the fork, and the grips join the arm', () => {
  at(0.5);
  const boxOf = (role) => byRole(role).map((o) => new THREE.Box3().setFromObject(o));
  const [pin] = boxOf('linkPin');
  assert.ok(pin, 'a pin across the fork');
  assert.ok(pin.min.x < -0.09 && pin.max.x > 0.09, `the pin ${pin.min.x.toFixed(3)} to ${pin.max.x.toFixed(3)}, reaching both of the fork's tubes`);
  const grips = boxOf('grip');
  const arms = boxOf('armTube');
  const struts = boxOf('gripStrut');
  assert.equal(struts.length, 2, 'a strut each side');
  for (const strut of struts) {
    assert.ok(arms.some((a) => a.intersectsBox(strut)), 'a strut stops short of the arm');
    assert.ok(grips.some((g) => g.intersectsBox(strut)), 'a strut stops short of its grip');
  }
});

test('every support reaches the floor or its frame', () => {
  at(0);
  const boxOf = (role) => byRole(role).map((o) => new THREE.Box3().setFromObject(o));
  const posts = boxOf('post');
  const braces = boxOf('brace');
  assert.equal(braces.length, 2, 'a brace each side');
  for (const brace of braces) {
    assert.ok(posts.some((p) => p.intersectsBox(brace)), 'a brace stops short of its post');
    assert.ok(brace.min.y < 0.2, `a brace stops ${brace.min.y.toFixed(3)} up, above the rail`);
  }
  const [arch] = boxOf('arch');
  assert.ok(arch.min.y < 0.005, `the arch's upright stops ${arch.min.y.toFixed(3)} off the floor`);
  assert.ok(posts.every((p) => p.intersectsBox(arch)), 'the arch stops short of a post');
});

test('the low pulley hangs on the gusset, and the cable runs clear of the frame all through the rep', () => {
  const named = byRole;
  const [gusset] = named('gusset');
  assert.ok(gusset, 'a gusset');
  const plate = new THREE.Box3().setFromObject(gusset);
  assert.ok(plate.max.x > 0.15 && plate.min.x < -0.15, `the gusset ${plate.min.x.toFixed(3)} to ${plate.max.x.toFixed(3)}, reaching both posts`);
  const posts = named('post').map((o) => new THREE.Box3().setFromObject(o));
  assert.equal(posts.length, 2, 'two posts');
  for (const d of [0, 0.5, 1]) {
    at(d);
    for (const run of named('cable')) {
      const box = new THREE.Box3().setFromObject(run);
      for (const post of posts) assert.ok(!box.intersectsBox(post), `a cable through a post at ${d}, x ${box.min.x.toFixed(3)} to ${box.max.x.toFixed(3)}`);
    }
  }
  assert.ok(named('cable').length >= 4, 'the cable in its runs');
  // The stack's rods stand in the stack's own plane, so the run across to it passes in front of them.
  const across = named('cable').map((o) => new THREE.Box3().setFromObject(o)).find((b) => b.max.x - b.min.x > 0.2);
  assert.ok(stackHead().getWorldPosition(new THREE.Vector3()).z - across.max.z > 0.012, 'the run across meets the stack\'s guide rods');
});

test('the balls of the feet stay on the bar, the heels clear of the floor plate', () => {
  const place = [0, 0.5, 1].map((d) => { const g = at(d); return { ball: g('ball_l') }; });
  for (const { ball } of place) {
    assert.ok(Math.abs(ball.z - place[0].ball.z) < 0.012, `the ball moves to z ${ball.z.toFixed(3)}`);
    assert.ok(Math.abs(ball.y - place[0].ball.y) < 0.012, `the ball moves to ${ball.y.toFixed(3)} up`);
  }
  // The heel's underside: down from the ankle by its height, square to the tilted sole, and back along it.
  const g = at(1);
  const tilt = (soleOf(g) * Math.PI) / 180;
  const heel = g('foot_l').y - stage.rig.dims.ankleY * Math.cos(tilt) + 0.05 * Math.sin(tilt);
  assert.ok(heel > 0.05, `the heel ${heel.toFixed(3)} up at the stretch, the floor plate 0.018`);
});

test('the shoulders carry the pads up on their arc and the stack rises with them', () => {
  const pads = () => byRole('shoulderPad').map(wpos);
  const shoulderLow = at(1)('upperarm_l');
  const low = pads();
  const stackLow = stackHead().position.y;
  const shoulderHigh = at(0)('upperarm_l');
  const high = pads();
  const stackHigh = stackHead().position.y;
  assert.equal(low.length, 2, 'two pads');
  const rise = high[0].y - low[0].y;
  assert.ok(rise > 0.11 && rise < 0.2, `the pads rise ${rise.toFixed(3)}, about 0.155`);
  assert.ok(Math.abs(shoulderHigh.y - shoulderLow.y - rise) < 0.03, `the shoulders rise ${(shoulderHigh.y - shoulderLow.y).toFixed(3)} with them`);
  assert.ok(stackHigh - stackLow > 0.1, `the stack rises ${(stackHigh - stackLow).toFixed(3)}`);
});

test('the trunk stands upright over the bar, the hands on the grips with the palms in', () => {
  for (const d of [0, 0.5, 1]) {
    const g = at(d);
    const spine = g('neck_01').sub(g('pelvis'));
    const lean = (Math.atan2(spine.z, spine.y) * 180) / Math.PI;
    assert.ok(Math.abs(lean) < 8, `the trunk ${lean.toFixed(0)}° at ${d}`);
    assert.ok(Math.abs(g('upperarm_l').z - g('ball_l').z) < 0.1, `the shoulders ${g('upperarm_l').z.toFixed(3)} over the bar at ${d}`);
    for (const [k, side] of [['l', 1], ['r', -1]]) {
      // Closed fingers fold toward the palm, so their fold shows the way it faces.
      const fingers = g(`middle_01_${k}`).sub(g(`hand_${k}`)).normalize();
      const fold = g(`middle_04_leaf_${k}`).sub(g(`middle_01_${k}`)).projectOnPlane(fingers);
      assert.ok(fold.x * side < 0, `the ${k} palm faces ${fold.x.toFixed(3)} across at ${d}, away from the other hand`);
    }
  }
});
