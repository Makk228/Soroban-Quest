package com.sorobanquest.training.internal.application

/** Источник seed-ов для новых сессий (в тестах подменяется на фиксированный). */
internal fun interface SeedGenerator {
    fun newSeed(): Long
}

/** Настройки модуля, вынесенные из кода в конфигурацию приложения. */
internal data class TrainingSettings(
    val maxActiveSessions: Int,
    val minTaskCount: Int,
    val maxTaskCount: Int,
)
