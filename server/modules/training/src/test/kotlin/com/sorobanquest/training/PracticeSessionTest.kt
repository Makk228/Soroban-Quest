package com.sorobanquest.training

import com.sorobanquest.shared.error.RuleViolationException
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.api.SessionStatus
import com.sorobanquest.training.internal.domain.DifficultyFactory
import com.sorobanquest.training.internal.domain.PracticeSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PracticeSessionTest {
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private val sampleSession =
        PracticeSessionFactory.start(
            id = SessionId(UUID.randomUUID()),
            userId = UserId(UUID.randomUUID()),
            kind = DrillKind.FLASH_ANZAN,
            difficulty = DifficultyFactory.of(4),
            seed = 123L,
            taskCount = 3,
            generatorVersion = 1,
            now = now,
        )

    private fun session(taskCount: Int) = sampleSession.copy(taskCount = taskCount)

    @Test
    fun `правильный ответ увеличивает счётчики и комбо`() {
        val result = session(taskCount = 3).applyAnswer(taskIndex = 0, givenAnswer = 42, expectedAnswer = 42, elapsedMs = 1500, now = now)

        assertThat(result.answer.correct).isTrue()
        assertThat(result.session.correctCount).isEqualTo(1)
        assertThat(result.session.answeredCount).isEqualTo(1)
        assertThat(result.session.currentCombo).isEqualTo(1)
        assertThat(result.session.score).isPositive()
        assertThat(result.completed).isFalse()
    }

    @Test
    fun `ошибка обнуляет комбо, но лучший результат серии сохраняется`() {
        val first = session(taskCount = 5).applyAnswer(0, 10, 10, 1000, now).session
        val second = first.applyAnswer(1, 20, 20, 1000, now).session
        assertThat(second.currentCombo).isEqualTo(2)
        val current = second.applyAnswer(2, 1, 30, 1000, now).session

        assertThat(current.currentCombo).isZero()
        assertThat(current.bestCombo).isEqualTo(2)
        assertThat(current.correctCount).isEqualTo(2)
        assertThat(current.answeredCount).isEqualTo(3)
    }

    @Test
    fun `после последнего задания сессия завершается`() {
        val current = session(taskCount = 2).applyAnswer(0, 1, 1, 1000, now).session
        val last = current.applyAnswer(1, 2, 2, 1000, now)

        assertThat(last.completed).isTrue()
        assertThat(last.session.status).isEqualTo(SessionStatus.COMPLETED)
        assertThat(last.session.completedAt).isEqualTo(now)
        assertThat(last.session.currentTaskIndex).isNull()
    }

    @Test
    fun `ответ не по порядку отклоняется`() {
        assertThatThrownBy {
            session(
                taskCount = 3,
            ).applyAnswer(taskIndex = 2, givenAnswer = 1, expectedAnswer = 1, elapsedMs = 1000, now = now)
        }.isInstanceOf(RuleViolationException::class.java)
            .hasMessageContaining("Ожидается ответ на задание №0")
    }

    @Test
    fun `завершённая сессия больше не принимает ответы`() {
        val completed = session(taskCount = 1).applyAnswer(0, 1, 1, 500, now).session

        assertThatThrownBy { completed.applyAnswer(1, 1, 1, 500, now) }
            .isInstanceOf(RuleViolationException::class.java)
            .hasMessageContaining("завершена")
    }

    @Test
    fun `нереалистичное время ответа отклоняется`() {
        assertThatThrownBy { session(taskCount = 3).applyAnswer(0, 1, 1, elapsedMs = 0, now = now) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { session(taskCount = 3).applyAnswer(0, 1, 1, elapsedMs = 10_000_000, now = now) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `прерванную сессию нельзя прервать повторно`() {
        val abandoned = session(taskCount = 3).abandon(now)
        assertThat(abandoned.status).isEqualTo(SessionStatus.ABANDONED)

        assertThatThrownBy { abandoned.abandon(now) }.isInstanceOf(RuleViolationException::class.java)
    }

    @Test
    fun `сложность вне диапазона 1-10 не принимается`() {
        assertThatThrownBy { DifficultyFactory.of(0) }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { DifficultyFactory.of(11) }.isInstanceOf(ValidationException::class.java)
    }
}
