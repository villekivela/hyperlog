"""Tests for the target checker. Run: python3 -I -m unittest discover -s tools/compare-replies"""
import unittest

import checks

LIBRARY = {
    "bench": {"id": "bench", "pattern": "horizontal_push", "equipment": "barbell", "muscles": ["Chest"], "secondary": ["Triceps Long Head"]},
    "pec_deck": {"id": "pec_deck", "pattern": "chest_fly", "equipment": "machine", "muscles": ["Chest"], "secondary": []},
    "curl": {"id": "curl", "pattern": "elbow_flexion", "equipment": "dumbbell", "muscles": ["Biceps"], "secondary": []},
    "leg_curl": {"id": "leg_curl", "pattern": "knee_flexion", "equipment": "machine", "muscles": ["Hamstrings"], "secondary": []},
    "squat": {"id": "squat", "pattern": "squat", "equipment": "barbell", "muscles": ["Quads"], "secondary": ["Glutes"]},
}


def line(reps, rir, count=1, rest=120, technique=None):
    out = {"count": count, "reps": reps, "rir": rir, "rest": rest}
    if technique:
        out["technique"] = technique
    return out


def program(days, focus=None, targets=None):
    """One block; each day is a list of (exercise, [set lines])."""
    p = {"format": 1, "title": "t", "blocks": [{"id": "b", "name": "b", "weekStart": 1, "weekEnd": 4, "days": [
        {"id": f"d{i}", "name": f"Day {i}", "groups": [{"slots": [{"id": f"s{i}{j}", "exercise": ex, "sets": sets}]} for j, (ex, sets) in enumerate(day)]}
        for i, day in enumerate(days)]}]}
    if focus is not None:
        p["focus"] = focus
    if targets is not None:
        p["targets"] = targets
    return p


def run(proposal, scenario="new-program"):
    return {"scenario": scenario, "ended": "ProgramReview", "replies": [{"text": "", "toolCalls": [{"name": "propose_program", "input": {"program": proposal}}]}]}


def verdict(rows, target):
    return next(r for r in rows if r["target"] == target)["verdict"]


class SetsPerMuscle(unittest.TestCase):
    def test_a_week_of_three_bench_sets_is_under_the_eight_to_fourteen_range(self):
        rows = checks.score(run(program([[("bench", [line("6-8", 1, count=3)])]])), LIBRARY)
        self.assertEqual("missed", verdict(rows, "Sets per muscle"))

    def test_primary_sets_count_one_and_secondary_half_across_the_week(self):
        # Chest: 4 bench + 4 pec deck = 8, in range. Triceps long head is only secondary and isn't judged.
        days = [[("bench", [line("6-8", 1, count=4)])], [("pec_deck", [line("10-12", 1, count=4)])]]
        rows = checks.score(run(program(days)), LIBRARY)
        self.assertEqual("met", verdict(rows, "Sets per muscle"))

    def test_the_week_adds_every_day_and_names_the_muscles_outside(self):
        days = [[("bench", [line("6-8", 1, count=20)])]]
        row = next(r for r in checks.score(run(program(days)), LIBRARY) if r["target"] == "Sets per muscle")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("Chest 20", row["detail"])


class RepsInReserve(unittest.TestCase):
    def test_last_set_at_zero_or_one_and_earlier_sets_at_one_or_two_is_met(self):
        sets = [line("6-8", 2), line("6-8", 1), line("6-8", 1)]
        self.assertEqual("met", verdict(checks.score(run(program([[("bench", sets)]])), LIBRARY), "Reps in reserve"))

    def test_an_earlier_set_at_three_misses(self):
        sets = [line("6-8", 3), line("6-8", 1)]
        self.assertEqual("missed", verdict(checks.score(run(program([[("bench", sets)]])), LIBRARY), "Reps in reserve"))

    def test_a_set_with_no_rir_misses(self):
        sets = [line("6-8", None)]
        self.assertEqual("missed", verdict(checks.score(run(program([[("bench", sets)]])), LIBRARY), "Reps in reserve"))


class Failure(unittest.TestCase):
    def test_zero_rir_on_an_isolation_exercise_or_a_machine_is_met(self):
        days = [[("curl", [line("10-12", 1), line("10-12", 0)]), ("pec_deck", [line("10-12", 0)])]]
        self.assertEqual("met", verdict(checks.score(run(program(days)), LIBRARY), "Failure"))

    def test_zero_rir_on_a_barbell_compound_misses_and_names_it(self):
        row = next(r for r in checks.score(run(program([[("squat", [line("6-8", 1), line("6-8", 0)])]])), LIBRARY) if r["target"] == "Failure")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("squat", row["detail"])

    def test_a_failure_technique_counts_as_failure(self):
        sets = [line("10-12", 1, technique={"kind": "dropset"})]
        self.assertEqual("missed", verdict(checks.score(run(program([[("bench", sets)]])), LIBRARY), "Failure"))


