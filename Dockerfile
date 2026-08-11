FROM gradle:8.14.3-jdk21 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN addgroup --system canteiro && adduser --system --ingroup canteiro canteiro
COPY --from=build /workspace/build/libs/*.jar app.jar

USER canteiro
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
