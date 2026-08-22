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
        super(messageFor(scope));
        this.scope = scope;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limit = limit;
        this.resetAt = resetAt;
    }

    private static String messageFor(String scope) {
        return switch (scope) {
            case "daily" -> "Daily nutrition label photo submission limit exceeded";
            case "monthly" -> "Monthly successful nutrition label scan quota exceeded";
            case "premium-daily" -> "Daily successful nutrition label scan quota exceeded";
            default -> scope + " nutrition label scan limit exceeded";
        };
    }
}
