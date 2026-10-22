package com.sorobanquest.api.rest.auth

import com.sorobanquest.identity.api.AuthenticationService
import com.sorobanquest.identity.api.LoginCommand
import com.sorobanquest.identity.api.RegisterUserCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Аутентификация", description = "Регистрация, вход и обновление токенов")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authentication: AuthenticationService,
) {

    @Operation(summary = "Зарегистрировать нового ученика")
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        val tokens = authentication.register(
            RegisterUserCommand(
                email = request.email,
                displayName = request.displayName,
                password = request.password,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.of(tokens))
    }

    @Operation(summary = "Войти по адресу и паролю")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        AuthResponse.of(authentication.login(LoginCommand(request.email, request.password)))

    @Operation(summary = "Обновить пару токенов (старый refresh-токен становится недействительным)")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthResponse =
        AuthResponse.of(authentication.refresh(request.refreshToken))

    @Operation(summary = "Выйти и отозвать refresh-токен")
    @PostMapping("/logout")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshRequest) {
        authentication.logout(request.refreshToken)
    }
}
