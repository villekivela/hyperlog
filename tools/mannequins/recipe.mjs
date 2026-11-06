// The recipe format (ADR 0007): a kit is a fixed frame of parts from the library (parts.mjs) and
// moving bodies, each on a joint: revolute (a lever turning about its pivot) or prismatic (a sled
// or a carriage sliding along its rails), with the limits it moves between. A free weight is a
// free body, on no joint, carried by the mannequin.
//
// A recipe declares, as data:
//
// - `bodies`: each moving body by name, with its joint (`revolute`, `prismatic` or `free`) and
//   limits, and a revolute joint's `settings` where holes set it. A joint's value is in radians or metres from where its recipe measures it: from its
//   rest where it raises a stack, or a lever's angle up from level ahead.
// - `contacts`: where a body, or the fixed `frame`, ties to the mannequin, the point and the
//   landmark it meets (`hands`, `feet`, `shins`, `thighs`, `knees` (a thigh's lower end and the shin's top), `shoulders`, `chest`, `back`, `upperArms`, `forearms` or
//   `hips`), `hand` (`l` or `r`) where one arm's grip holds one hand, and `optional` where a pose
//   may use it or not, so nothing is held to it.
// - `stacks`: each weight stack and the joint whose travel raises it, by a ratio (metres a
//   radian, or a metre).
// - `fit`: its fit rules, the adjustments the machine's placard sets for the mannequin, each
//   naming the option it sets and how to `solve({ L, options, entry })` it from the mannequin's
//   measurements. The entry can set the option instead.
// - `rep` (a machine's): `{ joint, from }`, the joint the rep drives and the limit (`min` or `max`)
//   the top of the rep is at, the bottom at the other; an entry's `top` and `bottom` can give their
//   own, in degrees or metres, under the joint's name.
// - `linkage(options, L)` (a machine's): the machine with no pose, the body's place on it, `hip`
//   [y, z], for a mannequin of measurements `L`, and `at(value)`, what stands where with its joint at
//   `value`, which the motion hangs the limbs from (`fit.mjs`) and `drive` finds on the frame as
//   `P.linkage`. With no `rep`, `at` is one place whatever the value: a static machine's, or a
//   smith machine's rods, whose `line` gives z at a height for a free lift to solve against.
// - `standsAlone` (optional): `{ camera }`, for a recipe that stands where it goes with no pose,
//   its joints at rest; the studio shows it so, in that framing, while no pose uses it.
//
// Each of those may instead be a function of the kit's options. `build(options, materials)` makes
// the parts, in the order the bake writes them, and says which belong to which body (`bodies`) and
// which stack each stack's joint lifts (`stacks`). Its `drive` places the parts from the pose frame
// and returns each joint's value and where it is, and each contact's point. `stand(group)`, if it has one, stands the kit's group where it goes, and `follow()` lays
// again what hangs between a body and the frame (a cable to a cam) after a scrub has moved it. The
// parts are named after `node` (the recipe's name by default) in the clip.
import * as THREE from 'three';
import { group, tones } from './parts.mjs';

/**
 * A body turning about `axis` (in the kit's frame) between `limits` [min, max] radians, or set by
 * hand at one of `settings`, the radians its holes give it, where it has any.
 */
export const revolute = ({ axis, limits, settings }) => ({ type: 'revolute', axis, limits, ...(settings ? { settings } : {}) });

/** A body sliding along `axis` between `limits` [min, max] metres. */
export const prismatic = ({ axis, limits }) => ({ type: 'prismatic', axis, limits });

/** A body on no joint: a free weight, where the mannequin carries it. */
export const free = () => ({ type: 'free' });

/**
 * How far a working stack is held off its rest at the stretch: only before the first rep does it
 * sit down.
 */
export const HELD = 0.06;

/** The mannequin's landmarks a contact can tie a body to. */
export const LANDMARKS = ['hands', 'feet', 'shins', 'thighs', 'knees', 'shoulders', 'chest', 'back', 'upperArms', 'forearms', 'hips'];

/** A declaration that may be a function of the kit's options, for `options`. */
export const forOptions = (v, options) => (typeof v === 'function' ? v(options) : v);

