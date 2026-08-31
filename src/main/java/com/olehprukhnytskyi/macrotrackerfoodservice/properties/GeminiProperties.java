package com.olehprukhnytskyi.macrotrackerfoodservice.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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

    @NotBlank
    private String foodPhotoPrompt;

    private NutritionLabelScan nutritionLabelScan = new NutritionLabelScan();
    private FoodPhotoScan foodPhotoScan = new FoodPhotoScan();

    @Data
    public static class NutritionLabelScan {
        @Min(1)
        private int requestDailyLimit = 50;
        @Min(1)
        private int freeSuccessfulMonthlyLimit = 3;
        @Min(1)
        private int proSuccessfulDailyLimit = 30;
        @Min(1)
        private int maxImageWidth = 1280;
        @Min(1)
        private int maxImageHeight = 1280;
        @Positive
        private double imageQuality = 0.75;
        @Min(1)
        private int maxOutputTokens = 128;
        @Min(-1)
        private int thinkingBudget = 0;
        private double temperature = 0.0;
        private String responseMimeType = "application/json";
        @Min(1)
        private long defaultRetryAfterSeconds = 60;
        private ZoneId rateLimitZone = ZoneId.of("UTC");
    }

    @Data
    public static class FoodPhotoScan {
        @Min(1)
        private int premiumDailyLimit = 30;
        @Min(1)
        private int maxImageWidth = 1280;
        @Min(1)
        private int maxImageHeight = 1280;
        @Positive
        private double imageQuality = 0.75;
        @Min(1)
        private int maxOutputTokens = 1024;
        @Min(-1)
        private int thinkingBudget = 0;
        private double temperature = 0.0;
        private String responseMimeType = "application/json";
        private ZoneId quotaZone = ZoneId.of("UTC");
        @Positive
        private double matchThreshold = 0.85;
        @Min(1)
        private int historyLimit = 50;
        @Min(1)
        private int globalCandidateLimit = 10;
        @Min(30)
        private long reservationTtlSeconds = 180;
        @Min(1)
        private int maxConcurrentScans = 32;
        @Min(1)
        private long maxDecodedPixels = 20_000_000;
        @Min(1)
        private long idempotencyTtlHours = 24;
    }
}
