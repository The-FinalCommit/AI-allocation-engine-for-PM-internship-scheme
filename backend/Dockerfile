# PRAGATI backend — multi-stage build.
# Stage 1: compile the Spring Boot service with Maven (Java 17).
# Stage 2: lean runtime image that also carries the built frontend (single-port demo).

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/pragati-backend-*.jar app.jar
COPY frontend-dist ./frontend-dist
EXPOSE 8080
ENV PRAGATI_STATIC_DIR=/app/frontend-dist \
    PRAGATI_UPLOADS_DIR=/app/data/uploads
ENTRYPOINT ["java", "-Xms256m", "-Xmx900m", "-jar", "/app/app.jar"]
