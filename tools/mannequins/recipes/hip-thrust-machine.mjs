// A plate-loaded hip thrust machine (refs/hip_thrust_machine/ref.md): the body sits on a low seat
// with its upper back against two round pads and a waist belt over the hips, the feet flat on a
// separate platform ahead, tilted 15° toes up, and drives the hips up until the torso and thighs
// are level. The pads, the belt, two upright handles and two plate horns all ride on one carriage:
// an arm each side, turning on an axle through the tops of two rear posts, 800 mm behind the belt.
// Two more horns on the rear posts store plates.
//
// The lifter faces +z, the belt over the hips at z = 0, the pad behind it. The carriage's joint is
// its angle up from level ahead, as a lever's: negative at the bottom of the rep, where it rests on
// a stop, with the belt 700 mm up, rising to 930 mm at the top. The rest of the machine stands
// where the ref puts it, whatever the pose: it has no seat, pad or platform adjustment.
//
// Option `strap`: `short` or `long`, the loop of the dual-loop waist strap the belt hangs on. The
// long one lifts the belt 40 mm higher over the hips, a higher lift-off point.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { basePlate, floorRail, hub, pad, padGeometry, part, plateGeometry, rod, seatOn, tube } from '../parts.mjs';
import { V, X, rad } from '../rig.mjs';

// The pivot, 880 mm up and 800 mm behind the belt; the belt 700 mm up at the bottom of the rep and
// 930 mm at the top.
const PIVOT = V(0, 0.88, -0.8);
const BELT = { z: 0, bottom: 0.7, top: 0.93 };
const reach = Math.hypot(BELT.z - PIVOT.z, BELT.bottom - PIVOT.y);
/** The carriage's angle up from level ahead that has the belt `y` up. */
const angleFor = (y) => Math.asin((y - PIVOT.y) / reach);
const LIMITS = [angleFor(BELT.bottom), angleFor(BELT.top)];
const LONG = 0.04;
// The seat top 470 mm up, its front 30 mm ahead of the belt; the platform's back edge 220 mm ahead of that.
const SEAT = { top: 0.47, front: 0.03, d: 0.33, w: 0.4 };
// The platform: 450 mm up in the middle, 400 deep and 600 wide, tilted 15° toes up, its face turned back to the seat.
const PLATFORM = { top: 0.45, depth: 0.4, width: 0.6, thick: 0.04, tilt: rad(15), z: SEAT.front + 0.22 + 0.2 * Math.cos(rad(15)) };
const FACE = V(0, Math.cos(PLATFORM.tilt), -Math.sin(PLATFORM.tilt));
// The rear posts, leaning forward from their feet to the axle, and the footprint's ends: 1.73 m.
const POST = { x: 0.42, foot: -0.92, w: 0.06, top: 0.96 };
const postZ = (y) => POST.foot + (PIVOT.z - 0.012 - POST.foot) * ((y - 0.07) / (PIVOT.y - 0.07));
const WIDTH = 0.729;
const FRONT = 0.73;
// The carriage: an arm each side at 330 mm, to the belt and on down to a horn mount, with the pad
// rolls 260 across 230 behind the belt, and a handle each side beside the hips.
const ARM = { x: 0.33, w: 0.06 };
const HORN = { y: 0.62, z: 0.14, to: 0.7, plate: 0.55 };
const PAD = { radius: 0.13, z: -0.23, from: 0.025, to: 0.285 };
const HANDLE = { x: 0.34, z: -0.04, from: 0.73, top: 1.02, radius: 0.016 };
const STOP = { z: -0.5 };
/** The arm's line at `z`, the pivot to the belt. */
const armY = (z) => PIVOT.y + ((BELT.bottom - PIVOT.y) * (z - PIVOT.z)) / (BELT.z - PIVOT.z);
/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };

