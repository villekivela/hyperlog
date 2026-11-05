// A plate-loaded hip thrust machine (refs/hip_thrust_machine/ref.md): the body sits on a low seat
// with its upper back against two round pads and a waist belt over the hips, the feet flat on a
// separate platform ahead, tilted 15° toes up, and drives the hips up until the thighs are level.
// The pads, the belt, two upright handles and two plate horns all ride on one carriage: an arm each
// side, bent twice, turning on an axle through the tops of two rear posts, behind the lifter.
// Two more horns on the rear posts store plates.
//
// Read from the photos (build/hip-thrust-refs/notes.md, scaled by the 450 mm plate), from the hip
// joint at z = 0 with the lifter facing +z: the seat's front edge 140 mm ahead, the platform's back
// edge 220 mm beyond it, the pads' centre 430 mm behind and the plates' centres and handles 180 to
// 200 mm behind, the rear posts and the axle 820 mm behind.
//
// The carriage's joint is its angle up from level ahead, as a lever's: negative at the bottom of the
// rep, where it rests on a stop, with the hip joint 650 mm up and the belt over it, rising to 930
// mm at the top. The rest of the machine stands where the ref puts it, whatever the pose: it has no
// seat, pad or platform adjustment.
//
// Option `strap`: `short` or `long`, the loop of the dual-loop waist strap the belt hangs on. The
// long one lifts the belt 40 mm higher over the hips, a higher lift-off point.
import * as THREE from 'three';
import { recipe, revolute } from '../recipe.mjs';
import { basePlate, floorRail, mitered, pad, padGeometry, part, pivotJoint, plateGeometry, rod, seatOn, span, tube } from '../parts.mjs';
import { V, X, forwardOf, rad } from '../rig.mjs';

// The pivot, 880 mm up and 820 mm behind the hips; the hip joint 650 mm up at the bottom of the rep
// (the belt over it) and 930 mm at the top, thighs level.
const PIVOT = V(0, 0.88, -0.82);
const HIPS = { z: 0, bottom: 0.6, top: 0.93 };
const REACH = Math.hypot(HIPS.z - PIVOT.z, HIPS.bottom - PIVOT.y);
/** The carriage's angle up from level ahead that has the hip joint `y` up. */
const angleFor = (y) => Math.asin((y - PIVOT.y) / REACH);
const LIMITS = [angleFor(HIPS.bottom), angleFor(HIPS.top)];
const LONG = 0.04;
// The seat top 470 mm up, its front 140 mm ahead of the hips; the platform's back edge 220 mm ahead of that.
const SEAT = { top: 0.5, front: 0.14, d: 0.38, w: 0.4 };
// The platform: 450 mm up in the middle, 400 deep and 600 wide, tilted 15° toes up, its face turned back to the seat.
const PLATFORM = { top: 0.45, depth: 0.4, width: 0.6, thick: 0.04, tilt: rad(15), z: SEAT.front + 0.22 + 0.2 * Math.cos(rad(15)) };
const FACE = V(0, Math.cos(PLATFORM.tilt), -Math.sin(PLATFORM.tilt));
// The rear posts, leaning forward from their feet to the axle.
const POST = { x: 0.42, foot: -0.91, w: 0.06, top: 0.96 };
const postZ = (y) => POST.foot + (PIVOT.z - 0.012 - POST.foot) * ((y - 0.07) / (PIVOT.y - 0.07));
// The footprint: the rear rail's half-width (1.49 m across with its caps) and the front rail's z (1.73 m long).
const WIDTH = 0.729;
const FRONT = 0.74;
// The carriage's arm each side at 330 mm: from the axle to the pad's centre, down to the horn, and
// on to the belt's anchor beside the hips. The pad rolls are 260 across 430 behind the hips; each
// handle stands 340 mm out and 200 behind; each horn carries its plate 550 mm out.
const ARM = { x: 0.33, w: 0.06 };
const PAD = V(0, 0.7, -0.43);
const HORN = { y: 0.5, z: -0.18, to: 0.7, plateX: 0.55 };
const ANCHOR = V(0, 0.6, 0);
const PAD_ROLL = { radius: 0.13, from: 0.025, to: 0.285 };
const HANDLE = { x: 0.34, z: -0.2, top: 1.02, radius: 0.016, grip: 0.86 };
const STOP = { z: -0.6 };
// How far the shoulder blades stand out of the back past the spine's own depth, where they meet the pads.
const SCAPULA = 0.065;
// The strap over the hips: a padded roll 80 mm across, laid on the body (a belt hangs from its arms and
// lies where the hips are): this far up the belly from the hip joint and up the spine, from the pose.
const STRAP = { radius: 0.04, f: 0.11, u: 0.16 };
const SIDES = [-1, 1];
/** The point on the arm's line from the axle to the pad's centre at `z`. */
const armY = (z) => PIVOT.y + ((PAD.y - PIVOT.y) * (z - PIVOT.z)) / (PAD.z - PIVOT.z);
/** Names what a part is, for the tests and the studio. */
const role = (mesh, name) => { mesh.userData.role = name; return mesh; };
/** A carriage point turned to where it stands with the carriage `value` up from level. */
const turned = (p, value) => PIVOT.clone().add(p.clone().sub(PIVOT).applyAxisAngle(X, -(value - LIMITS[0])));
/** Each handle's grip, at the bottom of the rep. */
const gripAt = (s) => V(s * HANDLE.x, HANDLE.grip, HANDLE.z);

