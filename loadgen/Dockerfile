FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY common/ common/
COPY loadgen/ loadgen/
RUN chmod +x gradlew && ./gradlew :loadgen:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
COPY --from=builder /app/loadgen/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
