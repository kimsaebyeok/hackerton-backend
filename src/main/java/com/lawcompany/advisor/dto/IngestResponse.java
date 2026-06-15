package com.lawcompany.advisor.dto;

import java.util.UUID;

/** 적재 결과. 서버가 부여한 이벤트 식별자를 돌려준다. */
public record IngestResponse(UUID eventId) {}
