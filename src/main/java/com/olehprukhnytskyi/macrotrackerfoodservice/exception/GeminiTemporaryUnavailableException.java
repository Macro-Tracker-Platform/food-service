package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import lombok.Getter;

@Getter
public class GeminiTemporaryUnavailableException extends RuntimeException {
    private final long retryAfterSeconds;

    public GeminiTemporaryUnavailableException(long retryAfterSeconds, Throwable cause) {
        super("Gemini is temporarily unavailable", cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
