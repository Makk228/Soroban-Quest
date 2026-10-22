package com.sorobanquest.leaderboard

import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.leaderboard.internal.domain.PeriodKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PeriodKeyTest {

    @Test
    fun `ключ недели одинаков для всех дней одной ISO-недели`() {
        val monday = LocalDate.of(2026, 10, 19)
        val sunday = LocalDate.of(2026, 10, 25)

        assertThat(PeriodKey.weekKey(monday)).isEqualTo(PeriodKey.weekKey(sunday))
    }

    @Test
    fun `следующий понедельник открывает новую неделю`() {
        val sunday = LocalDate.of(2026, 10, 25)
        val nextMonday = LocalDate.of(2026, 10, 26)

        assertThat(PeriodKey.weekKey(sunday)).isNotEqualTo(PeriodKey.weekKey(nextMonday))
    }

    @Test
    fun `на стыке года используется ISO-неделя, а не календарный год`() {
        // 31 декабря 2025 относится к первой ISO-неделе 2026 года
        assertThat(PeriodKey.weekKey(LocalDate.of(2025, 12, 31))).isEqualTo("2026-W01")
    }

    @Test
    fun `формат ключа фиксирован`() {
        assertThat(PeriodKey.weekKey(LocalDate.of(2026, 1, 8))).matches("\\d{4}-W\\d{2}")
        assertThat(PeriodKey.of(LeaderboardPeriod.ALL_TIME, LocalDate.of(2026, 1, 8))).isEqualTo("ALL")
    }
}
