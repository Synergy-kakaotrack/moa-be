package com.moa.moa_backend.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleException(ApiException e, HttpServletRequest request) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(new ErrorResponse(
                        errorCode.code(),
                        e.getMessage(),
                        request.getRequestURI(),
                        Instant.now(),
                        null
                ));
    }

    //@Valid 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleException(MethodArgumentNotValidException e, HttpServletRequest request) {
        BindingResult bindingResult = e.getBindingResult();

        List<ErrorResponse.FieldError> fieldErrors =
                bindingResult.getFieldErrors().stream()
                        .map(fe -> new ErrorResponse.FieldError(
                                fe.getField(),
                                fe.getDefaultMessage()
                        ))
                        .toList();

        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;

        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(new ErrorResponse(
                        errorCode.code(),
                        errorCode.message(),
                        request.getRequestURI(),
                        Instant.now(),
                        fieldErrors
                ));
    }

    /**
     * 그 외 모든 예외 (예상 못한 오류)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception e,
            HttpServletRequest request
    ) {


        log.error("예기치 못한 에러 발생. path={}", request.getRequestURI(),e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;

        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(new ErrorResponse(
                        errorCode.code(),
                        errorCode.message(),
                        request.getRequestURI(),
                        Instant.now(),
                        null
                ));
    }

}
