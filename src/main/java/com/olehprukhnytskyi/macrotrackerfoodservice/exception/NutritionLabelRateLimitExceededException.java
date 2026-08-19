package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import lombok.Getter;

@Getter
public class NutritionLabelRateLimitExceededException extends RuntimeException {
    private final String scope;
    private final long retryAfterSeconds;
    private final int limit;
    private final java.time.Instant resetAt;

    public NutritionLabelRateLimitExceededException(String scope, long retryAfterSeconds,
                                                    int limit, java.time.Instant resetAt) {
        super(scope + " nutrition label scan limit exceeded");
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
        this.resetAt = resetAt;
    }
}
