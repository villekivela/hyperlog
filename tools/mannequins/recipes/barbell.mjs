// A 20 kg barbell with a 20 and a 10 a side, along x, on the hands' bar: collars, sleeves and
// clamps. A free body: the hands carry it.
import { recipe, free } from '../recipe.mjs';
import { along, plateGeometry, turnedPart } from '../parts.mjs';
import { V, gripTarget } from '../rig.mjs';

export default recipe({
  name: 'barbell',
  bodies: { bar: free() },
  contacts: [{ body: 'bar', point: 'grips', landmark: 'hands' }],
  build(options, M) {
    const parts = [along(0.014, -0.7, 0.7, M.metal, 24, { held: true })];
    for (const s of [-1, 1]) {
      const at = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
      parts.push(along(0.038, ...at(0.69, 0.72), M.metal, 32, { solid: true }));
      parts.push(along(0.025, ...at(0.72, 1.075), M.metal, 24, { solid: true }));
      parts.push(along(0.021, ...at(1.075, 1.085), M.metal, 24, { solid: true }));
      parts.push(turnedPart(plateGeometry(0.225, 0.052), M.plate, V(s * 0.75, 0, 0)));
      parts.push(turnedPart(plateGeometry(0.17, 0.038), M.plate, V(s * 0.805, 0, 0)));
      parts.push(along(0.034, ...at(0.828, 0.85), M.metal, 24, { solid: true }));
    }
    let bar;
    return {
      parts,
      stand(g) { bar = g; },
      drive(P) {
        bar.position.copy(P.bar);
        return { points: { grips: [-1, 1].map((s) => gripTarget(P, s)) } };
      },
    };
  },
});
