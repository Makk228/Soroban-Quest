package com.sorobanquest.api.rest.progress

import com.sorobanquest.identity.api.AuthenticatedUser
import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.progression.api.AchievementView
import com.sorobanquest.progression.api.DailyProgressView
import com.sorobanquest.progression.api.LearnerProfileView
import com.sorobanquest.progression.api.ProgressionService
import com.sorobanquest.progression.api.SkillMasteryView
import com.sorobanquest.shared.error.NotFoundException
import com.sorobanquest.training.api.DrillKind
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

data class UpdateDailyGoalRequest(
    @field:NotNull(message = "Укажите дневную цель")
    @field:Min(20) @field:Max(5000)
    val dailyGoalXp: Int?,
)

data class ProfileResponse(
    val userId: String,
    val displayName: String,
    val email: String,
    val level: Int,
    val totalXp: Long,
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

data class AchievementResponse(
    val code: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val unlockedAt: Instant?,
) {
    companion object {
        fun of(view: AchievementView) =
            AchievementResponse(view.code, view.title, view.description, view.unlocked, view.unlockedAt)
    }
}

data class SkillResponse(
    val kind: DrillKind,
    val sessions: Int,
    val tasksAnswered: Int,
    val correctAnswers: Int,
    val accuracy: Double,
    val bestScore: Int,
    val bestAverageAnswerMs: Long?,
    val updatedAt: Instant,
) {
    companion object {
        fun of(view: SkillMasteryView) = SkillResponse(
            kind = view.kind,
            sessions = view.sessions,
            tasksAnswered = view.tasksAnswered,
            correctAnswers = view.correctAnswers,
            accuracy = view.accuracy,
            bestScore = view.bestScore,
            bestAverageAnswerMs = view.bestAverageAnswerMs,
            updatedAt = view.updatedAt,
        )
    }
}

data class DailyProgressResponse(
    val date: LocalDate,
    val xp: Int,
    val sessions: Int,
    val goalReached: Boolean,
) {
    companion object {
        fun of(view: DailyProgressView) = DailyProgressResponse(view.date, view.xp, view.sessions, view.goalReached)
    }
}

@Tag(name = "Профиль", description = "Опыт, уровни, серии, достижения и статистика ученика")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/me")
class ProgressController(
    private val progression: ProgressionService,
    private val users: UserDirectory,
) {

    @Operation(summary = "Профиль текущего ученика")
    @GetMapping
    fun profile(@AuthenticationPrincipal user: AuthenticatedUser): ProfileResponse =
        toResponse(progression.profile(user.userId))

    @Operation(summary = "Достижения: открытые и ещё закрытые")
    @GetMapping("/achievements")
    fun achievements(@AuthenticationPrincipal user: AuthenticatedUser): List<AchievementResponse> =
        progression.achievements(user.userId).map(AchievementResponse::of)

    @Operation(summary = "Статистика по видам тренировок")
    @GetMapping("/skills")
    fun skills(@AuthenticationPrincipal user: AuthenticatedUser): List<SkillResponse> =
        progression.skills(user.userId).map(SkillResponse::of)

    @Operation(summary = "Активность по дням (для графика и календаря серий)")
    @GetMapping("/daily")
    fun daily(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false, defaultValue = "7") days: Int,
    ): List<DailyProgressResponse> = progression.recentDays(user.userId, days).map(DailyProgressResponse::of)

    @Operation(summary = "Изменить дневную цель по опыту")
    @PutMapping("/daily-goal")
    fun updateDailyGoal(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: UpdateDailyGoalRequest,
    ): ProfileResponse = toResponse(progression.updateDailyGoal(user.userId, request.dailyGoalXp!!))

    private fun toResponse(view: LearnerProfileView): ProfileResponse {
        val summary = users.findById(view.userId) ?: throw NotFoundException("Пользователь", view.userId)
        return ProfileResponse(
            userId = view.userId.toString(),
            displayName = summary.displayName,
            email = summary.email,
            level = view.level,
            totalXp = view.totalXp,
            xpIntoLevel = view.xpIntoLevel,
            xpToNextLevel = view.xpToNextLevel,
            currentStreakDays = view.currentStreakDays,
            longestStreakDays = view.longestStreakDays,
            lastTrainingDate = view.lastTrainingDate,
            totalSessions = view.totalSessions,
            totalTasks = view.totalTasks,
            totalCorrect = view.totalCorrect,
            accuracy = view.accuracy,
            dailyGoalXp = view.dailyGoalXp,
            todayXp = view.todayXp,
            dailyGoalReached = view.dailyGoalReached,
        )
    }
}
