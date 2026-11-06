// A selectorized hip abduction / adduction machine (refs/hip_machine/ref.md): the lifter sits on a
// seat 520 mm up, back on a reclined pad, knees bent and feet on a plate at the foot of each arm's
// post, a knee pad on the post at knee height. A spine rakes up behind the pad from a cross member
// between two rear legs, and a hoop from its sides carries a grip each side of the seat. The two arms turn
// about one vertical axis on the centre line under the hips, 240 mm behind the seat's front edge;
// each is an L, a bar out from the axle and a bar forward to a post 430 mm ahead of it, and the lower
// leg rides with it. A tower, deep fore and aft with the weight stack's broad face looking to the
// side, stands ahead of the posts, as the maker's photos show.
//
// One machine: abduction has the pads on the outsides of the knees and the arms closed, adduction
// has them between the knees and the arms open. The `pads` option says which side, the `start`
// option how far open the arms stand, in the ratchet's 10° steps.
//
// The user sits facing +z, the hips over the axis. The left arm (`arm_l`, at +x) and the right one
// are each on a joint about the vertical. Its value is how far the arm is open, in radians from
// closed, where the arms lie parallel to the machine's length. Both arms move together, so the
// stack hangs on the left one's joint, rising as they open, or as they close with the pads inside.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { floorRail, mitered, pad, tube, weightStack, rod } from '../parts.mjs';
import { UP, V, rad } from '../rig.mjs';

/** The arms' range from closed, and the ratchet's step, in degrees. */
const OPEN = 50;
const STEP = 10;
/** The least an arm stands open with the pads between the knees, so the posts clear each other. */
const INNER_LEAST = 12;
/** The start the pads' side gives, in degrees, when none is asked for. */
const STARTS = { outer: 0, inner: 50 };
const PADS = Object.keys(STARTS);

/** The side handles, behind the seat's sweep, at the seat's height: out from the centre line, up, their length along z, and their rear end. */
const HANDLE = { x: 0.24, y: 0.54, length: 0.18, z: -0.26 };

/** The seat's top, its depth, its front edge along z, and its width. */
const SEAT = { top: 0.52, depth: 0.36, front: 0.14, width: 0.36 };
/** The backrest's foot along z, its thickness, height and length, and how far it leans back from upright. */
const BACK = { z: -0.22, depth: 0.08, from: 0.62, length: 0.5, lean: rad(15) };
/** The rear legs: from under the seat's back edge, out and back to the rear rail. */
const LEG = { top: V(0.19, 0.43, -0.24), foot: V(0.3, 0.07, -0.44) };
/** The spine behind the backrest, the height it kinks at, and the cross member it lands on. */
const SPINE = { width: 0.08, kink: 0.55, cross: 0.3 };
/** The tower's top, and the bar the stack's guide rods stand up into. */
const TOWER = 1.365;
const HEAD_Y = 1.175;
/** The tower with the stack stands ahead of the posts: two uprights, one ahead of the other. */
const STACK_Z = 0.85;
const UPRIGHT = { width: 0.13, depth: 0.07, spread: 0.19 };
const ARCH_RADIUS = 0.2;
/** The brace's foot on the floor beam, and how high it meets the rear upright. */
const BRACE = { foot: 0.54, top: 0.45 };
/** How far the hips sit above the seat, and where along z, the back on its pad. */
const SITS = 0.09;
const HIP_Z = -0.1;
/** The arms' axis, under the hips, as the placard's rule has it. */
const AXIS_Z = HIP_Z;
/** How far the stack rises, in metres a radian of the left arm's sweep. */
const STACK_RATIO = 0.3;
/** The arms: each at its own height on the one axle, 430 mm from it to the post. */
const ARM_Y = { l: 0.18, r: 0.24 };
const REACH = 0.43;
const BAR = 0.05;
/** The post's top and foot, and the foot plate's top above the floor, all of it clear of the beam it swings over. */
const POST_TOP = 0.72;
const POST_FOOT = 0.075;
const PLATE_TOP = 0.1;
/** The foot plate, from the heel to the toes, its middle this far ahead of the ankle; narrow enough to pass the tower's brace. */
const PLATE = { width: 0.13, length: 0.26, ahead: 0.1 };
/** The knee pad: thick, 280 high and 160 across, its middle 560 mm up. */
const PAD = { thick: 0.06, height: 0.28, depth: 0.16, y: 0.56 };
/** The mannequin's knee, from the centre line, and the half width of it. */
const KNEE = { x: 0.089, half: 0.056 };
/** Where the pad meets the knee in the left arm's frame: its face, and the pad's and post's middle. */
const FACES = {
  outer: KNEE.x + KNEE.half,
  inner: KNEE.x - KNEE.half,
};
const sides = [{ k: 'l', s: 1 }, { k: 'r', s: -1 }];

