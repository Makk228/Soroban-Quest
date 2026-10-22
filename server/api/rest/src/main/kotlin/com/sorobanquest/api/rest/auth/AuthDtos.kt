package com.sorobanquest.api.rest.auth

import com.sorobanquest.identity.api.AuthTokens
import com.sorobanquest.identity.api.UserSummary
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

@Schema(description = "Запрос на регистрацию")
data class RegisterRequest(
    @field:NotBlank(message = "Укажите адрес электронной почты")
    @field:Email(message = "Некорректный адрес электронной почты")
    @field:Size(max = 254)
    val email: String,

    @field:NotBlank(message = "Укажите имя")
    @field:Size(min = 2, max = 32, message = "Имя должно содержать от 2 до 32 символов")
    val displayName: String,

    @field:NotBlank(message = "Укажите пароль")
    @field:Size(min = 8, max = 72, message = "Пароль должен содержать от 8 до 72 символов")
    val password: String,
)

@Schema(description = "Запрос на вход")
data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

@Schema(description = "Запрос с refresh-токеном")
data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
)

data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(summary: UserSummary) = UserResponse(
            id = summary.id.toString(),
            email = summary.email,
            displayName = summary.displayName,
            createdAt = summary.createdAt,
        )
    }
}

@Schema(description = "Пара токенов доступа")
data class AuthResponse(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: UserResponse,
) {
    companion object {
        fun of(tokens: AuthTokens) = AuthResponse(
            accessToken = tokens.accessToken,
            accessTokenExpiresAt = tokens.accessTokenExpiresAt,
            refreshToken = tokens.refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
            user = UserResponse.of(tokens.user),
        )
    }
}
