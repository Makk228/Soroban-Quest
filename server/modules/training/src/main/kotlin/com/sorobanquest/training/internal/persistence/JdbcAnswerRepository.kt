package com.sorobanquest.training.internal.persistence

import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.training.internal.application.AnswerRepository
import com.sorobanquest.training.internal.domain.SubmittedAnswer
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class JdbcAnswerRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : AnswerRepository {

    /**
     * `on conflict do nothing` вместе с первичным ключом (session_id, task_index)
     * делает отправку ответа идемпотентной даже при параллельных запросах.
     */
    override fun insertIfAbsent(answer: SubmittedAnswer): Boolean {
        val inserted = jdbc.update(
            """
            insert into training.session_answers
                (session_id, task_index, given_answer, expected_answer, correct, elapsed_ms, awarded_points, answered_at)
            values
                (:sessionId, :taskIndex, :givenAnswer, :expectedAnswer, :correct, :elapsedMs, :awardedPoints, :answeredAt)
            on conflict (session_id, task_index) do nothing
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("sessionId", answer.sessionId.value)
                .addValue("taskIndex", answer.taskIndex)
                .addValue("givenAnswer", answer.givenAnswer)
                .addValue("expectedAnswer", answer.expectedAnswer)
                .addValue("correct", answer.correct)
                .addValue("elapsedMs", answer.elapsedMs)
                .addValue("awardedPoints", answer.awardedPoints)
                .addValue("answeredAt", OffsetDateTime.ofInstant(answer.answeredAt, ZoneOffset.UTC)),
        )
        return inserted == 1
    }

    override fun find(sessionId: SessionId, taskIndex: Int): SubmittedAnswer? = jdbc.query(
        "select * from training.session_answers where session_id = :sessionId and task_index = :taskIndex",
        MapSqlParameterSource()
            .addValue("sessionId", sessionId.value)
            .addValue("taskIndex", taskIndex),
        ROW_MAPPER,
    ).firstOrNull()

    override fun listBySession(sessionId: SessionId): List<SubmittedAnswer> = jdbc.query(
        "select * from training.session_answers where session_id = :sessionId order by task_index",
        MapSqlParameterSource("sessionId", sessionId.value),
        ROW_MAPPER,
    )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            SubmittedAnswer(
                sessionId = SessionId(rs.getObject("session_id", UUID::class.java)),
                taskIndex = rs.getInt("task_index"),
                givenAnswer = rs.getLong("given_answer"),
                expectedAnswer = rs.getLong("expected_answer"),
                correct = rs.getBoolean("correct"),
                elapsedMs = rs.getInt("elapsed_ms"),
                awardedPoints = rs.getInt("awarded_points"),
                answeredAt = rs.getObject("answered_at", OffsetDateTime::class.java).toInstant(),
            )
        }
    }
}
