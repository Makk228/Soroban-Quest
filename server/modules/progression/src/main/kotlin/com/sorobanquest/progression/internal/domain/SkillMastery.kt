package com.sorobanquest.progression.internal.domain

import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import java.time.Instant

/** Накопленная статистика по конкретному виду тренировки. */
internal data class SkillMastery(
    val userId: UserId,
    val kind: DrillKind,
    val sessions: Int,
    val tasksAnswered: Int,
    val correctAnswers: Int,
    val bestScore: Int,
    val bestAverageAnswerMs: Long?,
    val updatedAt: Instant,
) {
    val accuracy: Double get() = if (tasksAnswered == 0) 0.0 else correctAnswers.toDouble() / tasksAnswered

    fun apply(outcome: SessionOutcome, now: Instant): SkillMastery {
        val averageAnswerMs = outcome.averageAnswerMs
        // Рекорд скорости засчитываем только за «честную» тренировку: точность от 80 %.
        val newBestAverage = if (outcome.accuracy >= 0.8 && averageAnswerMs > 0) {
            bestAverageAnswerMs?.let { minOf(it, averageAnswerMs) } ?: averageAnswerMs
        } else {
            bestAverageAnswerMs
        }
        return copy(
            sessions = sessions + 1,
            tasksAnswered = tasksAnswered + outcome.taskCount,
            correctAnswers = correctAnswers + outcome.correctCount,
            bestScore = maxOf(bestScore, outcome.score),
            bestAverageAnswerMs = newBestAverage,
            updatedAt = now,
        )
    }

    companion object {
        fun empty(userId: UserId, kind: DrillKind, now: Instant) = SkillMastery(
            userId = userId,
            kind = kind,
            sessions = 0,
            tasksAnswered = 0,
            correctAnswers = 0,
            bestScore = 0,
            bestAverageAnswerMs = null,
            updatedAt = now,
        )
    }
}
