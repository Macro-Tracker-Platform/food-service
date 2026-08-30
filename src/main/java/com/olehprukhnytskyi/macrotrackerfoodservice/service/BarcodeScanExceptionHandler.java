package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.dto.ProblemDetails;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import java.time.Duration;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class BarcodeScanExceptionHandler {
    public static final String RATE_LIMIT_HEADER = "X-Barcode-Scan-Rate-Limit";
    public static final String LIMIT_HEADER = "X-Barcode-Scan-Limit";
    public static final String REMAINING_HEADER = "X-Barcode-Scan-Remaining";
    public static final String RESET_AT_HEADER = "X-Barcode-Scan-Reset-At";

    @ExceptionHandler(BarcodeScanService.LimitExceededException.class)
    public ResponseEntity<ProblemDetails> handleLimit(
            BarcodeScanService.LimitExceededException exception) {
        BarcodeScanQuotaDto quota = exception.getQuota();
        HttpHeaders headers = new HttpHeaders();
        headers.add(RATE_LIMIT_HEADER, "daily-limit");
        if (quota.getLimit() != null) {
            headers.add(LIMIT_HEADER, String.valueOf(quota.getLimit()));
        }
        headers.add(REMAINING_HEADER, "0");
        if (quota.getResetAt() != null) {
            headers.add(RESET_AT_HEADER, quota.getResetAt().toString());
            long retryAfter = Math.max(1,
                    Duration.between(Instant.now(), quota.getResetAt()).toSeconds());
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        ProblemDetails body = ProblemDetails.builder()
                .title("Barcode scan limit exceeded")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .code("BARCODE_DAILY_LIMIT")
                .detail(exception.getMessage())
                .traceId("N/A")
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(body);
    }
}
