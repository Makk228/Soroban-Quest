// progression — игровой слой: опыт, уровни, ежедневные серии, достижения и
// статистика по видам тренировок. Модуль подписан на события training и identity,
// но сам ни на кого не ссылается напрямую (связь односторонняя, через события).
dependencies {
    api(project(":shared"))
    api(project(":modules:training"))   // нужен контракт событий и DrillKind
    implementation(project(":modules:identity"))

    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation("org.springframework.boot:spring-boot")
    implementation(libs.kotlin.logging)

    testImplementation(libs.mockk)
}
