// A plate-loaded smith machine with its bar on two guide rods (refs/smith_machine/ref.md): side-on an
// A on each floor beam. A front upright either side, a quarter of the way along the beam and leaning
// 7° with its top toward the back, carries on its rear face a plate of 13 hook notches 152 mm apart,
// the teeth toward the bar, and behind that the rod. A rear upright either side, at the beam's rear
// end, leans forward, wrapped in a pad, with four plate horns through its outer face, and its top
// sweeps forward over into the front upright. Crossbars join the front uprights at the top and the
// rear ones at the middle, a gusset each side under the rear one.
// Each carriage is a block round its rod, with the bar through it and a hook that rests on a notch
// and turns up off it for a lift; a safety stop lies on the plate, and the bar can't go below it.
//
// The bar is one body on a prismatic joint along the rods; its value is the metres it has moved
// along them from 250 mm above the floor. The hooks ride the bar, as its carriages do: a joint turning
// about the bar would be left behind by one sliding along the rods, so a hook rests on its notch with
// no pose, and stands turned up off the teeth for one, the wrists' turn.
// The frame stands where the ref puts it, or `z` along for a lift on a bench: `P.bar` is the bar's
// place on the rods' line, which the linkage gives a lift to solve against, or its value in a scrub,
// or the bar hangs on its hook where the pose frame gives none.
import * as THREE from 'three';
import { prismatic, recipe } from '../recipe.mjs';
import { along, curvedTube, floorRail, mitered, padGeometry, part, plateGeometry, rod, tube, turnedPart } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

const LEAN = rad(7);
// Up the rods, toward the back.
const UP = V(0, Math.cos(LEAN), -Math.sin(LEAN));
// The rod's foot, 250 mm above the floor, in the middle of the machine; the rods stand 1.3 m apart.
const FOOT = V(0, 0.25, 0.18);
const ROD = 0.65;
const RODS_TOP = 2.3;
// The upright is 90 mm square, ahead of the rod, beside it.
const UPRIGHT = { x: 0.71, w: 0.09, ahead: 0.11 };
const HEIGHT = 2.357;
// The hook plate stands on the upright's rear face, its notches 152 mm apart from 290 mm up: 13, the
// highest at 2.11 m. The carriage's hook rests on a notch and the stop's top is a carriage's half
// height below.
const NOTCH = { lowest: 0.29, pitch: 0.152, count: 13 };
// The plate over its floor beam, in line with the carriage's hook: the spine flush on the upright,
// ahead of the rod by this much, and the teeth back from it toward the bar.
const PLATE = 0.68;
const SPINE = { ahead: UPRIGHT.ahead - UPRIGHT.w / 2 - 0.015, deep: 0.03 };
const TOOTH = { ahead: 0.035, deep: 0.03 };
// The hook: how far it reaches, and how far the wrists turn it up off the notch for a lift.
const HOOK = { reach: 0.05, turn: rad(90) };
const CARRIAGE = 0.12;
const BLOCK = 0.07;
// The bar: 2.1 m, its sleeves outside the rods, its shaft 28 mm through, a barbell's.
const BAR = { half: 1.05, grip: 0.014 };
// The rear uprights rise from near the beams' rear ends, leaning forward, with four horns a side.
const REAR = { z: -0.62, lean: rad(10), pad: 1.8 };
// The hood sweeps forward from this height on the rear upright.
const HOOD = { from: 2.03 };
// The rear crossbar's height.
const CROSSBAR_Y = 1.95;
// The gusset from the crossbar, this far out, down to this height on the rear upright.
const GUSSET = { x: 0.4, y: 1.55 };
// The horns stand out of the pad's outer face, 200 mm.
const HORN = { x: UPRIGHT.x + UPRIGHT.w / 2 + 0.015, length: 0.2 };
const HORNS = [0.5, 0.93, 1.36, 1.79];
// The floor beams run 1.24 m along the machine.
const FLOOR = { front: 0.56, back: -0.68 };

