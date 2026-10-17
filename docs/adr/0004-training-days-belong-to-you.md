# Training days belong to you, not the gym

The weekday plan used to be set on each gym (Gyms and equipment → the gym → Usual days). It moved
to one plan for the app: the weekdays you train on, each with an optional gym, set from Program.
Your week describes you, not a place. With one gym you had to open its equipment page to say when
you train, and with no gym set up you couldn't have training days at all, so time off on Today had
nothing to count from.

The gym default and time off both read this one plan. A day's gym is optional: with one gym every
training day is at it, and with several, a day naming no gym leaves the start sheet to history, as
before. Deleting a gym keeps its days, naming no gym.

The v23 migration, and the restore of an older backup, turn every gym's weekdays into training days
with that gym; when two gyms shared a day, the first in the gym list (by name) keeps it, as the old
usual-gym rule picked it. A gym file's `usualDays` is still read on import, as those days' gym, and
never written.
