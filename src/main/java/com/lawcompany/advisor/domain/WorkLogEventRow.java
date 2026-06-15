package com.lawcompany.advisor.domain;

import java.time.Instant;
import java.util.UUID;

/** work_log_event 적재용 한 행. 타입별 null 컬럼 허용. */
public record WorkLogEventRow(
        UUID eventId,
        UUID sessionId,
        String type,
        Instant eventTime,
        Integer tabId,
        String domain,
        String safeUrl,
        String title,
        Long durationMs,
        String tag,
        String role,
        String label
) {}
