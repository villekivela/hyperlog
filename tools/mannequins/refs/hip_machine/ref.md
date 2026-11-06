# Hip abduction / adduction machine (`hip_machine`)

## Movement and exercises served
Seated, back supported, knees bent, feet on a foot plate at the bottom of each arm, the outside (abduction) or inside (adduction) of each knee against a pad on a vertical post. Each arm swings about a vertical axis under the seat. Abduction pushes the knees apart; adduction squeezes them together. Serves `machine_hip_abduction` and `machine_adduction`.

**One machine, no variant.** The kinematics are identical (two arms, each a revolute about a vertical axis, same stack); only the start angle and the direction of travel differ. Hammer Strength sells these as two cabinets (HS-HAB, HS-HAD), and Hoist as two (RS-1407 outer, RS-1406 inner), but the photos show the same frame, seat, hub and arms on both; the HS-HAD has its pads on the inner side of the posts and starts with the arms open. Model a start-angle parameter, not two machines.

## Based on
- Brand/model: Hammer Strength Select Hip Abduction (HS-HAB) and Hip Adduction (HS-HAD).
- Product pages: https://lifefitness.com/en-gb/catalog/strength-training/selectorized/hammer-strength-select-hip-abduction , https://lifefitness.com/en-eu/catalog/strength-training/selectorized/hammer-strength-select-hip-adduction
- Spec sheet: only the brochure path `/sb-assets/292045577297161/x/d78f528b33/pm-012-hs-sel-260507.pdf` seen on https://www.lifefitness.com.au/commercial/hip-abduction-hs-hab (relative link, not opened). No manual found.
- Photos viewed (all three-quarter views from the front left, none side-on or top-down):
  - HS-HAB, arms closed: https://production-gvckb4eyhna3g6c7.a03.azurefd.net/v11-24-24/1280_lossy_level2_webp/kentico13corebase/media/lfmedia/lifefitnessimages/mediasync/19-66-hammer-strength-select-hip-abduction-image-1-.webp (and the same render in other colours: `21-623-...-image-2-.webp`, `20-73-...-image-3-.webp` with the arm in red, `18-855-...-image-4-.webp`, `20-75-...-image-5-.webp`, same path)
  - HS-HAB, labelled placard: https://a.storyblok.com/f/292045577297161/1920x1920/b47cd8a84e/hip-abduction.jpg
  - HS-HAD, arms open: https://production-gvckb4eyhna3g6c7.a03.azurefd.net/v11-24-24/1280_lossy_level2_webp/kentico13corebase/media/lfmedia/lifefitnessimages/mediasync/31-16-hammer-strength-select-hip-adduction-image-new.webp and https://cdn.shopify.com/s/files/1/0789/5875/6072/files/HipAdduction.jpg?v=1787277315
  - Not used: `419-678-hip-adduction.webp` on the same path is a Life Fitness Insignia machine.
  - https://www.fitness-superstore.co.uk/hammer-strength-select-se-full-hip-abduction.html refused the fetch (HTTP 403).
- Video viewed: `machine_hip_abduction` https://youtu.be/iGd7fQffkkM, side-on 0:00–0:11, three-quarter front 0:12–0:17 (frames at 0:05, 0:12, 0:13.5, 0:14.2, 0:15, 0:16). It shows a different make (green pads, legs nearly straight forward on foot bars, pads at the outer knee), not the HS-HAB. `machine_adduction` has no video, and this one shows only abduction.
- Also seen: Hoist RS-1407 https://fitshop.com/en/article/detail/H-RS-1407-G-AUS and RS-1406 https://dynamofitness.com.au/products/hoist-commercial-inner-thigh-machine (footprint only).
- Chosen: Hammer Strength is a preferred brand and its page gives the only usable pad/ratchet numbers.

