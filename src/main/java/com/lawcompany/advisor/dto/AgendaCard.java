package com.lawcompany.advisor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * LLM이 structured output 으로 생성하는 추천 카드(efficiency_agenda 대응).
 * evidence / agentSpecDraft 는 중첩 JSON 그대로 받아 JSONB 로 저장.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgendaCard(
        String archetype,          // data_transfer | monitoring | form_entry | research_collect | quick_win_shortcut
        String title,
        String oneLiner,
        Double automationScore,    // 0~1
        Integer estSavingMinDay,
        Double priorityScore,      // 0~1
        JsonNode evidence,
        JsonNode agentSpecDraft
) {}
