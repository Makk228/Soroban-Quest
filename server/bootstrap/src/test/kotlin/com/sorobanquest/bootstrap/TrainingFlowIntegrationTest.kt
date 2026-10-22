package com.sorobanquest.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** Сквозной сценарий тренировки: от выбора режима до разбора ответов. */
class TrainingFlowIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `каталог тренировок доступен авторизованному пользователю`() {
        val account = registerUser()

        mockMvc.perform(get("/api/v1/training/drills").authorizedBy(account))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].kind").value("FLASH_ANZAN"))
    }

    @Test
    fun `тренировка проходится целиком и завершается подсчётом результата`() {
        val account = registerUser()

        val session = playSession(account, kind = "FLASH_ANZAN", difficulty = 4, taskCount = 6)

        assertThat(session["status"].asText()).isEqualTo("COMPLETED")
        assertThat(session["correctCount"].asInt()).isEqualTo(6)
        assertThat(session["accuracy"].asDouble()).isEqualTo(1.0)
        assertThat(session["score"].asInt()).isPositive()
        assertThat(session["bestCombo"].asInt()).isEqualTo(6)
        assertThat(session["completedAt"].asText()).isNotBlank()
    }

    @Test
    fun `ошибка обнуляет комбо и уменьшает точность`() {
        val account = registerUser()

        val session = playSession(account, taskCount = 6, mistakesAt = setOf(2))

        assertThat(session["correctCount"].asInt()).isEqualTo(5)
        assertThat(session["accuracy"].asDouble()).isEqualTo(5.0 / 6.0, org.assertj.core.data.Offset.offset(0.0001))
        assertThat(session["bestCombo"].asInt()).isEqualTo(3)
    }

    @Test
    fun `все виды тренировок проходимы и проверяются сервером`() {
        val account = registerUser()

        listOf("ADD_SUB_CHAIN", "MULTIPLICATION", "DIVISION", "MIXED").forEach { kind ->
            val session = playSession(account, kind = kind, difficulty = 5, taskCount = 5)
            assertThat(session["status"].asText()).describedAs(kind).isEqualTo("COMPLETED")
            assertThat(session["correctCount"].asInt()).describedAs(kind).isEqualTo(5)
        }
    }

    @Test
    fun `повторная отправка того же ответа идемпотентна`() {
        val account = registerUser()
        val started = mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 3, "taskCount" to 5)),
        ).andExpect(status().isCreated).andReturn().json()

        val sessionId = started["sessionId"].asText()
        val answer = solve(started["currentTask"])
        val payload = mapOf("taskIndex" to 0, "answer" to answer, "elapsedMs" to 1200)

        val first = mockMvc.perform(
            post("/api/v1/training/sessions/$sessionId/answers").authorizedBy(account).jsonBody(payload),
        ).andExpect(status().isOk).andReturn().json()

        val second = mockMvc.perform(
            post("/api/v1/training/sessions/$sessionId/answers").authorizedBy(account).jsonBody(payload),
        ).andExpect(status().isOk).andReturn().json()

        assertThat(first["duplicate"].asBoolean()).isFalse()
        assertThat(second["duplicate"].asBoolean()).isTrue()
        assertThat(second["awardedPoints"].asInt()).isEqualTo(first["awardedPoints"].asInt())
        assertThat(second["session"]["answeredCount"].asInt()).isEqualTo(1)
    }

    @Test
    fun `ответ не по порядку отклоняется с кодом 422`() {
        val account = registerUser()
        val started = mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 3, "taskCount" to 5)),
        ).andReturn().json()

        mockMvc.perform(
            post("/api/v1/training/sessions/${started["sessionId"].asText()}/answers")
                .authorizedBy(account)
                .jsonBody(mapOf("taskIndex" to 3, "answer" to 1, "elapsedMs" to 1000)),
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.code").value("RULE_VIOLATION"))
            .andExpect(jsonPath("$.rule").value("task_out_of_order"))
    }

    @Test
    fun `чужая сессия недоступна, несуществующая даёт 404`() {
        val owner = registerUser()
        val stranger = registerUser()
        val session = mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(owner)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 2, "taskCount" to 5)),
        ).andReturn().json()

        mockMvc.perform(
            get("/api/v1/training/sessions/${session["sessionId"].asText()}").authorizedBy(stranger),
        ).andExpect(status().isForbidden)

        mockMvc.perform(get("/api/v1/training/sessions/${UUID.randomUUID()}").authorizedBy(owner))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `тренировку можно прервать, после этого ответы не принимаются`() {
        val account = registerUser()
        val session = mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 2, "taskCount" to 5)),
        ).andReturn().json()
        val sessionId = session["sessionId"].asText()

        mockMvc.perform(post("/api/v1/training/sessions/$sessionId/abandon").authorizedBy(account))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ABANDONED"))

        mockMvc.perform(
            post("/api/v1/training/sessions/$sessionId/answers")
                .authorizedBy(account)
                .jsonBody(mapOf("taskIndex" to 0, "answer" to 1, "elapsedMs" to 1000)),
        ).andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `лимит незавершённых тренировок защищает от накопления мусора`() {
        val account = registerUser()
        repeat(5) {
            mockMvc.perform(
                post("/api/v1/training/sessions")
                    .authorizedBy(account)
                    .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 1, "taskCount" to 5)),
            ).andExpect(status().isCreated)
        }

        mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 1, "taskCount" to 5)),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `некорректные параметры старта отклоняются до создания сессии`() {
        val account = registerUser()

        mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 42, "taskCount" to 5)),
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 3, "taskCount" to 1000)),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `история и разбор ответов сохраняются в базе`() {
        val account = registerUser()
        val session = playSession(account, taskCount = 5, mistakesAt = setOf(1))
        val sessionId = session["sessionId"].asText()

        val answers = mockMvc.perform(
            get("/api/v1/training/sessions/$sessionId/answers").authorizedBy(account),
        ).andExpect(status().isOk).andReturn().json()

        assertThat(answers).hasSize(5)
        assertThat(answers[1]["correct"].asBoolean()).isFalse()
        assertThat(answers[1]["expectedAnswer"].asLong()).isNotEqualTo(answers[1]["givenAnswer"].asLong())

        val history = mockMvc.perform(
            get("/api/v1/training/sessions?page=0&size=10").authorizedBy(account),
        ).andExpect(status().isOk).andReturn().json()

        assertThat(history["totalItems"].asInt()).isEqualTo(1)
        assertThat(history["items"][0]["sessionId"].asText()).isEqualTo(sessionId)
        assertThat(history["items"][0]["status"].asText()).isEqualTo("COMPLETED")
    }
}
