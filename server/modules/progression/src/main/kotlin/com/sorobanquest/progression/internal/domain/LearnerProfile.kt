package com.sorobanquest.progression.internal.domain

import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.UserId
import java.time.Instant
import java.time.LocalDate

/** Результат применения тренировки к профилю. */
internal data class ProfileUpdate(
    val profile: LearnerProfile,
    val awardedXp: Int,
    val levelUp: Boolean,
    val streakContinued: Boolean,
)

/**
 * Профиль ученика — агрегат модуля прогресса.
 * Содержит накопительные показатели и правила их изменения; хранение и события
 * остаются снаружи.
 */
internal data class LearnerProfile(
    val userId: UserId,
    val totalXp: Long,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastTrainingDate: LocalDate?,
    val totalSessions: Int,
    val totalTasks: Int,
    val totalCorrect: Int,
    val dailyGoalXp: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
) {
    val level: Int get() = LevelCurve.levelFor(totalXp)
    val accuracy: Double get() = if (totalTasks == 0) 0.0 else totalCorrect.toDouble() / totalTasks

    /**
     * Серия: тренировка в тот же день её не меняет, на следующий — продлевает,
     * пропуск хотя бы одного дня — обнуляет.
     */
    fun applySession(outcome: SessionOutcome, trainingDate: LocalDate, now: Instant): ProfileUpdate {
        val streak = when {
            lastTrainingDate == null -> 1
            trainingDate == lastTrainingDate -> currentStreakDays.coerceAtLeast(1)
            trainingDate == lastTrainingDate.plusDays(1) -> currentStreakDays + 1
            trainingDate.isAfter(lastTrainingDate) -> 1
            else -> currentStreakDays.coerceAtLeast(1) // тренировка «задним числом» серию не ломает
        }
        val xp = XpPolicy.xpFor(outcome, streak)
        val newTotalXp = totalXp + xp
        val updated = copy(
            totalXp = newTotalXp,
            currentStreakDays = streak,
            longestStreakDays = maxOf(longestStreakDays, streak),
            lastTrainingDate = maxOfDates(lastTrainingDate, trainingDate),
            totalSessions = totalSessions + 1,
            totalTasks = totalTasks + outcome.taskCount,
            totalCorrect = totalCorrect + outcome.correctCount,
            updatedAt = now,
            version = version + 1,
        )
        return ProfileUpdate(
            profile = updated,
            awardedXp = xp,
            levelUp = updated.level > level,
            streakContinued = streak > currentStreakDays,
        )
    }

    fun withDailyGoal(goalXp: Int, now: Instant): LearnerProfile {
        if (goalXp !in MIN_DAILY_GOAL..MAX_DAILY_GOAL) {
            throw ValidationException(
                "Дневная цель должна быть в диапазоне $MIN_DAILY_GOAL..$MAX_DAILY_GOAL опыта",
                mapOf("dailyGoalXp" to goalXp),
            )
        }
        return copy(dailyGoalXp = goalXp, updatedAt = now, version = version + 1)
    }

    private fun maxOfDates(a: LocalDate?, b: LocalDate): LocalDate = if (a == null || b.isAfter(a)) b else a

    companion object {
        const val DEFAULT_DAILY_GOAL = 100
        const val MIN_DAILY_GOAL = 20
        const val MAX_DAILY_GOAL = 5000

        fun createFor(userId: UserId, now: Instant) = LearnerProfile(
            userId = userId,
            totalXp = 0,
            currentStreakDays = 0,
            longestStreakDays = 0,
            lastTrainingDate = null,
            totalSessions = 0,
            totalTasks = 0,
            totalCorrect = 0,
            dailyGoalXp = DEFAULT_DAILY_GOAL,
            createdAt = now,
            updatedAt = now,
            version = 0,
        )
    }
}
