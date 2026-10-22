package com.sorobanquest.progression.internal.application

import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.SkillMastery
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import java.time.Instant
import java.time.LocalDate

internal interface LearnerProfileRepository {
    /** Создание профиля; false — если он уже есть (обработка события регистрации идемпотентна). */
    fun insertIfAbsent(profile: LearnerProfile): Boolean

    fun findById(userId: UserId): LearnerProfile?

    /** Чтение с блокировкой строки: две одновременные тренировки не потеряют опыт. */
    fun findForUpdate(userId: UserId): LearnerProfile?

    fun update(profile: LearnerProfile): Boolean
}

internal interface AchievementRepository {
    /** Выдать достижение; false — если оно уже было выдано. */
    fun unlockIfAbsent(userId: UserId, code: String, unlockedAt: Instant): Boolean

    fun listUnlocked(userId: UserId): Map<String, Instant>
}

internal interface SkillMasteryRepository {
    fun find(userId: UserId, kind: DrillKind): SkillMastery?

    fun upsert(mastery: SkillMastery)

    fun listByUser(userId: UserId): List<SkillMastery>
}

internal data class DailyProgress(
    val userId: UserId,
    val date: LocalDate,
    val xp: Int,
    val sessions: Int,
)

internal interface DailyProgressRepository {
    /** Атомарно прибавить опыт и счётчик тренировок за день (upsert). */
    fun addProgress(userId: UserId, date: LocalDate, xp: Int, sessions: Int)

    fun find(userId: UserId, date: LocalDate): DailyProgress?

    fun listSince(userId: UserId, from: LocalDate): List<DailyProgress>
}
