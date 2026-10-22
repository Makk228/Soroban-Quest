package com.sorobanquest.training.internal.application

import com.sorobanquest.shared.error.ConflictException
import com.sorobanquest.shared.error.ForbiddenException
import com.sorobanquest.shared.error.NotFoundException
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.model.Page
import com.sorobanquest.shared.model.PageRequest
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.shared.tx.TransactionRunner
import com.sorobanquest.training.api.AnswerRecord
import com.sorobanquest.training.api.AnswerResult
import com.sorobanquest.training.api.DrillDescriptor
import com.sorobanquest.training.api.SessionSummary
import com.sorobanquest.training.api.SessionView
import com.sorobanquest.training.api.StartSessionCommand
import com.sorobanquest.training.api.SubmitAnswerCommand
import com.sorobanquest.training.api.TaskPromptView
import com.sorobanquest.training.api.TaskView
import com.sorobanquest.training.api.TrainingService
import com.sorobanquest.training.api.TrainingSessionCompleted
import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.GeneratedTask
import com.sorobanquest.training.internal.domain.PracticeSession
import com.sorobanquest.training.internal.domain.SubmittedAnswer
import com.sorobanquest.training.internal.domain.TaskPrompt
import com.sorobanquest.training.internal.domain.generator.TaskGeneratorRegistry
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock

private val log = KotlinLogging.logger {}

/**
 * Прикладной слой модуля тренировок: оркестрирует домен, хранилище и события.
 * Не содержит ни SQL, ни знаний об HTTP — только сценарии использования.
 */
