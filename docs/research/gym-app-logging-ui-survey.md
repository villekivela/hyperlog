# In-workout set logging UI: survey of strength apps

Researched 2026-10-12 for Hyperlog, a calm, one-handed Android set logger for Nippard-style periodized hypertrophy programs (rep ranges, RIR, drop sets, myo-reps, lengthened partials, supersets). MacroFactor Workouts is out of scope here; see `macrofactor-workouts-ui.md`.

**Labels**

- **[Documented]**: stated in the cited source (official site, help center, store listing, or release notes).
- **[Secondary]**: third-party review, review aggregator, or competitor blog. Competitor blogs (Setgraph, Boostcamp, Dr. Muscle, Alpha Progression) are biased.
- **[Recalled]**: my own memory of the app UI from before mid-2026. Plausible but not re-checked against a current build. Verify before relying on it.
- **[Inferred]**: my reasoning or a design recommendation.

**Source limits.** Reddit blocked automated fetching and search engines returned no Reddit threads, so user sentiment comes from App Store reviews, review aggregators, and editorial reviews. The RP, Fitbod, and Hevy help centers returned 403. Strong has no public help center. Few vendors describe layouts in text, so the screen-structure claims are thinner than the feature claims.

---

## 1. Per-app notes

### 1.1 Hevy (iOS, Android, Wear OS, Apple Watch, web)

1. **Structure.** One scrolling list of every exercise in the session. Each exercise has a note field, a rest-timer chip, then set rows. A header shows elapsed time, completed sets, volume, and a Finish button. "+ Add Exercise" sits at the bottom. [Documented] https://www.hevyapp.com/features/track-workouts/ Columns are SET / PREVIOUS / KG / REPS / ✓. [Recalled] With supersets, "smart superset scrolling" jumps to the next exercise in the group. [Documented, mechanism not described] same URL.
2. **Set entry.** On a repeated exercise, the set count, weight, and reps from last time are prefilled and editable. The PREVIOUS column shows the earlier performance. You can choose whether "previous" means the last time you did the exercise anywhere or the last time within this routine. [Documented] https://www.hevyapp.com/features/track-workouts/ , https://www.hevyapp.com/features/workout-settings/ Entry uses the system keyboard, with an optional plate-calculator button "above the phone keyboard". [Documented] track-workouts URL. Tapping ✓ completes the set as shown, which is a one-tap "as planned". [Documented/Recalled]
3. **RPE.** An optional RPE column, turned on in settings. RPE can be logged per set but not set as a target in routines. There is no RIR. [Documented] https://www.hevyapp.com/features/track-workouts/
4. **Special sets.** Tapping the set number opens a type menu: Normal, Warm-up (W), Drop (D), Failure (F). Types can be mixed within an exercise. The auto rest timer is suppressed when the next set is a drop set, so drops run back to back. [Documented] https://www.hevyapp.com/features/workout-set-types/ , https://help.hevyapp.com/hc/en-us/articles/34896293707927 Supersets: exercise ⋯ menu > "Add To Superset". More than two exercises are allowed (circuits). [Documented] Myo-reps and partials have no type. Users have asked for half-rep tracking. [Secondary] https://www.onclarity.com/leaderboard/insight/hevy
5. **Rest timer.** Starts when a set is marked complete. Set per exercise from 5 s to 5 min, or off (useful inside supersets), with a global default. ±15 s buttons during rest. [Documented] https://www.hevyapp.com/features/workout-rest-timer/ The countdown appears in a bar pinned to the bottom of the screen. [Recalled] Live Activity on iOS. On Android, a lock-screen notification shows the timer with ±15 s and skip controls, plus the current exercise, next set, and target reps. [Documented, via search snippet; article 403] https://help.hevyapp.com/hc/en-us/articles/35649846517399 Android 14 needed in-app instructions to stop the background timer from being killed. [Documented] https://www.apkmirror.com/apk/hevy-gym-workout-tracker/hevy-gym-log-workout-tracker/hevy-gym-log-workout-tracker-1-30-27-release/
6. **Reorder/swap.** Exercise ⋯ menu offers reorder, replace, and remove. Swipe left on a set to delete it. [Documented] track-workouts URL.
7. **Style.** Dark or light, a fairly dense table, completed rows turn green, live volume totals. [Secondary] https://gainframe.app/blog/best-workout-tracker-apps/ Described as "minimal and clean". [Secondary] https://setgraph.app/ai-blog/hevy-vs-strong
8. **Sentiment.** Praised for its interface and free unlimited logging. Complaints in an aggregator: about 5% want the timer to auto-start after checking a set off (it already does, so this is a discoverability problem), weight-calculation bugs, and no autocorrect in notes. [Secondary] https://www.onclarity.com/leaderboard/insight/hevy

