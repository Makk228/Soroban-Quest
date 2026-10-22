package com.sorobanquest.identity.internal.application

import com.sorobanquest.identity.internal.domain.EmailAddress
import com.sorobanquest.identity.internal.domain.RefreshToken
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.shared.model.UserId
import java.time.Instant

internal interface UserRepository {
    /** Вставка; false — если email уже занят (нарушен уникальный индекс). */
    fun insertIfEmailFree(user: UserAccount): Boolean

    fun findByEmail(email: EmailAddress): UserAccount?

    fun findById(id: UserId): UserAccount?

    fun findAllByIds(ids: Collection<UserId>): List<UserAccount>
}

internal interface RefreshTokenRepository {
    fun insert(token: RefreshToken)

    fun findByHash(tokenHash: String): RefreshToken?

    /** Пометить токен использованным. false — если он уже был отозван (признак повторного использования). */
    fun revoke(tokenId: java.util.UUID, revokedAt: Instant): Boolean

    /** Отозвать все активные токены пользователя — реакция на компрометацию. */
    fun revokeAllForUser(userId: UserId, revokedAt: Instant): Int

    fun deleteExpired(before: Instant): Int
}

/** Хеширование паролей (реализация — BCrypt). */
internal interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(rawPassword: String, hash: String): Boolean
}

internal data class IssuedAccessToken(val token: String, val expiresAt: Instant)

/** Выпуск access-токенов (реализация — JWT). */
internal interface AccessTokenIssuer {
    fun issue(user: UserAccount, issuedAt: Instant): IssuedAccessToken
}

/** Генерация непредсказуемых refresh-токенов и их хеширование для хранения. */
internal interface RefreshTokenFactory {
    fun newToken(): String

    fun hash(token: String): String
}

internal data class IdentitySettings(
    val refreshTokenTtl: java.time.Duration,
)
