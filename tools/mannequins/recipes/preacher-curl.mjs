// A plate-loaded preacher curl: the lifter leans onto one wide arm pad `pad` metres deep, on a
// column leaning up from the floor ahead, the chest against its back edge and its front edge
// stopping short of the elbows, so the upper arms lie on clear pad. A bar on two levers turns about
// the elbows, on hubs beyond the pad's front edge either side of the arms, and each
// lever carries a loaded horn on a stub below it, whose plate swings forward and up as the bar
// curls.
//
// The bar and its levers are one body on a revolute joint about the elbows, its value the levers'
// angle up from level ahead.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { along, around, floorRail, hub, loadedHorn, padGeometry, part, seatOn, span, stretchedTube, swingOf } from '../parts.mjs';
import { STRAIGHT, V, X, gripTarget, hanging, rad, shouldersOf, spineLean, trunkUp } from '../rig.mjs';

// The levers' pivot, [y, z], in line with the elbows, and how far along them the bar is.
const PIVOT = [0.9575, 0.4233];
const ARM = 0.3637;
// The arm pad slopes as the upper arms lie on it, swung this far forward from hanging, and the
// column stands this far ahead.
const UPPER = hanging(65);
const COLUMN = 0.85;

export default recipe({
  name: 'preacher_curl_machine',
  node: 'preacher_curl',
  bodies: { lever: revolute({ axis: V(-1, 0, 0), limits: [rad(-30), rad(100)] }) },
  rep: { joint: 'lever', from: 'max' },
  contacts: [
    { body: 'lever', point: 'grips', landmark: 'hands' },
    { body: 'frame', point: 'armPad', landmark: 'upperArms' },
  ],
  fit: [{
    sets: 'seat',
    rule: "the armpits at the pad's back edge, the elbows just past its front",
    // The elbows on the levers' axis: the hips as far below it as the trunk and the upper arm reach.
    solve: ({ L, entry }) => {
      const reach = shouldersOf(V(0, 0, 0), trunkUp(spineLean(entry.body.trunk, L)), L).addScaledVector(hanging(entry.top.upperArm), L.up * STRAIGHT);
      const hip = V(0, ...PIVOT).sub(reach);
      return [hip.y - 0.09, hip.z];
    },
  }],
  linkage: ({ seat = [0.5, 0] }) => {
    const pivot = V(0, ...PIVOT);
    return { hip: [seat[0] + 0.09, seat[1]], at: (value) => ({ pivot, bar: around(pivot, value, ARM) }) };
  },
  build({ seat = [0.5, 0], pad: deep = 0.08 } = {}, M) {
    const side = 0.3;
    const thick = 0.07;
    // One wide pad under both upper arms, on a board.
    const armPad = new THREE.Group();
    armPad.add(part(padGeometry(0.5, thick, deep, 0.025), M.vinyl, { solid: true }));
    armPad.add(part(padGeometry(0.46, 0.012, deep - 0.04, 0.003), M.frame).translateY(-thick / 2 - 0.006));
    const column = stretchedTube(0.1, M.frame);
    const crossbar = stretchedTube(0.05, M.frame);
    const mounts = [-1, 1].map(() => stretchedTube(0.04, M.frame));
    const bosses = [-1, 1].map(() => hub(M, V(0, 0, 0)));
    const arms = [-1, 1].map(() => stretchedTube(0.04, M.frame));
    const loads = [-1, 1].map((s) => loadedHorn(M, s));
    const stubs = [-1, 1].map(() => stretchedTube(0.04, M.frame));
    const bar = along(0.014, -0.32, 0.32, M.metal, 16, { held: true });
    const grips = [-1, 1].map((s) => along(0.0175, ...(s < 0 ? [-0.25, -0.1] : [0.1, 0.25]), M.vinyl, 16, { held: true }));
    const handle = new THREE.Group();
    handle.add(bar, ...grips);
    return {
      parts: [
        ...seatOn(seat[0], seat[1], M, { pin: -1 }),
        ...floorRail(V(0, 0.035, seat[1] - 0.3), V(0, 0.035, COLUMN), M),
        ...floorRail(V(-0.3, 0.035, COLUMN), V(0.3, 0.035, COLUMN), M),
        ...floorRail(V(-0.25, 0.035, seat[1] - 0.3), V(0.25, 0.035, seat[1] - 0.3), M),
        armPad, column, crossbar, ...mounts, ...bosses, ...arms, ...stubs, ...loads.flatMap((l) => l.parts), handle,
      ],
      bodies: { lever: [...bosses, ...arms, ...stubs, ...loads.flatMap((l) => l.parts), handle] },
      drive(P) {
        // Under the upper arms, on the side of them away from the forearms' swing, its front edge 3 cm
        // short of the elbows.
        const under = V(0, -UPPER.z, UPPER.y);
        const at = P.linkage.pivot.clone().addScaledVector(UPPER, -(0.03 + deep / 2)).addScaledVector(under, 0.045 + thick / 2);
        armPad.position.copy(at);
        armPad.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, under.clone().negate(), X.clone().cross(under.clone().negate())));
        // Carried from under its front edge, clear of the lap.
        const below = at.clone().addScaledVector(UPPER, deep / 2 + 0.01).addScaledVector(under, thick / 2 + 0.03);
        span(column, V(0, 0.07, COLUMN), below);
        span(crossbar, below.clone().setX(-side), below.clone().setX(side));
        handle.position.copy(P.bar);
        let joint;
        [-1, 1].forEach((s, i) => {
          const pivot = P.linkage.pivot.clone().setX(s * side);
          const end = P.bar.clone().setX(s * side);
          bosses[i].position.copy(pivot);
          span(mounts[i], pivot, below.clone().setX(s * side));
          span(arms[i], pivot, end);
          // The horn on a stub a quarter turn below the lever, swinging forward and up as the bar curls.
          const horn = around(pivot, swingOf(pivot, end) + rad(-90), 0.2);
          span(stubs[i], pivot, horn);
          loads[i].place(horn);
          joint = { value: swingOf(pivot, end), origin: pivot };
        });
        return { joints: { lever: joint }, points: { grips: [-1, 1].map((s) => gripTarget(P, s)), armPad: [at.clone()] } };
      },
    };
  },
});
