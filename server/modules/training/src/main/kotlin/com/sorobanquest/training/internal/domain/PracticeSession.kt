package com.sorobanquest.training.internal.domain

import com.sorobanquest.shared.error.RuleViolationException
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.api.SessionStatus
import java.time.Instant

/** Ответ пользователя, сохранённый в истории сессии. */
internal data class SubmittedAnswer(
    val sessionId: SessionId,
    val taskIndex: Int,
    val givenAnswer: Long,
    val expectedAnswer: Long,
    val correct: Boolean,
    val elapsedMs: Int,
    val awardedPoints: Int,
    val answeredAt: Instant,
)

/** Результат применения ответа к агрегату. */
internal data class AnswerApplication(
    val session: PracticeSession,
    val answer: SubmittedAnswer,
    val completed: Boolean,
)

/**
 * Агрегат «тренировочная сессия».
 *
 * Задания не хранятся в БД: сессия помнит seed, а задание с номером N всегда
 * воспроизводится детерминированно из пары (seed, N). Это значит, что правильный
 * ответ считает сервер, а клиент не может его подсмотреть или подменить.
 *
 * Класс неизменяемый: каждая операция возвращает новое состояние, а решение
 * о сохранении принимает прикладной слой.
 */
internal data class PracticeSession(
    val id: SessionId,
    val userId: UserId,
    val kind: DrillKind,
    val difficulty: Difficulty,
    val seed: Long,
    val taskCount: Int,
    val generatorVersion: Int,
    val status: SessionStatus,
    val answeredCount: Int,
    val correctCount: Int,
    val currentCombo: Int,
    val bestCombo: Int,
    val score: Int,
    val totalTimeMs: Long,
    val startedAt: Instant,
    val completedAt: Instant?,
    val version: Long,
) {
    val isActive: Boolean get() = status == SessionStatus.IN_PROGRESS

    val accuracy: Double get() = if (answeredCount == 0) 0.0 else correctCount.toDouble() / answeredCount

    /** Номер задания, которое сейчас должно быть показано, либо null если сессия закрыта. */
    val currentTaskIndex: Int? get() = if (isActive && answeredCount < taskCount) answeredCount else null

    fun applyAnswer(
        taskIndex: Int,
        givenAnswer: Long,
        expectedAnswer: Long,
        elapsedMs: Long,
        now: Instant,
    ): AnswerApplication {
        if (!isActive) {
            throw RuleViolationException(
                rule = "session_not_active",
                message = "Сессия уже завершена и не принимает ответы",
                details = mapOf("status" to status.name),
            )
        }
        if (taskIndex != answeredCount) {
            throw RuleViolationException(
                rule = "task_out_of_order",
                message = "Ожидается ответ на задание №$answeredCount, получен №$taskIndex",
                details = mapOf("expectedTaskIndex" to answeredCount, "receivedTaskIndex" to taskIndex),
            )
        }
        if (elapsedMs !in MIN_ELAPSED_MS..MAX_ELAPSED_MS) {
            throw ValidationException(
                "Время ответа должно быть в диапазоне $MIN_ELAPSED_MS..$MAX_ELAPSED_MS мс",
                mapOf("elapsedMs" to elapsedMs),
            )
        }

        val correct = givenAnswer == expectedAnswer
        val combo = if (correct) currentCombo + 1 else 0
        val points = ScoringPolicy.pointsFor(difficulty, correct, elapsedMs, combo)
        val newAnsweredCount = answeredCount + 1
        val completed = newAnsweredCount >= taskCount

        val updated = copy(
            answeredCount = newAnsweredCount,
            correctCount = correctCount + if (correct) 1 else 0,
            currentCombo = combo,
            bestCombo = maxOf(bestCombo, combo),
            score = score + points,
            totalTimeMs = totalTimeMs + elapsedMs,
            status = if (completed) SessionStatus.COMPLETED else status,
            completedAt = if (completed) now else completedAt,
            version = version + 1,
        )

        val answer = SubmittedAnswer(
            sessionId = id,
            taskIndex = taskIndex,
            givenAnswer = givenAnswer,
            expectedAnswer = expectedAnswer,
            correct = correct,
            elapsedMs = elapsedMs.toInt(),
            awardedPoints = points,
            answeredAt = now,
        )
        return AnswerApplication(updated, answer, completed)
    }

    fun abandon(now: Instant): PracticeSession {
        if (!isActive) {
            throw RuleViolationException(
                rule = "session_not_active",
                message = "Нельзя прервать уже закрытую сессию",
                details = mapOf("status" to status.name),
            )
        }
        return copy(status = SessionStatus.ABANDONED, completedAt = now, version = version + 1)
    }

    companion object {
        const val MIN_ELAPSED_MS = 1L
        const val MAX_ELAPSED_MS = 600_000L

        fun start(
            id: SessionId,
            userId: UserId,
            kind: DrillKind,
            difficulty: Difficulty,
            seed: Long,
            taskCount: Int,
            generatorVersion: Int,
            now: Instant,
        ) = PracticeSession(
            id = id,
            userId = userId,
            kind = kind,
            difficulty = difficulty,
            seed = seed,
            taskCount = taskCount,
            generatorVersion = generatorVersion,
            status = SessionStatus.IN_PROGRESS,
            answeredCount = 0,
            correctCount = 0,
            currentCombo = 0,
            bestCombo = 0,
            score = 0,
            totalTimeMs = 0,
            startedAt = now,
            completedAt = null,
            version = 0,
        )
    }
}
