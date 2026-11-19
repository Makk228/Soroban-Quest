package com.sorobanquest.identity

import com.sorobanquest.identity.api.LoginCommand
import com.sorobanquest.identity.api.RegisterUserCommand
import com.sorobanquest.identity.api.UserRegistered
import com.sorobanquest.identity.internal.application.AuthenticationServiceImpl
import com.sorobanquest.identity.internal.application.IdentitySettings
import com.sorobanquest.identity.internal.domain.EmailAddress
import com.sorobanquest.identity.internal.domain.RefreshToken
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.identity.internal.persistence.JdbcRefreshTokenRepository
import com.sorobanquest.identity.internal.persistence.JdbcUserRepository
import com.sorobanquest.identity.internal.security.BcryptPasswordHasher
import com.sorobanquest.identity.internal.security.JwtAccessTokenIssuer
import com.sorobanquest.identity.internal.security.SecureRandomRefreshTokenFactory
import com.sorobanquest.shared.error.ConflictException
import com.sorobanquest.shared.error.UnauthorizedException
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.event.DomainEvent
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.shared.tx.TransactionRunner
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Сценарии аутентификации проверяются на настоящих BCrypt и JWT,
 * подменены только хранилища — так тест остаётся быстрым, но не «пустым».
 */
class AuthenticationServiceTest {
    private val users = InMemoryUserRepository()
    private val refreshTokens = InMemoryRefreshTokenRepository()
    private val publishedEvents = mutableListOf<DomainEvent>()

    // JWT проверяется по системному времени, поэтому фиксируем часы «сейчас»,
    // а не произвольной датой — иначе свежевыпущенный токен окажется просроченным.
    private val startedAt: Instant = Instant.now()
    private val clock = Clock.fixed(startedAt, ZoneOffset.UTC)
    private val jwt =
        JwtAccessTokenIssuer(
            secret = "integration-test-secret-key-which-is-long-enough",
            issuer = "soroban-quest-test",
            accessTokenTtl = Duration.ofMinutes(15),
        )

    private val service =
        AuthenticationServiceImpl(
            users = users.repository,
            refreshTokens = refreshTokens.repository,
            passwordHasher = BcryptPasswordHasher(4),
            accessTokenIssuer = jwt,
            refreshTokenFactory = SecureRandomRefreshTokenFactory(),
            transactions = DirectTransactionRunner,
            events = DomainEventPublisher { publishedEvents += it },
            clock = clock,
            ids = SequentialIdGenerator(),
            settings = IdentitySettings(refreshTokenTtl = Duration.ofDays(30)),
        )

    private fun register(email: String) = service.register(RegisterUserCommand(email, "Ученик", "soroban2026"))

    @Test
    fun `регистрация создаёт пользователя, выдаёт токены и публикует событие`() {
        val tokens = register(email = "student@example.com")

        assertThat(tokens.user.email).isEqualTo("student@example.com")
        assertThat(tokens.accessToken).isNotBlank()
        assertThat(tokens.refreshToken).isNotBlank()
        assertThat(jwt.verify(tokens.accessToken).userId).isEqualTo(tokens.user.id)
        assertThat(publishedEvents.filterIsInstance<UserRegistered>()).hasSize(1)
    }