/** A rigid move about a revolute joint, or along a prismatic one, by `by`. */
export function moveOf(joint, by) {
  const m = new THREE.Matrix4();
  if (joint.type === 'revolute') {
    const o = joint.origin;
    return m.makeTranslation(o.x, o.y, o.z)
      .multiply(new THREE.Matrix4().makeRotationAxis(joint.axis.clone().normalize(), by))
      .multiply(new THREE.Matrix4().makeTranslation(-o.x, -o.y, -o.z));
  }
  const d = joint.axis.clone().normalize().multiplyScalar(by);
  return m.makeTranslation(d.x, d.y, d.z);
}

/** Moves `node` by `move`, a matrix in the world, keeping it under its parent. */
function moveNode(node, move) {
  node.updateWorldMatrix(true, false);
  const world = move.clone().multiply(node.matrixWorld);
  const local = node.parent ? node.parent.matrixWorld.clone().invert().multiply(world) : world;
  local.decompose(node.position, node.quaternion, node.scale);
}

/**
 * Throws unless `name`'s declarations mean something: each one names what it ties, and each limit
 * is in order.
 */
function validate(name, { bodies, contacts, stacks, fit }) {
  const refuse = (why) => { throw new Error(`${name}: ${why}`); };
  for (const [body, { type, limits }] of Object.entries(bodies)) {
    if (type !== 'free' && !(limits?.[0] <= limits?.[1])) refuse(`${body}'s limits [${limits?.join(', ')}] are out of order`);
  }
  for (const c of contacts) {
    if (!bodies[c.body] && c.body !== 'frame') refuse(`a contact on "${c.body}", which isn't a body`);
    if (!LANDMARKS.includes(c.landmark)) refuse(`no landmark "${c.landmark}"`);
  }
  for (const { joint, ratio } of stacks) {
    if (!bodies[joint] || bodies[joint].type === 'free') refuse(`a stack on "${joint}", which isn't a joint`);
    if (typeof ratio !== 'number') refuse(`the stack on "${joint}" has no ratio`);
  }
  for (const f of fit) if (!f.sets) refuse(`the fit rule "${f.rule}" sets nothing`);
}

/** A recipe: `make(options)` builds the kit. */
export function recipe(def) {
  const { name, node = name, build } = def;
  return {
    ...def,
    make(options) {
      const bodies = forOptions(def.bodies ?? {}, options);
      const stacks = forOptions(def.stacks ?? [], options);
      const contacts = forOptions(def.contacts ?? [], options);
      validate(name, { bodies, contacts, stacks, fit: forOptions(def.fit ?? [], options) });
      const built = build(options, tones());
      const g = group(node, built.parts);
      built.stand?.(g);
      const joints = {};
      // Where each contact's point is now, by its name.
      let points = {};
      // Each stack, raised by its ratio of its joint's travel from rest.
      const lift = () => {
        for (const { joint, ratio } of stacks) built.stacks[joint].lift(HELD + ratio * joints[joint].value);
      };
      return {
        group: g,
        cables: built.cables ?? [],
        joints,
        /** Each body on a joint, by name: the nodes that move with it. */
        bodies: built.bodies ?? {},
        /** Whether it has a free body, which the mannequin carries. */
        free: Object.values(bodies).some((b) => b.type === 'free'),
        /** Each contact, with where its point is now (`at`, a point a side where the body has two). */
        get contacts() {
          return contacts.map((c) => ({ ...c, at: points[c.point] }));
        },
        place(P, P0, P1) {
          const state = built.drive(P, P0, P1) ?? {};
          for (const [body, { type }] of Object.entries(bodies)) {
            if (type !== 'free' && !state.joints?.[body]) throw new Error(`${name}: its drive gives ${body} no value`);
          }
          for (const [body, at] of Object.entries(state.joints ?? {})) {
            joints[body] = { ...bodies[body], ...at, axis: at.axis ?? bodies[body].axis };
          }
          points = state.points ?? {};
          lift();
        },
        /** Moves joint `body` to `value`, within its limits, its body rigidly with it. */
        scrub(body, value) {
          if (bodies[body]?.type === 'free') throw new Error(`${name}: ${body} is a free body, on no joint to scrub`);
          const joint = joints[body];
          if (!joint) throw new Error(`${name}: no joint ${body}`);
          const [min, max] = joint.limits;
          value = Math.min(max, Math.max(min, value));
          const by = value - joint.value;
          const move = moveOf(joint, by);
          for (const node of built.bodies[body]) moveNode(node, move);
          for (const c of contacts) if (c.body === body) points[c.point] = points[c.point].map((p) => p.clone().applyMatrix4(move));
          joint.value = value;
          lift();
          built.follow?.();
        },
      };
    },
  };
}
