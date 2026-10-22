package com.sorobanquest.leaderboard.internal.persistence

import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.leaderboard.internal.application.LeaderboardRepository
import com.sorobanquest.leaderboard.internal.application.RankedRow
import com.sorobanquest.shared.model.UserId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.util.UUID

/**
 * Рейтинг хранится как агрегат по (период, участник): накопление идёт инкрементом
 * через `on conflict do update`, поэтому параллельные начисления не теряются и
 * не требуют блокировок. Места считает оконная функция rank().
 */
internal class JdbcLeaderboardRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : LeaderboardRepository {

    override fun addXp(userId: UserId, period: LeaderboardPeriod, periodKey: String, xp: Int, sessions: Int) {
        jdbc.update(
            """
            insert into leaderboard.entries (period_type, period_key, user_id, xp, sessions, updated_at)
            values (:periodType, :periodKey, :userId, :xp, :sessions, now())
            on conflict (period_type, period_key, user_id) do update set
                xp = leaderboard.entries.xp + excluded.xp,
                sessions = leaderboard.entries.sessions + excluded.sessions,
                updated_at = now()
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("periodType", period.name)
                .addValue("periodKey", periodKey)
                .addValue("userId", userId.value)
                .addValue("xp", xp)
                .addValue("sessions", sessions),
        )
    }

    override fun top(period: LeaderboardPeriod, periodKey: String, limit: Int): List<RankedRow> = jdbc.query(
        """
        select user_id, xp, sessions,
               rank() over (order by xp desc, updated_at asc) as position
        from leaderboard.entries
        where period_type = :periodType and period_key = :periodKey
        order by position
        limit :limit
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("periodType", period.name)
            .addValue("periodKey", periodKey)
            .addValue("limit", limit),
        ROW_MAPPER,
    )

    override fun rankOf(userId: UserId, period: LeaderboardPeriod, periodKey: String): RankedRow? = jdbc.query(
        """
        select * from (
            select user_id, xp, sessions,
                   rank() over (order by xp desc, updated_at asc) as position
            from leaderboard.entries
            where period_type = :periodType and period_key = :periodKey
        ) ranked
        where user_id = :userId
        """.trimIndent(),
        MapSqlParameterSource()
            .addValue("periodType", period.name)
            .addValue("periodKey", periodKey)
            .addValue("userId", userId.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun countParticipants(period: LeaderboardPeriod, periodKey: String): Long = jdbc.queryForObject(
        "select count(*) from leaderboard.entries where period_type = :periodType and period_key = :periodKey",
        MapSqlParameterSource()
            .addValue("periodType", period.name)
            .addValue("periodKey", periodKey),
        Long::class.java,
    ) ?: 0L

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            RankedRow(
                rank = rs.getInt("position"),
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                xp = rs.getLong("xp"),
                sessions = rs.getInt("sessions"),
            )
        }
    }
}
