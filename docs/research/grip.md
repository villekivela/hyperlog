# How each grip meets its bar: research notes

Researched 2026-11-03 for Hyperlog's mannequin studio (`tools/mannequins/`). Issue #162 builds a grip solver. Each grip names where the bar sits in the hand, what the thumb does and how thick the bar is, and the fingers curl until they touch it. This note backs those defaults with sources (issue #169). Its tables in section 5 are the solver's defaults.

**Labels used below**

- **[Documented]**: stated in the cited source.
- **[Secondary]**: from a third-party copy or summary (a retailer's copy of a maker's spec, a coaching blog, a study guide, a forum). Plausible, but not checked against the source that owns it.
- **[Inferred]**: my own reasoning or a design choice. No source states it.

**Reading status.** Each bullet ends with "(read)" when I opened the page or PDF and read the passage, or "(search snippet only)" when I saw the claim only in a search result. Startingstrength.com, the NSCA's book pages and several Rogue support guides refused automated access, so some coaching claims rest on snippets. Section 7 lists every source by status.

**Hand landmarks.** The note uses four, from the wrist out:

- **Palm heel**: low in the palm, between the thenar and hypothenar pads, just past the distal wrist crease, so the bar sits over the forearm bones.
- **Mid-palm**: over the middle of the metacarpals, between the heel and the palmar creases.
- **Finger base**: at the metacarpophalangeal (MCP) joints, from the distal palmar crease to the creases at the roots of the fingers.
- **Mid-fingers**: across the middle phalanges, hook-like.

---

## 1. The hand: creases, size and the best handle size

### 1.1 Where the creases lie over the bones
- [Documented] Bugbee & Botte 1993 (Clin Orthop Relat Res; radiographs of 53 marked hands): the proximal digital creases, at the roots of the fingers, lie 14–20 mm distal to the MCP joints. The distal transverse palmar crease lies 6.8–10.3 mm from its MCP joint, and the proximal palmar crease 9.1–22.1 mm from its joint. (read, abstract) https://pubmed.ncbi.nlm.nih.gov/8222413/
- [Documented] Same abstract: the distal wrist crease lies 0.7 mm from the scaphoid and 3.4 mm from the pisiform, the two bones under the heel of the palm. (read, abstract) https://pubmed.ncbi.nlm.nih.gov/8222413/
- [Inferred] The band from the distal palmar crease to the finger-root creases is about 21–30 mm wide, roughly one 28 mm barbell. A bar whose axis lies over the MCP heads touches both creases. That is the "finger base" landmark.

### 1.2 Hand size and grip
- [Documented] Seo & Armstrong 2008 (Hum Factors): mean hand length, fingertip to first wrist crease, was 191 ± 14 mm for men and 171 ± 8 mm for women. The ratio of handle diameter to hand length explained 62% of the variance in grip force and 71% of contact area. (read, full text on PMC) https://pmc.ncbi.nlm.nih.gov/articles/PMC9089462/
- [Documented] Same paper: the mean inner grip breadth, the circle made by touching the thumb tip to the middle fingertip, is 49 mm (Garrett 1971, air force data). From it they estimate a mean optimal power-grip diameter of 40 mm, close to the 38 mm optimum Ayoub & Lo Presti 1971 and Edgren et al. 2004 reported. Past the optimum the fingertips and thumb tip end up opposed and work against each other. (read) https://pmc.ncbi.nlm.nih.gov/articles/PMC9089462/
- [Documented] Kong & Lowe 2005 (Int J Ind Ergon; 24 participants, handles 25–50 mm): the 30, 35 and 40 mm handles were rated the most comfortable for maximum grip. The most comfortable diameter was 19.7% of hand length. Total finger force fell as diameter rose. (read, NIOSH abstract) https://stacks.cdc.gov/view/cdc/197408 , DOI https://doi.org/10.1016/j.ergon.2004.11.003
- [Inferred] At 19.7% of 191 mm and 171 mm, the optimum is about 38 mm for men and 34 mm for women. Every barbell in section 4 (25–29 mm) is thinner than that. On a bar thinner than the 49 mm inner grip breadth the fingertips reach past the thumb tip, so in a closed grip the thumb lies across the index and middle fingers, not on the bar. The solver's contact check should let the thumb touch the fingers on thin bars.

### 1.3 Wrist position and grip strength
- [Documented] O'Driscoll et al. 1992 (J Hand Surg Am; 20 healthy subjects): the self-selected wrist position for maximum grip was 35° extension and 7° ulnar deviation. Deviating the wrist 10–15° in any direction lowered grip strength. At 15° extension, strength fell to two-thirds to three-quarters of normal. At least 25° extension was needed for full grip strength. (read, abstract) https://pubmed.ncbi.nlm.nih.gov/1538102/
- [Inferred] That optimum is for squeezing a dynamometer, not for holding a load. A hanging barbell pulls the wrist straight, and pressing coaches want it close to straight (section 2). The tables use straight or a little extension, not 35°.

## 2. Where the bar sits, per kind of lift

