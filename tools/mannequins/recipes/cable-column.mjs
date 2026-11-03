// A cable column: a weight stack between two uprights, with a carriage at `pulley` metres up its
// front whose pulley swivels toward the cable, standing at `at` [x, z] and facing the middle of
// the room. `attachment` is `handle` (a stirrup in the hand on side `hand`, 1 the left) or `rope`
// (its two ends in both hands). With `twin`, a second column stands mirrored at [-x, z] for the
// other hand, a stirrup in each; with `crossed` too, each hand takes the far column's cable.
//
// The handles or the rope are a free body, carried by the hands. Each column's stack rides its
// guide rods on a prismatic joint (`stack`, or `stack_l` and `stack_r` for the column at +x and the
// one at -x), its value how far that column's cable is drawn past where the stack rests, and rises
// one for one with it.
import * as THREE from 'three';
import { recipe, free, prismatic } from '../recipe.mjs';
import {
  STIRRUP, clevis, floorRail, knob, nameAll, pad, part, pulley, rod, rodGeometry, span, stackFrame, stirrup, turnTo, weightStack,
} from '../parts.mjs';
import { V, X, UP } from '../rig.mjs';

/** Each column's joint, by column: the one at +x first. */
const stacksOf = ({ twin = false } = {}) => (twin ? ['stack_l', 'stack_r'] : ['stack']);

