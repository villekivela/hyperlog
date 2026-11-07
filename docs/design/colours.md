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
| Fit orchid (`FitChipFill`) | A planned day runs over its training day's minutes | Only on the fit chip, behind dark ink |
| Plate colours | The kg competition colour of a real plate | Only on a drawn plate |

## The rules

- **One meaning per colour.** Amber is a weight coming down, and nothing else: the fit chip has an
  orchid of its own, 45° of hue from every other meaning (owner, #271). `ColourInventoryTest`
  fails if a screen borrows the trend's amber, and `FixedColoursTest` keeps the orchid 30° from
  every other hue.
- **The four reds are told apart by shape.** Red-family hues have four meanings: `error`, RIR 0,
  the drop set and the 25 kg plate. None stands alone: each keeps its own shape (a word or outline,
  a chip, a circle, a plate), and the shape is what is read, with a number or a symbol on all but
  the plate, so colour is never the only sign.
- **Plates are exempt.** A drawn plate takes its kg competition colour, because that is the colour
  of the plate on the floor, so plate colours may share a hue with any meaning.

The RIR scale's warm step (RIR 1–2) is amber too, the same amber as a falling weight in dark mode.
It is always a chip with its number, so it reads by its shape, as the reds do; whether it should
keep that hue is the owner's call.
