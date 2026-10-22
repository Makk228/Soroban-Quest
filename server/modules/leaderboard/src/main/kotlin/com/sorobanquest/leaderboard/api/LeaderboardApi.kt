package com.sorobanquest.leaderboard.api

import com.sorobanquest.shared.model.UserId

enum class LeaderboardPeriod {
    /** Текущая ISO-неделя. */
    WEEKLY,

    /** За всё время. */
    ALL_TIME,
}

data class LeaderboardEntry(
    val rank: Int,
    val userId: UserId,
    val displayName: String,
    val xp: Long,
    val sessions: Int,
    val currentUser: Boolean,
)

data class LeaderboardView(
    val period: LeaderboardPeriod,
    /** Человекочитаемый ключ периода, например `2026-W43` или `ALL`. */
    val periodKey: String,
    val entries: List<LeaderboardEntry>,
    /** Строка самого пользователя — даже если он не попал в топ. */
    val viewerEntry: LeaderboardEntry?,
    val totalParticipants: Long,
)

interface LeaderboardService {
    fun top(period: LeaderboardPeriod, limit: Int, viewer: UserId?): LeaderboardView
}
