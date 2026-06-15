package com.lawcompany.advisor.service.llm;

/**
 * 외부 LLM 통신 추상화. provider(OpenAI/Gemini/Anthropic 등)를 구현체로 교체한다.
 * 프롬프트 빌드·결과 적재(Phase 5)는 이 인터페이스의 호출자 책임.
 */
public interface LlmClient {

    /** 자유 텍스트 응답. */
    String complete(String systemPrompt, String userPrompt);

    /**
     * JSON 구조화 응답을 강제하고 결과를 {@code responseType}으로 역직렬화한다.
     * (structured output — generic 제안 방지용. 프롬프트에 JSON 형식 지시 필요)
     */
    <T> T completeAsJson(String systemPrompt, String userPrompt, Class<T> responseType);
}
