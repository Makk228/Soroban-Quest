package com.sorobanquest.progression.internal.domain

/**
 * Кривая уровней: чтобы перейти с уровня L на L+1, нужно 100 × L опыта.
 * Итого порог уровня L равен 50 × L × (L−1) — то есть 0, 100, 300, 600, 1000 …
 *
 * Чистая арифметика без побочных эффектов: легко проверяется тестами и
 * одинаково считается на сервере и на клиенте.
 */
internal object LevelCurve {
    const val XP_PER_LEVEL_STEP = 100L
    const val MAX_LEVEL = 99

    fun thresholdFor(level: Int): Long {
        val bounded = level.coerceIn(1, MAX_LEVEL)
        return XP_PER_LEVEL_STEP * bounded * (bounded - 1) / 2
    }

    fun levelFor(totalXp: Long): Int {
        require(totalXp >= 0) { "Опыт не может быть отрицательным" }
        return (1..MAX_LEVEL).last { totalXp >= thresholdFor(it) }
    }

    fun xpIntoLevel(totalXp: Long): Long = totalXp - thresholdFor(levelFor(totalXp))

    /** Сколько опыта осталось до следующего уровня (0 — если достигнут максимум). */
    fun xpToNextLevel(totalXp: Long): Long {
        val level = levelFor(totalXp)
        if (level >= MAX_LEVEL) return 0
        return thresholdFor(level + 1) - totalXp
    }
}
