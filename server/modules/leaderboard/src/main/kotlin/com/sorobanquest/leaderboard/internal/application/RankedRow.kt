package com.sorobanquest.leaderboard.internal.application

import com.sorobanquest.shared.model.UserId

internal data class RankedRow(
    val rank: Int,
    val userId: UserId,
    val xp: Long,
    val sessions: Int,
)
