package com.lawcompany.advisor.dto;

import java.time.Instant;
import java.util.UUID;

/** 세션 현황/종료 결과 요약. */
public record SessionSummary(
        UUID sessionId,
        UUID userId,
        String status,
        long eventCount,
        long activeDurationMs,
        Instant startedAt,
        Instant endedAt
) {}
