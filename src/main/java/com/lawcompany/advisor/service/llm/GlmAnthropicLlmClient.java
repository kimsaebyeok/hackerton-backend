package com.lawcompany.advisor.service.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawcompany.advisor.config.LlmProperties;
import com.lawcompany.advisor.exception.LlmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * z.ai Anthropic 호환 Messages API 클라이언트 (base-url …/api/anthropic).
 * POST /v1/messages, 헤더 x-api-key, 응답 content[].text.
 * HTTP 오류는 RestClient 기본 처리로 예외가 던져지고, 호출부(AnalysisService)가 best-effort 로 잡는다.
 */
@Component
@ConditionalOnProperty(name = "advisor.llm.provider", matchIfMissing = true)
public class GlmAnthropicLlmClient implements LlmClient {

    private static final int MAX_TOKENS = 2048;

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GlmAnthropicLlmClient(LlmProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.timeoutMs());
        factory.setReadTimeout(props.timeoutMs());

        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        var request = new MessageRequest(
                props.model(), MAX_TOKENS, props.temperature(),
                systemPrompt, List.of(new Message("user", userPrompt))
        );
        MessageResponse res = restClient.post()
                .uri("/v1/messages")
                .header("x-api-key", props.apiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MessageResponse.class);
        return res.content().get(0).text();
    }

    @Override
    public <T> T completeAsJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        String text = stripFences(complete(systemPrompt, userPrompt));
        try {
            return objectMapper.readValue(text, responseType);
        } catch (Exception e) {
            throw new LlmException("LLM JSON 응답 파싱 실패: " + text, e);
        }
    }

    /** 모델이 ```json … ``` 펜스로 감싸 줄 때만 벗겨냄. */
    private static String stripFences(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }

    // ── Anthropic Messages 와이어 포맷 ──
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MessageRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            Double temperature,
            String system,
            List<Message> messages
    ) {}

    record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessageResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}
}
