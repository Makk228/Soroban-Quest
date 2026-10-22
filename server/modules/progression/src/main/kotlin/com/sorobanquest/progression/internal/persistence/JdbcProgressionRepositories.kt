package com.sorobanquest.progression.internal.persistence

import com.sorobanquest.progression.internal.application.AchievementRepository
import com.sorobanquest.progression.internal.application.DailyProgress
import com.sorobanquest.progression.internal.application.DailyProgressRepository
import com.sorobanquest.progression.internal.application.LearnerProfileRepository
import com.sorobanquest.progression.internal.application.SkillMasteryRepository
import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.SkillMastery
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class JdbcLearnerProfileRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : LearnerProfileRepository {

    override fun insertIfAbsent(profile: LearnerProfile): Boolean = jdbc.update(
        """
        insert into progression.learner_profiles
            (user_id, total_xp, current_streak_days, longest_streak_days, last_training_date,
             total_sessions, total_tasks, total_correct, daily_goal_xp, created_at, updated_at, version)
        values
            (:userId, :totalXp, :currentStreak, :longestStreak, :lastTrainingDate,
             :totalSessions, :totalTasks, :totalCorrect, :dailyGoalXp, :createdAt, :updatedAt, :version)
        on conflict (user_id) do nothing
        """.trimIndent(),
        profile.toParams(),
    ) == 1

    override fun findById(userId: UserId): LearnerProfile? = jdbc.query(
        "select * from progression.learner_profiles where user_id = :userId",
        MapSqlParameterSource("userId", userId.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun findForUpdate(userId: UserId): LearnerProfile? = jdbc.query(
        "select * from progression.learner_profiles where user_id = :userId for update",
        MapSqlParameterSource("userId", userId.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun update(profile: LearnerProfile): Boolean = jdbc.update(
        """
        update progression.learner_profiles set
            total_xp = :totalXp,
            current_streak_days = :currentStreak,
            longest_streak_days = :longestStreak,
            last_training_date = :lastTrainingDate,
            total_sessions = :totalSessions,
            total_tasks = :totalTasks,
            total_correct = :totalCorrect,
            daily_goal_xp = :dailyGoalXp,
            updated_at = :updatedAt,
            version = :version
        where user_id = :userId and version = :previousVersion
        """.trimIndent(),
        profile.toParams().addValue("previousVersion", profile.version - 1),
    ) == 1

    private fun LearnerProfile.toParams() = MapSqlParameterSource()
        .addValue("userId", userId.value)
        .addValue("totalXp", totalXp)
        .addValue("currentStreak", currentStreakDays)
        .addValue("longestStreak", longestStreakDays)
        .addValue("lastTrainingDate", lastTrainingDate)
        .addValue("totalSessions", totalSessions)
        .addValue("totalTasks", totalTasks)
        .addValue("totalCorrect", totalCorrect)
        .addValue("dailyGoalXp", dailyGoalXp)
        .addValue("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
        .addValue("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
        .addValue("version", version)

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            LearnerProfile(
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                totalXp = rs.getLong("total_xp"),
                currentStreakDays = rs.getInt("current_streak_days"),
                longestStreakDays = rs.getInt("longest_streak_days"),
                lastTrainingDate = rs.getObject("last_training_date", LocalDate::class.java),
                totalSessions = rs.getInt("total_sessions"),
                totalTasks = rs.getInt("total_tasks"),
                totalCorrect = rs.getInt("total_correct"),
                dailyGoalXp = rs.getInt("daily_goal_xp"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
                version = rs.getLong("version"),
            )
        }
    }
}

internal class JdbcAchievementRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : AchievementRepository {

    /** Первичный ключ (user_id, code) гарантирует, что достижение выдаётся ровно один раз. */
    override fun unlockIfAbsent(userId: UserId, code: String, unlockedAt: Instant): Boolean = jdbc.update(
        """
        insert into progression.achievements (user_id, code, unlocked_at)
        values (:userId, :code, :unlockedAt)
        on conflict (user_id, code) do nothing
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("userId", userId.value)
            .addValue("code", code)
            .addValue("unlockedAt", OffsetDateTime.ofInstant(unlockedAt, ZoneOffset.UTC)),
    ) == 1

    override fun listUnlocked(userId: UserId): Map<String, Instant> = jdbc.query(
        "select code, unlocked_at from progression.achievements where user_id = :userId",
        MapSqlParameterSource("userId", userId.value),
    ) { rs, _ -> rs.getString("code") to rs.getObject("unlocked_at", OffsetDateTime::class.java).toInstant() }
        .toMap()
}

internal class JdbcSkillMasteryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : SkillMasteryRepository {

    override fun find(userId: UserId, kind: DrillKind): SkillMastery? = jdbc.query(
        "select * from progression.skill_mastery where user_id = :userId and drill_kind = :kind",
        MapSqlParameterSource().addValue("userId", userId.value).addValue("kind", kind.name),
        ROW_MAPPER,
    ).firstOrNull()

    override fun upsert(mastery: SkillMastery) {
        jdbc.update(
            """
            insert into progression.skill_mastery
                (user_id, drill_kind, sessions, tasks_answered, correct_answers, best_score, best_average_answer_ms, updated_at)
            values
                (:userId, :kind, :sessions, :tasksAnswered, :correctAnswers, :bestScore, :bestAverageAnswerMs, :updatedAt)
            on conflict (user_id, drill_kind) do update set
                sessions = excluded.sessions,
                tasks_answered = excluded.tasks_answered,
                correct_answers = excluded.correct_answers,
                best_score = excluded.best_score,
                best_average_answer_ms = excluded.best_average_answer_ms,
                updated_at = excluded.updated_at
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", mastery.userId.value)
                .addValue("kind", mastery.kind.name)
                .addValue("sessions", mastery.sessions)
                .addValue("tasksAnswered", mastery.tasksAnswered)
                .addValue("correctAnswers", mastery.correctAnswers)
                .addValue("bestScore", mastery.bestScore)
                .addValue("bestAverageAnswerMs", mastery.bestAverageAnswerMs)
                .addValue("updatedAt", OffsetDateTime.ofInstant(mastery.updatedAt, ZoneOffset.UTC)),
        )
    }

    override fun listByUser(userId: UserId): List<SkillMastery> = jdbc.query(
        "select * from progression.skill_mastery where user_id = :userId order by drill_kind",
        MapSqlParameterSource("userId", userId.value),
        ROW_MAPPER,
    )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            SkillMastery(
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                kind = DrillKind.valueOf(rs.getString("drill_kind")),
                sessions = rs.getInt("sessions"),
                tasksAnswered = rs.getInt("tasks_answered"),
                correctAnswers = rs.getInt("correct_answers"),
                bestScore = rs.getInt("best_score"),
                bestAverageAnswerMs = rs.getObject("best_average_answer_ms")?.let { (it as Number).toLong() },
                updatedAt = rs.getObject("updated_at", OffsetDateTime::class.java).toInstant(),
            )
        }
    }
}

internal class JdbcDailyProgressRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : DailyProgressRepository {

    /** Инкремент через `on conflict do update` — безопасен при параллельных тренировках. */
    override fun addProgress(userId: UserId, date: LocalDate, xp: Int, sessions: Int) {
        jdbc.update(
            """
            insert into progression.daily_progress (user_id, progress_date, xp, sessions)
            values (:userId, :date, :xp, :sessions)
            on conflict (user_id, progress_date) do update set
                xp = progression.daily_progress.xp + excluded.xp,
                sessions = progression.daily_progress.sessions + excluded.sessions
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId.value)
                .addValue("date", date)
                .addValue("xp", xp)
                .addValue("sessions", sessions),
        )
    }

    override fun find(userId: UserId, date: LocalDate): DailyProgress? = jdbc.query(
        "select * from progression.daily_progress where user_id = :userId and progress_date = :date",
        MapSqlParameterSource().addValue("userId", userId.value).addValue("date", date),
        ROW_MAPPER,
    ).firstOrNull()

    override fun listSince(userId: UserId, from: LocalDate): List<DailyProgress> = jdbc.query(
        """
        select * from progression.daily_progress
        where user_id = :userId and progress_date >= :from
        order by progress_date
        """.trimIndent(),
        MapSqlParameterSource().addValue("userId", userId.value).addValue("from", from),
        ROW_MAPPER,
    )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            DailyProgress(
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                date = rs.getObject("progress_date", LocalDate::class.java),
                xp = rs.getInt("xp"),
                sessions = rs.getInt("sessions"),
            )
        }
    }
}
