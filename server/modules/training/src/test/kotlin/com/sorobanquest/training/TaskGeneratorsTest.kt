package com.sorobanquest.training

import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.DifficultyProfile
import com.sorobanquest.training.internal.domain.Operator
import com.sorobanquest.training.internal.domain.TaskPrompt
import com.sorobanquest.training.internal.domain.generator.AddSubChainGenerator
import com.sorobanquest.training.internal.domain.generator.DivisionGenerator
import com.sorobanquest.training.internal.domain.generator.FlashAnzanGenerator
import com.sorobanquest.training.internal.domain.generator.MixedGenerator
import com.sorobanquest.training.internal.domain.generator.MultiplicationGenerator
import com.sorobanquest.training.internal.domain.generator.TaskGeneratorRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Генерация заданий — основа честности приложения: сервер не хранит задания,
 * а восстанавливает их по (seed, номер). Поэтому проверяем детерминированность
 * и соблюдение правил ментальной арифметики.
 */
class TaskGeneratorsTest {

    private val registry = newRegistry()

    @ParameterizedTest
    @EnumSource(DrillKind::class)
    fun `одинаковые seed и номер задания дают одинаковое задание`(kind: DrillKind) {
        val difficulty = Difficulty.of(5)
        repeat(20) { index ->
            val first = registry.generate(kind, index, difficulty, seed = 42L)
            val second = registry.generate(kind, index, difficulty, seed = 42L)
            assertThat(first).isEqualTo(second)
        }
    }

    @Test
    fun `разные seed дают разные последовательности`() {
        val difficulty = Difficulty.of(5)
        val first = (0 until 10).map { registry.generate(DrillKind.FLASH_ANZAN, it, difficulty, seed = 1L).answer }
        val second = (0 until 10).map { registry.generate(DrillKind.FLASH_ANZAN, it, difficulty, seed = 2L).answer }
        assertThat(first).isNotEqualTo(second)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3, 5, 7, 10])
    fun `флеш-анзан - ответ равен сумме показанных чисел`(level: Int) {
        val difficulty = Difficulty.of(level)
        val profile = DifficultyProfile.of(difficulty)
        repeat(30) { index ->
            val task = registry.generate(DrillKind.FLASH_ANZAN, index, difficulty, seed = 7L)
            val prompt = task.prompt as TaskPrompt.Sequence
            assertThat(prompt.numbers).hasSize(profile.terms)
            assertThat(prompt.numbers).allMatch { it in profile.minOperand..profile.maxOperand }
            assertThat(task.answer).isEqualTo(prompt.numbers.sumOf { it.toLong() })
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 4, 7, 10])
    fun `цепочка плюс-минус - промежуточная сумма никогда не уходит в минус`(level: Int) {
        val difficulty = Difficulty.of(level)
        repeat(50) { index ->
            val task = registry.generate(DrillKind.ADD_SUB_CHAIN, index, difficulty, seed = 11L)
            val prompt = task.prompt as TaskPrompt.Sequence
            var running = 0L
            prompt.numbers.forEach { term ->
                running += term
                assertThat(running)
                    .describedAs("промежуточный результат цепочки %s", prompt.numbers)
                    .isGreaterThanOrEqualTo(1)
            }
            assertThat(task.answer).isEqualTo(running)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 5, 10])
    fun `умножение - ответ равен произведению операндов`(level: Int) {
        val difficulty = Difficulty.of(level)
        repeat(30) { index ->
            val task = registry.generate(DrillKind.MULTIPLICATION, index, difficulty, seed = 3L)
            val prompt = task.prompt as TaskPrompt.Binary
            assertThat(prompt.operator).isEqualTo(Operator.MULTIPLY)
            assertThat(task.answer).isEqualTo(prompt.left.toLong() * prompt.right)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 5, 10])
    fun `деление всегда выполняется нацело`(level: Int) {
        val difficulty = Difficulty.of(level)
        repeat(30) { index ->
            val task = registry.generate(DrillKind.DIVISION, index, difficulty, seed = 5L)
            val prompt = task.prompt as TaskPrompt.Binary
            assertThat(prompt.operator).isEqualTo(Operator.DIVIDE)
            assertThat(prompt.right).isNotZero()
            assertThat(prompt.left % prompt.right).isZero()
            assertThat(task.answer).isEqualTo((prompt.left / prompt.right).toLong())
        }
    }

    @Test
    fun `смешанный режим выдаёт задания разных видов`() {
        val difficulty = Difficulty.of(6)
        val kinds = (0 until 40).map { registry.generate(DrillKind.MIXED, it, difficulty, seed = 99L).kind }.toSet()
        assertThat(kinds).hasSizeGreaterThan(1)
        assertThat(kinds).doesNotContain(DrillKind.MIXED)
    }

    @Test
    fun `сложность влияет на разрядность и количество слагаемых`() {
        val easy = DifficultyProfile.of(Difficulty.of(1))
        val hard = DifficultyProfile.of(Difficulty.of(10))
        assertThat(hard.digits).isGreaterThan(easy.digits)
        assertThat(hard.terms).isGreaterThan(easy.terms)
        assertThat(hard.flashIntervalMs).isLessThan(easy.flashIntervalMs)
    }

    private fun newRegistry(): TaskGeneratorRegistry {
        val flash = FlashAnzanGenerator()
        val chain = AddSubChainGenerator()
        val multiplication = MultiplicationGenerator()
        val division = DivisionGenerator()
        return TaskGeneratorRegistry(
            listOf(flash, chain, multiplication, division, MixedGenerator(listOf(flash, chain, multiplication, division))),
        )
    }
}
