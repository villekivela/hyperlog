// A selectorized donkey calf raise (refs/donkey_calf/ref.md): the lifter stands on a foot bar between
// two side frames, the trunk hinged forward onto a rest pad, forearms along it and hands on two
// handles, the low back under a pad on a lever. Each side frame is one closed triangle of tube: a
// rail along the floor, bending up at the front into a leg under the rest and at the rear into a strut
// to the top of an upright. The lever is a flat loop pivoted between the uprights, the pad hung under
// its front. A post on the stack's top plate carries two rollers that ride under the lever's arms, so
// the lever lifts the stack directly, with no cable.
//
// The lever is a body on a revolute joint at the pivot, its value how far it has turned up from level.
// Everything is laid out with `z` forward toward the rest and the lifter's ankles near the origin.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { mitered, pad, part, pivotJoint, rod, rodGeometry, span, stretchedTube, tube, weightStack } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

/** A place in the ref's terms: `d` forward of the base's rear, `y` up, both in metres. */
const REAR = -1.135;
const at = (d, y, x = 0) => V(x, y, REAR + d);

const HEIGHT = 1.473;
// The side frames: how far out they stand, and the tube, wide across and tall in the frame's plane.
const SIDE = { out: 0.368, wide: 0.051, tall: 0.102 };
// The foot bar's top, its middle, and its size.
const BAR = { top: 0.155, d: 1.265, wide: 0.787, deep: 0.105 };
// The lever's pivot, and how far it turns up from level at the stretch and at the top.
const PIVOT = [1.145, 0.4];
const STRETCH = rad(8.7);
const TOP = rad(17.6);
// The loop's arms: how far out, and their reach to the loop's front.
const LEVER = { out: 0.295, reach: 0.87 };
// The hip pad, hung under the loop: its reach, its middle below the lever's line, and its size.
const PAD = { reach: 0.8, drop: 0.115, wide: 0.67, deep: 0.23, thick: 0.085 };
// The rest pad's size, its middle and its tilt, front up.
const REST = { wide: 0.89, deep: 0.216, thick: 0.08, d: 1.808, top: 0.905, tilt: rad(4) };
// A handle from under the rest's front edge to its tip, this far out, and where along it the hand holds.
const HANDLE = { from: [1.91, 0.805], to: [2.115, 1.0], out: 0.25, hold: 0.35 };
// The stack's column, the post's reach along the lever from the pivot, and how far ahead of the post
// the rollers' axle stands, clear of the guide rods in the stack's plane.
const STACK_D = 0.79;
const POST_REACH = STACK_D - PIVOT[1];
const AXLE_AHEAD = 0.03;
// The stack's rise for each radian the lever turns, the post being that far from the pivot.
const RATIO = POST_REACH;

const sides = [-1, 1];
/** A part for the checks alone, a straight stand-in inside a mitred tube: the clip leaves it out. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };
/** Straight stand-ins along the bends of a mitred tube `w` across. */
const proxies = (points, w, M) => points.slice(1).map((p, i) => proxy(tube(w - 0.01, points[i], p, M.frame)));
/** Names what a part is, for the tests. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** A point on the lever turned `turn` up from level, `reach` out from the pivot and `rise` off its line. */
const onLever = (turn, reach, rise = 0) => at(...PIVOT.slice().reverse()).add(V(0, rise, reach).applyAxisAngle(X, -turn));
const handlePoint = (t) => HANDLE.from.map((a, i) => a + (HANDLE.to[i] - a) * t);

