package com.lawcompany.advisor.dto;

import java.util.UUID;

/** API #2 응답: 생성·저장된 에이전트 초안. */
public record AgentDraftView(
        UUID draftId,
        UUID agendaId,
        String specMarkdown,
        String contextMarkdown,
        String executionPrompt
) {}
