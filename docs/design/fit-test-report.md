# The fit: designer and tester pass (#88)

Rendered on Robolectric at the phone's size (412 × 915 dp, xxhdpi), light and dark, by
`FitRenders` (`RENDERS=docs/design/screens ./gradlew testDebugUnitTest --tests '*FitRenders*'`, `DARK=1` for dark).
The emulator came up, but a seeded program with training days, minutes and a gym is easier to
reach in a render than by tapping through the sheets. Not tried on the owner's phone yet.

Screens: `light-fit-today-cards.png`, `dark-fit-today-cards.png` (the hero and a planned card),
`light-fit-program.png`, `dark-fit-program.png` (a dated day), `light-fit-review.png`,
`dark-fit-review.png` (the New program review).

## Designer

- **Contrast.** Amber text measured 3.27:1 on the light hero, 2.65:1 on the dark hero and 2.13:1 on
  a light card. The owner chose an amber chip with dark ink: ink on amber is 8.3:1, the same in both
  themes and on every card. Fixed; `FitChipTest` holds the amber and the 4.5:1 floor.
- **Review read the wrong date.** With two days on three training days Day A comes twice in the
  week and the review read its last date (Saturday, no minutes, no gym), so it showed nothing. It
  now reads the first. Fixed, with a test.
- **Swaps line touched the first slot card** in Program and the review. 8 dp under it now.
- The chip's left edge sits on the card's 24 dp text edge and the swaps line under it on the same
  edge; the chip is 28 dp inside a 48 dp tap target.

## Tester

- The chip doesn't look tappable: nothing says it opens a check-in. Left as the spec has it; a
  taste question for the owner (a trailing chevron or an `ask` symbol would say it).
- On the planned card the line sits under the numbers where the eye is already, and says what is
  wrong in the words the check-in opens with: good. On the hero it pushes Start session down by
  about 80 dp, which is tolerable.
- A day with both lines shows the chip, then the swaps, which reads as most to least urgent.

## On the owner's phone (2026-10-28)

The PR #93 debug build (`com.hyperlog.app.dev`, 1280 × 2856 at 480 dpi, unlocked, the app's own dark
theme; the app has no theme toggle of its own, so light stayed on the renders above and the system
setting was left alone). The dev data was copied off first and restored byte for byte after
(`sha256` equal); the Claude key was never used, the Scripted coach ran the check-ins. Training days
set to Mon 30 min, Thu no limit, Sat 35 min, then Thu 30 min; two exercises (and later High-Bar Back
Squat) marked missing at the only gym, all in the dev data that was put back.

| Criterion | What the phone showed | Screenshot |
| --- | --- | --- |
| Time chip on a planned card, ending in a chevron | `~45 min · Saturday has 35 ›`, amber with dark ink, a 48 dp target | `screens/phone/dark-fit-today-card.png` |
| Training day without minutes is never checked | Thursday with no limit: no chip on the hero, only the swaps line | `screens/phone/dark-fit-hero-swaps-only.png` |
| Chip on the hero, with the muted swap line below | `~50 min · Thursday has 30 ›`, then `2 swaps at Kalliokoski`; the chip is legible on the purple | `screens/phone/dark-fit-hero.png` |
| Tapping it starts a check-in | Hero and card chips both opened Check-in; the saved opening read `Day A runs ~50 minutes but Thursday has 30.` and `Day B runs ~45 minutes but Saturday has 35.` The scripted coach ignores the opening and plays its first canned question, so no reply to it was seen | `screens/phone/dark-fit-checkin-opened.png` |
| Program's dated days | Under the day heading: the chip, then the swaps line | `screens/phone/dark-fit-program.png` |
| New program review | `1 swap at Kalliokoski` under Day A, read-only, no chevron. Day A is under the 35 minutes of Thursday on this data, so the review showed no time chip; its chip is covered by the render and `ProgramReviewTest` | `screens/phone/dark-fit-review.png` |

Findings:

- Nothing needed fixing: the chip and the lines sit where the renders put them, and the chevron
  reads as a button.
- A gym with no exercise marked missing shows no swap line, so the lines only appear after a gym has
  learned what it lacks ("Not at <gym>"); a first look at a fresh install shows the chip alone.
- The check-in context for the day's fit carried the training days and the lift goal
  (`Barbell Bench Press: 110 kg`), read from the saved check-in.
