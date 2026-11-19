package com.sorobanquest.progression.internal.application

import com.sorobanquest.shared.model.UserId
import java.time.LocalDate

internal data class DailyProgress(
    val userId: UserId,
    val date: LocalDate,
    val xp: Int,
    val sessions: Int,
)
