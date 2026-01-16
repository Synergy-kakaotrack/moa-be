# ---- Build stage ----
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app

# 캐시 효율을 위해 Gradle 파일 먼저 복사
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle
RUN chmod +x /app/gradlew

# 소스 복사 후 빌드
COPY . /app
RUN ./gradlew clean bootJar -x test




# ---- Run stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Spring Boot jar 복사
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
