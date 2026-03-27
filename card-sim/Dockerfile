FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY common/ common/
COPY card-sim/ card-sim/
RUN chmod +x gradlew && ./gradlew :card-sim:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
COPY --from=builder /app/card-sim/build/libs/*.jar /app/app.jar
EXPOSE 8081 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
