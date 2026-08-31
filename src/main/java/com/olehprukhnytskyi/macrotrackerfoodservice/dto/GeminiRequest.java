package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRequest {
    private List<Content> contents;
    private GenerationConfig generationConfig;

    public GeminiRequest(List<Content> contents) {
        this.contents = contents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Part {
        private String text;
        private InlineData inlineData;

        public Part(String text) {
            this.text = text;
        }

        public Part(InlineData inlineData) {
            this.inlineData = inlineData;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InlineData {
        private String mimeType;
        private String data;
    }

    @Data
    @NoArgsConstructor
    public static class GenerationConfig {
        private Double temperature;
        private Integer maxOutputTokens;
        private String responseMimeType;
        private ThinkingConfig thinkingConfig;
        private Map<String, Object> responseSchema;

        public GenerationConfig(Double temperature, Integer maxOutputTokens,
                                String responseMimeType, ThinkingConfig thinkingConfig) {
            this(temperature, maxOutputTokens, responseMimeType, thinkingConfig, null);
        }

        public GenerationConfig(Double temperature, Integer maxOutputTokens,
                                String responseMimeType, ThinkingConfig thinkingConfig,
                                Map<String, Object> responseSchema) {
            this.temperature = temperature;
            this.maxOutputTokens = maxOutputTokens;
            this.responseMimeType = responseMimeType;
            this.thinkingConfig = thinkingConfig;
            this.responseSchema = responseSchema;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThinkingConfig {
        private Integer thinkingBudget;
    }
}
