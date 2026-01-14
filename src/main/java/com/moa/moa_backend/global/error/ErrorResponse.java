package com.moa.moa_backend.global.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse (
        String code,
        String message,
        String path,
        Instant timestamp,
        List<FieldError> errors
) {
    public record FieldError(
            String field,
            String reason
    ){}
}
