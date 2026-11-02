# Tracing the technique clips (2026-11-02)

**Method:**
- **Tracing:** MediaPipe Pose Landmarker (heavy model, version 0.10.21).
- **Angles:**
  - Knee, hip, elbow and shoulder come from the 3D world landmarks, with the 2D value given second where both are shown.
  - Shin and trunk lean come from the 2D image, so they only hold for side views.
  - All are smoothed with a 0.25 s Savitzky–Golay filter.
- **Reps** are split on the driving angle with 15% and 85% bands.
- **Prototype numbers** were read from the rig after IK, in headless Chromium.

**Bias:** the 3D landmarks read straight joints as 10–30° bent (a bench lockout reads 29° in 3D and 3° in 2D). Read lockouts from the side-on 2D angle.

## Clips and trust

| Exercise | Clip | View | Trust |
|---|---|---|---|
| Squat | library UGwmUJkTIx8, 0–20 s | side | legs and trunk good; arms not usable |
| Bench | eWIZk_S_Bi0 | low side | legs and trunk good; elbow from 2D at the top, 3D at the bottom |
| RDL | z3Nk7sbHZSU | true side | best clip |
| Pulldown | X20GrB0mZbU | three-quarter from behind | legs and trunk good; arms off by 31–39° |
| Pressdown | library B5S2mbg0g5c, 0–11 s | side | very clean; use the 2D elbow |
| Leg press | V8D-OtRkfLo | oblique | legs good |
| Chest press | dYF2d_I24uE | front three-quarter | low; no trunk lean from the front |

## Per exercise

Angles are in degrees, real (from the clip) against the prototype.

| Exercise | Gaps over about 10° | Fix to the pose entry |
|---|---|---|
| Squat | Bottom: shin lean 47 against 31, trunk lean 23 against 14. The bar sits ahead of the shoulder joint; on a back squat it sits behind. | Bar about 10 cm further back on the trunk (`-0.07` along `f` becomes about `-0.17`) |
| Bench | Hip 16–22 against 45; shoulder at the bottom 42–59 against 71 | Flatter thighs; elbows tucked to 45–60° at the bottom |
| RDL | Bottom: hip 131 against 89, knee 61–71 against 30; the bar reaches mid-shin, not above the knee | Hinge to mid-shin; knees about 20° at the top and 45–55° at the bottom |
| Pulldown | Knee 96–101 against 70; shoulder at the bottom 12–18 against 64 | Knees at about 95°; pull lower, with the elbows down to the sides |
| Pressdown | Elbow at the top 117 against 64; knee and hip 5 against 25 | Forearm starts about 27° higher; stand straight |
| Leg press | Legs within about 5°; elbow 33–53 against 2 | Handles closer to the hips |
| Chest press | Handles 0.43 arm lengths below the shoulder against 0.04 | Handles about 20 cm lower, back to the shoulder line |

**Tempo:** demonstration reps don't keep a coaching tempo, and several clips lower faster than they lift. Keep tempo authored (#124).

## Retarget prototype (squat)

Page: `traced.html`, built by `bin/build_traced.py`. A Source switch offers Authored, Traced, and Traced at the clip's tempo.

- **How it works:**
  - The shin, thigh and trunk come from one side-on rep, smoothed and resampled to 31 samples over the lowering.
  - The feet are pinned, the hands held on the bar by IK, and the body turns about the ankle to keep the bar over mid-foot.
- **Jitter after smoothing:** 0.2–1.6°.
- **Foot slide:** the trace's foot landmarks drift 8–17 cm within a rep. Never take foot positions from a trace.
- **Balance correction:** −11° at the bottom with the prototype's bar placement, −4.5° with the bar 10 cm further back.
- **How it looks:** deeper than the authored squat, with more knee travel, and more natural at the bottom.

## As a tool step

- **Works for side-on lifts**, where the legs and trunk carry the look: squat, deadlift, RDL, lunge, hip thrust, leg press, calf raise, pressdown, curl, row.
- **Fails on:**
  - arms moving out of the camera's plane: raises, flyes, grip width, elbow flare;
  - front-only clips;
  - machines that hide the body;
  - lying lifts, where the far side is hidden;
  - edited videos. Only about a third of the library links have a usable side-on stretch.
- **Keep authored:** tempo, foot positions and every contact.
- **Proposed command:** `trace <clip> <time range>` writes angle keyframes; the checks flag an authored pose more than 10° off its trace.
- **A better source:** the owner filming each lift side-on, with one camera and no cuts.
