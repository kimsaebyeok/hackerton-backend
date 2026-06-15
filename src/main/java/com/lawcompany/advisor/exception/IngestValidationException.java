package com.lawcompany.advisor.exception;

import java.util.List;

/** 배치 내 이벤트 검증 실패. 어떤 이벤트의 무엇이 틀렸는지 목록으로 전달. */
public class IngestValidationException extends RuntimeException {

    private final List<String> errors;

    public IngestValidationException(List<String> errors) {
        super("ingest validation failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
