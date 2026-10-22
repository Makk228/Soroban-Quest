package com.sorobanquest.training.internal.domain

import com.sorobanquest.training.api.DrillKind

internal enum class Operator(val symbol: String) {
    MULTIPLY("×"),
    DIVIDE("÷"),
}

/** Условие задания внутри домена (вместе с правильным ответом). */
internal sealed interface TaskPrompt {
    data class Sequence(val numbers: List<Int>, val flashIntervalMs: Int) : TaskPrompt

    data class Binary(val left: Int, val right: Int, val operator: Operator) : TaskPrompt
}

internal data class GeneratedTask(
    val index: Int,
    val kind: DrillKind,
    val prompt: TaskPrompt,
    val answer: Long,
    val timeLimitMs: Long,
)
