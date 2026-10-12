# Progression and partial reps: research notes

Researched 2026-10-12 for Hyperlog, an Android/Kotlin tracker for Jeff Nippard-style periodized hypertrophy programs.

**Labels used below**

- **[Documented]**: stated in the cited source.
- **[Secondary]**: from a third-party copy or summary (community program uploads, blogs, news articles). Plausible, but not checked against the primary source.
- **[Inferred]**: my own reasoning or a design recommendation. No source states it.

MacroFactor ("MF") help-center articles are the main primary source for MF Workouts. MF does not publish its algorithm. The help center describes behaviour, not formulas.

---

## 1. MacroFactor Workouts: automatic progression ("Smart Progression")

### 1.1 General model
- [Documented] Progression is rule-based, not generative AI. The program "auto-adjusts based on your progress." https://macrofactor.com/workouts/
- [Documented] Inputs to recommendations: the weight and reps you log, your RIR target, how your performance compares with expectations, and your history on that exercise. https://help.macrofactorapp.com/en/articles/373-why-does-the-app-sometimes-recommend-lowering-weight-or-reps
- [Documented] Recommendations update as soon as you finish a workout. The program structure (exercises, sets, rep ranges) only changes when you edit it. https://help.macrofactorapp.com/en/articles/369-how-often-does-my-program-update , https://help.macrofactorapp.com/en/articles/387-will-smart-progression-work-with-altered-or-customized-programs
- [Documented] Smart Progression is separate from periodization. Smart-generated programs periodize sets, rep ranges and RIR per "cycle" (week). Custom programs need a Periodization toggle. https://help.macrofactorapp.com/en/articles/387-will-smart-progression-work-with-altered-or-customized-programs , https://help.macrofactorapp.com/en/articles/389-how-can-i-customize-periodization-rir-reps-and-sets-for-the-exercises-in-my-program
- [Documented] A pre-launch newsletter says the app will "estimate and account for fatigue" within a session. https://macrofactor.com/mm-oct-2025/
- [Documented] RIR "helps the app understand how hard each set actually was." The app uses it to account for "your current strength levels and how quickly you tend to fatigue." https://help.macrofactorapp.com/en/articles/385-what-is-rir-and-how-should-i-use-it-during-training

### 1.2 Fixed rep target vs rep range vs failure / AMRAP
- [Documented] **Sets not taken to failure (RIR target ≥1)** get a rep range and an RIR target. The app's expected capacity is **the middle of the rep range plus the RIR target**. Example: a target of 7–9 reps @ 2 RIR at 150 lb implies about 10 reps to failure at 150 lb. Logging 9+ reps @ 2 RIR counts as progress. So do 8+ @ 3 RIR, 10+ @ 1 RIR and 11+ @ 0 RIR: any deviation from the RIR target is converted into reps-to-failure. https://help.macrofactorapp.com/en/articles/372-what-does-progressive-overload-mean-in-macrofactor-workouts
  - [Inferred] Internally, MF almost certainly tracks something equivalent to "reps + RIR at load", meaning estimated reps to failure, and compares it with the target's implied reps to failure. A fixed rep target is the degenerate case where min = max. MF documents no separate fixed-rep mode.
- [Documented] **Sets to failure (0 RIR target)**: loads progress when you beat a prior performance. Example: 11 reps at 150 lb after 10 reps at 150 lb. https://help.macrofactorapp.com/en/articles/372-what-does-progressive-overload-mean-in-macrofactor-workouts
- [Documented] **Load vs reps.** Two settings control this:
  - "Expand Rep Range": the app may widen the target rep range "when adding or lowering weight alone can't achieve an optimal result". Example: after 100 lb × 8 it suggests 100 × 9 rather than 105 × 8.
  - "Weight Match": the app prefers keeping the previous set's weight and adding a rep.
  
  If no achievable load keeps the next set inside the rep range, the app shows "Progression unavailable within target rep range" and offers fixes. https://help.macrofactorapp.com/en/articles/305-understanding-and-using-smart-progressions , release note: https://macrofactor.com/wo-version-1-1-0/
