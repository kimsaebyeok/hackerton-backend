package com.lawcompany.advisor.controller;

import com.lawcompany.advisor.dto.SessionSummary;
import com.lawcompany.advisor.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 수집 세션 생명주기. */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Session", description = "수집 세션 (생성은 ingest lazy, 종료/현황은 여기)")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/{sessionId}/close")
    @Operation(summary = "수집 종료",
            description = "집계(ended_at·active_duration_ms·event_count) 확정 + status=closed. "
                    + "분석/LLM 은 advisor.analysis.enabled=false 면 실행하지 않음.")
    public SessionSummary close(@PathVariable UUID sessionId) {
        return sessionService.close(sessionId);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "세션 현황", description = "실시간 집계(event_count·active_duration_ms 등). 수집 검증용.")
    public SessionSummary get(@PathVariable UUID sessionId) {
        return sessionService.get(sessionId);
    }
}
