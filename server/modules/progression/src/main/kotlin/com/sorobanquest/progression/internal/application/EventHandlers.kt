package com.sorobanquest.progression.internal.application

import com.sorobanquest.identity.api.UserRegistered
import com.sorobanquest.progression.api.XpAwarded
import com.sorobanquest.progression.internal.domain.AchievementCatalog
import com.sorobanquest.progression.internal.domain.AchievementContext
import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.SessionOutcome
import com.sorobanquest.progression.internal.domain.SkillMastery
import com.sorobanquest.shared.event.DomainEventHandler
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.shared.tx.TransactionRunner
import com.sorobanquest.training.api.TrainingSessionCompleted
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val log = KotlinLogging.logger {}

/** Создание профиля ученика сразу после регистрации. */
internal class UserRegisteredHandler(
    private val profiles: LearnerProfileRepository,
) : DomainEventHandler<UserRegistered> {

    override val eventType = UserRegistered::class.java

    override fun handle(event: UserRegistered) {
        val created = profiles.insertIfAbsent(LearnerProfile.createFor(event.userId, event.occurredAt))
        if (created) {
            log.info { "Создан профиль ученика для ${event.userId}" }
        }
    }
}

/**
 * Главный сценарий игрового слоя: начисление опыта, продление серии,
 * обновление статистики по виду тренировки и выдача достижений.
 *
 * Выполняется в той же транзакции, что и завершение тренировки, поэтому
 * очки и опыт не могут разъехаться. Профиль читается с блокировкой строки —
 * две параллельно завершённые тренировки не потеряют начисления.
 */
internal class TrainingSessionCompletedHandler(
    private val profiles: LearnerProfileRepository,
    private val skills: SkillMasteryRepository,
    private val achievements: AchievementRepository,
    private val dailyProgress: DailyProgressRepository,
    private val transactions: TransactionRunner,
    private val events: DomainEventPublisher,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : DomainEventHandler<TrainingSessionCompleted> {

    override val eventType = TrainingSessionCompleted::class.java

    override fun handle(event: TrainingSessionCompleted) {
        transactions.required {
            val now = clock.instant()
            val profile = ensureProfile(event.userId, now)
            val outcome = SessionOutcome(
                difficulty = event.difficulty,
                taskCount = event.taskCount,
                correctCount = event.correctCount,
                score = event.score,
                bestCombo = event.bestCombo,
                totalTimeMs = event.totalTimeMs,
            )
            val trainingDate = LocalDate.ofInstant(event.occurredAt, zoneId)

            val update = profile.applySession(outcome, trainingDate, now)
            if (!profiles.update(update.profile)) {
                // Строка заблокирована на чтении, поэтому расхождение версии означает
                // настоящую аномалию — лучше упасть и откатить транзакцию целиком.
                error("Не удалось обновить профиль ${event.userId}: состояние изменилось")
            }

            dailyProgress.addProgress(event.userId, trainingDate, update.awardedXp, sessions = 1)

            val mastery = skills.find(event.userId, event.kind)
                ?: SkillMastery.empty(event.userId, event.kind, now)
            skills.upsert(mastery.apply(outcome, now))

            unlockAchievements(update.profile, outcome, event, now)

            log.info {
                "Пользователь ${event.userId}: +${update.awardedXp} опыта (итого ${update.profile.totalXp}, " +
                    "уровень ${update.profile.level}, серия ${update.profile.currentStreakDays} дн.)"
            }

            events.publish(
                XpAwarded(
                    userId = event.userId,
                    xp = update.awardedXp,
                    totalXp = update.profile.totalXp,
                    level = update.profile.level,
                    levelUp = update.levelUp,
                    sourceSessionId = event.sessionId,
                    occurredAt = now,
                ),
            )
        }
    }

    private fun ensureProfile(userId: UserId, now: Instant): LearnerProfile =
        profiles.findForUpdate(userId) ?: run {
            // Подстраховка: профиль мог не создаться, если пользователь появился
            // до подключения модуля прогресса.
            profiles.insertIfAbsent(LearnerProfile.createFor(userId, now))
            profiles.findForUpdate(userId) ?: error("Не удалось создать профиль ученика $userId")
        }

    private fun unlockAchievements(
        profile: LearnerProfile,
        outcome: SessionOutcome,
        event: TrainingSessionCompleted,
        now: Instant,
    ) {
        val context = AchievementContext(profile, outcome, event.kind)
        AchievementCatalog.evaluate(context).forEach { definition ->
            if (achievements.unlockIfAbsent(profile.userId, definition.code, now)) {
                log.info { "Пользователь ${profile.userId} получил достижение «${definition.title}»" }
            }
        }
    }
}
