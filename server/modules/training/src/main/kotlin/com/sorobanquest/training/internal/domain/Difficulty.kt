package com.sorobanquest.training.internal.domain

import com.sorobanquest.shared.error.ValidationException
import kotlin.math.max
import kotlin.math.pow

/** Уровень сложности 1..10. Инвариант проверяется в конструкторе. */
internal data class Difficulty(val level: Int) {
    init {
        if (level !in MIN..MAX) {
            throw ValidationException(
                "Сложность должна быть в диапазоне $MIN..$MAX",
                mapOf("difficulty" to level),
            )
        }
    }

    companion object {
        const val MIN = 1
        const val MAX = 10

        fun of(level: Int): Difficulty = Difficulty(level)
    }
}

/**
 * Числовые параметры тренировки, выведенные из уровня сложности.
 * Вынесены в отдельный тип, чтобы правила «насколько сложно» жили в одном месте
 * и покрывались тестами, а генераторы оставались простыми.
 */
internal data class DifficultyProfile(
    val digits: Int,
    val terms: Int,
    val flashIntervalMs: Int,
    val targetAnswerMs: Long,
    val timeLimitMs: Long,
) {
    val minOperand: Int get() = if (digits <= 1) 1 else 10.0.pow(digits - 1).toInt()
    val maxOperand: Int get() = 10.0.pow(digits).toInt() - 1

    companion object {
        fun of(difficulty: Difficulty): DifficultyProfile {
            val level = difficulty.level
            val digits = 1 + (level - 1) / 3            // 1..4 разряда
            val terms = 3 + (level - 1) / 2             // 3..7 слагаемых
            val flashIntervalMs = max(400, 1600 - (level - 1) * 130)
            val targetAnswerMs = 1500L + terms * 600L + digits * 400L
            return DifficultyProfile(
                digits = digits,
                terms = terms,
                flashIntervalMs = flashIntervalMs,
                targetAnswerMs = targetAnswerMs,
                timeLimitMs = targetAnswerMs * 4,
            )
        }
    }
}
