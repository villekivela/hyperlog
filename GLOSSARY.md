# Hyperlog

Hyperlog runs a periodized strength program, records what was done, and proposes changes when
training can't go as planned. This glossary is the language the app, its docs and its tickets use.

## Program

**Program**:
A periodized plan made of blocks of weeks, each week a set of training days.
_Avoid_: Plan, routine, template

**Block**:
A run of consecutive weeks within a program that share the same days, such as Accumulation weeks 1–7.
_Avoid_: Phase, mesocycle

**Week override**:
A change to a set prescription that applies only to particular weeks of its block, such as a lower target RIR in week 1. A day or a whole block can carry one for all of its sets.

**Day**:
One planned training session in a program: an ordered list of groups.
_Avoid_: Workout, routine day

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

**Position**:
Where you are in a program: which program, which week, and which day comes next.

## Exercises

**Exercise library**:
Every exercise the app knows: the shipped library plus those added on the phone. Programs, sessions and swaps refer to its exercises by id.
_Avoid_: Exercise database, catalogue

**Movement pattern**:
What an exercise's movement does, such as vertical pull or knee flexion. Swaps look for exercises with the same pattern.

**Grip demand**:
How hard an exercise works the hands: high, low or none. The coach's "no grip" constraint reads it.

**Technique note**:
An exercise's own cue on how to do it, kept in the library. A program's slot note is the program's cue for that slot.
_Avoid_: Tip, instruction

**Technique video**:
A YouTube link for an exercise, opened in the YouTube app at its timestamp.

## Session

**Session**:
What was actually done on one date: the sets logged, with their weights and reps. It's a record, never recalculated from new rules, though it can be corrected.
_Avoid_: Workout, log, entry

**Logged set**:
One set as it was done: weight, full reps, partial reps and RIR, plus any drops or mini-sets.

**Warm-up set**:
A lighter set before the work sets, generated from the first work set's weight. It doesn't count toward volume or progression.

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
An activation set followed by mini-sets of a few reps separated by short breathing pauses, ending when a mini-set falls short.
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

**Deload**:
A planned week of reduced volume or effort. It never lowers the estimate.

## Gyms and equipment

**Gym**:
A place you train, with the equipment it has.
_Avoid_: Location, venue

**Equipment item**:
One piece of equipment at a gym, such as a particular cable station or leg press. Dumbbells and barbell plates are shared by all gyms.
_Avoid_: Machine (when you mean any equipment)

**Weekday plan**:
The weekdays a gym is planned for. Starting a session on one of them picks that gym.
_Avoid_: Schedule (the program runs as a sequence, not on dates)

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

**Situation**:
Why the plan can't be followed as written: time off, pain in an area, no gym, short on time or poor recovery.

**Constraint**:
A limit on what an exercise may demand, such as no grip, no axial load or home equipment only. Swaps respect it.

**Swap**:
Replacing a slot's exercise with an alternative or a matching exercise, for one session or as part of a change.
_Avoid_: Substitution
