package com.sorobanquest.progression

import com.sorobanquest.progression.internal.domain.SessionOutcome
import com.sorobanquest.progression.internal.domain.XpPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class XpPolicyTest {

    private fun outcome(
        correct: Int = 8,
        total: Int = 10,
        score: Int = 400,
        difficulty: Int = 5,
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
        val low = XpPolicy.xpFor(outcome(score = 100), streakDays = 1)
        val high = XpPolicy.xpFor(outcome(score = 1000), streakDays = 1)
        assertThat(high).isGreaterThan(low)
    }

    @Test
    fun `высокая точность даёт бонус`() {
        val sloppy = XpPolicy.xpFor(outcome(correct = 5, total = 10), streakDays = 1)
        val accurate = XpPolicy.xpFor(outcome(correct = 9, total = 10), streakDays = 1)
        assertThat(accurate).isGreaterThan(sloppy)
    }

    @Test
    fun `безошибочная тренировка даёт дополнительный бонус за сложность`() {
        val perfectEasy = XpPolicy.xpFor(outcome(correct = 10, total = 10, difficulty = 1), streakDays = 1)
        val perfectHard = XpPolicy.xpFor(outcome(correct = 10, total = 10, difficulty = 10), streakDays = 1)
        assertThat(perfectHard - perfectEasy).isEqualTo(
            XpPolicy.PERFECT_BONUS_PER_LEVEL * 10 - XpPolicy.PERFECT_BONUS_PER_LEVEL * 1,
        )
    }

    @Test
    fun `бонус серии ограничен сверху`() {
        val day1 = XpPolicy.xpFor(outcome(), streakDays = 1)
        val day11 = XpPolicy.xpFor(outcome(), streakDays = 11)
        val day100 = XpPolicy.xpFor(outcome(), streakDays = 100)

        assertThat(day11).isGreaterThan(day1)
        assertThat(day100).isEqualTo(day11)
    }

    @Test
    fun `тренировка без единого правильного ответа опыта не приносит`() {
        assertThat(XpPolicy.xpFor(outcome(correct = 0, score = 0), streakDays = 5)).isZero()
    }
}
