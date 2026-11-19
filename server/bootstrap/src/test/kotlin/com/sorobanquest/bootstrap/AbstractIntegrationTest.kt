package com.sorobanquest.bootstrap

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * База для интеграционных тестов: настоящий PostgreSQL в Docker (Testcontainers),
 * настоящие миграции Flyway, настоящий HTTP-слой. Заглушек нет — проверяется
 * ровно то приложение, которое уедет в прод.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.test.context.ContextConfiguration(initializers = [PostgresTestInitializer::class])
abstract class AbstractIntegrationTest(
    protected val mockMvc: MockMvc,
    protected val objectMapper: ObjectMapper,
) {
    /** Данные зарегистрированного в тесте ученика. */
    protected data class Account(
        val userId: String,
        val email: String,
        val displayName: String,
        val accessToken: String,
        val refreshToken: String,
    )

    protected fun registerUser(
        displayName: String,
        password: String,
    ): Account {
        val email = "learner-${UUID.randomUUID()}@example.com"
        val body =
            objectMapper.writeValueAsString(
                mapOf("email" to email, "displayName" to displayName, "password" to password),
            )
        val response =
            mockMvc
                .perform(
                    post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .json()

        return Account(
            userId = response["user"]["id"].asText(),
            email = email,
            displayName = displayName,
            accessToken = response["accessToken"].asText(),
            refreshToken = response["refreshToken"].asText(),
        )
    }

    protected fun MockHttpServletRequestBuilder.authorizedBy(account: Account): MockHttpServletRequestBuilder =
        header("Authorization", "Bearer ${account.accessToken}")

    protected fun MockHttpServletRequestBuilder.jsonBody(payload: Any): MockHttpServletRequestBuilder =
        contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(payload))

    protected fun MvcResult.json(): JsonNode = objectMapper.readTree(response.contentAsString)

    /** Решает задание так же, как это делал бы клиент: по условию, без подсказок сервера. */
    protected fun solve(task: JsonNode): Long {
        val prompt = task["prompt"]
        return when (val type = prompt["type"].asText()) {
            "SEQUENCE" -> prompt["numbers"].sumOf { it.asLong() }
            "BINARY" -> {
                val left = prompt["left"].asLong()
                val right = prompt["right"].asLong()
                when (val operator = prompt["operator"].asText()) {
                    "×" -> left * right
                    "÷" -> left / right
                    else -> error("Неизвестная операция: $operator")
                }
            }

            else -> error("Неизвестный тип задания: $type")
        }
    }

    /**
     * Проходит тренировку целиком.
     * @param mistakesAt номера заданий, на которые нужно намеренно ответить неверно.
     */
    protected fun playSession(
        account: Account,
        kind: String,
        difficulty: Int,
        taskCount: Int,
        elapsedMs: Long,
        mistakesAt: Set<Int>,
    ): JsonNode {
        val started =
            mockMvc
                .perform(
                    post("/api/v1/training/sessions")
                        .authorizedBy(account)
                        .jsonBody(mapOf("kind" to kind, "difficulty" to difficulty, "taskCount" to taskCount)),
                ).andExpect(status().isCreated)
                .andReturn()
                .json()

        val sessionId = started["sessionId"].asText()
        return (0 until taskCount).fold(started) { session, index ->
            val task = requireNotNull(session["currentTask"]?.takeUnless { it.isNull }) { "Сервер не выдал задание №$index" }
            val correctAnswer = solve(task)
            val answer = if (index in mistakesAt) correctAnswer + 1 else correctAnswer

            val result =
                mockMvc
                    .perform(
                        post("/api/v1/training/sessions/$sessionId/answers")
                            .authorizedBy(account)
                            .jsonBody(mapOf("taskIndex" to index, "answer" to answer, "elapsedMs" to elapsedMs)),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .json()

            result["session"]
        }
    }

    protected fun fetchProfile(account: Account): JsonNode =
        mockMvc
            .perform(get("/api/v1/me").authorizedBy(account))
            .andExpect(status().isOk)
            .andReturn()
            .json()
}
