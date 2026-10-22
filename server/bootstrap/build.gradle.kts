import org.springframework.boot.gradle.tasks.bundling.BootJar

// bootstrap — композиционный корень: собирает модули в одно приложение,
// поставляет инфраструктурные реализации портов (транзакции, шина событий, часы)
// и владеет конфигурацией запуска. Бизнес-логики здесь нет.
plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:training"))
    implementation(project(":modules:progression"))
    implementation(project(":modules:leaderboard"))
    implementation(project(":api:rest"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    implementation(libs.flyway.core)

    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.mockk)
}

tasks.withType<BootJar> {
    archiveFileName.set("soroban-quest-server.jar")
}

tasks.withType<Test> {
    // Testcontainers поднимает PostgreSQL в Docker — нужен запущенный демон Docker.
    systemProperty("spring.profiles.active", "test")
}
