package com.sorobanquest.progression.api

import com.sorobanquest.shared.event.DomainEvent
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import java.time.Instant
import java.time.LocalDate

/** Профиль ученика: всё, что показывает главный экран приложения. */
data class LearnerProfileView(
    val userId: UserId,
    val totalXp: Long,
    val level: Int,
    val xpIntoLevel: Long,
    val xpToNextLevel: Long,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val lastTrainingDate: LocalDate?,
    val totalSessions: Int,
    val totalTasks: Int,
    val totalCorrect: Int,
    val accuracy: Double,
    val dailyGoalXp: Int,
    val todayXp: Int,
    val dailyGoalReached: Boolean,
)

data class AchievementView(
    val code: String,
    val title: String,
    val description: String,
    val unlockedAt: Instant?,
) {
    val unlocked: Boolean get() = unlockedAt != null
}

data class SkillMasteryView(
    val kind: DrillKind,
    val sessions: Int,
    val tasksAnswered: Int,
    val correctAnswers: Int,
    val accuracy: Double,
    val bestScore: Int,
    val bestAverageAnswerMs: Long?,
    val updatedAt: Instant,
)

data class DailyProgressView(
    val date: LocalDate,
    val xp: Int,
    val sessions: Int,
    val goalReached: Boolean,
)

interface ProgressionService {
    fun profile(userId: UserId): LearnerProfileView

    /** Весь каталог достижений: открытые — с датой, остальные — с `unlockedAt = null`. */
    fun achievements(userId: UserId): List<AchievementView>

    fun skills(userId: UserId): List<SkillMasteryView>

    fun recentDays(userId: UserId, days: Int): List<DailyProgressView>

    fun updateDailyGoal(userId: UserId, dailyGoalXp: Int): LearnerProfileView
}

/**
 * Событие начисления опыта. Публикуется модулем прогресса и потребляется
 * таблицей лидеров — благодаря этому цифры в профиле и в рейтинге всегда совпадают.
 */
data class XpAwarded(
    val userId: UserId,
    val xp: Int,
    val totalXp: Long,
    val level: Int,
    val levelUp: Boolean,
    val sourceSessionId: SessionId,
    override val occurredAt: Instant,
) : DomainEvent
