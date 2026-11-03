// A leg curl: seated (the leg extension's machine with the roller behind the ankles and a pad over
// the thighs), or with `lying` a lying one, laid out as the owner's reference (#149): a bed bent
// over the hips, its top at `hips` [y, z], the thigh pad sloping down `thigh` degrees to just short
// of the knees and the chest pad `chest` degrees down toward the head, on legs leaning toward the
// head; a bar under the bed's head end with grips angled out and down; the tower beside the bed at
// the hips; and on the near side a post up to the cam lever about the knees, its roller behind the
// ankles, with a plate on a horn low on the post.
//
// Either way the lever is a body on a revolute joint about the knees, its value how far it has
// turned from the stretch, where the stack rests; the stack rises 22 cm a radian of it.
import * as THREE from 'three';
import { recipe } from '../recipe.mjs';
import { floorRail, kneeDrive, pad, part, plateGeometry, rod, rodGeometry, span, stretchedTube, tube } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';
import { SEATED_FIT, kneeLever, seatedKnees } from './leg-extension.mjs';

function lying({ hips: [hy, hz] = [0.66, 0], thigh = 21, chest = 10 } = {}, M) {
  const tz = hz + 0.05;
  const drive = kneeDrive(M, { z: tz, toward: -1, down: 0.37, behind: true });
  const { side, tx, z0 } = drive;
  const apex = V(0, hy, hz);
  // A pad `length` long from the apex `way` along z (±1), sloping down `slope` degrees, on a board
  // and a beam; `at(d, h)` is the point `d` along it and `h` below its top.
  const bedPad = (length, way, slope) => {
    const a = rad(slope);
    const dir = V(0, -Math.sin(a), way * Math.cos(a));
    const normal = V(0, Math.cos(a), way * Math.sin(a));
    const holder = new THREE.Group();
    holder.add(
      pad(0.32, 0.07, length, M.vinyl, V(0, -0.035, 0), { soft: 0.025 }),
      pad(0.28, 0.012, length - 0.04, M.frame, V(0, -0.076, 0), { soft: 0.003 }),
      tube(0.06, V(0, -0.112, -length / 2 + 0.03), V(0, -0.112, length / 2 - 0.03), M.frame),
    );
    holder.position.copy(apex).addScaledVector(dir, length / 2 + 0.01);
    holder.rotation.x = way * a;
    return { mesh: holder, at: (d, h) => apex.clone().addScaledVector(dir, d).addScaledVector(normal, -h) };
  };
  const thighLength = 0.37;
  const chestLength = 0.62;
  const thighPad = bedPad(thighLength, -1, thigh);
  const chestPad = bedPad(chestLength, 1, chest);
  const head = chestPad.at(chestLength, 0);
  const kneeEnd = thighPad.at(thighLength - 0.04, 0.112);
  const hipLeg = chestPad.at(0.1, 0.112);
  const headLeg = chestPad.at(chestLength - 0.05, 0.112);
  // The handles: a bar across under the head end, hung from the chest pad's beam, its grips angled
  // out and down.
  const handleY = head.y - 0.24;
  const handleZ = head.z - 0.02;
  // A small plate stored on a horn low on the post.
  const horn = part(rodGeometry(0.02), M.metal);
  const stored = part(plateGeometry(0.1, 0.03), M.plate, { solid: true });
  // A bar from under the thigh pad's knee end out to the post.
  const kneeBar = stretchedTube(0.05, M.frame);
  return {
    parts: [
      thighPad.mesh, chestPad.mesh, ...drive.parts, horn, stored, kneeBar,
      ...floorRail(V(0, 0.035, z0 + 0.035), V(0, 0.035, head.z - 0.2), M),
      ...floorRail(V(-0.3, 0.035, head.z - 0.165), V(0.3, 0.035, head.z - 0.165), M),
      ...floorRail(V(tx, 0.035, z0 + 0.035), V(tx, 0.035, tz + 0.3), M),
      tube(0.06, V(0, 0.07, hipLeg.z - 0.22), hipLeg, M.frame),
      tube(0.06, V(0, 0.07, head.z - 0.2), headLeg, M.frame),
      tube(0.05, chestPad.at(chestLength - 0.05, 0.14), V(0, handleY, handleZ), M.frame),
      tube(0.04, V(-0.14, handleY, handleZ), V(0.14, handleY, handleZ), M.frame),
      ...[-1, 1].flatMap((s) => [
        rod(0.016, V(s * 0.14, handleY, handleZ), V(s * 0.3, handleY - 0.06, handleZ), M.metal, { held: true }),
        rod(0.0195, V(s * 0.17, handleY - 0.011, handleZ), V(s * 0.29, handleY - 0.056, handleZ), M.vinyl, { held: true }),
      ]),
    ],
    bodies: { lever: drive.lever },
    stacks: { lever: drive.tower },
    follow: drive.follow,
    drive(P, P0, P1) {
      const { pivot, roller, joint } = drive.place(P, P0, P1);
      span(kneeBar, kneeEnd, kneeEnd.clone().setX(side));
      const low = V(side, 0.07, z0).lerp(pivot, 0.3);
      span(horn, low, low.clone().add(V(0.14, 0, 0)));
      stored.position.copy(low).add(V(0.08, 0, 0));
      return { joints: { lever: joint }, points: { roller: [roller], handles: [-1, 1].map((s) => P.hand(s).clone()) } };
    },
  };
}

export default recipe({
  name: 'leg_curl',
  // From the stretch, the knee all but straight, to a bend of about 135°.
  bodies: { lever: kneeLever(X, rad(120)) },
  contacts: ({ lying: flat = false } = {}) => [
    { body: 'lever', point: 'roller', landmark: 'shins' },
    flat ? { body: 'frame', point: 'handles', landmark: 'hands' } : { body: 'frame', point: 'thighPad', landmark: 'thighs' },
  ],
  stacks: [{ joint: 'lever', ratio: 0.22 }],
  fit: ({ lying: flat = false } = {}) => (flat ? [{ sets: 'hips', rule: "the knees just past the thigh pad's end, on the pivot axis" }] : SEATED_FIT),
  build: (options, M) => (options?.lying ? lying(options, M) : seatedKnees({ ...options, curl: true }, M)),
});
