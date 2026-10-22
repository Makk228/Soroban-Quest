package com.sorobanquest.bootstrap.tx

import com.sorobanquest.shared.tx.TransactionRunner
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/**
 * Реализация порта транзакций поверх Spring. Прикладной слой модулей
 * вызывает `transactions.required { ... }` и ничего не знает про Spring.
 */
class SpringTransactionRunner(
    transactionManager: PlatformTransactionManager,
) : TransactionRunner {

    private val template = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
    }

    override fun <T> required(block: () -> T): T =
        template.execute { block() } ?: error("Транзакционный блок вернул null")
}
