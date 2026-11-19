// leaderboard — недельный и общий рейтинг.
// Данные накапливаются по событию XpAwarded из модуля прогресса, имена
// подтягиваются через публичный контракт UserDirectory модуля identity.
dependencies {
    api(project(":shared"))
    api(project(":modules:progression"))
    implementation(project(":modules:identity"))

    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation("org.springframework.boot:spring-boot")
    implementation(libs.kotlin.logging)

    testImplementation(libs.mockk)
}