/** Where the rear upright's middle is, `y` up, behind the middle. */
const rearAt = (y) => REAR.z + (y - 0.07) * Math.tan(REAR.lean);
const notchY = (k) => NOTCH.lowest + k * NOTCH.pitch;
/** The bar's value, in metres along the rods from 250 mm up, when its height is `y`. */
const valueAt = (y) => (y - FOOT.y) / Math.cos(LEAN);
/** The point `s` metres up the rod's line, on side `x`, and `z` further forward. */
const onRod = (x, s, z = 0) => FOOT.clone().addScaledVector(UP, s).add(V(x, 0, z));
/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
const tilted = (mesh) => { mesh.rotation.x = -LEAN; return mesh; };
/** A leaning pad `w` (x) by `h` (up the lean) by `d` (across it) centred on `at`. */
/** An upright from `foot` to `top`, cut flat on the floor beam, and a tube inside it the body meets. */
const upright = (foot, top, M) => [
  role(mitered([foot, top], UPRIGHT.w, M.frame, { solid: false, ends: [V(0, 1, 0), null] }), 'upright'),
  role(tube(UPRIGHT.w - 0.01, foot, top, M.frame), 'uprightCore'),
];
const leaning = (w, h, d, material, at, { soft = 0.003, solid = false } = {}) => {
  const mesh = part(padGeometry(w, h, d, soft), material, { solid });
  mesh.position.copy(at);
  return tilted(mesh);
};

