package com.sorobanquest.api.rest.auth

import com.sorobanquest.identity.api.AuthTokens
import com.sorobanquest.identity.api.UserSummary
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "Запрос на регистрацию")
data class RegisterRequest(
    val email: String,
    val displayName: String,
    val password: String,
) {
    fun validate() {
        val errors =
            listOfNotNull(
                if (email.length > 254 || !Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email.trim())) {
                    "email" to "Некорректный адрес электронной почты"
                } else {
                    null
                },
                if (displayName.trim().length !in 2..32) "displayName" to "Имя должно содержать от 2 до 32 символов" else null,
                if (password.length < 8 || password.toByteArray(Charsets.UTF_8).size > 72) {
                    "password" to "Пароль должен содержать минимум 8 символов и максимум 72 байта"
                } else {
                    null
                },
            ).toMap()
        if (errors.isNotEmpty()) {
            throw com.sorobanquest.shared.error
                .ValidationException("Запрос не прошёл валидацию", mapOf("fieldErrors" to errors))
        }
    }
}

@Schema(description = "Запрос на вход")
data class LoginRequest(
    val email: String,
    val password: String,
) {
    init {
        if (email.isBlank() || password.isBlank()) {
            throw com.sorobanquest.shared.error
                .ValidationException("Укажите адрес и пароль", emptyMap())
        }
    }
}

@Schema(description = "Запрос с refresh-токеном")
data class RefreshRequest(
    val refreshToken: String,
) {
    init {
        if (refreshToken.isBlank()) {
            throw com.sorobanquest.shared.error
                .ValidationException("Укажите refresh-токен", emptyMap())
        }
    }
}

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
)

@Schema(description = "Пара токенов доступа")
data class AuthResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: UserResponse,
)

object UserResponseFactory {
    fun of(summary: UserSummary) =
        UserResponse(
            id = summary.id.toString(),
            email = summary.email,
            displayName = summary.displayName,
            createdAt = summary.createdAt,
        )
}

object AuthResponseFactory {
    fun of(tokens: AuthTokens) =
        AuthResponse(
            accessToken = tokens.accessToken,
            accessTokenExpiresAt = tokens.accessTokenExpiresAt,
            refreshToken = tokens.refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
            user = UserResponseFactory.of(tokens.user),
        )
}
