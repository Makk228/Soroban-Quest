package com.sorobanquest.identity.internal.security

import com.sorobanquest.identity.api.AccessTokenVerifier
import com.sorobanquest.identity.api.AuthenticatedUser
import com.sorobanquest.identity.internal.application.AccessTokenIssuer
import com.sorobanquest.identity.internal.application.IssuedAccessToken
import com.sorobanquest.identity.internal.domain.UserAccount
import com.sorobanquest.shared.error.UnauthorizedException
import com.sorobanquest.shared.model.UserId
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Duration
import java.time.Instant
import java.util.Date

/**
 * Реализация выпуска и проверки access-токенов на JWT (HS256).
 * Это единственный класс, который знает про формат токена: прикладной слой
 * работает через порты AccessTokenIssuer / AccessTokenVerifier.
 */
internal class JwtAccessTokenIssuer(
    secret: String,
    private val issuer: String,
    private val accessTokenTtl: Duration,
) : AccessTokenIssuer, AccessTokenVerifier {

    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    override fun issue(user: UserAccount, issuedAt: Instant): IssuedAccessToken {
        val expiresAt = issuedAt.plus(accessTokenTtl)
        val token = Jwts.builder()
            .issuer(issuer)
            .subject(user.id.value.toString())
            .claim("name", user.displayName)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return IssuedAccessToken(token, expiresAt)
    }

    override fun verify(accessToken: String): AuthenticatedUser {
        try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(accessToken)
                .payload
            val userId = UserId.parse(claims.subject)
            val displayName = claims.get("name", String::class.java) ?: ""
            return AuthenticatedUser(userId, displayName)
        } catch (e: ExpiredJwtException) {
            throw UnauthorizedException("Срок действия токена истёк")
        } catch (e: JwtException) {
            throw UnauthorizedException("Недействительный токен доступа")
        } catch (e: IllegalArgumentException) {
            throw UnauthorizedException("Недействительный токен доступа")
        }
    }
}
