// A pair of hex dumbbells, one in each hand, the handle across the palm between collars. Free
// bodies: the hands carry them.
import { recipe, free } from '../recipe.mjs';
import { along, group, hexGeometry, turnedPart } from '../parts.mjs';
import { V, X } from '../rig.mjs';

export default recipe({
  name: 'dumbbells',
  bodies: { dumbbells: free() },
  contacts: [{ body: 'dumbbells', point: 'handles', landmark: 'hands' }],
  build(options, M) {
    const bells = [-1, 1].map((s) => {
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
        [-1, 1].forEach((s, i) => {
          bells[i].position.copy(P.hand(s));
          // The handle lies along the knuckles, as the hand turned and tipped it.
          bells[i].quaternion.setFromUnitVectors(X, (P.held?.[s] ?? P.across(s)).clone().normalize());
        });
        return { points: { handles: bells.map((b) => b.position.clone()) } };
      },
    };
  },
});
