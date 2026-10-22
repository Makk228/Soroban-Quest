package com.sorobanquest.leaderboard.internal.application

import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.leaderboard.api.LeaderboardEntry
import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.leaderboard.api.LeaderboardService
import com.sorobanquest.leaderboard.api.LeaderboardView
import com.sorobanquest.leaderboard.internal.domain.PeriodKey
import com.sorobanquest.progression.api.XpAwarded
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.event.DomainEventHandler
import com.sorobanquest.shared.model.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

private val log = KotlinLogging.logger {}

internal const val MAX_LEADERBOARD_LIMIT = 100

internal class LeaderboardServiceImpl(
    private val repository: LeaderboardRepository,
    private val users: UserDirectory,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : LeaderboardService {

    override fun top(period: LeaderboardPeriod, limit: Int, viewer: UserId?): LeaderboardView {
        if (limit !in 1..MAX_LEADERBOARD_LIMIT) {
            throw ValidationException(
                "Размер таблицы лидеров должен быть в диапазоне 1..$MAX_LEADERBOARD_LIMIT",
                mapOf("limit" to limit),
            )
        }
        val periodKey = PeriodKey.of(period, today())
        val rows = repository.top(period, periodKey, limit)
        val viewerRow = viewer?.let { id ->
            rows.firstOrNull { it.userId == id } ?: repository.rankOf(id, period, periodKey)
        }

        val names = users.findAllByIds((rows.map { it.userId } + listOfNotNull(viewerRow?.userId)).toSet())
        fun toEntry(row: RankedRow) = LeaderboardEntry(
            rank = row.rank,
            userId = row.userId,
            displayName = names[row.userId]?.displayName ?: "Аноним",
            xp = row.xp,
            sessions = row.sessions,
            currentUser = row.userId == viewer,
        )

        return LeaderboardView(
            period = period,
            periodKey = periodKey,
            entries = rows.map(::toEntry),
            viewerEntry = viewerRow?.let(::toEntry),
            totalParticipants = repository.countParticipants(period, periodKey),
        )
    }

    private fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), zoneId)
}

/**
 * Рейтинг обновляется по событию начисления опыта, а не по завершению тренировки:
 * так цифры в профиле и в таблице лидеров считаются по одному и тому же правилу.
 */
internal class XpAwardedHandler(
    private val repository: LeaderboardRepository,
    private val zoneId: ZoneId,
) : DomainEventHandler<XpAwarded> {

    override val eventType = XpAwarded::class.java

    override fun handle(event: XpAwarded) {
        if (event.xp <= 0) return
        val date = LocalDate.ofInstant(event.occurredAt, zoneId)
        LeaderboardPeriod.entries.forEach { period ->
            repository.addXp(
                userId = event.userId,
                period = period,
                periodKey = PeriodKey.of(period, date),
                xp = event.xp,
                sessions = 1,
            )
        }
        log.debug { "Рейтинг обновлён: пользователь ${event.userId}, +${event.xp} опыта" }
    }
}
