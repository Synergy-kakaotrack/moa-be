package com.moa.moa_backend.global.llm.gemini;

public class GeminiClientException extends RuntimeException{
    public GeminiClientException(String message) {
        super(message);
    }
    public GeminiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
