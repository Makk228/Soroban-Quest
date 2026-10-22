package com.sorobanquest.bootstrap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Проверяет связку модулей через события: завершённая тренировка должна
 * отразиться в опыте, достижениях, дневной активности и таблице лидеров.
 */
class ProgressionIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `профиль создаётся сразу после регистрации`() {
        val account = registerUser()

        mockMvc.perform(get("/api/v1/me").authorizedBy(account))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.level").value(1))
            .andExpect(jsonPath("$.totalXp").value(0))
            .andExpect(jsonPath("$.currentStreakDays").value(0))
            .andExpect(jsonPath("$.displayName").value(account.displayName))
    }

    @Test
    fun `завершённая тренировка начисляет опыт, серию и статистику`() {
        val account = registerUser()
        val session = playSession(account, kind = "FLASH_ANZAN", difficulty = 5, taskCount = 10)

        val profile = fetchProfile(account)

        assertThat(session["score"].asInt()).isPositive()
        assertThat(profile["totalXp"].asLong()).isPositive()
        assertThat(profile["currentStreakDays"].asInt()).isEqualTo(1)
        assertThat(profile["totalSessions"].asInt()).isEqualTo(1)
        assertThat(profile["totalTasks"].asInt()).isEqualTo(10)
        assertThat(profile["totalCorrect"].asInt()).isEqualTo(10)
        assertThat(profile["accuracy"].asDouble()).isEqualTo(1.0)
        assertThat(profile["todayXp"].asInt()).isEqualTo(profile["totalXp"].asInt())
        assertThat(profile["xpToNextLevel"].asLong()).isNotNegative()
    }

    @Test
    fun `достижения выдаются по правилам и не дублируются`() {
        val account = registerUser()

        playSession(account, kind = "FLASH_ANZAN", difficulty = 7, taskCount = 10)
        playSession(account, kind = "FLASH_ANZAN", difficulty = 7, taskCount = 10)

        val achievements = mockMvc.perform(get("/api/v1/me/achievements").authorizedBy(account))
            .andExpect(status().isOk).andReturn().json()

        val unlocked = achievements.filter { it["unlocked"].asBoolean() }.map { it["code"].asText() }

        assertThat(unlocked).contains("FIRST_STEPS", "PERFECT_RUN", "COMBO_10", "FLASH_MASTER")
        assertThat(unlocked).doesNotHaveDuplicates()
        assertThat(achievements.map { it["code"].asText() }).contains("MONTH_WARRIOR") // закрытые тоже видны
    }

    @Test
    fun `статистика по видам тренировок накапливается отдельно`() {
        val account = registerUser()

        playSession(account, kind = "MULTIPLICATION", difficulty = 4, taskCount = 5)
        playSession(account, kind = "DIVISION", difficulty = 4, taskCount = 5)
        playSession(account, kind = "MULTIPLICATION", difficulty = 4, taskCount = 5)

        val skills = mockMvc.perform(get("/api/v1/me/skills").authorizedBy(account))
            .andExpect(status().isOk).andReturn().json()

        val byKind = skills.associate { it["kind"].asText() to it }
        assertThat(byKind.keys).containsExactlyInAnyOrder("MULTIPLICATION", "DIVISION")
        assertThat(byKind.getValue("MULTIPLICATION")["sessions"].asInt()).isEqualTo(2)
        assertThat(byKind.getValue("MULTIPLICATION")["tasksAnswered"].asInt()).isEqualTo(10)
        assertThat(byKind.getValue("DIVISION")["sessions"].asInt()).isEqualTo(1)
        assertThat(byKind.getValue("MULTIPLICATION")["bestAverageAnswerMs"].asLong()).isPositive()
    }

    @Test
    fun `дневная активность и цель отражают тренировки`() {
        val account = registerUser()
        playSession(account, taskCount = 5)

        val days = mockMvc.perform(get("/api/v1/me/daily?days=7").authorizedBy(account))
            .andExpect(status().isOk).andReturn().json()

        assertThat(days).hasSize(7)
        val today = days.last()
        assertThat(today["xp"].asInt()).isPositive()
        assertThat(today["sessions"].asInt()).isEqualTo(1)

        mockMvc.perform(put("/api/v1/me/daily-goal").authorizedBy(account).jsonBody(mapOf("dailyGoalXp" to 20)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.dailyGoalXp").value(20))
            .andExpect(jsonPath("$.dailyGoalReached").value(true))

        mockMvc.perform(put("/api/v1/me/daily-goal").authorizedBy(account).jsonBody(mapOf("dailyGoalXp" to 1)))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `таблица лидеров показывает тот же опыт, что и профиль`() {
        val leader = registerUser(displayName = "Лидер")
        val rookie = registerUser(displayName = "Новичок")

        playSession(leader, kind = "FLASH_ANZAN", difficulty = 8, taskCount = 15)
        playSession(rookie, kind = "FLASH_ANZAN", difficulty = 1, taskCount = 5, mistakesAt = setOf(0, 1, 2))

        val leaderProfile = fetchProfile(leader)
        val board = mockMvc.perform(get("/api/v1/leaderboard?period=ALL_TIME&limit=50").authorizedBy(leader))
            .andExpect(status().isOk).andReturn().json()

        val leaderRow = board["entries"].first { it["userId"].asText() == leader.userId }
        val rookieRow = board["entries"].first { it["userId"].asText() == rookie.userId }

        assertThat(leaderRow["xp"].asLong()).isEqualTo(leaderProfile["totalXp"].asLong())
        assertThat(leaderRow["displayName"].asText()).isEqualTo("Лидер")
        assertThat(leaderRow["currentUser"].asBoolean()).isTrue()
        assertThat(leaderRow["rank"].asInt()).isLessThan(rookieRow["rank"].asInt())
        assertThat(board["viewerEntry"]["userId"].asText()).isEqualTo(leader.userId)
    }

    @Test
    fun `недельный рейтинг использует ключ ISO-недели`() {
        val account = registerUser()
        playSession(account, taskCount = 5)

        mockMvc.perform(get("/api/v1/leaderboard?period=WEEKLY&limit=10").authorizedBy(account))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.period").value("WEEKLY"))
            .andExpect(jsonPath("$.periodKey").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-W\\d{2}")))
            .andExpect(jsonPath("$.totalParticipants").isNumber)
    }

    @Test
    fun `некорректный размер таблицы лидеров отклоняется`() {
        val account = registerUser()

        mockMvc.perform(get("/api/v1/leaderboard?limit=0").authorizedBy(account))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
    }
}
