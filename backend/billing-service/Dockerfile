# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (security)
RUN addgroup -S taasim && adduser -S taasim -G taasim
USER taasim

COPY billing-service/target/billing-service-*.jar app.jar

EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