export default recipe({
  name: 'hip_thrust_machine',
  // Shown alone in the studio until a pose uses it: three-quarter from the front, as the ref's
  // photos are.
  standsAlone: { camera: { target: [0, 0.5, -0.15], halfHeight: 1.2, azimuth: -40, elevation: 14 } },
  bodies: { carriage: revolute({ axis: V(-1, 0, 0), limits: LIMITS }) },
  // From on its stop at the bottom to the hips level with the thighs at the top.
  rep: { joint: 'carriage', from: 'max' },
  contacts: [
    { body: 'carriage', point: 'handles', landmark: 'hands' },
    { body: 'carriage', point: 'pad', landmark: 'back' },
    { body: 'carriage', point: 'belt', landmark: 'hips' },
    { body: 'frame', point: 'platform', landmark: 'feet' },
  ],
  fit: [{
    sets: 'strap',
    rule: 'the long loop of the waist strap for a longer-waisted body: a higher lift-off point; the short one otherwise (no seat, pad or platform adjusts)',
  }],
  build({ strap = 'short' } = {}, M) {
    const lift = strap === 'long' ? LONG : 0;
    const sides = [-1, 1];
    // Everything on the carriage is laid where it stands at the bottom of the rep, in the carriage's
    // own frame about the pivot.
    const local = (p) => p.clone().sub(PIVOT);

    // The base: a rail across the rear, a beam along the middle to the front rail, and the posts.
    const base = [
      ...floorRail(V(-WIDTH, 0.035, POST.foot - 0.01), V(WIDTH, 0.035, POST.foot - 0.01), M),
      tube(0.07, V(0, 0.035, POST.foot - 0.01), V(0, 0.035, FRONT), M.frame),
      ...floorRail(V(-0.3, 0.035, FRONT), V(0.3, 0.035, FRONT), M),
    ];
    const posts = sides.flatMap((s) => [
      tube(POST.w, V(s * POST.x, 0.07, postZ(0.07)), V(s * POST.x, POST.top, postZ(POST.top)), M.frame),
      // A plate horn out of each post's outer face, for storing plates.
      role(rod(0.025, V(s * (POST.x + POST.w / 2), 0.55, postZ(0.55)), V(s * WIDTH, 0.55, postZ(0.55)), M.metal), 'horn'),
    ]);
    const crossbar = tube(0.05, V(-POST.x, POST.top - 0.02, postZ(POST.top)), V(POST.x, POST.top - 0.02, postZ(POST.top)), M.frame);
    const axle = rod(0.02, PIVOT.clone().setX(-POST.x - 0.03), PIVOT.clone().setX(POST.x + 0.03), M.metal);

    // The seat on its post, and the platform on two legs a side, standing apart from the machine.
    const [cushion, ...seat] = seatOn(SEAT.top, SEAT.front - SEAT.d / 2, M, { w: SEAT.w, d: SEAT.d });
    const platformMid = V(0, PLATFORM.top, PLATFORM.z);
    const platform = part(padGeometry(PLATFORM.width, PLATFORM.thick, PLATFORM.depth, 0.006), M.vinyl, { solid: true });
    platform.position.copy(platformMid).addScaledVector(FACE, -PLATFORM.thick / 2);
    platform.quaternion.setFromAxisAngle(X, -PLATFORM.tilt);
    const legAt = (z) => PLATFORM.top - (PLATFORM.z - z) * Math.tan(PLATFORM.tilt) - PLATFORM.thick - 0.01;
    const legs = sides.flatMap((s) => [PLATFORM.z - 0.15, PLATFORM.z + 0.15].map((z) => tube(0.06, V(s * 0.25, 0.07, z), V(s * 0.25, legAt(z), z), M.frame)));
    const legRails = [PLATFORM.z - 0.15, PLATFORM.z + 0.15].map((z) => tube(0.05, V(-0.25, 0.035, z), V(0.25, 0.035, z), M.frame));
    const feet = sides.flatMap((s) => [PLATFORM.z - 0.15, PLATFORM.z + 0.15].map((z) => basePlate(s * 0.25, z, M)));
    // The carriage's stop, a post on the beam with a rubber bumper under its crossbar.
    const stopTop = armY(STOP.z) - 0.025 - 0.03;
    const stop = [
      tube(0.06, V(0, 0.07, STOP.z), V(0, stopTop, STOP.z), M.frame),
      pad(0.1, 0.03, 0.1, M.vinyl, V(0, stopTop + 0.015, STOP.z), { soft: 0.008 }),
    ];

    // The carriage: an arm each side from the axle to the belt and on to the horn, a crossbar at the
    // stop and one through the pad rolls, the belt strap, two handles, two loaded horns.
    const carriage = new THREE.Group();
    const onArm = [];
    for (const s of sides) {
      onArm.push(
        hub(M, local(V(s * (ARM.x + 0.04), PIVOT.y, PIVOT.z))),
        tube(ARM.w, local(V(s * ARM.x, PIVOT.y, PIVOT.z)), local(V(s * ARM.x, BELT.bottom, BELT.z)), M.frame),
        tube(ARM.w, local(V(s * ARM.x, BELT.bottom, BELT.z)), local(V(s * ARM.x, HORN.y, HORN.z)), M.frame),
        // The strap's anchor, a block on the arm that holds it `lift` higher for the long loop.
        pad(0.04, 0.05 + lift, 0.06, M.metal, local(V(s * (ARM.x - 0.04), BELT.bottom + (0.05 + lift) / 2 - 0.025, BELT.z)), { soft: 0.006 }),
        role(rod(0.025, local(V(s * ARM.x, HORN.y, HORN.z)), local(V(s * HORN.to, HORN.y, HORN.z)), M.metal), 'horn'),
        rod(HANDLE.radius, local(V(s * HANDLE.x, HANDLE.from, HANDLE.z)), local(V(s * HANDLE.x, HANDLE.top - 0.002, HANDLE.z)), M.vinyl, { held: true }),
        tube(0.05, local(V(s * HANDLE.x, armY(HANDLE.z), HANDLE.z)), local(V(s * HANDLE.x, HANDLE.from, HANDLE.z)), M.frame),
      );
      const plate = part(plateGeometry(0.225, 0.06), M.plate, { solid: true });
      plate.position.copy(local(V(s * HORN.plate, HORN.y, HORN.z)));
      onArm.push(plate);
      // The two pads, each a round cushion across the machine, a gap between for the spine.
      const roll = rod(PAD.radius, local(V(s * PAD.from, BELT.bottom, PAD.z)), local(V(s * PAD.to, BELT.bottom, PAD.z)), M.vinyl, { solid: true });
      onArm.push(role(roll, 'backPad'));
    }
    onArm.push(
      tube(0.05, local(V(-ARM.x, BELT.bottom, PAD.z)), local(V(ARM.x, BELT.bottom, PAD.z)), M.frame),
      tube(0.05, local(V(-ARM.x, armY(STOP.z), STOP.z)), local(V(ARM.x, armY(STOP.z), STOP.z)), M.frame),
      pad(2 * (ARM.x - 0.04), 0.014, 0.16, M.vinyl, local(V(0, BELT.bottom + 0.007 + lift, BELT.z)), { soft: 0.004 }),
    );
    carriage.add(...onArm);

    // The contacts' points where they stand at the bottom of the rep, in the carriage's frame.
    const grips = sides.map((s) => local(V(s * HANDLE.x, (HANDLE.from + HANDLE.top) / 2, HANDLE.z)));
    const belt = local(V(0, BELT.bottom + lift, BELT.z));
    const back = local(V(0, BELT.bottom + PAD.radius - 0.02, PAD.z));
    const surface = platformMid.clone();

    return {
      parts: [role(cushion, 'seat'), ...seat, role(platform, 'platform'), ...legs, ...legRails, ...feet, ...base, ...posts, crossbar, axle, ...stop, carriage],
      bodies: { carriage: [carriage] },
      drive(P) {
        const value = P.linkage?.value ?? LIMITS[0];
        carriage.position.copy(PIVOT);
        carriage.rotation.set(-(value - LIMITS[0]), 0, 0);
        carriage.updateMatrix();
        const onCarriage = (ps) => ps.map((p) => p.clone().applyMatrix4(carriage.matrix));
        return {
          joints: { carriage: { value, origin: PIVOT.clone() } },
          points: { handles: onCarriage(grips), pad: onCarriage([back]), belt: onCarriage([belt]), platform: [surface.clone()] },
        };
      },
    };
  },
});