class RepRanges(unittest.TestCase):
    def test_compounds_at_five_to_ten_and_isolation_at_eight_to_fifteen_is_met(self):
        days = [[("bench", [line("5-8", 1)]), ("curl", [line("10-15", 1)])]]
        self.assertEqual("met", verdict(checks.score(run(program(days)), LIBRARY), "Rep ranges"))

    def test_a_compound_at_twelve_to_fifteen_misses(self):
        self.assertEqual("missed", verdict(checks.score(run(program([[("bench", [line("12-15", 1)])]])), LIBRARY), "Rep ranges"))

    def test_isolation_at_five_misses_because_it_is_under_eight(self):
        self.assertEqual("missed", verdict(checks.score(run(program([[("curl", [line(5, 1)])]])), LIBRARY), "Rep ranges"))


class Frequency(unittest.TestCase):
    def test_a_muscle_on_two_days_is_met_and_on_one_day_misses(self):
        two = [[("bench", [line("6-8", 1)])], [("pec_deck", [line("10-12", 1)])]]
        one = [[("bench", [line("6-8", 1)])], [("curl", [line("10-12", 1)])]]
        self.assertEqual("met", verdict(checks.score(run(program(two)), LIBRARY), "Frequency"))
        row = next(r for r in checks.score(run(program(one)), LIBRARY) if r["target"] == "Frequency")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("Chest 1", row["detail"])


class Focus(unittest.TestCase):
    CHEST_16 = [[("bench", [line("6-8", 1, count=8)])], [("pec_deck", [line("10-12", 1, count=8)])], [("curl", [line("10-12", 1, count=4)])]]

    def test_a_chest_focus_with_chest_in_the_raised_range_and_the_rest_at_maintenance_is_met(self):
        rows = checks.score(run(program(self.CHEST_16, focus=["Chest"]), "new-program-chest"), LIBRARY)
        self.assertEqual("met", verdict(rows, "Focus"))

    def test_no_focus_written_misses_when_the_lifter_asked_for_chest(self):
        row = next(r for r in checks.score(run(program(self.CHEST_16, focus=[]), "new-program-chest"), LIBRARY) if r["target"] == "Focus")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("no focus", row["detail"])

    def test_a_focused_group_under_the_raised_range_misses(self):
        days = [[("bench", [line("6-8", 1, count=9)])], [("curl", [line("10-12", 1, count=5)])]]
        row = next(r for r in checks.score(run(program(days, focus=["Chest"]), "new-program-chest"), LIBRARY) if r["target"] == "Focus")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("Chest 9", row["detail"])

    def test_the_maintenance_range_is_judged_for_each_muscle_not_for_the_whole_group(self):
        # Quads 6 and Hamstrings 6 are each within 4–8, though Legs adds up to 12.
        days = self.CHEST_16 + [[("squat", [line("6-8", 1, count=6)]), ("leg_curl", [line("10-12", 1, count=6)])]]
        rows = checks.score(run(program(days, focus=["Chest"]), "new-program-chest"), LIBRARY)
        self.assertEqual("met", verdict(rows, "Focus"))

    def test_a_focus_scenario_is_judged_by_the_focus_ranges_not_the_plain_volume_range(self):
        rows = checks.score(run(program(self.CHEST_16, focus=["Chest"]), "new-program-chest"), LIBRARY)
        self.assertNotIn("Sets per muscle", [r["target"] for r in rows])

    def test_a_scenario_without_a_focus_is_not_scored_on_one(self):
        self.assertNotIn("Focus", [r["target"] for r in checks.score(run(program(self.CHEST_16)), LIBRARY)])


def workout(groups, scenario="workout-45", name="w"):
    return {"scenario": scenario, "ended": "WorkoutReview", "replies": [{"text": "", "toolCalls": [{"name": "propose_workout", "input": {"name": name, "summary": "s", "groups": groups}}]}]}


def straight(exercise, *sets):
    return {"slots": [{"id": exercise, "exercise": exercise, "sets": list(sets)}]}


