package com.sorobanquest.training.api

import com.sorobanquest.shared.model.Page
import com.sorobanquest.shared.model.PageRequest
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import java.time.Instant

/** Вид тренировки. */
enum class DrillKind {
    /** Флеш-анзан: числа показываются по очереди, нужно назвать сумму. */
    FLASH_ANZAN,

    /** Цепочка сложений и вычитаний «как на соробане» (промежуточная сумма не уходит в минус). */
    ADD_SUB_CHAIN,

    /** Умножение. */
    MULTIPLICATION,

    /** Деление без остатка. */
    DIVISION,

    /** Смешанный режим: вид задания выбирается для каждого номера. */
    MIXED,
}

enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }

/** Описание тренировки для экрана выбора. */
data class DrillDescriptor(
    val kind: DrillKind,
    val title: String,
    val description: String,
    val minDifficulty: Int,
    val maxDifficulty: Int,
    val recommendedTaskCount: Int,
)

/** Условие задания, передаваемое клиенту. Правильный ответ наружу не выдаётся. */
sealed interface TaskPromptView {
    /** Последовательность чисел, которые нужно сложить. */
    data class Sequence(
        val numbers: List<Int>,
        val flashIntervalMs: Int,
    ) : TaskPromptView

    /** Бинарная операция: left (op) right. */
    data class Binary(
        val left: Int,
        val right: Int,
        val operator: String,
    ) : TaskPromptView
}

data class TaskView(
    val index: Int,
    val kind: DrillKind,
    val prompt: TaskPromptView,
    val timeLimitMs: Long,
)

data class SessionView(
    val sessionId: SessionId,
    val userId: UserId,
    val kind: DrillKind,
    val difficulty: Int,
    val taskCount: Int,
    val status: SessionStatus,
    val answeredCount: Int,
    val correctCount: Int,
    val accuracy: Double,
    val score: Int,
    val bestCombo: Int,
    val currentCombo: Int,
    val totalTimeMs: Long,
    val startedAt: Instant,
    val completedAt: Instant?,
    val currentTask: TaskView?,
)

data class AnswerResult(
    val correct: Boolean,
    val givenAnswer: Long,
    val expectedAnswer: Long,
    val awardedPoints: Int,
    val combo: Int,
    /** true, если этот номер задания уже был отвечен ранее и результат взят из истории. */
    val duplicate: Boolean,
    val session: SessionView,
    val nextTask: TaskView?,
)

data class SessionSummary(
    val sessionId: SessionId,
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
)

data class AnswerRecord(
    val taskIndex: Int,
    val givenAnswer: Long,
    val expectedAnswer: Long,
    val correct: Boolean,
    val elapsedMs: Int,
    val awardedPoints: Int,
    val answeredAt: Instant,
)

data class StartSessionCommand(
    val userId: UserId,
    val kind: DrillKind,
    val difficulty: Int,
    val taskCount: Int,
)

data class SubmitAnswerCommand(
    val userId: UserId,
    val sessionId: SessionId,
    val taskIndex: Int,
    val answer: Long,
    val elapsedMs: Long,
)

/**
 * Публичный контракт модуля тренировок. Любой транспорт (REST, gRPC, GraphQL)
 * работает только через него.
 */
interface TrainingService {
    fun listDrills(): List<DrillDescriptor>

    fun startSession(command: StartSessionCommand): SessionView

    fun submitAnswer(command: SubmitAnswerCommand): AnswerResult

    fun getSession(userId: UserId, sessionId: SessionId): SessionView

    fun listAnswers(userId: UserId, sessionId: SessionId): List<AnswerRecord>

    fun abandonSession(userId: UserId, sessionId: SessionId): SessionView

    fun history(userId: UserId, pageRequest: PageRequest): Page<SessionSummary>
}
