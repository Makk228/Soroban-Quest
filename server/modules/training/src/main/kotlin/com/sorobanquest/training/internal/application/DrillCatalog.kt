package com.sorobanquest.training.internal.application

import com.sorobanquest.training.api.DrillDescriptor
import com.sorobanquest.training.api.DrillKind
import com.sorobanquest.training.internal.domain.Difficulty

/** Витрина доступных тренировок — то, что видит ученик на экране выбора. */
internal object DrillCatalog {
    val descriptors: List<DrillDescriptor> = listOf(
        DrillDescriptor(
            kind = DrillKind.FLASH_ANZAN,
            title = "Флеш-анзан",
            description = "Числа вспыхивают одно за другим — удержи сумму в уме и назови результат.",
            minDifficulty = Difficulty.MIN,
            maxDifficulty = Difficulty.MAX,
            recommendedTaskCount = 10,
        ),
        DrillDescriptor(
            kind = DrillKind.ADD_SUB_CHAIN,
            title = "Цепочка «плюс-минус»",
            description = "Сложение и вычитание по правилам соробана: промежуточный результат не уходит в минус.",
            minDifficulty = Difficulty.MIN,
            maxDifficulty = Difficulty.MAX,
            recommendedTaskCount = 10,
        ),
        DrillDescriptor(
            kind = DrillKind.MULTIPLICATION,
            title = "Умножение",
            description = "Умножение многозначного числа на короткий множитель.",
            minDifficulty = Difficulty.MIN,
            maxDifficulty = Difficulty.MAX,
            recommendedTaskCount = 12,
        ),
        DrillDescriptor(
            kind = DrillKind.DIVISION,
            title = "Деление",
            description = "Деление нацело: задание строится от ответа, остатка никогда не будет.",
            minDifficulty = Difficulty.MIN,
            maxDifficulty = Difficulty.MAX,
            recommendedTaskCount = 12,
        ),
        DrillDescriptor(
            kind = DrillKind.MIXED,
            title = "Смешанный режим",
            description = "Все четыре действия вперемешку — проверка боем перед соревнованием.",
            minDifficulty = Difficulty.MIN,
            maxDifficulty = Difficulty.MAX,
            recommendedTaskCount = 15,
        ),
    )
}