- [Documented] A wand icon explains each recommendation. Blue means informational, yellow means a larger-than-expected change worth reviewing, and red means the required equipment is missing from your gym profile. https://help.macrofactorapp.com/en/articles/391-what-does-the-smart-progression-wand-mean
- [Documented] Generated programs deliberately make the first set hard (near failure) and later sets higher-RIR. The hard first set acts as an RIR "anchor", and "if RIR estimates are consistently off, the recommendations that follow may also drift." https://help.macrofactorapp.com/en/articles/397-is-it-normal-to-see-failure-sets-or-rir-decreasing-across-sets
- [Not documented] MF documents no AMRAP or training-max mechanism, and no top-set/back-off logic. Failure sets ("F") are the closest equivalent.

### 1.3 Failing reps / harder-than-expected sets
- [Documented] If you get fewer reps than prescribed (e.g. 6 when 8 were prescribed), or hit the reps at a lower RIR than targeted (8 reps @ 1 RIR when 2 was the target), "your recommended load may decrease slightly." Recommendations "are never punitive." https://help.macrofactorapp.com/en/articles/373-why-does-the-app-sometimes-recommend-lowering-weight-or-reps , https://help.macrofactorapp.com/en/articles/372-what-does-progressive-overload-mean-in-macrofactor-workouts
- [Not documented] MF does not publish the size of the reductions, its thresholds, or how many sessions of history it weighs.

### 1.4 Starting weight; changes to the rep range
- [Documented] The app gives **no weight recommendation for an exercise with no history**. The user picks a conservative load for the target reps and RIR. Recommendations start after logging. https://help.macrofactorapp.com/en/articles/377-how-do-i-know-what-weight-to-start-with , https://help.macrofactorapp.com/en/articles/305-understanding-and-using-smart-progressions
- [Documented] The "Initial log fill" setting pre-fills either Smart Progression values or the previous logged values. https://help.macrofactorapp.com/en/articles/305-understanding-and-using-smart-progressions
- [Documented] When you change sets, reps or rep ranges, future recommendations are based "on what you actually perform, not on the original defaults." https://help.macrofactorapp.com/en/articles/374-can-i-change-sets-or-reps-in-a-smart-generated-program
- [Documented] The Exercises view shows estimated 1RM, 3RM and 10RM. No formula is given. https://help.macrofactorapp.com/en/articles/281-exercises-overview
- [Documented] A MacroFactor article's example: 275 lb × 5 @ ~2 RIR implies a higher capacity, "perhaps around 305–325 lb." https://macrofactor.com/reps-in-reserve/
  - [Inferred] Epley with reps+RIR: 275 × (1 + 7/30) ≈ 339. With reps only: 275 × (1 + 5/30) ≈ 321. The RTS chart puts 5 @ RPE 8 at ≈81.1% (see §4), giving ≈339. MF's range is vague and does not pin down a formula.
  - [Inferred] Because MF tracks e1RM/3RM/10RM and re-targets the load after rep-range edits, it very likely converts between rep counts using an e1RM-style model. **The formula is not public.**