### 1.2 Strong (iOS, Android, Apple Watch)

1. **Structure.** One scrolling list of exercise cards with SET / PREVIOUS / KG / REPS / ✓ rows. This is the template Hevy and most clones copy. [Recalled] "Interface assumes you are mid-session, resting between sets, and need to log quickly." [Secondary] https://repreturn.com/?p=2544
2. **Set entry.** Strong uses its own numeric keypad with +/− increment keys, a "Next" key that jumps to the next field or set, and an RPE key. [Recalled] Release notes support this: "RPE values not saving when pressing Next" (6.4.2) and "RPE keyboard not appearing on iOS 27" (6.4.3). [Documented] https://apps.apple.com/app/id464254577 Prefill comes from previous values. [Recalled]
3. **RPE.** Supported per set. No RIR. [Secondary] https://www.boostcamp.app/best/workout-logging
4. **Special sets.** Warm-up, Failure, and Drop tags, a warm-up calculator (Pro), and "Supersets/Grouped Exercises". [Documented] https://apps.apple.com/app/id464254577
5. **Rest timer.** Auto countdown after a set. [Documented] same URL. Shown inline beneath the completed set. [Recalled] Live Activity and Dynamic Island for rest timers only arrived in 6.5.0 (Aug 2026). [Documented] same URL. The Watch app logs independently of the phone. [Documented]
6. **Reorder/swap.** Exercise menu: replace, reorder. [Recalled]
7. **Style.** "Richer with more buttons and menus", with a learning curve. [Secondary] https://setgraph.app/ai-blog/hevy-vs-strong Rated "fastest minimal logger". [Secondary] https://gainframe.app/blog/best-workout-tracker-apps/
8. **Sentiment.** Speed and polish are praised. Complaints: the free tier is capped at 3 routines [Secondary], and some type combinations can't be logged, e.g. added weight with duration (weighted planks). [Documented review] https://apps.apple.com/app/id464254577

### 1.3 RP Hypertrophy (iOS, Android, web)

1. **Structure.** A list of the day's exercises, each with set rows and a checkbox to log. [Documented review mentions checkboxes] https://apps.apple.com/us/app/rp-hypertrophy/id1555614554?see-all=reviews&platform=iphone The week's RIR target appears in the workout header, and target weight/reps show as greyed placeholders in each set's fields. [Recalled] "Know exactly the weight and reps to hit every week." [Documented] https://apps.apple.com/us/app/rp-hypertrophy/id1555614554
2. **Set entry.** Weight and reps fields use the system keyboard, then you tick the checkbox. [Recalled]
3. **RIR.** The target is shown and periodized across the meso, but per-set RIR is not logged. Performance against target drives progression. [Recalled] RIR falls over a 4–6 week cycle, then a deload. [Secondary] https://dr-muscle.com/rp-hypertrophy-app-review/
4. **Special sets.** Myorep and "Myomatch" set types can be programmed. [Documented] https://rpstrength.com/modernwisdom The help center has FAQs on logging warm-up sets and doing exercises out of order (section returned 403). [Documented titles] https://help.rpstrength.com/hc/en-us/sections/30801323964183-Navigating-the-App
5. **Feedback prompts.** Mid-workout modals ask about soreness and joint pain when you start a muscle group, then pump and workload after its last set. [Recalled] The documented inputs are pump, soreness, joint pain, and workload. [Documented] https://rpstrength.com/modernwisdom One reviewer wants soreness asked once before the session "since soreness fades once warmed up". [Documented review] App Store reviews URL.
6. **Rest timer.** None. The help center FAQ is titled "Why is there no rest timer?". This is the most frequent review request, with the developer replying it is "under consideration". [Documented] App Store reviews URL. [Secondary] https://dr-muscle.com/rp-hypertrophy-app-critique/
7. **Reorder/swap.** Exercises can be replaced on the fly ("change things on the fly"). [Documented] Multi-gym equipment is handled poorly: the developer suggests duplicating a custom exercise per gym. [Documented dev reply] App Store reviews URL.
8. **Sentiment.** Auto-regulation and volume ramping are praised. Complaints: checkboxes "sometimes uncheck themselves", which leads to extra sets; poor behaviour on bad gym signal; no per-side logging for unilateral work; adds sets even when performance stalls. [Documented reviews] same URL.

