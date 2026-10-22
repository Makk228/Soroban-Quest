package com.sorobanquest.identity.internal.persistence

import com.sorobanquest.identity.internal.application.RefreshTokenRepository
import com.sorobanquest.identity.internal.domain.RefreshToken
import com.sorobanquest.shared.model.UserId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class JdbcRefreshTokenRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : RefreshTokenRepository {

    override fun insert(token: RefreshToken) {
        jdbc.update(
            """
            insert into identity.refresh_tokens (id, user_id, token_hash, issued_at, expires_at, revoked_at)
            values (:id, :userId, :tokenHash, :issuedAt, :expiresAt, :revokedAt)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", token.id)
                .addValue("userId", token.userId.value)
                .addValue("tokenHash", token.tokenHash)
                .addValue("issuedAt", OffsetDateTime.ofInstant(token.issuedAt, ZoneOffset.UTC))
                .addValue("expiresAt", OffsetDateTime.ofInstant(token.expiresAt, ZoneOffset.UTC))
                .addValue("revokedAt", token.revokedAt?.let { OffsetDateTime.ofInstant(it, ZoneOffset.UTC) }),
        )
    }

    override fun findByHash(tokenHash: String): RefreshToken? = jdbc.query(
        "select * from identity.refresh_tokens where token_hash = :tokenHash",
        MapSqlParameterSource("tokenHash", tokenHash),
        ROW_MAPPER,
    ).firstOrNull()

    /**
     * Условие `revoked_at is null` делает отзыв атомарным: при параллельном
     * обновлении токена выиграет только один запрос.
     */
    override fun revoke(tokenId: UUID, revokedAt: Instant): Boolean = jdbc.update(
        "update identity.refresh_tokens set revoked_at = :revokedAt where id = :id and revoked_at is null",
        MapSqlParameterSource()
            .addValue("id", tokenId)
            .addValue("revokedAt", OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC)),
    ) == 1

    override fun revokeAllForUser(userId: UserId, revokedAt: Instant): Int = jdbc.update(
        "update identity.refresh_tokens set revoked_at = :revokedAt where user_id = :userId and revoked_at is null",
        MapSqlParameterSource()
            .addValue("userId", userId.value)
            .addValue("revokedAt", OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC)),
    )

    override fun deleteExpired(before: Instant): Int = jdbc.update(
        "delete from identity.refresh_tokens where expires_at < :before",
        MapSqlParameterSource("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC)),
    )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            RefreshToken(
                id = rs.getObject("id", UUID::class.java),
                userId = UserId(rs.getObject("user_id", UUID::class.java)),
                tokenHash = rs.getString("token_hash"),
                issuedAt = rs.getObject("issued_at", OffsetDateTime::class.java).toInstant(),
                expiresAt = rs.getObject("expires_at", OffsetDateTime::class.java).toInstant(),
                revokedAt = rs.getObject("revoked_at", OffsetDateTime::class.java)?.toInstant(),
            )
        }
    }
}
