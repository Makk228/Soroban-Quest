package com.sorobanquest.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Проверка поведения при одновременных запросах.
 *
 * Два механизма защиты:
 *  - первичный ключ (session_id, task_index) + `on conflict do nothing` — ответ
 *    на одно задание не может быть засчитан дважды;
 *  - `select ... for update` на строках сессии и профиля — параллельные
 *    завершения тренировок не теряют начисленный опыт.
 */
class ConcurrencyIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `одновременная отправка одного ответа засчитывается ровно один раз`() {
        val account = registerUser()
        val started = mockMvc.perform(
            post("/api/v1/training/sessions")
                .authorizedBy(account)
                .jsonBody(mapOf("kind" to "FLASH_ANZAN", "difficulty" to 3, "taskCount" to 5)),
        ).andExpect(status().isCreated).andReturn().json()

        val sessionId = started["sessionId"].asText()
        val payload = mapOf("taskIndex" to 0, "answer" to solve(started["currentTask"]), "elapsedMs" to 1200)

        val threads = 8
        val barrier = CyclicBarrier(threads)
        val pool = Executors.newFixedThreadPool(threads)
        val statuses = try {
            pool.invokeAll(
                (1..threads).map {
                    Callable {
                        barrier.await(10, TimeUnit.SECONDS)
                        mockMvc.perform(
                            post("/api/v1/training/sessions/$sessionId/answers")
                                .authorizedBy(account)
                                .jsonBody(payload),
                        ).andReturn().response.status
                    }
                },
            ).map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }

        // каждый запрос завершился предсказуемо: либо принят, либо отклонён как конфликт
        assertThat(statuses).allMatch { it == 200 || it == 409 }

        val session = mockMvc.perform(get("/api/v1/training/sessions/$sessionId").authorizedBy(account))
            .andExpect(status().isOk).andReturn().json()
        assertThat(session["answeredCount"].asInt()).isEqualTo(1)
        assertThat(session["correctCount"].asInt()).isEqualTo(1)

        val answers = mockMvc.perform(get("/api/v1/training/sessions/$sessionId/answers").authorizedBy(account))
            .andReturn().json()
        assertThat(answers).hasSize(1)
    }

    @Test
    fun `параллельно завершённые тренировки не теряют опыт`() {
        val account = registerUser()
        val sessions = (1..4).map {
            mockMvc.perform(
                post("/api/v1/training/sessions")
                    .authorizedBy(account)
                    .jsonBody(mapOf("kind" to "MULTIPLICATION", "difficulty" to 3, "taskCount" to 5)),
            ).andExpect(status().isCreated).andReturn().json()
        }

        val pool = Executors.newFixedThreadPool(sessions.size)
        val scores = try {
            pool.invokeAll(
                sessions.map { started ->
                    Callable {
                        val sessionId = started["sessionId"].asText()
                        var task = started["currentTask"]
                        var last = started
                        repeat(5) { index ->
                            val result = mockMvc.perform(
                                post("/api/v1/training/sessions/$sessionId/answers")
                                    .authorizedBy(account)
                                    .jsonBody(
                                        mapOf("taskIndex" to index, "answer" to solve(task), "elapsedMs" to 1500),
                                    ),
                            ).andExpect(status().isOk).andReturn().json()
                            last = result["session"]
                            result["nextTask"]?.takeUnless { it.isNull }?.let { task = it }
                        }
                        last["score"].asInt()
                    }
                },
            ).map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }

        val profile = fetchProfile(account)

        assertThat(scores).allMatch { it > 0 }
        assertThat(profile["totalSessions"].asInt()).isEqualTo(4)
        assertThat(profile["totalTasks"].asInt()).isEqualTo(20)
        assertThat(profile["totalCorrect"].asInt()).isEqualTo(20)
        assertThat(profile["totalXp"].asLong()).isPositive()
        // опыт в таблице лидеров совпадает с профилем, несмотря на гонку
        val board = mockMvc.perform(get("/api/v1/leaderboard?period=ALL_TIME&limit=100").authorizedBy(account))
            .andReturn().json()
        val row = board["entries"].first { it["userId"].asText() == account.userId }
        assertThat(row["xp"].asLong()).isEqualTo(profile["totalXp"].asLong())
    }
}
