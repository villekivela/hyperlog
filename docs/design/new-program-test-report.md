# New program check-in: test report (#75)

Emulator, debug build, scripted coach (canned replies, so the flow is judged, not Claude's reading).
Tester: 8+ years lifting, runs Nippard programs, used Strong, Hevy and RP.

## Tap counts

| # | Scenario | Taps |
|---|---|---|
| 1 | Coach → New program → Build from my answers → 3 → Start program → Replace → Today | 6 |
| 2 | Coach → New program → Attach → pick small PDF → 3 → review | 5 |
| 3 | Attach → 25 MB PDF (warning shown) → Attach → photo | 2 + 2 |
| 4 | Reject → typed reason + Send → review again → × | 1 + 2 (plus typing) + 1 |
| 5 | Relaunch → Coach → 3 (bounced back) → Attach → photo → 3 → review | 6 |

## Issues

### Major

**1. After a restart, answering silently throws you back a step (scenario 5).** I attached the photo,
force-stopped, reopened and went to Coach. The "How many days" step was there as I'd left it. I
tapped 3, and the check-in jumped back to "Do you have the program as a file?" without a word.
It looked like the app had eaten my answer. Spec says this is deliberate (the file isn't stored),
but nothing tells you why. Say it on the restored step itself ("Hyperlog was closed, so attach
Whiteboard.jpg again"), or open on the attach step with that line, before I answer anything.

**2. A restored review takes over the app at launch, and Reject from there lands on Today
(scenario 5).** I reached the review and force-stopped. On relaunch the review covered the whole
app before Today showed. My next tap went to Coach's spot in the tab bar, which is under Reject. That
rejected the program and dropped me on Today. The check-in had quietly stepped back to "How many
days" with Type my own open, and I only found that by opening Coach. In the gym I open the app to
log a set, not to judge a 12-week program. Either open the review only from Coach, or have Reject
land on the check-in.

**3. × on the review throws the whole program away with no confirm (scenario 4).** With a key,
that review is a paid Claude read of a 30-page PDF plus several answers. One stray tap on the top
corner loses it. Reject is safe because it steps back. × should ask first, or be undoable.

**4. The attached file leaves no trace (scenarios 2, 5).** After picking the PDF the next question
appears, and nothing says "Nippard-Upper-Lower.pdf attached". Back shows the file step with nothing
picked, and the review never says what it was built from. Spec says the check-in keeps the file
name as the answer, but I never saw it on screen. When importing someone else's program I want
proof it read *my* file: "From Nippard-Upper-Lower.pdf" on the review's head card.

**5. Reject asks the wrong question (scenario 4).** Reject opens the last step, "How many days a
week can you train?", with an empty "Your answer". I wanted to say "no barbell curls", which answers
a different question. Change the field's hint to what I'm actually saying ("What should change?").

### Minor

**6. The new exercise floats free.** "New in your library: Barbell Drag Curl" appears, but no week I
browsed uses it. It is only the curl's alternative, and the review doesn't say so. Say where it goes:
"Alternative to Barbell Curl".

**7. "Next" on Day A of a program that isn't running.** The review reuses Program's day header, so
week 1 Day A reads "Next" before I've accepted anything. Drop it on the review.

**8. The Build card contradicts the deload week.** Week 4 days are tagged Deload at 3–4 RIR, but the
block card above still says "Every set: 1 RIR closer to failure · techniques back".

**9. Rows on the review don't open.** I can't see a slot's technique note, rest or alternatives
before committing. For a Nippard import the notes (lengthened partials, myo-reps, which set gets a
drop) are the program.

**10. Training days aren't set after Start program.** The check-in asked how many days I train,
but Program then says "Set your training days" and Today shows day A only on Saturday. Ask
for the days in the check-in, or put the line right after Replace.

**11. Today doesn't say a new program started.** Start program lands on Today with no message
and no program name; only "Week 1 · Ramp-in" changes. One line like "Started Scripted full body"
would settle it.

**12. Coach reopens a stale check-in.** The first time I opened Coach this session, it went
straight into a half-finished New program check-in from earlier. Show it on the tiles as "Carry on:
New program" rather than dropping me inside it.

Test-harness note, not a product bug: after one Start program, the scripted coach can't propose
again. Its Barbell Drag Curl now clashes by name with the library, and its self-fix reply ("Nothing
in this program has an alternative to swap to") is a Change-my-program answer. I renamed the
library copy to carry on.

## What works and must not be lost

- One question a screen with big rows: fast and thumb-friendly.
- The 25 MB warning is excellent: in place, says why, says what to do instead (photos of the pages).
- The review's head card (title, author · weeks · days a week, description) and the swaps row
  ("Seal Row → 1-Arm DB Kroc Row") are exactly how a lifter reads an import.
- Browsing blocks and weeks in the review like Program, with the RIR chips and the deload tag.
- The Replace dialog: names the old program and week, says past sessions are kept.
- Reject keeps the check-in going instead of dumping me back to the tiles.
- Nav bar hidden during the check-in, so there are no stray tab taps.

## Spec gaps

- **Several photos per answer.** The 23 MB message says "photos of the pages that matter", but a
  Nippard program is 5–10 pages of tables and one answer takes one file. Spec should say whether
  several images can go in one answer, or how to add pages one by one.
- **Starting mid-program.** Start program always runs from week 1, day 1. Someone moving an
  in-progress Nippard program over wants to start at week 5.
- **Units and loads from the file.** Nippard sheets carry lb loads, % of 1RM and "last-set
  intensity technique" columns. Spec doesn't say what happens to loads or 1RM percentages.
- **The source on record.** Whether the program keeps which file it came from, for later "what did
  the PDF say here?" questions.
- **Restore behaviour across screens.** Where a restored review appears (any screen or only
  Coach), and where Reject lands when it wasn't opened from Coach.