export default recipe({
  name: 'donkey_calf',
  bodies: { lever: revolute({ axis: V(-1, 0, 0), limits: [STRETCH, TOP] }) },
  contacts: [
    { body: 'lever', point: 'hipPad', landmark: 'hips' },
    { body: 'frame', point: 'rest', landmark: 'forearms' },
    { body: 'frame', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'bar', landmark: 'feet' },
  ],
  rep: { joint: 'lever', from: 'max' },
  stacks: [{ joint: 'lever', ratio: RATIO }],
  linkage: () => ({
    at: (value) => ({
      value,
      ball: [BAR.top, REAR + BAR.d],
      /** Where the pad's face meets the back. */
      padFace: onLever(value, PAD.reach, -PAD.drop - PAD.thick / 2),
      hand: (s) => at(...handlePoint(HANDLE.hold)).setX(s * HANDLE.out),
      across: () => V(0, HANDLE.to[1] - HANDLE.from[1], HANDLE.to[0] - HANDLE.from[0]).normalize(),
    }),
  }),
  standsAlone: { camera: { target: [0, 0.75, 0], halfHeight: 1.05, azimuth: 70, elevation: 12 } },
  build(_options, M) {
    const topOf = (s) => at(0.28, 1.273, s * SIDE.out);
    const frameOf = (s) => [
      topOf(s), at(0, 0.16, s * SIDE.out), at(0.06, 0.052, s * SIDE.out), at(1.44, 0.052, s * SIDE.out),
      at(1.52, 0.15, s * SIDE.out), at(1.86, 0.84, s * SIDE.out),
    ];
    const uprightOf = (s) => [at(0.88, 0.05, s * SIDE.out), topOf(s)];
    const open = { solid: false, tall: SIDE.tall };

    // The lever, built level about its pivot: two arms, a tie at the loop's front, the pad hung under it.
    const lever = new THREE.Group();
    lever.name = 'donkey_calf_lever';
    const arms = sides.map((s) => role(tube(0.051, V(s * LEVER.out, 0, 0), V(s * LEVER.out, 0, LEVER.reach), M.frame), 'leverArm'));
    const hipPad = role(pad(PAD.wide, PAD.thick, PAD.deep, M.vinyl, V(0, -PAD.drop, PAD.reach), { soft: 0.03 }), 'hipPad');
    lever.add(
      ...arms, hipPad,
      tube(0.051, V(-LEVER.out, 0, 0), V(LEVER.out, 0, 0), M.frame),
      tube(0.051, V(-LEVER.out, 0, LEVER.reach), V(LEVER.out, 0, LEVER.reach), M.frame),
      ...sides.map((s) => role(tube(0.03, V(s * 0.2, 0, PAD.reach), V(s * 0.2, -PAD.drop + PAD.thick / 2, PAD.reach), M.frame), 'padLug')),
      role(tube(0.03, V(-LEVER.out, 0, PAD.reach), V(LEVER.out, 0, PAD.reach), M.frame), 'padRod'),
    );
    lever.position.copy(at(...PIVOT.slice().reverse()));

    // The stack, and the post on its top plate carrying the rollers under the lever's arms.
    const weights = weightStack(at(STACK_D, 0), M, { rodTop: HEIGHT, toward: 1 });
    const post = stretchedTube(0.07, M.frame);
    const axle = role(part(rodGeometry(0.012), M.metal), 'rollerAxle');
    const rollers = sides.map(() => role(part(rodGeometry(0.03, 20), M.vinyl), 'roller'));
    let lifted = 0;
    const lay = () => {
      const under = onLever(-lever.rotation.x, POST_REACH + AXLE_AHEAD, -0.0255 - 0.03);
      span(post, weights.top(lifted), under.clone().setZ(at(STACK_D, 0).z));
      span(axle, under.clone().setX(-LEVER.out - 0.03), under.clone().setX(LEVER.out + 0.03));
      rollers.forEach((m, i) => {
        const x = sides[i] * LEVER.out;
        span(m, under.clone().setX(x - 0.025), under.clone().setX(x + 0.025));
      });
    };
    const place = (value) => {
      lever.rotation.x = -value;
      lay();
    };
    place(STRETCH);

    const restAt = at(REST.d, REST.top - REST.thick / 2);
    const rest = pad(REST.wide, REST.thick, REST.deep, M.vinyl, restAt, { soft: 0.02 });
    rest.rotation.x = -REST.tilt;
    const handles = sides.map((s) => {
      const m = role(part(rodGeometry(0.0125), M.vinyl, { held: true }), 'grip');
      span(m, at(...HANDLE.from, s * HANDLE.out), at(...HANDLE.to, s * HANDLE.out));
      return m;
    });
    const joints = sides.flatMap((s) => {
      const { housing, fixed } = pivotJoint(M, at(PIVOT[1], PIVOT[0], s * (LEVER.out + 0.05)), X);
      return [housing, ...fixed];
    });

    return {
      parts: [
        // Each side frame, its upright, and the beams between them.
        ...sides.flatMap((s) => [
          role(mitered(frameOf(s), SIDE.wide, M.frame, open), 'sideFrame'),
          ...proxies(frameOf(s), SIDE.wide, M),
          mitered(uprightOf(s), SIDE.wide, M.frame, open),
          ...proxies(uprightOf(s), SIDE.wide, M),
        ]),
        role(tube(0.08, at(0.38, 1.215, -SIDE.out), at(0.38, 1.215, SIDE.out), M.frame), 'crossbeam'),
        role(tube(0.06, at(STACK_D, 0.05, -SIDE.out), at(STACK_D, 0.05, SIDE.out), M.frame), 'crossbeam'),
        role(tube(0.06, at(1.72, 0.74, -SIDE.out), at(1.72, 0.74, SIDE.out), M.frame), 'crossbeam'),
        // The foot bar across the rails, its rubber on top, and the rest on its cross tube.
        pad(BAR.wide, BAR.deep, BAR.deep, M.frame, at(BAR.d, BAR.top - 0.01 - BAR.deep / 2), { soft: 0.006 }),
        pad(0.749, 0.01, 0.095, M.vinyl, at(BAR.d, BAR.top - 0.005), { soft: 0.002, solid: false }),
        rest,
        tube(0.05, at(1.85, 0.825, -SIDE.out), at(1.85, 0.825, SIDE.out), M.frame),
        ...handles,
        ...sides.map((s) => tube(0.03, at(...HANDLE.from, s * HANDLE.out), at(1.85, 0.825, s * HANDLE.out), M.frame)),
        ...weights.parts,
        ...joints,
        lever, post, axle, ...rollers,
      ],
      bodies: { lever: [lever] },
      stacks: {
        lever: {
          lift(l) {
            lifted = l;
            weights.lift(l);
            lay();
          },
        },
      },
      follow: lay,
      drive(P) {
        const value = P.linkage?.value ?? STRETCH;
        place(value);
        return {
          joints: { lever: { value, origin: at(...PIVOT.slice().reverse()) } },
          points: {
            hipPad: [onLever(value, PAD.reach, -PAD.drop)],
            rest: sides.map((s) => at(REST.d - 0.05, REST.top, s * HANDLE.out)),
            grips: sides.map((s) => P.hand(s).clone()),
            bar: P.ankles.map((a) => V(a.x, BAR.top, REAR + BAR.d)),
          },
        };
      },
    };
  },
});
