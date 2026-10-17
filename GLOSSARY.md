# Hyperlog

Hyperlog runs a periodized strength program, records what was done, and proposes changes when
training can't go as planned. This glossary is the language the app, its docs and its tickets use.

## Program

**Program**:
A periodized plan made of blocks of weeks, each week a set of days.
_Avoid_: Plan, routine, template

**Block**:
A run of consecutive weeks within a program that share the same days, such as Accumulation weeks 1–7.
_Avoid_: Phase, mesocycle

**Week override**:
A change to a set prescription that applies only to particular weeks of its block, such as a lower target RIR in week 1. A day or a whole block can carry one for all of its sets.

**Day**:
One planned training session in a program: an ordered list of groups.
_Avoid_: Routine day, workout (that's a day Claude builds outside the program)

**Group**:
Exercises done together within a day, either one exercise on its own (straight sets) or a superset.

**Superset**:
A group of two exercises done alternately, set for set (A1, A2), with rest only after each round.
_Avoid_: Pairing, circuit

**Slot**:
A place in a day for one exercise. It holds the planned exercise and its alternatives.

**Alternative**:
An exercise the program allows in place of a slot's planned exercise.
_Avoid_: Option, substitute

**Set prescription**:
What a set should be: its type, target reps (a fixed number or a range), target RIR, rest and any intensity technique.
_Avoid_: Target set, planned set

**Top set**:
The heaviest work set of an exercise in a session, the one progression judges.

**Back-off set**:
A work set after the top set at a lighter weight: the top set's weight minus a percentage, rounded down to a rung.
_Avoid_: Drop (that's part of a drop set)

**Starter program**:
The original program that ships with the app, Hyperlog Full Body, offered while no program runs.
_Avoid_: Default program, bundled program

**Position**:
Where you are in a program: which program, which week, and which day comes next.

**Planned date**:
The date Today's calendar puts a coming day on: the next day on today (or the next training day
once a session was finished today), each day after it on the next day of the weekday plan. An
estimate, since the program runs as a sequence; with no weekday plan only the next day has one.
_Avoid_: Scheduled date, due date

**Day letter**:
The letter a day goes by on Today's calendar and cards: `A` from `Day A — …`, else its place in
the week.

## Exercises

**Exercise library**:
Every exercise the app knows: the shipped library plus those added on the phone. Programs, sessions and swaps refer to its exercises by id.
_Avoid_: Exercise database, catalogue

**Movement pattern**:
What an exercise's movement does, such as vertical pull or knee flexion. Swaps look for exercises with the same pattern.

**Grip demand**:
How hard an exercise works the hands: high, low or none. The coach's prompt shares it, so Claude can keep sore hands off grip work.

**Technique note**:
An exercise's own cue on how to do it, kept in the library. A program's slot note is the program's cue for that slot.
_Avoid_: Tip, instruction

**Technique video**:
A YouTube link for an exercise, opened in the YouTube app at its timestamp.

## Session

**Session**:
What was actually done on one date: the sets logged, with their weights and reps. It's a record, never recalculated from new rules, though it can be corrected.
_Avoid_: Workout (that's the plan, not the record), log, entry

**Logged set**:
One set as it was done: weight, full reps, partial reps and RIR, plus any drops or mini-sets.

**Warm-up set**:
A lighter set before the work sets, generated from the first work set's weight. It doesn't count toward volume or progression.

**Plate calculator**:
The plates to load on each side of a bar or plate-loaded machine for a weight, from the plates there are.

**Work set**:
A set that counts toward volume and progression.

**Full reps**:
Repetitions through the whole prescribed range of motion.

**Partial reps**:
Repetitions in the lengthened (stretched) part of the range, done after full reps. They are recorded but never drive progression.
_Avoid_: Half reps, partials of any other kind

**Rest**:
The planned pause after a set or a superset round before the next one.

## Intensity techniques

**Intensity technique**:
A way of extending a work set past its main reps: drop set, myo-reps, lengthened partials, iso hold or failure.
_Avoid_: Intensity modification, advanced technique

**Drop set**:
A work set followed immediately by one or more drops at lower weights, with no rest between them.

**Drop**:
One lighter continuation within a drop set. Its weight is reduced from the set's weight and rounded down to a rung.

**Myo-reps**:
An activation set followed by mini-sets of a few reps separated by short breathing pauses, ending when a mini-set falls short (there is no set number of them).
_Avoid_: Rest-pause (a different technique)

**Mini-set**:
One short continuation within myo-reps, done at the activation set's weight.

**Lengthened partials**:
A technique where, at failure on full reps, the set continues with partial reps to failure.

**Iso hold**:
A timed static hold at a set position (stretched or contracted).

**Failure set**:
A work set taken to zero reps in reserve.

## Effort and progression

**RIR**:
Reps in reserve: how many more full reps could have been done. It's the only effort scale the app uses.
_Avoid_: RPE

**RIR chip**:
How the app shows an RIR, in set text and on its own (the logging screen, its pad, the exercise sheet): the number in a small chip of the RIR colour scale, outlined for a target and a solid fill for a logged set. Text that leaves the app writes `@ N` instead.
_Avoid_: RIR badge, RIR pill

**Estimate**:
The current strength estimate for an exercise (for machines, for one machine), worked out from logged reps and RIR. It drives suggestions.
_Avoid_: 1RM, max

**Suggestion**:
The weight and target reps the app proposes for an upcoming set, with the reason shown.
_Avoid_: Recommendation, prescription (that is the program's, not the app's)

**Lift goal**:
A target value for one exercise's estimate, such as a 100 kg bench, optionally by a date.
_Avoid_: Target (that's a set prescription's), PR goal

**Starting weight**:
A one-off suggestion for an exercise with no history yet, taken from the old app's data.

**Bodyweight**:
The body's weight on one day, logged on History, one value a day. A weighted bodyweight exercise's estimate adds it to the load on the belt or held.
_Avoid_: Body mass, weigh-in

**Weekly sets**:
The work sets a muscle got in a week (Monday to Sunday), each counted as 1 for every primary muscle of its exercise and 0.5 for every secondary muscle, whatever partials, drops or mini-sets extend it.
_Avoid_: Volume load, tonnage

**Deload**:
A planned week of reduced volume or effort, marked as one in the program file. It never lowers the estimate.

## Gyms and equipment

**Gym**:
A place you train, with the equipment it has.
_Avoid_: Location, venue

**Equipment item**:
One piece of equipment at a gym, such as a particular cable station or leg press. Each gym has its own dumbbell rack and its own plates.

**Plates** (a gym's):
The loose plates a gym has, counted as pairs per size, such as 8 pairs of 20 kg. Its bars and plate-loaded machines load them.
_Avoid_: Plate set, shared plates
_Avoid_: Machine (when you mean any equipment)

**Weekday plan**:
The weekdays you train on, each with an optional gym. Kept once for the app, not per gym. A day's
gym is the one a session started that day picks; Time off counts from the days. "Training days"
on screen only; in code and docs it is the weekday plan, since a program's days are days.
_Avoid_: Schedule (the program runs as a sequence, not on dates), training days (in code)

**Pin stack**:
A selectorised machine's weight stack: the pin picks a plate, and add-ons go on top. Its ladder is every pin weight plus every combination of the add-ons.

**Built-in add-on**:
A machine's own small weight on top of its stack, or a lever or knob adding a half step. Set per machine.

**Pin plates**:
A gym's small loose plates that hang on any stack's pin, such as 1.25 and 2.5 kg. Set once per gym.
_Avoid_: Add-on (for these)

**Weight ladder**:
The weights an equipment item can actually be set to, such as a cable stack in 5 lb steps.
_Avoid_: Increments, stack

**Rung**:
One weight on a weight ladder. Progression moves between rungs, never to weights the equipment can't be set to.

**Weight check**:
The warning a set's weight gets before it is logged when it is far from the reference or off the ladder: the reason and three real weights. A second ✓ keeps the weight.
_Avoid_: Sanity check, outlier warning

## Coaching

**Change**:
A proposed edit to the plan for a period or the rest of the program, shown as a diff and applied only after you accept it.
_Avoid_: Adjustment, modification, override

**Change in force**:
An accepted Change, not reverted, that still covers today or a later day. Coach shows each as a card with Revert.
_Avoid_: Active change, current change

**Replaces**:
A Change's list of Changes in force it ends on accept, as Revert ends them. A Change that only ends others has no operations. The ended ones read "Replaced" on Program.
_Avoid_: Supersedes, cancels, overrides

**Time off**:
Days without training beyond the plan's own rest days: from the first day of the weekday plan missed since the last session, or the days since it with no weekday plan. More than a week shows Today's time-off line.
_Avoid_: Break, layoff

**Swap**:
Replacing a slot's exercise with an alternative or a matching exercise, for one session or as part of a Change.
_Avoid_: Substitution

**Workout**:
A day Claude builds for one date, outside the program: groups, slots and set prescriptions like a program day's. Training it doesn't move the position.
_Avoid_: One-off day, coach workout, ad-hoc session (that one has no prescription)

**Ask Claude**:
The hand-off of your own words to the Claude app: a prompt shared through the share sheet, and Claude's reply, a Change or a workout, pasted back for review. It is the coach's only way in. Hyperlog makes no model calls.
_Avoid_: AI coach, chat

## Data

**Backup**:
One JSON file holding everything the app stores, made by Export backup. Restoring one replaces all the data on the phone with it.
_Avoid_: Import (that is for program files and the old app's starting weights), sync

**Auto Backup**:
Android's own copy of the app's database in the Google account, restored when the app is installed again.

**Release**:
A published version of Hyperlog: a `vMAJOR.MINOR.PATCH` tag in the public `villekivela/hyperlog` repo with its signed APK.
_Avoid_: Build (that is any APK, debug ones included)

**Update check**:
The once-a-day read of the latest release when the app opens; a newer one shows the update banner on Today.
_Avoid_: Sync, auto-update (Android always asks before installing)
