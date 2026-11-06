# Seated cable row station (`seated_row`)

Stand-alone selectorized low row: one long low bench on a single rear post, two angled foot plates, a rubber deck rising from them to a tall enclosed stack tower, and one cable leaving the tower's front. Not the `cable_tower` or `cable_crossover` kit.

## Movement and exercises served
The lifter sits on the bench facing the tower, feet braced on the plates, and pulls a handle on one cable toward the body. The bench, the plates and the pulley stay still. Only the cable, the handle and the stack move.

- `seated_cable_row`: close neutral handle. Trunk rocks a little, the elbows drive back.
- `wide_grip_seated_cable_row`: wide overhand bar. Trunk stays upright, the elbows flare out and back.
- `seated_cable_kelso_shrug`: long bar. Trunk upright, a short pull, about 1 second held at the top, then the shoulder blades peel apart under control (the library's note).
- `seated_cable_deadlift`: long bar. Arms stay long, the trunk hinges forward over the legs and rocks back past upright.

One machine, no variants. The lifts differ by attachment and body path.

## Based on
- Brand and model: Atlantis Strength D124 Low Row (also written D-124), Precision Series. It is a dedicated selectorized low row, not a lat tower combo, and it publishes length, width, height, weight, stack and pulley size. Atlantis is the make several of the library's other videos show.
- Product page (specs read): https://atlantisstrength.com/gym-equipment/d124 (the same page answers at https://atlantisstrength.com/gym-equipment/ms7 and https://atlantisstrength.com/gym-equipment/rd124).
- Dealer page (specs read, gives 229 x 69 x 243 cm, 281 kg): https://performaxx.com.au/product/atlantis-low-row/
- Spec sheet: none linked. Owner's manual, covering the whole range, read as text: https://atlantisstrength.com/app/uploads/2023/01/atlantisownersmanual2023-en.pdf. It gives cable sizes only: no tube, seat or plate sizes, and it never names the D124.
- An older Atlantis catalogue gives the D-124 as 90 x 27 x 96 in (229 x 69 x 244 cm), seen only as a search snippet: https://www.bcrpa.bc.ca/media/361919/atlantis_product_catalog.pdf returns HTTP 404 on fetch.
- Near twins, for comparison:
  - Matrix Aura Low Row, 230 x 71 x 240 cm, 134 kg stack, 1:1, two independent cables. Search snippet only, the page refused the fetch (HTTP 429): https://commercial.fitnessexperience.ca/products/matrix-aura-low-row
  - York ST Low Row (specs read), (L) 72 x (W) 30 1/4 x (H) 90 in, a second "footprint" line of 94 x 30 x 25 in, 11.5 gauge steel, a 2.5 in seat pad, a diamond-plate footplate: https://yorkbarbell.com/product/st-low-row/
- Why not the preferred Hammer Strength: its HD Elite Cable Row (HDLRW, 245 x 188 x 244 cm, row station 290 lb at 1:1) is discontinued, has no photos on the page and shares its frame with an adjustable pulley station: https://www.lifefitness.com/en-gb/catalog/strength-training/cable-motion/hd-elite-cable-row. The Hammer Strength Select Seated Row is 132 x 86 x 180 cm with a 138 kg stack, far too short for a long bench: https://lifefitness.com/en-gb/catalog/strength-training/selectorized/hammer-strength-select-seated-row
- Not reachable: elitefts low row (HTTP 429), PMX Raptor at johnsonfitness.com (403), American Fitness Elite Plus Long Pull Row (429), archiexpo's Atlantis catalogue (403), Atlantis's 2024 strength catalogue PDF (404), beyondthebasicsfitness.com (404).
- Photos read: Atlantis's five cut-out product images, all three-quarter, none side-on or top-down. Base `https://atlantisstrength.com/photos/d124-low-row/`:
  - `D124.png?o=3`, whole machine from the tower's front corner.
  - `D124_1.png?o=3`, from the bench end, the tower at the right: both foot plates, the deck, the handle hanging on the cable, the stack.
  - `D124_2.png?o=3`, the most side-on view, the tower at the left. The main measuring image.
  - `D124_3.png?o=3`, the tower's rear and side, the bench running away.
  - `D124_4.png?o=3`, the rear corner, the tower's two feet.
  - How they were read: no image could be saved, so each was opened in a browser and sampled as a brightness and transparency grid, and edges were taken from that. That finds outlines and heights well, and it can miss surface detail and small parts. Pixel values below are from the images at their native size. An owner's look at the images would settle the items marked rough.
- Videos viewed (none shows a D124, and no maker's name is readable; they settle the body and the cable's line, not Atlantis sizes). Times come from matching the frame sheets against the videos:
  - `seated_cable_row` https://youtu.be/FbWfA_s0XL8?t=273: 4:34 to 5:02. Three-quarter from above 4:38 to 4:45, side-on from 5:00 (reach) and 5:02 (finish). The floor is out of frame in the side-on shots.
  - `wide_grip_seated_cable_row` https://youtu.be/jLvqKgW-_G8?t=577: 9:38 to 9:46. Back view 9:38, side-on 9:43 and 9:46, floor in frame, the machine's plate at the left.
  - `seated_cable_kelso_shrug` https://youtu.be/qULwEAa7Lfo: whole video (0:27). Side-on 0:01 to 0:13, front-on 0:18 to 0:25.
  - `seated_cable_deadlift` https://youtu.be/FCe6PIQbB-s: whole video (0:29). Side-on 0:01 to 0:19, front-on 0:23 and 0:25. Same room and bench as the kelso video.

## Key dimensions (mm)
Along the machine, L runs from the tower's rear (0) toward the bench end (2286). Heights are above the floor.

D124_2 is the measuring image. The tower's front edge stands 985 px from floor to top, so the published 2413 mm height gives 0.41 px/mm there. Parallel bench lines in that image meet at a point about 4640 px left of the bench end, which gives about 0.49 px/mm at the bench end and places everything along L. That vanishing point rests on a 5 px convergence, so L values are good to about 15%, and the heights to about 40 mm.

In the videos the lifter is taken as 1.75 m, sitting 910 mm from seat to crown. That gives 2.6 mm/px in the deadlift frames, 2.0 mm/px in the row's 5:00 frame and 3.3 mm/px in the wide row's 9:43 frame.

| Dimension | mm | Source |
|---|---|---|
| Overall length | 2286 (90 in) | Atlantis D124 page |
| Overall width | 686 (27 in), the tower's width; the bench and plates are narrower | Atlantis D124 page; the tower as its widest part read from D124_1 |
| Overall height | 2413 (95 in); dealer 243 cm, catalogue 96 in (2438) | Atlantis D124 page. The three sources disagree by 25 mm |
| Machine mass | 281 kg (619 lb) | Atlantis D124 page |
| Stack | 141 kg (310 lb) | Atlantis D124 page. Plate count and step not published |
| Pulley diameter | 152 (6 in) | Atlantis D124 page |
| Cable | 4.8 (3/16 in), rated 1905 kg (4200 lb) | Rating from the D124 page; size from the owner's manual, which gives 3/16 in for stacks over 200 lb |
| Bench top height | about 430; rough, +-40 | estimated from D124_2 at x 650 (pad and upper rail 53 px, gap 66 px, lower beam 25 px, 40 px to the floor), scaled by the 2413 mm height. The wide row's bench agrees: 130 px at 9:43 gives 430, scaled by the 1.75 m lifter. The deadlift and kelso bench is lower, about 340 to 370 at 0:12 of the deadlift video, scaled the same way |
| Bench pad length | 1000; low confidence | chosen from the videos' benches: 1040 (deadlift 0:12), 1080 (row 5:02), 925 (wide row 9:43), each scaled by the lifter. The D124 images don't separate pad from rail |
| Bench pad width | 300; low confidence | chosen, the same fit rule as `pec_deck`'s back pad (the mannequin's hips and back). No view settles it |
| Bench pad thickness | 65 | estimated from D124_2 (about 30 px at 0.45 px/mm), scaled by the 2413 mm height; York publishes 2.5 in for its pad |
| Bench end | L 2286 | published length |
| Rear post centre | L about 2070, 215 in from the bench end | estimated from D124_2 (x about 870 against the end at 970), scaled by the vanishing point above; rough |
| Foot plate bottom edge | L about 1170, about 80 high | estimated from D124_2 (y about 985), scaled by the 2413 mm height; rough |
| Foot plate top edge | L about 1005, about 400 high | estimated from D124_2 (y about 850), same scale; rough |
| Foot plate angle | about 30 from vertical, the top leaning toward the tower; low confidence | estimated from D124_2, where the plate's edge leans 19 degrees in a view about 45 degrees off side-on. The videos' plates lean 32 degrees (deadlift 0:01) and about 40 degrees (wide row 9:43) |
| Foot plate size | two plates, each about 190 wide and 360 long, about 90 apart; about 470 across both; low confidence, +-30 | estimated from D124_1 (70 to 78 px each and 42 px between them in a view about 30 degrees off the bench's line), scaled by the 686 mm tower width. Length follows from the two edges above |
| Foot plate to seat | plate face to hips 570 to 830 for a 1.75 m lifter; the bench end stands 1115 from the plate's bottom edge | hips from the deadlift video at 0:05 and 0:19, scaled by the lifter; the bench end from D124_2 |
| Deck | from the plates' top edge at about 400, rising to about 570 at the tower's front; rubber covered | estimated from D124_2 (y about 850 and 770), scaled by the 2413 mm height; the rubber from the Atlantis page |
| Cable exit (low pulley) | built at 850, the videos' height, at the tower's front, L about 640; the D124 reads about 700 high, rough, +-80 | estimated from D124_1, where the handle hangs from about y 810 with the tower's base at about y 1130 (2.2 mm/px), scaled by the 2413 mm height. The pulley itself is hidden: this is where the cable comes out, not a measured wheel |
| Cable exit to plate's middle | about 450 along the machine | follows from the two rows above |
| Cable line | rises from the exit to the hands: about 16 degrees at the reach, about 9 at the finish, for hands 890 high at L 1300 and L 1800 | follows from the exit above and the hands in the Motion table. The videos differ: the deadlift at 0:12 shows the cable near level, rising 3 degrees toward the machine at about 820 high, scaled by the lifter. The row at 5:00 puts it about 520 above the seat, so about 890 high on a 370 bench (its floor is out of frame). Those machines' pulleys sit higher than the D124's reads |
| Tower footprint | 686 wide, about 610 deep (L 0 to 610); depth rough | width published; depth estimated from D124_2, scaled by the vanishing point above |
| Stack at rest | bottom about 120, top about 570, about 450 tall | estimated from D124_1 (y 868 to 1072 at 2.2 mm/px) and D124_2 (y 760 to 950 at 0.41 px/mm), scaled by the 2413 mm height |
| Stack travel | room for about 1500; low confidence | chosen from the height: from the 570 stack top to below the tower's top frame. The page says only "added weight stack travel" |
| Lower beam | about 55 tall, its underside about 100 up, from the plates' frame to the rear post | estimated from D124_2 (25 px, and 32 to 48 px above the foot's underside), scaled by the 2413 mm height |
| Upper rail | about 60 tall, under the pad, running from the rear post to the plates' frame | estimated from D124_2 (53 px of pad and rail less the pad), same scale |

