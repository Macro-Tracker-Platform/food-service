package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiFoodPhotoScanDto {
    @JsonProperty("scan_type")
    private String scanType;

    @JsonProperty("image_quality")
    private String imageQuality;

    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;

        @JsonProperty("estimated_weight_grams")
        private BigDecimal estimatedWeightGrams;

        @JsonProperty("confidence_score")
        private BigDecimal confidenceScore;

        @JsonProperty("fallback_nutrition")
        private FallbackNutrition fallbackNutrition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FallbackNutrition {
        private BigDecimal calories;

        @JsonProperty("protein_g")
        private BigDecimal proteinG;

        @JsonProperty("fat_g")
        private BigDecimal fatG;

        @JsonProperty("carbs_g")
        private BigDecimal carbsG;
    }
}
