// A hexagonal trap bar, closed frame: the lifter stands inside it and grips two parallel handles at
// the sides, palms in. The sleeves lie along x; the frame, the sleeves and the low handles' centres
// are one horizontal plane, 225 mm up (a 450 mm plate's radius, so the plates stand on the floor).
// The frame's two corners are on the sleeve axis, and its arms leave them for the front and rear
// vertices, and a ring of one piece with mitred corners joins them, front and rear closed. The lifter
// faces +z.
//
// Options: `handle`, `'low'` (the handle centres at the sleeve axis' height) or `'high'` (102 mm
// above, the flipped bracket), and `plates`, the 450 mm plates a side, one by default. Nothing on it
// moves while lifting: the whole bar rises with the lifter, which a pose carries. Contacts: the hands
// at the handles' centres (`grips`).
import { recipe } from '../recipe.mjs';
import { along, rod, turnedPart, plateGeometry, frameRingGeometry, part } from '../parts.mjs';
import { V } from '../rig.mjs';

const PLANE = 0.225;
const HANDLE = { low: PLANE, high: PLANE + 0.102 };
const GRIP = { radius: 0.017, half: 0.0635, spacing: 0.635 };
// The frame's tube is 38 mm square; its corners are 467 mm from the middle along the sleeve axis,
// and its front and rear vertices about 300 mm to either side of it, 255 mm out.
const FRAME = { w: 0.038, corner: 0.467, vertex: { x: 0.255, z: 0.307 } };
const SLEEVE = { radius: 0.025, from: 0.532, to: 0.938 };
const BRACE = 0.012;
const PLATE = { radius: 0.225, thick: 0.045, gap: 0.01 };

export default recipe({
  name: 'trap_bar',
  standsAlone: { camera: { target: [0, 0.25, 0], halfHeight: 1.0, azimuth: 35, elevation: 20 } },
  bodies: {},
  contacts: [{ body: 'frame', point: 'grips', landmark: 'hands' }],
  fit: [{ sets: 'handle', rule: 'the handle height is chosen before the set, low (225 mm) or high (327 mm), never both; the handles stay parallel and 635 mm apart' }],
  build({ handle = 'low', plates = 1 } = {}, M) {
    const sides = [-1, 1];
    const y = HANDLE[handle];
    if (y === undefined) throw new Error(`trap_bar: no handle height "${handle}"`);
    const at = (x, z, height = PLANE) => V(x, height, z);
    const parts = [];
    // The frame: one mitred ring through the two corners and the four vertices.
    const { vertex } = FRAME;
    // Where an arm's middle is, `z` from the sleeve axis: the handle's braces end on it, inside the ring.
    const armAt = (z) => FRAME.corner - z * (FRAME.corner - vertex.x) / vertex.z;
    const ring = part(frameRingGeometry([[FRAME.corner, 0], [vertex.x, vertex.z], [-vertex.x, vertex.z], [-FRAME.corner, 0], [-vertex.x, -vertex.z], [vertex.x, -vertex.z]], FRAME.w, FRAME.w), M.frame, { solid: true });
    ring.position.y = PLANE;
    parts.push(ring);
    for (const s of sides) {
      // The corner's stub out to the sleeve.
      parts.push(rod(FRAME.w / 2, at(s * FRAME.corner, 0), at(s * SLEEVE.from, 0), M.frame, { solid: true }));
      // The handle, its two ends braced to the arms, and posts up to it where it is high.
      const x = s * GRIP.spacing / 2;
      parts.push(rod(GRIP.radius, at(x, -GRIP.half, y), at(x, GRIP.half, y), M.metal, { held: true }));
      for (const z of sides) {
        parts.push(rod(BRACE, at(s * armAt(GRIP.half), z * GRIP.half), at(x, z * GRIP.half), M.frame, { solid: true }));
        if (y > PLANE) parts.push(rod(BRACE, at(x, z * GRIP.half), at(x, z * GRIP.half, y), M.frame, { solid: true }));
      }
      const sleeve = along(SLEEVE.radius, ...[s * SLEEVE.from, s * SLEEVE.to].sort((a, b) => a - b), M.metal, 24, { solid: true });
      sleeve.position.y = PLANE;
      parts.push(sleeve);
      for (let i = 0; i < plates; i++) {
        const out = SLEEVE.from + PLATE.gap + PLATE.thick / 2 + i * PLATE.thick;
        parts.push(turnedPart(plateGeometry(PLATE.radius, PLATE.thick), M.plate, V(s * out, PLANE, 0)));
      }
    }
    const grips = sides.map((s) => at(s * GRIP.spacing / 2, 0, y));
    return {
      parts,
      drive() {
        return { points: { grips: grips.map((p) => p.clone()) } };
      },
    };
  },
});
