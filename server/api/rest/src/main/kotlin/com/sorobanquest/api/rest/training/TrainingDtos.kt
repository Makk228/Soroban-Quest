package com.sorobanquest.api.rest.training

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sorobanquest.training.api.AnswerRecord
import com.sorobanquest.training.api.AnswerResult
import com.sorobanquest.training.api.DrillDescriptor
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.api.SessionStatus
import com.sorobanquest.training.api.SessionSummary
import com.sorobanquest.training.api.SessionView
import com.sorobanquest.training.api.TaskPromptView
import com.sorobanquest.training.api.TaskView
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.Instant

@Schema(description = "Запрос на старт тренировки")
data class StartSessionRequest(
    @field:NotNull(message = "Укажите вид тренировки")
    val kind: DrillKind?,

    @field:NotNull(message = "Укажите сложность")
    @field:Min(1) @field:Max(10)
    val difficulty: Int?,

    @field:NotNull(message = "Укажите количество заданий")
    @field:Min(5) @field:Max(50)
    val taskCount: Int?,
)

@Schema(description = "Ответ на задание")
data class SubmitAnswerRequest(
    @field:NotNull(message = "Укажите номер задания")
    @field:Min(0)
    val taskIndex: Int?,

    @field:NotNull(message = "Укажите ответ")
    val answer: Long?,

    @field:NotNull(message = "Укажите время решения в миллисекундах")
    @field:Min(1) @field:Max(600_000)
    val elapsedMs: Long?,
)

/** Условие задания; тип указывается полем `type`, чтобы клиенту было удобно разбирать JSON. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = PromptResponse.Sequence::class, name = "SEQUENCE"),
    JsonSubTypes.Type(value = PromptResponse.Binary::class, name = "BINARY"),
)
sealed interface PromptResponse {
    data class Sequence(val numbers: List<Int>, val flashIntervalMs: Int) : PromptResponse

    data class Binary(val left: Int, val right: Int, val operator: String) : PromptResponse

    companion object {
        fun of(prompt: TaskPromptView): PromptResponse = when (prompt) {
            is TaskPromptView.Sequence -> Sequence(prompt.numbers, prompt.flashIntervalMs)
            is TaskPromptView.Binary -> Binary(prompt.left, prompt.right, prompt.operator)
        }
    }
}

data class TaskResponse(
    val index: Int,
    val kind: DrillKind,
    val prompt: PromptResponse,
    val timeLimitMs: Long,
) {
    companion object {
        fun of(task: TaskView) = TaskResponse(task.index, task.kind, PromptResponse.of(task.prompt), task.timeLimitMs)
    }
}

data class SessionResponse(
    val sessionId: String,
    val kind: DrillKind,
    val difficulty: Int,
    val status: SessionStatus,
    val taskCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracy: Double,
    val score: Int,
    val currentCombo: Int,
    val bestCombo: Int,
    val totalTimeMs: Long,
    val startedAt: Instant,
    val completedAt: Instant?,
    val currentTask: TaskResponse?,
) {
    companion object {
        fun of(view: SessionView) = SessionResponse(
            sessionId = view.sessionId.toString(),
            kind = view.kind,
            difficulty = view.difficulty,
            status = view.status,
            taskCount = view.taskCount,
            answeredCount = view.answeredCount,
            correctCount = view.correctCount,
            accuracy = view.accuracy,
            score = view.score,
            currentCombo = view.currentCombo,
            bestCombo = view.bestCombo,
            totalTimeMs = view.totalTimeMs,
            startedAt = view.startedAt,
            completedAt = view.completedAt,
            currentTask = view.currentTask?.let(TaskResponse::of),
        )
    }
}

data class AnswerResponse(
    val correct: Boolean,
    val givenAnswer: Long,
    val expectedAnswer: Long,
    val awardedPoints: Int,
    val combo: Int,
    val duplicate: Boolean,
    val session: SessionResponse,
    val nextTask: TaskResponse?,
) {
    companion object {
        fun of(result: AnswerResult) = AnswerResponse(
            correct = result.correct,
            givenAnswer = result.givenAnswer,
            expectedAnswer = result.expectedAnswer,
            awardedPoints = result.awardedPoints,
            combo = result.combo,
            duplicate = result.duplicate,
            session = SessionResponse.of(result.session),
            nextTask = result.nextTask?.let(TaskResponse::of),
        )
    }
}

data class SessionSummaryResponse(
    val sessionId: String,
    val kind: DrillKind,
    val difficulty: Int,
    val status: SessionStatus,
    val taskCount: Int,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracy: Double,
    val score: Int,
    val bestCombo: Int,
    val totalTimeMs: Long,
    val startedAt: Instant,
    val completedAt: Instant?,
) {
    companion object {
        fun of(summary: SessionSummary) = SessionSummaryResponse(
            sessionId = summary.sessionId.toString(),
            kind = summary.kind,
            difficulty = summary.difficulty,
            status = summary.status,
            taskCount = summary.taskCount,
            answeredCount = summary.answeredCount,
            correctCount = summary.correctCount,
            accuracy = summary.accuracy,
            score = summary.score,
            bestCombo = summary.bestCombo,
            totalTimeMs = summary.totalTimeMs,
            startedAt = summary.startedAt,
            completedAt = summary.completedAt,
        )
    }
}

data class AnswerRecordResponse(
    val taskIndex: Int,
    val givenAnswer: Long,
    val expectedAnswer: Long,
    val correct: Boolean,
    val elapsedMs: Int,
    val awardedPoints: Int,
    val answeredAt: Instant,
) {
    companion object {
        fun of(record: AnswerRecord) = AnswerRecordResponse(
            taskIndex = record.taskIndex,
            givenAnswer = record.givenAnswer,
            expectedAnswer = record.expectedAnswer,
            correct = record.correct,
            elapsedMs = record.elapsedMs,
            awardedPoints = record.awardedPoints,
            answeredAt = record.answeredAt,
        )
    }
}

data class DrillResponse(
    val kind: DrillKind,
    val title: String,
    val description: String,
    val minDifficulty: Int,
    val maxDifficulty: Int,
    val recommendedTaskCount: Int,
) {
    companion object {
        fun of(descriptor: DrillDescriptor) = DrillResponse(
            kind = descriptor.kind,
            title = descriptor.title,
            description = descriptor.description,
            minDifficulty = descriptor.minDifficulty,
            maxDifficulty = descriptor.maxDifficulty,
            recommendedTaskCount = descriptor.recommendedTaskCount,
        )
    }
}
