// A dip belt round the hips and a plate on its chain (refs/power_rack/ref.md, Adjustments): the
// chain runs from the belt's sides down through the plate's bore, and the plate hangs plumb in
// front of the thighs at about mid-thigh, however the body leans. A free body: the hips carry it.
import * as THREE from 'three';
import { recipe, free } from '../recipe.mjs';
import { bent, plateGeometry, span, turnedPart, wire } from '../parts.mjs';
import { UP, V, X, forwardOf } from '../rig.mjs';

// The belt, a hand above the hip joints: an oval round the skin there, 172 mm either side and 130 mm
// before and behind a middle 10 mm back of the hips.
const BELT = { up: 0.05, half: 0.172, depth: 0.13, back: 0.01, radius: 0.012 };
// A 25 lb iron plate (277 mm, refs/README.md), down from the hips and out in front of them.
const PLATE = { radius: 0.1385, thick: 0.03, down: 0.2, out: 0.17 };
// Where the chain leaves the belt: on each side, a little ahead of the hips.
const CHAIN = { x: 0.15, out: 0.05 };

const oval = Array.from({ length: 25 }, (_, i) => {
  const a = (i / 24) * 2 * Math.PI;
  return V(BELT.half * Math.cos(a), 0, BELT.depth * Math.sin(a) - BELT.back);
});

export default recipe({
  name: 'dip_belt',
  bodies: { plate: free() },
  build(options, M) {
    const belt = bent('dip_belt', oval, BELT.radius, M.vinyl, { segments: 48 });
    const plate = turnedPart(plateGeometry(PLATE.radius, PLATE.thick), M.plate, V(0, 0, 0));
    plate.rotation.y = Math.PI / 2;
    const chains = [-1, 1].map(() => wire(M));
    return {
      parts: [belt, plate, ...chains],
      drive(P) {
        const f = forwardOf(P.u);
        belt.position.copy(P.hipC).addScaledVector(P.u, BELT.up);
        belt.quaternion.copy(new THREE.Quaternion().setFromUnitVectors(UP, P.u));
        plate.position.copy(P.hipC).add(V(0, -PLATE.down, PLATE.out));
        [-1, 1].forEach((s, i) => span(chains[i], belt.position.clone().addScaledVector(X, s * CHAIN.x).addScaledVector(f, CHAIN.out), plate.position));
      },
    };
  },
});
