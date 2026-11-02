package com.hyperlog.app.coach

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.week.WeekdayPlan
import java.time.LocalDate

/** The frame before the training style: the context takes no style. */
internal fun compareFrame(
    plan: ActiveProgram, sessions: List<PastSessionItem>, library: List<Exercise>, gyms: List<Gym>, weekdayPlan: WeekdayPlan, today: LocalDate,
): CheckInFrame = CheckInContext.frame(plan, sessions, library, gyms, today = today, weekdayPlan = weekdayPlan)
