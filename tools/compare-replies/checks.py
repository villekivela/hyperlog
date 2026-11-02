"""Scores a reply from a check-in against the training targets in force (SPEC, Coaching → Training style).

Pure: a run (the JSON `CompareReplies` writes) and the exercise library go in, one row per target
come out: {"target", "verdict": "met" | "missed" | "n/a", "detail"}. A program is scored on its
first block's base prescription (week overrides are ignored), and every number is the app's default
targets, whichever run it is: the "before" run was never told them, which is the point.
"""

VOLUME = (8, 14)
COMPOUND_REPS = (5, 10)
ISOLATION_REPS = (8, 15)
FREQUENCY = (2, 3)
WORKOUT_MINUTES = {"workout-45": 45}
FOCUS_ASKED = {"new-program-chest": "Chest"}
PRESS = {"horizontal_push", "vertical_push"}
ELBOW = PRESS | {"elbow_extension", "elbow_flexion"}
SECONDS_PER_SET, WARMUP_MINUTES = 45, 2

# Library movement patterns that are compounds; every other pattern is isolation.
COMPOUND = {"squat", "hinge", "lunge", "hip_thrust", "horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull"}
# Equipment where going to failure is safe (SPEC: isolation and machines only; a Smith machine and a cable count as machines).
SAFE_FAILURE = {"machine", "cable", "smith_machine"}

# The app's muscle groups (library/Exercise.kt, MuscleGroup).
GROUPS = {
    "Chest": ["Chest", "Serratus Anterior"],
    "Back": ["Lats", "Teres Major", "Traps", "Rhomboids", "Lower Back", "Neck"],
    "Shoulders": ["Front Delts", "Side Delts", "Rear Delts", "Rotator Cuff"],
    "Arms": ["Triceps Long Head", "Triceps Lateral Head", "Biceps", "Brachialis", "Forearm Flexors", "Forearm Extensors"],
    "Legs": ["Quads", "Hamstrings", "Adductors", "Gastrocnemius", "Soleus"],
    "Glutes": ["Glutes", "Hip Abductors"],
    "Core": ["Upper Abs", "Lower Abs", "Obliques", "Hip Flexors"],
}
GROUP_OF = {muscle: group for group, muscles in GROUPS.items() for muscle in muscles}


def proposal(run):
    """The last non-ask tool call of the run: (name, input), or None when Claude ended in words."""
    for reply in reversed(run.get("replies", [])):
        for call in reply.get("toolCalls", []):
            if call["name"] != "ask":
                return call["name"], call["input"]
    return None


def first_block_days(program):
    return program["blocks"][0]["days"]


def days_of(call, scenario):
    """The days a proposal trains, with the minutes each is held to (None: no limit); a workout is one day."""
    name, data = call
    if name == "propose_program":
        # Keep in step with weekdayPlan in CompareReplies.kt.
        minutes = [45, 60, 60]  # Monday 60, Wednesday 45, Friday 60; from a Tuesday, Day A lands on Wednesday, B on Friday, C on Monday
        days = first_block_days(data["program"])
        return days, [minutes[i] if i < len(minutes) else None for i in range(len(days))]
    return [data], [WORKOUT_MINUTES.get(scenario)]


def slots(day):
    return [slot for group in day["groups"] for slot in group["slots"]]


def lines(slot):
    return slot["sets"]


def count(line):
    return line.get("count", 1)


def week_sets(days, library):
    """Sets in a week of the first block for each muscle: primary 1, secondary 0.5 (as the app counts)."""
    sets, primary = {}, set()
    for day in days:
        for slot in slots(day):
            exercise = library.get(slot["exercise"])
            if exercise is None:
                continue
            n = sum(count(line) for line in lines(slot))
            for muscle in exercise["muscles"]:
                sets[muscle] = sets.get(muscle, 0) + n
                primary.add(muscle)
            for muscle in exercise["secondary"]:
                if muscle not in exercise["muscles"]:
                    sets[muscle] = sets.get(muscle, 0) + n / 2
    return {m: n for m, n in sets.items() if m in primary}


def number(n):
    return str(int(n)) if n == int(n) else str(n)


def row(target, verdict, detail):
    return {"target": target, "verdict": verdict, "detail": detail}


def sets_per_muscle(days, library):
    sets = week_sets(days, library)
    low, high = VOLUME
    outside = {m: n for m, n in sets.items() if not low <= n <= high}
    if not outside:
        return row("Sets per muscle", "met", f"every muscle trained as a main mover is within {low}–{high} sets a week")
    listed = ", ".join(f"{m} {number(n)}" for m, n in sorted(outside.items(), key=lambda kv: kv[1]))
    return row("Sets per muscle", "missed", f"outside {low}–{high} sets a week: {listed}")


def reps_in_reserve(days, library):
    bad = []
    for day in days:
        for slot in slots(day):
            expanded = [line.get("rir") for line in lines(slot) for _ in range(count(line))]
            for i, rir in enumerate(expanded):
                last = i == len(expanded) - 1
                allowed = (0, 1) if last else (1, 2)
                if rir not in allowed:
                    bad.append(f"{slot['exercise']} set {i + 1} at {'no RIR' if rir is None else rir}")
    if not bad:
        return row("Reps in reserve", "met", "last sets at 0–1 RIR, earlier sets at 1–2")
    return row("Reps in reserve", "missed", f"{len(bad)} sets outside (last 0–1, earlier 1–2): " + "; ".join(bad[:6]) + (" …" if len(bad) > 6 else ""))


def is_compound(exercise):
    return exercise["pattern"] in COMPOUND


