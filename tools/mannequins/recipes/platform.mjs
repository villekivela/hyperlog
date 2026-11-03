// A low platform under the feet, `height` tall (the pose's `feet.y`) and `size` [w, d] at `at`
// [x, z]: a deficit. All frame: nothing on it moves.
import { recipe } from '../recipe.mjs';
import { pad } from '../parts.mjs';
import { V } from '../rig.mjs';

export default recipe({
  name: 'platform',
  build({ height = 0.05, size = [0.8, 0.5], at = [0, 0.05] } = {}, M) {
    // A frame with a rubber mat on top, where the feet stand.
    const mat = 0.012;
    return {
      parts: [
        pad(size[0], height - mat, size[1], M.frame, V(at[0], (height - mat) / 2, at[1]), { soft: 0.006 }),
        pad(size[0] - 0.03, mat, size[1] - 0.03, M.vinyl, V(at[0], height - mat / 2, at[1]), { soft: 0.004 }),
      ],
      drive() {},
    };
  },
});
