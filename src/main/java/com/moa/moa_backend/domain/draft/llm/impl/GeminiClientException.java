package com.moa.moa_backend.domain.draft.llm.impl;

public class GeminiClientException extends RuntimeException{
    public GeminiClientException(String message) {
        super(message);
    }
    public GeminiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