def goes_to_failure(line):
    return line.get("rir") == 0 or (line.get("technique") or {}).get("kind") in ("failure", "dropset")


def failure(days, library):
    bad = []
    for day in days:
        for slot in slots(day):
            exercise = library.get(slot["exercise"])
            if exercise is None or not is_compound(exercise) or exercise["equipment"] in SAFE_FAILURE:
                continue
            if any(goes_to_failure(line) for line in lines(slot)):
                bad.append(slot["exercise"])
    if not bad:
        return row("Failure", "met", "to failure only on isolation exercises and machine, cable and Smith compounds")
    return row("Failure", "missed", "failure on free-weight compounds: " + ", ".join(sorted(set(bad))))


def reps_of(line):
    reps = line["reps"]
    if isinstance(reps, int):
        return reps, reps
    low, _, high = str(reps).partition("-")
    return int(low), int(high or low)


def rep_ranges(days, library):
    bad = []
    for day in days:
        for slot in slots(day):
            exercise = library.get(slot["exercise"])
            if exercise is None:
                continue
            low, high = COMPOUND_REPS if is_compound(exercise) else ISOLATION_REPS
            for line in lines(slot):
                lo, hi = reps_of(line)
                if lo < low or hi > high:
                    bad.append(f"{slot['exercise']} {line['reps']} (wants {low}–{high})")
    if not bad:
        return row("Rep ranges", "met", "compounds within 5–10 reps, isolation within 8–15")
    return row("Rep ranges", "missed", f"{len(bad)} lines outside: " + "; ".join(bad[:6]) + (" …" if len(bad) > 6 else ""))


def frequency(days, library):
    trained = {}
    for day in days:
        for slot in slots(day):
            exercise = library.get(slot["exercise"])
            for muscle in (exercise or {}).get("muscles", []):
                trained.setdefault(GROUP_OF.get(muscle, muscle), set()).add(day["id"])
    low, high = FREQUENCY
    outside = {g: len(d) for g, d in trained.items() if not low <= len(d) <= high}
    if not outside:
        return row("Frequency", "met", f"every muscle group trained {low}–{high} days a week")
    return row("Frequency", "missed", f"days a week outside {low}–{high}: " + ", ".join(f"{g} {n}" for g, n in sorted(outside.items())))


def day_minutes(day):
    total = 0.0
    for slot in slots(day):
        total += WARMUP_MINUTES + sum(count(line) * (SECONDS_PER_SET + line.get("rest", 120)) for line in lines(slot)) / 60
    return total


def time_fit(days, limits):
    over = []
    for day, limit in zip(days, limits):
        if limit is None:
            continue
        minutes = day_minutes(day)
        if minutes > limit:
            over.append(f"{day.get('name', 'the workout')} about {number(round(minutes))} min against {limit}")
    if not over:
        return row("Time", "met", "estimated within the minutes asked (45 s a set + rest, 2 min warm-up per exercise)")
    return row("Time", "missed", "over its minutes: " + "; ".join(over))


def focus(program, days, library, asked):
    low, high = VOLUME
    raised = (high, round(high * 1.5))
    maintenance = (-(-low // 2), low)
    written = program.get("focus") or []
    if asked not in written:
        return row("Focus", "missed", f"asked to bring up {asked}; the program has {'focus ' + ', '.join(written) if written else 'no focus'}")
    focused = {m for g in written for m in GROUPS[g]}
    sets = week_sets(days, library)
    bad = []
    for muscle, n in sorted(sets.items()):
        want = raised if muscle in focused else maintenance
        if not want[0] <= n <= want[1]:
            bad.append(f"{muscle} {number(n)} (wants {want[0]}–{want[1]})")
    if not bad:
        return row("Focus", "met", f"{asked} muscles within the raised {raised[0]}–{raised[1]} sets; the rest within the maintenance {maintenance[0]}–{maintenance[1]}")
    return row("Focus", "missed", "outside range: " + "; ".join(bad))


def pain(call, library, plan):
    if plan is None:
        return []
    ops = call[1].get("operations", [])
    day_slots = [slot for day in first_block_days(plan) for slot in slots(day)]
    pattern = {slot["id"]: library.get(slot["exercise"], {}).get("pattern") for slot in day_slots}
    pressing = {slot for slot, p in pattern.items() if p in PRESS}
    changed = {op["slot"] for op in ops if op.get("op") in ("swap", "remove")}
    swap = row("Swaps the painful lift", "met" if pressing & changed else "missed",
               f"{len(pressing & changed)} of {len(pressing)} pressing slots swapped or left out")
    dropped = {slot for slot in changed - pressing if pattern.get(slot) not in ELBOW}
    keep = row("Keeps the rest of the session", "missed" if dropped else "met",
               ("changes slots that don't load the elbow: " + ", ".join(sorted(dropped))) if dropped else "only slots that load the elbow are changed")
    return [swap, keep]


def score(run, library, plan=None):
    scenario = run.get("scenario", "")
    found = proposal(run)
    if found is None:
        return [row("Proposal", "missed", "Claude ended in words, with no program, workout or Change")]
    if found[0] == "propose_change":
        return pain(found, library, plan)
    days, limits = days_of(found, scenario)
    rows = []
    if found[0] == "propose_program":
        rows += ([] if scenario in FOCUS_ASKED else [sets_per_muscle(days, library)]) + [frequency(days, library)]
    rows += [reps_in_reserve(days, library), failure(days, library), rep_ranges(days, library), time_fit(days, limits)]
    if found[0] == "propose_program" and scenario in FOCUS_ASKED:
        rows.append(focus(found[1]["program"], days, library, FOCUS_ASKED[scenario]))
    return rows
