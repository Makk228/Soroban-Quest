package com.sorobanquest.leaderboard.internal.config

import com.sorobanquest.identity.api.UserDirectory
import com.sorobanquest.leaderboard.api.LeaderboardService
import com.sorobanquest.leaderboard.internal.application.LeaderboardServiceImpl
import com.sorobanquest.leaderboard.internal.application.XpAwardedHandler
import com.sorobanquest.leaderboard.internal.persistence.JdbcLeaderboardRepository
import com.sorobanquest.progression.api.XpAwarded
import com.sorobanquest.shared.event.DomainEventHandler
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import java.time.ZoneId

/** Настройки рейтинга (префикс `soroban.leaderboard`). */
@ConfigurationProperties(prefix = "soroban.leaderboard")
data class LeaderboardProperties(
    /** Часовой пояс, по которому определяется граница недели. */
    val timeZone: String = "UTC",
) {
    fun zoneId(): ZoneId = ZoneId.of(timeZone)
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LeaderboardProperties::class)
class LeaderboardModuleConfiguration {

    @Bean
    fun leaderboardService(
        jdbc: NamedParameterJdbcTemplate,
        userDirectory: UserDirectory,
        clock: Clock,
        properties: LeaderboardProperties,
    ): LeaderboardService = LeaderboardServiceImpl(
        repository = JdbcLeaderboardRepository(jdbc),
        users = userDirectory,
        clock = clock,
        zoneId = properties.zoneId(),
    )

    @Bean
    fun xpAwardedLeaderboardHandler(
        jdbc: NamedParameterJdbcTemplate,
        properties: LeaderboardProperties,
    ): DomainEventHandler<XpAwarded> = XpAwardedHandler(
        repository = JdbcLeaderboardRepository(jdbc),
        zoneId = properties.zoneId(),
    )
}
