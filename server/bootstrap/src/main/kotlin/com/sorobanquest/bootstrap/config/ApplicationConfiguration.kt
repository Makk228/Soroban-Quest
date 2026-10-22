package com.sorobanquest.bootstrap.config

import com.sorobanquest.bootstrap.events.SynchronousDomainEventBus
import com.sorobanquest.bootstrap.tx.SpringTransactionRunner
import com.sorobanquest.shared.event.DomainEventHandler
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.id.IdGenerator
import com.sorobanquest.shared.id.RandomIdGenerator
import com.sorobanquest.shared.tx.TransactionRunner
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock

/**
 * Инфраструктурные реализации портов из модуля shared.
 * Это единственное место, где модули «встречаются» со Spring-инфраструктурой.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    /** Часы как бин: в тестах подменяются на фиксированные. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun idGenerator(): IdGenerator = RandomIdGenerator

    @Bean
    fun transactionRunner(transactionManager: PlatformTransactionManager): TransactionRunner =
        SpringTransactionRunner(transactionManager)

    @Bean
    fun domainEventPublisher(handlers: ObjectProvider<DomainEventHandler<*>>): DomainEventPublisher =
        SynchronousDomainEventBus(handlers)

    @Bean
    fun namedParameterJdbcTemplate(dataSource: javax.sql.DataSource) = NamedParameterJdbcTemplate(dataSource)
}
