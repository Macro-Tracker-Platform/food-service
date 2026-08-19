package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionLabelScanResponseDto {
    private NutrimentsLabelResponseDto nutriments;
    private ScanQuota quota;

    public NutritionLabelScanResponseDto(NutrimentsLabelResponseDto nutriments) {
        this.nutriments = nutriments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanQuota {
        private int limit;
        private int remaining;
        private Instant resetAt;
    }
}
