rootProject.name = "soroban-quest-server"

// Модульный монолит: каждый бизнес-модуль — отдельный Gradle-проект.
// Границы модулей проверяются компилятором: всё, что помечено `internal`,
// физически недоступно из других модулей.
include(
    ":shared",
    ":modules:identity",
    ":modules:training",
    ":modules:progression",
    ":modules:leaderboard",
    ":api:rest",
    ":bootstrap",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
