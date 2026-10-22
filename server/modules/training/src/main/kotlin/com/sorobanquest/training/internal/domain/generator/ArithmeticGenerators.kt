package com.sorobanquest.training.internal.domain.generator

import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.DifficultyProfile
import com.sorobanquest.training.internal.domain.GeneratedTask
import com.sorobanquest.training.internal.domain.Operator
import com.sorobanquest.training.internal.domain.TaskPrompt
import kotlin.math.max
import kotlin.random.Random

/**
 * Флеш-анзан: числа «вспыхивают» по очереди, ученик держит сумму в уме.
 * Только сложение — классический формат ментальной арифметики.
 */
internal class FlashAnzanGenerator : TaskGenerator {
    override val kind = DrillKind.FLASH_ANZAN

    override fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask {
        val profile = DifficultyProfile.of(difficulty)
        val numbers = List(profile.terms) { random.nextInt(profile.minOperand, profile.maxOperand + 1) }
        return GeneratedTask(
            index = index,
            kind = kind,
            prompt = TaskPrompt.Sequence(numbers, profile.flashIntervalMs),
            answer = numbers.sumOf { it.toLong() },
            timeLimitMs = profile.timeLimitMs + profile.terms * profile.flashIntervalMs,
        )
    }
}

/**
 * Цепочка «+/−». Правило соробана: промежуточная сумма не должна уходить в минус,
 * поэтому вычитание подставляется только если результат остаётся положительным.
 */
internal class AddSubChainGenerator : TaskGenerator {
    override val kind = DrillKind.ADD_SUB_CHAIN

    override fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask {
        val profile = DifficultyProfile.of(difficulty)
        val numbers = ArrayList<Int>(profile.terms)
        var running = random.nextInt(profile.minOperand, profile.maxOperand + 1)
        numbers += running

        repeat(profile.terms - 1) {
            val magnitude = random.nextInt(profile.minOperand, profile.maxOperand + 1)
            val subtract = random.nextBoolean() && running - magnitude >= 1
            if (subtract) {
                running -= magnitude
                numbers += -magnitude
            } else {
                running += magnitude
                numbers += magnitude
            }
        }

        return GeneratedTask(
            index = index,
            kind = kind,
            prompt = TaskPrompt.Sequence(numbers, profile.flashIntervalMs),
            answer = running.toLong(),
            timeLimitMs = profile.timeLimitMs,
        )
    }
}

/** Умножение: множитель короче множимого на разряд — так тренируют на соробане. */
internal class MultiplicationGenerator : TaskGenerator {
    override val kind = DrillKind.MULTIPLICATION

    override fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask {
        val profile = DifficultyProfile.of(difficulty)
        val left = random.nextInt(profile.minOperand, profile.maxOperand + 1)
        val rightDigits = max(1, profile.digits - 1)
        val right = randomWithDigits(random, rightDigits)
        return GeneratedTask(
            index = index,
            kind = kind,
            prompt = TaskPrompt.Binary(left, right, Operator.MULTIPLY),
            answer = left.toLong() * right.toLong(),
            timeLimitMs = profile.timeLimitMs,
        )
    }
}

/** Деление строится «от ответа»: делимое = делитель × частное, поэтому всегда нацело. */
internal class DivisionGenerator : TaskGenerator {
    override val kind = DrillKind.DIVISION

    override fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask {
        val profile = DifficultyProfile.of(difficulty)
        val divisor = randomWithDigits(random, max(1, profile.digits - 1)).coerceAtLeast(2)
        val quotient = random.nextInt(profile.minOperand, profile.maxOperand + 1)
        val dividend = divisor * quotient
        return GeneratedTask(
            index = index,
            kind = kind,
            prompt = TaskPrompt.Binary(dividend, divisor, Operator.DIVIDE),
            answer = quotient.toLong(),
            timeLimitMs = profile.timeLimitMs,
        )
    }
}

/**
 * Смешанный режим: вид задания определяется номером задания и seed-ом,
 * поэтому последовательность так же воспроизводима, как и в обычных режимах.
 */
internal class MixedGenerator(private val delegates: List<TaskGenerator>) : TaskGenerator {
    override val kind = DrillKind.MIXED

    init {
        require(delegates.isNotEmpty()) { "Для смешанного режима нужен хотя бы один генератор" }
    }

    override fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask {
        val delegate = delegates[random.nextInt(delegates.size)]
        return delegate.generate(index, difficulty, random)
    }
}

private fun randomWithDigits(random: Random, digits: Int): Int {
    val min = if (digits <= 1) 2 else intPow10(digits - 1)
    val max = intPow10(digits) - 1
    return random.nextInt(min, max + 1)
}

private fun intPow10(exponent: Int): Int {
    var result = 1
    repeat(exponent) { result *= 10 }
    return result
}
