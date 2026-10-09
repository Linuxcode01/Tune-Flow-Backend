package com.tuneflow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Swagger UI at {@code /swagger-ui.html} with a Bearer-token "Authorize" button. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI tuneFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TuneFlow API")
                        .version("v1")
                        .description("""
                                TuneFlow music streaming platform REST API.

                                Base URL: `/api/v1`. All responses use the standard envelope:
                                `{ success, message, data, timestamp }` for success and
                                `{ success, message, errorCode, timestamp }` for errors.

                                Authenticate with `POST /api/v1/auth/login`, then click **Authorize**
                                and paste the returned `accessToken` (without the "Bearer " prefix).
                                """)
                        .contact(new Contact().name("TuneFlow Engineering"))
                        .license(new License().name("All rights reserved")))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Short-lived access token issued by /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
