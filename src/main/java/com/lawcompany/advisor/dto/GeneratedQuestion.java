package com.lawcompany.advisor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 어드바이징을 위해 사용자에게 추가로 물을 질문 1건. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneratedQuestion(
        String field,      // 질문이 채우는 항목 키 (예: data_mapping)
        String question,   // 사용자에게 보여줄 질문
        String why         // 왜 필요한지
) {}
