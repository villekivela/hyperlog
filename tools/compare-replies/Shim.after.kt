package com.hyperlog.app.coach

import com.hyperlog.app.gym.Gym
import com.hyperlog.app.library.Exercise
import com.hyperlog.app.program.ActiveProgram
import com.hyperlog.app.session.PastSessionItem
import com.hyperlog.app.week.WeekdayPlan
import java.io.File
import java.time.LocalDate

/** The frame with the training style at its defaults, as the wizard's Done with nothing changed leaves it, and the shipped principles' sentences. */
internal fun compareFrame(
    plan: ActiveProgram, sessions: List<PastSessionItem>, library: List<Exercise>, gyms: List<Gym>, weekdayPlan: WeekdayPlan, today: LocalDate,
): CheckInFrame = CheckInContext.frame(
    plan, sessions, library, gyms, today = today, weekdayPlan = weekdayPlan,
    style = TrainingStyle(sentences = Principles.parse(File("src/main/assets/${Principles.ASSET}").readText())),
)