### 2.1 Presses: bench, overhead, dumbbell and machine presses, dips
- [Secondary] Rippetoe, Starting Strength, via an unofficial copy of the press chapter: out of the rack "the bar will be on the heel of your palm and directly over your forearm bones". The bar sits near the heel, not in the fingers. (search snippet only) https://www.scribd.com/doc/208714042/Learning-Press-Rippetoe
- [Secondary] Rippetoe's article "Gripping Matters" on startingstrength.com is the source coaches cite for the bench grip: rotate the hands so the bar sits on the heel of the palm, because a closed grip taken carelessly pushes the bar up the palm. I could not open it (403). A Barbell Medicine forum post describes it. (search snippet only) https://startingstrength.com/article/gripping_matters , https://forum.barbellmedicine.com/t/bench-press-grip-question/557
- [Secondary] Testify Strength & Conditioning, a Starting Strength affiliate gym (Meggers, 2023), on bench and press: "get the bar sitting over the base of the palms", not the base of the fingers. Pronate the hand "just enough that your thumbs can grip the bar". The wrist avoids "extreme extension" and "won't be entirely straight". (read) https://decagon-koala-6f68.squarespace.com/articles/use-this-grip-for-a-bigger-bench-press-and-press
- [Secondary] An exercise site summarising Starting Strength's press video: hold the bar "along pad of hand, not straight across", wrists "neutral (slightly extended)". (read) https://exercises.kemitchell.com/sources/Starting-Strength-how-to-barbell-press
- [Documented] NSCA, Exercise Technique Manual for Resistance Training, 3rd ed. (Kinetic Select excerpt, push jerk): with the bar at the shoulders, the grip is "even, pronated, closed, and slightly wider than shoulder-width". (read) https://www.nsca.com/education/articles/kinetic-select/push-jerk
- [Inferred] Dumbbell presses, machine presses and dips load the hand the same way, along the forearm into the palm, so they take the same landmark: palm heel, wrist straight to about 10° extension. No source I read describes the dumbbell or machine press grip separately.
- [Inferred] No peer-reviewed study I found measures wrist angle in the bench press. The 5–10° figures in the tables are a reading of "neutral, slightly extended", not a measurement.

### 2.2 Squats
- [Documented] IPF Technical Rulebook 2026, squat rule 1: the hands, thumbs and fingers must stay in complete contact with the bar, but "the thumbs are not required to wrap around the bar". (read) https://www.irishpowerliftingfederation.com/wp-content/uploads/2026/03/2026-IPF-Technical-Rulebook-effective-01-March-2026-v3.pdf
- [Secondary] Starting Strength teaches the low-bar squat with the thumbs over the bar and the wrist straight, in line with the forearm. Deaton, "The Squat: Thumbs-Around Grip" (2018), calls wrist overextension and flexion "very common errors" and offers thumbs-around only as a fallback for lifters who cannot get there. (search snippet only) https://startingstrength.com/training/the-squat-thumbs-around-grip
- [Secondary] Coaches agree the hands pin the bar to the back and do not carry it. One forum reply: "the load is placed on the back, not in the hands". Coaches disagree on thumbs: some use thumbless for a straight wrist, others thumbs-around for lifters with stiff shoulders or long forearms. (search snippet only) https://www.artofmanliness.com/strength/fitness/troubleshooting-the-low-bar-squat/
- [Documented] NSCA Exercise Technique Manual, power clean: the catch ends "with the bar on the anterior deltoids and clavicles, similar to the arm and bar position of the front squat", with "wrists hyperextended, elbows fully flexed, upper arms parallel to the floor". (read) https://www.nsca.com/education/articles/kinetic-select/power-clean
- [Secondary] Crossed-arm front squat: the arms cross in front of the chest and the bar rests on the front delts, the hands on top of the bar. Coaches offer it to lifters short of wrist or shoulder mobility. It feels less stable as loads rise. (search snippet only) https://torokhtiy.com/blogs/guides/front-squat-grip
- [Inferred] The library poses `front_squat` crossed-arm (`grip.crossed`), with open hands lying over the bar. The bar rides on the delts, so the hand only rests on it: palm and finger pads on top, no closing, thumb free.