## Motion
- Nothing but the cable system moves. There is no seat or plate adjustment in any source.
- Cable and handle: one free end, leaving the tower at about 850 high, running forward along the bench's centre line above the deck and between the lifter's legs.
- Stack: prismatic, vertical, one stack. It rests low in the tower (table above). The ratio is taken as 1:1: Atlantis doesn't publish it, but a 2.4 m tower only makes sense with a stack moving the handle's full distance, and Matrix's near twin is listed at 1:1 and Hammer Strength's row station at 1:1. Low confidence.
- Handle at rest: hangs on the cable at the tower's front, over the deck (D124_1). A stop on the cable holds it there, chosen.
- Hand travel by lift, so stack travel at 1:1 (lifter 1.75 m; hand positions from the frames):
  - `seated_cable_row`: about 560, from over the shins to the lower ribs (5:00 and 5:02).
  - `wide_grip_seated_cable_row`: at least 310 between the frames at 9:43 and 9:46, which don't show a whole rep. Chosen 450, low confidence.
  - `seated_cable_kelso_shrug`: about 300, rough (0:12 and 0:13).
  - `seated_cable_deadlift`: about 830 (0:09 and 0:19), the longest of the four, well inside the 1500.
- Body by lift, angles from vertical:

| Lift | Feet | Knee flexion | Trunk | Hands and attachment |
|---|---|---|---|---|
| `seated_cable_row` | on the plates | about 50 at the reach, about 20 at the finish (5:00, 5:02) | about 30 forward at the reach to near upright, about 10 forward, at the finish | V handle, palms in, arms long at the reach; elbows behind the trunk at the finish, hands at the lower ribs, about 520 above the seat |
| `wide_grip_seated_cable_row` | on the plates | about 25, little change (9:43, 9:46) | upright, no swing (9:43, 9:46) | long bar overhand, hands level at about 900 high, drawn to the lower chest, elbows out and back |
| `seated_cable_kelso_shrug` | on the plates | about 30 | upright, within about 5 (0:11 to 0:13); 0:05 shows about 20 back, in the set-up | long bar, hands about 550 apart, about 830 high, in front of the lower chest, arms long, about 160 side-on (0:08 to 0:13); front-on frames foreshorten them and look bent |
| `seated_cable_deadlift` | on the plates; one foot on the floor at 0:01 is the set-up | about 60 with the trunk forward, about 15 rocked back (0:05, 0:09) | about 40 forward (0:19) to about 65 back (0:09), through upright (0:12, 0:16) | long bar, arms long throughout, hands about 550 apart, reaching past the shins, then ending at the hips |

