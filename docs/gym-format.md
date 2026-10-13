# Gym file format

A gym file describes one gym and its equipment, so a whole gym can be set up at once: Settings →
Gyms and equipment → **Import gym** picks the file. Importing adds a new gym and never changes or
replaces one already there. The file is checked strictly first: every problem is listed with its
path (`equipment[2].serves[0]: unknown exercise "bb_row"`) and nothing is added until the file is
clean. `SPEC.md` (Gyms and equipment → Importing a gym) has the product rules.

## Example

A made-up gym:

```json
{
  "format": "hyperlog-gym",
  "version": 1,
  "name": "Harbour Fitness",
  "usualDays": ["monday", "thursday"],
  "pinPlates": [1.25, 2.5],
  "dumbbells": "1 2 3 … 10 12.5 15 … 50",
  "bars": [
    { "name": "Olympic bar", "kind": "barbell", "kg": 20 },
    { "name": "EZ bar", "kind": "ez_bar", "kg": 8 },
    { "name": "Swiss bar", "kind": "swiss_bar", "kg": 15 }
  ],
  "equipment": [
    { "name": "Leg extension", "kind": "stack", "serves": ["leg_extension"] },
    {
      "name": "Lat pulldown", "kind": "stack", "serves": ["lat_pulldown", "close_grip_lat_pulldown"],
      "ladder": { "kind": "pin", "unit": "lb", "plates": "10 20 … 200", "addOns": [2.5, 2.5], "pinPlates": false }
    },
    {
      "name": "Cable crossover", "kind": "stack", "cableStation": true,
      "ladder": { "kind": "pin", "unit": "kg", "plates": "5 10 … 100" }, "unconfirmed": true
    },
    { "name": "Leg press", "kind": "plate_loaded", "serves": ["leg_press"] },
    { "name": "Smith machine", "kind": "plate_loaded", "serves": ["smith_machine_squat"], "ladder": { "kind": "plates", "base": 15 } },
    { "name": "Squat rack", "kind": "rack_bench" }
  ]
}
```

## Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `format` | yes | Always `"hyperlog-gym"`. |
| `version` | yes | Always `1`. |
| `name` | yes | The gym's name. A gym of the same name already there stays; the import is a second one. |
| `usualDays` | no | The weekday plan: `"monday"` … `"sunday"`. Starting a session on one picks this gym. |
| `pinPlates` | no | The small loose plates that hang on any stack's pin, kg, one entry per plate. Default `[1.25, 2.5]`. |
| `dumbbells` | no | The dumbbell rack, as a weights shorthand (below) or a `steps` or `list` ladder. Default: 1 to 10 kg by 1, then 12.5 to 50 by 2.5. |
| `bars` | no | The gym's bars, each `{ "name", "kind", "kg" }`. `kind` is `barbell`, `ez_bar`, `trap_bar`, `safety_bar`, `swiss_bar` or `axle_bar`; a barbell, EZ, trap or safety bar's weight is what sessions at this gym load the shared plates on. |
| `equipment` | no | The equipment items, below. |

### Equipment items

| Field | Required | Meaning |
| --- | --- | --- |
| `name` | yes | What the item is called at the gym. |
| `kind` | yes | `stack` (a selectorised machine or cable station), `plate_loaded` (a leg press, sled, Smith machine) or `rack_bench` (racks, benches and stands, listed with the gym; no weights). |
| `ladder` | no | Its weights (below). A `stack` without one gets the default pin stack (pin steps of 5 kg from 5 to 100); a `plate_loaded` one the shared barbell plates from a zero start. A `rack_bench` has none. |
| `unconfirmed` | no | The ladder is a guess: the first time an exercise is done on it, the session asks for the weights printed on the stack. Default `true` without a `ladder`, `false` with one. |
| `serves` | no | Library exercise ids done on it, so "Which machine?" never asks for them here. Each id once per file; only machine, cable and Smith machine exercises for a `stack` or `plate_loaded` item. |
| `cableStation` | no | `true` makes this `stack` the gym's one shared cable station: every cable exercise at the gym defaults to it. At most one per file. |

### Ladders

- A **weights shorthand** string: weights separated by spaces or commas (a decimal comma such as
  `12,5` works), with `…` (or `..`) filling in the step before it up to the next weight:
  `"1 2 3 … 10 12.5 15 … 50"`. Taken exactly as written, in kg.
- `{ "kind": "pin", "unit": "kg" | "lb", "plates": …, "labels": { "kg": 1, "cut": true }, "addOns": [2.5, 2.5], "pinPlates": true }`:
  a pin stack. `plates` is a shorthand or a list in `unit`. `labels` (optional) says how an lb
  stack's kg labels are printed: to the nearest `kg` (default 0.1), or `cut` down to it. `addOns`
  are the machine's own built-in add-on plates in kg, one entry per plate. `pinPlates` (default
  `true`) says the gym's pin plates fit its pin.
- `{ "kind": "steps", "unit": "kg" | "lb", "start": 5, "step": 2.5, "count": 20 }`: an even stack.
- `{ "kind": "list", "kg": [4.5, 9, 13.5] }`: an explicit list.
- `{ "kind": "plates", "base": 15, "pairs": [20, 20, 10, 5] }`: a plate-loaded machine's empty
  weight and the plate pairs it takes, one of each pair per side; without `pairs`, the shared
  barbell plates.

A `stack` takes `pin`, `steps` or `list`; a `plate_loaded` item takes `plates`; `dumbbells` takes a
shorthand, `steps` or `list`.

Unknown fields are errors, so a typo never passes silently.
