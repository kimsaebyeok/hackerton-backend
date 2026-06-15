package com.lawcompany.advisor.domain;

import java.time.Instant;

/** 분석용으로 조회한 work_log_event 한 행. */
public record EventRecord(
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
