package com.sorobanquest.progression

import com.sorobanquest.progression.internal.domain.SessionOutcome
import com.sorobanquest.progression.internal.domain.XpPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XpPolicyTest {
    private fun outcome(
        correct: Int,
        total: Int,
        score: Int,
        difficulty: Int,
    ) = SessionOutcome(
        difficulty = difficulty,
        taskCount = total,
        correctCount = correct,
        score = score,
        bestCombo = correct,
        totalTimeMs = total * 2000L,
    )

    @Test
    fun `опыт растёт вместе с очками`() {
        val low = XpPolicy.xpFor(outcome(score = 100, correct = 8, total = 10, difficulty = 5), streakDays = 1)
        val high = XpPolicy.xpFor(outcome(score = 1000, correct = 8, total = 10, difficulty = 5), streakDays = 1)
        assertThat(high).isGreaterThan(low)
    }

    @Test
    fun `высокая точность даёт бонус`() {
        val sloppy = XpPolicy.xpFor(outcome(correct = 5, total = 10, score = 400, difficulty = 5), streakDays = 1)
        val accurate = XpPolicy.xpFor(outcome(correct = 9, total = 10, score = 400, difficulty = 5), streakDays = 1)
        assertThat(accurate).isGreaterThan(sloppy)
    }

    @Test
    fun `безошибочная тренировка даёт дополнительный бонус за сложность`() {
        val perfectEasy = XpPolicy.xpFor(outcome(correct = 10, total = 10, difficulty = 1, score = 400), streakDays = 1)
        val perfectHard = XpPolicy.xpFor(outcome(correct = 10, total = 10, difficulty = 10, score = 400), streakDays = 1)
        assertThat(perfectHard - perfectEasy).isEqualTo(
            XpPolicy.PERFECT_BONUS_PER_LEVEL * 10 - XpPolicy.PERFECT_BONUS_PER_LEVEL * 1,
        )
    }

    @Test
    fun `бонус серии ограничен сверху`() {
        val day1 = XpPolicy.xpFor(outcome(correct = 8, total = 10, score = 400, difficulty = 5), streakDays = 1)
        val day11 = XpPolicy.xpFor(outcome(correct = 8, total = 10, score = 400, difficulty = 5), streakDays = 11)
        val day100 = XpPolicy.xpFor(outcome(correct = 8, total = 10, score = 400, difficulty = 5), streakDays = 100)

        assertThat(day11).isGreaterThan(day1)
        assertThat(day100).isEqualTo(day11)
    }

    @Test
    fun `тренировка без единого правильного ответа опыта не приносит`() {
        assertThat(XpPolicy.xpFor(outcome(correct = 0, score = 0, total = 10, difficulty = 5), streakDays = 5)).isZero()
    }
}
