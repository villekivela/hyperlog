# Colours

Every colour in the app and what it means, on every screen. `ColourInventoryTest` fails if a screen
writes a colour of its own, and `FixedColoursTest` keeps the fixed scales 30° of hue from the accent.

Graphite (`ui/theme/GraphitePalette.kt`) is the base: greys with a faint cool bias, and an accent with
no hue. Anything with a hue carries information.

| Colour | Means | Drawn as |
|---|---|---|
| Surfaces (`surfaceContainer`, `surfaceBright`, `surfaceContainerLowest`) | Nothing: they divide the screen into sections | Screens and cards |
| `primary` | What you act on, or what is selected | Active set, text buttons, section heads, chart ink |
| `primaryContainer` | The one thing to do on a screen | Start, Resume, the next action, a picked option |
| `secondaryContainer` | A place for an exercise or a setting's symbol | Symbol circles |
| `tertiary` | A departure from the plan as written | Deload, swap, technique text, a superset's label, easing back |
| `error` | Something is wrong and needs you | Flagged weight, a problem in a file |
| RIR scale | How hard a set was, or should be | Only on the RIR chip |
| Trend green, grey, amber | The weight went up, stayed, came down | Only with a trend arrow |
| Technique colours | Which intensity technique a set uses | Only on the index circle, its pill and the Next row's fill |
| Plate colours | The kg competition colour of a real plate | Only on a drawn plate |

## Where one hue serves twice

Red-family hues have four meanings: `error`, RIR 0, the drop set and the 25 kg plate. They never stand
alone. Each keeps its own shape (a word or outline, a chip, a circle, a plate), and that shape is
what is read, with a number or a symbol on all but the plate, so colour is never the only sign.

Amber has two: a weight coming down, and the fit line's time chip (`FitChipFill`, the owner's call on
2026-10-27). This is the one second meaning that isn't told apart by shape alone.
