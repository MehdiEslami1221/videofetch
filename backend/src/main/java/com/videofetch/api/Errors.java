package com.videofetch.api;

import com.videofetch.core.ApiProblem;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
    private Map<String, Object> data(String code, String message) {
        return Map.of("code", code, "message", message, "timestamp", Instant.now().toString());
    }
    @ExceptionHandler(ApiProblem.class)
    public ResponseEntity<Map<String, Object>> known(ApiProblem e) {
        return ResponseEntity.status(e.status()).body(data(e.code(), e.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(data("INVALID_REQUEST", "Invalid request fields or JSON."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(data("TOO_LARGE", "Uploaded file exceeded the configured size limit."));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        return ResponseEntity.status(500).body(data("INTERNAL_ERROR", "Unexpected server error. Check server logs."));
    }
}
