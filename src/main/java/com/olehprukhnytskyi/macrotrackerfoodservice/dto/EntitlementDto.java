package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import lombok.Data;

@Data
public class EntitlementDto {
    private String plan;
    private String state;
    private boolean legacyAccess;
    private Features features;

    @Data
    public static class Features {
        private ScanAllowance nutritionLabelScans;
    }

    @Data
    public static class ScanAllowance {
        private int limit;
    }
}
