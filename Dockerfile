# 1단계: 빌드 (gradlew가 알아서 정확한 Gradle 버전을 받아오므로 베이스 이미지의 Gradle 버전은 안 맞아도 된다)
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

# 2단계: 실행 (JDK가 아니라 JRE만 있으면 되므로 이미지 용량을 줄인다)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
