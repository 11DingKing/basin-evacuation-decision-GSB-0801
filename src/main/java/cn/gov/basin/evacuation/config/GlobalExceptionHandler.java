package cn.gov.basin.evacuation.config;

import cn.gov.basin.evacuation.decision.AdviceNotFoundException;
import cn.gov.basin.evacuation.override.DuplicateRequestIdException;
import cn.gov.basin.evacuation.override.OverrideNotFoundException;
import cn.gov.basin.evacuation.region.RegionNotFoundException;
import cn.gov.basin.evacuation.snapshot.SnapshotAlreadyExistsException;
import cn.gov.basin.evacuation.snapshot.SnapshotNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            RegionNotFoundException.class,
            SnapshotNotFoundException.class,
            AdviceNotFoundException.class,
            OverrideNotFoundException.class,
            NoSuchElementException.class
    })
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            SnapshotAlreadyExistsException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DuplicateRequestIdException.class)
    public ResponseEntity<Map<String, Object>> duplicateRequest(DuplicateRequestIdException ex) {
        Map<String, Object> body = baseBody(HttpStatus.CONFLICT, ex.getMessage());
        body.put("existingOverrideId", ex.getExistingOverrideId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "validation failed");
        body.put("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message",
                        e.getDefaultMessage() == null ? "" : e.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