class Time(unittest.TestCase):
    def test_a_workout_of_two_exercises_with_two_minute_rests_fits_forty_five_minutes(self):
        # 2 exercises x (3 sets x (45 s + 120 s) + 2 min warm-up) = 2 x 10.25 = 20.5 min
        groups = [straight("bench", line("6-8", 1, count=3)), straight("curl", line("10-12", 1, count=3))]
        self.assertEqual("met", verdict(checks.score(workout(groups), LIBRARY), "Time"))

    def test_eight_exercises_do_not_fit_forty_five_minutes(self):
        groups = [straight("bench", line("6-8", 1, count=3)) for _ in range(8)]
        row = next(r for r in checks.score(workout(groups), LIBRARY) if r["target"] == "Time")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("82", row["detail"])

    def test_a_workout_is_scored_on_how_hard_but_not_on_weekly_volume(self):
        targets = [r["target"] for r in checks.score(workout([straight("bench", line("6-8", 1))]), LIBRARY)]
        self.assertEqual(["Reps in reserve", "Failure", "Rep ranges", "Time"], targets)

    def test_a_program_day_is_held_to_the_minutes_of_the_training_day_it_lands_on(self):
        # Day A lands on Wednesday (45 min), B on Friday and C on Monday (60 min each): 8 x 10.25 = 82 min fits neither.
        # A day of 45.75 min fits the 60 but not the 45.
        long_enough = [("bench", [line("6-8", 1, count=3)])] * 4 + [("curl", [line("10-12", 1, count=1)])]
        rows = checks.score(run(program([[("curl", [line("10-12", 1)])], long_enough, long_enough])), LIBRARY)
        self.assertEqual("met", verdict(rows, "Time"))
        rows = checks.score(run(program([long_enough, [("curl", [line("10-12", 1)])], [("curl", [line("10-12", 1)])]])), LIBRARY)
        self.assertEqual("missed", verdict(rows, "Time"))

    def test_a_program_day_is_held_to_its_minutes(self):
        long_day = [("bench", [line("6-8", 1, count=3)])] * 8
        rows = checks.score(run(program([long_day, [("curl", [line("10-12", 1)])], [("curl", [line("10-12", 1)])]])), LIBRARY)
        self.assertEqual("missed", verdict(rows, "Time"))


class NoProposal(unittest.TestCase):
    def test_a_program_scenario_that_ends_in_words_misses_the_proposal(self):
        rows = checks.score({"scenario": "new-program", "ended": "Words", "replies": [{"text": "Train as planned.", "toolCalls": []}]}, LIBRARY)
        self.assertEqual([("Proposal", "missed")], [(r["target"], r["verdict"]) for r in rows])


PLAN = {"blocks": [{"days": [{"id": "a", "groups": [
    {"slots": [{"id": "bench", "exercise": "bench"}]}, {"slots": [{"id": "curl_slot", "exercise": "curl"}]}, {"slots": [{"id": "squat_slot", "exercise": "squat"}]}]}]}]}


def change(ops, summary="s"):
    return {"scenario": "hurts-elbow", "ended": "ChangeReview", "replies": [{"text": "", "toolCalls": [{"name": "propose_change", "input": {"summary": summary, "scope": "rest_of_block", "operations": ops}}]}]}


class Pain(unittest.TestCase):
    def test_swapping_the_pressing_slot_and_keeping_the_curl_is_met(self):
        rows = checks.score(change([{"op": "swap", "slot": "bench", "exercise": "pec_deck", "reason": "elbow"}]), LIBRARY, PLAN)
        self.assertEqual("met", verdict(rows, "Swaps the painful lift"))
        self.assertEqual("met", verdict(rows, "Keeps the rest of the session"))

    def test_swapping_a_press_for_a_kinder_press_still_counts(self):
        rows = checks.score(change([{"op": "swap", "slot": "bench", "exercise": "bench", "reason": "x"}]), LIBRARY, PLAN)
        self.assertEqual("met", verdict(rows, "Swaps the painful lift"))

    def test_taking_out_the_curl_that_works_the_same_elbow_keeps_the_session(self):
        rows = checks.score(change([{"op": "swap", "slot": "bench", "exercise": "pec_deck", "reason": "x"}, {"op": "remove", "slot": "curl_slot", "reason": "elbow"}]), LIBRARY, PLAN)
        self.assertEqual("met", verdict(rows, "Keeps the rest of the session"))

    def test_leaving_the_press_alone_misses(self):
        rows = checks.score(change([{"op": "swap", "slot": "curl_slot", "exercise": "curl", "reason": "x"}]), LIBRARY, PLAN)
        self.assertEqual("missed", verdict(rows, "Swaps the painful lift"))

    def test_removing_every_slot_does_not_keep_the_session(self):
        ops = [{"op": "remove", "slot": "bench", "reason": "x"}, {"op": "remove", "slot": "squat_slot", "reason": "x"}]
        self.assertEqual("missed", verdict(checks.score(change(ops), LIBRARY, PLAN), "Keeps the rest of the session"))


class UnaskedTargets(unittest.TestCase):
    DAY = [[("bench", [line("6-8", 1, count=4)])]]

    def test_a_program_without_targets_is_met(self):
        self.assertEqual("met", verdict(checks.score(run(program(self.DAY)), LIBRARY), "No unasked targets"))

    def test_targets_nobody_asked_for_miss_and_name_the_fields(self):
        row = next(r for r in checks.score(run(program(self.DAY, targets={"goal": "both", "volume": "8-14"})), LIBRARY) if r["target"] == "No unasked targets")
        self.assertEqual("missed", row["verdict"])
        self.assertIn("goal, volume", row["detail"])

    def test_empty_targets_count_as_none(self):
        self.assertEqual("met", verdict(checks.score(run(program(self.DAY, targets={})), LIBRARY), "No unasked targets"))

    def test_a_workout_is_not_scored_on_it(self):
        self.assertNotIn("No unasked targets", [r["target"] for r in checks.score(workout([straight("bench", line("6-8", 1))]), LIBRARY)])


if __name__ == "__main__":
    unittest.main()
