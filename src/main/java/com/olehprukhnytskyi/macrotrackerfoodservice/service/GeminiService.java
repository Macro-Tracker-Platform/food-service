package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.client.GeminiClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiRequest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiResponse;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import feign.FeignException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {
    private static final String GEMINI_IMAGE_MIME_TYPE = "image/jpeg";
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final ImageService imageService;
    private final ObjectMapper objectMapper;

    public List<String> generateKeywords(Food food) {
        if (isFoodInvalid(food)) {
            log.warn("Skipping keyword generation: incomplete food data");
            return Collections.emptyList();
        }
        GeminiRequest request = createRequest(food);
        try {
            log.debug("Requesting Gemini keyword generation for food='{}'", food.getProductName());
            GeminiResponse response = geminiClient.generateContent(
                    geminiProperties.getApiKey(),
                    request
            );
            return extractKeywords(response, food.getProductName());
        } catch (Exception e) {
            log.error("Failed to generate keywords for '{}'", food.getProductName(), e);
        }
        return List.of();
    }

    public NutritionLabelScanResponseDto scanNutritionLabel(MultipartFile image) {
        GeminiRequest request = createNutritionLabelScanRequest(image);
        try {
            log.debug("Requesting Gemini nutrition label scan");
            GeminiResponse response = geminiClient.generateContent(
                    geminiProperties.getApiKey(),
                    request
            );
            return parseNutritionLabelResponse(response);
        } catch (FeignException e) {
            if (isTemporaryGeminiFailure(e)) {
                throw new GeminiTemporaryUnavailableException(
                        retryAfterSeconds(e),
                        e
                );
            }
            log.error("Gemini nutrition label scan failed with status={}", e.status(), e);
            throw new ExternalServiceException(
                    CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Gemini nutrition label scan failed",
                    e
            );
        }
    }

    private List<String> extractKeywords(GeminiResponse response, String productName) {
        if (response == null
                || response.getCandidates() == null
                || response.getCandidates().isEmpty()) {
            return Collections.emptyList();
        }
        String content = response.getCandidates().getFirst()
                .getContent().getParts().getFirst()
                .getText().trim();
        if ("unknown".equalsIgnoreCase(content)) {
            log.debug("Gemini returned 'unknown' for '{}'", productName);
            return Collections.emptyList();
        }
        List<String> keywords = Arrays.stream(content.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        log.info("Generated {} keywords for '{}'", keywords.size(), productName);
        return keywords;
    }

    private GeminiRequest createRequest(Food food) {
        String promptText = buildPromptText(food);
        return new GeminiRequest(List.of(
                new GeminiRequest.Content(List.of(new GeminiRequest.Part(promptText)))
        ));
    }

    private GeminiRequest createNutritionLabelScanRequest(MultipartFile image) {
        GeminiProperties.NutritionLabelScan scanProperties =
                geminiProperties.getNutritionLabelScan();
        byte[] imageBytes = imageService.resizeImageToJpegBytes(
                image,
                scanProperties.getMaxImageWidth(),
                scanProperties.getMaxImageHeight(),
                scanProperties.getImageQuality()
        );
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        GeminiRequest request = new GeminiRequest(List.of(
                new GeminiRequest.Content(List.of(
                        new GeminiRequest.Part(geminiProperties.getNutritionLabelPrompt()),
                        new GeminiRequest.Part(new GeminiRequest.InlineData(
                                GEMINI_IMAGE_MIME_TYPE,
                                base64Image
                        ))
                ))
        ));
        request.setGenerationConfig(new GeminiRequest.GenerationConfig(
                scanProperties.getTemperature(),
                scanProperties.getMaxOutputTokens(),
                scanProperties.getResponseMimeType(),
                new GeminiRequest.ThinkingConfig(scanProperties.getThinkingBudget())
        ));
        return request;
    }

    private NutritionLabelScanResponseDto parseNutritionLabelResponse(GeminiResponse response) {
        String text = extractJsonObject(extractFirstText(response)
                .replaceAll("(?s)```json|```", "")
                .trim());
        try {
            return objectMapper.readValue(text, NutritionLabelScanResponseDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini nutrition label response: {}", text, e);
            throw new ExternalServiceException(
                    CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Gemini returned an invalid nutrition label response",
                    e
            );
        }
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String extractFirstText(GeminiResponse response) {
        if (response == null
                || response.getCandidates() == null
                || response.getCandidates().isEmpty()
                || response.getCandidates().getFirst().getContent() == null
                || response.getCandidates().getFirst().getContent().getParts() == null
                || response.getCandidates().getFirst().getContent().getParts().isEmpty()
                || response.getCandidates().getFirst().getContent().getParts().getFirst()
                .getText() == null) {
            throw new ExternalServiceException(
                    CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Gemini returned an empty nutrition label response"
            );
        }
        return response.getCandidates().getFirst()
                .getContent().getParts().getFirst().getText();
    }

    private boolean isTemporaryGeminiFailure(FeignException exception) {
        return exception.status() == 429 || exception.status() == 503;
    }

    private long retryAfterSeconds(FeignException exception) {
        return extractRetryAfterHeader(exception.responseHeaders())
                .map(this::parseRetryAfterSeconds)
                .orElse(geminiProperties.getNutritionLabelScan()
                        .getDefaultRetryAfterSeconds());
    }

    private java.util.Optional<String> extractRetryAfterHeader(
            Map<String, Collection<String>> headers) {
        if (headers == null) {
            return java.util.Optional.empty();
        }
        return headers.entrySet().stream()
                .filter(entry -> "retry-after".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(values -> values != null && !values.isEmpty())
                .flatMap(Collection::stream)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private long parseRetryAfterSeconds(String value) {
        try {
            return Math.max(1, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            // Retry-After can be an HTTP date.
        }
        try {
            Instant retryAt = ZonedDateTime
                    .parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            return Math.max(1, Duration.between(Instant.now(), retryAt).toSeconds());
        } catch (DateTimeParseException ignored) {
            return geminiProperties.getNutritionLabelScan()
                    .getDefaultRetryAfterSeconds();
        }
    }

    private boolean isFoodInvalid(Food food) {
        return food == null || food.getProductName() == null || food.getNutriments() == null;
    }

    private static String buildPromptText(Food food) {
        return """
                You are given information about a food product.
                         Your task:
                         1. Detect the language of the product data.
                         2. If confident what the product is, generate 5–10
                         relevant keywords in that language.
                         3. Only use specific nouns or phrases directly related
                         to ingredients, type of product, form, preparation method, or brand.
                         4. Do NOT include vague adjectives like tasty, nutritious,
                         healthy, caloric, or any emotional or evaluative terms.
                         5. Format: return ONLY a single comma-separated list of
                         lowercase keywords. No labels, no line breaks, no explanations.
                         6. If not confident what the product is — return exactly: unknown
                
                         Product name: %s
                         Generic name: %s
                         Brand: %s
                         Nutritional values: kcal=%s, fat=%s, proteins=%s, carbohydrates=%s
                """.formatted(
                        food.getProductName(),
                        food.getGenericName(),
                        food.getBrands(),
                        food.getNutriments().getCalories(),
                        food.getNutriments().getFat(),
                        food.getNutriments().getProtein(),
                        food.getNutriments().getCarbohydrates()
                );
    }
}
