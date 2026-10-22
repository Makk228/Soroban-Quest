package com.sorobanquest.progression.internal.domain

import kotlin.math.roundToInt

/** Исходные данные для начисления опыта — то, что пришло из завершённой тренировки. */
internal data class SessionOutcome(
    val difficulty: Int,
    val taskCount: Int,
    val correctCount: Int,
    val score: Int,
    val bestCombo: Int,
    val totalTimeMs: Long,
) {
    val accuracy: Double get() = if (taskCount == 0) 0.0 else correctCount.toDouble() / taskCount
    val perfect: Boolean get() = taskCount > 0 && correctCount == taskCount
    val averageAnswerMs: Long get() = if (taskCount == 0) 0 else totalTimeMs / taskCount
}

/**
 * Опыт за тренировку:
 *   xp = очки / 2
 *      + бонус за точность ≥ 90 % (+20 %)
 *      + бонус за безошибочную тренировку (10 × сложность)
 *      + бонус серии (+5 % за каждый день серии, максимум +50 %)
 *
 * Очки отвечают за «результат конкретного забега», опыт — за долгий прогресс,
 * поэтому правила намеренно разделены между модулями training и progression.
 */
internal object XpPolicy {
    const val SCORE_TO_XP = 0.5
    const val ACCURACY_BONUS_THRESHOLD = 0.9
    const val ACCURACY_BONUS = 0.2
    const val PERFECT_BONUS_PER_LEVEL = 10
    const val STREAK_BONUS_PER_DAY = 0.05
    const val MAX_STREAK_BONUS = 0.5

    fun xpFor(outcome: SessionOutcome, streakDays: Int): Int {
        if (outcome.correctCount == 0) return 0
        val base = outcome.score * SCORE_TO_XP
        val accuracyMultiplier = if (outcome.accuracy >= ACCURACY_BONUS_THRESHOLD) 1 + ACCURACY_BONUS else 1.0
        val streakMultiplier = 1 + minOf((streakDays - 1).coerceAtLeast(0) * STREAK_BONUS_PER_DAY, MAX_STREAK_BONUS)
        val perfectBonus = if (outcome.perfect) PERFECT_BONUS_PER_LEVEL * outcome.difficulty else 0
        return (base * accuracyMultiplier * streakMultiplier).roundToInt() + perfectBonus
    }
}
