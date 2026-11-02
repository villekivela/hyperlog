# Training style wizard test report (#133)

Emulator-5554, debug build, branch feat/133-guide-the-training-style. Start values: Goal Both,
Custom 10-16 Rising, Failure Any lift, Calibration off, reps 6-8 / 10-15, age 30-39, months + break,
aches Elbows/Knees with notes, avoid EZ-Bar Skull Crusher, favour Hack Squat. All restored at the end.

## Tap counts
- Scenario 1 (all defaults): Settings > Training style > Set it up again = 1 tap on the page, then 9 Next + 1 Done =
  10 taps through the wizard, 11 in all. Done lands on the Training style page.
- Change one answer from the summary: tap the row (1), change, then 3 Next taps to get back to the summary
  for a step-7 edit (step 7 -> 10). Edit costs 1 + change + (10 - step).

## Issues
Blocker: none.

Major
1. (3) A typed rep range that is not whole is dropped without a word. Typing 12 into Compounds 6-8
   shows the red "Each end 1-30, the fewest not above the most", but Next moves on, and on Back the
   field is back to 6-8. Raising a range (6-8 to 12-15) means typing the high end first; nobody
   knows that, so a lifter types low first, sees red, taps Next and loses the entry. Custom volume
   is the same field type (not tried invalid). Suggest: Next stays and keeps the error, or a short note
   "Kept 6-8" on the next step. Spec says it is as on the page, so this is also a spec gap.
2. (5) After a jump from the summary, Next walks every step in between (7 to summary = 3 taps,
   2 to summary = 8). Hevy-style "Back to summary" would save the walk. Edit-in-place is the
   common use of the summary.

Minor
3. (4/8) The summary shows ache areas only ("Shoulders, Elbows, Hips, Knees"), not the notes;
   a summary that exists to be checked before Done should show them (or the count of notes).
4. (8) Returning from How I train resets the summary to the top; I had scrolled to the bottom.
5. (8) How I train switches save the instant they flip (Stretch-biased stayed off after the wizard's
   x), though x "leaves without saving". Back from How I train and x both leave it changed. Say so
   on that screen, or treat the switches as a draft.
6. Step 3 (How hard): a ~40px blank gap between the Failure line and its options, also on the page.
7. Landscape (7): the content area is about one row tall, so the Volume fields need a scroll.
   Usable, not good. (In portrait the Next bar rides above the keyboard, which is right.)
8. Two things called "Anything else" (step 9 is About me's line, How I train has its own).
   Step 9 title "Anything else Claude should know?" helps, the summary row "Anything else" does not.
9. After editing the high volume field on the page, the line "toward 18" stayed until the field
   lost focus (page behaviour, not new).

## What works well
- (2) Goal Strength, custom 12-18, tapped Next with the keyboard open: summary showed 12-18, Done saved
  12-18. Next commits a whole range as the spec says. System Back also stepped back and kept it.
- (4) Two aches with notes typed and Next tapped without leaving the field: both saved. Picker has
  search, opens above the keyboard, picks in one tap, and an exercise appears at once in the list.
  Avoid and favour both landed on the page.
- (6) x mid-way and top-bar Back from step 1 (also system Back from step 1) saved nothing; goal,
  volume and Hips were unchanged on the page.
- (7) Rotated to landscape mid-step with a number typed (7-18): kept in both orientations.
- (9) Not checked: I saw no "115%" text; Anything else read "6am sessions on an empty stomach, 75 minutes tops".
- Layout matches the check-in look; "N of 10" with Next/Done is clear; the summary rows read well
  ("12-18 sets a week - Build up each week").

## Spec gaps
- What a non-whole range does on Next, and whether the user is told (issue 1).
- Is there a way back to the summary after a jump (issue 2)? Not stated.
- Do How I train's switches follow "Answers are a draft" or save at once (issue 5)? The spec says
  "kept as they change" but x says "leaves without saving".
- Untested: whether Age or Where are you now? can be cleared once chosen (no visible "Not said" choice).
- Landscape is not mentioned; the wizard works but is cramped.
