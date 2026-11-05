// The machine-first solve (ADR 0007, #160). An entry posed in a machine is fitted to the mannequin:
// the recipe's fit rules set each adjustment the entry leaves unset, and the seat or the pad puts
// the hips.
// Then the rep drives the recipe's joint between the top's value and the bottom's, the limits by
// default, and the motion hangs the body from where the machine has its contacts at that value
// (`frameAt`). A free weight or a cable, on no recipe with a `linkage`, stays body-first.
import { deg } from './angles.mjs';
import { forOptions } from './recipe.mjs';
import { RECIPES } from './recipes/index.mjs';
import { rad } from './rig.mjs';

const fits = new WeakMap();

/**
 * The entry fitted to a mannequin of measurements `L`: its machine's options solved and its joint's
 * ends filled in (degrees for a revolute joint, metres for a prismatic one), `body.hip` where the
 * seat or the pad puts it, `linkage` the joint and what the recipe has at a value, and `fit` what
 * was solved, by the path an entry would set it at. An entry on no machine comes back as it is.
 */
export function fitted(entry, L) {
  const name = Object.keys(entry.kit ?? {}).find((k) => RECIPES[k]?.linkage);
  if (!name || entry.linkage) return entry;
  const seen = fits.get(entry);
  if (seen?.L === L) return seen.result;
  const recipe = RECIPES[name];
  const options = { ...entry.kit[name] };
  const fit = {};
  for (const { sets, solve } of forOptions(recipe.fit ?? [], options)) {
    if (!solve || options[sets] !== undefined) continue;
    options[sets] = solve({ L, options, entry });
    fit[`kit.${name}.${sets}`] = options[sets];
  }
  // A machine with nothing on it that moves (no `rep`) has one place for the body: its linkage is that, whatever the value.
  if (!recipe.rep) {
    const { hip, at } = recipe.linkage(options, L);
    const result = {
      ...entry,
      kit: { ...entry.kit, [name]: options },
      body: { ...entry.body, ...(hip ? { hip } : {}) },
      linkage: { joint: null, at: () => ({ value: 0, ...at() }) },
      fit,
    };
    fits.set(entry, { L, result });
    return result;
  }
  const { joint, from } = recipe.rep;
  const { type, limits } = forOptions(recipe.bodies, options)[joint];
  const shown = (v) => (type === 'revolute' ? deg(v) : v);
  const [top, bottom] = (from === 'max' ? [...limits].reverse() : limits).map(shown);
  const ends = {};
  for (const [end, limit] of [['top', top], ['bottom', bottom]]) {
    ends[end] = { ...entry[end], [joint]: entry[end]?.[joint] ?? limit };
    if (entry[end]?.[joint] === undefined) fit[`${end}.${joint}`] = limit;
  }
  const { hip, at } = recipe.linkage(options, L);
  const result = {
    ...entry,
    ...ends,
    kit: { ...entry.kit, [name]: options },
    body: { ...entry.body, ...(hip ? { hip } : {}) },
    linkage: {
      joint,
      at: (given) => {
        const value = type === 'revolute' ? rad(given) : given;
        return { value, ...at(value) };
      },
    },
    fit,
  };
  fits.set(entry, { L, result });
  return result;
}
