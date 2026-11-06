# Shrug machine (`shrug_machine`)

## Movement and exercises served
Standing (or seated on a high seat) between two levers, a handle at each side; the levers lift as the shoulders elevate, with the arms straight. Serves `machine_shrug`.

**Serves `machine_shrug` only. `kelso_shrug` is not this machine.** The library's `kelso_shrug` ("Machine Kelso Shrug", video https://youtu.be/76rz0UNAlYI) is done chest-supported on the T-bar row machine (confirmed by the owner), so #161 moves it to `t_bar_row_machine` (see [its ref](../t_bar_row_machine/ref.md)). That leaves this machine at one exercise.

## Based on
- Brand/model: Hammer Strength Plate-Loaded Seated/Standing Shrug (PL-SH).
- Product pages: https://lifefitness.com/en-us/catalog/strength-training/plate-loaded/plate-loaded-seated-standing-shrug , https://www.lifefitness.com.au/commercial/seatedstanding-shrug-plsh
- Spec sheet / manual: none found (the pages link only a general brochures page).
- Photos viewed (all on `https://shop.lifefitness.com/cdn/shop/files/Hammer_Strength_-_Plate_Loaded_-_Seated_Standing_Shrug_` + the name):
  - Side-on, empty: `PL-SH_27__mr.jpg`
  - Side-on, standing user: `PL-SH_29__mr_1200x1200.jpg`
  - Side-on, seated user: `PL-SH_1__mr_1200x1200.jpg`
  - Front, standing user at the top of a shrug: `PL-SH_17__mr.jpg`
  - Front and three-quarter, empty and loaded: `PL-SH_11__mr.jpg`, `PL-SH_12__mr.jpg`, `PL-SH_28__mr.jpg`; seated front `PL-SH_15__mr.jpg`; close-ups `PL-SH_20__mr.jpg`, `PL-SH_25__mr.jpg`
  - Life Fitness AU gallery: https://a.storyblok.com/f/292045577297161/1920x1280/19774bec40/strength-plateloaded-seatedstandingshrug-4.webp (front, standing), `.../22f2eff25f/...-2.webp`, `.../49c75679f5/...-1.webp`, `.../f18ba789cd/...-5.webp`, `.../2bf6338957/...-6.webp` (seat close-up), render https://a.storyblok.com/f/292045577297161/1920x1920/82ea28b53f/pl-sh-midblk-seated-standing-shrug.png
  - Remanufactured unit render: https://www.fitnesssuperstore.com/cdn/shop/files/HSISOshrug-main.webp
- Video viewed: `machine_shrug` https://youtu.be/2KDc6iAcrAw, side-on 0:00–0:08, front 0:08–0:14 (frames at 0:02, 0:02.5, 0:02.75 top, 0:03.5 bottom, and a 4 fps strip 0:01.5–0:07.5). It shows a different make: a long plate-loaded lever whose pivot is in front of the lifter, not behind as on the PL-SH.
- Chosen: preferred brand, and the only shrug spec found with dimensions.