    @Test
    fun `адрес нормализуется и повторная регистрация отклоняется`() {
        register("Student@Example.COM")

        assertThatThrownBy { register("student@example.com") }
            .isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `слабый пароль и некорректный адрес не принимаются`() {
        assertThatThrownBy { service.register(RegisterUserCommand("a@b.ru", "Ученик", "short1")) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { service.register(RegisterUserCommand("a@b.ru", "Ученик", "onlyletters")) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { service.register(RegisterUserCommand("не-адрес", "Ученик", "soroban2026")) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `вход по правильному паролю выдаёт новую пару токенов`() {
        register(email = "student@example.com")

        val tokens = service.login(LoginCommand("student@example.com", "soroban2026"))

        assertThat(tokens.accessToken).isNotBlank()
        assertThat(jwt.verify(tokens.accessToken).displayName).isEqualTo("Ученик")
    }

    @Test
    fun `неверный пароль и неизвестный адрес дают одинаковую ошибку`() {
        register(email = "student@example.com")

        assertThatThrownBy { service.login(LoginCommand("student@example.com", "wrongpass1")) }
            .isInstanceOf(UnauthorizedException::class.java)
        assertThatThrownBy { service.login(LoginCommand("nobody@example.com", "soroban2026")) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `обновление ротирует refresh-токен`() {
        val first = register(email = "student@example.com")

        val second = service.refresh(first.refreshToken)

        assertThat(second.refreshToken).isNotEqualTo(first.refreshToken)
        assertThat(second.user.id).isEqualTo(first.user.id)
    }

    @Test
    fun `повторное использование старого refresh-токена отзывает все сессии`() {
        val first = register(email = "student@example.com")
        val second = service.refresh(first.refreshToken)

        assertThatThrownBy { service.refresh(first.refreshToken) }
            .isInstanceOf(UnauthorizedException::class.java)

        // токен, выданный на замену, тоже отозван — сессии завершены принудительно
        assertThatThrownBy { service.refresh(second.refreshToken) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `выход делает refresh-токен недействительным`() {
        val tokens = register(email = "student@example.com")

        service.logout(tokens.refreshToken)

        assertThatThrownBy { service.refresh(tokens.refreshToken) }
            .isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `просроченный refresh-токен не принимается`() {
        val tokens = register(email = "student@example.com")
        refreshTokens.expireAll(startedAt.minus(Duration.ofDays(1)))

        assertThatThrownBy { service.refresh(tokens.refreshToken) }
            .isInstanceOf(UnauthorizedException::class.java)
    }
}

// --- тестовые подмены портов ---

private object DirectTransactionRunner : TransactionRunner {
    override fun <T> required(block: () -> T): T = block()
}

private class SequentialIdGenerator : IdGenerator {
    private val counter = AtomicLong()

    override fun newId(): UUID = UUID(0, counter.incrementAndGet())
}

private class InMemoryUserRepository {
    val repository = mockk<JdbcUserRepository>()
    private val byId = LinkedHashMap<UserId, UserAccount>()

    private fun insertIfEmailFree(user: UserAccount): Boolean {
        if (byId.values.any { it.email == user.email }) return false
        byId[user.id] = user
        return true
    }

    private fun findByEmail(email: EmailAddress): UserAccount? = byId.values.firstOrNull { it.email == email }

    private fun findById(id: UserId): UserAccount? = byId[id]

    private fun findAllByIds(ids: Collection<UserId>): List<UserAccount> = ids.mapNotNull { byId[it] }

    init {
        every { repository.insertIfEmailFree(any()) } answers { insertIfEmailFree(arg<UserAccount>(0)) }
        every { repository.findByEmail(any()) } answers { findByEmail(EmailAddress(arg<String>(0))) }
        every { repository.findById(any()) } answers { findById(UserId(arg<UUID>(0))) }
        every { repository.findAllByIds(any()) } answers { findAllByIds(arg<Collection<UserId>>(0)) }
    }
}

private class InMemoryRefreshTokenRepository {
    val repository = mockk<JdbcRefreshTokenRepository>()
    private val tokens = LinkedHashMap<UUID, RefreshToken>()

    private fun insert(token: RefreshToken) {
        tokens[token.id] = token
    }

    private fun findByHash(tokenHash: String): RefreshToken? = tokens.values.firstOrNull { it.tokenHash == tokenHash }

    private fun revoke(
        tokenId: UUID,
        revokedAt: Instant,
    ): Boolean {
        val token = tokens[tokenId] ?: return false
        if (token.revokedAt != null) return false
        tokens[tokenId] = token.copy(revokedAt = revokedAt)
        return true
    }

    private fun revokeAllForUser(
        userId: UserId,
        revokedAt: Instant,
    ): Int {
        val activeTokens = tokens.values.filter { it.userId == userId && it.revokedAt == null }
        activeTokens.forEach {
            tokens[it.id] = it.copy(revokedAt = revokedAt)
        }
        return activeTokens.size
    }

    private fun deleteExpired(before: Instant): Int {
        val expired = tokens.values.filter { it.expiresAt.isBefore(before) }
        expired.forEach { tokens.remove(it.id) }
        return expired.size
    }

    fun expireAll(expiresAt: Instant) {
        tokens.keys.toList().forEach { id -> tokens[id] = tokens.getValue(id).copy(expiresAt = expiresAt) }
    }

    init {
        every { repository.insert(any()) } answers { insert(arg<RefreshToken>(0)) }
        every { repository.findByHash(any()) } answers { findByHash(arg<String>(0)) }
        every { repository.revoke(any(), any()) } answers { revoke(arg<UUID>(0), arg<Instant>(1)) }
        every { repository.revokeAllForUser(any(), any()) } answers { revokeAllForUser(UserId(arg<UUID>(0)), arg<Instant>(1)) }
        every { repository.deleteExpired(any()) } answers { deleteExpired(arg<Instant>(0)) }
    }
}
