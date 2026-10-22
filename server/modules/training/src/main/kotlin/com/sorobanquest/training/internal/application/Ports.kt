package com.sorobanquest.training.internal.application

import com.sorobanquest.shared.model.Page
import com.sorobanquest.shared.model.PageRequest
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.internal.domain.PracticeSession
import com.sorobanquest.training.internal.domain.SubmittedAnswer

/**
 * Порты хранения. Прикладной слой знает только эти интерфейсы, поэтому
 * PostgreSQL-адаптер можно заменить на любой другой, не трогая бизнес-логику.
 */
internal interface PracticeSessionRepository {
    fun insert(session: PracticeSession)

    fun findById(id: SessionId): PracticeSession?

    /** Чтение с блокировкой строки (SELECT ... FOR UPDATE) — защита от гонок при ответах. */
    fun findByIdForUpdate(id: SessionId): PracticeSession?

    /** Оптимистичная блокировка по полю version; вернёт false, если состояние устарело. */
    fun update(session: PracticeSession): Boolean

    fun countActive(userId: UserId): Int

    fun findPage(userId: UserId, pageRequest: PageRequest): Page<PracticeSession>
}

internal interface AnswerRepository {
    /** Вставка ответа; false, если ответ на этот номер задания уже существует. */
    fun insertIfAbsent(answer: SubmittedAnswer): Boolean

    fun find(sessionId: SessionId, taskIndex: Int): SubmittedAnswer?

    fun listBySession(sessionId: SessionId): List<SubmittedAnswer>
}

/** Источник seed-ов для новых сессий (в тестах подменяется на фиксированный). */
internal fun interface SeedGenerator {
    fun newSeed(): Long
}

/** Настройки модуля, вынесенные из кода в конфигурацию приложения. */
internal data class TrainingSettings(
    val maxActiveSessions: Int = 5,
    val minTaskCount: Int = 5,
    val maxTaskCount: Int = 50,
)
