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
COPY gateway-service/pom.xml gateway-service/
COPY geospatial-service/pom.xml geospatial-service/
COPY billing-service/pom.xml billing-service/

# Copy sources for common and geospatial-service
COPY common/src common/src
COPY geospatial-service/src geospatial-service/src

# Build package
RUN mvn -pl geospatial-service -am clean package -Dmaven.test.skip=true -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (security)
RUN addgroup -S taasim && adduser -S taasim -G taasim
USER taasim

COPY --from=build /app/geospatial-service/target/geospatial-service-*.jar app.jar

EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
