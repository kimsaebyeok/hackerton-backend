package com.lawcompany.advisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 계약 A — 단일 WorkLogEvent. eventId/sessionId 는 FE 생성(오프라인 버퍼·중복방지).
 * 타입별 필수 필드는 IngestService 가 검증.
 *   navigation_completed / tab_activated : title?
 *   dwell_time                           : durationMs
 *   click                                : tag, role?, label?
 */
public record EventDto(
        @NotNull UUID eventId,
        @NotNull UUID sessionId,
        @NotBlank @Size(max = 32) String type,
        @NotNull Long ts,                  // epoch ms
        @NotNull Integer tabId,
        @NotBlank @Size(max = 255) String domain,
        @NotBlank String safeUrl,
        String title,                      // nav, tab
        Long durationMs,                   // dwell
        @Size(max = 64) String tag,        // click
        @Size(max = 64) String role,       // click
        String label                       // click
) {}
