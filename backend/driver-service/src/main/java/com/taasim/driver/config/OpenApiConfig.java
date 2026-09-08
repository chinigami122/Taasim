package com.taasim.driver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "TaaSim Driver Service API",
        version = "1.0",
        description = "Driver telemetry, trip acceptance, and ride lifecycle endpoints.",
        contact = @Contact(name = "Soufiane", email = "bouzianisoufiane00@gmail.com")
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Gateway"),
        @Server(url = "http://localhost:8083", description = "Direct")
    }
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {}
