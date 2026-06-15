package com.lawcompany.advisor.dto;

import java.util.UUID;

/** efficiency_agenda 읽기 모델 (어드바이징 프롬프트 구성용). evidence/agentSpecDraft 는 원본 JSON 문자열. */
public record EfficiencyAgendaView(
        UUID agendaId,
        UUID reportId,
        String archetype,
        String title,
        String oneLiner,
        Double automationScore,
        Integer estSavingMinDay,
        Double priorityScore,
        String evidenceJson,
        String agentSpecDraftJson
) {}
