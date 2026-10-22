package com.sorobanquest.identity.internal.persistence

import com.sorobanquest.identity.internal.application.UserRepository
import com.sorobanquest.identity.internal.domain.EmailAddress
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.shared.model.UserId
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

internal class JdbcUserRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : UserRepository {

    /**
     * Уникальность email обеспечивает БД: `on conflict do nothing` превращает
     * гонку двух одновременных регистраций в предсказуемый отказ.
     */
    override fun insertIfEmailFree(user: UserAccount): Boolean {
        val inserted = jdbc.update(
            """
            insert into identity.users (id, email, display_name, password_hash, created_at, active)
            values (:id, :email, :displayName, :passwordHash, :createdAt, :active)
            on conflict (lower(email)) do nothing  -- совпадает с выражением уникального индекса
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", user.id.value)
                .addValue("email", user.email.value)
                .addValue("displayName", user.displayName)
                .addValue("passwordHash", user.passwordHash)
                .addValue("createdAt", OffsetDateTime.ofInstant(user.createdAt, ZoneOffset.UTC))
                .addValue("active", user.active),
        )
        return inserted == 1
    }

    override fun findByEmail(email: EmailAddress): UserAccount? = jdbc.query(
        "select * from identity.users where email = :email",
        MapSqlParameterSource("email", email.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun findById(id: UserId): UserAccount? = jdbc.query(
        "select * from identity.users where id = :id",
        MapSqlParameterSource("id", id.value),
        ROW_MAPPER,
    ).firstOrNull()

    override fun findAllByIds(ids: Collection<UserId>): List<UserAccount> = jdbc.query(
        "select * from identity.users where id in (:ids)",
        MapSqlParameterSource("ids", ids.map { it.value }),
        ROW_MAPPER,
    )

    private companion object {
        val ROW_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            UserAccount(
                id = UserId(rs.getObject("id", UUID::class.java)),
                email = EmailAddress(rs.getString("email")),
                displayName = rs.getString("display_name"),
                passwordHash = rs.getString("password_hash"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                active = rs.getBoolean("active"),
            )
        }
    }
}
