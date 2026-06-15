package com.lawcompany.advisor.controller;

import com.lawcompany.advisor.dto.EventDto;
import com.lawcompany.advisor.dto.IngestResponse;
import com.lawcompany.advisor.service.IngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 계약 A — Ingest API. 클라이언트는 단일 이벤트를 전송한다. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ingest", description = "이벤트 적재 (계약 A)")
public class IngestController {

    /** 프론트와 합의된 사용자 식별 쿠키명. */
    private static final String USER_ID_COOKIE = "user_id";

    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "단일 WorkLogEvent 적재",
            description = "eventId/sessionId 는 FE 생성. 세션은 첫 이벤트에서 자동 생성(lazy)된다. "
                    + "타입: navigation_completed | tab_activated | dwell_time | click."
    )
    public IngestResponse ingest(
            @Parameter(in = ParameterIn.COOKIE, name = USER_ID_COOKIE,
                    description = "사용자 식별 쿠키 값(UUID). 없거나 형식이 아니면 null로 세션에 기록")
            @CookieValue(value = USER_ID_COOKIE, required = false) String userIdCookie,
            @Valid @RequestBody EventDto event
    ) {
        return ingestService.ingest(parseUserId(userIdCookie), event);
    }

    /** 쿠키 값을 UUID로 파싱. 없거나 형식이 아니면 null(멀티유저 전 MVP 허용). */
    private static UUID parseUserId(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(cookieValue.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
