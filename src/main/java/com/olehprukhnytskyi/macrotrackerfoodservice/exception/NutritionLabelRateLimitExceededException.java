package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import lombok.Getter;

@Getter
public class NutritionLabelRateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public NutritionLabelRateLimitExceededException(long retryAfterSeconds) {
        super("Daily nutrition label scan limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
