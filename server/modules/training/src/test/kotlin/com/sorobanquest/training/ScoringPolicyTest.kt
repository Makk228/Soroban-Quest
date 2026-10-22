package com.sorobanquest.training

import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.DifficultyProfile
import com.sorobanquest.training.internal.domain.ScoringPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScoringPolicyTest {

    private val difficulty = Difficulty.of(5)
    private val target = DifficultyProfile.of(difficulty).targetAnswerMs

    @Test
    fun `за неверный ответ очки не начисляются`() {
        val points = ScoringPolicy.pointsFor(difficulty, correct = false, elapsedMs = 100, comboAfterAnswer = 0)
        assertThat(points).isZero()
    }

    @Test
    fun `быстрый ответ оценивается выше медленного`() {
        val fast = ScoringPolicy.pointsFor(difficulty, true, elapsedMs = target / 4, comboAfterAnswer = 1)
        val slow = ScoringPolicy.pointsFor(difficulty, true, elapsedMs = target * 3, comboAfterAnswer = 1)
        assertThat(fast).isGreaterThan(slow)
    }

    @Test
    fun `коэффициент скорости ограничен сверху и снизу`() {
        assertThat(ScoringPolicy.speedFactor(1, target)).isEqualTo(1.5)
        assertThat(ScoringPolicy.speedFactor(target * 10, target)).isEqualTo(0.75)
        assertThat(ScoringPolicy.speedFactor(target, target)).isBetween(0.75, 1.5)
    }

    @Test
    fun `комбо увеличивает награду, но не бесконечно`() {
        val noCombo = ScoringPolicy.pointsFor(difficulty, true, target, comboAfterAnswer = 1)
        val combo5 = ScoringPolicy.pointsFor(difficulty, true, target, comboAfterAnswer = 5)
        val combo50 = ScoringPolicy.pointsFor(difficulty, true, target, comboAfterAnswer = 50)
        val comboMax = ScoringPolicy.pointsFor(difficulty, true, target, comboAfterAnswer = 11)

        assertThat(combo5).isGreaterThan(noCombo)
        assertThat(combo50).isEqualTo(comboMax)
    }

    @Test
    fun `сложность повышает базовую стоимость задания`() {
        val easy = ScoringPolicy.pointsFor(Difficulty.of(1), true, 1, comboAfterAnswer = 1)
        val hard = ScoringPolicy.pointsFor(Difficulty.of(10), true, 1, comboAfterAnswer = 1)
        assertThat(hard).isGreaterThan(easy)
    }
}
