package com.sorobanquest.leaderboard

import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.identity.api.UserSummary
import com.sorobanquest.leaderboard.api.LeaderboardPeriod
import com.sorobanquest.leaderboard.internal.application.LeaderboardRepository
import com.sorobanquest.leaderboard.internal.application.LeaderboardServiceImpl
import com.sorobanquest.leaderboard.internal.application.RankedRow
import com.sorobanquest.leaderboard.internal.application.XpAwardedHandler
import com.sorobanquest.leaderboard.internal.domain.PeriodKey
import com.sorobanquest.progression.api.XpAwarded
import com.sorobanquest.shared.error.ValidationException
import com.sorobanquest.shared.model.SessionId
import com.sorobanquest.shared.model.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class LeaderboardServiceTest {

    private val now = Instant.parse("2026-10-22T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = InMemoryLeaderboardRepository()
    private val leader = UserId(UUID.randomUUID())
    private val viewer = UserId(UUID.randomUUID())
    private val directory = FakeUserDirectory(mapOf(leader to "Лидер", viewer to "Ученик"))
    private val service = LeaderboardServiceImpl(repository, directory, clock, ZoneOffset.UTC)

    private fun award(userId: UserId, xp: Int) {
        XpAwardedHandler(repository, ZoneOffset.UTC).handle(
            XpAwarded(
                userId = userId,
                xp = xp,
                totalXp = xp.toLong(),
                level = 1,
                levelUp = false,
                sourceSessionId = SessionId(UUID.randomUUID()),
                occurredAt = now,
            ),
        )
    }

    @Test
    fun `опыт попадает и в недельный, и в общий рейтинг`() {
        award(leader, 120)

        val weekly = service.top(LeaderboardPeriod.WEEKLY, 10, viewer = null)
        val allTime = service.top(LeaderboardPeriod.ALL_TIME, 10, viewer = null)

        assertThat(weekly.periodKey).isEqualTo(PeriodKey.weekKey(LocalDate.of(2026, 10, 22)))
        assertThat(weekly.entries).singleElement().satisfies({
            assertThat(it.xp).isEqualTo(120)
            assertThat(it.displayName).isEqualTo("Лидер")
        })
        assertThat(allTime.entries.first().xp).isEqualTo(120)
    }

    @Test
    fun `начисления накапливаются и формируют места`() {
        award(leader, 100)
        award(leader, 50)
        award(viewer, 70)

        val view = service.top(LeaderboardPeriod.WEEKLY, 10, viewer)

        assertThat(view.entries.map { it.rank to it.xp }).containsExactly(1 to 150L, 2 to 70L)
        assertThat(view.entries.first { it.currentUser }.userId).isEqualTo(viewer)
        assertThat(view.totalParticipants).isEqualTo(2)
    }

    @Test
    fun `строка пользователя возвращается, даже если он не попал в топ`() {
        award(leader, 500)
        award(viewer, 10)

        val view = service.top(LeaderboardPeriod.WEEKLY, limit = 1, viewer = viewer)

        assertThat(view.entries).hasSize(1)
        assertThat(view.viewerEntry).isNotNull
        assertThat(view.viewerEntry!!.rank).isEqualTo(2)
        assertThat(view.viewerEntry!!.currentUser).isTrue()
    }

    @Test
    fun `нулевое начисление рейтинг не меняет`() {
        award(leader, 0)

        assertThat(service.top(LeaderboardPeriod.WEEKLY, 10, viewer = null).entries).isEmpty()
    }

    @Test
    fun `некорректный размер выборки отклоняется`() {
        assertThatThrownBy { service.top(LeaderboardPeriod.WEEKLY, 0, viewer = null) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { service.top(LeaderboardPeriod.WEEKLY, 1000, viewer = null) }
            .isInstanceOf(ValidationException::class.java)
    }
}

private class FakeUserDirectory(private val names: Map<UserId, String>) : UserDirectory {
    override fun findById(id: UserId): UserSummary? = names[id]?.let {
        UserSummary(id, "$it@example.com", it, Instant.EPOCH)
    }

    override fun findAllByIds(ids: Collection<UserId>): Map<UserId, UserSummary> =
        ids.mapNotNull { id -> findById(id)?.let { id to it } }.toMap()
}

private class InMemoryLeaderboardRepository : LeaderboardRepository {
    private data class Key(val period: LeaderboardPeriod, val periodKey: String, val userId: UserId)

    private val xp = LinkedHashMap<Key, Pair<Long, Int>>()

    override fun addXp(userId: UserId, period: LeaderboardPeriod, periodKey: String, xp: Int, sessions: Int) {
        val key = Key(period, periodKey, userId)
        val current = this.xp[key] ?: (0L to 0)
        this.xp[key] = (current.first + xp) to (current.second + sessions)
    }

    private fun ranked(period: LeaderboardPeriod, periodKey: String): List<RankedRow> =
        xp.filterKeys { it.period == period && it.periodKey == periodKey }
            .entries
            .sortedByDescending { it.value.first }
            .mapIndexed { index, entry ->
                RankedRow(index + 1, entry.key.userId, entry.value.first, entry.value.second)
            }

    override fun top(period: LeaderboardPeriod, periodKey: String, limit: Int): List<RankedRow> =
        ranked(period, periodKey).take(limit)

    override fun rankOf(userId: UserId, period: LeaderboardPeriod, periodKey: String): RankedRow? =
        ranked(period, periodKey).firstOrNull { it.userId == userId }

    override fun countParticipants(period: LeaderboardPeriod, periodKey: String): Long =
        ranked(period, periodKey).size.toLong()
}
