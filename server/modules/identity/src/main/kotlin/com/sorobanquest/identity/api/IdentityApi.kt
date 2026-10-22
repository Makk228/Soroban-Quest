package com.sorobanquest.identity.api

import com.sorobanquest.shared.event.DomainEvent
import com.sorobanquest.shared.model.UserId
import java.time.Instant

data class UserSummary(
    val id: UserId,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)

/** Пара токенов, выдаваемая после входа или обновления. */
data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: UserSummary,
)

data class RegisterUserCommand(
    val email: String,
    val displayName: String,
    val password: String,
)

data class LoginCommand(
    val email: String,
    val password: String,
)

/** Результат проверки access-токена — то, что API-слой кладёт в контекст запроса. */
data class AuthenticatedUser(
    val userId: UserId,
    val displayName: String,
)

interface AuthenticationService {
    fun register(command: RegisterUserCommand): AuthTokens

    fun login(command: LoginCommand): AuthTokens

    /** Обновление пары токенов с ротацией refresh-токена. */
    fun refresh(refreshToken: String): AuthTokens

    fun logout(refreshToken: String)
}

/** Справочник пользователей для других модулей (например, для таблицы лидеров). */
interface UserDirectory {
    fun findById(id: UserId): UserSummary?

    fun findAllByIds(ids: Collection<UserId>): Map<UserId, UserSummary>
}

/**
 * Проверка access-токена. Вынесено в отдельный порт, чтобы транспортный слой
 * не знал, что внутри именно JWT.
 */
fun interface AccessTokenVerifier {
    fun verify(accessToken: String): AuthenticatedUser
}

/** Событие регистрации — на него подписан модуль прогресса (создаёт профиль ученика). */
data class UserRegistered(
    val userId: UserId,
    val displayName: String,
    override val occurredAt: Instant,
) : DomainEvent
