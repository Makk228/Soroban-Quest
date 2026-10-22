package com.sorobanquest.training.internal.domain

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Правила начисления очков за ответ.
 *
 * points = базовая_стоимость(сложность) × коэффициент_скорости × множитель_комбо
 *
 *  - базовая стоимость растёт линейно со сложностью;
 *  - коэффициент скорости: 1.5 при ответе быстрее половины целевого времени,
 *    0.75 при ответе медленнее двойного целевого времени, между — линейно;
 *  - множитель комбо: +5% за каждый подряд правильный ответ, максимум +50%.
 *
 * Политика — чистая функция без побочных эффектов, поэтому легко тестируется.
 */
internal object ScoringPolicy {
    const val BASE_POINTS_PER_LEVEL = 10
    const val MAX_COMBO_STEPS = 10
    const val COMBO_STEP = 0.05

    fun pointsFor(difficulty: Difficulty, correct: Boolean, elapsedMs: Long, comboAfterAnswer: Int): Int {
        if (!correct) return 0
        val profile = DifficultyProfile.of(difficulty)
        val base = BASE_POINTS_PER_LEVEL * difficulty.level
        val speedFactor = speedFactor(elapsedMs, profile.targetAnswerMs)
        val comboMultiplier = 1.0 + min(comboAfterAnswer - 1, MAX_COMBO_STEPS).coerceAtLeast(0) * COMBO_STEP
        return (base * speedFactor * comboMultiplier).roundToInt()
    }

    fun speedFactor(elapsedMs: Long, targetMs: Long): Double {
        val fast = targetMs / 2.0
        val slow = targetMs * 2.0
        return when {
            elapsedMs <= fast -> 1.5
            elapsedMs >= slow -> 0.75
            else -> {
                // линейная интерполяция 1.5 -> 0.75 на отрезке [fast, slow]
                val ratio = (elapsedMs - fast) / (slow - fast)
                1.5 - ratio * 0.75
            }
        }
    }
}
