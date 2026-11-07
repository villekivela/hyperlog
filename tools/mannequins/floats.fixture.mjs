// What of a machine touches nothing (#127). A box round a tilted tube stands well clear of it, so a part
// a few centimetres off one would pass as touching: parts here are measured between their real shapes.
// A straight part goes by its centre line and a radius, a bent one by points along its edges.
import * as THREE from 'three';

/** How near two parts' surfaces come to count as touching. */
const TOUCHING = 0.006;
/** How far apart the points along a part stand. */
const STEP = 0.02;

/** Whether the mesh's own box is what it fills: a vertex by each corner, as a tube or a pad has. */
const fillsItsBox = (mesh, box) => {
  const p = mesh.geometry.attributes.position;
  const v = new THREE.Vector3();
  const corners = [0, 1, 2, 3, 4, 5, 6, 7].map((i) => new THREE.Vector3(
    i & 1 ? box.max.x : box.min.x, i & 2 ? box.max.y : box.min.y, i & 4 ? box.max.z : box.min.z,
  ));
  const reach = Math.min(0.05, box.getSize(new THREE.Vector3()).length() / 3);
  return corners.every((c) => {
    for (let i = 0; i < p.count; i++) if (v.fromBufferAttribute(p, i).distanceTo(c) < reach) return true;
    return false;
  });
};

/**
 * A part that fills its box, as points over its middle sheet across its two longest sides, and how far
 * its surface stands off that sheet: half its thinnest side. A tube is then a line of points, a pad a
 * sheet of them, so a flat part a few centimetres off another doesn't pass as touching it.
 */
function sheetOf(mesh, box) {
  const scale = new THREE.Vector3().setFromMatrixScale(mesh.matrixWorld);
  const size = box.getSize(new THREE.Vector3()).multiply(scale);
  const [a, b, c] = ['x', 'y', 'z'].sort((u, v) => size[v] - size[u]);
  const middle = box.getCenter(new THREE.Vector3());
  const [na, nb] = [a, b].map((k) => Math.max(1, Math.ceil(size[k] / STEP)));
  const points = [];
  for (let i = 0; i <= na; i++) {
    for (let j = 0; j <= nb; j++) {
      const p = middle.clone();
      p[a] = box.min[a] + ((box.max[a] - box.min[a]) * i) / na;
      p[b] = box.min[b] + ((box.max[b] - box.min[b]) * j) / nb;
      points.push(p.applyMatrix4(mesh.matrixWorld));
    }
  }
  return { points, radius: size[c] / 2 };
}

/** Points along a bent part's edges. */
function edgesOf(mesh) {
  const { index, attributes: { position } } = mesh.geometry;
  const at = (i) => new THREE.Vector3().fromBufferAttribute(position, i).applyMatrix4(mesh.matrixWorld);
  const count = index ? index.count : position.count;
  const vertex = (i) => (index ? index.getX(i) : i);
  const seen = new Set();
  const points = [];
  for (let t = 0; t < count; t += 3) {
    for (const [a, b] of [[t, t + 1], [t + 1, t + 2], [t + 2, t]].map(([x, y]) => [vertex(x), vertex(y)])) {
      const key = a < b ? `${a}:${b}` : `${b}:${a}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const [A, B] = [at(a), at(b)];
      const n = Math.max(1, Math.ceil(A.distanceTo(B) / STEP));
      for (let i = 0; i <= n; i++) points.push(A.clone().lerp(B, i / n));
    }
  }
  return { points, radius: 0 };
}

/** A mesh's shape in the world as it now stands: points through it, and how far its surface stands off them. */
export function shapeOf(mesh) {
  mesh.geometry.computeBoundingBox();
  const box = mesh.geometry.boundingBox;
  return fillsItsBox(mesh, box) ? sheetOf(mesh, box) : edgesOf(mesh);
}

/** The names of the parts of `stage`'s kit that join nothing standing on the floor, at `d` of the rep. */
export function floating(stage, d = 0.5) {
  stage.pose(d);
  stage.scene.updateMatrixWorld(true);
  const parts = [];
  stage.kit.group.traverse((o) => {
    if (!o.isMesh || !o.visible) return;
    const shape = shapeOf(o);
    parts.push({ name: o.name, role: o.userData.role, ...shape, around: new THREE.Box3().setFromPoints(shape.points).expandByScalar(shape.radius + TOUCHING) });
  });
  const touching = (a, b) => a.around.intersectsBox(b.around)
    && a.points.some((p) => b.points.some((q) => p.distanceTo(q) < a.radius + b.radius + TOUCHING));
  const reached = new Set(parts.filter((p) => p.around.min.y < 0.012));
  const waiting = new Set(parts.filter((p) => !reached.has(p)));
  for (const from of reached) {
    for (const p of waiting) if (touching(from, p)) { reached.add(p); waiting.delete(p); }
  }
  return [...waiting].map((p) => p.role ? `${p.name} (${p.role})` : p.name);
}
