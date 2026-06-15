package com.lawcompany.advisor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI advisorOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("AI 업무생산성 어드바이저 — Backend API")
                .description("계약 A: 단일 WorkLogEvent 적재 / 계약 B: 분석·조회. Swagger UI: /swagger-ui")
                .version("v1"));
    }
}