### 1.5 Equipment, increments, units
- [Documented] The app "only recommends weights that match the equipment you indicate you have access to." Gym profiles list the available plates and increments. Removing small plates (e.g. 1.25 lb or 0.5 kg) makes the app round to the nearest remaining option. You can keep several gym profiles. https://help.macrofactorapp.com/en/articles/390-how-to-change-equipment-settings-to-simplify-weight-recommendations
- [Documented] **Pin-loaded machines** are described as a list of weight-stack ranges. Each range has a start, an end and an increment, e.g. 15–25 by 5, 25–55 by 10, 55–190 by 15. Micro-adjustments come in three kinds: dial, selectorized add-on pin, or external plates (several can be added at once). The ranges can be generated from a photo ("Scan with AI"). https://help.macrofactorapp.com/en/articles/403-how-to-configure-weight-stack-ranges-for-pin-loaded-machines , https://macrofactor.com/mm-july-2026/
- [Documented] Some exercises count the machine's starting resistance and a share of bodyweight, so recommendations can end in odd decimals (.5, .75). https://help.macrofactorapp.com/en/articles/390-how-to-change-equipment-settings-to-simplify-weight-recommendations , https://help.macrofactorapp.com/en/articles/315-understanding-bodyweight-contribution
- [Documented] At light loads, the next increment can be a big relative jump (10 → 15 lb is +50%), so the app may add reps instead. It suggests a heavier load with fewer reps, finer increments in settings, or a swap to an exercise with finer increments. https://help.macrofactorapp.com/en/articles/392-why-am-i-being-recommended-very-high-reps
- [Documented] The Smith machine uses the barbell plate calculator. https://macrofactor.com/wo-version-1-1-0/
- [Not documented] Dumbbell and cable increments, and lb↔kg conversion rules. A unit preference exists: https://help.macrofactorapp.com/en/articles/353-changing-unit-preferences

### 1.6 Deloads and time off
- [Documented] The program's deload setting is None, First Cycle ("ease into new workloads"), or Last Cycle ("promote recovery"). A deload "temporarily lower[s] intensity or volume." The exact changes to sets, RIR and load are **not documented**. https://help.macrofactorapp.com/en/articles/297-deload-first-cycle-or-last-cycle
- [Documented] Programs are not calendar-based. Missed workouts don't change the program. On return, the newly logged sets update recommendations as usual. No documented "detraining" reduction is applied. https://help.macrofactorapp.com/en/articles/382-what-happens-to-my-program-if-i-miss-workouts-or-take-time-off

---

## 2. MacroFactor Workouts: partials and intensity techniques

- [Documented] **Set types**: Standard, Warm-up (W), Drop (D), Myo (M), Failure (F). You pick the type by tapping the set number.
  - Drop sets "appear as a single set with multiple weight and rep entries."
  - Myo sets are an activation set plus rest-pause entries at the same weight, all "grouped together."
  - Failure sets are logged at RIR 0, meaning technical failure.
  
  https://help.macrofactorapp.com/en/articles/317-logging-different-types-of-sets
- [Documented] Set types (myo, failure, drop) can be scheduled per cycle in the periodization editor. https://help.macrofactorapp.com/en/articles/389-how-can-i-customize-periodization-rir-reps-and-sets-for-the-exercises-in-my-program
- [Documented] **Partial reps** have an F/P toggle below the RIR selector. In P mode you enter how many full reps **and** how many partial reps you did. Logging partials "helps Smart Progression better interpret fatigue and results." https://help.macrofactorapp.com/en/articles/316-logging-full-or-partial-reps
  - [Not documented] What "lengthened" means, how partials display in history, and whether partials affect e1RM, progression or volume. MF has no dedicated "lengthened partial" or "static stretch" set type. The closest documented features are the partial-rep count and the free-text notes.
- [Documented] Volume views: "Sets" "treats all sets equally" per muscle group. "Volume" weights sets by load. https://help.macrofactorapp.com/en/articles/343-viewing-sets-and-volume-over-time , https://help.macrofactorapp.com/en/articles/280-muscle-groups
  - [Not documented] Whether warm-ups, drop or myo entries, or partial reps count. Whether indirect (synergist) sets count fractionally.
- [Documented] Importing Jeff Nippard programs (.xlsx) works only for programs still sold. Launch support included Min-Max, Ultimate PPL and Essentials, among six programs. You must own the program. https://help.macrofactorapp.com/en/articles/287-how-to-import-a-workout-or-program , https://macrofactor.com/workouts/
  - [Not documented] How the importer maps Nippard's "last-set intensity technique" column (LLPs, myo-reps, drop sets, static stretch) to MF set types.

---

## 3. Jeff Nippard's own progression and intensity-technique guidance

