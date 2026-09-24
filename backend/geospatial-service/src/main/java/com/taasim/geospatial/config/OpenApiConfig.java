package com.taasim.geospatial.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TaaSim Geospatial Service API",
                version = "1.0.0",
                description = "Internal service managing Redis Geospatial driver indexes and proximity searches.",
                contact = @Contact(name = "TaaSim Engineering Team")
        ),
        servers = {
                @Server(url = "http://localhost:8084", description = "Direct Service URL"),
                @Server(url = "http://localhost:8080", description = "API Gateway URL")
        }
)
public class OpenApiConfig {
}