### 2.3 Deadlifts, RDLs, shrugs and rows
- [Documented] Feigenbaum, "The Grip Problem" (Barbell Medicine, updated 2025; written with Rippetoe's editing): a bar "will be held most securely at the level of the palmar digital crease at the junction of the palm and the fingers". Holding it "deeper/higher in the palm will result in the thing sliding down to this location under the tension of the pull", which can tear a callus. The same holds for a pull-up bar. (read) https://www.barbellmedicine.com/blog/the-grip-problem/
- [Secondary] Rippetoe, "The correct place for the bar in your hands in the deadlift" (startingstrength.com): a bar set high on the palm slides down into the fingers under load, in either the alternate or the hook grip. (search snippet only) https://startingstrength.com/training/from-the-archives-the-correct-place-for-the-bar-in-your-hands-in-the-deadlift
- [Documented] IPF Technical Rulebook 2026, deadlift rule 1: the bar is "gripped with an optional grip in both hands". (read) https://www.irishpowerliftingfederation.com/wp-content/uploads/2026/03/2026-IPF-Technical-Rulebook-effective-01-March-2026-v3.pdf
- [Inferred] Rows, shrugs and the barbell RDL hang the same load from the same fingers, so they take the deadlift's landmark (finger base) and a straight wrist. Under a heavy hanging load the wrist cannot hold the 35° extension of a free squeeze (section 1.3).

### 2.4 Pulldowns and pull-ups
- [Documented] Feigenbaum (Barbell Medicine): chin-ups use the power grip, and a pull-up bar held higher in the palm slides down to the palmar digital crease, as a barbell does. (read) https://www.barbellmedicine.com/blog/the-grip-problem/
- [Inferred] A lat pulldown loads the hand as a pull-up does, so it takes the finger base, wrist straight. Most lifters wrap the hand fully. A hook-like hang in the mid-fingers appears only as a lifter fatigues, and the library does not pose it.

### 2.5 Curls
- [Inferred] No source I read places the bar in the hand for curls. Through most of a curl the forearm is near horizontal, palm up, and the load presses into the palm, so the default is mid-palm with a straight wrist. At the bottom, arm hanging, the load pulls toward the fingers as in a deadlift, so mid-palm is a compromise between the two.
- [Documented] Rogue describes its curl bar as "Cambered for reducing stress on wrists during curls". It gives no camber angle. (read) https://www.roguefitness.com/ie/rogue-curl-bar-eu
- [Inferred] On an EZ bar the forearm sits part way from underhand toward neutral, so `grip.rotate` is about 150–165°, not 180°. That value is my estimate from typical cambers, not a spec.

### 2.6 Triceps pressdowns and extensions
- [Inferred] A pressdown pushes the bar down against a cable pulling it up into the palm. At the bottom, forearm vertical, the load runs along the forearm into the palm, as in a press. At the top, forearm near horizontal, the cable pulls the bar out toward the fingers. So the bar sits mid-palm, not on the heel, and the wrist stays straight. No source I read says this.
- [Inferred] A rope is held with the hands neutral (`rotate` 90), each fist round the rope just above its end stop, the stop against the little-finger side of the hand. The rope sits mid-palm in a full fist.

### 2.7 Neutral handles, cable stirrups, machine levers and leg-machine handles
- [Inferred] A stirrup, a V-handle or a machine's lever handle is a short cylinder pulled or pushed along the forearm. Pulled (rows, raises, flyes, curls on a stirrup), it sits at the finger base as a barbell does in a row. Pushed (cable or machine presses), it sits at the palm heel.
- [Inferred] Seat and shoulder-pad handles on leg machines (leg press, leg extension, leg curls, hack squat, seated calf raise) only steady the body. The hand wraps them at the finger base with a relaxed, straight wrist. No source describes these holds.

### 2.8 Upright rows and raises
- [Inferred] An upright row is a pull, so the bar starts at the finger base. As the elbows rise above the hands the forearms slope down to the bar and the wrists flex, about 30° at the top by my estimate. No source I read gives an angle.
- [Inferred] A lateral raise holds a dumbbell or stirrup at the finger base with a straight wrist. The load pulls straight down the fingers at every point of the rep.

## 3. The thumb

### 3.1 Definitions
- [Secondary] NSCA terms, from a summary of the Essentials of Strength Training and Conditioning chapter on technique: closed grip, "the thumb wraps over the bar"; false grip, "the thumb doesn't wrap over the bar"; hook grip, "like pronated, but the thumb stays under the index and middle finger". (read) https://www.ptpioneer.com/personal-training/certifications/nsca-cscs/cscs-chapter-15/
- [Documented] NSCA Exercise Technique Manual, power snatch: "The actual grip can be a closed grip or a hook grip. To use a hook grip, place a pronated hand on the bar and first wrap the thumb around the bar, then wrap the four fingers. The first one or two fingers, depending on their length, will cover the thumb." It suggests taping the thumbs. (read) https://www.nsca.com/education/articles/kinetic-select/power-snatch
- [Documented] IWF Technical and Competition Rules & Regulations 2026, rule 2.4.1: "hooking" is permitted, "covering the thumb with the other fingers of the same hand at the moment of gripping the barbell". (read) https://iwf.sport/wp-content/plugins/download-monitor/download.php?id=598
- [Documented] Feigenbaum (Barbell Medicine): in a double overhand grip "the thumbs are laid over the top of the encircling fingers". The hook "is made between the thumb, index, and middle finger", and the thumbnail pressed under the fingers adds friction. (read) https://www.barbellmedicine.com/blog/the-grip-problem/

### 3.2 Defaults and where coaches split
- [Documented] Bench: IPF Technical Rulebook 2026, bench rule 2: hands, thumbs and fingers "must completely grip the bar ... with a thumb around grip", held throughout the lift. Rule 5 forbids the reverse grip. (read) https://www.irishpowerliftingfederation.com/wp-content/uploads/2026/03/2026-IPF-Technical-Rulebook-effective-01-March-2026-v3.pdf
- [Secondary] Thumbless ("suicide") bench: lifters who use it say it eases the wrist. Most coaching pages call it unsafe because nothing stops the bar rolling off the palm heel. I found no Rippetoe or NSCA statement and no study on it. (search snippet only) https://liftvault.com/resources/suicide-grip/
- [Documented] Squat: the IPF lets the thumbs off the bar (2.2 above). [Secondary] Starting Strength teaches thumbs over the bar for the low bar. (read; search snippet only) https://startingstrength.com/training/the-squat-thumbs-around-grip
- [Secondary] Deadlift hook grip: Rippetoe has a Starting Strength article on it ("The Hook Grip", 2019). The IWF permits it and the NSCA teaches it for maximal pulls. Most gym lifters use a closed double overhand, an alternate grip or straps. (search snippet only) https://startingstrength.com/training/the-hook-grip-breakdown
- [Secondary] Thumbless pulldown and pull-up: some bodybuilding pages claim a thumbless grip cuts biceps involvement. A gymnastics-minded gym argues the wrapped thumb gives a stronger grip and a more stable shoulder. None cites data. (search snippet only) https://www.muscleandstrength.com/node/49610 , https://crossfitroots.com/the-grip-talk/
- [Inferred] Default for the solver: wrapped (closed) everywhere, except `back_squat_low_bar` (thumbless) and `front_squat` crossed-arm (open hand). Hook grip and thumbless are entry overrides, not defaults.

## 4. Diameters

Diameters are of the gripped section. Knurling is cut into the steel, so it does not add diameter. [Inferred]

### 4.1 Barbells
- [Documented] IWF TCRR 2026, regulation to 3.3.3: men's bar 20 kg, grip section 2.8 cm (28 mm) diameter; women's bar 15 kg, grip section 2.5 cm (25 mm); both knurled, sleeves 5 cm. (read) https://iwf.sport/wp-content/plugins/download-monitor/download.php?id=598
- [Documented] IPF Technical Rulebook 2026, bar rule (a)3: "Diameter of the bar is not to exceed 29 mm or be less than 28 mm." (read) https://www.irishpowerliftingfederation.com/wp-content/uploads/2026/03/2026-IPF-Technical-Rulebook-effective-01-March-2026-v3.pdf
- [Documented] Eleiko IPF Powerlifting Competition Bar, product sheet: 29 mm diameter, aggressive knurl. (read) https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3061173_product-sheet-eleiko-ipf-powerlifting-competition-bar-20-kg.pdf
- [Documented] Rogue Ohio Power Bar 20 kg: "29MM Diameter". (read) https://www.roguecanada.ca/rogue-ohio-power-bar-20-kg
- [Documented] Rogue Ohio Bar, a multipurpose men's bar: diameter 28.5 mm, dual knurl marks, no centre knurl. (read) https://www.roguefitness.com/the-ohio-bar-black-zinc
- [Documented] Feigenbaum (Barbell Medicine) gives 27 mm for a whippy deadlift bar and 28 mm for an Olympic bar, and says a 28.5–29 mm bar is noticeably harder to hold for small hands. (read) https://www.barbellmedicine.com/blog/the-grip-problem/
- [Inferred] Default 28 mm for every barbell in the library. Powerlifting bars run to 29 mm, which only matters within the 2 mm check if an entry names one.

### 4.2 Specialty bars
- [Documented] Rogue Curl Bar (EZ): "Shaft: 28.5mm diameter", cambered, knurled. (read) https://www.roguefitness.com/ie/rogue-curl-bar-eu
- [Documented] The Eleiko Curl Bar sheet gives length, sleeve diameter (50 mm) and knurl, but no grip diameter. (read) https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3061174_product-sheet-eleiko-curl-bar-50-mm-12-kg.pdf
- [Documented] Rogue SB-1 safety squat bar: "1" diameter formed solid steel handles" (25.4 mm) on a 1.5 in (38.1 mm) shaft. The page does not say the handles are covered. (read) https://www.roguecanada.ca/sb-1-rogue-safety-squat-bar
- [Documented] Rogue TB-2 trap bar: two sets of knurled handles, 1.34 in (34.0 mm) diameter, 25 in (635 mm) apart on centre. (read) https://www.roguecanada.ca/rogue-tb-2-trap-bar
- [Documented] Eleiko Öppen deadlift bar, product sheet: handle diameter 28 mm (1.1 in), handles 625 mm apart, neutral handle position. (read) https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3085153-03_product-sheet-eleiko-oppen-deadlift-bar-black_2.pdf
- [Secondary] Smith machine bars vary: Bodycraft lists a 28 mm knurled bar, a Primal Pro Series 30 mm, and an unbranded commercial spec sheet 1.4 in (35 mm). (search snippet only) https://www.bodycraft.com/commercial-product/csm/ , https://gymequipment.co.uk/primal-pro-series-olympic-smith-machine

### 4.3 Dumbbells
- [Documented] Rogue rubber hex dumbbells: 25 mm handles up to 10 lb, 35 mm from 12.5 lb up, chrome. (read) https://www.roguefitness.com/ca/rogue-dumbbells
- [Documented] Eleiko Evo fixed dumbbells: chromed handles 30 mm under 32 kg and 32 mm above. (read) https://eleiko.com/equipment/dumbbells/fixed/3060990-0220-eleiko-evo-dumbbell-fixed-22-kg
- [Secondary] Ivanko pro-style dumbbells: "Fully knurled and chrome plated 30mm drop forged" handles (Iron Company's listing). (read) https://www.ironcompany.com/ivanko-pro-style-dumbbells-chrome-endcaps
- [Secondary] York pro-style dumbbells: "fully knurled 33mm solid steel handle". (search snippet only) https://northernfitness.ca/products/pro-style-dumbbells
- [Documented] PowerBlock Elite EXP, selectorized (Rogue's listing): "Grip Diameter: 1.265" / 33mm", a "straight cylindrical grip set inside a padded handle". 1.265 in is 32.1 mm, so Rogue's 33 mm is rounded up. (read) https://www.roguefitness.com/powerblock-series-elite-exp-dumbbells
- [Secondary] PowerBlock's own comparison lists 32 mm and 38 mm grips on its commercial models. (search snippet only) https://powerblock.com/en-gb/products/commercial-pro-100-adjustable-dumbbells
- [Secondary] Bowflex SelectTech 552: no maker figure found. One review lists 31.75 mm and also calls it 36 mm, with a rubber middle section. (search snippet only) https://garagegymlab.com/bowflex-selectech-552-review/
- [Inferred] Range 30–35 mm, typical 32 mm. The kit's 32 mm dumbbell handle fits.

### 4.4 Cable attachments
- [Documented] Rogue Single Handle Cable Attachment (stirrup): 5 in handle, 25 mm knurled steel as standard, or 28.5, 32 or 45 mm aluminium. The handle spins on a shoulder bolt. Strap is 1.5 in (38 mm) nylon webbing. (read) https://www.roguecanada.ca/single-handle-cable-attachment
- [Documented] Rogue Lat Bar: 48 in wide, 1.125 in (28.6 mm) diameter, fully knurled. The page says nothing about rubber grips. (read) https://www.roguecanada.ca/rogue-lat-bar
- [Documented] Rogue Stainless Straight Lat Bar, 20 in or 40 in: 28.5 mm, fully knurled. (read) https://www.roguecanada.ca/rogue-stainless-straight-lat-bar
- [Secondary] REP Fitness 48 in lat bars: 26 mm. (search snippet only) https://repfitness.com/products/pro-series-48-lat-bar
- [Documented] Rogue Grip Triangle (V-handle / close-grip handle): 7 in handles, 1.31 in (33.3 mm) diameter, 6 in apart, made of 1 in Schedule 40 knurled pipe. (read) https://www.roguecanada.ca/rogue-grip-triangle-standard-grip
- [Secondary] Rogue Tricep Push Down Attachment: 12 in handles of 1 in Schedule 40 pipe, 1.31 in (33 mm). (search snippet only) https://support.roguefitness.com/hc/en-us/articles/47332294564500-Product-Guide-Rogue-Tricep-Push-Down-Attachment
- [Documented] Rogue Tricep Rope: nylon rope, 28 in total "including rubber ends", about 11.5 in a side once doubled. No rope diameter given. (read) https://www.roguecanada.ca/tricep-rope-attachment
- [Secondary] Body-Solid triceps rope (retailer's copy of the spec): rope diameter 1.25 in (31.75 mm), end caps 2.5 in (63.5 mm), "oversized rubber end blocks". (read) https://www.sportsunlimitedinc.com/body-solid-triceps-rope.html
- [Secondary] Other ropes list 25 mm with oversized end blocks (Little Bloke Fitness, living.fit). (search snippet only) https://www.littleblokefitness.com.au/products/tricep-rope-attachment
- [Inferred] On a rope the hand closes on the rope (25–32 mm). The end stop (about 50–64 mm) does not enter the fist. It sits against the little-finger side of the hand and stops the hand sliding off.
- [Inferred] Rubber or foam sleeves on gym lat bars and stirrups add a few millimetres. No maker I found publishes the sleeved diameter.

### 4.5 Machine handles, dip handles and pull-up bars
- [Secondary] I found no published grip diameter from Hammer Strength, Life Fitness, Matrix, Technogym, Cybex or Precor. Life Fitness's pages for plate-loaded machines give overall sizes only. (search snippet only) https://lifefitness.com/en-us/catalog/strength-training/plate-loaded/hammer-strength-plate-loaded-t-bar-row
- [Secondary] Canada's occupational safety centre (CCOHS) recommends 30–50 mm for power-grip handles, 40 mm being best. (search snippet only) https://www.ccohs.ca/oshanswers/ergonomics/handtools/tooldesign.pdf
- [Documented] Rogue Velocidor dip attachment: handle diameter 1.9 in (48.3 mm), smooth or knurled. (read) https://www.roguecanada.ca/rogue-velocidor
- [Secondary] Rogue's older Matador dip handles: 1.875 in (47.6 mm). (search snippet only) https://tfusa.net/products/rogue-monster-lite-matador-dip-bar
- [Documented] Rogue Monster Crown pull-up bar: "1.25" diameter standard grip, 2" diameter fat grips" (31.75 and 50.8 mm). (read) https://www.roguecanada.ca/rogue-monster-crown-pull-up-bar
- [Secondary] Rogue's other pull-up bars (SPEAL, 70 in single, Monster Lite) list 1.25 in (31.75 mm), and its V2 bar's FAQ calls 1.25 in the standard for pull-ups. (search snippet only) https://www.roguefitness.com/ca/rogue-speal-bar-2-0
- [Inferred] Machine handles: 32–39 mm with a rubber or foam sleeve, typical 36 mm. A 1 in Schedule 40 pipe (33.4 mm), the tube Rogue uses for its handles, plus a thin sleeve lands there. The kit's 39 mm vinyl sleeve sits at the top of that range and near Kong & Lowe's optimum.

## 5. Defaults

`grip.rotate`: 0 overhand, 180 underhand, 90 neutral. Wrist angles are degrees, extension or flexion. In the rig's `P.wrist` flexion is positive, so "ext 10°" is −10. Diameters are what the hand closes on.

### 5.1 Posed motions

| Motion | Entries | Bar or handle | Diameter (mm) | Landmark | Thumb | Wrist |
|---|---|---|---|---|---|---|
| squat | back_squat_low_bar | barbell | 28 | palm heel | thumbless, over the bar | straight 0° |
| squat | back_squat_high_bar, barbell_squat | barbell | 28 | mid-palm | wrapped | straight 0° |
| squat | front_squat (crossed arms) | barbell, on the delts | 28 | open hand, palm and finger pads on top of the bar | open | straight 0° |
| hinge | deadlift, deficit_deadlift, barbell_rdl, barbell_shrug | barbell | 28 | finger base | wrapped; hook by override | straight 0° |
| bench_press | barbell_bench_press, close_grip_bench_press, floor_press | barbell | 28 | palm heel | wrapped (IPF rule) | ext 10° |
| pulldown | lat_pulldown, close_grip_lat_pulldown | `lat_bar` | 28 bare bar; 33 on its vinyl grips | finger base | wrapped | straight 0° |
| pulldown | 2_grip_lat_pulldown | `straight_bar` (kit default) | 28 | finger base | wrapped | straight 0° |
| pressdown | triceps_pressdown | `straight_bar` | 28 | mid-palm | wrapped | straight 0° |
| leg_press | leg_press | seat side handles | 36 (kit 39) | finger base | wrapped | straight 0° |
| chest_press | machine_chest_press | machine press handles | 36 (kit 39) | palm heel | wrapped | ext 5° |
| dumbbell_press | db_incline_press | dumbbell | 32 | palm heel | wrapped | ext 10° |
| arms | barbell_curl | barbell | 28 | mid-palm | wrapped | straight 0° |
| arms | bayesian_cable_curl | `handle` (stirrup) | 33 | mid-palm | wrapped | straight 0° |
| arms | machine_preacher_curl | machine curl handles | 36 (kit 39) | mid-palm | wrapped | straight 0° |
| arms | overhead_cable_triceps_extension | `rope`, fist above the end stop | 28 rope; end stop 60 | mid-palm | wrapped | straight 0° |
| row | barbell_row, deficit_pendlay_row | barbell | 28 | finger base | wrapped | straight 0° |
| free_arms | barbell_upright_row | barbell | 28 | finger base | wrapped | 0° at the bottom to flexion 30° at the top |
| free_arms | cable_flye, reverse_cable_crossover, cable_lateral_raise | `handles` / `handle` (stirrups) | 33 | finger base | wrapped | straight 0° |
| free_arms | cable_shoulder_press | `handles` (twin stirrups) | 33 | palm heel | wrapped | ext 10° |
| knee_lever | leg_extension, lying_leg_curl, seated_leg_curl | seat or front handles | 36 (kit 39) | finger base | wrapped | straight 0° |
| calf_raise | seated_calf_raise | grips on the knee-pad handle | 36 (kit 39) | finger base | wrapped | straight 0° |
| hack_squat | hack_squat | shoulder-pad handles | 36 (kit 39) | finger base | wrapped | straight 0° |
| lever_handles | chest_supported_machine_row (neutral), machine_pulldown | machine lever handles | 36 (kit 39) | finger base | wrapped | straight 0° |
| bridge | barbell_glute_bridge, barbell_hip_thrust | barbell, on the hips | 28 | mid-palm | wrapped | straight 0° |

### 5.2 Library equipment and patterns with no posed motion yet

| Equipment + pattern | Example entries | Bar or handle | Diameter (mm) | Landmark | Thumb | Wrist |
|---|---|---|---|---|---|---|
| bodyweight / weighted_bodyweight vertical_pull, hang, hanging core | chinup, 2_grip_pullup, weighted_pullup, dead_hang, hanging_leg_raise | pull-up bar | 31.75 (1.25 in) | finger base | wrapped | straight 0° |
| bodyweight vertical_pull, neutral | neutral_grip_pullup | parallel handles on the rig | 32 | finger base | wrapped | straight 0° |
| bodyweight / weighted_bodyweight vertical_push | dip, close_grip_dip, weighted_dip | dip handles | 32–48, typical 40 | palm heel | wrapped | straight 0° to ext 10° |
| barbell vertical_push, elbow_extension | overhead_press, push_press, z_press, jm_press | barbell | 28 | palm heel | wrapped | ext 10° |
| barbell lunge | barbell_lunge, split_squat_barbell | barbell on the back | 28 | as high-bar squat | wrapped | straight 0° |
| trap_bar hinge | trap_bar_deadlift | trap bar handles | 28–34, typical 32 | finger base | wrapped | straight 0°, rotate 90 |
| safety_bar squat, hinge | safety_bar_squat, good_morning_barbell_safety_bar | SSB handles | 25.4 (1 in) | finger base | wrapped | straight 0° |
| ez_bar elbow_flexion | ez_bar_curl, ez_bar_preacher_curl | EZ bar | 28.5 | mid-palm | wrapped | straight 0°, rotate about 160 |
| ez_bar elbow_extension | skull_crusher | EZ bar | 28.5 | palm heel | wrapped | ext 5° |
| dumbbell horizontal_pull, shrug, hinge, lunge | single_arm_db_row, db_shrug, db_romanian_deadlift, db_lunge | dumbbell | 32 | finger base | wrapped | straight 0° |
| dumbbell elbow_flexion | db_bicep_curl, db_hammer_curl, db_preacher_curl | dumbbell | 32 | mid-palm | wrapped | straight 0° |
| dumbbell horizontal_push, vertical_push | db_bench_press, seated_db_shoulder_press | dumbbell | 32 | palm heel | wrapped | ext 10° |
| dumbbell lateral_raise, rear_delt_fly | db_lateral_raise, bent_over_reverse_db_flye | dumbbell | 32 | finger base | wrapped | straight 0° |
| dumbbell chest_fly | db_flye | dumbbell | 32 | mid-palm | wrapped | straight 0°, rotate 90 |
| dumbbell two-hand overhead extension, goblet squat | overhead_db_triceps_extension, goblet_squat | dumbbell head, cupped | n/a | open palms under the inner plate | open | ext 20° |
| dumbbell / cable wrist | db_wrist_curl, cable_wrist_extension | dumbbell or `straight_bar` | 32 / 28 | finger base at the top, mid-fingers at the bottom of a wrist curl | wrapped | the rep's own range |
| smith_machine (all patterns) | smith_machine_bench_press, smith_machine_rdl | Smith bar | 28–35, typical 30 | as the barbell counterpart | as the barbell counterpart | as the barbell counterpart |
| cable horizontal_pull | seated_cable_row, wide_grip_seated_cable_row | `v_handle` / `lat_bar` | 33 / 28 | finger base | wrapped | straight 0° |
| cable rope work | cable_face_pull, cable_rope_hammer_curl, cable_crunch | `rope` | 28 | mid-palm, fist above the end stop | wrapped | straight 0° |
| cable elbow_flexion, bar | cable_ez_curl | cable EZ bar | 28 | mid-palm | wrapped | straight 0° |
| machine vertical_push, horizontal_push | machine_shoulder_press, incline_machine_press, seated_dip_machine | machine press handles | 36 | palm heel | wrapped | ext 5° |
| machine horizontal_pull, vertical_pull, shrug | machine_row, t_bar_row, machine_lat_pullover, kelso_shrug | machine handles | 36 | finger base | wrapped | straight 0° |
| machine chest_fly, rear_delt_fly | pec_deck, reverse_pec_deck | vertical machine handles | 36 | mid-palm | wrapped | straight 0°, rotate 90 |

Every row in 5.1 and 5.2 is [Inferred] from sections 2–4. The landmark and thumb defaults for barbell presses, back squats and deadlifts are [Documented] or [Secondary] in those sections. The rest follow the load direction.

## 6. Open questions

- [Inferred] **`pressdown` in `PALM_GRIPS`.** `motions.mjs` holds the pressdown's bar low in the palm. The cable pulls the bar into the palm only at the bottom of the rep, and out toward the fingers at the top (section 2.6). The table puts it mid-palm. Under a binary palm/fingers switch, mid-palm is closer to the fingers' roots than to the heel, so the pressdown should come out of `PALM_GRIPS` until a mid-palm depth exists.
- [Inferred] **`squat` in `PALM_GRIPS`.** Right for the low bar, thumbs over (section 2.2). High-bar and `barbell_squat` hands wrap the bar like a pull and sit mid-palm. `front_squat` is open-handed and should skip the finger closing and the contact check, or check only the palm and finger pads.
- [Inferred] **Where the rig's "palm" depth lands.** `gripPalm` is 0.45 of the palm's length from the wrist, which is mid-palm, not the heel. The heel over the forearm bones is about 0.2–0.3. That matches the press cue in section 2.1 ("directly over your forearm bones"). `gripAlong` at 0.8 lands at the finger base as section 1.1 defines it.
- [Inferred] **`HELD_RADIUS` is 16 mm for everything.** That is a 32 mm bar. Every barbell is 28–29 mm, so fingers fitted to 32 mm stand 2 mm off a 28 mm bar, right at the failure line. The kit already gives each held part its own radius (barbell 14 mm, dumbbell 16 mm, stirrup and lat-bar grips 16.5 mm, machine grips 19.5 mm). The solver should read the radius from the part under the hand.
- [Inferred] **The rope is too thin, and so are its end stops.** The kit's rope strands are 22 mm and its knobs 40 mm. Makers list 25–32 mm rope and 2.5 in (63.5 mm) end caps (section 4.4). The table uses 28 mm and 60 mm.
- [Inferred] **Machine handle diameters are unpublished.** No machine maker I found gives one. The table uses 36 mm, from 1 in Schedule 40 pipe with a thin sleeve, inside the 30–50 mm ergonomic range. The kit's 39 mm sleeve is within 3 mm of that, so it can stay.
- [Inferred] **The thumb on a thin bar.** On every bar under 49 mm the fingertips pass the thumb (section 1.2). A wrapped thumb on a 25–35 mm bar rests across the index and middle fingers, so its contact with the bar itself can show a gap. The 2 mm check should accept thumb contact with the fingers in a wrapped grip on bars under about 40 mm.
- [Inferred] **Curls at mid-palm.** No source places a curl bar in the hand. Mid-palm follows the load through most of the rep. If the owner's eye wants the classic curl look of the bar near the fingers' roots at the bottom, finger base is the other honest choice.
- [Inferred] **Thumbless bench and pulldown, hook-grip deadlift.** Coaches split, and the IPF requires a wrapped thumb on the bench. Defaults stay wrapped. An entry can override.
- [Inferred] **Wrist numbers.** The 5–10° extensions on presses read coaches' words ("neutral, slightly extended", "won't be entirely straight"). No study measures them. The 30° flexion at the top of an upright row is my estimate. The EZ bar's `rotate` of about 160 is also an estimate, since no maker publishes a camber angle.
- [Inferred] **Trap bar default.** Rogue's handles are 34 mm and Eleiko's 28 mm. The table takes 32 mm as a middle value. Use the kit part's radius when the trap bar is built.
- [Inferred] **Leg-machine holds.** Nothing describes how hands hold leg-press, leg-curl, hack-squat or calf-raise handles. The table treats them as relaxed, wrapped finger-base holds.
- [Inferred] **The IPF rulebook's host.** I read the IPF 2026 Technical Rulebook as hosted by the Irish Powerlifting Federation, not from powerlifting.sport. The bar and grip rules quoted here should be checked against the IPF's own copy before anything relies on the exact wording.

## 7. Sources

### Read
- IPF Technical Rulebook 2026, effective 1 March 2026 (PDF, Irish Powerlifting Federation's copy): https://www.irishpowerliftingfederation.com/wp-content/uploads/2026/03/2026-IPF-Technical-Rulebook-effective-01-March-2026-v3.pdf
- IWF Technical and Competition Rules & Regulations 2026 (PDF from iwf.sport): https://iwf.sport/wp-content/plugins/download-monitor/download.php?id=598
- NSCA Kinetic Select, Exercise Technique Manual for Resistance Training 3rd ed., power snatch: https://www.nsca.com/education/articles/kinetic-select/power-snatch
- NSCA Kinetic Select, power clean: https://www.nsca.com/education/articles/kinetic-select/power-clean
- NSCA Kinetic Select, push jerk: https://www.nsca.com/education/articles/kinetic-select/push-jerk
- Feigenbaum, "The Grip Problem", Barbell Medicine: https://www.barbellmedicine.com/blog/the-grip-problem/
- Seo & Armstrong 2008, Hum Factors (PMC full text): https://pmc.ncbi.nlm.nih.gov/articles/PMC9089462/
- Kong & Lowe 2005, Int J Ind Ergon (NIOSH abstract): https://stacks.cdc.gov/view/cdc/197408
- Bugbee & Botte 1993, Clin Orthop Relat Res (PubMed abstract): https://pubmed.ncbi.nlm.nih.gov/8222413/
- O'Driscoll et al. 1992, J Hand Surg Am (PubMed abstract): https://pubmed.ncbi.nlm.nih.gov/1538102/
- Eleiko IPF Powerlifting Competition Bar product sheet: https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3061173_product-sheet-eleiko-ipf-powerlifting-competition-bar-20-kg.pdf
- Eleiko Öppen Deadlift Bar product sheet: https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3085153-03_product-sheet-eleiko-oppen-deadlift-bar-black_2.pdf
- Eleiko Curl Bar product sheet: https://cdn.shopify.com/s/files/1/0262/8157/4456/files/3061174_product-sheet-eleiko-curl-bar-50-mm-12-kg.pdf
- Eleiko Evo fixed dumbbell: https://eleiko.com/equipment/dumbbells/fixed/3060990-0220-eleiko-evo-dumbbell-fixed-22-kg
- Rogue Ohio Bar: https://www.roguefitness.com/the-ohio-bar-black-zinc
- Rogue Ohio Power Bar 20 kg: https://www.roguecanada.ca/rogue-ohio-power-bar-20-kg
- Rogue Curl Bar: https://www.roguefitness.com/ie/rogue-curl-bar-eu
- Rogue SB-1 Safety Squat Bar: https://www.roguecanada.ca/sb-1-rogue-safety-squat-bar
- Rogue TB-2 Trap Bar: https://www.roguecanada.ca/rogue-tb-2-trap-bar
- Rogue Dumbbells: https://www.roguefitness.com/ca/rogue-dumbbells
- PowerBlock Elite EXP (Rogue listing): https://www.roguefitness.com/powerblock-series-elite-exp-dumbbells
- Rogue Single Handle Cable Attachment: https://www.roguecanada.ca/single-handle-cable-attachment
- Rogue Lat Bar: https://www.roguecanada.ca/rogue-lat-bar
- Rogue Stainless Straight Lat Bar: https://www.roguecanada.ca/rogue-stainless-straight-lat-bar
- Rogue Grip Triangle: https://www.roguecanada.ca/rogue-grip-triangle-standard-grip
- Rogue Tricep Rope Attachment: https://www.roguecanada.ca/tricep-rope-attachment
- Rogue Monster Crown Pull-up Bar: https://www.roguecanada.ca/rogue-monster-crown-pull-up-bar
- Rogue Velocidor: https://www.roguecanada.ca/rogue-velocidor
- [Secondary] Body-Solid triceps rope, retailer's spec: https://www.sportsunlimitedinc.com/body-solid-triceps-rope.html
- [Secondary] Ivanko pro-style dumbbells, Iron Company listing: https://www.ironcompany.com/ivanko-pro-style-dumbbells-chrome-endcaps
- [Secondary] Testify Strength & Conditioning, "Use this grip for a bigger bench press and press": https://decagon-koala-6f68.squarespace.com/articles/use-this-grip-for-a-bigger-bench-press-and-press
- [Secondary] "How to Barbell Press", exercise summary of Starting Strength material: https://exercises.kemitchell.com/sources/Starting-Strength-how-to-barbell-press
- [Secondary] PTPioneer summary of NSCA CSCS chapter 15: https://www.ptpioneer.com/personal-training/certifications/nsca-cscs/cscs-chapter-15/
- Body-Solid Tools catalogue 2020 (read; lists attachments but gives no diameters): https://cdn.shopify.com/s/files/1/0550/5060/0670/files/2020_Body-Solid_Tools.pdf

### Seen only in search
- Rippetoe, "Gripping Matters" (startingstrength.com, blocked): https://startingstrength.com/article/gripping_matters
- Rippetoe, "The correct place for the bar in your hands in the deadlift": https://startingstrength.com/training/from-the-archives-the-correct-place-for-the-bar-in-your-hands-in-the-deadlift
- Rippetoe, "The Hook Grip" (2019): https://startingstrength.com/training/the-hook-grip-breakdown
- Deaton, "The Squat: Thumbs-Around Grip" (2018): https://startingstrength.com/training/the-squat-thumbs-around-grip
- Rippetoe, press chapter, unofficial copy: https://www.scribd.com/doc/208714042/Learning-Press-Rippetoe
- Barbell Medicine forum, bench grip thread: https://forum.barbellmedicine.com/t/bench-press-grip-question/557
- Art of Manliness, "Troubleshooting the low bar squat": https://www.artofmanliness.com/strength/fitness/troubleshooting-the-low-bar-squat/
- Torokhtiy, front squat grips: https://torokhtiy.com/blogs/guides/front-squat-grip
- Lift Vault, suicide grip: https://liftvault.com/resources/suicide-grip/
- Muscle & Strength, thumbless pull-up profile: https://www.muscleandstrength.com/node/49610
- CrossFit Roots, "The Grip Talk": https://crossfitroots.com/the-grip-talk/
- Rogue Tricep Push Down Attachment guide: https://support.roguefitness.com/hc/en-us/articles/47332294564500-Product-Guide-Rogue-Tricep-Push-Down-Attachment
- Rogue Matador dip handles (retailer): https://tfusa.net/products/rogue-monster-lite-matador-dip-bar
- Rogue SPEAL pull-up bar 2.0: https://www.roguefitness.com/ca/rogue-speal-bar-2-0
- REP Fitness Pro Series 48 in lat bar: https://repfitness.com/products/pro-series-48-lat-bar
- York pro-style dumbbells (retailer): https://northernfitness.ca/products/pro-style-dumbbells
- PowerBlock Commercial Pro 100: https://powerblock.com/en-gb/products/commercial-pro-100-adjustable-dumbbells
- Garage Gym Lab, Bowflex SelectTech 552 review: https://garagegymlab.com/bowflex-selectech-552-review/
- Bodycraft commercial Smith machine: https://www.bodycraft.com/commercial-product/csm/
- Primal Pro Series Smith machine (retailer): https://gymequipment.co.uk/primal-pro-series-olympic-smith-machine
- Little Bloke Fitness triceps rope: https://www.littleblokefitness.com.au/products/tricep-rope-attachment
- Life Fitness, Hammer Strength plate-loaded T-bar row: https://lifefitness.com/en-us/catalog/strength-training/plate-loaded/hammer-strength-plate-loaded-t-bar-row
- CCOHS, hand tool design: https://www.ccohs.ca/oshanswers/ergonomics/handtools/tooldesign.pdf
- Edgren, Radwin & Irwin 2004, Hum Factors (its 38 mm optimum read only as cited in Seo & Armstrong): https://ergo.wisc.edu/wp-content/uploads/sites/901/2020/04/Edgren-et-al-2004.pdf
