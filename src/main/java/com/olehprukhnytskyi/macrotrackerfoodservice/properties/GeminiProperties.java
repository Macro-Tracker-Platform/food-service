package com.olehprukhnytskyi.macrotrackerfoodservice.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.ZoneId;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    @NotBlank
    private String apiKey;

    @NotBlank
    private String nutritionLabelPrompt;

    private NutritionLabelScan nutritionLabelScan = new NutritionLabelScan();

    @Data
    public static class NutritionLabelScan {
        @Min(1)
        private int dailyLimit = 10;
        @Min(1)
        private int maxImageWidth = 1600;
        @Min(1)
        private int maxImageHeight = 1600;
        @Min(1)
        private long defaultRetryAfterSeconds = 60;
        private ZoneId rateLimitZone = ZoneId.of("UTC");
    }
}
