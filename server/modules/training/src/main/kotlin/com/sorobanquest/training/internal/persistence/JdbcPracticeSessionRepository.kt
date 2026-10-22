package com.sorobanquest.training.internal.persistence

import com.sorobanquest.shared.model.Page
import com.sorobanquest.shared.model.PageRequest
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.api.SessionStatus
import com.sorobanquest.training.internal.application.PracticeSessionRepository
import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.PracticeSession
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Адаптер хранения сессий поверх PostgreSQL.
 * Доменная модель не знает про JDBC — преобразование живёт здесь.
 */
internal class JdbcPracticeSessionRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : PracticeSessionRepository {

    override fun insert(session: PracticeSession) {
        jdbc.update(
            """
            insert into training.practice_sessions
                (id, user_id, drill_kind, difficulty, seed, task_count, generator_version, status,
                 answered_count, correct_count, current_combo, best_combo, score, total_time_ms,
                 started_at, completed_at, version)
            values
                (:id, :userId, :drillKind, :difficulty, :seed, :taskCount, :generatorVersion, :status,
                 :answeredCount, :correctCount, :currentCombo, :bestCombo, :score, :totalTimeMs,
                 :startedAt, :completedAt, :version)
            """.trimIndent(),
            session.toParams(),
        )
    }

    override fun findById(id: SessionId): PracticeSession? = jdbc.query(
        "select * from training.practice_sessions where id = :id",
        MapSqlParameterSource("id", id.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun findByIdForUpdate(id: SessionId): PracticeSession? = jdbc.query(
        "select * from training.practice_sessions where id = :id for update",
        MapSqlParameterSource("id", id.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun update(session: PracticeSession): Boolean {
        val updated = jdbc.update(
            """
            update training.practice_sessions set
                status = :status,
                answered_count = :answeredCount,
                correct_count = :correctCount,
                current_combo = :currentCombo,
                best_combo = :bestCombo,
                score = :score,
                total_time_ms = :totalTimeMs,
                completed_at = :completedAt,
                version = :version
            where id = :id and version = :previousVersion
            """.trimIndent(),
            session.toParams().addValue("previousVersion", session.version - 1),
        )
        return updated == 1
    }

    override fun countActive(userId: UserId): Int = jdbc.queryForObject(
        "select count(*) from training.practice_sessions where user_id = :userId and status = 'IN_PROGRESS'",
        MapSqlParameterSource("userId", userId.value),
        Int::class.java,
    ) ?: 0

    override fun findPage(userId: UserId, pageRequest: PageRequest): Page<PracticeSession> {
        val total = jdbc.queryForObject(
            "select count(*) from training.practice_sessions where user_id = :userId",
            MapSqlParameterSource("userId", userId.value),
            Long::class.java,
        ) ?: 0L

        val items = jdbc.query(
            """
            select * from training.practice_sessions
            where user_id = :userId
            order by started_at desc
            limit :limit offset :offset
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId.value)
                .addValue("limit", pageRequest.size)
                .addValue("offset", pageRequest.offset),
            ROW_MAPPER,
        )
        return Page(items, pageRequest.page, pageRequest.size, total)
    }

    private fun PracticeSession.toParams() = MapSqlParameterSource()
        .addValue("id", id.value)
        .addValue("userId", userId.value)
        .addValue("drillKind", kind.name)
        .addValue("difficulty", difficulty.level)
        .addValue("seed", seed)
        .addValue("taskCount", taskCount)
        .addValue("generatorVersion", generatorVersion)
        .addValue("status", status.name)
        .addValue("answeredCount", answeredCount)
        .addValue("correctCount", correctCount)
        .addValue("currentCombo", currentCombo)
        .addValue("bestCombo", bestCombo)
        .addValue("score", score)
        .addValue("totalTimeMs", totalTimeMs)
        .addValue("startedAt", OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC))
        .addValue("completedAt", completedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) })
        .addValue("version", version)

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            PracticeSession(
                id = SessionId(rs.getObject("id", java.util.UUID::class.java)),
                userId = UserId(rs.getObject("user_id", java.util.UUID::class.java)),
                kind = DrillKind.valueOf(rs.getString("drill_kind")),
                difficulty = Difficulty.of(rs.getInt("difficulty")),
                seed = rs.getLong("seed"),
                taskCount = rs.getInt("task_count"),
                generatorVersion = rs.getInt("generator_version"),
                status = SessionStatus.valueOf(rs.getString("status")),
                answeredCount = rs.getInt("answered_count"),
                correctCount = rs.getInt("correct_count"),
                currentCombo = rs.getInt("current_combo"),
                bestCombo = rs.getInt("best_combo"),
                score = rs.getInt("score"),
                totalTimeMs = rs.getLong("total_time_ms"),
                startedAt = rs.getObject("started_at", OffsetDateTime::class.java).toInstant(),
                completedAt = rs.getObject("completed_at", OffsetDateTime::class.java)?.toInstant(),
                version = rs.getLong("version"),
            )
        }
    }
}
