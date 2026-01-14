package com.moa.moa_backend.global.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("Moa Backend API")
                        .version("v1")
                        .description("MOA MVP API 문서 (X=User-Id 기반 사용자 식별)"));

    }

}