Hips aren't fixed on the pad. In the deadlift video they sit at 570 from the plate at 0:05 and 830 at 0:19, and the row's shift about 120 between 5:00 and 5:02. How much is the pelvis rolling and how much a real slide isn't settled.

## Handles
Atlantis says only that accessories are interchangeable, clip to a carabiner that needs a tool to remove, and doesn't list what ships.

| Lift | Attachment | Size | Source |
|---|---|---|---|
| `seated_cable_row` | close V handle, two neutral grips | about 300 long, 150 to 200 across, 25 grip diameter | published: BLK BOX Angled V-Grip 300 x 200, 25 mm grips, https://www.blkboxfitness.com/da/products/blk-box-angled-v-grip-handle-cable-attachment. Rogue's Rotating V-Grip is 366 x 147, https://www.roguefitness.com/it/en/rogue-rotating-v-grip-cable-attachment |
| `wide_grip_seated_cable_row` | long bar, overhand; hands about 700 apart | 1219 long, 28.5 diameter, bent ends | bar published: Rogue Lat Bar, https://www.roguefitness.com/ca/rogue-lat-bar. Hand spacing chosen from the library's "wide", low confidence, since the 9:38 back view hides the hands |
| `seated_cable_kelso_shrug` | long bar, hands about 550 apart | same bar | hands estimated from the video at 0:18, about 1.2 times shoulder width, scaled by a 460 mm shoulder span |
| `seated_cable_deadlift` | long bar, hands about 550 apart | same bar | hands estimated from the video at 0:25, same scale |