internal class TrainingServiceImpl(
    private val sessions: PracticeSessionRepository,
    private val answers: AnswerRepository,
    private val generators: TaskGeneratorRegistry,
    private val transactions: TransactionRunner,
    private val events: DomainEventPublisher,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val seeds: SeedGenerator,
    private val settings: TrainingSettings,
) : TrainingService {

    override fun listDrills(): List<DrillDescriptor> = DrillCatalog.descriptors

    override fun startSession(command: StartSessionCommand): SessionView {
        val difficulty = Difficulty.of(command.difficulty)
        if (!generators.supports(command.kind)) {
            throw ValidationException("Неизвестный вид тренировки", mapOf("kind" to command.kind.name))
        }
        if (command.taskCount !in settings.minTaskCount..settings.maxTaskCount) {
            throw ValidationException(
                "Количество заданий должно быть в диапазоне ${settings.minTaskCount}..${settings.maxTaskCount}",
                mapOf("taskCount" to command.taskCount),
            )
        }

        return transactions.required {
            val active = sessions.countActive(command.userId)
            if (active >= settings.maxActiveSessions) {
                throw ConflictException(
                    "Слишком много незавершённых тренировок (${active}). Заверши или прерви их, прежде чем начинать новую.",
                    mapOf("activeSessions" to active, "limit" to settings.maxActiveSessions),
                )
            }

            val session = PracticeSession.start(
                id = SessionId(ids.newId()),
                userId = command.userId,
                kind = command.kind,
                difficulty = difficulty,
                seed = seeds.newSeed(),
                taskCount = command.taskCount,
                generatorVersion = TaskGeneratorRegistry.GENERATOR_VERSION,
                now = clock.instant(),
            )
            sessions.insert(session)
            log.info { "Начата тренировка ${session.id} пользователя ${session.userId}: ${session.kind}, сложность ${difficulty.level}, заданий ${session.taskCount}" }
            session.toView(currentTaskOf(session))
        }
    }

    override fun submitAnswer(command: SubmitAnswerCommand): AnswerResult = transactions.required {
        val session = sessions.findByIdForUpdate(command.sessionId)
            ?: throw NotFoundException("Тренировочная сессия", command.sessionId)
        session.requireOwnedBy(command.userId)

        // Повторная отправка того же номера задания (ретрай клиента, дубль запроса)
        // не меняет состояние: возвращаем ранее сохранённый результат.
        answers.find(command.sessionId, command.taskIndex)?.let { existing ->
            log.debug { "Повторный ответ на задание №${command.taskIndex} сессии ${command.sessionId} — отдаём сохранённый результат" }
            return@required existing.toResult(session, duplicate = true)
        }

        val task = generators.generate(session.kind, command.taskIndex, session.difficulty, session.seed)
        val now = clock.instant()
        val application = session.applyAnswer(
            taskIndex = command.taskIndex,
            givenAnswer = command.answer,
            expectedAnswer = task.answer,
            elapsedMs = command.elapsedMs,
            now = now,
        )

        if (!answers.insertIfAbsent(application.answer)) {
            // Параллельный запрос успел записать ответ на этот же номер задания.
            throw ConflictException(
                "Ответ на задание №${command.taskIndex} уже был засчитан",
                mapOf("taskIndex" to command.taskIndex),
            )
        }
        if (!sessions.update(application.session)) {
            throw ConflictException("Состояние сессии изменилось, повтори запрос", mapOf("sessionId" to command.sessionId.toString()))
        }

        if (application.completed) {
            val updated = application.session
            log.info { "Тренировка ${updated.id} завершена: ${updated.correctCount}/${updated.taskCount}, очков ${updated.score}" }
            events.publish(
                TrainingSessionCompleted(
                    sessionId = updated.id,
                    userId = updated.userId,
                    kind = updated.kind,
                    difficulty = updated.difficulty.level,
                    taskCount = updated.taskCount,
                    correctCount = updated.correctCount,
                    score = updated.score,
                    bestCombo = updated.bestCombo,
                    totalTimeMs = updated.totalTimeMs,
                    occurredAt = now,
                ),
            )
        }

        application.answer.toResult(application.session, duplicate = false)
    }

    override fun getSession(userId: UserId, sessionId: SessionId): SessionView {
        val session = sessions.findById(sessionId) ?: throw NotFoundException("Тренировочная сессия", sessionId)
        session.requireOwnedBy(userId)
        return session.toView(currentTaskOf(session))
    }

    override fun listAnswers(userId: UserId, sessionId: SessionId): List<AnswerRecord> {
        val session = sessions.findById(sessionId) ?: throw NotFoundException("Тренировочная сессия", sessionId)
        session.requireOwnedBy(userId)
        return answers.listBySession(sessionId).map {
            AnswerRecord(
                taskIndex = it.taskIndex,
                givenAnswer = it.givenAnswer,
                expectedAnswer = it.expectedAnswer,
                correct = it.correct,
                elapsedMs = it.elapsedMs,
                awardedPoints = it.awardedPoints,
                answeredAt = it.answeredAt,
            )
        }
    }

    override fun abandonSession(userId: UserId, sessionId: SessionId): SessionView = transactions.required {
        val session = sessions.findByIdForUpdate(sessionId)
            ?: throw NotFoundException("Тренировочная сессия", sessionId)
        session.requireOwnedBy(userId)
        val abandoned = session.abandon(clock.instant())
        if (!sessions.update(abandoned)) {
            throw ConflictException("Состояние сессии изменилось, повтори запрос", mapOf("sessionId" to sessionId.toString()))
        }
        log.info { "Тренировка $sessionId прервана пользователем $userId" }
        abandoned.toView(currentTask = null)
    }

    override fun history(userId: UserId, pageRequest: PageRequest): Page<SessionSummary> =
        sessions.findPage(userId, pageRequest).map { it.toSummary() }

    // --- внутренние преобразования ---

    private fun PracticeSession.requireOwnedBy(userId: UserId) {
        if (this.userId != userId) {
            throw ForbiddenException("Тренировочная сессия принадлежит другому пользователю")
        }
    }

    private fun currentTaskOf(session: PracticeSession): TaskView? =
        session.currentTaskIndex?.let { index ->
            generators.generate(session.kind, index, session.difficulty, session.seed).toView()
        }

    private fun SubmittedAnswer.toResult(session: PracticeSession, duplicate: Boolean) = AnswerResult(
        correct = correct,
        givenAnswer = givenAnswer,
        expectedAnswer = expectedAnswer,
        awardedPoints = awardedPoints,
        combo = session.currentCombo,
        duplicate = duplicate,
        session = session.toView(currentTaskOf(session)),
        nextTask = currentTaskOf(session),
    )

    private fun PracticeSession.toView(currentTask: TaskView?) = SessionView(
        sessionId = id,
        userId = userId,
        kind = kind,
        difficulty = difficulty.level,
        taskCount = taskCount,
        status = status,
        answeredCount = answeredCount,
        correctCount = correctCount,
        accuracy = accuracy,
        score = score,
        bestCombo = bestCombo,
        currentCombo = currentCombo,
        totalTimeMs = totalTimeMs,
        startedAt = startedAt,
        completedAt = completedAt,
        currentTask = currentTask,
    )

    private fun PracticeSession.toSummary() = SessionSummary(
        sessionId = id,
        kind = kind,
        difficulty = difficulty.level,
        status = status,
        taskCount = taskCount,
        answeredCount = answeredCount,
        correctCount = correctCount,
        accuracy = accuracy,
        score = score,
        bestCombo = bestCombo,
        totalTimeMs = totalTimeMs,
        startedAt = startedAt,
        completedAt = completedAt,
    )
}

private fun GeneratedTask.toView() = TaskView(
    index = index,
    kind = kind,
    prompt = prompt.toView(),
    timeLimitMs = timeLimitMs,
)

private fun TaskPrompt.toView(): TaskPromptView = when (this) {
    is TaskPrompt.Sequence -> TaskPromptView.Sequence(numbers, flashIntervalMs)
    is TaskPrompt.Binary -> TaskPromptView.Binary(left, right, operator.symbol)
}
