# --- Этап сборки: компилируем fat-jar внутри образа, чтобы сборка была воспроизводимой ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Сначала только файлы сборки — слой с зависимостями переиспользуется между сборками
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon --version > /dev/null

COPY shared shared
COPY modules modules
COPY api api
COPY bootstrap bootstrap

RUN ./gradlew --no-daemon :bootstrap:bootJar -x test

# --- Этап запуска: только JRE и собранный jar ---
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S soroban && adduser -S soroban -G soroban
WORKDIR /app
COPY --from=build /workspace/bootstrap/build/libs/soroban-quest-server.jar app.jar
USER soroban
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