### 1.4 Alpha Progression (iOS, Android)

1. **Structure.** One exercise per page, swiped horizontally, with an overview list. [Recalled] Notes and the last sets stay in view while logging. [Documented] https://apps.apple.com/app/id1462277793
2. **Set entry.** "Weight, reps and RIR in two taps." Before each set the app shows a recommended weight and reps that "change from set to set to account for fatigue" (Pro). Rounding follows the gym's available plates and increments, with multiple gym profiles. [Documented] same URL, https://alphaprogression.com/en/blog/alpha-progression-vs-hevy
3. **RIR.** Optional per set. Recommendations work without it. [Documented] alpha-progression-vs-hevy URL. Entry is a row of RIR chips rather than a keyboard. [Recalled]
4. **Special sets.** Automatic warm-up sets scaled to the first working set and available weights (Pro). Supersets, drop sets, bodyweight, assisted, and timed exercises. [Documented] App Store URL.
5. **Rest timer.** Free. Live Activity on the lock screen and in the Dynamic Island, showing the countdown and the next set. In 6.8 the whole-workout Live Activity turns into a countdown bar during rest. [Documented] App Store URL. The glossary frames the timer as "a checkpoint, not a command". [Documented] https://alphaprogression.com/en/glossary/rest-timer?redirect=1
6. **Swap.** Replace or delete mid-workout. Since 6.5, target metrics carry over to the replacement "where possible". [Documented] App Store URL.
7. **Style.** "Clean, streamlined." [Documented, self-description] Fully offline. [Documented]
8. **Sentiment.** "Completely replaced my need for a notebook." [Documented review] One past bug (6.3) blocked completing or changing a set, which shows how badly a logging-path bug lands. [Documented]

### 1.5 Boostcamp (iOS, Android)

1. **Structure.** List of the day's exercises, each showing target sets × reps and the last logged weight. [Documented] https://www.boostcamp.app/workout-tracker Hosts many Nippard-derived programs, e.g. a PPL with "Cable Crunch (Myo Rep)" as an exercise name. [Documented] https://www.boostcamp.app/users/MQBdPC-jeff-nippard-body-building-ppl This shows myo-reps encoded as text in the exercise name, not as a set type.
2. **Set entry.** "Hit the set, tap to log it." [Documented] workout-tracker URL.
3. **RPE/RIR.** Per-set fields for RPE (5–10) and RIR. Free. [Documented] same URL, https://boostcamp.app/features
4. **Special sets.** Drop sets on any working set, supersets alternated through, and reusable warm-up templates. [Documented] workout-tracker URL.
5. **Rest timer.** Auto-starts on log. [Documented] One user has to "manually turn off timer every week". [Documented review] https://justuseapp.com/en/app/1529354455/boostcamp-gym-workout-planner/reviews
6. **Swap.** Alternatives offered mid-workout, with weights carried over. [Documented] workout-tracker URL. Users report swaps not persisting ("only about half of them save"), and the ⋯ menu opening a white screen. [Documented reviews] justuseapp URL.
7. **Style.** "Clean interface", "simple and intuitive". [Documented reviews]
8. **Sentiment.** Complaints: network errors that "delete the information I just entered", a subscription popup that "takes 2 clicks to skip every single time" a set is added, and a donation prompt after the workout. [Documented reviews] justuseapp URL.

### 1.6 Liftosaur (iOS, Android, web)

