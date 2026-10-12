# Gyms, weight ladders and the weight check: design note

Settings → Gyms and equipment, a gym's equipment, the ladder editor, and the two places a ladder
shows in a session: the pad's weight strip and the weight check. It extends `logging-screen.md`
and `exercise-library.md`: same margins, type scale, symbols and theme roles. Screenshots are in
`screens/gyms-*.png`.

## Principles

1. **Weights are always real.** Every ladder is shown as the kg values it yields
   (`1.1 · 3.4 · 5.7 · 7.9 … 89.6 kg`), never as the start/step that produced them. An lb stack
   reads in kg everywhere.
2. **A list, not cards.** Gyms, then their equipment, one row each: name, then the ladder preview
   in tabular figures. Add rows sit at the end of their list in `primary`.
3. **Shared is visibly shared.** Dumbbells and barbell plates sit below a divider under "Shared by
   every gym", in the same Gyms screen, so there is one place to edit them.
4. **The editor shows its result live.** "Real weights" under the fields updates on every
   keystroke; Save stays disabled until the fields make a ladder.
5. **The check speaks in the strip.** The weight check replaces the pad's strip with the reason
   and three real weights, and colours the weight cell and ✓ in `error`. Nothing pops up.

## Tokens

| Token | Value | Use |
| --- | --- | --- |
| List row | ListItem, 8 dp extra inset | Name bodyLarge; preview bodyMedium + tnum, onSurfaceVariant |
| Text column | 64 dp | Names, add-row labels and the "Shared by every gym" label line up |
| Section label | labelLarge onSurfaceVariant, 16 dp above, 8 dp below, divider above | "Shared by every gym" |
| Editor fields | OutlinedTextField, 24 dp margin, 16 dp between | Start / Step / Weights share a row, 8 dp gaps |
| Preview | titleMedium + tnum, then bodySmall count | `outline` "Fill in the fields…" while incomplete |
| Check reason | labelLarge `error`, warning symbol 20 dp | Left 1.5 parts of the strip |
| Check offers | 40 dp pill, 1 dp outlineVariant border | Three, one part each |
| Check ✓ | `error` / `onError` | The next ✓ keeps the weight |

## Layout

```
 ←  Gyms and equipment          ←  Kallio            ✎ 🗑      ✕  Cable station       🗑  Save
      Kallio                 >        Cable station        >    [ Name                        ]
      1 equipment item                1.1 · 3.4 … 89.6 kg       [ Stack | List | Plates ]
  +   Add gym                     +   Add equipment             ( lb stack: 2.5, then 5 lb steps )
 ─────────────────────────                                      [  kg  |  lb  ]
      Shared by every gym                                       [Start] [Step] [Weights]
      Dumbbells              >                                  Real weights
      2 · 4 · 6 · 8 … 60 kg                                     1.1 · 3.4 · 5.7 · 7.9 · 10.2 · 12.5 … 89.6 kg
      Barbell plates         >                                  40 weights
      20 kg bar · 20 20 20 15 10 5 2.5 1.25

 Pad strip during a weight check:
 ⚠ 10× last set   ( 22.5 ) ( 25 ) ( 30 )
```

## States

- Gyms: no gyms (only Add gym and the shared ladders); gyms with item counts.
- Gym: empty (Add equipment only); items with previews. Rename and delete in the top bar; delete
  asks first and says the equipment goes with it.
- Ladder editor: new (Save disabled, preview "Fill in the fields…"), stack in kg or lb, list,
  plates, existing item (delete asks first). Dumbbells offer List and Stack; plates offer Plates
  only.
- Session strip: on the weight, the five rungs around the typed weight; during a weight check,
  the reason and three offers.

## Interaction rules

- Adding a gym asks for its name in a dialog and opens the new gym.
- The lb stack chip fills unit lb, start 2.5, step 5, 40 weights; every field stays editable.
- Lists and plate pairs are numbers separated by spaces; a decimal comma (`2,5`) works.
- Setup uses the system keyboard: it is done once, away from a set, and needs text and lists.
- A weight check shows even with the pad hidden. Tapping an offer puts it in the weight and clears
  the check; the next ✓ logs. ✓ again on the unchanged entry keeps the typed weight, but not
  within 1 s of the warning (a stutter isn't a decision).
