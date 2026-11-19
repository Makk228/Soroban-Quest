package com.sorobanquest.identity.internal.application

import com.sorobanquest.identity.internal.domain.UserAccount
import java.time.Instant

/** Хеширование паролей (реализация — BCrypt). */
internal interface PasswordHasher {
    fun hash(rawPassword: String): String

    fun matches(
        rawPassword: String,
        hash: String,
    ): Boolean
}

internal data class IssuedAccessToken(
    val token: String,
    val expiresAt: Instant,
)

/** Выпуск access-токенов (реализация — JWT). */
internal interface AccessTokenIssuer {
    fun issue(
        user: UserAccount,
        issuedAt: Instant,
    ): IssuedAccessToken
}

/** Генерация непредсказуемых refresh-токенов и их хеширование для хранения. */
internal interface RefreshTokenFactory {
    fun newToken(): String

    fun hash(token: String): String
}

internal data class IdentitySettings(
    val refreshTokenTtl: java.time.Duration,
)
