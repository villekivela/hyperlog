# Coach test report: words-first screen (#32)

Tester: experienced lifter persona (`tester-brief.md`), on the owner's phone with the DEV build of
`coach-words-first-screen`, on 2026-10-15 around 22:35. Data was the owner's real data: Week 6 ·
Build, Day A next, gym Kalliokoski, and two Changes in force ("Hands: less grip for two weeks",
"Lighter-close-grip-bench").

Cleanup: I accepted one test Change ("TEST Sore hands…") and reverted it. Today is back to Week 6,
Day A, with the same prescriptions as before. Nothing went to the Claude app; I opened the share
sheet three times and backed out each time. The words field is empty again. The clipboard still
holds a broken test reply.

## First impression

Before touching anything, I read the screen as: "I tell Claude what's wrong and it changes my
program, and those two cards are changes it already made." The intro line does its job. I'd
tap "My hands hurt" first, because it's the first thing I can touch that matches a real problem.

Three things made me stop:

- I couldn't tell what the two cards actually change. "Hands: less grip for two weeks" is in
  force, but Today still has 2-Grip Lat Pulldown, so what did it do? "Lighter-close-grip-bench"
  reads like a file name.
- "Paste reply" sits in the bar before I've asked anything. Paste what?
- "Gym [✓ Kalliokoski]" with one chip looks like a setting I can't change, not a choice.

Compared with the old situation rows and limit chips, it's much calmer. Within a few seconds I
knew this screen is where I talk to Claude.

## Tap counts

| # | Scenario | Taps | Notes |
| --- | --- | --- | --- |
| 1 | First look | 1 | Coach tab |
| 2 | Sore hands, to the share sheet | 3 (+ typing) | Coach, My hands hurt, Ask Claude. A 4th tap picks Claude in the sheet. The keyboard opens on its own and Ask Claude sits right above it. |
| 3 | Reply back, accept | 2 | Review, Accept. The reply-ready card appeared on return without a tap. |
| 3 | Find it and revert | 3 | Coach tab, Revert, Revert in the dialog |
| 4 | Tell Claude how it's going | 2 (+ typing) | Tell Claude how it's going, Ask Claude |
| 5 | Bad reply | 1 to see problems, 1 to copy them | Paste reply, then the copy icon. Going back to Claude happens outside the app. |
| 6 | Short on time, other gym | 3 (+ typing) | Short on time today, tap into the field, Ask Claude. I couldn't pick the other gym at all. |
| 7 | Time off, Ease back in | skipped | No time-off line on Today: the owner trains regularly. |

## Issues

### Blockers

None. Every scenario reached the share sheet or a reviewed Change.

### Major

**1. A Change card doesn't say what it changes, and tapping it does nothing.** (Scenarios 3, 4)
With the test Change accepted there were three cards, two of them about hands. Each shows a
summary and an end date, and that's it. Tapping the card did nothing. Today showed Day A with
2-Grip Lat Pulldown untouched while "Hands: less grip for two weeks" was in force, so I could not
tell from Coach what that Change does on which day. In a gym this matters: before I hit Revert or
tell Claude "it's going fine", I need to know which exercises it swapped or cut. Otherwise I'm
reverting by title and trusting my memory of a review I saw a week ago. The review screen already
has the diff, so tapping a card could open the same view, read-only, with Revert at the bottom.

**2. Claude doesn't see what a Change in force does either.** (Scenario 4) The prompt lists each
Change in force as one line of summary, scope and accept date (`coach/AskClaude.kt`, lines
197–198), without its operations. "About 'Lighter-close-grip-bench': elbow's fine now" leaves
Claude guessing what was lighter, by how much, and on which slot. It also can't answer "can I go
back to normal?" with a Change, because no operation ends or replaces one. The lifter has to
work out that the answer means tapping Revert by hand. Tell Claude how it's going is the right
button, but the round trip behind it is half there.

