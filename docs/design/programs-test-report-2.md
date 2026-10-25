# Test report 2: saved program page, Resume, Today's offer, Export (#84)

Tester: experienced lifter (8+ years, Nippard-style programs, used Strong, Hevy, MacroFactor
Workouts and RP). Debug build (`com.hyperlog.app.dev`) on the emulator, font scale 1.15, 25 Oct 2026.

Data was set up in the app. I ran the starter, finished one real session (Day B: RDL 100 × 7) and
skipped two days, so it was in week 2. Saved programs came from the scripted coach ("Scripted full
body, 2 days", new exercise Barbell Drag Curl) and from Coach's **Paste reply** ("Arm block, 2 days",
new exercise Waiter Curl, typed into a field and copied). I imported a small file built from
`TestPrograms.twoDays` ("Two-day test block"). I cleared the app's data twice for scenarios 4 and 2.
The emulator is left cleared, and my test files are removed from Downloads.

## Tap counts

| # | Scenario | Taps | Path |
|---|----------|------|------|
| 1 | Save a coach program for later | 5 | Coach → New program → Build it from my answers → 3 → Save for later |
| 1 | Open it | 4 | Program → ⋮ → Programs → row |
| 1 | Start it over the running one | 2 (6 from Program) | Start program → Replace |
| 2 | Start a saved program, nothing running | 2 | Today → row → Start program (no dialog) |
| 2 | Start a saved program, the running one finished | 3 | Today → row → Start program → Replace |
| 3 | Import over the running program | 4–6 | Program → ⋮ → Import program → Replace → (roots → Downloads) → file |
| 3 | Resume the program left in week 2 | 6 | Program → ⋮ → Programs → row → Resume → Replace |
| 4 | Export a coach program | 6 (7 from a saved page) | Program → ⋮ → Programs → row → Export → Save (saved: row → ⋮ → Export → Save) |
| 4 | Import that file on a fresh app | 3 | Today → Import a program (only opens Program) → Import program → file |
| 5 | Delete a saved program | 7 | Program → ⋮ → Programs → row → ⋮ → Delete → Delete |

These counts are fine for once-a-month actions. Starting a saved program from Today in 2 taps is
very good.

## Issues

### Blocker

None. Every scenario completed, and the data came out right.

### Major

**1. Program ⋮ → "Run Hyperlog Full Body" starts it again from week 1 while you could resume it
(scenario 3).** I left Hyperlog Full Body in week 2 by importing Two-day test block. The most
obvious way back is the ⋮ item named after the program, right above Programs. It asks "Replace
Two-day test block? … the new program starts at week 1, day 1 instead." That runs it from scratch.
Programs has the right action (Resume, picks up at week 2), but the menu item doesn't mention it or
lead there. Coming back to a paused block is exactly when I'd tap a menu item with the program's
name. Tapping it repeats weeks I already trained and adds another "Hyperlog Full Body" row to Past.
The old run can still be resumed, so it's recoverable, but only if I notice. The menu item should
resume a run that was left mid-way (or open its sheet with Resume first), or say "starts again".

### Minor

**2. The Past rows don't show which runs can be resumed (scenario 3).** On the list, Hyperlog Full
Body (left in week 2, with a session), Scripted full body (started and replaced before any
training) and Arm block (finished) all read "… · 25 Oct". Only the sheet says "Left in week 2".
When I come back to pick up a paused block, I want the row to say "Left in week 2" or "Finished".
That would also tell the same-titled rows apart.

**3. The Replace dialog doesn't say you can come back.** "You're in week 2 of Hyperlog Full Body;
the new program starts at week 1, day 1 instead. Past sessions are kept." Resume exists now, but
the dialog still reads as if week 2 is gone. "You can resume it from Programs" would remove the
worry that made pausing a program feel risky last round.

**4. The saved program page leaves out what the review showed (scenario 1).** The page is good
(see below), but the review's "Swapped for your library: Seal Row → 1-Arm DB Kroc Row" is gone.
Weeks later, a swap is exactly what I'd re-check before starting ("did it turn my seal row into a
Kroc row?"). Also, "New in your library: Barbell Drag Curl" reads as if it's already in the
library, but it joins only on Start. And the page never shows where the drag curl is used: it's
only an alternative to Barbell Curl on Day B, and alternatives aren't shown. Tapping the row does
nothing.

**5. "Next" on a program that isn't running.** The saved page and the review tag Week 1 · Day A as
"Next", although nothing is next until I start it.

**6. The dialogs say "the new program" and "day 1".** "Hyperlog Full Body picks up at week 2,
day 1": the program calls that day "Day A", so I had to translate. Start from a saved page says
"the new program starts at week 1, day 1" instead of naming it ("Scripted full body starts…").
The Run again dialog now names the program correctly.

