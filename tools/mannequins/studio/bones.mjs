// The mannequin's bones as the studio walks them (README, The studio), with nothing of the page in
// it: from a bone to its parent, its first child, its siblings and the mirror bone on the other side.

/** Whether bone `name` can be turned: not the rig's root, which holds the body up, nor a leaf at a fingertip or a toe. */
const turnable = (name) => name !== 'root' && !name.includes('_leaf');

/**
 * The walk of the rig's `bones` (`{ name: bone }`): `names`, every bone that can be turned in the
 * skeleton's order, the pelvis first; `parent(name)` and `child(name)` (its first), each the bone
 * itself at the end; `sibling(name, by)`, round the bones that share its parent; `mirror(name)`,
 * the same bone on the other side, or itself on the middle.
 */
export function skeletonOf(bones) {
  const names = [];
  const root = Object.values(bones).find((b) => !b.parent?.isBone);
  const walk = (b) => {
    if (turnable(b.name)) names.push(b.name);
    for (const c of b.children) if (c.isBone) walk(c);
  };
  walk(root);
  const known = new Set(names);
  const childrenOf = (name) => bones[name].children.filter((c) => c.isBone && known.has(c.name)).map((c) => c.name);
  const parentOf = (name) => (known.has(bones[name].parent?.name) ? bones[name].parent.name : null);
  return {
    names,
    parent: (name) => parentOf(name) ?? name,
    child: (name) => childrenOf(name)[0] ?? name,
    sibling(name, by) {
      const up = parentOf(name);
      if (!up) return name;
      const all = childrenOf(up);
      return all[(all.indexOf(name) + by + all.length * Math.abs(by)) % all.length];
    },
    mirror(name) {
      const other = name.replace(/_([lr])$/, (_, k) => (k === 'l' ? '_r' : '_l'));
      return known.has(other) ? other : name;
    },
  };
}
