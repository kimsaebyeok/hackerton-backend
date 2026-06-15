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

    /** true 가 되면 종료 시 L1~L4+LLM 분석 실행. 현재는 수집 검증용으로 off. */
    private final boolean analysisEnabled;

    public SessionService(WorkSessionRepository sessionRepository,
                          @Value("${advisor.analysis.enabled:false}") boolean analysisEnabled) {
        this.sessionRepository = sessionRepository;
        this.analysisEnabled = analysisEnabled;
    }

    /** 수집 종료: 집계 확정 + status=closed. 분석/LLM 은 게이트로 막혀 있으면 건너뜀. */
    public SessionSummary close(UUID sessionId) {
        SessionSummary summary = sessionRepository.close(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));

        if (analysisEnabled) {
            // TODO(Phase 분석): L1~L3 계산 + L4 LLM 카드 생성 후 status=analyzed
            log.info("analysis enabled — (미구현) 분석 단계는 추후 연결, session={}", sessionId);
        } else {
            log.info("analysis disabled — LLM/분석 건너뜀 (수집만), session={}", sessionId);
        }
        return summary;
    }

    public SessionSummary get(UUID sessionId) {
        return sessionRepository.findSummary(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));
    }
}