The program PDFs are paid and not public. The official product pages give only high-level descriptions. The specific rules below come from community copies of program text, labelled **[Secondary]**.

### 3.1 Progression rules
- [Documented] Essentials emphasises "strength progression" and "beating the logbook" over adding volume. Exercises stay the same for 4 weeks per block. https://jeffnippard.com/products/the-essentials-program
- [Documented] Fundamentals uses "a simple, linear progression" and tracks "LSRPE" ("how hard was your LAST set?"). https://jeffnippard.com/products/fundamentals-hypertrophy-program
- [Documented] Min-Max Phase 2 uses 1–2 hard working sets, "often taken to 0–1 reps in reserve." "Most exercises are now taken to failure on the first working set instead of the last." https://jeffnippard.com/products/min-max-program-phase-2
- [Secondary] Min-Max community upload on Boostcamp: "Progression is a double progression scheme. Add reps each week, once at the top of the rep range—add weight. If you can't do either, focus on strict form." Each exercise has an RPE ("Intensity") per set, e.g. Lying Leg Curl 6–8 @9 then 6–8 @10. https://www.boostcamp.app/users/XIZgUP-jeff-nippards-min-max-4xweek
- [Secondary] Bodybuilding Transformation System: "Early Sets should be a little bit easier than the later sets (slightly lower RPE)," and "the Last Set is pushed to failure on all exercises (besides intro/deload weeks)." Week 1 is mostly RPE ~7–9. https://www.boostcamp.app/users/lBskD1-nippards-the-bodybuilding-transformation-system
- [Secondary] Deloads (Nippard video): a deload is a light week, "a few reps further from failure and do one less set per exercise." https://rosetta.to/u/jeffnippard/when-and-how-to-deload-science-explained

### 3.2 Intensity techniques
- [Documented] Pure Bodybuilding Phase 2 lists "Greater focus on lengthened partials" and says the guide includes the "scientific details behind the intensity techniques." https://jeffnippard.com/products/the-pure-bodybuilding-program-phase2
- [Documented] Min-Max Phase 2: "Block 2 [weeks 7–12 of a 12-week, two-block program] introduces advanced last-set intensity techniques (drop sets, myo-reps, static holds)." https://jeffnippard.com/products/min-max-program-phase-2
- [Secondary] Min-Max community upload, technique definitions. All apply "only to the final working set":
  - Myo-reps: take the set to ~1 RIR, rest 10–15 s, do 3–5-rep mini-sets, stop when a mini-set falls below 3.
  - Drop set: from near failure, cut the load ~25% and continue to near failure.
  - Lengthened partials: after full reps, 5–10 controlled partials in the stretched position, *or* hold the stretch 10–20 s.
  
  https://www.boostcamp.app/users/XIZgUP-jeff-nippards-min-max-4xweek
- [Secondary] Pure Bodybuilding Upper/Lower (user-uploaded document) lists LLPs as the last-set technique on, e.g., lateral raises, rows and leg extensions. In some cases the whole last set is LLPs. https://www.scribd.com/document/730169778/Pure-Bodybuilding-Upper-Lower
- [Secondary] Nippard's LLP video (Nov 2023, as reported) describes three uses:
  1. As a set-extension after full-ROM failure ("another three to six partial reps").
  2. With every rep of a set done as an LLP.
  3. Mixed throughout a set.
  
  https://barbend.com/news/long-length-partial-reps/ , https://fitnessvolt.com/jeff-nippard-long-length-partials-muscle-growth/
- [Inferred] In Nippard programs the technique is a per-exercise, per-week property of the **last set**. It is usually absent in intro/deload weeks and in Block 1 of Min-Max. Model it as a set attribute, not as an exercise attribute.

---

## 4. Evidence on autoregulated load progression