The front-on frames read the bar at about 950 across, short of the Rogue's 1219. The ends bend away from the camera, so that reading is low. The existing `lat_bar` in `parts.mjs` is 1200 across, so it fits. Its `v_handle` has one cross grip 140 long, where the row wants two neutral grips.

## Structure and supports
Atlantis publishes no tube sizes. Dealer text puts oval tubing on one other Precision machine, and Atlantis's racks use 76 x 76 at 11 gauge, but neither is this machine. The layout below is read from the five images. The tube sizes are chosen, low confidence, except where a table row above gives a reading.

- Tower: an enclosed box on feet at its corners, the gap between them open under the stack. Two feet show in each of D124_3 and D124_4; four in all is chosen. Corner posts chosen 76 x 76, joined at the top by a cap plate and at the bottom by a base frame. Shrouds close the rear and both sides (D124_3); the front stays open on the stack and its guide rods (D124_1). Each post lands on the base frame, and the frame on the feet.
- Base beam: a low beam leaves the tower's front at about 100 up and runs to the plates' frame (D124_2 at x 340 to 560). It is welded square to the base frame.
- Plates' frame: a pair of angled plate carriers under the plates, closed under the deck, welded to the base beam below and the deck above. It is the machine's only joint between the tower and the bench, so it carries the bench's whole load.
- Deck: a plate closing the top from the plates' top edge to the tower's front, welded at both ends, the rubber on top.
- Bench: two parallel beams, the upper rail under the pad and the lower beam about 150 below it, each running from the plates' frame to the rear post. Chosen 60 x 60 upper, 55 x 100 lower. Both butt into the frame at one end and the post at the other, so neither shows an open end.
- Rear post: one post, about 75 across, from above the upper rail down through the lower beam to a floor foot. It is the bench's only floor contact.
- Rear foot: about 170 across in the images (D124_1, D124_2), a small pad on the floor. On its own that wouldn't stop a bench tipping sideways, so a build may widen it to the 300 pad width. Chosen.
- Pad: sits on the upper rail, its underside covered by a plate, and hangs 215 past the post at the bench end.
- Open ends: every tube above ends in a weld to another tube or a plate. Where the bench's beams stop at the post, the post runs past them and takes a cap. The pad's overhang has no tube inside it past the rail, and the rail's end is capped.
- Floor: the tower's feet and the rear foot are the only contact points. The base beam and the lower beam stand clear of it.

