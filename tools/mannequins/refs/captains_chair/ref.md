# Captain's chair (`captains_chair`)

## Movement and exercises served
A fixed frame with an upright back pad, two horizontal arm (elbow) pads and a grip at the front end of each pad. The lifter backs against the pad, rests the forearms on the arm pads with the elbows under the shoulders, holds the grips, lets the legs hang and raises the knees. Nothing on the chair moves.
- `roman_chair_crunch`: "Roman Chair Crunch", https://youtu.be/dd1WoEcQB4c. The video is a vertical knee raise on a captain's chair, not a crunch on the hyperextension bench, so it belongs to this chair (see `hyperextension_bench`).
- Not this item: `hanging_leg_raise` hangs from the pull-up bar (`power_rack`). The chair below also has dip handles, but the dips stay on `power_rack`.

## Based on
- Brand and model: Life Fitness Dip/Leg Raise, LBR-LD. Chosen because Life Fitness publishes a manual with a dimensioned plan drawing (length, width, live area), which gives the arm pad, grip and back pad layout to scale. It also states the step (foot pegs), the 10 degree back pad recline and the setup for the leg raise. None of the other candidates publish more than a bounding box: Hammer Strength VKR (reseller only, 37 x 24 x 60 in, https://fitnesssuperstore.com/products/hammer-strength-vkr-vertical-knee-raise-remanufactured), Body-Solid GVKR60 (60 x 37 x 24 in, https://www.banditfitness.com/cdn/shop/files/GVKR60.pdf), Gym Gear Elite (113 x 78 x 152 cm, https://gymgear.com/cdn/shop/files/Vertical_Knee_Raise.pdf) and Titan Heavy-Duty Power Tower (arm pad 12 x 6 in, no heights). None of them gives an arm pad height or a step height.
- Product page (fetched): https://www.lifefitness.com.au/commercial/life-fitness-dip-leg-raise-lbr-ld ("10 Degree reclined position and angled elbow pads to secure user position", "Step up for easy entry / exit"). Same unit: https://lifefitness.com/en-us/catalog/strength-training/benches/life-fitness-dip-leg-raise , https://facilityshop.lifefitness.com/products/life-fitness-dip-leg-raise
- Assembly instructions 1025353-0001 REV AA (fetched, 22 pages): https://kb.cybexintl.com/Assembly_Manuals/Strength/AI_LBR-LD_Life_Fitness_Dip_Leg%20Raise_1025353-0001.pdf , the same file at https://cdn.shopify.com/s/files/1/0627/6585/9029/files/Assembly-Instructions_Life_Fitness_Dip_Leg_Raise.pdf . Page 10 has the specs and a top-down plan drawing dimensioned 41.5 in x 29 in, page 11 the exercise instructions. Owner's manual for the benches (fetched, specs only, no drawing): https://kb.cybexintl.com/Owners_Manuals/Strength/Life_Fitness_Benches_and_Racks_Owners_Manual_1022961-0001.pdf
- Photos viewed (all the same three-quarter front-left render from slightly above; no side view or in-use photo was found):
  - silver, used for the measurements: https://shop.lifefitness.com/cdn/shop/files/220-1-lf-axiom-dipleg-raise.png
  - black: https://a.storyblok.com/f/292045577297161/1254x1254/97b831dfa5/dip-leg-raise.png , and charcoal: https://shop.lifefitness.com/cdn/shop/files/220-1-lf-axiom-dipleg-raise-charcoal-1000x1000.jpg
  - grey with tan pads: https://www.lifefitnessindia.com/wp-content/uploads/2024/11/Dip-Leg-Raise_LBR-LD.jpg.webp
  - small silver: https://www.lifefitness.com/Kentico13CoreBase/media/LFMedia/LifeFitnessImages/MediaSync/220-1-lf-axiom-dipleg-raise-1-.png
  - assembly instructions pages 6 (label locations, whole machine), 8 and 9 (assembly views of the grips, arm pads and back pad), 10 (plan drawing, rendered at 300 and 600 dpi to measure), 18 (foot).
  - also viewed and not used: Hammer Strength VKR, https://www.fitnesssuperstore.com/cdn/shop/files/HSVKR-main.webp
- Video viewed: `roman_chair_crunch` https://youtu.be/dd1WoEcQB4c (15 s, whole video). Contact sheets at 1 and 4 frames per second, full frames at 0:00, 0:01, 0:02.75, 0:03 (knees up), 0:06 (legs down), 0:09, 0:14.5, and crops of the chair's base at 0:01 and 0:06. The camera is low, front three-quarter on the lifter's left; there is no side-on view. The chair is another make (black, at a gym with Atlantis machines), with the same layout: upright back pad, two horizontal arm pads, a near-vertical grip rising from the front end of each pad, a short horizontal tube with a white end cap below each pad's front.
- Foot step in the video: none is visible. The front uprights go straight down to a U-shaped base on the floor. At the bottom of each rep (0:06, 0:09) the lifter's feet hang down to about the level of the base tubes, close to the floor. At 0:00 he stands upright with his forearms on the pads. A step behind his legs would be hidden, so it can't be ruled out, but he doesn't use one.

## Key dimensions (mm)
Scales: plan drawing (manual page 10) at 1.27 mm/px (300 dpi), from the 29 in (736 mm) width and 41.5 in (1054 mm) length, which agree to 1 percent. Heights come from the silver render. Each part's floor point is found by stepping along the rail and across between the rails from the rail ends, using the rail's image vector for 1054 mm and the plan positions. Heights are then scaled by the published 1550 mm overall height to the back pad top (2.26 mm/px at the back). The render is in strong perspective (it looks down about 30 degrees at the floor and 15 degrees at the pads), so every height is rough, about +-50 mm. "From the rear" means from the rear end of the floor rails, behind the back pad.

| Dimension | mm | Source |
|---|---|---|
| Footprint length x width | 1054 x 736 (41.5 x 29 in) | LBR-LD manual page 10; product page gives 41.4 in |
| Overall height | 1550 (61 in) | manual page 10. The highest points are the back pad top and the grip tops, within about 50 mm of each other in the render |
| Live area, length x width | 1970 x 1190 (77.5 x 47 in), extending forward of the chair | manual page 10 drawing |
| Machine weight / max user weight | 62 kg / 136 kg (137 lb / 300 lb) | manual page 10 |
| Floor rails | two, along the length, about 100 wide, centres 640 apart; a foot brace joins them low down; feet 15.7 (5/8 in) thick | rail width and spacing estimated from the plan drawing, scaled by the 736 mm width; foot from manual page 18 |
| Side frames | an X in each side plane above the rail: a straight tube from the rail's front half up to a rear post top, and a tube from the rail's rear half up and forward, bent upright under the arm pad's front | photos (all renders), manual page 6 |
| Arm pad top above floor | about 1380 (1350 to 1420) | estimated from the silver render, scaled by the 1550 mm height. Cross-check: the grip top (taken as about 1550) stands about 150 above the pad top in the same image. A user standing on the pegs (top about 280) has the elbow at about 280 + 1080 = 1360 |
| Arm pad length | 250 | estimated from the plan drawing, scaled by its 1054 mm length |
| Arm pad width | about 120 at the widest; the inner edge is curved | estimated from the plan drawing, scaled by its 736 mm width |
| Arm pad thickness | about 80 | estimated from the silver render, same scale as the heights, rough |
| Arm pad spacing | centres 560 apart; inner edges about 440 apart at the widest, 470 at the ends | estimated from the plan drawing, scaled by the 736 mm width |
| Arm pad position | rear end 330 from the rear, front end 580, so it starts about 40 in front of the back pad face | estimated from the plan drawing, scaled by its 1054 mm length |
| Arm pad tilt | flat | chosen, low confidence: the page says "angled elbow pads" but no render shows the angle, and the video's lifter keeps the forearms level |
| Knee-raise grip | one per side, a 30-35 thick tube about 230 long, rising from a hub at the pad's front end, leaning back about 15 degrees and slightly inward | estimated from the plan drawing (the top cap is 60 behind the base, 35 inward) and the render (length, same scale as the heights) |
| Grip position | base (hub) about 700 from the rear and about 1320 up; top cap 640 from the rear and about 1550 up; hand centre about 670 from the rear and 1430 up, about 50 above and 90 forward of the pad's front end | estimated from the plan drawing and the silver render, scaled as above |
| Grip spacing | 500 between top caps, 570 between bases | estimated from the plan drawing, scaled by the 736 mm width |
| Back pad width | about 290 | estimated from the plan drawing, scaled by the 736 mm width |
| Back pad height | about 550, from about 1000 to 1550 above the floor | estimated from the silver render: pad top and bottom against their floor points, scaled by the 1550 mm height |
| Back pad recline | 10 degrees back from vertical | product page |
| Back pad face position | about 360 from the rear at its bottom edge, about 290 at arm pad height | estimated from the plan drawing (bottom front edge) and the 10 degree recline |
| Foot step | two foot pegs, one per side frame, pointing forward, about 40 thick with rubber, under the front half of the arm pads (about 600 to 750 from the rear), about 650 apart | manual page 11 names them ("step onto the foot pegs"); position estimated from the plan drawing (mostly hidden under the pads) and the renders |
| Foot peg height | about 280 (220 to 320) | estimated from the silver render, scaled by the 1550 mm height, rough: the two ways of finding the peg's floor point give 220 and 320 |
| Dip handles (not used by this item) | one per side from the same hub, pointing forward; ends 930 from the rear, 600 apart, about 1200 up | estimated from the plan drawing and the render, rough |

## Motion
Nothing on the chair moves. The lifter's body during `roman_chair_crunch` (video https://youtu.be/dd1WoEcQB4c, front three-quarter, so angles are about +-15 degrees):
- Support throughout: forearms flat on the arm pads with the elbows on the pads' rear halves, upper arms about vertical, hands closed round the grips at the pads' front ends with the fists just above pad level, back against the back pad, torso upright (0:00, 0:06).
- Bottom: legs hang straight down, knees slightly bent (about 10 to 20 degrees), feet together close to the floor (0:06, 0:09).
- Top: hips flexed to about 110 to 130 degrees, so the thighs are 20 to 40 degrees above horizontal and the knees above the hips (0:02.75, 0:03). The knees are bent about 60 to 90 degrees on the tucked reps (0:03, the lower legs hang roughly level or below the thighs) and about 20 to 30 degrees on the near-straight reps (0:01, the feet above head height in the image). The video mixes both.
- Range of motion of the lift: hip flexion about 0 to 120 degrees; knee flexion varies with the rep style, from about 15 to 90 degrees.

## Adjustments and fit rules
- No adjustments: pad heights and spacing are fixed.
- Elbows on the arm pads, directly under the shoulders, upper arms vertical. Checked against the video at 0:00, 0:03 and 0:06. The video's elbows sit a little wider than the shoulders, which matches the 560 mm pad centres.
- Hands on the grips at the pads' front ends, fists just above the pad (video 0:00, 0:06).
- Back against the back pad through the raise (video 0:02.75: the lifter leans back on it with the knees up).
- Legs hang free at the bottom. On the Life Fitness chair the hanging mannequin's feet clear the floor: pad top 1.38 m, elbow joint about 0.04 m above it, upper arm 0.30 m, so the shoulder is at about 1.72 m and the hip at 1.19 m. The soles are then about 0.24 m off the floor and the pointed toes about 0.13 m, for the mannequin. The video's chair is lower: its lifter's feet hang close to the floor.
- Getting on: stand on the pegs, facing out, back to the pad, and put the forearms on the pads (manual page 11: "Position forearms on padded parallel bars and grasp handles. Lean back onto the padded surface and maintain an upright posture with legs hanging"). The video's lifter starts from the floor instead (0:00). The pegs sit outside the hanging legs (650 apart, on the side frames), so the legs swing forward between them.
- Back pad: the top at 1.55 m comes to about 0.17 m below the hanging mannequin's shoulder, and the bottom at 1.00 m reaches below its hip. In the video a dark edge beside the lifter's shoulder at 0:00 is probably the pad's top. The lifter hides the rest of the pad, so its height there is not measured.

## Parts against the lifter, read from the photos (#237)
Read from the silver, black and tan renders, the assembly manual's pages 8 and 10 and the video's frames at 0:00, 0:03 and 0:06; each place is relative to the lifter standing on the chair, the elbows on the arm pads (the model's origin).
- Frame: stout tubes about 60 mm across, round, the X crossing low, about 40 percent up. The straight tube runs from the rail's front half up and back to the rear post; the bent one from the rail's rear half up, forward and round to upright under the grip's hub. Page 8, step 1.
- Top rail: a tube about 60 mm across runs along each side from the rear post's top to the hub, level under the arm pad, which sits on a plate on it. Page 8, step 2.
- Back support: one upright, oval, about 50 mm thick, from the middle of a cross tube between the two rear posts' tops, leaning back with the pad; the pad bolts to its face (page 8, "Install Pads"). The posts stand beside and behind the pad, never in front of the lifter's back.
- Arm pads: flat on the top rails, about 250 by 120 by 80 mm, the lifter's elbows on their rear halves, the forearms along them and the fists at the pads' front ends (video, 0:00 and 0:03).
- Grips: each rises from a hub at the rail's front end, leaning back, level with the fist above the pad; the dip handle goes forward from the same hub (silver render).
- Foot pegs: a short thick rubber peg each side, forward from the straight tube low down; the lifter doesn't use them (video).
- Legs: hang between the side frames, 0.24 m clear of the floor at the model's size, past the pegs; the knees come up past the hands at the top (video, 0:03).

## Open gaps
- No side view of the Life Fitness chair exists online. All five photos are the same three-quarter render, and the manual has only a plan drawing. So every height (arm pads, grips, back pad, pegs) is a rough estimate from a perspective render, about +-50 mm, and the peg height about +-50 mm or worse. The plan-view numbers (pad length, spacing, grip and pad positions) are good to about +-15 mm.
- The arm pads' tilt ("angled elbow pads") is not visible in the renders and stays flat in the model.
- The video shows a different, lower make with no visible step. Its pad height can't be scaled: there is no side view, and the only scale is the lifter.
- The 1550 mm overall height is taken to the back pad top. The render puts the grip tops within about 50 mm of it, so either could be the highest point.
