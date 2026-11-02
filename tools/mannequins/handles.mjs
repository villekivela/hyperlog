// The Tune panel's drag handles (README, Reviewing a batch): a point on the posed body that, dragged,
// becomes the pose numbers a slider sets, so the rig re-solves the rest. A handle is only offered
// where the entry has numbers for it. `drag(point)` takes where the handle was dragged to, in
// metres, and returns the numbers that would put it there: `{ path: value }`, a point whole.
import { baseAt } from './tune.mjs';
import { X, between, gripTarget, wpos } from './rig.mjs';

const round = (v) => Number(v.toFixed(4));
const deg = (r) => (r * 180) / Math.PI;
/** How far the chest may pull the arch: degrees of lumbar arch for each metre it is raised. */
const ARCH_PER_METRE = 150;

/** The handles of a posed entry: the stage (`buildStage`), its pose frame `P` and how far down the rep (`d`, 0 or 1) it is. */
export function handlesAt(entry, stage, P, d) {
  const { rig } = stage;
  const section = d === 0 ? 'top' : 'bottom';
  const out = [];

  if (Array.isArray(entry[section]?.bar) && entry.grip?.width !== undefined) {
    out.push({
      id: 'hand', label: 'hands', at: gripTarget(P, 1),
      drag: (p) => ({ [`${section}.bar`]: [round(p.y), round(p.z)], 'grip.width': round(Math.abs(p.x)) }),
    });
  }

  if (Array.isArray(entry[section]?.hand) && entry[section].hand.length === 3) {
    // A free hand stands at its own point in the room, the left one's, which the right mirrors.
    out.push({
      id: 'hand', label: 'hand', at: gripTarget(P, 1),
      drag: (p) => ({ [`${section}.hand`]: [round(p.x), round(p.y), round(p.z)] }),
    });
  }

  if (entry.motion === 'dumbbell_press') {
    // From the shoulders, in the trunk's frame: out from the chest and up the trunk at the top, out from the chest and down the trunk at the bottom.
    const side = (p) => p.clone().sub(P.shoulders);
    const front = X.clone().cross(P.u).normalize();
    out.push({
      id: 'hand', label: 'hands', at: gripTarget(P, 1),
      drag: (p) => (d === 0
        ? { 'top.over': [round(side(p).dot(front)), round(side(p).dot(P.u))], 'grip.lockout': round(Math.abs(p.x)) }
        : { 'bottom.chest': [round(side(p).dot(front)), round(-side(p).dot(P.u))], 'grip.width': round(Math.abs(p.x)) }),
    });
  }

  if (entry.feet?.stance !== undefined) {
    out.push({
      id: 'feet', label: 'feet', at: P.ankles[1].clone(),
      drag: (p) => ({ 'feet.stance': round(Math.abs(p.x)), 'feet.z': round(p.z) }),
    });
  }

  if (Array.isArray(entry.body?.hip)) {
    out.push({ id: 'hips', label: 'hips', at: P.hipC.clone(), drag: (p) => ({ 'body.hip': [round(p.y), round(p.z)] }) });
  }

  const chest = between(rig, 'upperarm_l', 'upperarm_r');
  if (Array.isArray(entry.body?.arch)) {
    // Raising the chest arches the back: the lumbar spine up, the chest segment down, by the same.
    const arch = baseAt(entry, 'body.arch');
    out.push({
      id: 'chest', label: 'chest', at: chest,
      drag: (p) => {
        const lift = (p.y - chest.y) * ARCH_PER_METRE;
        return { 'body.arch': [round(arch[0] + lift), round(arch[1] - lift), arch[2]] };
      },
    });
  } else if (entry.motion === 'dumbbell_press') {
    // The trunk lies along the bench's pad; the chest turns the body about the hips, off the pad's angle.
    const leanOf = (p) => deg(Math.atan2(p.z - P.hipC.z, p.y - P.hipC.y));
    out.push({
      id: 'chest', label: 'chest', at: chest,
      drag: (p) => ({ 'body.lean': round(baseAt(entry, 'body.lean') + leanOf(p) - leanOf(chest)) }),
    });
  } else {
    const key = entry[section]?.trunk !== undefined && !entry.trace?.angles?.trunk ? `${section}.trunk` : entry.body?.trunk !== undefined ? 'body.trunk' : null;
    if (key) {
      out.push({
        id: 'chest', label: 'chest', at: chest,
        // The lean of the line from the hips to the shoulders, forward of straight up.
        drag: (p) => ({ [key]: round(deg(Math.atan2(p.z - P.hipC.z, p.y - P.hipC.y))) }),
      });
    }
  }

  if (entry.motion === 'pulldown' || entry.motion === 'bench_press' || entry.motion === 'dumbbell_press') {
    const sh = wpos(rig.bones.upperarm_l);
    const axis = wpos(rig.bones.hand_l).sub(sh);
    const elbow = wpos(rig.bones.lowerarm_l);
    // Where the elbow sits around the arm's axis is where the pole points: down, out by `elbows`
    // and, for a pulldown, forward or back by `elbowZ`. The solved elbow is not exactly on the
    // pole, so a drag moves each by how far the elbow's own ratio moved, not to it.
    const ratios = (p) => {
      const off = p.clone().sub(sh);
      const perp = off.addScaledVector(axis, -off.dot(axis) / axis.lengthSq());
      const down = Math.max(-perp.y, 0.02);
      return { out: perp.x / down, forward: perp.z / down };
    };
    const start = ratios(elbow);
    out.push({
      id: 'elbow', label: 'elbows', at: elbow,
      drag: (p) => {
        const now = ratios(p);
        const changes = { 'grip.elbows': round(Math.min(1.5, Math.max(-1, baseAt(entry, 'grip.elbows') + now.out - start.out))) };
        if (entry.motion === 'pulldown') {
          changes['grip.elbowZ'] = round(Math.min(1.5, Math.max(-1.5, baseAt(entry, 'grip.elbowZ') + now.forward - start.forward)));
        }
        return changes;
      },
    });
  }
  return out;
}
