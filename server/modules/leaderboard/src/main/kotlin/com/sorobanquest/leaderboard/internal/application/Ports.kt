package com.sorobanquest.leaderboard.internal.application

import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.shared.model.UserId

internal data class RankedRow(
    val rank: Int,
    val userId: UserId,
    val xp: Long,
    val sessions: Int,
)

internal interface LeaderboardRepository {
    /** Атомарно прибавить опыт участнику в рамках периода. */
    fun addXp(userId: UserId, period: LeaderboardPeriod, periodKey: String, xp: Int, sessions: Int)

    fun top(period: LeaderboardPeriod, periodKey: String, limit: Int): List<RankedRow>

    fun rankOf(userId: UserId, period: LeaderboardPeriod, periodKey: String): RankedRow?

    fun countParticipants(period: LeaderboardPeriod, periodKey: String): Long
}
