package com.lawcompany.advisor.service;

import com.lawcompany.advisor.domain.WorkLogEventRow;
import com.lawcompany.advisor.dto.EventDto;
import com.lawcompany.advisor.dto.IngestResponse;
import com.lawcompany.advisor.exception.IngestValidationException;
import com.lawcompany.advisor.repository.WorkLogEventRepository;
import com.lawcompany.advisor.repository.WorkSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IngestService {

    private final WorkSessionRepository sessionRepository;
    private final WorkLogEventRepository eventRepository;

    public IngestService(WorkSessionRepository sessionRepository, WorkLogEventRepository eventRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public IngestResponse ingest(UUID userId, EventDto dto) {
        List<String> errors = validateByType(dto);
        if (!errors.isEmpty()) {
            throw new IngestValidationException(errors);
        }

        Instant eventTime = Instant.ofEpochMilli(dto.ts());

        // 세션 lazy 생성 (FK 충족). 이미 있으면 무시 → user_id/started_at 은 최초값 유지
        sessionRepository.upsertIfAbsent(dto.sessionId(), userId, eventTime);

        eventRepository.insert(new WorkLogEventRow(
                dto.eventId(), dto.sessionId(), dto.type(), eventTime, dto.tabId(),
                dto.domain(), dto.safeUrl(), dto.title(), dto.durationMs(),
                dto.tag(), dto.role(), dto.label()
        ));

        return new IngestResponse(dto.eventId());
    }

    /** 타입별 필수 필드 검증. */
    private List<String> validateByType(EventDto dto) {
        List<String> errors = new ArrayList<>();
        switch (dto.type()) {
            case "navigation_completed", "tab_activated" -> { /* title 선택 */ }
            case "dwell_time" -> {
                if (dto.durationMs() == null) errors.add("durationMs: dwell_time 이벤트는 durationMs 필수");
            }
            case "click" -> {
                if (dto.tag() == null || dto.tag().isBlank()) errors.add("tag: click 이벤트는 tag 필수");
            }
            default -> errors.add("type: 알 수 없는 이벤트 타입 '" + dto.type() + "'");
        }
        return errors;
    }
}
