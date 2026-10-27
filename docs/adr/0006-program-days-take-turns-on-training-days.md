# Program days take turns on training days

The program used to run as a sequence: the next day was whatever came next, whenever you trained,
and training days only estimated dates and picked the gym. The owner trains on fixed days that
differ: Monday at a well-equipped gym for 60 minutes, Thursday at a basic gym for 75, Saturday at a
basic gym for under 50. Their program is written for those days. As a sequence, a missed Monday
pushed Day A onto Thursday's gym and time, and every miss meant deciding what to do.

So with training days set, the program's days take turns on them (the rotation): each training day
takes the next day, and a missed training day uses its day up. Three days on three training days
always land on the same weekdays. A program week is a calendar week. A missed day is skipped, never
moved, and nothing carries over. A week with no session trains again, and after two or more empty
weeks an easy week comes first. Without training days the program still runs as a sequence.

These are scheduling rules, like progression, not coaching: they apply without Claude and without
an Accept, and ADR 0003's "no built-in rules" still holds for the coach. The coach line on Today
points at patterns (the same day missed again and again) and leaves the fix to a check-in.

## Considered options

- **Keep the sequence and let the coach move missed days**: every miss becomes a decision, the one
  thing the owner wanted gone.
- **Carry a missed day's main lift into the next session**: the next day usually has the same
  pattern already, and two benches in one session can't happen.
- **Tailor each session on the fly to the gym and the time**: progression needs the same exercise on
  the same machine week after week (ADR 0002), so the program is written for the week once instead,
  and the app checks the fit.