### 4.1 RIR/RPE as the effort metric
- [Documented] The RIR-based RPE scale (RPE 10 = 0 RIR, 9 = 1 RIR, …). Zourdos et al. 2016, JSCR, https://doi.org/10.1519/JSC.0000000000001049 . Practical application: Helms et al. 2016, Strength Cond J 38(4):42–49, https://doi.org/10.1519/SSC.0000000000000218 (open access: https://openrepository.aut.ac.nz/items/92cefeac-7c32-44f1-bfbc-f48f4a5dd8cf/full ).
- [Documented] RIR prediction accuracy (meta-analysis, 12 studies, 414 participants): lifters **under-predict reps to failure by ~0.95 reps** on average. Predictions are more accurate closer to failure and in sets of ≤12 reps; they are much worse above 12 reps. Training status did not affect accuracy. Halperin et al. 2022, Sports Med, https://doi.org/10.1007/s40279-021-01559-x
- [Documented] Load chosen by RPE vs by %1RM, with sets and reps matched: both work, and RPE loading probably gives a small 1RM advantage. Helms et al. 2018, Front Physiol, https://doi.org/10.3389/fphys.2018.00247
- [Documented] Meta-analysis: autoregulated load prescription (RPE/VBT) and %-based prescription produced similar 1RM gains (MD 2.07 kg, p = 0.09). Too little hypertrophy data to compare. Hickmott et al. 2022, Sports Med Open, https://pmc.ncbi.nlm.nih.gov/articles/PMC8762534/
- [Documented] For hypertrophy, failure is not clearly needed. There is a trivial advantage for "set failure" vs non-failure, and none for momentary failure specifically. Refalo et al. 2023, Sports Med, https://doi.org/10.1007/s40279-022-01784-y
- [Documented] Progressing reps at a fixed load vs progressing load at fixed reps gave similar hypertrophy in trained lifters. Strength gains slightly favoured load progression. Plotkin et al. 2022, PeerJ, https://doi.org/10.7717/peerj.14142 ; SBS commentary: https://www.strongerbyscience.com/progressive-overload-strategies/

### 4.2 Prescription types
- **Fixed reps @ RPE** (e.g. 3×8 @ RPE 8): select the load from e1RM and the RIR chart. If the actual RPE comes in lower or higher, adjust the next set or session load. Helms 2016 (above) covers set-to-set adjustment. [Documented]
- **Rep range @ RPE (double progression)**: add reps until the top of the range is reached at the target RIR, then add load. This is Nippard's stated scheme ([Secondary], §3.1). MF does the same with an RIR-aware generalisation: compare reps+RIR with mid-range+RIR target ([Documented], §1.2).
- **AMRAP / last set to failure → training-max (TM) adjustment (SBS RTF)**: four sets at a %TM, then a last set AMRAP vs a rep target. The TM rises when you beat the target and falls when you miss.
  - [Secondary] Reported values are about +0.5% TM per rep over target, and about −1% per rep (or −2%/−5% steps) when missing by ≥2. Sources disagree on the exact table; the official spreadsheet is paywalled. https://liftvault.com/programs/strength/stronger-by-science-sbs-program-bundle-by-greg-nuckols/
  - [Secondary] SBS RIR variant: sets are done at a fixed load until the RIR target is hit. Fewer than 4 sets → TM −5%; more than 6 → TM +2%. https://www.scribd.com/document/491275201/Instructions

### 4.3 Estimating load when rep targets change (e1RM)
- [Documented] Rep-based 1RM prediction is most accurate at low reps:
  - Equations were more accurate when fewer than 10 reps to failure were used. Mayhew et al. 2008, JSCR, https://doi.org/10.1519/JSC.0b013e31817b02ad
  - 5RM-based prediction beat 10RM- and 20RM-based prediction. Reynolds et al. 2006, JSCR 20(3):584–592, https://www.unm.edu/~rrobergs/478RMStrengthPrediction.pdf
