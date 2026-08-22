package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import com.olehprukhnytskyi.dto.ProblemDetails;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class NutritionLabelScanExceptionHandler {
    public static final String RATE_LIMIT_HEADER = "X-Nutrition-Label-Rate-Limit";

    @ExceptionHandler(NutritionLabelRateLimitExceededException.class)
    public ResponseEntity<ProblemDetails> handleRateLimit(
            NutritionLabelRateLimitExceededException exception) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(RATE_LIMIT_HEADER, exception.getScope() + "-limit");
        headers.add("X-Nutrition-Label-Limit", String.valueOf(exception.getLimit()));
        headers.add("X-Nutrition-Label-Remaining", "0");
        headers.add("X-Nutrition-Label-Reset-At", exception.getResetAt().toString());
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()));
        ProblemDetails body = ProblemDetails.builder()
                .title("Nutrition label scan limit exceeded")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .code("NUTRITION_LABEL_"
                        + exception.getScope().toUpperCase().replace('-', '_')
                        + "_LIMIT")
                .detail(exception.getMessage())
                .traceId("N/A")
                .build();
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(body);
    }

    @ExceptionHandler(GeminiTemporaryUnavailableException.class)
    public ResponseEntity<ProblemDetails> handleGeminiUnavailable(
            GeminiTemporaryUnavailableException exception) {
        ProblemDetails body = ProblemDetails.builder()
                .title(CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE.getTitle())
                .status(CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE.getStatus())
                .code(CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE.getCode())
                .detail(exception.getMessage())
                .traceId("N/A")
                .invalidParams(List.of())
                .build();
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER,
                        String.valueOf(exception.getRetryAfterSeconds()))
                .body(body);
    }
}
