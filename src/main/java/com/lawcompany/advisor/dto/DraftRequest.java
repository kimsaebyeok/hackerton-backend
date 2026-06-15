package com.lawcompany.advisor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** API #2 요청 본문: 생성된 질문에 대한 답변 쌍들. (agendaId 는 경로변수) */
public record DraftRequest(
        @NotEmpty @Valid List<QaPair> answers
) {}
