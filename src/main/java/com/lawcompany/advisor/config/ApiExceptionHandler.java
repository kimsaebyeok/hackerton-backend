package com.lawcompany.advisor.config;

import com.lawcompany.advisor.exception.IngestValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** 배치 내 개별 이벤트 검증 실패. */
    @ExceptionHandler(IngestValidationException.class)
    public ResponseEntity<Map<String, Object>> handleIngestValidation(IngestValidationException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_failed",
                "details", e.getErrors()
        ));
    }

    /** 본문 자체(@Valid) 검증 실패 — 예: events 비어 있음. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBodyValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_failed",
                "details", details
        ));
    }

    /** 필수 헤더(X-User-Id) 누락. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "missing_header",
                "details", List.of(e.getHeaderName() + " 헤더가 필요합니다")
        ));
    }
}