1. **Structure.** The workout screen lists exercises with prefilled weight × reps per set. [Documented] https://www.liftosaur.com/blog/posts/liftosaur-overview/ The older UI showed each set as a square or circle labelled with reps. Tap once to complete as planned; further taps decrement reps. [Recalled; the docs playground says "tap on the squares to complete sets"] https://api3.liftosaur.com A redesigned workout screen was previewed on r/liftosaur. [Secondary] https://gummysearch.com/r/liftosaur/
2. **Set entry.** "Tells me the weight and reps and I tap once to say 'done'." [Documented testimonial] https://www.liftosaur.com/ Prescribed work is completed with a check icon. Weights are rounded to your plates. [Documented] overview URL.
3. **RPE.** Logged per set, and weights can be prescribed from RPE. Scripts can read `completedReps[n]`, `rpe`, and similar values. [Documented] https://www.liftosaur.com/ , https://www.liftosaur.com/doc/liftoscript
4. **Special sets.** Warm-ups ("W", by percentage or fixed weight). Drop sets and myo-reps "via scripts". There are no dedicated buttons. [Documented] https://www.liftosaur.com/
5. **Rest timer.** Appears after each completed set. Duration is set globally or per program exercise. [Documented] overview URL.
6. **Swap.** Not documented in what I fetched.
7. **Style.** Utilitarian. Built for power users who want programmable progression. [Inferred]
8. **Sentiment.** Programmable progression is the draw. [Inferred]

### 1.7 Setgraph (iOS only)

1. **Structure.** Exercise list, plus a set-entry screen per exercise. Swipe right on an exercise row to open the entry pad directly. [Documented] https://setgraph.app/articles/fast-workout-tracking-with-swipe-actions-in-setgraph
2. **Set entry.** Swipe right on a past set to repeat it as a new record ("swipe to repeat"). The 10.2.0 redesign made the rep and weight numbers larger. Partial reps such as 2.5 are allowed (26.0.1). [Documented] https://apps.apple.com/us/app/setgraph-workout-tracker/id1209781676
3. **RPE/RIR.** Not mentioned in the listing.
4. **Special sets.** Custom set labels (26.2.1). "Multisets" put supersets, dropsets, and circuits on one screen with rest timers between sets (26.6.0). [Documented] same URL.
5. **Rest timer.** Auto-starts after a set. Lock Screen and Dynamic Island. A "Next Set Due" notification. "Notepad Mode" keeps the screen awake. [Documented] same URL, https://setgraph.app/articles/setgraph-9-41-notepad-mode-for-faster-logging
6. **Swap.** n/a.
7. **Style.** Large numerals, minimal. [Documented]
8. **Sentiment.** One user, "out of breath", kept editing previous sets by accident and wanted a bigger "add set" button. Another complained that an update reversed the default set order. [Documented reviews] App Store URL.

### 1.8 JuggernautAI (iOS, Android)

1. **Structure.** A pre-session readiness check (motivation, sleep, calories, per-muscle soreness on 1–5 scales) adjusts that day's loads and reps. [Secondary] https://garagegymreviews.com/juggernautai-review 3.0 added a whole-block view showing past performance and upcoming weeks. [Documented] https://apps.apple.com/app/id1515756471
2. **Set entry / RPE.** RPE is logged on top and back-off sets, and later sets adapt within the session. Example: RPE 10 on set 2 of 3 removed set 3. [Secondary] garagegymreviews URL.
3. **Rest timer.** Separate defaults for main lifts and accessories (3.0.3 splits upper/lower main). Live Activity. Voice cue, beep, or silent. [Documented] App Store URL. 2023 review: the timer notification "works inconsistently". [Documented review]
4. **Swap.** For one day or for the whole block. [Secondary]
5. **Style.** Blue on black, dark only, "tough to read sometimes". [Secondary] garagegymreviews URL.

### 1.9 Fitbod (iOS, Android)

- Generated sessions, often around 8 exercises. [Secondary] gainframe URL. The rest timer sits below the exercise name, tap to adjust, with notification reminders. [Secondary, quoting help center] https://help.fitbod.me/hc/en-us/articles/30721437384215
- Swap: swipe left > Replace, or the Swap menu at the top right. "Session mods" change available equipment for today. Manual swaps feed the algorithm. [Documented] https://help.fitbod.me/hc/en-us/articles/360006335593-Editing-Workouts-in-Fitbod , https://help.fitbod.me/hc/en-us/articles/38318585683991
- Complaints: users want the timer to auto-start after a set is checked; there is a delay after logging a set before the list scrolls again; crashes. [Secondary] https://unitq.com/unitq-scorecards/fitbodworkoutfitnessplans , https://www.trustpilot.com/review/fitbod.me

