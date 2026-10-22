package com.sorobanquest.identity.internal.application

import com.sorobanquest.identity.api.AuthTokens
import com.sorobanquest.identity.api.AuthenticationService
import com.sorobanquest.identity.api.LoginCommand
import com.sorobanquest.identity.api.RegisterUserCommand
import com.sorobanquest.identity.api.UserRegistered
import com.sorobanquest.identity.api.UserSummary
import com.sorobanquest.identity.internal.domain.EmailAddress
import com.sorobanquest.identity.internal.domain.PasswordPolicy
import com.sorobanquest.identity.internal.domain.RefreshToken
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.shared.error.ConflictException
import com.sorobanquest.shared.error.UnauthorizedException
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.shared.tx.TransactionRunner
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.Instant

private val log = KotlinLogging.logger {}

/**
 * Сценарии аутентификации.
 *
 * Схема: короткоживущий access-токен + долгоживущий refresh-токен с ротацией.
 * Повторное использование уже израсходованного refresh-токена считается признаком
 * кражи: все токены пользователя отзываются.
 */
internal class AuthenticationServiceImpl(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenFactory: RefreshTokenFactory,
    private val transactions: TransactionRunner,
    private val events: DomainEventPublisher,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val settings: IdentitySettings,
) : AuthenticationService {

    override fun register(command: RegisterUserCommand): AuthTokens {
        PasswordPolicy.validate(command.password)
        val now = clock.instant()
        val user = UserAccount.register(
            id = UserId(ids.newId()),
            email = command.email,
            displayName = command.displayName,
            passwordHash = passwordHasher.hash(command.password),
            now = now,
        )

        return transactions.required {
            if (!users.insertIfEmailFree(user)) {
                throw ConflictException(
                    "Пользователь с таким адресом уже зарегистрирован",
                    mapOf("email" to user.email.value),
                )
            }
            log.info { "Зарегистрирован пользователь ${user.id}" }
            events.publish(UserRegistered(user.id, user.displayName, now))
            issueTokens(user, now)
        }
    }

    override fun login(command: LoginCommand): AuthTokens {
        val now = clock.instant()
        val email = EmailAddress.of(command.email)
        val user = users.findByEmail(email)

        // Проверяем пароль даже для несуществующего пользователя, чтобы время ответа
        // не выдавало, зарегистрирован ли такой email.
        val matches = if (user != null) {
            passwordHasher.matches(command.password, user.passwordHash)
        } else {
            passwordHasher.matches(command.password, DUMMY_HASH)
            false
        }
        if (user == null || !matches || !user.active) {
            log.debug { "Неудачная попытка входа для $email" }
            throw UnauthorizedException("Неверный адрес электронной почты или пароль")
        }

        return transactions.required { issueTokens(user, now) }
    }

    override fun refresh(refreshToken: String): AuthTokens = transactions.required {
        val now = clock.instant()
        val hash = refreshTokenFactory.hash(refreshToken)
        val stored = refreshTokens.findByHash(hash) ?: throw UnauthorizedException("Refresh-токен недействителен")

        if (stored.revokedAt != null) {
            // Токен уже использовали — вероятнее всего, его украли.
            val revoked = refreshTokens.revokeAllForUser(stored.userId, now)
            log.warn { "Повторное использование refresh-токена пользователя ${stored.userId}; отозвано токенов: $revoked" }
            throw UnauthorizedException("Refresh-токен уже использован, сессии завершены")
        }
        if (!stored.isActiveAt(now)) {
            throw UnauthorizedException("Срок действия refresh-токена истёк")
        }

        val user = users.findById(stored.userId)?.takeIf { it.active }
            ?: throw UnauthorizedException("Учётная запись недоступна")

        if (!refreshTokens.revoke(stored.id, now)) {
            throw UnauthorizedException("Refresh-токен уже использован")
        }
        issueTokens(user, now)
    }

    override fun logout(refreshToken: String) {
        transactions.required {
            val now = clock.instant()
            val stored = refreshTokens.findByHash(refreshTokenFactory.hash(refreshToken))
            if (stored != null && stored.revokedAt == null) {
                refreshTokens.revoke(stored.id, now)
                log.info { "Пользователь ${stored.userId} вышел из системы" }
            }
        }
    }

    private fun issueTokens(user: UserAccount, now: Instant): AuthTokens {
        val access = accessTokenIssuer.issue(user, now)
        val rawRefresh = refreshTokenFactory.newToken()
        val refresh = RefreshToken(
            id = ids.newId(),
            userId = user.id,
            tokenHash = refreshTokenFactory.hash(rawRefresh),
            issuedAt = now,
            expiresAt = now.plus(settings.refreshTokenTtl),
            revokedAt = null,
        )
        refreshTokens.insert(refresh)
        return AuthTokens(
            accessToken = access.token,
            accessTokenExpiresAt = access.expiresAt,
            refreshToken = rawRefresh,
            refreshTokenExpiresAt = refresh.expiresAt,
            user = UserSummary(user.id, user.email.value, user.displayName, user.createdAt),
        )
    }

    private companion object {
        /** Заведомо неподходящий BCrypt-хеш для выравнивания времени ответа. */
        const val DUMMY_HASH = "\$2a\$10\$C6UzMDM.H6dfI/f/IKcEe.4Qy1Hc8DqO1vk4tK6V0QX7nFbpFJzvG"
    }
}
