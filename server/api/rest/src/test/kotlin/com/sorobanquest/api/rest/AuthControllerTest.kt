package com.sorobanquest.api.rest

import com.sorobanquest.api.rest.auth.AuthController
import com.sorobanquest.api.rest.error.GlobalExceptionHandler
import com.sorobanquest.identity.api.AuthenticationService
import com.sorobanquest.shared.error.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AuthControllerTest {
    private val authentication = mockk<AuthenticationService>()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(AuthController(authentication))
            .setControllerAdvice(GlobalExceptionHandler())
            .setMessageConverters(MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json().build()))
            .build()
    private val loginRequest = """{"email":"student@example.com","password":"soroban2026"}"""

    @Test
    fun `invalid registration reports all fields without calling service`() {
        postJson("register", """{"email":"invalid","displayName":"A","password":"123"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.email").exists())
            .andExpect(jsonPath("$.fieldErrors.displayName").exists())
            .andExpect(jsonPath("$.fieldErrors.password").exists())
        verify(exactly = 0) { authentication.register(any()) }
    }

    @Test
    fun `missing and blank refresh tokens are rejected`() {
        listOf("{}", """{"refreshToken":null}""", """{"refreshToken":" "}""").forEach { body ->
            postJson("refresh", body).andExpect(status().isBadRequest)
        }
        verify(exactly = 0) { authentication.refresh(any()) }
    }

    @Test
    fun `domain failures have a predictable response`() {
        every { authentication.login(any()) } throws UnauthorizedException("Неверный пароль")
        postJson("login", loginRequest)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `unexpected failure hides internal details`() {
        every { authentication.login(any()) } throws IllegalStateException("database secret")
        postJson("login", loginRequest)
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("Внутренняя ошибка сервера"))
    }

    @Test
    fun `unsupported method remains a client error`() {
        mvc.perform(get("/api/v1/auth/login")).andExpect(status().isMethodNotAllowed)
    }

    private fun postJson(
        endpoint: String,
        body: String,
    ) = mvc.perform(post("/api/v1/auth/$endpoint").contentType(MediaType.APPLICATION_JSON).content(body))
}
