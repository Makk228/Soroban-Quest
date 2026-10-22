// training — ядро продукта: каталог тренировок, детерминированная генерация
// заданий, жизненный цикл тренировочной сессии и подсчёт очков.
// Домен не знает ни про HTTP, ни про Spring: наружу торчит только пакет `api`.
dependencies {
    api(project(":shared"))

    implementation(libs.spring.context)      // только для @Configuration в composition root модуля
    implementation(libs.spring.jdbc)         // адаптер персистентности
    implementation("org.springframework.boot:spring-boot")  // @ConfigurationProperties
    implementation(libs.kotlin.logging)

    testImplementation(libs.mockk)
}
