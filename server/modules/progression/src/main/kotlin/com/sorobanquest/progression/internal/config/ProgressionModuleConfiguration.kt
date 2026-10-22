package com.sorobanquest.progression.internal.config

import com.sorobanquest.identity.api.UserRegistered
import com.sorobanquest.progression.api.ProgressionService
import com.sorobanquest.progression.internal.application.ProgressionServiceImpl
import com.sorobanquest.progression.internal.application.TrainingSessionCompletedHandler
import com.sorobanquest.progression.internal.application.UserRegisteredHandler
import com.sorobanquest.progression.internal.persistence.JdbcAchievementRepository
import com.sorobanquest.progression.internal.persistence.JdbcDailyProgressRepository
import com.sorobanquest.progression.internal.persistence.JdbcLearnerProfileRepository
import com.sorobanquest.progression.internal.persistence.JdbcSkillMasteryRepository
import com.sorobanquest.shared.event.DomainEventHandler
import com.sorobanquest.shared.event.DomainEventPublisher
import com.sorobanquest.shared.tx.TransactionRunner
import com.sorobanquest.training.api.TrainingSessionCompleted
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.ZoneId

/**
 * Настройки модуля прогресса (префикс `soroban.progression`).
 * Часовой пояс определяет, когда «начинается новый день» для серий и дневной цели.
 */
@ConfigurationProperties(prefix = "soroban.progression")
data class ProgressionProperties(
    val timeZone: String = "UTC",
) {
    fun zoneId(): ZoneId = ZoneId.of(timeZone)
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProgressionProperties::class)
class ProgressionModuleConfiguration {

    @Bean
    fun progressionService(
        jdbc: NamedParameterJdbcTemplate,
        transactionRunner: TransactionRunner,
        clock: Clock,
        properties: ProgressionProperties,
    ): ProgressionService = ProgressionServiceImpl(
        profiles = JdbcLearnerProfileRepository(jdbc),
        achievements = JdbcAchievementRepository(jdbc),
        skills = JdbcSkillMasteryRepository(jdbc),
        dailyProgress = JdbcDailyProgressRepository(jdbc),
        transactions = transactionRunner,
        clock = clock,
        zoneId = properties.zoneId(),
    )

    @Bean
    fun userRegisteredProgressionHandler(
        jdbc: NamedParameterJdbcTemplate,
    ): DomainEventHandler<UserRegistered> = UserRegisteredHandler(JdbcLearnerProfileRepository(jdbc))

    @Bean
    fun trainingSessionCompletedProgressionHandler(
        jdbc: NamedParameterJdbcTemplate,
        transactionRunner: TransactionRunner,
        eventPublisher: DomainEventPublisher,
        clock: Clock,
        properties: ProgressionProperties,
    ): DomainEventHandler<TrainingSessionCompleted> = TrainingSessionCompletedHandler(
        profiles = JdbcLearnerProfileRepository(jdbc),
        skills = JdbcSkillMasteryRepository(jdbc),
        achievements = JdbcAchievementRepository(jdbc),
        dailyProgress = JdbcDailyProgressRepository(jdbc),
        transactions = transactionRunner,
        events = eventPublisher,
        clock = clock,
        zoneId = properties.zoneId(),
    )
}
