package com.reservas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Reservations API").version("v1")
                        .description("Book shared resources. Call /api/auth/login first: it sets httpOnly cookies "
                                + "that the following requests send automatically."));
    }
}