## Key dimensions (mm)
| Dimension | mm | Source |
|---|---|---|
| Footprint L x W | 1550 x 660 | Life Fitness HS-HAB page (61" x 26") |
| Height | 1400 | same (55") |
| Recommended live area | 1550 x 1960 | same (61" x 77") |
| Weight stack | 138 kg | same |
| Hoist RS-1407 L x W x H (cross-check) | 1730 x 950 x 1440 | fitshop listing |
| Seat top (front edge) above floor | 520 | estimated from the `19-66` render and the storyblok `hip-abduction.jpg`, scaled by the 1400 mm overall height (530 and 515), rough (±50) because both are three-quarter views. The video's other make: about 620, estimated from video https://youtu.be/iGd7fQffkkM at 0:05, scaled by a 0.43 m shin |
| Seat depth | 360 | estimated from the red-arm render `20-73`, scaled by the 1550 mm length, rough |
| Seat height range | fixed at 520 | kept at the measured seat top, since no picture shows the roller adjustment's travel; low confidence |
| Pivot axis | vertical, on the machine's centreline under the seat, about 140 mm behind the seat's front edge by the renders, 240 in the kit, under the hips (about 220 mm in front of the backrest) | estimated from the `20-73` render (the silver hub on a vertical post under the seat front), scaled by the 1550 mm length, rough |
| Arm reach, pivot to knee pad post | 400 by the photos; 430 in the kit, for the hips under the axis | estimated from `20-73` (about 410) and `hip-abduction.jpg` (about 400), scaled by the 1550 mm length |
| Knee pad, height x width | 280 x 160 | estimated from `hip-abduction.jpg`, scaled by the 1400 mm height, rough |
| Knee pad centre above floor | 560 | estimated from `hip-abduction.jpg` (pad spans about 420 to 700), scaled by the 1400 mm height, rough; matches the seated knee of the mannequin (0.56 m) |
| Pad spacing, arms closed | 250 between the post centres by the fit rule; 460 in the kit | chosen from the knee rule (pads touching the outside of the mannequin's knees), low confidence |
| Foot plate above floor | 80 by the photo; 100 in the kit, so it clears the floor beam | estimated from `hip-abduction.jpg` (an L-bracket at the foot of each post), scaled by the 1400 mm height, rough |

## Motion
- Two arms, one per leg, each a horizontal beam from the hub forward to a vertical post carrying the knee pad, with a foot plate at the post's foot. Both arms turn about the one vertical axis at the hub (the photos show a single hub on the centreline, not one axis per hip). The lower leg rides with the arm, so the knee and foot swing together.
- Start angle set by a ratchet in 10 degree steps (HS-HAB page). HS-HAB starts with the arms closed and parallel to the machine's length; HS-HAD starts with them open (the `31-16` and Shopify renders show the near arm swung well out).
- Range of motion: 45 degrees per leg, estimated from video https://youtu.be/iGd7fQffkkM at 0:13.5 (knees together) and 0:14.2 (near thigh pointing nearly at the camera), from thigh foreshortening against the far thigh, scaled by a 0.44 m thigh; low confidence (oblique view, different make, could be 30 to 60).
- Both arms are linked to one stack by cable. The stack isn't drawn moving, so its travel doesn't matter for the recipe.

## Adjustments and fit rules
- Seat adjusts with a roller mechanism (page); its range is not visible in the photos, so the recipe keeps it at 520.
- Start position ratchet, 10 degree increments; abduction starts knees together, adduction starts knees apart.
- Placard rule (from the page): align the red axis marker with the hip joint. The video agrees: its make's pivot column sits under the seat just in front of the backrest, under the hip, and the lifter keeps his back on the pad and holds the side handles. On the HS-HAB the single axis is on the centreline, so it can sit under the midpoint between the hips, not under either hip joint.
- The knee pads press on the outside of the knee (abduction) or the inside (adduction), the feet sit on the foot plates, the shins upright beside the posts. The video's make instead has the legs out in front on foot bars with the pads at the outer knee; the HS-HAB renders show the bent-knee setup.

## Measured part list (#241)
Where each main part sits, from the `19-66` render, the `31-16` render and the storyblok `hip-abduction.jpg`, all three-quarter from the front left, and the video at 0:12 to 0:16. Scaled by the 1400 mm height and the 1550 mm length.

| Part | Where it sits, estimated from those pictures | Was in the kit | Now |
|---|---|---|---|
| Tower | the front upright sweeps over the top in one length, as wide all the way, down onto the rear upright, the stack between them | a thin bent tube rising out of a wider upright | as measured, 130 by 70 mm, a 200 mm corner |
| Guide rods | up from the stack to a head block under the top | stopped short of everything | up into a bar between the uprights at 1175 |
| Spine | one tube raked back behind the backrest, flat top a little under the pad's, kinked just behind the seat and down to a cross member | a straight vertical leg, the pad standing off it | as measured, 80 mm, flush on the pad's back |
| Rear legs | two, from under the seat's back edge out and back to the rear foot, a cross member between them | none | as measured, from 190 out at 430 up to 300 out on the rear rail |
| Grips | a hoop behind the spine at seat height, its sides running forward beside the seat | handles 180 mm above the seat on struts ending in the air | as measured, grips 240 out at 540 up, each half of the hoop ending in the spine |
| Axle | up from the base to the seat's rail | stopped 150 mm under the seat | as measured, up to the seat's bracket |
| Axis and arms | the placard's rule puts the axis under the hip joints; the renders read about 140 behind the seat's front edge, 400 to the post | 140, 400 | 240 behind the front edge, under the mannequin's hips on the back pad, as the rule has it, and 430 to the post so the pads still meet the knees |
| Tower brace | a diagonal from the tower's side down to the floor beam | none | from the beam up to the rear upright at 450, kept short so the foot plates clear it |
| Foot plate | at the foot of each post, under the whole foot | centred on the ankle, the toes over its end, and dipping into the floor beam | 130 by 260, its middle 100 ahead of the ankle, its top at 100 so it and the post's foot clear the beam they swing over |
| Seat | on a rail under it, 520 up, 360 deep | on a bracket from a straight leg | as measured, on a bracket from the spine and a beam between the legs |
| Knee pads | 280 by 160, their middle at 560, on a post at the end of each arm | as measured | as measured, their faces 145 from the centre line with the arms closed, so the post centres stand 460 apart (the table's 250 can't hold with the post outside its pad) |
| Stack | broad face to the side, between the tower's uprights | as measured | as measured, on the left arm's joint, and rising through the rep |

The photos' rear foot is two flat plates on a low beam; the kit keeps the floor rail its footprint test measures. The grips stay on the hoop's sides behind the thighs' sweep, and the kit's hoop ends in the spine's sides where the photos' passes behind it.

## Open gaps
- No side-on or top-down photo of the HS-HAB or HS-HAD was found, so the seat height, pad heights, arm reach and pivot position are proportions from three-quarter renders, rough.
- The seat's adjustment range is chosen as fixed: no picture shows it.
- The range of motion is read from an oblique view of a different make. The HS-HAB's own travel is not shown in any picture.
- Closed pad spacing is chosen from the mannequin's knees.
