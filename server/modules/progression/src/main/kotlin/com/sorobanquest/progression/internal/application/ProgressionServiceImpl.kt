package com.sorobanquest.progression.internal.application

import com.sorobanquest.progression.api.AchievementView
import com.sorobanquest.progression.api.DailyProgressView
import com.sorobanquest.progression.api.LearnerProfileView
import com.sorobanquest.progression.api.ProgressionService
import com.sorobanquest.progression.api.SkillMasteryView
import com.sorobanquest.progression.internal.domain.AchievementCatalog
import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.LevelCurve
import com.sorobanquest.shared.error.ConflictException
import com.sorobanquest.shared.error.NotFoundException
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.shared.tx.TransactionRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Запросы к прогрессу ученика + изменение дневной цели. */
internal class ProgressionServiceImpl(
    private val profiles: LearnerProfileRepository,
    private val achievements: AchievementRepository,
    private val skills: SkillMasteryRepository,
    private val dailyProgress: DailyProgressRepository,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ProgressionService {

    override fun profile(userId: UserId): LearnerProfileView {
        val profile = profiles.findById(userId) ?: throw NotFoundException("Профиль ученика", userId)
        return profile.toView(todayXp(userId))
    }

    override fun achievements(userId: UserId): List<AchievementView> {
        val unlocked = achievements.listUnlocked(userId)
        return AchievementCatalog.definitions.map {
            AchievementView(
                code = it.code,
                title = it.title,
                description = it.description,
                unlockedAt = unlocked[it.code],
            )
        }
    }

    override fun skills(userId: UserId): List<SkillMasteryView> = skills.listByUser(userId).map {
        SkillMasteryView(
            kind = it.kind,
            sessions = it.sessions,
            tasksAnswered = it.tasksAnswered,
            correctAnswers = it.correctAnswers,
            accuracy = it.accuracy,
            bestScore = it.bestScore,
            bestAverageAnswerMs = it.bestAverageAnswerMs,
            updatedAt = it.updatedAt,
        )
    }

    override fun recentDays(userId: UserId, days: Int): List<DailyProgressView> {
        if (days !in 1..365) {
            throw ValidationException("Период должен быть в диапазоне 1..365 дней", mapOf("days" to days))
        }
        val profile = profiles.findById(userId) ?: throw NotFoundException("Профиль ученика", userId)
        val from = today().minusDays((days - 1).toLong())
        val byDate = dailyProgress.listSince(userId, from).associateBy { it.date }
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val entry = byDate[date]
            DailyProgressView(
                date = date,
                xp = entry?.xp ?: 0,
                sessions = entry?.sessions ?: 0,
                goalReached = (entry?.xp ?: 0) >= profile.dailyGoalXp,
            )
        }
    }

    override fun updateDailyGoal(userId: UserId, dailyGoalXp: Int): LearnerProfileView = transactions.required {
        val profile = profiles.findForUpdate(userId) ?: throw NotFoundException("Профиль ученика", userId)
        val updated = profile.withDailyGoal(dailyGoalXp, clock.instant())
        if (!profiles.update(updated)) {
            throw ConflictException("Профиль изменился параллельно, повтори запрос")
        }
        updated.toView(todayXp(userId))
    }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zoneId)

    private fun todayXp(userId: UserId): Int = dailyProgress.find(userId, today())?.xp ?: 0

    private fun LearnerProfile.toView(todayXp: Int) = LearnerProfileView(
        userId = userId,
        totalXp = totalXp,
        level = level,
        xpIntoLevel = LevelCurve.xpIntoLevel(totalXp),
        xpToNextLevel = LevelCurve.xpToNextLevel(totalXp),
        currentStreakDays = currentStreakDays,
        longestStreakDays = longestStreakDays,
        lastTrainingDate = lastTrainingDate,
        totalSessions = totalSessions,
        totalTasks = totalTasks,
        totalCorrect = totalCorrect,
        accuracy = accuracy,
        dailyGoalXp = dailyGoalXp,
        todayXp = todayXp,
        dailyGoalReached = todayXp >= dailyGoalXp,
    )
}