## Adjustments and fit rules
No placard text found. Nothing on the machine adjusts.

- The lifter sits so the knees stay a little bent with the arms long at the reach. The hips land 570 to 830 from the plate at 1.75 m tall. A 1000 pad from the bench end stops about 115 short of the plate's bottom edge, which covers that. The kit solves the hips' place from the mannequin's leg.
- Feet: mid-plate, one on each, the cable passing between the shins. The plates run from 80 to 400 high, so the sole lies about 240 high on the D124, below the seat. In the videos the sole sits at about 480, above their seat: their plates are higher. The kit takes the D124's.
- Cable taut at every lift's start: the reach already has the hands well short of the plate, so the stack is already a little off its rest there.
- Attachment by lift as in the Handles table. Same cable, no pulley change.

## Open gaps
- Built differently from the D124, on purpose: the pad is 220 wide, not the chosen 300, so the shins pass beside it with the plates this low; the tower is an open frame, not shrouded, so the stack shows moving; its wheels are the library's shared 90 mm ones, not the published 152.
- The cable exit is built at 850, within the 820 to 890 the videos' cables run at, because the lifts are judged against the videos (coordinator, #127). The D124's own reads about 700, from where the handle hangs in one image, a low-confidence estimate. The pulley itself isn't visible. A wheel at foot height, as the brief for this file has it, would sit near 250. Both videos' cables run far higher, at about 820 to 890, near level with the hands. No source I reached shows a pulley at foot height on a machine of this kind, so the file keeps 700 for the owner's eye. The cable's angle to the hands follows from whichever height he picks.
- The D124 images were read as sampled grids, not looked at by eye, and none is side-on. Lengths along the machine rest on one vanishing point. Heights rest on the published height, at about +-40 mm.
- No technique video shows a D124. The foot plates in the videos sit higher than the D124's and their benches differ (about 350 and 430). Sizes follow the images. Body angles follow the videos.
- Bench pad length and width stay chosen: the images don't separate pad from rail, and no view from above exists.
- Stack travel (about 1500), the plate count and step, and the 1:1 ratio stay chosen. Atlantis publishes none of them.
- The cable's route inside the tower is hidden, and so is the number of wheels. A build needs one exit wheel and top wheels over the stack, chosen.
- All tube sizes, the two bench beams' joints and the rear foot's width are chosen from the images' outlines. No source gives them.
- How far the hips slide on the pad in each lift isn't settled.
- The wide row's hand spacing and whole hand travel are chosen. Its frames show neither.
- The three published heights (2413, 2430, 2438) disagree, and no page says which is right. The kit uses Atlantis's own page.
- Overall width: the page's 686 is taken as the tower's, and no drawing confirms that.
