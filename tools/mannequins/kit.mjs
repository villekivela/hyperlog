// Generic equipment, built from primitives, and the floor shadow under the mannequin. Materials
// are named for the phone, which colours each by name from the theme (body, joint, bar, plate,
// shadow); the colours here are the light theme's, for viewing a clip anywhere else. Nothing is
// metallic: the stage has no reflections for metal to show.
import * as THREE from 'three';

export const MATERIALS = {
  body: () => new THREE.MeshStandardMaterial({ name: 'body', color: 0xbabac1, roughness: 0.8 }),
  joint: () => new THREE.MeshStandardMaterial({ name: 'joint', color: 0x9a9aa1, roughness: 0.8 }),
  bar: () => new THREE.MeshStandardMaterial({ name: 'bar', color: 0x5c5c62, roughness: 0.35 }),
  plate: () => new THREE.MeshStandardMaterial({ name: 'plate', color: 0x48484e, roughness: 0.85 }),
};

/** A cylinder along x, from `from` to `to`. */
function along(radius, from, to, material, segments) {
  const g = new THREE.CylinderGeometry(radius, radius, to - from, segments);
  g.rotateZ(Math.PI / 2);
  g.translate((from + to) / 2, 0, 0);
  return new THREE.Mesh(g, material);
}

/** A 20 kg barbell with a 20 and a 10 a side, along x and centred on its node. */
export function barbell() {
  const bar = MATERIALS.bar();
  const plate = MATERIALS.plate();
  const group = new THREE.Group();
  group.name = 'barbell';
  group.add(along(0.014, -0.7, 0.7, bar, 24));
  for (const s of [-1, 1]) {
    const at = (a, b) => [Math.min(s * a, s * b), Math.max(s * a, s * b)];
    group.add(along(0.025, ...at(0.7, 1.08), bar, 24));
    group.add(along(0.038, ...at(0.69, 0.72), bar, 32));
    group.add(along(0.225, ...at(0.724, 0.776), plate, 48));
    group.add(along(0.17, ...at(0.786, 0.824), plate, 48));
  }
  group.children.forEach((m, i) => { m.name = `barbell_${i}`; });
  return group;
}

/**
 * A soft shadow on the floor: an ellipse `width` by `depth` metres, darkest in the middle and
 * gone at the rim, drawn unlit with its alpha in the vertex colours.
 */
export function floorShadow(width, depth, darkest = 0.45) {
  const rings = 8;
  const segments = 48;
  const position = [0, 0, 0];
  const color = [1, 1, 1, darkest];
  for (let r = 1; r <= rings; r++) {
    const t = r / rings;
    const alpha = darkest * (1 - t * t) * (1 - t * t);
    for (let k = 0; k < segments; k++) {
      const a = (k / segments) * Math.PI * 2;
      position.push((Math.cos(a) * t * width) / 2, 0, (Math.sin(a) * t * depth) / 2);
      color.push(1, 1, 1, alpha);
    }
  }
  const index = [];
  const at = (r, k) => 1 + (r - 1) * segments + (k % segments);
  for (let k = 0; k < segments; k++) index.push(0, at(1, k + 1), at(1, k));
  for (let r = 1; r < rings; r++) {
    for (let k = 0; k < segments; k++) {
      index.push(at(r, k), at(r, k + 1), at(r + 1, k + 1), at(r, k), at(r + 1, k + 1), at(r + 1, k));
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(position, 3));
  g.setAttribute('normal', new THREE.Float32BufferAttribute(position.map((_, i) => (i % 3 === 1 ? 1 : 0)), 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(color, 4));
  g.setIndex(index);
  const material = new THREE.MeshBasicMaterial({ name: 'shadow', color: 0x000000, vertexColors: true, transparent: true, depthWrite: false });
  const mesh = new THREE.Mesh(g, material);
  mesh.name = 'shadow';
  mesh.position.y = 0.001;
  return mesh;
}