/** Which side the pads are on and the start the arms are set to, snapped to the ratchet's steps. */
function setting({ pads = 'outer', start } = {}) {
  if (!PADS.includes(pads)) throw new Error(`The hip machine has no pads "${pads}": ${PADS.join(' or ')}`);
  const least = pads === 'inner' ? INNER_LEAST : 0;
  const steps = Math.round((start ?? STARTS[pads]) / STEP) * STEP;
  return { pads, least, start: Math.min(OPEN, Math.max(Math.ceil(least / STEP) * STEP, steps)) };
}

/** The pad's middle and its post's, in the left arm's frame. */
function padAt(pads) {
  const way = pads === 'outer' ? 1 : -1;
  const middle = FACES[pads] + way * PAD.thick / 2;
  return { middle, post: middle + way * (PAD.thick / 2 + BAR / 2) };
}

/** Marks a part for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** A point of the closed arm on side `s`, carried round the axle by `value`. */
const turned = (p, s, value) => { p.applyAxisAngle(UP, s * value).z += AXIS_Z; return p; };
/** The rear leg on side `s` at height `y`. */
const legAt = (s, y) => {
  const at = LEG.top.clone().lerp(LEG.foot, (LEG.top.y - y) / (LEG.top.y - LEG.foot.y));
  at.x *= s;
  return at;
};
/** A part for the checks alone, a straight stand-in inside a mitred tube: the clip leaves it out. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };

export default recipe({
  name: 'hip_machine',
  bodies: (options) => {
    const { least } = setting(options);
    return Object.fromEntries(sides.map(({ k, s }) => [`arm_${k}`, revolute({ axis: UP.clone().multiplyScalar(s), limits: [rad(least), rad(OPEN)] })]));
  },
  contacts: [
    ...sides.flatMap(({ k }) => [
      { body: `arm_${k}`, point: `pad_${k}`, landmark: 'knees', hand: k },
      { body: `arm_${k}`, point: `plate_${k}`, landmark: 'feet', hand: k },
      { body: 'frame', point: `handle_${k}`, landmark: 'hands', hand: k },
    ]),
    { body: 'frame', point: 'seat', landmark: 'thighs' },
    { body: 'frame', point: 'back', landmark: 'back' },
  ],
  stacks: (options) => [{ joint: 'arm_l', ratio: setting(options).pads === 'inner' ? -STACK_RATIO : STACK_RATIO }],
  rep: { joint: 'arm_l', from: 'min' },
  linkage: (_, L) => ({
    hip: [SEAT.top + SITS, HIP_Z],
    at: (value) => {
      return {
        pivot: V(0, 0, AXIS_Z),
        ankle: (side) => turned(V(side * KNEE.x, PLATE_TOP + L.ankleY, REACH), side, value),
        ahead: (side) => V(0, 0, 1).applyAxisAngle(UP, side * value),
        sole: V(0, PLATE_TOP, 0),
        hand: (side) => V(side * HANDLE.x, HANDLE.y, HANDLE.z + HANDLE.length / 2),
        // Along the handle, the index finger forward.
        across: () => V(0, 0, 1),
      };
    },
  }),
  fit: [{
    sets: 'start',
    rule: 'abduction starts with the knees together, adduction with them apart: the ratchet\'s 10° step that puts the pads against the knees; the axis marker under the midpoint between the hips',
  }],
  // `pads`: `outer` (on the outsides of the knees, abduction) or `inner` (between them, adduction);
  // `start`: how far open the arms stand, in degrees.
  build(options, M) {
    const { pads, start } = setting(options);
    const at = padAt(pads);
    const rest = rad(start);
    const frame = [];
    const mesh = (m, name) => { frame.push(role(m, name)); };
    // The base: a floor beam the length of the machine, a cross beam under the foot and one under the stack.
    frame.push(...floorRail(V(0, 0.035, LEG.foot.z), V(0, 0.035, STACK_Z + UPRIGHT.spread), M));
    frame.push(...floorRail(V(-0.33, 0.035, STACK_Z), V(0.33, 0.035, STACK_Z), M));
    frame.push(...floorRail(V(-0.33, 0.035, LEG.foot.z), V(0.33, 0.035, LEG.foot.z), M));
    const backMiddle = V(0, BACK.from + BACK.length / 2 * Math.cos(BACK.lean), BACK.z - BACK.length / 2 * Math.sin(BACK.lean) - BACK.depth / 2);
    // The seat on its pan; the bracket under it and the backrest come after the spine.
    const seatMiddle = SEAT.front - SEAT.depth / 2;
    mesh(pad(SEAT.width, 0.075, SEAT.depth, M.vinyl, V(0, SEAT.top - 0.0375, seatMiddle), { soft: 0.024 }), 'seat');
    frame.push(pad(SEAT.width - 0.06, 0.012, SEAT.depth - 0.06, M.frame, V(0, SEAT.top - 0.081, seatMiddle), { soft: 0.003 }));
    // The spine: up from the cross member between the legs, kinked behind the seat, then raked with
    // the backrest, flush on its back. A tube inside each length is what the body meets.
    const rake = V(0, Math.cos(BACK.lean), -Math.sin(BACK.lean));
    const behind = V(0, -Math.sin(BACK.lean), -Math.cos(BACK.lean));
    const spineMiddle = backMiddle.clone().addScaledVector(behind, BACK.depth / 2 + SPINE.width / 2);
    const spineTop = spineMiddle.clone().addScaledVector(rake, BACK.length / 2 - 0.03);
    const kink = spineMiddle.clone().addScaledVector(rake, (SPINE.kink - spineMiddle.y) / rake.y);
    const spineFoot = V(0, SPINE.cross + 0.03, legAt(1, SPINE.cross).z);
    const spineZ = (y) => spineFoot.z + (y - spineFoot.y) / (kink.y - spineFoot.y) * (kink.z - spineFoot.z);
    frame.push(
      role(mitered([spineFoot, kink, spineTop], SPINE.width, M.frame, { solid: false, ends: [UP, null] }), 'spine'),
      proxy(tube(SPINE.width - 0.01, spineFoot, kink, M.frame)),
      proxy(tube(SPINE.width - 0.01, kink, spineTop, M.frame)),
      role(tube(0.06, legAt(-1, SPINE.cross), legAt(1, SPINE.cross), M.frame), 'crossMember'),
    );
    // A leg a side, cut flat on the rail and under the beam, and the seat on a bracket from the spine.
    const beamY = LEG.top.y;
    for (const { s } of sides) {
      frame.push(
        role(mitered([legAt(s, LEG.foot.y), legAt(s, beamY)], 0.07, M.frame, { solid: false, ends: [UP, UP] }), 'rearLeg'),
        proxy(tube(0.06, legAt(s, LEG.foot.y), legAt(s, beamY), M.frame)),
      );
    }
    frame.push(
      role(tube(0.05, legAt(-1, beamY), legAt(1, beamY), M.frame), 'seatBeam'),
      role(tube(0.05, V(0, beamY, spineZ(beamY)), V(0, beamY, seatMiddle), M.frame), 'seatBracket'),
    );
    const back = pad(SEAT.width, BACK.length, BACK.depth, M.vinyl, backMiddle, { soft: 0.024 });
    back.rotation.x = -BACK.lean;
    mesh(back, 'backrest');
    // The side handles beside the seat, each on half a hoop back and in to the spine.
    for (const { s } of sides) {
      frame.push(role(rod(0.015, V(s * HANDLE.x, HANDLE.y, HANDLE.z), V(s * HANDLE.x, HANDLE.y, HANDLE.z + HANDLE.length), M.vinyl, { held: true }), 'handle'));
      const corner = V(s * HANDLE.x, HANDLE.y, spineZ(HANDLE.y));
      const rearEnd = V(s * HANDLE.x, HANDLE.y, HANDLE.z);
      const end = V(s * SPINE.width / 2, HANDLE.y, spineZ(HANDLE.y));
      frame.push(
        role(mitered([rearEnd, corner, end], 0.03, M.frame, { solid: false }), 'handleStrut'),
        proxy(tube(0.025, rearEnd, corner, M.frame)),
        proxy(tube(0.025, corner, end, M.frame)),
      );
    }
    // The tower: the rear upright straight up, the front one swept round into the top in one length,
    // and the stack turned between them.
    const weights = weightStack(V(0, 0, 0), M, { rodTop: HEAD_Y, toward: 1 });
    const stack = new THREE.Group();
    stack.add(...weights.parts);
    stack.position.z = STACK_Z;
    stack.rotation.y = Math.PI / 2;
    const [rear, front] = [STACK_Z - UPRIGHT.spread, STACK_Z + UPRIGHT.spread];
    const bend = Array.from({ length: 13 }, (_, i) => {
      const a = (i / 12) * Math.PI / 2;
      return V(0, TOWER - ARCH_RADIUS + ARCH_RADIUS * Math.sin(a), front - ARCH_RADIUS + ARCH_RADIUS * Math.cos(a));
    });
    frame.push(
      pad(UPRIGHT.width, TOWER, UPRIGHT.depth, M.frame, V(0, TOWER / 2, rear), { soft: 0.01 }),
      role(mitered([V(0, 0, front), ...bend, V(0, TOWER, rear - UPRIGHT.depth / 2)], UPRIGHT.width, M.frame, { solid: false, tall: UPRIGHT.depth, ends: [V(0, 1, 0), null] }), 'towerArch'),
      proxy(pad(UPRIGHT.width, 1.15, UPRIGHT.depth, M.frame, V(0, 0.575, front), { soft: 0.01 })),
      // The panel under the top, where the maker's placard sits.
      pad(UPRIGHT.width, 0.19, front - rear - 0.12, M.frame, V(0, TOWER - 0.1, (front + rear - 0.12) / 2), { soft: 0.02 }),
      role(tube(0.05, V(0, HEAD_Y, rear), V(0, HEAD_Y, front), M.frame), 'headBar'),
      // The brace from the beam to the rear upright, ahead of where the plates swing.
      role(mitered([V(0, 0.07, BRACE.foot), V(0, BRACE.top, rear - UPRIGHT.depth / 2)], 0.04, M.frame, { solid: false, ends: [UP, V(0, 0, 1)] }), 'towerBrace'),
      proxy(tube(0.03, V(0, 0.08, BRACE.foot), V(0, BRACE.top, rear - UPRIGHT.depth / 2), M.frame)),
      stack,
    );
    // The one axle both arms turn on, from the base up to the seat's bracket.
    frame.push(role(tube(0.04, V(0, 0.07, AXIS_Z), V(0, beamY - 0.02, AXIS_Z), M.metal), 'axle'));

    // Each arm, in the frame of its closed self, turned about the axle by its value.
    const arms = Object.fromEntries(sides.map(({ k, s }) => {
      const y = ARM_Y[k];
      const post = s * at.post;
      const arm = new THREE.Group();
      arm.add(
        // The L, mitred at its corner and butted to the post; a tube inside each length is what the body meets.
        role(mitered([V(0, y, 0), V(post, y, 0), V(post, y, REACH - BAR / 2)], BAR, M.frame, { solid: false }), 'armBar'),
        proxy(tube(BAR - 0.01, V(0, y, 0), V(post, y, 0), M.frame)),
        proxy(tube(BAR - 0.01, V(post, y, 0), V(post, y, REACH - BAR / 2), M.frame)),
        role(tube(BAR, V(post, POST_FOOT, REACH), V(post, POST_TOP, REACH), M.frame), 'post'),
        role(pad(PAD.thick, PAD.height, PAD.depth, M.vinyl, V(s * at.middle, PAD.y, REACH), { soft: 0.015 }), 'pad'),
        // The foot plate on a bracket from the post's foot, under the foot.
        tube(0.02, V(post, POST_FOOT + 0.008, REACH), V(s * KNEE.x, POST_FOOT + 0.008, REACH), M.frame),
        role(pad(PLATE.width, 0.016, PLATE.length, M.frame, V(s * KNEE.x, PLATE_TOP - 0.008, REACH + PLATE.ahead), { soft: 0.003 }), 'footPlate'),
        pad(0.1, 0.03, 0.1, M.pulley, V(0, y, 0), { soft: 0.01 }),
      );
      return [k, arm];
    }));
    return {
      parts: [...frame, ...Object.values(arms)],
      bodies: { arm_l: [arms.l], arm_r: [arms.r] },
      stacks: {
        arm_l: {
          // With the pads inside, the arms lift it as they close from open.
          lift: (raised) => weights.lift(pads === 'inner' ? raised + STACK_RATIO * rad(OPEN) : raised),
        },
      },
      drive(P) {
        const value = P.linkage?.value ?? rest;
        const joints = {};
        const points = {
          seat: [V(0, SEAT.top, seatMiddle)],
          back: [V(0, backMiddle.y, backMiddle.z + BACK.depth / 2)],
        };
        for (const { k, s } of sides) {
          arms[k].rotation.y = s * value;
          arms[k].position.z = AXIS_Z;
          points[`pad_${k}`] = [turned(V(s * FACES[pads], PAD.y, REACH), s, value)];
          points[`plate_${k}`] = [turned(V(s * KNEE.x, PLATE_TOP, REACH), s, value)];
          points[`handle_${k}`] = [V(s * HANDLE.x, HANDLE.y, HANDLE.z + HANDLE.length / 2)];
          joints[`arm_${k}`] = { value, origin: V(0, ARM_Y[k], AXIS_Z) };
        }
        return { joints, points };
      },
    };
  },
});
