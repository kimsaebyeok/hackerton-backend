package com.lawcompany.advisor.service;

import com.lawcompany.advisor.dto.SessionSummary;
import com.lawcompany.advisor.repository.WorkSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * 수집 세션. 분석(지표/아젠다)은 별도 애플리케이션이 담당하므로 여기선 원시 세션만 관리한다.
 */
@Service
public class SessionService {

    private final WorkSessionRepository sessionRepository;

    public SessionService(WorkSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** 수집 종료: 이벤트로부터 집계 확정 + status=closed. */
    public SessionSummary close(UUID sessionId) {
        return sessionRepository.close(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));
    }

    public SessionSummary get(UUID sessionId) {
        return sessionRepository.findSummary(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found: " + sessionId));
    }
}
