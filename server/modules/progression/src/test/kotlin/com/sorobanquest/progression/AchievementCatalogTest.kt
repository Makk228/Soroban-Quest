package com.sorobanquest.progression

import com.sorobanquest.progression.internal.domain.AchievementCatalog
import com.sorobanquest.progression.internal.domain.AchievementContext
import com.sorobanquest.progression.internal.domain.LearnerProfile
import com.sorobanquest.progression.internal.domain.LearnerProfileFactory
import com.sorobanquest.progression.internal.domain.SessionOutcome
import com.sorobanquest.shared.model.UserId
import com.sorobanquest.training.api.DrillKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class AchievementCatalogTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val today = LocalDate.of(2026, 10, 22)

    private fun context(
        outcome: SessionOutcome,
        kind: DrillKind,
        profileMutator: (LearnerProfile) -> LearnerProfile,
    ): AchievementContext {
        val profile =
            profileMutator(
                LearnerProfileFactory.createFor(UserId(UUID.randomUUID()), now).applySession(outcome, today, now).profile,
            )
        return AchievementContext(profile, outcome, kind)
    }

    @Test
    fun `коды достижений уникальны`() {
        val codes = AchievementCatalog.definitions.map { it.code }
        assertThat(codes).doesNotHaveDuplicates()
    }

    @Test
    fun `первая тренировка открывает достижение первых шагов`() {
        val outcome = SessionOutcome(3, 5, 3, 100, 3, 15_000)
        val unlocked = AchievementCatalog.evaluate(context(outcome, kind = DrillKind.FLASH_ANZAN, profileMutator = { it })).map { it.code }
        assertThat(unlocked).contains("FIRST_STEPS")
    }

    @Test
    fun `безошибочная тренировка из десяти заданий открывает PERFECT_RUN`() {
        val perfect = SessionOutcome(5, 10, 10, 600, 10, 25_000)
        val unlocked = AchievementCatalog.evaluate(context(perfect, kind = DrillKind.FLASH_ANZAN, profileMutator = { it })).map { it.code }

        assertThat(unlocked).contains("PERFECT_RUN", "COMBO_10")
    }

    @Test
    fun `короткая безошибочная тренировка PERFECT_RUN не открывает`() {
        val shortPerfect = SessionOutcome(5, 5, 5, 300, 5, 12_000)
        val unlocked =
            AchievementCatalog
                .evaluate(
                    context(shortPerfect, kind = DrillKind.FLASH_ANZAN, profileMutator = { it }),
                ).map { it.code }

        assertThat(unlocked).doesNotContain("PERFECT_RUN")
    }

    @Test
    fun `скоростное достижение требует и скорости, и точности`() {
        val fastAccurate = SessionOutcome(5, 10, 10, 700, 10, 10_000) // 1000 мс на задание
        val fastSloppy = SessionOutcome(5, 10, 5, 300, 2, 10_000)

        assertThat(
            AchievementCatalog
                .evaluate(
                    context(fastAccurate, kind = DrillKind.FLASH_ANZAN, profileMutator = {
                        it
                    }),
                ).map { it.code },
        ).contains("SPEEDSTER")
        assertThat(
            AchievementCatalog
                .evaluate(
                    context(fastSloppy, kind = DrillKind.FLASH_ANZAN, profileMutator = {
                        it
                    }),
                ).map { it.code },
        ).doesNotContain("SPEEDSTER")
    }

    @Test
    fun `мастер флеш-анзана открывается только на высокой сложности и в своём режиме`() {
        val hardPerfect = SessionOutcome(8, 10, 10, 900, 10, 30_000)

        val flash = AchievementCatalog.evaluate(context(hardPerfect, DrillKind.FLASH_ANZAN, profileMutator = { it })).map { it.code }
        val division = AchievementCatalog.evaluate(context(hardPerfect, DrillKind.DIVISION, profileMutator = { it })).map { it.code }

        assertThat(flash).contains("FLASH_MASTER")
        assertThat(division).doesNotContain("FLASH_MASTER")
    }

    @Test
    fun `достижения за серию зависят от состояния профиля`() {
        val outcome = SessionOutcome(5, 10, 10, 500, 10, 20_000)
        val weekly = context(outcome, kind = DrillKind.FLASH_ANZAN) { it.copy(currentStreakDays = 7) }

        assertThat(AchievementCatalog.evaluate(weekly).map { it.code }).contains("WEEK_WARRIOR")
        assertThat(AchievementCatalog.evaluate(weekly).map { it.code }).doesNotContain("MONTH_WARRIOR")
    }
}
