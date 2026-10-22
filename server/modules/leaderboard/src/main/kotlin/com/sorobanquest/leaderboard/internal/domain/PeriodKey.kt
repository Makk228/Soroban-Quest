package com.sorobanquest.leaderboard.internal.domain

import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Ключ периода рейтинга. Для недельного рейтинга используется ISO-неделя
 * (понедельник — первый день), чтобы итог не зависел от локали сервера.
 */
internal object PeriodKey {
    const val ALL_TIME_KEY = "ALL"

    fun of(period: LeaderboardPeriod, date: LocalDate): String = when (period) {
        LeaderboardPeriod.ALL_TIME -> ALL_TIME_KEY
        LeaderboardPeriod.WEEKLY -> weekKey(date)
    }

    fun weekKey(date: LocalDate): String {
        val fields = WeekFields.ISO
        val week = date.get(fields.weekOfWeekBasedYear())
        val year = date.get(fields.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }
}