**7. Resume is offered on a run with no sessions.** Two-day test block was replaced after one Skip
day and no training. Its sheet offers Resume ("Left in week 1") above Run again. SPEC says a run
replaced before its first session has only Run again. Resuming one skip is harmless, but the
behaviour doesn't match the spec. Pick one and write it down.

**8. A finished run and an abandoned one read the same.** The sheet for Arm block (finished) says
only "Ran 25 Oct", the same as Scripted full body (never trained). "Finished" belongs there.

**9. Today's "Program finished" doesn't say which program finished**, and it offers no Run again
for it. It works ("Start a saved program below, or train ad hoc."), but "Arm block, 2 days
finished" plus a Run again would be what a lifter expects after a block.

**10. With nothing running, the starter outranks my own saved program on Today (scenario 2).**
"Hyperlog Full Body · Run this program", the only filled button, sits centred, and "Your saved
programs" sits at the bottom. With nothing running, one tap on the big button starts the starter
without a dialog. I just saved Claude's program on purpose, so I expect it to be the offer. A
taste question for the owner: should a saved program take the starter's place once one exists?

**11. A saved program you start and then replace can't be viewed or deleted.** Scripted full body
was saved, started, and replaced before any training. It became a Past row with only Run again and
Export. Its read-only page is gone, and there is no Delete, although nothing was trained on it.

**12. No feedback after Export or Import.** Export still closes with no "Exported" message. Import
of a file that carried Barbell Drag Curl added it to the library (213 → 214) silently. "Added
Barbell Drag Curl to your library" would save a trip to Settings.

**13. Small things.**
- Today's "Import a program" only switches to Program, where you tap Import program again.
- Start program from a saved page opened from Programs lands on Program (Back → Today), not on
  Programs as SPEC says ("staying where the page was opened"). From Today it stays on Today. Landing
  on Program seems better to me; the spec should say which.
- Replacing a *finished* program still asks "Replace Arm block, 2 days?". The wording ("The new
  program starts at week 1, day 1.") is fine, but nothing is lost, so the question is odd.
- Out of scope: after I chose Don't allow, the notification permission prompt came back the first
  time I opened Coach → New program, after each data clear.

## What works and must stay

- **The saved program page** answers what I need before I start: title, author, weeks and days a
  week, when I saved it, the description, the new exercise, and every block with each week's days,
  sets, rep ranges and RIR ("Build · weeks 2–4: every set 1 RIR closer to failure"). Start program
  sits at the bottom, with Export and Delete in ⋮. This fixes last round's issue 3.
- **Resume works.** Hyperlog Full Body came back at Week 2 · Day A with its 2 Changes in force,
  Day A of week 1 marked Skipped and Day B showing the RDL session (Sun 25 Oct, 100 × 7). It did
  this twice, after a saved program and after an import. "Ran 25 Oct · Left in week 2" on the sheet,
  with Resume filled above Run again, makes the choice clear.
- **The Resume dialog says where you land:** "Hyperlog Full Body picks up at week 2, day 1 instead."
  The Run again dialog says "starts again at week 1, day 1". The two are easy to tell apart.
- **Today's saved programs:** 2 taps from Today to a running program, with no dialog when nothing
  runs. After a finished program, the line reads "Start a saved program below, or train ad hoc."
- **Export carries `newExercises`.** The coach program's file has `format, title, blocks, author,
  description, newExercises` (barbell_drag_curl with all its fields). The pasted program's file
  carries waiter_curl the same way. Exported from a saved page, from a past run, and after the
  exercise had already joined the library, the file is byte-identical. On a fresh app, the file
  imported cleanly, and Barbell Drag Curl appeared in Settings → Exercise library (214 exercises).
  This fixes last round's issue 1.
- **New exercises wait** until Start: Waiter Curl and the drag curl joined only when their programs
  started.
- **Delete** sits on the saved page's ⋮, and its dialog ("It leaves your programs. It was never
  started.") is exactly right.
- **The Programs list** is now newest first (the run just left is at the top of Past).
- **The paste path** opens the same review with Save for later, with no key.

## Spec gaps

- **What Program ⋮ "Run <starter>" does** when that program has a resumable past run (issue 1).
- **Past row text:** whether a row says "Left in week N" or "Finished", so resumable runs show on
  the list (issues 2, 8).
- **Resume eligibility:** whether "trained into" means a session or any move of the position, such
  as a Skip day (issue 7).
- **The saved page's content:** the swaps, and where each new exercise is used (issue 4).
- **A saved program started and replaced untrained:** whether it goes back to Saved, keeps its page,
  or can be deleted (issue 11).
- **Where Start program lands** from a saved page opened from Programs (issue 13).
- **Today when a saved program exists and nothing runs:** which offer leads (issue 10).
- **A finished program on Today:** name it and offer Run again (issue 9).
