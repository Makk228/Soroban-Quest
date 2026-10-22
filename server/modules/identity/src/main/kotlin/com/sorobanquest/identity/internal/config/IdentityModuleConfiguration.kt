package com.sorobanquest.identity.internal.config

import com.sorobanquest.identity.api.AccessTokenVerifier
import com.sorobanquest.identity.api.AuthenticationService
import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.identity.internal.application.AuthenticationServiceImpl
import com.sorobanquest.identity.internal.application.IdentitySettings
import com.sorobanquest.identity.internal.application.UserDirectoryImpl
import com.sorobanquest.identity.internal.persistence.JdbcRefreshTokenRepository
import com.sorobanquest.identity.internal.persistence.JdbcUserRepository
import com.sorobanquest.identity.internal.security.BcryptPasswordHasher
import com.sorobanquest.identity.internal.security.JwtAccessTokenIssuer
import com.sorobanquest.identity.internal.security.SecureRandomRefreshTokenFactory
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.tx.TransactionRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.Duration

/**
 * Настройки модуля (префикс `soroban.identity`).
 * Секрет подписи токенов приходит из переменных окружения и никогда не хранится в коде.
 */
@ConfigurationProperties(prefix = "soroban.identity")
data class IdentityProperties(
    val jwtSecret: String = "",
    val jwtIssuer: String = "soroban-quest",
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    val bcryptStrength: Int = 10,
) {
    fun validated(): IdentityProperties {
        check(jwtSecret.toByteArray().size >= MIN_SECRET_BYTES) {
            "soroban.identity.jwt-secret должен быть длиной не менее $MIN_SECRET_BYTES байт " +
                "(задайте переменную окружения SOROBAN_JWT_SECRET)"
        }
        check(!accessTokenTtl.isZero && !accessTokenTtl.isNegative) { "access-token-ttl должен быть положительным" }
        check(!refreshTokenTtl.isZero && !refreshTokenTtl.isNegative) { "refresh-token-ttl должен быть положительным" }
        return this
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties::class)
class IdentityModuleConfiguration {

    @Bean
    fun authenticationService(
        jdbc: NamedParameterJdbcTemplate,
        transactionRunner: TransactionRunner,
        eventPublisher: DomainEventPublisher,
        clock: Clock,
        idGenerator: IdGenerator,
        properties: IdentityProperties,
    ): AuthenticationService {
        val validated = properties.validated()
        return AuthenticationServiceImpl(
            users = JdbcUserRepository(jdbc),
            refreshTokens = JdbcRefreshTokenRepository(jdbc),
            passwordHasher = BcryptPasswordHasher(validated.bcryptStrength),
            accessTokenIssuer = jwtIssuer(validated),
            refreshTokenFactory = SecureRandomRefreshTokenFactory(),
            transactions = transactionRunner,
            events = eventPublisher,
            clock = clock,
            ids = idGenerator,
            settings = IdentitySettings(refreshTokenTtl = validated.refreshTokenTtl),
        )
    }

    @Bean
    fun userDirectory(jdbc: NamedParameterJdbcTemplate): UserDirectory = UserDirectoryImpl(JdbcUserRepository(jdbc))

    @Bean
    fun accessTokenVerifier(properties: IdentityProperties): AccessTokenVerifier = jwtIssuer(properties.validated())

    /** Класс не хранит состояния, поэтому отдельные экземпляры для выпуска и проверки безопасны. */
    private fun jwtIssuer(properties: IdentityProperties) = JwtAccessTokenIssuer(
        secret = properties.jwtSecret,
        issuer = properties.jwtIssuer,
        accessTokenTtl = properties.accessTokenTtl,
    )
}
