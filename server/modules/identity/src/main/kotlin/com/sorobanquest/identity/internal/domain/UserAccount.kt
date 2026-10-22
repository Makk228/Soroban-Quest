package com.sorobanquest.identity.internal.domain

import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.UserId
import java.time.Instant

/** Нормализованный адрес электронной почты. */
@JvmInline
internal value class EmailAddress(val value: String) {
    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun of(raw: String): EmailAddress {
            val normalized = raw.trim().lowercase()
            if (!PATTERN.matches(normalized)) {
                throw ValidationException("Некорректный адрес электронной почты", mapOf("email" to raw))
            }
            return EmailAddress(normalized)
        }
    }
}

/** Требования к паролю. Проверяются до хеширования, в домене. */
internal object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 72 // ограничение BCrypt

    fun validate(rawPassword: String) {
        if (rawPassword.length < MIN_LENGTH || rawPassword.length > MAX_LENGTH) {
            throw ValidationException("Пароль должен содержать от $MIN_LENGTH до $MAX_LENGTH символов")
        }
        if (rawPassword.none { it.isLetter() } || rawPassword.none { it.isDigit() }) {
            throw ValidationException("Пароль должен содержать хотя бы одну букву и одну цифру")
        }
    }
}

internal object DisplayNamePolicy {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 32

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) {
            throw ValidationException("Имя должно содержать от $MIN_LENGTH до $MAX_LENGTH символов")
        }
        return trimmed
    }
}

/** Учётная запись пользователя. */
internal data class UserAccount(
    val id: UserId,
    val email: EmailAddress,
    val displayName: String,
    val passwordHash: String,
    val createdAt: Instant,
    val active: Boolean = true,
) {
    companion object {
        fun register(
            id: UserId,
            email: String,
            displayName: String,
            passwordHash: String,
            now: Instant,
        ) = UserAccount(
            id = id,
            email = EmailAddress.of(email),
            displayName = DisplayNamePolicy.normalize(displayName),
            passwordHash = passwordHash,
            createdAt = now,
        )
    }
}
