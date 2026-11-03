// A plate-loaded preacher curl: the lifter leans onto one wide arm pad `pad` metres deep, on a
// column leaning up from the floor ahead, the chest against its back edge and its front edge
// stopping short of the elbows, so the upper arms lie on clear pad. A bar on two levers turns about
// the elbows (`P0.elbow`), on hubs beyond the pad's front edge either side of the arms, and each
// lever carries a loaded horn on a stub below it, whose plate swings forward and up as the bar
// curls.
//
// The bar and its levers are one body on a revolute joint about the elbows, its value the levers'
// angle up from level ahead.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { along, around, floorRail, hub, loadedHorn, padGeometry, part, seatOn, span, stretchedTube, swingOf } from '../parts.mjs';
import { V, X, gripTarget, rad } from '../rig.mjs';

export default recipe({
  name: 'preacher_curl_machine',
  node: 'preacher_curl',
  bodies: { bar: revolute({ axis: V(-1, 0, 0), limits: [rad(-60), rad(120)] }) },
  contacts: [{ body: 'bar', point: 'grips', landmark: 'hands' }],
  fit: [{ sets: 'seat', rule: "the armpits at the pad's back edge, the elbows just past its front" }],
  build({ seat = [0.5, 0], pad: deep = 0.08 } = {}, M) {
    const side = 0.3;
    const frontZ = seat[1] + 0.85;
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
        ...floorRail(V(0, 0.035, seat[1] - 0.3), V(0, 0.035, frontZ), M),
        ...floorRail(V(-0.3, 0.035, frontZ), V(0.3, 0.035, frontZ), M),
        ...floorRail(V(-0.25, 0.035, seat[1] - 0.3), V(0.25, 0.035, seat[1] - 0.3), M),
        armPad, column, crossbar, ...mounts, ...bosses, ...arms, ...stubs, ...loads.flatMap((l) => l.parts), handle,
      ],
      bodies: { bar: [...bosses, ...arms, ...stubs, ...loads.flatMap((l) => l.parts), handle] },
      drive(P, P0) {
        // Under the upper arms, on the side of them away from the forearms' swing, its front edge 3 cm
        // short of the elbows.
        const under = V(0, -P0.upper.z, P0.upper.y);
        const at = P0.elbow.clone().addScaledVector(P0.upper, -(0.03 + deep / 2)).addScaledVector(under, 0.045 + thick / 2);
        armPad.position.copy(at);
        armPad.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(X, under.clone().negate(), X.clone().cross(under.clone().negate())));
        // Carried from under its front edge, clear of the lap.
        const below = at.clone().addScaledVector(P0.upper, deep / 2 + 0.01).addScaledVector(under, thick / 2 + 0.03);
        span(column, V(0, 0.07, frontZ), below);
        span(crossbar, below.clone().setX(-side), below.clone().setX(side));
        handle.position.copy(P.bar);
        let joint;
        [-1, 1].forEach((s, i) => {
          const pivot = P0.elbow.clone().setX(s * side);
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
        return { joints: { bar: joint }, points: { grips: [-1, 1].map((s) => gripTarget(P, s)) } };
      },
    };
  },
});
