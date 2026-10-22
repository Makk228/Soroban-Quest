package com.sorobanquest.progression

import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.SessionOutcome
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LearnerProfileTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val today = LocalDate.of(2026, 10, 22)
    private val profile = LearnerProfile.createFor(UserId(UUID.randomUUID()), now)

    private val outcome = SessionOutcome(
        difficulty = 5,
        taskCount = 10,
        correctCount = 9,
        score = 500,
        bestCombo = 6,
        totalTimeMs = 20_000,
    )

    @Test
    fun `первая тренировка начинает серию и накопительную статистику`() {
        val update = profile.applySession(outcome, today, now)

        assertThat(update.awardedXp).isPositive()
        assertThat(update.profile.currentStreakDays).isEqualTo(1)
        assertThat(update.profile.longestStreakDays).isEqualTo(1)
        assertThat(update.profile.totalSessions).isEqualTo(1)
        assertThat(update.profile.totalTasks).isEqualTo(10)
        assertThat(update.profile.totalCorrect).isEqualTo(9)
        assertThat(update.profile.lastTrainingDate).isEqualTo(today)
        assertThat(update.profile.version).isEqualTo(profile.version + 1)
    }

    @Test
    fun `вторая тренировка в тот же день серию не удлиняет`() {
        val first = profile.applySession(outcome, today, now).profile
        val second = first.applySession(outcome, today, now).profile

        assertThat(second.currentStreakDays).isEqualTo(1)
        assertThat(second.totalSessions).isEqualTo(2)
    }

    @Test
    fun `тренировка на следующий день продлевает серию`() {
        val first = profile.applySession(outcome, today, now).profile
        val second = first.applySession(outcome, today.plusDays(1), now)

        assertThat(second.profile.currentStreakDays).isEqualTo(2)
        assertThat(second.streakContinued).isTrue()
    }

    @Test
    fun `пропуск дня обнуляет серию, но рекорд сохраняется`() {
        var current = profile
        repeat(5) { day ->
            current = current.applySession(outcome, today.plusDays(day.toLong()), now).profile
        }
        assertThat(current.currentStreakDays).isEqualTo(5)

        val afterGap = current.applySession(outcome, today.plusDays(10), now).profile

        assertThat(afterGap.currentStreakDays).isEqualTo(1)
        assertThat(afterGap.longestStreakDays).isEqualTo(5)
    }

    @Test
    fun `повышение уровня фиксируется в результате применения`() {
        var current = profile
        var levelUps = 0
        repeat(10) {
            val update = current.applySession(outcome, today, now)
            if (update.levelUp) levelUps++
            current = update.profile
        }

        assertThat(current.level).isGreaterThan(1)
        // одна тренировка может поднять сразу несколько уровней,
        // поэтому «событий повышения» не больше, чем набрано уровней
        assertThat(levelUps).isBetween(1, current.level - 1)
    }

    @Test
    fun `дневная цель проверяется на допустимый диапазон`() {
        assertThat(profile.withDailyGoal(300, now).dailyGoalXp).isEqualTo(300)
        assertThatThrownBy { profile.withDailyGoal(5, now) }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { profile.withDailyGoal(100_000, now) }.isInstanceOf(ValidationException::class.java)
    }
}