**3. "At a different gym" has nowhere to go.** (Scenario 6) The Gym row shows only the gyms that
are set up. Here that's one chip, and tapping it doesn't deselect it. There's no "Somewhere else"
or "No gym" option. I typed "Not at Kalliokoski today, hotel gym with dumbbells to 30 kg", and
the prompt still sends Kalliokoski's full equipment list as the gym picked. Claude gets two
contradictory sources and may build around a cable stack that isn't there. When I travel this is
the moment I most need Coach, and it's when the app tells Claude the wrong thing.

### Minor

**4. The "asked" state never ends.** (Scenarios 4, 6) After I asked about the test Change and then
reverted it, Paste reply stayed filled. It stayed filled when I started a new request (short on
time) and after I cleared the words entirely. The rule that the filled button is the next step
stops meaning anything once you've asked once. The spec doesn't say when the asked state resets
(after an accept, a revert, or cleared words).

**5. A truncated reply gets a contradictory message.** (Scenario 5) I pasted "Here you go:
{"summary":"Hands","scope":{"from":…" (a reply cut off mid-copy). Coach said "1 problem.
Nothing changed; ask Claude to fix it, then paste again." over "no Change found: copy Claude's
reply with its JSON block". So which is it, ask Claude or copy again? The real fault is my copy.
The spec's wording is "No Change or workout found", while the app says "no Change found",
lowercase. With a cleanly invalid Change (blank summary, `to` before `from`, unknown slot "rows",
`by: 9`) all four problems showed by path and were easy to read. That part is good.

**6. The bad-paste panel doesn't tell you where to take the problems.** (Scenario 5) The copy
icon has no label. The panel doesn't say to paste the problems into the same Claude chat, and
Ask Claude would start a fresh conversation with the whole prompt again, which is the wrong
move. One line like "Copy these into your Claude chat" would settle it.

**7. With three Changes in force, the words field is below the fold.** (Scenario 4) The pass note
says the field shows with two cards. With three, the cold screen ends at the starters. In real
life three overlapping Changes happen easily (hands, a lighter lift, a travel week).

**8. The keyboard is still up when you come back from Claude.** (Scenario 3) The "Claude's reply
is ready" card shows, squeezed between the field and the keyboard. It worked, but half the screen
is keyboard when the only thing to do is tap Review.

**9. Short on time hard-codes 45 minutes.** (Scenario 6) If I have 30, I have to place the cursor
on "45" with a sweaty thumb and fix it. Hevy-style duration chips (30 / 45 / 60) would avoid the
text editing. Plan a workout (#33) has time chips, and this starter could share them.

**10. Revert leaves stale words and has no undo.** (Scenario 3) After reverting the test Change,
the field still read "About 'TEST Sore hands…': hands feel better, can I go back?". The dialog
explains the revert well. With no snackbar undo, though, a mis-tap on Revert means pasting the
Change again, assuming it's still on the clipboard.

**11. The review drops "In place of" on a slot that's already swapped.** (Scenario 3) Today shows
"In place of Barbell Bench Press / Smith Machine Bench Press". The review showed only Smith
Machine Bench Press with the RIR change, so at first I didn't know which slot it was.

## What works and must not be lost

- The intro line. One sentence and I knew what the screen is for.
- Starter, then the field focused, then Ask Claude right above the keyboard: three taps from
  "my hands hurt" to the share sheet.
- Finding the reply on return. The card appeared without a tap, and nothing opened by itself.
  That's the best part of the pass.
- The review diff: strikethrough before, new values after, "In place of" on the swap, and Reject
  and Accept at the bottom where my thumb is.
- Accept lands on Today with the change already applied, so I see what I'll train.
- The Revert dialog says in plain words what happens to the position and to sessions trained
  since.
- Invalid-reply problems listed by path, with nothing applied.
- The filled button always sits at the end of the bar.

## Spec gaps

- Viewing a Change in force: what it does, slot by slot, from its card on Coach.
- The prompt carrying each Change in force's operations, not only its summary.
- A way for Claude's reply to end or replace a Change in force (an operation, or a field naming
  the Change it supersedes).
- A gym option for "somewhere else / not set up" or "no gym", and what the prompt says then.
- When the asked state ends: after an accept, a revert, cleared words, or a new day.
- The message for a reply whose JSON is cut off, as opposed to one with no JSON.
- What Coach shows on return from Claude when the keyboard was open.
