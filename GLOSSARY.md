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

**Saved program**:
A program kept on the Programs list without starting it, by Save for later on a new program's review. It waits with the new exercises it brings, and its page shows it read-only; Start replaces the program running with it, from week 1, day 1.
_Avoid_: Draft, queued program

**Run again**:
Starting a program that has run before afresh, from week 1, day 1, as a new run. The old run keeps its sessions and Changes.
_Avoid_: Restart, reset (the old run isn't changed)

**Resume (a program)**:
Picking up a program replaced mid-way where it was left, the same run with its sessions and Changes, in place of the one running. Not Resume session, which reopens a finished session.
_Avoid_: Continue, reactivate

**Block review**:
One page on how a finished block went: each lift's estimate from start to end, sessions done
against training days, weekly sets against the block as written, its Changes, records and the lift
goal.
_Avoid_: Recap, summary, report

**Program review**:
The last block's review, with the whole program beside it.
_Avoid_: Program summary, final report

**Starter program**:
The original program that ships with the app, Hyperlog Full Body, offered while no program runs.
_Avoid_: Default program, bundled program

**Position**:
Where you are in a program: which program, which week, and which day comes next.

**Planned date**:
The date a coming day is trained on: the training day the rotation puts it on. With no weekday plan
the program runs as a sequence, and only the next day has one, today.
_Avoid_: Scheduled date, due date

**Missed day**:
A training day whose date passed with no session started on it. Its day is used up and skipped,
never moved.
_Avoid_: Skipped day (Skip day is a Change), postponed day

**Rotation**:
The program's days in order over the training days: each training day takes the next day, so three
days on three training days always fall on the same weekdays, and four on three go A B C, D A B.
_Avoid_: Pinning, sequence (that is the order with no weekday plan)

**Turn**:
A date with the program day it trains, trained, missed or to come: a training day the rotation
gives a day, or, with no weekday plan, the next day on its one planned date.
_Avoid_: Slot (a place in a day for an exercise), occurrence

**Rotation anchor**:
Where the rotation was last set, kept with the program: the date it counts from and the week and
day the first training day from then takes. It moves when the program starts, resumes, a Change
moves the position or the training days change; the days missed before it are kept.
_Avoid_: Checkpoint, reset point

**Program week**:
A calendar week, Monday to Sunday, of the program running. A week with no session doesn't count,
so the next one trains it again.
_Avoid_: Microcycle

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

**Adjustable bench**:
An exercise done on a bench whose backrest is set to an angle, such as an incline DB press or a seated DB shoulder press. Its sessions keep the bench angle. Not a flat bench, a bench used as a prop, or a machine's own seat.

**Technique note**:
An exercise's own cue on how to do it, kept in the library. A program's slot note is the program's cue for that slot.
_Avoid_: Tip, instruction

**Technique video**:
A YouTube link for an exercise, opened in the YouTube app at its timestamp. The studio shows it beside the stage, fetched once into a cache on the owner's machine and never committed.

**Mannequin**:
The faceless body at the top of an exercise's prescription sheet that does one rep in a loop, with plain generic equipment around it. A cue for which movement it is, not form instruction.
_Avoid_: Avatar, figure (the spec's figures are numerals), animation

**Pose**:
An exercise's authored entry in the mannequin tool: where the body, hands and feet go through the rep, its equipment and its default tempo.
_Avoid_: Preset, keyframe

**Bone offset**:
How far a pose turns one of the mannequin's bones at the top or the bottom of the rep, on top of what the solve does, eased between the two ends. Set in the studio.
_Avoid_: Turn (that is a date in the rotation), override, keyframe

**Foot catch**:
The bench's option for a decline movement: a post out of the back pad's far end with two pairs of rollers, over and behind which the lifter's legs hook, lying the other way round. The reference is its maker's decline kit; it is an option of the `bench` kit, not a kit of its own.
_Avoid_: Decline kit, leg roller

**Framing**:
Where the stage's camera looks for a clip, how much it shows there, and the turn and tilt the phone opens on. The studio's `:frame` saves its view as the clip's framing.
_Avoid_: Camera angle, view (the studio's free camera)

**Clip**:
A baked file of one exercise's lowering that the phone plays forward, holds and plays backward. An adjustable-bench exercise has one per 15° and one at 85° of bench angle.
_Avoid_: Animation, video (that is the Technique video), asset

**Kit**:
The generic equipment a pose is posed in, built by the mannequin tool for a kind of equipment: a barbell, a bench, a cable tower, a leg press's sled. Each of its parts is in one of five tones (frame, vinyl, plate, metal, pulley), which the theme colours.
_Avoid_: Prop, model, machine (that is the gym's own)

**Footage**:
A side-on video of a lift that a trace is taken from: the owner's own filming, or a technique video. It stays on the machine that traces and is never committed.
_Avoid_: Clip (that is the baked file)

**Trace**:
The side-on angles of one rep's lowering, taken from footage by pose estimation, that set some of a pose's angles. The feet, hands, contacts and tempo stay authored.
_Avoid_: Capture, keyframes

**Movement spec**:
What a lift should do, written from its technique video and research: what moves and how far, what stays still, the path of the hands or the bar, and what must never happen. One short file per exercise in the mannequin tool, each line exact or left to the reviewer's eyes.
_Avoid_: Trace (that is taken from footage), technique note (that is the library's cue)

**Cold spec**:
A movement spec written before its lift is posed, by someone who hasn't seen a pose, and hidden from the worker who poses it. It tests the review, so neither is tuned to the other.

**Movement reviewer**:
An agent that didn't pose a lift and checks its clip against its movement spec, line by line, before the owner sees it. A failure goes back to the worker, a doubt goes to the owner, and a clean lift needs only a sample.
_Avoid_: Tester (that is the UI agent), checker

**Batch**:
The poses made and reviewed together. A batch is done when the owner has OK'd every one, for the clip it has now, on the studio.

**Studio**:
The mannequin tool served on the owner's machine (`npm run studio`), where every exercise and kit is listed with its status, posed live, tuned, OK'd and noted. It views, tunes and annotates; it never models.
_Avoid_: Review page (the published page it replaced), editor

**Video loop**:
One rep in an exercise's technique video, marked at its top (A), its bottom (B) and back at the top (C), which the studio keeps for the exercise. With sync on it is the clip's rep, the lowering A to B and the lift B to C: moving either one moves the other.
_Avoid_: A–B loop (it has three marks), range, segment, clip (that is the baked file)

**Recipe**:
A kit written as data: the parts it is made of, placed by dimensions, its fixed frame and its moving bodies on joints, with its contacts and fit rules (ADR 0007).
_Avoid_: Blueprint, builder

**Static machine**:
A machine with nothing on it that moves (the glute-ham developer): its recipe has a linkage and no rep, so the machine-first solve sets its options for the mannequin once and gives the motion one place for the knees, the ankles and the hands.
_Avoid_: Fixed kit, stationary

**Rack add-on**:
A bar or handles that bolts to the power rack's holes as an option of its recipe: the multi-grip pull-up bar and the dip handles, with J-hooks for squats to follow. It brings its own contacts, and the rack builds without it.
_Avoid_: Attachment (that is a cable's handle), add-on alone (that is a machine's weight)

**Part**:
One piece from the kits' shared library that a recipe places: tubing with its caps and base plates, a pad, a seat, a roller, a pulley in its housing, a cable, a stack, a pop-pin, a handle.
_Avoid_: Component, mesh

**Body**:
A piece of a recipe that moves as one: on a joint (a lever, a sled, a stack), or free, on no joint, where the mannequin carries it (a barbell, a cable's attachment, a dip belt's plate).
_Avoid_: Link, moving part

**Joint**:
Where a recipe's moving body turns (revolute: a lever, a pec deck's arm) or slides (prismatic: a sled, a carriage) on its frame, with its limits. Not one of the mannequin's own joints, which are bones.
_Avoid_: Hinge, axis

**Contact**:
Where a recipe ties a moving body, or its fixed frame, to the mannequin: handles to the hands, a pad to the shins or the thighs, a foot plate to the feet, shoulder pads to the shoulders, a chest pad to the chest, a seat to the hips. On a machine the joint drives the rep and IK holds the limbs to the contacts.
_Avoid_: Attachment (that is a cable's handle), constraint

**Linkage**:
What a recipe's moving bodies do with no pose: the joint the rep drives, and where each contact is at any value of it. A motion hangs the limbs from it. Its check holds each joint within its limits, each handle on its arc, and each pad on its limb.
_Avoid_: Mechanism, rig (that is the mannequin's)

**Grip landmark**:
Where a bar or handle sits in a pose's hand: the heel of the palm (a press), mid-palm (a curl, a squat) or the base of the fingers (a pull); or an open hand, which holds nothing round. With the thumb, wrapped round the bar or thumbless over it, it is what the grip solve closes the hand to. Each motion has one by default; an entry can name its own.
_Avoid_: Grip depth, grip (that is the hands' width and turn on the bar)

**Seat** (of a bar):
Where a bar of some radius rests on the palm at a grip landmark, for every way it can slant across the hand. It belongs to the mannequin, not to a pose, so each is solved once and kept in `seats.json`. Not a machine's seat, which a body sits on.
_Avoid_: Palm offset

**Fit rule**:
How a recipe sets its adjustments for the mannequin, as the machine's placard says: the knee on the pivot axis, the handles at mid-chest, the pad just above the ankle. A pose entry can override what it sets.
_Avoid_: Setting, preset

## Session

**Session**:
What was actually done on one date: the sets logged, with their weights and reps. It's a record, never recalculated from new rules, though it can be corrected.
_Avoid_: Workout (that's the plan, not the record), log, entry

**Logged set**:
One set as it was done: weight, full reps, partial reps and RIR, plus any drops or mini-sets.

**Bench angle**:
The backrest's angle from flat, in whole degrees, that an adjustable-bench exercise was done at in a session. Measured with the phone laid on the backrest, or typed. The target is last time's angle until one is set in the session, then that one; progression ignores it.
_Avoid_: Incline (that's one kind of exercise), seat angle

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

**Exercise history**:
An exercise's past sessions as progression reads them: per machine for machines and cables, deload as of each session, bodyweight on its date, the main set only. Suggestions, records and trends all read it.

**Suggestion**:
The weight and target reps the app proposes for an upcoming set, with the reason shown.
_Avoid_: Recommendation, prescription (that is the program's, not the app's)

**Record**:
A logged set that beats every earlier one of the exercise (of the machine, for machines and cables): an estimate record or a rep record. Warm-ups, deload sets and partial reps never make one.
_Avoid_: PR (that is a pull request here), personal best, PB

**Estimate record**:
A set whose e1RM is above the exercise's best estimate so far.

**Rep record**:
A set with more full reps than ever at a weight already lifted for that exercise.

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
Your training days, kept once for the app, not per gym. "Training days" on screen; in code and
docs it is the weekday plan, since a program's days are also days.
_Avoid_: Schedule

**Training day**:
One weekday of the weekday plan, with an optional gym and the minutes you have, such as Mon at
Harbour for 60 minutes. The program's days take turns on training days (Rotation).
_Avoid_: Slot (that is a place in a day for an exercise), session window

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
_Avoid_: Break, layoff. About me's "Coming back from a break" is the owner's wording for where you are as a lifter, not the app's time off.

**Easy week**:
The week back after two or more weeks without a session: the days that come up, one set fewer each
and one more RIR, counted as a deload and outside the program, which then picks up where it stopped.
_Avoid_: Ease back in (that is the check-in), return week

**Coach line**:
The one line on Today that points at a pattern in the log (the same day missed, a stall, grinding,
the same swap) with Check in. It proposes nothing itself.
_Avoid_: Nudge, alert, notification

**Swap**:
Replacing a slot's exercise with an alternative or a matching exercise, for one session or as part of a Change. In a new program from Claude, an exercise the source names that the library lacks, done as the closest library exercise.
_Avoid_: Substitution

**Workout**:
A day Claude builds for one date, outside the program: groups, slots and set prescriptions like a program day's. Training it doesn't move the position.
_Avoid_: One-off day, coach workout, ad-hoc session (that one has no prescription)

**Check-in**:
One run of the coach's wizard: a first tile ("Something hurts", "New program", …), then Claude's questions one a screen, each with a few answers to tap, ending in words with Done, a review, or ×. With a Claude key the app asks Claude itself; without one it hands off through Ask Claude. An accepted Change keeps the check-in that made it.
_Avoid_: Chat, conversation, session (that is training)

**Ask Claude**:
The check-in without a Claude key: a prompt shared through the share sheet to the Claude app, and Claude's reply, a Change, a workout or a program, pasted back for review.
_Avoid_: AI coach, chat

**Claude key**:
The Anthropic API key typed in Settings. With it, check-ins call Claude from the app. It stays on the phone, encrypted, and never goes into a backup.
_Avoid_: Token, credentials, API token

**Scripted coach**:
A debug-build setting that plays canned check-ins with no Claude key and no network, so the wizard can be tried and tested.
_Avoid_: Mock coach, demo mode

**Training style**:
How you like to train, kept once for the app and carried by every check-in: the training targets, How I train and About me.
_Avoid_: Preferences, profile, philosophy (that is How I train)

**Training style wizard**:
The local walk through the training style, one choice a screen, ending in a summary with Done. Done counts as set, even with every default kept. Until it is done, Today shows a "Set how you train" card and New program opens the wizard first. Not the Check-in's wizard, which asks Claude's questions.
_Avoid_: Onboarding, setup wizard

**Training targets**:
The training style's standing numbers and rules: goal, weekly sets per muscle as a range, how that volume runs across a block, frequency, effort and failure, calibration and rep ranges. Every new program starts from them; a program can carry its own for itself alone.
_Avoid_: Targets alone (a set prescription has targets), preferences

**How I train**:
The training philosophy as principles: fixed rules Claude always gets (form before load, no loads in a program, swap around pain, alternatives in every slot), switches on by default (stretch-biased exercises, stable lifts you can push hard, blocks with a deload) and one optional line. You see each principle's short label; Claude reads its full sentence, which the app ships.
_Avoid_: Philosophy, system prompt, instructions, rules (alone)

**About me**:
What Claude should know about you as a lifter: age band, how fast you progress, aches and injuries, exercises to avoid and to favour, and anything else.
_Avoid_: Profile, bio, user info

**Program focus**:
Up to two muscle groups a program brings up, kept with the program: their muscles get the raised range, go first and are trained more often, and the other muscles drop toward maintenance. Not a workout's focus, which picks the muscle groups of one day.
_Avoid_: Focus alone, priority, specialisation (the idea, not what a program picks)

**Raised range**:
The weekly sets each muscle of a focused group is written for: from the top of the training targets' range to half as much again.
_Avoid_: Focus range, specialisation volume

**Maintenance range**:
The weekly sets the other muscles get in a program with a focus: from half the range's bottom to its bottom, enough to keep what they have.
_Avoid_: Minimum volume, MV

**Calibration set**:
A last set taken to 0 RIR in a block's last week, unless it is a deload, on an exercise that is safe to fail, so the RIR you log stays honest.
_Avoid_: Test set, AMRAP

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

**Changeover**:
The time between finishing one exercise and starting the next: moving to the station, setting the weight and the seat. Counted in a day's minutes, once per superset.
_Avoid_: Transition, setup

**Main lift**:
Bench press and the squat, hinge (RDL) and row patterns. They get the full warm-up ramp, and the day's minutes count three warm-up sets for them where other exercises get one.