- [Secondary] LeSuer et al. 1997 (7 equations; bench, squat, deadlift): correlations were all high (r > 0.95), absolute accuracy varied by lift, and all equations underestimated the deadlift. Reported via https://arxiv.org/pdf/2603.17495 . It is often stated that Mayhew and Wathan did best on bench and squat; that claim was not verified here.
- [Documented, not peer-reviewed] An app-data preprint (303k near-failure sets) proposes a weight-dependent formula, `1RM = w·(1 + (r−1)^0.85 / (−2.55 + 4.58·ln w))`, with 17–22% less internal inconsistency than classic equations. The improvement is largest on light isolation exercises. Marzagao 2026, arXiv, https://arxiv.org/abs/2603.17495
- [Documented] RTS/Tuchscherer RPE chart. This is a coach-derived table of %1RM by reps × RPE (e.g. 5 @ RPE 8 ≈ 81.1%). It is widely reproduced, e.g. https://fitnessvolt.com/rpe-training/rep-max-chart/ . It has no peer-reviewed validation as a table. Its values are close to Epley applied to reps+RIR.
- [Inferred] **Most defensible choice for Hyperlog.** Use Epley (or the near-identical Brzycki at ≤10 reps) on *effective reps to failure* = reps + RIR. Use it for re-targeting between rep counts only.
  - Restrict input sets to reps+RIR ≤ ~12, and down-weight sets above that (Halperin; Mayhew 2008; Reynolds 2006).
  - Prefer each exercise's own history over the generic formula. Fit a per-exercise correction once enough sets exist (MF hints at this: "how quickly you tend to fatigue").
  - When you change rep targets, round the load **down** to the nearest available increment. RIR is typically under-reported by about 1 rep, so the user's true capacity is usually slightly higher than estimated, which means rounding down is safe.
  - No formula is universally best (see the Wathan/Mayhew/Epley comparisons above).

---

## 5. Should partial reps count toward volume?

### 5.1 Evidence
- [Documented] Meta-analysis (Wolf et al. 2023, Int J Strength Cond 3(1)): there was a trivial overall advantage for full ROM over partial ROM (SMD 0.12, CI −0.02 to 0.26). A subgroup hinted that **long-muscle-length partials may beat full ROM** for hypertrophy (−0.28, CI −0.81 to 0.16; not significant). https://doaj.org/article/99e86b317bec40f5abc2f0008358737e
- [Documented] Pedrosa et al. 2022 (untrained women, knee extension): partials in the lengthened range gave greater regional quadriceps hypertrophy than full ROM. https://doi.org/10.1080/17461391.2021.1927199
- [Documented] Kassiano et al. 2023 (women, calf raise): lengthened partials gave +15.2% medial gastrocnemius vs +6.7% for full ROM. https://doi.org/10.1519/JSC.0000000000004460
- [Documented] Wolf et al. 2025 (PeerJ; 30 **trained** participants, within-subject, upper body, 8 weeks, sets made entirely of LPs vs full ROM): hypertrophy was similar, with moderate Bayesian support for no difference. Conclusion: "emphasize the stretched position, either by using a full ROM or LPs." Jeff Nippard is a co-author. https://doi.org/10.7717/peerj.18904
- [Documented] Gschneidner et al. 2025 (J Sports Sci; n = 297 trained, 15 sites, 12 weeks): LP-ROM vs full ROM differences were "small and practically equivalent." Formal equivalence was not reached at the strict alpha. https://doi.org/10.1080/02640414.2025.2567805
- [Documented] **Partials as a set extension past failure:**
  - Larsen et al. 2025 (Front Psychol; 23 untrained men, calf raise): adding lengthened partials after full-ROM failure gave more growth (+9.6% vs +6.7%). Volume-load was **not** matched: partials added ~87% more volume-load. https://doi.org/10.3389/fpsyg.2025.1494323
  - Goli et al. 2026 (Int J Exerc Sci; 16 untrained men, calf raise): with volume-load **equated**, half the sets plus post-failure LPs gave the **same** hypertrophy as twice the full-ROM sets. Growth per set roughly doubled. Partial reps "were counted the same way regardless of the ROM." https://doi.org/10.70252/ijes2026403
