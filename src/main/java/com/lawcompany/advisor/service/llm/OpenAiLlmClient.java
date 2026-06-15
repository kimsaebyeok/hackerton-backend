package com.lawcompany.advisor.service.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawcompany.advisor.config.LlmProperties;
import com.lawcompany.advisor.exception.LlmException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenAI Chat Completions 기반 LLM 클라이언트 (API key 방식).
 * 외부 SDK 없이 Spring RestClient로 직접 통신한다.
 */
@Component
@ConditionalOnProperty(name = "advisor.llm.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiLlmClient implements LlmClient {

    private final LlmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiLlmClient(LlmProperties props, ObjectMapper objectMapper) {
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
        return call(systemPrompt, userPrompt, false);
    }

    @Override
    public <T> T completeAsJson(String systemPrompt, String userPrompt, Class<T> responseType) {
        String content = call(systemPrompt, userPrompt, true);
        try {
            return objectMapper.readValue(content, responseType);
        } catch (JsonProcessingException e) {
            throw new LlmException("LLM JSON 응답 파싱 실패: " + content, e);
        }
    }

    private String call(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new LlmException("LLM API key가 설정되지 않았습니다 (advisor.llm.api-key / ADVISOR_LLM_API_KEY)");
        }

        var request = new ChatRequest(
                props.model(),
                List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
                props.temperature(),
                jsonMode ? new ResponseFormat("json_object") : null
        );

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new LlmException("LLM 호출 실패 (" + res.getStatusCode() + "): " + body);
                    })
                    .body(ChatResponse.class);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM 호출 중 오류: " + e.getMessage(), e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmException("LLM 응답에 choices가 없습니다");
        }
        return response.choices().get(0).message().content();
    }

    // ── OpenAI Chat Completions 와이어 포맷 ─────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(
            String model,
            List<Message> messages,
            Double temperature,
            @JsonProperty("response_format") ResponseFormat responseFormat
    ) {}

    record Message(String role, String content) {}

    record ResponseFormat(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {}
}
