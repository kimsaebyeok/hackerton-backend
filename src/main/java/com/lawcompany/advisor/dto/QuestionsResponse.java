package com.lawcompany.advisor.dto;

import java.util.List;
import java.util.UUID;

/** API #1 응답: 아젠다에 대해 생성된 추가 질문 목록. */
public record QuestionsResponse(
        UUID agendaId,
        List<GeneratedQuestion> questions
) {}