- [Documented] Related techniques:
  - Drop sets gave hypertrophy similar to traditional sets, in less time. Sødal et al. 2023, Sports Med Open, https://doi.org/10.1186/s40798-023-00620-5
  - Rest-pause and drop sets matched traditional sets for hypertrophy when volume was equated. Enes et al. 2021, APNM, https://doi.org/10.1139/apnm-2021-0278
- [Documented] Set-counting convention in the dose-response literature: counting indirect sets as 0.5 ("fractional") fit the data best. Pelland et al. 2026, Sports Med, https://doi.org/10.1007/s40279-025-02344-w

### 5.2 Implications [Inferred]
1. **A set done entirely as lengthened partials** (Nippard "all reps LLP", Wolf 2025, Gschneidner 2025) should count as **one full hard set** for weekly set volume. The evidence shows equivalent hypertrophy.
2. **Partials added after full-ROM failure** are an extension of an existing set, so they should **not** add another set. Goli 2026 suggests the extension roughly doubles the stimulus per set, so these partials are not worthless. Larsen 2025 suggests the benefit may come from the added volume. A defensible default is to count the set as 1 and record the extension. If you want a weighted metric, a configurable bonus such as +0.5 set is reasonable, but no study defines that number.
3. **Volume-load (kg × reps)**: including partial reps is consistent with how Goli 2026 counted them. Store full and partial reps separately so either view can be computed.
4. **Progression and e1RM**: use **full reps only**. Partial reps are not comparable to full-ROM reps at the same load. A set with partials appended still has a valid full-rep count at RIR 0, so treat it as a failure set for progression.
5. Drop and myo extensions follow the same logic as point 2: the main set counts as 1, and mini-sets are logged as entries in the same set. MF groups them this way ([Documented], §2).

---

## 6. Recommendations for Hyperlog [Inferred]

1. **One per-exercise state: an e1RM estimate built from reps + RIR.** Use it for all prescription types:
   - Fixed reps @ RIR: load = e1RM ⁻¹(reps + RIR).
   - Rep range: target the bottom of the range at the RIR target. Progress when reps + RIR ≥ (top of range + RIR target). This is stricter than MF's mid-range rule, and also the classic double progression.
   - AMRAP: update the TM by the reps over or under target.
2. **Formula**: Epley on reps + RIR, capped at ≤12 effective reps. Apply a per-exercise correction learned from history. Hide the formula behind one interface so it can later be swapped, e.g. for the weight-dependent formula in Marzagao 2026.
3. **Missed reps**:
   - A miss of 1 effective rep: hold the load.
   - A miss of 2 or more: cut the e1RM by ~2–5%. MF says only "slightly"; SBS uses −1% per rep to −5%.
   - Two consecutive misses: flag for review rather than cutting automatically again.
4. **Equipment model like MF's**: per gym profile, store plates (by unit), dumbbell sets, and machine stack ranges (start/end/step) plus micro-adjustments.
   - Recommend only achievable loads.
   - When the next increment overshoots the target, first keep the load and add reps (MF's "weight match"/"expand rep range"), and only then jump the load.
   - Store loads in kg internally and convert at the edge; round to the profile's actual increments.
5. **Store partial reps separately**: `fullReps`, `partialReps`, `partialKind` (lengthened / short / unspecified), plus the set's technique (none / LLP-extension / all-LLP / drop / myo / static-stretch hold with seconds). Use full reps for e1RM. Count an all-LLP set as one set. An extension adds no set (an optional weighted bonus is fine).
6. **Program model**: put the intensity technique on each set in each week (usually the last set), matching Nippard's "last-set intensity technique" column.
7. **Deloads and RIR reliability**:
   - Don't let deload-week or high-RIR sets (RIR ≥4, or >12 effective reps) lower the e1RM much. RIR is least accurate there (Halperin 2022).
   - Model deloads as Nippard describes: +2–3 RIR and one fewer set.
8. **After time off**: MF does nothing special. Optionally offer a conservative first-session suggestion (e.g. −5–10% after ≥3 weeks) that self-corrects after one logged session. No research supports a specific figure.
