// Hex dumbbells, one in each hand, or with `hand` (1 the left, -1 the right) one in that hand alone,
// the handle across the palm between collars. Free bodies: the hands carry them. With `steady`, each
// is laid by the palm as well as the handle's line, so it turns with the hand and can't roll in it.
import { Matrix4 } from 'three';
import { recipe, free } from '../recipe.mjs';
import { along, group, hexGeometry, turnedPart } from '../parts.mjs';
import { V, X } from '../rig.mjs';

export default recipe({
  name: 'dumbbells',
  bodies: { dumbbells: free() },
  contacts: [{ body: 'dumbbells', point: 'handles', landmark: 'hands' }],
  build(options, M) {
    const sides = options.hand ? [options.hand] : [-1, 1];
    const bells = sides.map((s) => {
      // Room for a whole hand between the collars.
      const parts = [along(0.016, -0.088, 0.088, M.metal, 16, { held: true })];
      for (const e of [-1, 1]) {
        parts.push(along(0.024, ...(e < 0 ? [-0.092, -0.084] : [0.084, 0.092]), M.metal, 20, { solid: true }));
        parts.push(turnedPart(hexGeometry(0.06, 0.06), M.plate, V(e * 0.118, 0, 0)));
      }
      return group(`dumbbell_${s < 0 ? 'r' : 'l'}`, parts);
    });
    return {
      parts: bells,
      drive(P) {
        sides.forEach((s, i) => {
          bells[i].position.copy(P.hand(s));
          // The handle lies along the knuckles, as the hand turned and tipped it.
          const line = (P.held?.[s] ?? P.across(s)).clone().normalize();
          const palm = options.steady && P.palm?.[s];
          if (palm) bells[i].quaternion.setFromRotationMatrix(new Matrix4().makeBasis(line, palm, line.clone().cross(palm)));
          else bells[i].quaternion.setFromUnitVectors(X, line);
        });
        return { points: { handles: bells.map((b) => b.position.clone()) } };
      },
    };
  },
});
