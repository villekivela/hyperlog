# Test report: Programs and Save for later (#84)

Tester: experienced lifter (8+ years, Nippard-style programs, used Strong, Hevy, MacroFactor
Workouts and RP). Debug build on the emulator, scripted coach, the app's seeded programs. 25 Oct 2026.

Starting state: Hyperlog Full Body running, week 7 (next: Day C). Programs held two saved programs
(Push pull legs, 5 days; Scripted full body, 2 days) and two past runs. For scenario 6 I cleared the
app's data (`pm clear`). The emulator is left with that cleared state, Scripted full body running
and three exported files in Downloads.

## Tap counts

| # | Scenario | Taps | Path |
|---|----------|------|------|
| 1 | Save a coach-built program for later | 5 (+1 to see it on the list) | Coach → New program → Build it from my answers → 3 → Save for later (→ snackbar's Programs) |
| 1 | Find it again later | 3 | Program → ⋮ → Programs |
| 2 | Start the saved program | 6 | Program → ⋮ → Programs → row → Start program → Replace |
| 2 | Look at the old program | 3 | ⋮ → Programs → its Past row |
| 3 | Run a past block program again | 6 | Program → ⋮ → Programs → row → Run again → Replace |
| 3 | Check an old session | 2 | History → session |
| 4 | Export a program | 6 | Program → ⋮ → Programs → row → Export → Save |
| 5 | Delete a saved program | 6 | Program → ⋮ → Programs → row → Delete → Delete |
| 5 | Try to delete a trained one | 4 | … → row: no Delete on the sheet |
| 6 | Start a saved program with nothing running | 5 from Today | Program → Programs → row → Start program (no dialog) |

The tap counts are fine. A program switch is a once-a-month action, and 6 taps with a confirm is right.

## Issues

### Major

**1. An exported coach program can't be imported by your training partner (scenario 4).**
I exported the program the scripted coach built ("Scripted full body, 2 days", the one that added
Barbell Drag Curl to my library). The file's Day B curl slot lists `"alternatives":
["barbell_drag_curl"]`, but the file has no `newExercises`. The keys are `format, title, blocks,
author, description`. The saved, never-started copy (Saved 24 Oct) exports the same way. My partner's
library doesn't have that exercise, and the parser checks alternatives against the library, so their
import should fail with "unknown exercise". The new exercise exists only on my phone. Sharing a
program with a partner is the reason to export it, and coach-built programs are the ones most likely
to bring a new exercise. The spec says Export "writes its file as imported", but a coach program
wasn't imported from a file, and the reply's `newExercises` never make it into the exported file.

**2. A program you replace can never be resumed where you left it (scenario 2).**
I was in week 7 of Hyperlog Full Body and started the saved program. The old run moved to Past as
"Ran 12 Oct – 25 Oct", and its only action is **Run again**, which starts at week 1, day 1. The
Replace dialog says "Past sessions are kept", which reads as "nothing is lost". It doesn't say that
week 7 is gone for good. In real training you pause a program all the time: a 4-week specialisation
block, a holiday on a hotel gym, a 3-week Claude program for a cranky shoulder. Afterwards you go
back to week 7, not week 1. Strong and Hevy don't have this problem because they don't run programs,
while RP and MacroFactor let you pause a meso and come back. Today the only way to "come back" is
Run again plus several Skip days or a Shift program, and that loses the Changes. At minimum, the
dialog should say you can't return to week 7.

**3. You can't look inside a saved program (scenarios 1, 2).**
The review shows the whole program: swaps, new exercises, blocks, weeks and days. Once I tap Save
for later, the sheet shows only the title, author, length, "Saved 25 Oct", the description and Start
program. The days, exercises and new exercises are gone. "A few weeks later" is exactly when I want
to re-read it: does it have a squat or a leg press, how many sets of bench, would it suit my gym? The
only way to see the program again is to start it, and starting replaces the program I'm running
(see 2). I'd want the same read-only view the review has, or the Program screen for that program,
from the sheet.

**4. The list isn't newest first, and identical titles make that worse (scenarios 1, 2, 3).**
The spec says newest first. After my save, Saved read 25 Oct, 3 Oct, 24 Oct. After I replaced
Hyperlog Full Body, the run I had just left (12 Oct – 25 Oct) went to the *bottom* of Past, below
1 Jun – 31 Aug. After Run again, Past read 25 Oct, 2 Sep, 1 Jun, 12 Oct. It looks sorted by when the
row was created, not by its dates. The seeded rows may exaggerate this, but the just-replaced run is a
real case. Meanwhile three rows were titled "Scripted full body, 2 days" and three "Hyperlog Full
Body", because the scripted coach always gives the same title and Run again copies it. Telling them
apart means reading every date line. The run I just left should be the first row under Past.

### Minor

**5. With nothing running, Today doesn't mention the saved program (scenario 6).** Program shows
**Programs** beside Import program, which works: 4 taps, and Start program needs no dialog. Today
offers only the starter's **Run this program**, Start session and Import a program. A lifter who
saved Claude's program opens the app on Today and sees a big button for a different program.

**6. A past run doesn't say how far it got or what was trained (scenarios 2, 3, 5).** "Ran
12 Oct – 25 Oct" doesn't say whether it stopped in week 7 or finished. It also doesn't say whether
it has sessions. Delete just isn't on the sheet, with no reason given, so on the trained run I
looked for a Delete that had silently gone. "Stopped in week 7 · 14 sessions" would answer both.

**7. History doesn't say which program or run a session belongs to (scenario 3).** The session list
and detail show "Day A — Heavy bench, squat, pulldown · Sat 17 Oct" with no program, run or week.
After Run again, the first and second runs' Day A sessions look identical. Comparing run 2 against
run 1 is the whole point of running a block again. The old sessions did read as before, so nothing
broke; it just can't be told apart.

**8. Export gives no confirmation.** After Save in the system dialog, the sheet is gone and nothing
says "Exported". Backing out of the dialog is clean, also silently, which is fine.

**9. Run again's dialog calls it "the new program".** "Replace Scripted full body, 2 days? … the
new program starts at week 1, day 1 instead." It's the same Hyperlog Full Body again. "Hyperlog Full
Body starts again at week 1, day 1" would read right.

**10. Empty runs clutter Past.** Starting a saved program and replacing it the same day leaves
"25 Oct – 25 Oct" with no sessions on Past. It can be deleted, but a one-day range would read better
as "25 Oct".

**11. You can't stop a program without replacing it.** The ⋮ menu has no Stop or Pause, so the
nothing-running state (scenario 6) is only reachable on a fresh install. I had to clear the app's
data to test it. That ties into issue 2.

**12. Small oddities.**
- Back from Programs, when you opened it from Coach's snackbar, lands on Program rather than Coach.
  That's acceptable, but you don't land where you came from.
- The ⋮ menu offers "Run Hyperlog Full Body" while Hyperlog Full Body is the running program, right
  above Programs, which has its own Run again.
- The seeded "Push pull legs, 5 days" exported as `push-pull-legs-5-days.json`, but the file's
  `title` is "Scripted full body, 2 days" and its body is the 2-day program. This is probably a test
  fixture, but worth checking that Export writes the row's own program.

## What works and must stay

- **Save for later is one tap with no questions.** The check-in ends, Coach says "Saved to your
  programs" with a Programs action, and the running program is untouched (still week 7, Day C next).
- **New exercises wait.** The library stayed at 213 while the program was saved and went to 214 only
  when I started it.
- **The Replace dialog names what you lose:** "You're in week 7 of Hyperlog Full Body … Past sessions
  are kept." Starting with nothing running doesn't ask, which is right.
- **One filled button per sheet**, with the words the review uses (Start program, Run again). Running
  sheet: "You're in week 1" and Export only. There's no way to delete something trained on.
- **The Delete dialog** says "It leaves your programs. Nothing was trained on it." That's exactly what
  you want to read before deleting.
- **Export** names the file from the title and uses the system save dialog. Backing out leaves no
  trace.
- **Running / Saved / Past with distinct symbols** reads at a glance, and the rows have no buttons to
  mis-tap.
- **No program running:** Programs sits beside Import program, plainly visible.

## Spec gaps

- **Pausing and resuming** a program: replacing it now ends the run for good (issue 2), and there is
  no Stop (issue 11).
- **Viewing a saved or past program's content** from Programs without starting it (issue 3).
- **What Export writes for a coach-built program:** its `newExercises` (or the full exercise
  definitions of anything not in the stock library), so the file imports on another phone (issue 1).
- **How the list is sorted** when "newest" is ambiguous: by the saved date or start date shown, or by
  the most recent activity (issue 4).
- **Telling programs with the same title apart:** rename, or a run number ("Hyperlog Full Body ·
  run 2").
- **Linking History to runs:** a session showing its program and week, and a past run's sheet leading
  to its sessions (issues 6, 7).
- **Today with nothing running** while saved programs exist (issue 5).
