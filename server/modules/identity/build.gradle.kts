// identity — регистрация, вход, выдача и ротация токенов.
// Наружу отдаёт AuthenticationService, UserDirectory и AccessTokenVerifier;
// как именно устроены токены (JWT) и хеши паролей (BCrypt) — деталь реализации.
dependencies {
    api(project(":shared"))

    implementation(libs.spring.context)
    implementation(libs.spring.jdbc)
    implementation("org.springframework.boot:spring-boot")
    implementation(libs.spring.security.crypto)
    implementation(libs.jjwt.api)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    testImplementation(libs.mockk)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.jackson)
}
