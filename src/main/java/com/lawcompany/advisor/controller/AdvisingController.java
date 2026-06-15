package com.lawcompany.advisor.controller;

import com.lawcompany.advisor.dto.AgentDraftView;
import com.lawcompany.advisor.dto.DraftRequest;
import com.lawcompany.advisor.dto.QuestionsResponse;
import com.lawcompany.advisor.service.AdvisingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 어드바이징 — 분석 아젠다 기반 질문 생성 / 에이전트 초안 생성. */
@RestController
@RequestMapping("/api/v1/agendas")
@Tag(name = "Advising", description = "아젠다 기반 추가질문 생성 / agent_draft 생성")
public class AdvisingController {

    private final AdvisingService advisingService;

    public AdvisingController(AdvisingService advisingService) {
        this.advisingService = advisingService;
    }

    @PostMapping("/{agendaId}/questions")
    @Operation(summary = "추가 질문 생성",
            description = "agendaId 의 efficiency_agenda 를 근거로 어드바이징에 필요한 추가 질문을 LLM이 생성해 반환(저장 안 함).")
    public QuestionsResponse questions(@PathVariable UUID agendaId) {
        return advisingService.generateQuestions(agendaId);
    }

    @PostMapping("/{agendaId}/draft")
    @Operation(summary = "에이전트 초안 생성",
            description = "질문/답변 쌍을 받아 LLM으로 명세·컨텍스트·실행프롬프트를 만들어 agent_draft 에 저장.")
    public AgentDraftView draft(@PathVariable UUID agendaId, @Valid @RequestBody DraftRequest request) {
        return advisingService.generateDraft(agendaId, request.answers());
    }
}
