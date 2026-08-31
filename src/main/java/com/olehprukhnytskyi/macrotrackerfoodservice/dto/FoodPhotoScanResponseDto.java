package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodPhotoScanResponseDto {
    @JsonProperty("scan_type")
    private String scanType;

    @JsonProperty("remaining_scans")
    private int remainingScans;

    @JsonProperty("is_premium")
    private boolean premium;

    @Builder.Default
    private List<Item> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @JsonProperty("temp_id")
        private UUID tempId;

        private String id;
        private String name;

        @JsonProperty("search_query")
        private String searchQuery;

        @JsonProperty("weight_g")
        private BigDecimal weightG;

        private String source;

        @JsonProperty("match_score")
        private BigDecimal matchScore;

        private BigDecimal calories;

        @JsonProperty("protein_g")
        private BigDecimal proteinG;

        @JsonProperty("fat_g")
        private BigDecimal fatG;

        @JsonProperty("carbs_g")
        private BigDecimal carbsG;
    }
}
