package com.sorobanquest.training.internal.domain.generator

import com.sorobanquest.shared.error.NotFoundException
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.internal.domain.Difficulty
import com.sorobanquest.training.internal.domain.GeneratedTask
import kotlin.random.Random

/**
 * Генератор заданий одного вида.
 *
 * Контракт: для одних и тех же (seed, index, difficulty) генератор обязан
 * вернуть ровно то же задание. Благодаря этому сервер не хранит задания в БД,
 * а восстанавливает их при проверке ответа.
 */
internal interface TaskGenerator {
    val kind: DrillKind

    fun generate(index: Int, difficulty: Difficulty, random: Random): GeneratedTask
}

/** Реестр генераторов + вывод псевдослучайного потока из (seed, index). */
internal class TaskGeneratorRegistry(generators: List<TaskGenerator>) {
    private val byKind: Map<DrillKind, TaskGenerator> = generators.associateBy { it.kind }

    fun generate(kind: DrillKind, index: Int, difficulty: Difficulty, seed: Long): GeneratedTask {
        val generator = byKind[kind] ?: throw NotFoundException("Генератор тренировки", kind.name)
        return generator.generate(index, difficulty, randomFor(seed, index))
    }

    fun supports(kind: DrillKind): Boolean = byKind.containsKey(kind)

    companion object {
        /**
         * Версия алгоритма генерации. Сохраняется в сессии: если правила генерации
         * изменятся, старые сессии можно будет отличить и воспроизвести корректно.
         */
        const val GENERATOR_VERSION = 1

        fun randomFor(seed: Long, index: Int): Random = Random(seed + (index + 1L) * 1_000_003L)
    }
}
