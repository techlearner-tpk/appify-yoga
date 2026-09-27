FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/wellness-0.1.0.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD wget -q -O - http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