export default recipe({
  name: 'cable_column',
  bodies: (o) => ({ attachment: free(), ...Object.fromEntries(stacksOf(o).map((j) => [j, prismatic({ axis: UP, limits: [0, 1.2] })])) }),
  contacts: ({ attachment = 'handle', hand = 1, twin = false } = {}) => (attachment === 'rope'
    ? [{ body: 'attachment', point: 'rope', landmark: 'hands' }]
    : (twin ? [1, -1] : [hand]).map((s) => ({ body: 'attachment', point: `handle_${s < 0 ? 'r' : 'l'}`, landmark: 'hands' }))),
  stacks: (o) => stacksOf(o).map((joint) => ({ joint, ratio: 1 })),
  build(options, M) {
    const { at = [0.8, 0], pulley: carriageY = 0.2, attachment = 'handle', hand = 1, twin = false, crossed = false } = options ?? {};
    const height = 2.15;
    const stackZ = -0.16;
    const columns = (twin ? [1, -1] : [1]).map((mirror) => {
      const column = new THREE.Group();
      column.name = `cable_column_${mirror < 0 ? 'r' : 'l'}`;
      const position = V(mirror * at[0], 0, at[1]);
      column.position.copy(position);
      // Its front faces the middle of the room.
      column.rotation.y = Math.atan2(-position.x, -position.z);
      const weights = weightStack(V(0, 0, stackZ), M, { rodTop: height - 0.25, toward: 1 });
      const carriage = pad(0.24, 0.12, 0.1, M.pulley, V(0, carriageY, 0.05), { soft: 0.01 });
      const swivel = new THREE.Group();
      swivel.position.set(0, carriageY, 0.13);
      swivel.add(...pulley(V(0, 0, 0), M));
      const parts = [
        ...stackFrame(stackZ, height, M),
        ...floorRail(V(-0.4, 0.035, stackZ), V(0.4, 0.035, stackZ), M),
        ...floorRail(V(0, 0.035, stackZ - 0.25), V(0, 0.035, 0.3), M),
        ...[-1, 1].map((s) => rod(0.012, V(s * 0.07, 0.12, 0.05), V(s * 0.07, height - 0.1, 0.05), M.metal)),
        // The carriage's rods end in brackets on the column, top and bottom.
        ...[0.11, height - 0.09].map((y) => pad(0.2, 0.02, 0.06, M.frame, V(0, y, 0.04), { soft: 0.004, solid: false })),
        carriage,
        knob(M, V(0, carriageY, 0.11)),
        ...weights.parts,
        swivel,
      ];
      const drop = part(rodGeometry(0.0045, 8), M.metal);
      const rise = part(rodGeometry(0.0045, 8), M.metal);
      parts.push(drop, rise);
      parts.forEach((p) => column.add(p));
      nameAll(column, column.name);
      const yaw = column.rotation.y;
      const local = (p) => p.clone().sub(position).applyAxisAngle(UP, -yaw);
      return {
        column,
        exit: position.clone().add(V(0, carriageY, 0.13).applyAxisAngle(UP, yaw)),
        // Swivels the pulley toward the hook.
        aim(hook) {
          const h = local(hook);
          swivel.rotation.y = Math.atan2(h.x, h.z - 0.13);
        },
        // Raises the stack `l`, the cable up the column and down to its head with it.
        lift(l) {
          weights.lift(l);
          span(rise, V(0, carriageY + 0.05, 0.05), V(0, height - 0.06, 0.05));
          span(drop, V(0, height - 0.06, stackZ), weights.top(l));
        },
      };
    });
    const joints = stacksOf(options);
    // The attachments, in the room's own frame.
    const ends = [];
    let rope = null;
    let handles = null;
    if (attachment === 'rope') {
      const strands = [-1, 1].map((s) => ({ s, strand: part(rodGeometry(0.011, 10), M.vinyl, { held: true }), end: knob(M, V(0, 0, 0), 0.02) }));
      strands.forEach(({ strand, end }) => ends.push(strand, end));
      const ring = clevis(M, 0);
      ends.push(ring);
      rope = { strands, ring };
    } else if (attachment === 'handle') {
      handles = (twin ? [1, -1] : [hand]).map((s) => ({ s, handle: stirrup(M, `cable_handle_${s < 0 ? 'r' : 'l'}`) }));
      handles.forEach(({ handle }) => ends.push(handle));
    } else throw new Error(`No cable attachment "${attachment}" on a column`);
    const cables = columns.map(() => part(rodGeometry(0.0045, 8), M.metal));
    // The column a hand's cable runs to.
    const columnOf = (s) => (twin ? columns[(crossed ? -s : s) > 0 ? 0 : 1] : columns[0]);
    const jointOf = (c) => joints[columns.indexOf(c)];
    // Where each cable hooks on: a stirrup's ring, or the rope's.
    const hookOn = rope
      ? [{ cable: cables[0], hook: () => rope.ring.getWorldPosition(V(0, 0, 0)) }]
      : handles.map(({ s, handle }) => ({ cable: cables[columns.indexOf(columnOf(s))], hook: () => handle.localToWorld(V(0, STIRRUP, 0)) }));
    // The stack rests where the cable is drawn least, at whichever end of the rep that is.
    let rests = null;
    const hooksAt = (P) => {
      if (rope) {
        const mid = P.hand(1).clone().add(P.hand(-1)).multiplyScalar(0.5);
        return [{ c: columns[0], hook: mid.addScaledVector(columns[0].exit.clone().sub(mid).normalize(), 0.18) }];
      }
      return handles.map(({ s }) => {
        const c = columnOf(s);
        const toward = c.exit.clone().sub(P.hand(s)).normalize();
        return { c, s, hook: P.hand(s).clone().addScaledVector(toward, STIRRUP) };
      });
    };
    return {
      parts: [...columns.map((c) => c.column), ...ends, ...cables],
      // Each stack's plates are raised by its own lift, not moved as a body.
      bodies: Object.fromEntries(joints.map((j) => [j, []])),
      cables: hookOn,
      stacks: Object.fromEntries(columns.map((c) => [jointOf(c), c])),
      drive(P, P0, P1) {
        if (!rests) {
          const bottom = hooksAt(P1);
          rests = hooksAt(P0).map(({ c, hook }, i) => Math.min(hook.distanceTo(c.exit), bottom[i].hook.distanceTo(c.exit)));
        }
        const out = {};
        const points = {};
        const hooks = hooksAt(P);
        hooks.forEach(({ c, s, hook }, i) => {
          let at = hook;
          if (handles) {
            // The stirrup in the hand, turned toward the pulley as square to the grip allows: the
            // cable runs from its ring, wherever that comes to.
            const { handle } = handles.find((h) => h.s === s);
            handle.position.copy(P.hand(s));
            turnTo(handle, P.held?.[s] ?? X, c.exit.clone().sub(P.hand(s)));
            handle.updateMatrixWorld(true);
            at = handle.localToWorld(V(0, STIRRUP, 0));
            points[`handle_${s < 0 ? 'r' : 'l'}`] = [P.hand(s).clone()];
          }
          c.aim(at);
          out[jointOf(c)] = { value: Math.max(0, at.distanceTo(c.exit) - rests[i]), origin: c.column.position.clone() };
          span(cables[columns.indexOf(c)], at, c.exit);
        });
        if (rope) {
          const { hook } = hooks[0];
          rope.ring.position.copy(hook);
          rope.strands.forEach(({ s, strand, end }) => {
            const h = P.hand(s);
            const beyond = h.clone().addScaledVector(h.clone().sub(hook).normalize(), 0.06);
            span(strand, hook, beyond);
            end.position.copy(beyond);
          });
          points.rope = [-1, 1].map((s) => P.hand(s).clone());
        }
        return { joints: out, points };
      },
    };
  },
});
