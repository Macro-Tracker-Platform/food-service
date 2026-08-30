package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.dto.ProblemDetails;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BarcodeScanExceptionHandlerTest {
    @Test
    void limitResponseUsesStableCodeAndQuotaHeaders() {
        BarcodeScanQuotaDto quota = new BarcodeScanQuotaDto();
        quota.setAllowed(false);
        quota.setLimit(5);
        quota.setRemaining(0);
        quota.setResetAt(Instant.now().plusSeconds(3600));

        ResponseEntity<ProblemDetails> response = new BarcodeScanExceptionHandler()
                .handleLimit(new BarcodeScanService.LimitExceededException(quota));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(
                BarcodeScanExceptionHandler.RATE_LIMIT_HEADER)).isEqualTo("daily-limit");
        assertThat(response.getHeaders().getFirst(
                BarcodeScanExceptionHandler.LIMIT_HEADER)).isEqualTo("5");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("BARCODE_DAILY_LIMIT");
    }
}
