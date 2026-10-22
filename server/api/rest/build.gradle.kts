// api:rest — транспортный слой. Знает про HTTP, JSON и Spring Security,
// но ничего не знает про SQL и внутренности модулей: он обращается только
// к публичным контрактам (AuthenticationService, TrainingService, ...).
// Чтобы добавить gRPC или GraphQL, достаточно написать второй такой модуль.
dependencies {
    implementation(project(":shared"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:training"))
    implementation(project(":modules:progression"))
    implementation(project(":modules:leaderboard"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.springdoc.openapi.webmvc)
    implementation(libs.kotlin.logging)
}
