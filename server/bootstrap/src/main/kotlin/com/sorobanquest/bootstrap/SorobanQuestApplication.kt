package com.sorobanquest.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Точка входа приложения Soroban Quest.
 *
 * Сканируется весь пакет com.sorobanquest: каждый модуль публикует свои бины
 * через собственный класс-конфигурацию, поэтому composition root остаётся тонким.
 */
@SpringBootApplication(scanBasePackages = ["com.sorobanquest"])
class SorobanQuestApplication

fun main(args: Array<String>) {
    runApplication<SorobanQuestApplication>(*args)
}
