package com.sorobanquest.progression

import com.sorobanquest.progression.internal.domain.LevelCurve
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LevelCurveTest {

    @ParameterizedTest(name = "{0} опыта -> уровень {1}")
    @CsvSource(
        "0, 1",
        "99, 1",
        "100, 2",
        "299, 2",
        "300, 3",
        "600, 4",
        "1000, 5",
    )
    fun `уровень считается по накопленному опыту`(totalXp: Long, expectedLevel: Int) {
        assertThat(LevelCurve.levelFor(totalXp)).isEqualTo(expectedLevel)
    }

    @Test
    fun `опыт внутри уровня и остаток до следующего согласованы`() {
        val totalXp = 450L
        val level = LevelCurve.levelFor(totalXp)

        assertThat(level).isEqualTo(3)
        assertThat(LevelCurve.xpIntoLevel(totalXp)).isEqualTo(totalXp - LevelCurve.thresholdFor(level))
        assertThat(LevelCurve.xpToNextLevel(totalXp)).isEqualTo(LevelCurve.thresholdFor(level + 1) - totalXp)
    }

    @Test
    fun `кривая монотонна - больше опыта никогда не даёт меньший уровень`() {
        var previous = 1
        (0..20_000L step 137).forEach { xp ->
            val level = LevelCurve.levelFor(xp)
            assertThat(level).isGreaterThanOrEqualTo(previous)
            previous = level
        }
    }

    @Test
    fun `на максимальном уровне остаток до следующего равен нулю`() {
        val hugeXp = 10_000_000L
        assertThat(LevelCurve.levelFor(hugeXp)).isEqualTo(LevelCurve.MAX_LEVEL)
        assertThat(LevelCurve.xpToNextLevel(hugeXp)).isZero()
    }
}
