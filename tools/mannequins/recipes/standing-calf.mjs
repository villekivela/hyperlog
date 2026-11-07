// A selectorized standing calf raise (refs/standing_calf/ref.md): the lifter stands facing two tall
// posts, the balls of the feet on a foot bar and the heels hanging off it over a floor plate, the
// shoulders under two pads and the hands on two grips. Two rails run from the posts to the foot bar,
// splaying as they go, on a foot block each. An upper arm, pads and grips on it, turns on a pivot at
// the posts' head; a link ties it to a lower arm on its own pivot, which rests on a bumper and whose
// tip pulls the cable to the stack, in a bay beside the posts on the lifter's right.
//
// Each arm is a body on a revolute joint at its own pivot, its value how far it has turned up from
// the stretch: they turn alike, the link's pins about as far out on each. Everything is laid out with
// `z` forward of the lifter, the posts ahead of the toes.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import {
  WHEEL, mitered, pad, part, pivotJoint, popPin, pulley, rod, rodGeometry, span, stretchedTube, tube, weightStack, wire,
} from '../parts.mjs';
import { UP, V, X, rad } from '../rig.mjs';

// The foot bar's top, its middle along z, and its size; the posts stand this far ahead of it.
const BAR = { top: 0.17, z: 0.14, wide: 0.536, deep: 0.088, thick: 0.038 };
const POST_Z = BAR.z + 0.87;
const POST_X = 0.19;
const HEIGHT = 1.829;
// The rails: their top, section, how far out they end at the bar and the kink on the way there.
const RAIL = { top: 0.18, wide: 0.07, tall: 0.085, out: 0.303, kink: POST_Z - 0.52, end: BAR.z - 0.06 };
// The floor plate, its back edge behind the bar, and how far the floor bracket runs behind the posts.
const PLATE_BACK = BAR.z - 0.35;
const BRACKET_BACK = POST_Z + 0.218;
// The arms' pivots, [y, z]. The upper arm's reach to the pads' middle, and the pads' size and spacing.
const PIVOT = [1.745, POST_Z];
const LOWER_PIVOT = [1.22, POST_Z];
const PAD = { reach: 0.87, wide: 0.19, long: 0.279, thick: 0.1, out: 0.2 };
// How far below level the arms lie at the stretch, and how far they turn up from there. The pads
// hang under the upper arm, their faces this far off its line.
const REST = rad(-1.3);
const TRAVEL = rad(10.4);
const PAD_DROP = PAD.thick + 0.0225;
// The lifter under the pads: the hips' place, the legs' share of their length, straight but not
// locked, and how far above the shoulder joints the pads' faces rest.
const HIPS_Z = 0.12;
const LEGS = 0.999;
const SHOULDER_TOP = 0.075;
// The arm's side tubes start here, clear of the posts they line up with.
const ARM_FROM = 0.2;
// Each link pin's reach along its arm and rise off it; the lower arm's tip.
const UPPER_PIN = [0.294, 0.06];
const LOWER_PIN = 0.335;
const LOWER_TIP = 0.425;
// The eye under the tip, where the cable ends.
const TIP_EYE = 0.04;
// How far off the bar the ball's joint is seated beyond the sole, so the tilted sole rides its edge.
const BALL_OFF = 0.012;
// A grip, from its foot to its head on the upper arm, [reach, rise]; where the hand holds it.
const GRIP = { foot: [0.33, -0.085], head: [0.4, 0.117], out: 0.26, hold: 0.3 };
// The stack's bay on the lifter's right: its column, the outer upright, and the arch's height. The
// stack stands in a plane just on the lifter's side of the posts, `RUN_Z`, clear of the arms' pivots.
const STACK_X = -0.533;
const ARCH = { x: -0.7465, top: 1.62, tube: 0.077 };
const RUN_Z = POST_Z - 0.095;
// The low pulley's height on the gusset, and the two corner wheels' height under the arch, a little
// nearer the lifter than the stack so the run across passes in front of its guide rods.
const LOW_WHEEL = 0.4;
const CORNER_Y = 1.52;
const CORNER_Z = RUN_Z - 0.02;
// The lower arm's two tubes, either side of the cable's rise.
const FORK = 0.09;
// The stack's rise for each radian the arms turn: about as far as the pads rise.
const RATIO = 0.9;

const sides = [-1, 1];