export default recipe({
  name: 'smith_machine',
  bodies: ({ stops = 0 } = {}) => ({
    bar: prismatic({ axis: UP, limits: [valueAt(notchY(stops)), valueAt(RODS_TOP - 0.25)] }),
  }),
  // The bar can only ride the rods, so a lift solves its body against their line. `z` moves the whole
  // machine along, to bring the rods over a lift on a bench.
  linkage: ({ z = 0 } = {}) => ({ at: () => ({ line: (y) => z + FOOT.z - (y - FOOT.y) * Math.tan(LEAN) }) }),
  // A pose holds the bar in its hands, or on its upper back.
  contacts: [
    { body: 'bar', point: 'grips', landmark: 'hands' },
    // On the upper back, where a lift has it there: a bench press holds it in the hands alone.
    { body: 'bar', point: 'back', landmark: 'shoulders', optional: true },
  ],
  fit: [
    {
      sets: 'hook',
      rule: 'the notch one below the bar\'s standing height, for a short push to unrack it: about 1.35 m for a squat or a lunge, numbered from the lowest notch',
    },
    {
      sets: 'stops',
      rule: 'the notch just below the bottom of the lift, numbered from the lowest: the bar can go no lower',
    },
  ],
  // `hook`: the notch the bar hangs on; `stops`: the notch the safety stops lie on; `z`: how far along
  // the machine stands.
  build({ hook = 7, stops = 0, z = 0 } = {}, M) {
    const frame = [];
    const sides = [-1, 1];
    const topAt = HEIGHT - 0.035;
    // The floor beams, with the front uprights on them about 530 mm from their front ends.
    for (const s of sides) {
      const [beam, ...caps] = floorRail(V(s * UPRIGHT.x, 0.035, FLOOR.back), V(s * UPRIGHT.x, 0.035, FLOOR.front), M);
      frame.push(role(beam, 'beam'), ...caps);
      const base = onRod(s * UPRIGHT.x, valueAt(0.07), UPRIGHT.ahead);
      const top = onRod(s * UPRIGHT.x, valueAt(HEIGHT), UPRIGHT.ahead);
      frame.push(...upright(base, top, M));
      // The rod on the upright's inner face, bracketed at each end, with a rubber stop at its foot.
      const rodAt = (along) => onRod(s * ROD, along);
      frame.push(role(rod(0.0125, rodAt(valueAt(0.2)), rodAt(valueAt(RODS_TOP)), M.metal), 'guide'));
      for (const y of [0.2, RODS_TOP]) {
        frame.push(role(leaning(0.07, 0.05, 0.1, M.pulley, onRod(s * ROD, valueAt(y), 0.03), { solid: true }), 'bracket'));
      }
      frame.push(leaning(0.05, 0.03, 0.05, M.vinyl, onRod(s * ROD, valueAt(0.235)), { soft: 0.006 }));
      // The hook plate on the upright, from the floor beam up to the hood: a spine and a tooth under each hook.
      frame.push(role(leaning(0.012, (2.25 - 0.07) / Math.cos(LEAN), SPINE.deep, M.metal, onRod(s * PLATE, valueAt(1.16), SPINE.ahead)), 'spine'));
      for (let k = 0; k < NOTCH.count; k++) {
        frame.push(role(leaning(0.012, 0.07, TOOTH.deep, M.metal, onRod(s * PLATE, valueAt(notchY(k) - 0.05), TOOTH.ahead)), 'tooth'));
      }
      // The safety stop on the plate: a block under the carriage, its top a carriage's half height below the bar.
      frame.push(role(leaning(0.03, 0.03, 0.24, M.vinyl, onRod(s * PLATE, valueAt(notchY(stops) - CARRIAGE / 2 - 0.015), -0.05), { soft: 0.006, solid: true }), 'stop'));
      // The rear upright, its pad inside it and four horns outside, and the hood: the upright's top
      // swept forward over into the front upright near its top.
      const hoodTo = onRod(s * UPRIGHT.x, valueAt(topAt - 0.05), UPRIGHT.ahead);
      const onRear = (y, ahead = 0) => V(s * UPRIGHT.x, y, rearAt(y) + ahead);
      frame.push(...upright(onRear(0.07), onRear(HOOD.from), M));
      frame.push(role(curvedTube([onRear(HOOD.from - 0.05), onRear(HOOD.from + 0.08), onRear(hoodTo.y - 0.07, 0.05), onRear(hoodTo.y - 0.01, 0.16), V(s * UPRIGHT.x, hoodTo.y, hoodTo.z)], UPRIGHT.w, M.frame), 'hood'));
      const middle = 0.18 + REAR.pad / 2;
      const shroud = leaning(UPRIGHT.w + 0.03, REAR.pad / Math.cos(REAR.lean), 0.24, M.vinyl, V(s * UPRIGHT.x, middle, rearAt(middle)), { soft: 0.012, solid: true });
      shroud.rotation.x = REAR.lean;
      frame.push(role(shroud, 'cushion'));
      for (const y of HORNS) {
        frame.push(role(rod(0.025, V(s * (HORN.x), y, rearAt(y)), V(s * (HORN.x + HORN.length), y, rearAt(y)), M.metal), 'horn'));
        frame.push(turnedPart(plateGeometry(0.04, 0.02), M.pulley, V(s * (HORN.x + 0.01), y, rearAt(y))));
      }
    }
    // The crossbars: between the front uprights at the top, between the rear ones at the middle.
    const topFront = onRod(0, valueAt(topAt - 0.03), UPRIGHT.ahead);
    frame.push(tube(0.07, topFront.clone().setX(-UPRIGHT.x), topFront.clone().setX(UPRIGHT.x), M.frame));
    frame.push(role(tube(0.07, V(-UPRIGHT.x, CROSSBAR_Y, rearAt(CROSSBAR_Y)), V(UPRIGHT.x, CROSSBAR_Y, rearAt(CROSSBAR_Y)), M.frame), 'crossbar'));
    // A gusset each side, from the crossbar down to the rear upright.
    for (const s of sides) {
      frame.push(role(tube(0.06, V(s * GUSSET.x, CROSSBAR_Y, rearAt(CROSSBAR_Y)), V(s * UPRIGHT.x, GUSSET.y, rearAt(GUSSET.y)), M.frame), 'gusset'));
    }

    // The bar and its carriages, in the bar's own frame, about its middle: the shaft between the
    // carriages, a sleeve beyond each, a block round each rod and a lug back to the plate.
    const bar = new THREE.Group();
    const hooks = [];
    const shaft = along(BAR.grip, -ROD, ROD, M.metal, 24, { held: true });
    role(shaft, 'shaft');
    bar.add(shaft);
    for (const s of sides) {
      const outward = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
      bar.add(role(along(0.025, ...outward(ROD, BAR.half), M.metal, 24, { solid: true }), 'sleeve'));
      bar.add(role(leaning(BLOCK, CARRIAGE, BLOCK, M.pulley, V(s * ROD, 0, 0), { soft: 0.01, solid: true }), 'carriage'));
      // The hook turns about the bar, so it hangs in a node of its own.
      const hook = new THREE.Group();
      hook.position.x = s * PLATE;
      hook.add(role(leaning(0.02, 0.03, HOOK.reach, M.pulley, V(0, 0, HOOK.reach / 2)), 'lug'));
      hooks.push(hook);
      bar.add(hook);
    }

    const hung = Math.max(valueAt(notchY(hook)), valueAt(notchY(stops)));
    return {
      parts: [...frame, bar],
      bodies: { bar: [bar] },
      stand(g) { g.position.z = z; },
      drive(P) {
        // A pose gives the bar's place, on the rods' line, its hooks turned off the notches; a scrub
        // its value along them.
        const lifted = Boolean(P.bar?.isVector3);
        const value = lifted ? valueAt(P.bar.y) : P.bar ?? hung;
        bar.position.copy(FOOT).addScaledVector(UP, value);
        for (const hook of hooks) hook.rotation.x = lifted ? -HOOK.turn : 0;
        bar.updateMatrix();
        const onBar = (xs) => xs.map((x) => V(x, 0, z).applyMatrix4(bar.matrix));
        return {
          joints: { bar: { value } },
          points: { grips: onBar(P.grip ? [-P.grip, P.grip] : [-0.3, 0.3]), back: onBar([-0.2, 0.2]) },
        };
      },
    };
  },
});
