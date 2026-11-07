# Standing calf raise machine (`standing_calf`)

## Movement and exercises served
Standing, facing the machine's two posts, the balls of the feet on a foot bar and the heels hanging off its back over a floor plate, shoulders under two pads on a lever, hands on two handles. The knees stay straight and the ankles do the work: the heels drop below the bar, then rise as high as they go, and the shoulders carry the pads up on an arc. Serves `standing_calf_raise` ("Press all the way up to your toes, stretch your calves at the bottom, don't bounce."; Gastrocnemius and Soleus).

## Based on
- Brand and model: Hammer Strength Select Standing Calf (HS-SC), the same machine as the Life Fitness Pro2 Series Standing Calf (PSSC). Measured on the PSSC in the video and its parts manual; sized by the HS-SC's published numbers.
- Product pages: https://lifefitness.com/en-eu/catalog/strength-training/selectorized/hammer-strength-select-standing-calf (full spec, read), https://www.lifefitnessindia.com/product/hammer-strength-select-standing-calf/ (same spec, and the renders), https://www.fitnesszone.com/product/hammer-strength-standing-calf.html (same numbers, but its copy is garbled: it calls the machine "Select Seated Standing Calf" and mentions a leg press and the chest).
- Parts manuals, the best source here: https://s3-us-west-2.amazonaws.com/files-hydra/Life%20Fitness/Diagrams/Manuals/PSSC.PDF (10 pages, dated 1/5/2019; exploded view on page 4, painted frame on page 7, stack on page 8) and https://s3-us-west-2.amazonaws.com/files-hydra/Life%20Fitness/Diagrams/Manuals/PSSCSE.PDF (the SE version, downloaded, not read). They list parts with some sizes but give no footprint.
- Owner's manual: the Pro 2 Series manual on https://personalcare.manualsonline.com/manuals/mfg/life_fitness/pro_2_series.html?p=36 lists machine specs on p=32 to p=38 (read one by one). None lists the PSSC, so no Life Fitness footprint is published for that badge.
- Photos viewed (all three-quarter from the front left, same angle, no side-on view):
  - Measuring render, black, 1000 px: https://shop.lifefitness.com/cdn/shop/files/outlet-hammer-strength-select-standing-calf-charcoal-black-1000x1000.jpg?v=1748945065&width=1000 ("the render" below). Its placard is half readable.
  - https://www.lifefitnessindia.com/wp-content/uploads/2022/09/hammer-strength-select-standing-calf-image-new.jpg and eight colour versions of the same render on that page (`imageye___-_imgi_48` to `imgi_55`, `.png`, 960 px; the third shows the upper and lower arms in red, which helped tell the moving parts from the frame).
- Video viewed: `standing_calf_raise` https://youtu.be/-qsRtp_PbVM?t=162, 2:30 to 3:40. It shows this machine. The shots used:
  - 2:44.4 to 2:50.6, side-on from about 18 degrees off square, whole machine and lifter (measuring frame 2:49, rep range 2:44.5 and 2:45.5).
  - 2:50.6 to 2:54.2, straight from behind (measuring frame 2:53): the best view of every width.
  - 2:54.2 to 2:57.3, close on the calves and foot bar from behind and to the left (2:56).
  - 3:23.0 to 3:26.3, straight down on the feet and bar (3:24.6): stance and the bar's depth.
  - 3:26.3 to 3:33.0, low side-on in shoes (bottom at 3:28, flat at 3:32); 3:33.0 to 3:38.1, the same in socks (top at 3:35, bottom at 3:38): the ankle range.
  - The frames near 3:20 are a paper on screen. The foot bar shots start at 3:23.
