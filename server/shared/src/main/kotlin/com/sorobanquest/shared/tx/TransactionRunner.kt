package com.sorobanquest.shared.tx

/**
 * Порт управления транзакциями. Прикладной слой модулей не зависит от Spring:
 * реализация (TransactionTemplate) подключается в bootstrap, в тестах легко
 * подменяется прямым вызовом блока.
 */
interface TransactionRunner {
    /** Выполнить блок в транзакции (присоединиться к текущей либо начать новую). */
    fun <T> required(block: () -> T): T
}