### 1.10 Gravitus (iOS)

- Autofilled workouts, per-exercise rest timers, Live Activity and Dynamic Island. 4.6.0 brought "a lighter logging screen" and in-workout tools: MAX (compares the current set with your best), PLATES, TREND (is the lift climbing, flat, or slipping), and STRAIN. 4.6.1: the rest timer counts up after rest ends. [Documented] https://apps.apple.com/us/app/gravitus-gym-workout-tracker/id965383840
- Review request: a checkmark to mark exercises complete. [Documented review]

### 1.11 Liftin' (iOS, watchOS)

- Rule-based automatic weight adjustment. 50.5 added a "unified view for tracking reps and RPE/RIR". 54.0 made the next set "bigger and clearer" in the Live Activity and redesigned the set display. [Documented] https://apps.apple.com/app/id1445041669
- Praised for its clean UX and Watch app. [Documented reviews]

### 1.12 Others worth noting

- **StrongLifts 5×5.** Tap a set circle to log it, which also starts the rest timer. The timer counts up. A first alert fires at an earlier time ("1min30 since your last set") and a "Rest done" alert at the final time. Defaults are 3 min after a success and 5 min after a failure. No rest between warm-ups or after an exercise's final set. [Documented] https://support.stronglifts.com/article/25-rest-timer
- **Loaded (iOS).** 7.2.1 added "highlighted next set" and reordering from a long-press menu. RPE is derived from RIR. [Documented] https://apps.apple.com/app/id1088956596
- **RIRLift (iOS + Watch, 2026).** Shows rep ranges and RIR targets per exercise. One-tap replacement on the Watch "when equipment is taken". Supports drop sets, rest-pause, and failure finishers. [Documented] https://mwm.ai/apps/rirlift-hypertrophy-log/6768680416
- **Hypertrophy OS (iOS, Mar 2026).** Myo-reps, drop sets, and rest-pause built into workouts ("no manual math or tracking required"). [Documented] https://mwm.ai/apps/hypertrophy-os/6760429230
- **GymNote Plus.** Free-text entry ("squat 3x5@135") parsed into sets. [Secondary] gainframe URL.
- **Stronger by Science.** No standalone logging app found. SBS logging remains spreadsheet- and coaching-based. [Secondary]

---

## 2. Synthesis

### 2.1 Near-universal patterns (users expect these)

