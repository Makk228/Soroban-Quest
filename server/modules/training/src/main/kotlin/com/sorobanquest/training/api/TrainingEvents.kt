package com.sorobanquest.training.api

import com.sorobanquest.shared.event.DomainEvent
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import java.time.Instant

/**
 * Событие завершения тренировки. На него подписан модуль прогресса
 * (опыт, серии, достижения). Модуль training ничего не знает о подписчиках.
 */
data class TrainingSessionCompleted(
    val sessionId: SessionId,
    val userId: UserId,
    val kind: DrillKind,
    val difficulty: Int,
    val taskCount: Int,
    val correctCount: Int,
    val score: Int,
    val bestCombo: Int,
    val totalTimeMs: Long,
    override val occurredAt: Instant,
) : DomainEvent {
    val accuracy: Double get() = if (taskCount == 0) 0.0 else correctCount.toDouble() / taskCount
    val perfect: Boolean get() = taskCount > 0 && correctCount == taskCount
    val averageAnswerMs: Long get() = if (taskCount == 0) 0 else totalTimeMs / taskCount
}
