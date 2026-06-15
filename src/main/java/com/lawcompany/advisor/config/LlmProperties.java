package com.lawcompany.advisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 LLM 연동 설정 (advisor.llm.*).
 * API key는 환경변수(ADVISOR_LLM_API_KEY)로 주입하며 커밋하지 않는다.
 */
@ConfigurationProperties(prefix = "advisor.llm")
public record LlmProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        Double temperature,
        Integer timeoutMs
) {
    public LlmProperties {
        if (provider == null || provider.isBlank()) provider = "openai";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.openai.com";
        if (model == null || model.isBlank()) model = "gpt-4o-mini";
        if (temperature == null) temperature = 0.2;
        if (timeoutMs == null) timeoutMs = 30000;
    }
}
