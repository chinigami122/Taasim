# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy parent + all module poms
COPY pom.xml .
COPY common/pom.xml common/
COPY driver-service/pom.xml driver-service/
COPY auth-service/pom.xml auth-service/
COPY trip-service/pom.xml trip-service/
COPY matching-service/pom.xml matching-service/

# Copy sources for common and auth-service
COPY common/src common/src
COPY auth-service/src auth-service/src

# Build package
RUN mvn -pl auth-service -am clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (security)
RUN addgroup -S taasim && adduser -S taasim -G taasim
USER taasim

COPY --from=build /app/auth-service/target/auth-service-*.jar app.jar

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
