plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

group = "com.sorobanquest"
version = "0.1.0"

val kotlinVersion = libs.versions.kotlin.get()

/*
 * Общие соглашения для всех модулей:
 *  - Kotlin/JVM 21,
 *  - BOM Spring Boot, чтобы версии транзитивных библиотек были согласованы,
 *  - JUnit 5 + AssertJ как общий тестовый стек.
 * Конкретные зависимости каждый модуль объявляет сам — это и есть его контракт
 * с внешним миром (см. build.gradle.kts внутри модулей).
 */
subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "io.spring.dependency-management")

    group = rootProject.group
    version = rootProject.version

    repositories { mavenCentral() }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().imports {
        // kotlin.version в BOM Spring Boot отстаёт от используемого компилятора,
        // поэтому переопределяем свойство — иначе Gradle «понижает» kotlin-stdlib
        // и внутренние артефакты Kotlin-плагина.
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES) {
            bomProperty("kotlin.version", kotlinVersion)
        }
    }

    extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter-api")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        // kotlin-logging пишет через SLF4J: в модульных тестах подключаем реализацию,
        // иначе падает инициализация логгеров (в приложении её приносит Spring Boot)
        "testRuntimeOnly"("ch.qos.logback:logback-classic")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
