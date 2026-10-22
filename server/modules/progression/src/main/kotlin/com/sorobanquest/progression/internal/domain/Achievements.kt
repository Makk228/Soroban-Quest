package com.sorobanquest.progression.internal.domain

import com.sorobanquest.training.api.DrillKind

/** Контекст проверки достижений: состояние профиля после тренировки + сама тренировка. */
internal data class AchievementContext(
    val profile: LearnerProfile,
    val outcome: SessionOutcome,
    val kind: DrillKind,
)

internal data class AchievementDefinition(
    val code: String,
    val title: String,
    val description: String,
    val rule: (AchievementContext) -> Boolean,
)

/**
 * Каталог достижений. Правило — обычная функция, поэтому новое достижение
 * добавляется одной строкой и сразу покрывается тестом.
 */
internal object AchievementCatalog {
    val definitions: List<AchievementDefinition> = listOf(
        AchievementDefinition(
            code = "FIRST_STEPS",
            title = "Первые шаги",
            description = "Завершить первую тренировку",
            rule = { it.profile.totalSessions >= 1 },
        ),
        AchievementDefinition(
            code = "PERFECT_RUN",
            title = "Без единой ошибки",
            description = "Пройти тренировку из 10+ заданий без ошибок",
            rule = { it.outcome.perfect && it.outcome.taskCount >= 10 },
        ),
        AchievementDefinition(
            code = "COMBO_10",
            title = "Серия из десяти",
            description = "Дать 10 правильных ответов подряд",
            rule = { it.outcome.bestCombo >= 10 },
        ),
        AchievementDefinition(
            code = "SPEEDSTER",
            title = "Скоростной счёт",
            description = "Средний ответ быстрее 2 секунд при точности от 90 %",
            rule = { it.outcome.averageAnswerMs in 1..1999 && it.outcome.accuracy >= 0.9 },
        ),
        AchievementDefinition(
            code = "FLASH_MASTER",
            title = "Мастер флеш-анзана",
            description = "Безошибочный флеш-анзан на сложности 7 и выше",
            rule = { it.kind == DrillKind.FLASH_ANZAN && it.outcome.perfect && it.outcome.difficulty >= 7 },
        ),
        AchievementDefinition(
            code = "HIGH_ROLLER",
            title = "Тысяча за забег",
            description = "Набрать 1000 очков за одну тренировку",
            rule = { it.outcome.score >= 1000 },
        ),
        AchievementDefinition(
            code = "WEEK_WARRIOR",
            title = "Неделя без пропусков",
            description = "Серия из 7 дней подряд",
            rule = { it.profile.currentStreakDays >= 7 },
        ),
        AchievementDefinition(
            code = "MONTH_WARRIOR",
            title = "Месяц дисциплины",
            description = "Серия из 30 дней подряд",
            rule = { it.profile.currentStreakDays >= 30 },
        ),
        AchievementDefinition(
            code = "SCHOLAR_5",
            title = "Пятый уровень",
            description = "Достичь 5 уровня",
            rule = { it.profile.level >= 5 },
        ),
        AchievementDefinition(
            code = "SCHOLAR_10",
            title = "Десятый уровень",
            description = "Достичь 10 уровня",
            rule = { it.profile.level >= 10 },
        ),
        AchievementDefinition(
            code = "GRINDER_1000",
            title = "Тысяча примеров",
            description = "Решить 1000 заданий",
            rule = { it.profile.totalTasks >= 1000 },
        ),
    )

    private val byCode = definitions.associateBy { it.code }

    fun find(code: String): AchievementDefinition? = byCode[code]

    fun evaluate(context: AchievementContext): List<AchievementDefinition> =
        definitions.filter { it.rule(context) }
}
