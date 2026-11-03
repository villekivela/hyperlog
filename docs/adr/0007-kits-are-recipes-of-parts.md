# Kits are recipes of parts, with joints and contacts

Every kit was its own builder function in `kit.mjs`, and each moving part was placed by code that
read the pose. That was fine for eight kits; the library needs dozens of machines. So a kit is now a
recipe: parts from one library (tubing, pads, pulleys, stacks, handles) placed by dimensions, a
fixed frame, and moving bodies on revolute or prismatic joints with limits. A contact ties a body to
the mannequin. A machine exercise is solved from the joint: the rep drives it through its range and
IK pins the hands, shins or feet to the contacts. The recipe's fit rules set its adjustments for the
mannequin.

- **Byte-identical first.** The existing kits are ported with the bake unchanged, so CI proves the
  format can say what the code said. Solving machine-first comes after, as its own change.
- **What a machine decides stays out of the entry.** A pose entry on a machine gives the rep's range
  and how the body sits. The pivot, the path and where the seat puts the hips are the recipe's, so a
  machine's second exercise can't disagree with its first. Cables stay body-first: their attachment is
  a free body the hands carry, on no joint.
- **Deterministic.** Recipes are code in the repo, so the same recipe bakes the same bytes and CI's
  re-bake check stays.

## Considered options

- **A builder per kit, as before:** no new format, but each machine costs about 100 lines, and every
  one moves its parts its own way.
- **Modelling in CAD and importing glTF:** the owner's own trade, but binaries in the repo, no diffs,
  and nothing for the checks to read.
- **Asset packs or image-to-3D:** fast to get, but other people's styles and messy meshes, and #149
  rules out shipping someone else's model.
- **Body-first for machines:** keeps one solver, but the handle has to follow the hands, where on a
  machine the path is the machine's.
