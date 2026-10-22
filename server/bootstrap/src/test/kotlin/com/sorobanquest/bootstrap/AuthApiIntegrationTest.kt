package com.sorobanquest.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

class AuthApiIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `регистрация возвращает 201 и пару токенов`() {
        val email = "learner-${UUID.randomUUID()}@example.com"

        mockMvc.perform(
            post("/api/v1/auth/register").jsonBody(
                mapOf("email" to email, "displayName" to "Новичок", "password" to "soroban2026"),
            ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.displayName").value("Новичок"))
    }

    @Test
    fun `повторная регистрация того же адреса даёт 409`() {
        val account = registerUser()

        mockMvc.perform(
            post("/api/v1/auth/register").jsonBody(
                mapOf("email" to account.email.uppercase(), "displayName" to "Двойник", "password" to "soroban2026"),
            ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `некорректный запрос возвращает 400 с описанием полей`() {
        mockMvc.perform(
            post("/api/v1/auth/register").jsonBody(
                mapOf("email" to "не-почта", "displayName" to "A", "password" to "123"),
            ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors.displayName").isNotEmpty)
            .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty)
    }

    @Test
    fun `вход с верным паролем выдаёт токены, с неверным — 401`() {
        val account = registerUser(password = "soroban2026")

        mockMvc.perform(
            post("/api/v1/auth/login").jsonBody(mapOf("email" to account.email, "password" to "soroban2026")),
        ).andExpect(status().isOk).andExpect(jsonPath("$.accessToken").isNotEmpty)

        mockMvc.perform(
            post("/api/v1/auth/login").jsonBody(mapOf("email" to account.email, "password" to "wrongpass1")),
        ).andExpect(status().isUnauthorized).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `защищённый маршрут без токена и с мусорным токеном возвращает 401`() {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `обновление токенов ротирует refresh, старый перестаёт работать`() {
        val account = registerUser()

        val refreshed = mockMvc.perform(
            post("/api/v1/auth/refresh").jsonBody(mapOf("refreshToken" to account.refreshToken)),
        ).andExpect(status().isOk).andReturn().json()

        val newRefresh = refreshed["refreshToken"].asText()
        assertThat(newRefresh).isNotEqualTo(account.refreshToken)

        // старый refresh-токен считается скомпрометированным
        mockMvc.perform(
            post("/api/v1/auth/refresh").jsonBody(mapOf("refreshToken" to account.refreshToken)),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `выход делает refresh-токен недействительным`() {
        val account = registerUser()

        mockMvc.perform(
            post("/api/v1/auth/logout").jsonBody(mapOf("refreshToken" to account.refreshToken)),
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/refresh").jsonBody(mapOf("refreshToken" to account.refreshToken)),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `документация и проверка здоровья доступны без токена`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
        mockMvc.perform(get("/v3/api-docs").accept(MediaType.ALL)).andExpect(status().isOk)
    }
}
