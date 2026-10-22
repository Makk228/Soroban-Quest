package com.sorobanquest.api.rest.leaderboard

import com.sorobanquest.identity.api.AuthenticatedUser
import com.sorobanquest.leaderboard.api.LeaderboardEntry
import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.leaderboard.api.LeaderboardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class LeaderboardEntryResponse(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val xp: Long,
    val sessions: Int,
    val currentUser: Boolean,
) {
    companion object {
        fun of(entry: LeaderboardEntry) = LeaderboardEntryResponse(
            rank = entry.rank,
            userId = entry.userId.toString(),
            displayName = entry.displayName,
            xp = entry.xp,
            sessions = entry.sessions,
            currentUser = entry.currentUser,
        )
    }
}

data class LeaderboardResponse(
    val period: LeaderboardPeriod,
    val periodKey: String,
    val entries: List<LeaderboardEntryResponse>,
    val viewerEntry: LeaderboardEntryResponse?,
    val totalParticipants: Long,
)

@Tag(name = "Таблица лидеров", description = "Недельный и общий рейтинг по опыту")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/v1/leaderboard")
class LeaderboardController(
    private val leaderboard: LeaderboardService,
) {

    @Operation(summary = "Таблица лидеров; строка текущего пользователя возвращается, даже если он вне топа")
    @GetMapping
    fun leaderboard(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(required = false, defaultValue = "WEEKLY") period: LeaderboardPeriod,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
    ): LeaderboardResponse {
        val view = leaderboard.top(period, limit, user.userId)
        return LeaderboardResponse(
            period = view.period,
            periodKey = view.periodKey,
            entries = view.entries.map(LeaderboardEntryResponse::of),
            viewerEntry = view.viewerEntry?.let(LeaderboardEntryResponse::of),
            totalParticipants = view.totalParticipants,
        )
    }
}