// The platform's face and the foot on it: the sole's middle on the platform's, toes up its slope.
const SOLE = V(0, PLATFORM.top, PLATFORM.z);
const TOES = V(0, Math.sin(PLATFORM.tilt), Math.cos(PLATFORM.tilt));
/** The ankles' middle of a foot flat on the platform, its sole's middle on the platform's. */
const ankleOn = (L) => SOLE.clone().addScaledVector(FACE, L.ankleY).addScaledVector(TOES, -L.midfoot);

export default recipe({
  name: 'hip_thrust_machine',
  bodies: { carriage: revolute({ axis: V(-1, 0, 0), limits: LIMITS }) },
  // From on its stop at the bottom to the thighs level at the top.
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
  linkage: (options, L) => ({
    hip: [HIPS.bottom, HIPS.z],
    at: (value) => {
      const hip = turned(V(0, HIPS.bottom, HIPS.z), value);
      // The spine runs from the hips to lie along the pad's round top, its line a pad's radius, the
      // back's own depth and the shoulder blades' above the pad's centre.
      const toPad = turned(PAD, value).sub(hip);
      const lean = Math.atan2(toPad.y, toPad.z) - Math.asin((PAD_ROLL.radius + L.trunkBack + SCAPULA) / toPad.length());
      const across = V(0, 1, 0).applyAxisAngle(X, -(value - LIMITS[0]));
      return {
        hip,
        spine: V(0, Math.sin(lean), Math.cos(lean)),
        handle: (s) => turned(gripAt(s), value),
        across: () => across.clone(),
        sole: SOLE.clone(), soleUp: FACE.clone(), toes: TOES.clone(), ankle: ankleOn(L),
      };
    },
  }),
  build({ strap = 'short' } = {}, M) {
    const lift = strap === 'long' ? LONG : 0;
    // Everything on the carriage is laid where it stands at the bottom of the rep, in the carriage's
    // own frame about the pivot.
    const local = (p) => p.clone().sub(PIVOT);

    // The base: a rail across the rear, a beam along the middle to the front rail, and the posts.
    const base = [
      ...floorRail(V(-WIDTH, 0.035, POST.foot - 0.01), V(WIDTH, 0.035, POST.foot - 0.01), M),
      tube(0.07, V(0, 0.035, POST.foot - 0.01), V(0, 0.035, FRONT), M.frame),
      ...floorRail(V(-0.3, 0.035, FRONT), V(0.3, 0.035, FRONT), M),
    ];
    const axle = pivotJoint(M, PIVOT, X);
    const posts = SIDES.flatMap((s) => [
      tube(POST.w, V(s * POST.x, 0.07, postZ(0.07)), V(s * POST.x, POST.top, postZ(POST.top)), M.frame),
      // A plate horn out of each post's outer face, for storing plates.
      role(rod(0.025, V(s * (POST.x + POST.w / 2), 0.55, postZ(0.55)), V(s * WIDTH, 0.55, postZ(0.55)), M.metal), 'horn'),
    ]);
    const crossbar = tube(0.05, V(-POST.x, POST.top - 0.02, postZ(POST.top)), V(POST.x, POST.top - 0.02, postZ(POST.top)), M.frame);
    const through = rod(0.02, PIVOT.clone().setX(-POST.x - 0.03), PIVOT.clone().setX(POST.x + 0.03), M.metal);

    // The seat on its post, and the platform on two legs a side, standing apart from the machine.
    const [cushion, ...seat] = seatOn(SEAT.top, SEAT.front - SEAT.d / 2, M, { w: SEAT.w, d: SEAT.d });
    const platformMid = V(0, PLATFORM.top, PLATFORM.z);
    const platform = part(padGeometry(PLATFORM.width, PLATFORM.thick, PLATFORM.depth, 0.006), M.vinyl, { solid: true });
    platform.position.copy(platformMid).addScaledVector(FACE, -PLATFORM.thick / 2);
    platform.quaternion.setFromAxisAngle(X, -PLATFORM.tilt);
    const legAt = (z) => PLATFORM.top - (PLATFORM.z - z) * Math.tan(PLATFORM.tilt) - PLATFORM.thick - 0.01;
    const legZs = [PLATFORM.z - 0.15, PLATFORM.z + 0.15];
    const legs = SIDES.flatMap((s) => legZs.map((z) => tube(0.06, V(s * 0.25, 0.07, z), V(s * 0.25, legAt(z), z), M.frame)));
    const legRails = legZs.map((z) => tube(0.05, V(-0.25, 0.035, z), V(0.25, 0.035, z), M.frame));
    const feet = SIDES.flatMap((s) => legZs.map((z) => basePlate(s * 0.25, z, M)));
    // The carriage's stop, a post on the beam with a rubber bumper under its crossbar.
    const stopTop = armY(STOP.z) - 0.025 - 0.03;
    const stop = [
      tube(0.06, V(0, 0.07, STOP.z), V(0, stopTop, STOP.z), M.frame),
      pad(0.1, 0.03, 0.1, M.vinyl, V(0, stopTop + 0.015, STOP.z), { soft: 0.008 }),
    ];

    // The carriage: an arm each side from the axle through the pad's centre and the horn to the
    // belt's anchor (one length, mitred, not a solid: the checks take a solid for its box, and a bent
    // length's box is far wider than it), a crossbar at the stop and one through the pad rolls, the
    // belt strap, two handles, two loaded horns.
    const carriage = new THREE.Group();
    const onArm = [];
    // At rest, hung from its anchors over the hips: the roll's middle on the belly, `STRAP.f` up it.
    const rest = V(0, HIPS.bottom + lift, HIPS.z).addScaledVector(V(0, 0.906, 0.423), STRAP.f).addScaledVector(V(0, 0.423, -0.906), STRAP.u);
    const strapRoll = rod(STRAP.radius, local(rest).setX(-(ARM.x - 0.04)), local(rest).setX(ARM.x - 0.04), M.vinyl, { solid: true });
    // A side strap from each anchor on its arm to the roll's end, laid again each frame.
    const straps = SIDES.map(() => rod(0.012, V(0, 0, 0), V(0, 1, 0), M.metal));
    const layStraps = (middle) => SIDES.forEach((s, i) => {
      strapRoll.position.copy(middle);
      span(straps[i], local(V(s * ARM.x, ANCHOR.y, ANCHOR.z)), middle.clone().setX(s * (ARM.x - 0.04)));
    });
    layStraps(local(rest));
    for (const s of SIDES) {
      const at = (p) => p.clone().setX(s * ARM.x);
      onArm.push(
        mitered([at(PIVOT), at(PAD), at(V(0, HORN.y, HORN.z)), at(ANCHOR)].map(local), ARM.w, M.frame, { solid: false }),
        role(rod(0.025, local(at(V(0, HORN.y, HORN.z))), local(V(s * HORN.to, HORN.y, HORN.z)), M.metal), 'horn'),
        rod(HANDLE.radius, local(V(s * HANDLE.x, HANDLE.grip - 0.2, HANDLE.z)), local(V(s * HANDLE.x, HANDLE.top - 0.002, HANDLE.z)), M.vinyl, { held: true }),
        tube(0.05, local(V(s * HANDLE.x, HORN.y + 0.03, HANDLE.z)), local(V(s * HANDLE.x, HANDLE.grip - 0.2, HANDLE.z)), M.frame),
      );
      const plate = part(plateGeometry(0.225, 0.06), M.plate, { solid: true });
      plate.position.copy(local(V(s * HORN.plateX, HORN.y, HORN.z)));
      onArm.push(plate);
      // The two pads, each a round cushion across the machine, a gap between for the spine.
      onArm.push(role(rod(PAD_ROLL.radius, local(V(s * PAD_ROLL.from, PAD.y, PAD.z)), local(V(s * PAD_ROLL.to, PAD.y, PAD.z)), M.vinyl, { solid: true }), 'backPad'));
    }
    onArm.push(
      // Through the pad rolls' middles, inside them: not a solid for the contact to land on.
      tube(0.05, local(V(-ARM.x, PAD.y, PAD.z)), local(V(ARM.x, PAD.y, PAD.z)), M.frame, { solid: false }),
      tube(0.05, local(V(-ARM.x, armY(STOP.z), STOP.z)), local(V(ARM.x, armY(STOP.z), STOP.z)), M.frame),
      role(strapRoll, 'strap'), ...straps,
    );
    // The bearing housing turns with the carriage, built in its frame.
    carriage.add(pivotJoint(M, local(PIVOT), X).housing, ...onArm);

    // The contacts' points where they stand at the bottom of the rep, in the carriage's frame.
    const grips = SIDES.map((s) => local(gripAt(s)));
    // The belt's contact is where the strap hangs at rest, on the carriage.
    const belt = local(rest);
    const back = local(PAD);
    const surface = platformMid.clone();

    return {
      parts: [role(cushion, 'seat'), ...seat, role(platform, 'platform'), ...legs, ...legRails, ...feet, ...base, ...posts, crossbar, through, ...axle.fixed, ...stop, carriage],
      bodies: { carriage: [carriage] },
      drive(P) {
        const value = P.linkage?.value ?? LIMITS[0];
        carriage.position.copy(PIVOT);
        carriage.rotation.set(-(value - LIMITS[0]), 0, 0);
        carriage.updateMatrix();
        // The belt lies on the body where the pose puts it, else as it hangs.
        if (P.hipC && P.u) {
          const f = forwardOf(P.u);
          layStraps(P.hipC.clone().addScaledVector(f, STRAP.f).addScaledVector(P.u, STRAP.u).applyMatrix4(carriage.matrix.clone().invert()));
        } else layStraps(local(rest));
        const onCarriage = (ps) => ps.map((p) => p.clone().applyMatrix4(carriage.matrix));
        return {
          joints: { carriage: { value, origin: PIVOT.clone() } },
          points: { handles: onCarriage(grips), pad: onCarriage([back]), belt: onCarriage([belt]), platform: [surface.clone()] },
        };
      },
    };
  },
});
