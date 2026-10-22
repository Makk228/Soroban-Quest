package com.sorobanquest.identity.internal.security

import com.sorobanquest.identity.internal.application.PasswordHasher
import com.sorobanquest.identity.internal.application.RefreshTokenFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** BCrypt-реализация хеширования паролей. */
internal class BcryptPasswordHasher(strength: Int) : PasswordHasher {
    private val encoder = BCryptPasswordEncoder(strength)

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, hash: String): Boolean = encoder.matches(rawPassword, hash)
}

/**
 * Refresh-токен — 32 случайных байта в base64url. В БД кладём только SHA-256,
 * поэтому сам токен существует лишь у клиента.
 */
internal class SecureRandomRefreshTokenFactory : RefreshTokenFactory {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    override fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    override fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
