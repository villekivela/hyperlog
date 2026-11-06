// A plate-loaded chest-supported row, laid out as the Gymleco 010 Seated Row: the seat, and ahead
// of the knees a column up from the base to the chest pad's arm, with the levers' axle across it
// low down at its pivot, braced to the base. Each lever rises from its hub to a handle and
// runs on forward and down to a loaded horn, whose plate rises as the handles come back.
//
// The levers are one body on a revolute joint at the pivot, its value the arms' angle up from
// level ahead.
import { recipe, revolute } from '../recipe.mjs';
import { chestPad, floorRail, levers, part, placedRail, rodGeometry, seatOn, span, stretchedTube, swungHandles } from '../parts.mjs';
import { V, rad } from '../rig.mjs';

// The levers' pivot, [y, z], low in front of the knees, and how far it is up them to the handles.
const PIVOT = [0.45, 0.527];
const ARM = 0.563;
// The handles this far either side of the middle.
const HANDLES_OUT = 0.22;
/** How far up from the hips to the shoulders the chest pad's middle is. */
const PAD_UP = 0.715;

export default recipe({
  name: 'row_machine',
  // From the arms straight ahead to the handles back at the ribs; `reach` is the forward stop, in
  // degrees, further forward for handles low on the levers.
  bodies: ({ reach = 73 } = {}) => ({ levers: revolute({ axis: V(-1, 0, 0), limits: [rad(reach), rad(135)] }) }),
  rep: { joint: 'levers', from: 'min' },
  contacts: [
    { body: 'levers', point: 'handles', landmark: 'hands' },
    { body: 'frame', point: 'pad', landmark: 'chest' },
  ],
  fit: [{
    sets: 'chest',
    rule: 'the chest on the pad, the arms long to the handles',
    solve: ({ L }) => [PAD_UP * L.trunkUp, L.chest],
  }],
  // `arm`: how far up the levers the handles are, lower for a row from the arms reaching down.
  linkage: ({ seat = [0.5, 0], arm = ARM }) => ({ hip: [seat[0] + 0.09, seat[1]], at: swungHandles(PIVOT, arm, HANDLES_OUT) }),
  build({ seat = [0.5, 0], chest: [padUp, padFront] = [0.4, 0.1], horn = -100 } = {}, M) {
    const out = 0.33;
    const pad_ = chestPad(M, { up: padUp, front: padFront });
    const arms = levers(M, { out, horn: { length: 0.45, turn: horn } });
    const column = stretchedTube(0.08, M.frame);
    const housing = stretchedTube(0.06, M.frame);
    const axle = part(rodGeometry(0.02), M.metal);
    const braces = [-1, 1].map(() => stretchedTube(0.05, M.frame));
    const spine = placedRail(M);
    const front = placedRail(M);
    return {
      parts: [
        ...seatOn(seat[0], seat[1], M, { pin: -1 }),
        ...floorRail(V(-0.25, 0.035, seat[1] - 0.18), V(0.25, 0.035, seat[1] - 0.18), M),
        ...spine.parts, ...front.parts, ...pad_.parts, ...arms.parts, column, housing, axle, ...braces,
      ],
      bodies: { levers: arms.parts },
      drive(P, P0) {
        const joint = arms.place(P);
        const { pivot } = P;
        // The braces lean from the axle's housing forward and down to the base's front rail.
        const foot = pivot.z + 0.35;
        spine.set(V(0, 0, seat[1] - 0.25), V(0, 0, foot + 0.1));
        front.set(V(-out, 0, foot), V(out, 0, foot));
        const base = V(0, 0.07, pivot.z);
        // The chest pad's arm runs forward from its board to the column's head.
        pad_.fit(P0, (back) => {
          const head = base.clone().setY(back.y);
          span(column, base, head);
          return head;
        });
        span(housing, pivot.clone().setX(-out + 0.04), pivot.clone().setX(out - 0.04));
        span(axle, pivot.clone().setX(-out - 0.03), pivot.clone().setX(out + 0.03));
        [-1, 1].forEach((s, i) => span(braces[i], pivot.clone().setX(s * (out - 0.06)), V(s * (out - 0.06), 0.07, foot)));
        return { joints: { levers: joint }, points: { handles: [-1, 1].map((s) => P.handle(s).clone()), pad: [pad_.parts[0].position.clone()] } };
      },
    };
  },
});
