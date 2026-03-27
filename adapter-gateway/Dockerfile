FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY common/ common/
COPY adapter-gateway/ adapter-gateway/
RUN chmod +x gradlew && ./gradlew :adapter-gateway:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
COPY --from=builder /app/adapter-gateway/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