- Which make the video shows: the Life Fitness logo at 2:49 is on the foreground machine, not this one, and nothing on the calf machine reads clearly (its placard and stack labels at 2:53 are too small). The make comes from the shape instead. Its kinked rails, braces, gusset, foot bar, floor plate, arch and work arms all match the PSSC's exploded view, and the gym around it is white Life Fitness.
- Why the two badges are one machine: a dealer sells "Hammer Strength/Life Fitness Pro2 Selectorized Standing Calf" under the code HS-SC (https://threesixtysports.squarespace.com/products/p/preowned-hammer-strengthlife-fitness-pro2-selectorized-standing-calf). The Pro 2 manual's sizes for the hip abduction (61 x 26 x 55) and crunch (62 x 35 x 55) equal the Select sizes in this repo's `hip_machine` and `crunch_machine` refs, even the crunch's misprinted live area (70" x 58" given as 140 x 147 cm). The HS-SC render differs from the PSSC only in trim.
- Refused: https://gymequipment.co.uk/hammer-strength-select-se-full-standing-calf and https://www.fitness-superstore.co.uk/hammer-strength-select-se-full-standing-calf.html (HTTP 403); https://shop.lifefitness.com/products/hammer-strength-select-standing-calf, https://ctxhomegyms.com/products/hammer-strength-select-standing-calf-remanufactured and https://www.sparksfitness.net/products/hammerstrengthselectstandingcalf (HTTP 429, twice for the first two). The render's direct image link above did load.
- Not this machine: Life Fitness Signature Calf Extension FZCE (62 x 40 x 64) and Insignia Calf Extension, other designs.
- Chosen: this is the video's own machine, with a published footprint and a parts manual, which no other candidate offers.

## Places and scales
Heights are above the floor. `f` is forward from the plane through the post centres toward the foot bar and the lifter, `x` is across from the machine's centre line (the foot bar's and pads' middle), positive toward the stack, which is on the lifter's right.

Every number below is scaled by a published size, not by the lifter (their height isn't given, and they never get under the pads).

- Side view at 2:49: the near post runs 540 px, foot 660 px to top 120 px, for the published 1829 mm, so 3.39 mm/px at the post. Floor lines put the horizon near 300 px and the camera near 1.2 m, so 2.96 mm/px at the near foot block and about 3.1 at the near pad. The far foot block against the near gives a focal length near 950 px, and the rail's run gives the 18 degree turn. Good to about 8%.
- Rear view at 2:53: the pad's published 7-1/2" width (190 mm) spans 52 and 59 px, so 3.46 mm/px at the lifter. The arch's foot to its top spans 367 px for about 1660 mm (the arch top, from the render), so 4.52 mm/px at the frame. That scale puts the frame's full width, 253 px, at 1143 mm, the published 45".
- Top-down at 3:24.6: the bar's 536 mm (rear view) spans 450 px, so 1.19 mm/px at the bar, about 1.30 at the floor.
- The render: the far post runs 724 px for 1829 mm, 2.53 mm/px there, about 2.15 at the foot bar.

## Key dimensions (mm)
| Dimension | mm | Source |
|---|---|---|
| Footprint L x W | 1473 x 1143 | Life Fitness HS-SC page (58" x 45"; prints 148 x 114 cm). L runs `f`, W runs `x` |
| Height | 1829 | same (72"), the post tops |
| Recommended live area | 1930 x 1600 x 1829 | same (76" x 63" x 72") |
| Weight stack | 195 kg (390 lb) | same; PSSC manual p. 8: 19 plates of 20 lb. The render's stack reads 30 to 390 in 20s, so the top part weighs about 10 lb |
| Machine weight, max user | 308 kg, 136 kg | same (680 lb, 300 lb) |
| Pad height positions | 9 | same |
| Foot bar top above floor | 170 | estimated from the rail top, 180 (below), less 13: at 2:56 the bar's top sits 15 px under the rail's top on a rail end of 95 px; ±15 |
| Foot bar depth (fore and aft) | 88 | estimated from video at 3:24.6, 74 px at 1.19 mm/px |
| Foot bar thickness | 38 | estimated from video at 2:53 (10 px at 3.46 mm/px) and 2:56 (45 px against the 95 px rail end, 85 mm); ±8 |
| Foot bar clear width, end plate to end plate | 536 | estimated from video at 2:53, 155 px at 3.46 mm/px, scaled by the pad's published 190 mm width |
| Foot bar's shape | a flat-topped bar with rounded edges, level, grit coated, its ends bolted through plates into the rails | manual part 2, "Weldment PSHC Foot Platform" (shared with the horizontal calf); video at 2:56 and 3:24.6. The search snippet of a dealer page, not opened, calls it angled; no frame shows a tilt |
| Foot bar centre, `f` | 870 from the post centres (its back edge 914) | estimated from video at 2:49: near block 786 mm along the image from the near post, 408 mm in depth, less the rail's 113 mm splay; scaled by the 1829 mm height; ±60. The lifter's straight arms reach their shoulder here from the link at about `f` 330, which agrees |
| Rails | two, 70 wide x 85 tall, from the posts forward, kinked about 40% of the way from the front, spreading from 190 to 303 off the centre line at the foot bar (outer edges 678 apart there) | width estimated from video at 2:53 (20.5 px) and 3:24.6 (57 px); height from 3:38 (90 px, at about 0.95 mm/px by the rail's bolt washers and the shoe); plan from 2:53; ±10 |
| Rail top at the foot bar | 180 | estimated from video at 2:49 (67 px at 2.96: 198), 2:53 (50 px at 3.46: 173) and the render (85 px at 2.15: 183); ±15 |
| Foot blocks | a post under each rail end, as wide as the rail, on a flange on the floor about 110 wide | estimated from video at 3:35 and 3:38, scaled by the rail's 180 height; PSSC manual page 7 |
| Floor plate | 455 wide, its back edge about 350 behind the bar's centre (`f` 1220), flat on the floor, corners rounded about 30, tabs bolted to the foot blocks, a thin black foot pad on top | width estimated from video at 3:24.6 (345 px at 1.30: 448) and 2:53 (149 px, nearer the camera than the bar: about 460); length from 3:24.6 (275 px); manual parts 5 and 19 |
| Posts | two, about 80 wide x 140 deep, side by side, centres about 380 apart, at `x` -190 and +190 | spacing estimated from video at 2:53 (rails' rear ends 110 px apart outside at 4.52 mm/px: 415) and manual p. 7 (340; the render's 285 is foreshortened), rough; section from 2:49 and 2:53, ±15 |
| Arch and stack bay | from the right post across to `x` 913 and down to the floor, rounded outer corner of about 150, top 1660, upright 77 wide, in the plane of the posts; a floor beam joins its foot to the post | top estimated from the render (70 px under the near post at 2.38 mm/px); upright and corner from video at 2:53 (17 px and about 32 px at 4.52); `x` from the published 1143 and the post place; shroud 21.43" (544) wide fits the bay (manual p. 6) |
| Stabilizer | a floor bracket bolted to the frame, running back about 250 behind the post centres | chosen to close the published 1473 length (870 + 350 + 253); manual p. 7 part 7; no frame shows its end |
| Diagonal braces | one each side, from the rail at `f` 280 up to the post at 540 | estimated from video at 2:49 (70 px along, 104 px up), scaled by the 1829 mm height; ±40 |
| Gusset | a plate on the posts at the centre line, about 190 wide, 280 to 520 high, the low pulley inside it | estimated from video at 2:49 (320 to 560) and the render (253 to 480), scaled by the 1829 mm height, rough |
| Cross member | a box between the posts, top about 1130, a rubber bumper on it under the lower arm | estimated from video at 2:49 (330 px) and the render (1063 to 1100), scaled by the 1829 mm height; manual parts 28 and 41 |
| Top pivot (upper arm) | axis across the machine at `f` 0, 1745 high, in pillow blocks on both posts | estimated from video at 2:49 (147 px: 1739) and the render (bracket 76 mm under the post top: 1750), scaled by the 1829 mm height; manual part 10; ±40 |
| Lower pivot (lower arm) | axis across the machine at `f` 0, about 1220 high, in pillow blocks on both posts | estimated from video at 2:49 (300 px) and the render (1232), scaled by the 1829 mm height; ±40 |
| Upper arm, pivot to link pin | 300 (294 ahead, 60 above the arm's line) | estimated from video at 2:49, link top at 457, 100 px, scaled by the 1829 mm height; ±40 |
| Upper arm, pivot to pad centre | about 800; the pad's back end 934 | estimated from video at 2:49: pad back end at 610, 30 px, less half the 279 mm pad, scaled by the 1829 mm height and the 18 degree turn. The render reads more, about 900. See Open gaps |
| Lower arm, pivot to link pin | 335 | estimated from video at 2:49, pin at 485, 285 px, scaled by the 1829 mm height; ±40 |
| Lower arm, pivot to its tip and moving pulley | 425 | estimated from video at 2:49, tip at 510 px, same scale; ±40 |
| Arm tubes | 38 x 51 section, the pad tubes running along the arm's line | PSSC manual part 21 (12 caps, 1.5 x 2 in, on these ends in the exploded view) |
| Link | a telescoping post, 51 square inner tube, pull pin in a row of holes about 28 apart | seat size from manual part 7 (2 x 2); hole pitch estimated from the render (12 px at 2.3 mm/px), rough. Nine positions published |
| Link length, pin to pin | about 630 as parked at 2:49, about 480 for the mannequin (below) | estimated from video at 2:49, scaled by the 1829 mm height; the 480 is chosen from the fit rule, low confidence |
| Shoulder pads | two, 190 wide x 279 long, about 100 thick, the long side fore and aft | size from PSSC manual part 60 ("Pad, Shoulder 7-1/2 X 11"), long side by video at 2:53 (the width from behind fits 190, not 279); thickness estimated from video at 2:49 (33 px at 3.1 mm/px) and the render, ±15 |
| Pad spacing | 400 centre to centre, a 208 gap for the neck | estimated from video at 2:53, 116 px and 60 px at 3.46 mm/px, scaled by the pad's published 190 mm width |
| Pad underside, parked | about 1600 in the render, about 1850 at 2:49: two different holes | estimated from the render (20 px under its horizon) and video at 2:49 (93 px at 3.1 mm/px), scaled by the 1829 mm height, ±50 |
| Handles | two bent tubes from the upper arm, grips 215 long, 32 OD. With the arm level, each grip runs from 300 ahead of the pivot and 1660 high to 370 ahead and 1862 high, its top leaning toward the lifter 19 degrees | length and place estimated from video at 2:49 (near grip 453, 135 px to about 445, 70 px, rotated to level), scaled by the 1829 mm height, ±40; OD from manual part 38 (1.25"); the render shows the same bend |
| Handle spacing | 420 centre to centre | chosen from the pad spacing (the grips stand just outside the pad tubes in the render), low confidence |
| Stack | 540 wide x 565 tall, centre at `x` 533, bottom about 200 high, its broad face toward the front, numbers on that face | estimated from video at 2:53 (right edge and number column: half width 60 px; height 125 px; bottom 44 px over the arch's foot), scaled at 4.52 mm/px by the published 1143 mm width; the render agrees (216 px, 250 px) |
| Guide rods | two, 19 OD, 1115 long, about 176 apart, from about 225 to 1340 high | length and OD from PSSC manual part 13 (.75 x 43.88"); spacing and place estimated from video at 2:53 (39 px at 4.52 mm/px) |
| Cable and pulleys | 3823 long (150-1/2"), four 114 OD pulleys (4.5") | PSSC manual parts 5 and 6 |

## Motion
- It is an arc, not a slide. The upper arm, carrying the pads and handles as one rigid piece, turns on the top pivot. The PSSC manual settles it: "Upper Work Arm" and "Lower Work Arm" are separate weldments, each on two pillow blocks on the posts, joined by the adjustable link on 1 inch shafts (parts 1, 4, 9, 10, 11, 12). The arms are a four-bar with the link as the coupler, so a pad's path is a true circle about the top pivot.
- Set the pads with the link: its pull pin changes the pin-to-pin length, which tilts the upper arm (the lower arm stays on its bumper). So the pads tilt with their height. No pad pivot exists.
- Pad path for the rep: about 155 up. Pads at 800 from the pivot turn through about 10 degrees, from 10.6 below level at the stretch to about level at the top. They swing about 14 back along the way, nearly straight up, because the pads work at about the pivot's height. Estimated from the lifter's rise, the 150 mm of head travel at 2:44.5 and 2:45.5 (48 px at about 3.1 mm/px) and from the ankle (below: 146, plus 17 from the shin coming upright). No frame shows the pads moved: see Open gaps.
- Lower arm: the link pushes it up with the upper arm, about 10 degrees (58 at its link pin, 74 at the tip).
- Stack drive: one cable. The render shows two strands from the lower arm's tip to the gusset and one up the post to the arch, then across to the stack's head, and the exploded view has a pulley on the lower arm's tip, one in the gusset and two up top. So the moving pulley works a 2 to 1 block: the stack rises twice the tip's rise, about 148 for 155 at the pads, or 0.95 to 1. Estimated from those drawings and the arm lengths above, low confidence on the ratio. It matches the pin's weight being about what the shoulders carry.
- At rest the lower arm sits on the bumper and the stack on its cushions.

## How the lifter stands
- Placard, a blurred reading of the render: standing on the floor, set the pads to shoulder height; back straight, shoulders under the pads; bend the knees and step onto the foot bar, balls of the feet on the bar, heels hanging off the back; extend the legs to bring the stack up; raise the heels, hold briefly, return; bend the knees to bring the stack down and step off. So the knees do the lifting off the bumper and stay straight for the set.
- The video's lifter does bodyweight priming sets. They stand behind the parked pads, which sit over their head, arms straight forward, both hands on the link at about shoulder height (2:44 to 2:50). Use their feet, ankles and legs. Take the shoulders under the pads and the hands on the handles from the placard.
- Faces the posts, the stack on their right, the floor plate under their heels.
- Feet: parallel, toes straight ahead, about 180 centre to centre at the balls (155 px at 1.19 mm/px at 3:24.6), about 230 at the heels at 2:53. Scaled by the bar's 536 mm. Keep them straight: the study on screen near 3:10 compares turned-in and turned-out feet, and the library's exercise asks for neither.
- On the bar: the ball of the foot on the bar's centre, the toes past its front edge by about 45, the rest hanging behind it (3:24.6).
- Knees: straight through the rep, not locked hard (3:35, 3:38).
- Trunk: upright, in line with the legs. The shoulders stay over the bar at about `f` 870, near the back of the pad (2:49, for the body; the pad contact is from the placard).
- Hands: on the two grips, arms forward and nearly straight, about 0.5 m ahead of the shoulder at shoulder height. Chosen from the handle's place, low confidence.
- Ankle range, in socks, measured on the sole from ball to heel and on the shin:

| Point of the rep | Sole | Shin | Ankle | Source |
|---|---|---|---|---|
| Stretch | heel 28 degrees below level | 11 degrees forward of vertical | about 39 degrees dorsiflexed | estimated from video at 3:38: ball 490, 500 px to heel 695, 610 px; shin 590, 150 to 665, 540 px |
| Top | heel 37 to 40 degrees above level; 40 used | vertical | about 40 degrees plantarflexed | estimated from video at 3:35: ball about 530, 505 px to heel 670, 400 px; good to 5 degrees |
| In shoes (not used) | sole about 42 below level at 3:28, shin 3 forward | | | a stiffer sole and raised heel read steeper |

Angles need no scale. The heel clears the floor plate by about 75 at the stretch (bar 170, 190 from ball to heel, 28 degrees).

## Adjustments and fit rules
- Pad height: nine positions on the link (Life Fitness page). The only adjustment.
- Fit rule, from the placard: the pads rest on the shoulders with the legs straight and the heels at the stretch, a little above the arm's rest so the knees can lift the stack off.
- For the mannequin, chosen from that rule, low confidence: its shoulder joint stands at about 1.45 m and the top of the trapezius at about 1.50 m (`recipes/safety-bar.mjs`). On the 170 bar with a level foot the shoulder top is at 1670. The ankle, 140 behind the ball and 80 above the sole, drops 75 at the stretch and rises 71 at the top, so the pad's underside goes from 1595 to 1741. That puts the arm at 10.6 degrees below level at the stretch, level at the top, the link near 480, and the bumper's rest about 40 under the stretch. The pad's underside and the arm's line are treated as one here; the 50 mm between them moves all of this by about 4 degrees.
- Fit rule for the feet: the ball of the foot over the bar's centre, as above.

## Open gaps
- Built differently from the numbers above, to fit the mannequin:
  - The pads hang under the upper arm, their faces 122 off its line, so the arm runs from about 1 below level to about 9 above it, not 10.6 below to level. The pad's underside and its travel keep their numbers.
  - The pad's reach is 870, inside the 800 to 900 the sources give, which puts the shoulders under its back third.
  - The grips stand 30 nearer the lifter than read, and 520 apart, not 420, for the arms' reach and to keep the fingers off the arm's tube.
  - The gusset is 300 wide, not 190, to reach both posts as a plate with the low pulley on it.
  - The lower arm is a fork, so the cable rises between its tubes, and the stack and the arch run in a plane 95 ahead of the posts, not in theirs, the cable 20 ahead of that again to pass the guide rods, with a corner wheel at each turn: no frame shows the route inside the posts.
  - The arch's upright stands at 804, not 913: 913 with rails splayed to 339 makes 1252 across, against the published 1143.
  - The stack is the library's, 260 wide, not 540. The two strands to the lower arm's tip are drawn as one, and the lower arm turns as far as the upper.
- The number I trust least: the upper arm's reach to the pad centre, 800. The video reads 800 from a view turned 18 degrees with the pads high, the render about 900 from a three-quarter view, and no side-on picture exists. Shoulders over the bar keep the contact on the pad for either, but a 100 mm error moves the handles or the pad's place. Solve it in the studio by keeping the shoulders under the pad's back third.
- No frame shows the pads loaded or moving. The video's lifter never gets under them, and no demo of this machine was found. Pad travel comes from their rise and their ankles; the pad heights for the mannequin are chosen from the fit rule; the pad's tilt on the shoulder is untested.
- Stack travel and the 2 to 1 reading come from the drawings; no frame shows the stack up.
- Post spacing is rough: 415, 340 and 285 by three views, 380 used.
- The rail length, 870, and the rear stabilizer's 250 are tied together to close the published 1473, so an error in one hides in the other. The manual gives no lengths for frame parts.
- Handle spacing is chosen. The rear view hides the grips behind the lifter.
- The foot bar's exact section and any tilt: a level, flat-topped bar is what the frames show, against a dealer's word "angled" in a search snippet.
- The hole pitch is rough and the holes' range isn't shown, so the height span of the nine positions isn't known. The two parked heights seen are 1600 and 1850.
- The lifter's height is unknown, so every scale is a machine size, and no lifter cross-check is used except the arm reach to the bar.
- The render's and the video's machines may differ in trim (the handle bend, the pad tube ends). The frame matches.