## Key dimensions (mm)
| Dimension | mm | Source |
|---|---|---|
| Footprint | 1020 front to back x 1520 across (horn tip to horn tip) | Life Fitness (40" x 60"); which side is which settled by `PL-SH_27`, where the side-on length from handle tip to rear foot is about 1010, scaled by the 1240 mm height |
| Height | 1240 (top of the back pad) | same (49") |
| Starting resistance | 11 kg per arm | same |
| Max plates | 5 x 20 kg per rod, 2 horns | same pages and search summary |
| Lever pivot | transverse, at the top of the A-frames, about 600 above the floor | estimated from `PL-SH_27`, scaled by the 1240 mm height (2.44 mm/px) |
| Lever reach, pivot to grip centre | 760 forward, grip 100 above the pivot at rest | same photo and scale |
| Grip height at rest (lever on its stop) | 700 | same photo and scale |
| Grip height at the top of a shrug | 940 | estimated from `PL-SH_17` (front), scaled by the lifter, taken as a person of about 1.78 m; matches the mannequin's 0.93 below |
| Grip height, mannequin, bottom / top | 805 / 866 in the kit (820 / 930 first chosen) | chosen from the fit rule (arms hanging straight from a 1.45 m shoulder, 0.63 m to the grip; 60 mm of shoulder rise) for the mannequin, low confidence |
| Grip spread | 740 between grip centres | estimated from `PL-SH_17`, scaled by the 45 lb plate (450 mm), rough; the hands sit well outside the hips |
| Handle | a hoop and a strut rising about 270 from each lever, the grip pointing forward | estimated from `PL-SH_27`, scaled by the 1240 mm height |
| Plate horn | on each lever, near its front end | `PL-SH_27`, `PL-SH_29`; the two horns on the A-frames are storage by their place on the fixed frame |
| Seat top above floor | 650 | estimated from `PL-SH_27` (empty, about 660) and `PL-SH_1` (seated, about 620 to 640), scaled by the 1240 mm height and the 45 lb plate |
| Seat height range | adjustable, range not visible; 650 by default, 450 for the standing lift | the seat post shows numbered holes (`strength-...-4`, `PL-SH_25`), their spacing not visible; 650 kept at the measured seat, since no picture shows the travel; low confidence |

## Motion
- Two levers, one per side, each a revolute about one transverse axis at the top of the A-frames, behind the back pad. The grips swing on a 760 mm radius, close to vertical over the shrug's small angle.
- Range of motion: 60 mm at the grip for the mannequin, about 5 degrees of lever (from 15.5 to 20.3 degrees above the pivot's level), as far as its shoulders rise, so the arms stay long; the first choice of 110 mm, from the video's 120 mm, folded the elbows. The video's make: the grip rises about 40 px, about 120 mm, from video https://youtu.be/2KDc6iAcrAw at 0:03.5 (bottom) and 0:02.75 (top), scaled by the lifter, taken as a person of about 1.78 m; about 5 degrees on its longer lever. The stop sits lower than the hanging hands, so the levers travel about 8 degrees more from rest to the start of the rep (chosen from the fit rule, low confidence).
- Plate-loaded on the lever horns.

## Adjustments and fit rules
- Only the seat adjusts; the handles are fixed (page FAQ). Can be used standing.
- Fit rule, checked against the video and `PL-SH_17`: stand upright between the levers, arms straight at the sides, the grips beside the front of the thighs at about the hip line; shrug straight up with no elbow bend and no roll. At the top the shoulders are raised and the arms still straight (video 0:02.75, `PL-SH_17`). The PL-SH grips are wider than the hips (about 740 apart), so the arms hang slightly out from the body in the front view.
- Seated: the photo `PL-SH_1` shows the lifter on the seat with the elbows bent about 110 degrees, which is not the working position, so it is not used for the fit. For the mannequin, seated use needs the shoulders about 0.63 m above the grip, which puts the seat near its top setting.

## Measured part list (#241)
Where each main part sits, from `PL-SH_27` (side, scaled by the 1240 mm height at 2.44 mm a pixel), `PL-SH_12`, `PL-SH_17` and `PL-SH_29`. Depths are from the grips' middle on the stop, ahead positive. Against the lifter: the feet stand between the rails' front ends ahead of the first cross beam, the legs either side of the lowered seat, the hands a little ahead of the legs.

| Part | Where it sits, estimated from the photos | Was in the kit | Now |
|---|---|---|---|
| Base | a rectangle of tube, underside 38 up on a foot at each corner, rails from about -10 back to -910, two cross beams | floor rails on the floor, 220 ahead of the grips | as measured |
| Column | raked back about 8.8 degrees from a cross beam at about -310 up to 1090, the pad on its front | a square gantry, a post and a beam | as measured, cut flat on the cross beam |
| Back pad | parallel to the column, 560 long, its top at 1240 | upright, 500 long | as measured, flush on the column |
| Brace | from the column at about 980 down and back to a cross tube at the pivot | none | as measured |
| A-frames | two legs each side from the rails up to the pivot, a storage horn on each | one post each side | one mitred length each side, its tip ending in the pivot's block |
| Stop | a block under each lever at rest, on the frame | none, the lever on nothing | a block on the A-frame's front leg, the beam lying on it at 7.5 degrees |
| Levers | a heavy beam about 100 tall and 50 wide, falling about 13 degrees toward the front on the stop, 630 long | a thin tube rising forward | as measured |
| Handles | a hoop and a flat strut up from the beam to the grip, a second grip running on from the beam's end | a round post, an elbow | hoop, strut and both grips |
| Plate horn | sideways out of the beam near its front end | under the post | as measured, 585 along the beam |
| Seat | on a sleeve on the column, narrow, about 300 deep; `PL-SH_17` shows it at about 550, between the legs | on a post on the floor | on a sleeve, 240 wide and 260 deep, at 450 for the standing lift, so the legs clear it |

The kit's depths are these plus 100 mm, as the rig stands the lifter at the origin: the grips rest at z 100. The photos' strut is a flat bar; the kit's is square.

## Open gaps
- The seat's range is not visible (the numbered holes' spacing can't be read), so the recipe defaults to the measured 650 mm, and the standing lift's 450 mm is chosen to clear the legs.
- No photo shows the bottom of a shrug on the PL-SH, so the start height and the 5 degree range are chosen from the mannequin's arm and how far its shoulders rise.
- The lever's stop angle comes from one empty photo.
