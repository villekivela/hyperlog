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