/** A point on an arm turned `turn` from level, `reach` out from its pivot and `rise` off its line. */
const onArm = ([y, z], turn, reach, rise = 0) => V(0, y, z).add(V(0, rise, -reach).applyAxisAngle(X, turn));
const gripPoint = (t) => GRIP.foot.map((a, i) => a + (GRIP.head[i] - a) * t);
/** A part for the checks alone, a straight stand-in inside a mitred tube: the clip leaves it out. */
const proxy = (mesh) => { mesh.userData.proxy = true; return mesh; };
/** Names what a part is, for the tests. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** Straight stand-ins along the bends of a mitred tube `w` across. */
const proxies = (points, w, M) => points.slice(1).map((p, i) => proxy(tube(w - 0.01, points[i], p, M.frame)));

export default recipe({
  name: 'standing_calf',
  bodies: { arm: revolute({ axis: X, limits: [0, TRAVEL] }), lowerArm: revolute({ axis: X, limits: [0, TRAVEL] }) },
  contacts: [
    { body: 'arm', point: 'pads', landmark: 'shoulders' },
    { body: 'arm', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'bar', landmark: 'feet' },
  ],
  rep: { joint: 'arm', from: 'max' },
  stacks: [{ joint: 'arm', ratio: RATIO }],
  linkage: () => ({
    at: (value) => {
      const turn = REST + value;
      return {
        value,
        ball: [BAR.top + BALL_OFF, BAR.z],
        /** The pads' faces over `z`. */
        padFace: (z) => PIVOT[0] + (PIVOT[1] - z) * Math.tan(turn) - PAD_DROP / Math.cos(turn),
        hipsZ: HIPS_Z, legs: LEGS, shoulderTop: SHOULDER_TOP,
        hand: (s) => onArm(PIVOT, turn, ...gripPoint(GRIP.hold)).setX(s * GRIP.out),
        across: () => V(0, GRIP.head[1] - GRIP.foot[1], GRIP.foot[0] - GRIP.head[0]).normalize().applyAxisAngle(X, turn),
      };
    },
  }),
  standsAlone: { camera: { target: [0, 0.9, 0.6], halfHeight: 1.1, azimuth: 60, elevation: 12 } },
  build(_options, M) {
    const barY = BAR.top - BAR.thick / 2;
    const railY = RAIL.top - RAIL.tall / 2;
    const rail = (s) => [V(s * POST_X, railY, POST_Z), V(s * POST_X, railY, RAIL.kink), V(s * RAIL.out, railY, RAIL.end)];
    const arch = [V(POST_X, ARCH.top, RUN_Z), V(ARCH.x + 0.1, ARCH.top, RUN_Z), V(ARCH.x, ARCH.top - 0.1, RUN_Z), V(ARCH.x, 0, RUN_Z)];

    // The upper arm, built level about its pivot: a cross tube between the posts and a spine from it
    // to the first tie, then a tube each side out past the pads, ties across them ahead of the head,
    // the pads hanging under them and the grips outside them.
    const upper = new THREE.Group();
    upper.name = 'standing_calf_upper_arm';
    const out = (s, reach, rise = 0) => V(s * PAD.out, rise, -reach);
    const pads = sides.map((s) => role(pad(PAD.wide, PAD.thick, PAD.long, M.vinyl, out(s, PAD.reach, -PAD_DROP + PAD.thick / 2), { soft: 0.03 }), 'shoulderPad'));
    const grips = sides.map((s) => {
      const m = role(part(rodGeometry(0.016), M.vinyl, { held: true }), 'grip');
      span(m, V(s * GRIP.out, GRIP.foot[1], -GRIP.foot[0]), V(s * GRIP.out, GRIP.head[1], -GRIP.head[0]));
      return m;
    });
    const armTubes = sides.map((s) => role(tube(0.045, out(s, ARM_FROM), out(s, PAD.reach + PAD.long / 2), M.frame), 'armTube'));
    // A strut up out of the arm's tube ahead of the fingers, and back over the hand to the grip.
    const strutOf = (s) => [out(s, 0.28), out(s, 0.28, 0.105), out(s, 0.395, 0.105), V(s * GRIP.out, 0.105, -0.395)];
    const struts = sides.flatMap((s) => {
      return [role(mitered(strutOf(s), 0.03, M.frame, { solid: false }), 'gripStrut'), ...proxies(strutOf(s), 0.03, M)];
    });
    upper.add(
      ...armTubes,
      tube(0.05, V(-POST_X + 0.04, 0, 0), V(POST_X - 0.04, 0, 0), M.frame),
      tube(0.05, V(0, 0, 0), V(0, 0, -ARM_FROM), M.frame),
      ...[ARM_FROM, UPPER_PIN[0]].map((reach) => tube(0.05, out(-1, reach), out(1, reach), M.frame)),
      ...struts,
      ...pads, ...grips,
    );
    // The lower arm, built level about its pivot: a fork out to a tie at the tip, the cable rising
    // between its tubes.
    const linkPin = role(rod(0.0127, V(-FORK - 0.02, 0, -LOWER_PIN), V(FORK + 0.02, 0, -LOWER_PIN), M.metal), 'linkPin');
    const lower = new THREE.Group();
    lower.name = 'standing_calf_lower_arm';
    lower.add(
      tube(0.05, V(-POST_X, 0, 0), V(POST_X, 0, 0), M.frame),
      ...sides.map((s) => tube(0.05, V(s * FORK, 0, 0), V(s * FORK, 0, -LOWER_TIP), M.frame)),
      tube(0.05, V(-FORK, 0, -LOWER_TIP), V(FORK, 0, -LOWER_TIP), M.frame),
      rod(0.012, V(0, 0, -LOWER_TIP), V(0, -TIP_EYE, -LOWER_TIP), M.metal),
      linkPin,
    );
    // The link: a post in a sleeve, a pull pin through the sleeve's head setting the pads' height.
    const link = stretchedTube(0.051, M.frame);
    const sleeve = role(stretchedTube(0.065, M.frame), 'linkSleeve');
    const linkPull = role(popPin(V(0, 0, 0), 1, M), 'linkPull');
    const strand = role(wire(M), 'cable');
    const drop = role(wire(M), 'cable');
    const weights = weightStack(V(STACK_X, 0, RUN_Z), M, { rodTop: ARCH.top - ARCH.tube / 2, toward: -1 });
    const lowWheel = V(0, LOW_WHEEL, CORNER_Z - WHEEL + 0.006);
    /** A wheel turning in the plane of the arch, for the cable's corners. */
    const corner = (x) => {
      const g = new THREE.Group();
      g.add(...pulley(V(0, 0, 0), M));
      g.rotation.y = Math.PI / 2;
      g.position.set(x, CORNER_Y, CORNER_Z);
      return g;
    };
    const run = (A, B) => role(rod(0.0045, A, B, M.metal), 'cable');
    const gusset = role(pad(0.3, 0.24, 0.06, M.frame, V(0, LOW_WHEEL, POST_Z - 0.08), { soft: 0.004 }), 'gusset');
    const posts = sides.map((s) => role(pad(0.08, HEIGHT, 0.14, M.frame, V(s * POST_X, HEIGHT / 2, POST_Z), { soft: 0.006 }), 'post'));

    const lay = () => {
      const [head, foot] = [onArm(PIVOT, upper.rotation.x, ...UPPER_PIN), onArm(LOWER_PIVOT, lower.rotation.x, LOWER_PIN)];
      const sleeveTop = foot.clone().lerp(head, 0.55);
      span(link, head, foot);
      span(sleeve, sleeveTop, foot);
      linkPull.position.copy(sleeveTop).addScaledVector(head.clone().sub(foot).normalize(), -0.03).add(V(0, 0, -0.05));
      span(strand, lowWheel.clone().setZ(lowWheel.z - WHEEL), onArm(LOWER_PIVOT, lower.rotation.x, LOWER_TIP, -TIP_EYE));
    };
    const place = (value) => {
      upper.rotation.x = REST + value;
      lower.rotation.x = REST + value;
      lay();
    };
    upper.position.set(0, ...PIVOT);
    lower.position.set(0, ...LOWER_PIVOT);
    place(0);

    return {
      parts: [
        // The posts, tied at the top and across under the lower arm, on a floor bracket behind.
        ...posts,
        tube(0.06, V(-POST_X, HEIGHT - 0.03, POST_Z), V(POST_X, HEIGHT - 0.03, POST_Z), M.frame),
        tube(0.08, V(-POST_X, 1.09, POST_Z), V(POST_X, 1.09, POST_Z), M.frame),
        ...sides.map((s) => pad(0.07, 0.05, 0.07, M.vinyl, V(s * FORK, 1.155, POST_Z - 0.005), { soft: 0.012 })),
        tube(0.07, V(-POST_X, 0.035, POST_Z), V(POST_X, 0.035, POST_Z), M.frame),
        tube(0.07, V(0, 0.035, POST_Z), V(0, 0.035, BRACKET_BACK), M.frame),
        tube(0.07, V(-POST_X, 0.035, BRACKET_BACK), V(POST_X, 0.035, BRACKET_BACK), M.frame),
        // The arms' pivots, a bearing each side on the posts.
        ...sides.flatMap((s) => [PIVOT, LOWER_PIVOT].flatMap(([y, z]) => {
          const { housing, fixed } = pivotJoint(M, V(s * (POST_X + 0.07), y, z), X);
          return [housing, ...fixed];
        })),
        // The rails, each on its foot block, and a brace up to its post.
        ...sides.flatMap((s) => [
          mitered(rail(s), RAIL.wide, M.frame, { solid: false, tall: RAIL.tall }),
          ...proxies(rail(s), RAIL.wide, M),
          tube(RAIL.wide, V(s * RAIL.out, 0.012, RAIL.end), V(s * RAIL.out, railY, RAIL.end), M.frame),
          pad(0.11, 0.012, 0.11, M.frame, V(s * RAIL.out, 0.006, RAIL.end), { soft: 0.003 }),
          role(tube(0.05, V(s * POST_X, RAIL.top, POST_Z - 0.28), V(s * POST_X, 0.54, POST_Z - 0.04), M.frame), 'brace'),
        ]),
        // The foot bar across the rails' ends, and the floor plate under the heels, tabbed to the foot blocks.
        pad(BAR.wide, BAR.thick, BAR.deep, M.vinyl, V(0, barY, BAR.z), { soft: 0.01 }),
        pad(0.455, 0.012, BAR.z - PLATE_BACK + 0.06, M.frame, V(0, 0.006, (BAR.z + 0.06 + PLATE_BACK) / 2), { soft: 0.004 }),
        ...sides.map((s) => pad(0.05, 0.008, 0.07, M.frame, V(s * 0.24, 0.006, RAIL.end), { soft: 0.002 })),
        pad(0.42, 0.006, BAR.z - PLATE_BACK, M.vinyl, V(0, 0.015, (BAR.z + PLATE_BACK) / 2), { soft: 0.002, solid: false }),
        // The stack's bay: the arch from across the posts, its upright, and a floor beam back to the post.
        role(mitered(arch, ARCH.tube, M.frame, { solid: false, ends: [null, UP] }), 'arch'),
        ...proxies(arch, ARCH.tube, M),
        tube(0.07, V(-POST_X, 0.035, RUN_Z), V(ARCH.x, 0.035, RUN_Z), M.frame),
        ...weights.parts,
        // The cable: the moving strand down to the low pulley on the gusset, then up between the
        // fork's tubes, round the two corner wheels and across to the stack.
        gusset, ...pulley(lowWheel, M),
        run(V(0, LOW_WHEEL, CORNER_Z), V(0, CORNER_Y, CORNER_Z)),
        corner(-WHEEL), corner(STACK_X + WHEEL),
        run(V(-WHEEL, CORNER_Y + WHEEL, CORNER_Z), V(STACK_X + WHEEL, CORNER_Y + WHEEL, CORNER_Z)),
        upper, lower, link, sleeve, linkPull, strand, drop,
      ],
      bodies: { arm: [upper], lowerArm: [lower] },
      stacks: {
        arm: {
          lift(l) {
            weights.lift(l);
            span(drop, V(STACK_X, CORNER_Y, CORNER_Z), weights.top(l));
          },
        },
      },
      follow: lay,
      drive(P) {
        const value = P.linkage?.value ?? 0;
        place(value);
        upper.updateWorldMatrix(true, true);
        return {
          joints: { arm: { value, origin: V(0, ...PIVOT) }, lowerArm: { value, origin: V(0, ...LOWER_PIVOT) } },
          points: {
            pads: pads.map((m) => m.getWorldPosition(new THREE.Vector3())),
            grips: sides.map((s) => P.hand(s).clone()),
            bar: P.ankles.map((a) => V(a.x, BAR.top, BAR.z)),
          },
        };
      },
    };
  },
});
