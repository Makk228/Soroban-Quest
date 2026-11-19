package com.sorobanquest.bootstrap

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.PostgreSQLContainer

private object TestDatabase {
    val postgres: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("soroban")
            .withUsername("soroban")
            .withPassword("soroban")
            .also { it.start() }
}

class PostgresTestInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val postgres = TestDatabase.postgres
        TestPropertyValues
            .of(
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
            ).applyTo(applicationContext.environment)
    }
}
