// How far one part of the body's skin goes into a leg's, which a check of vertices can miss, for the
// tests of a lift whose arm comes close to its leg.
import * as THREE from 'three';
import { prepare } from './entries.mjs';
import { buildStage, FRAMES } from './stage.mjs';
import { bodyVertices } from './checks.mjs';

/**
 * How far the skin of bones `part` goes into the skin of the leg on side `k`, at the worst frame:
 * negative is the gap left between them. A vertex is inside where it lies behind the nearest triangle's face.
 */
export const skinInto = async (id, k, part) => {
  const stage = await buildStage(prepare(id));
  const vertices = bodyVertices(stage.rig);
  const starts = new Map();
  vertices.forEach((v, i) => { if (!starts.has(v.mesh)) starts.set(v.mesh, i); });
  const leg = new RegExp(`^(thigh|calf)_${k}$`);
  const faces = [...starts].flatMap(([mesh, start]) => {
    const order = mesh.geometry.index;
    return Array.from({ length: order.count / 3 }, (_, t) => [0, 1, 2].map((c) => start + order.getX(t * 3 + c)))
      .filter((face) => face.every((i) => leg.test(vertices[i].bone)));
  });
  const [closest, normal] = [new THREE.Vector3(), new THREE.Vector3()];
  let worst = { depth: -Infinity };
  for (let f = 0; f < FRAMES; f++) {
    stage.pose(f / (FRAMES - 1));
    stage.scene.updateMatrixWorld(true);
    const at = vertices.map((v) => v.mesh.getVertexPosition(v.i, new THREE.Vector3()).applyMatrix4(v.mesh.matrixWorld));
    const triangles = faces.map(([a, b, c]) => new THREE.Triangle(at[a], at[b], at[c]));
    vertices.forEach((v, i) => {
      if (!part.test(v.bone)) return;
      let gap = Infinity;
      let inside = false;
      for (const t of triangles) {
        t.closestPointToPoint(at[i], closest);
        const d = closest.distanceTo(at[i]);
        if (d >= gap) continue;
        gap = d;
        inside = t.getNormal(normal).dot(at[i].clone().sub(closest)) < 0;
      }
      const depth = inside ? gap : -gap;
      if (depth > worst.depth) worst = { depth, frame: f, bone: v.bone };
    });
  }
  return worst;
};
