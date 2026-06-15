package com.lawcompany.advisor.service;

import com.lawcompany.advisor.dto.SessionSummary;
import com.lawcompany.advisor.repository.WorkSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final WorkSessionRepository sessionRepository;
    private final AnalysisService analysisService;

    /** true 면 종료 시 분석+LLM 실행. false 면 수집만(분석 건너뜀). */
    private final boolean analysisEnabled;

    public SessionService(WorkSessionRepository sessionRepository,
                          AnalysisService analysisService,
                          @Value("${advisor.analysis.enabled:false}") boolean analysisEnabled) {
        this.sessionRepository = sessionRepository;
        this.analysisService = analysisService;
        this.analysisEnabled = analysisEnabled;
    }

    /**
     * 수집 종료: 집계 확정 + status=closed. 분석이 켜져 있으면 이어서 LLM 분석 실행(→ analyzed).
     * 분석 실패(키 미설정/LLM 오류 등)는 close 를 깨지 않고 경고만 남긴다(세션은 closed 유지).
     */
    public SessionSummary close(UUID sessionId) {
        SessionSummary summary = sessionRepository.close(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));

        if (!analysisEnabled) {
            log.info("analysis disabled — 분석/LLM 건너뜀 (수집만), session={}", sessionId);
            return summary;
        }

        try {
            analysisService.analyze(sessionId);
            return get(sessionId);   // analyzed 상태로 갱신된 요약 반환
        } catch (Exception e) {
            log.warn("분석 실패 — 세션은 closed 유지, session={}, err={}", sessionId, e.toString());
            return summary;
        }
    }

    public SessionSummary get(UUID sessionId) {
        return sessionRepository.findSummary(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));
    }
}
