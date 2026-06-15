package com.lawcompany.advisor.dto;

import jakarta.validation.constraints.NotBlank;

/** 질문-답변 쌍 (API #2 입력). field 는 선택. */
public record QaPair(
        String field,
        @NotBlank String question,
        @NotBlank String answer
) {}