1. **Auto-start rest timer on set completion.** Hevy, Strong, Boostcamp, Liftosaur, Setgraph, AP, and StrongLifts all have it [Documented]. Where it is missing (RP) or manual (Fitbod, per complaints), it becomes the top request. Users even ask for it in Hevy, where it already exists, so it has to be visible.
2. **Previous performance next to today's entry.** A PREVIOUS column or "last time" line (Hevy, Strong, Boostcamp, AP) [Documented]. Hevy lets the user choose the scope of "previous".
3. **Prefilled values plus a one-tap complete.** Hevy prefills from last time, Liftosaur and RP from the program, AP from a per-set recommendation. A check or tap accepts them unchanged. [Documented]
4. **Set types via the set number or label.** W, D, and F tags in Hevy and Strong [Documented]. Myo-reps, partials, and rest-pause are generally not first-class (Boostcamp puts "(Myo Rep)" in the exercise name; Liftosaur uses scripts).
5. **Supersets as grouped exercises** from an exercise menu (Hevy, Strong, Boostcamp, AP) [Documented].
6. **Replace or swap an exercise mid-workout** from a per-exercise ⋯ menu, ideally keeping targets (AP 6.5, Boostcamp) [Documented].
7. **Lock-screen timer.** Live Activity on iOS (AP, Hevy, Strong since Aug 2026, Setgraph, Gravitus, Juggernaut, Liftin', Loaded), and an ongoing notification with ±15 s and skip on Android (Hevy) [Documented]. Strong waited until 2026 for this, which suggests it is now table stakes.
8. **Optional effort entry.** RPE in Hevy and Strong, RIR in AP and RIRLift, both in Boostcamp. It is optional everywhere except RP, where RIR is a periodized target rather than an input [Documented/Recalled].

### 2.2 What sets the best-loved loggers apart

1. **Low friction per set.** Strong is "fastest minimal logger"; AP is "two taps"; Liftosaur is "tap once to say done". [Secondary/Documented] The winning loop is: see the target, do the set, tap once, rest starts.
2. **Smarter defaults over more fields.** AP recommends each set and adjusts it for intra-session fatigue. Juggernaut drops a set after an RPE-10 set. Liftosaur and AP round weights to plates you actually have. [Documented]
3. **A lock-screen surface with the next set.** AP, Hevy (Android notification shows the next set and target reps), and Liftin' ("next set bigger and clearer") [Documented]. The lifter can rest without unlocking.
4. **Rest timer that adapts to context.** Hevy suppresses rest before drop sets; StrongLifts uses longer rest after a failed set and none between warm-ups; Juggernaut has separate main and accessory defaults; per-exercise timers exist almost everywhere. [Documented]
5. **Large, readable numbers.** Setgraph enlarged its digits in 10.2.0; Gravitus shipped a "lighter logging screen"; Liftin' enlarged the next set. [Documented] All three moved in the same direction recently.
6. **Calm guidance over commands.** AP's "checkpoint, not a command" framing. Gravitus's TREND and MAX give quiet context during the set. [Documented]
7. **Offline-first.** AP is fully offline [Documented]. The Boostcamp and RP complaints about bad gym signal show the cost of not being.

### 2.3 Anti-patterns users complain about

1. **No rest timer, or one that doesn't auto-start** (RP, Fitbod; Hevy discoverability). [Documented reviews]
2. **Unreliable set state.** RP checkboxes unchecking themselves; AP 6.3 blocking set completion; Boostcamp failing to register a completed workout. [Documented]
3. **Network dependence that loses data.** Boostcamp "network error and delete the information I just entered"; RP "struggles when gym signal is bad". [Documented reviews]
4. **Interruptions in the logging loop.** Paywall or donation popups per set (Boostcamp). RP's mid-workout feedback modals, with users asking for soreness to be collected once before the session. [Documented reviews]
5. **Small targets and accidental edits while fatigued.** Setgraph: "edited previous sets by accident while out of breath", wants a bigger add-set button. [Documented review]
6. **Silent changes to familiar behaviour.** Setgraph reversed the set order in an update. [Documented review]
7. **Background timer killed on Android.** Hevy needed Android 14 instructions; Juggernaut's timer notification "works inconsistently". [Documented]
8. **Rigid type combinations.** No weighted duration in Strong; no per-side logging in RP; no half reps in Hevy. [Documented reviews] Lengthened partials are not first-class anywhere I found.
9. **Laggy UI after logging.** Fitbod's delay before the list scrolls again after a set. [Secondary]
10. **Dark-only, low-contrast styling.** Juggernaut is "tough to read". [Secondary]
11. **Timer settings that don't persist.** Boostcamp users switch the timer off every week. [Documented review]

### 2.4 Implications for Hyperlog [Inferred]

- **Current set as a single hero row.** Show the set being worked as one large row with target (rep range, RIR, load) and last time. Make "Done as planned" the large bottom-anchored button within thumb reach. Edits are the exception path: steppers (±1 rep, ± plate increment) instead of a keyboard, and RIR as 0–4+ chips. Keep the full list available by scrolling, but collapsed.
- **Start rest on Done, and show it outside the app.** Use an Android foreground-service ongoing notification showing the countdown, the next set's target, ±15 s, and ideally a "Done as planned" action so a set can be logged from the lock screen without unlocking. Test against OEM battery killers.
- **Rest rules per set type.** No rest before drop or myo-rep mini-sets or before the second exercise in a superset; full rest after the superset round.
- **First-class Nippard set types.** A myo-rep cluster is one activation set plus a list of mini-set rep counts. A drop is a chained row with the load pre-reduced. Lengthened partials are a "+N partials" suffix on the set, not a separate set. No app surveyed models partials properly, so this is an opening.
- **Mid-workout prompts.** Don't pop modals during the workout. Collect feedback (pump, joints) as optional chips after an exercise ends, or once at the end.
- **Data safety.** Offline-first, every tap persisted immediately, an undo snackbar instead of confirmation dialogs, and no surprise reordering.
- **Calm visuals.** High contrast, large numerals, few colours, and one accent reserved for the actionable element.
