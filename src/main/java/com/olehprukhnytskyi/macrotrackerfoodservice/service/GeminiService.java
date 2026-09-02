package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.client.GeminiClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiRequest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiResponse;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import feign.FeignException;
import java.math.BigDecimal;
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
import java.util.Locale;
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
    private static final String DEFAULT_FOOD_NAME_LANGUAGE = "en";
    private static final int MAX_UPSTREAM_ERROR_LOG_LENGTH = 1_000;
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

    public GeminiFoodPhotoScanDto scanFoodPhoto(MultipartFile image) {
        return scanFoodPhoto(image, null);
    }

    public GeminiFoodPhotoScanDto scanFoodPhoto(MultipartFile image, String acceptLanguage) {
        String languageTag = resolveFoodNameLanguage(acceptLanguage);
        GeminiRequest request = createFoodPhotoScanRequest(image, languageTag);
        Instant startedAt = Instant.now();
        try {
            log.debug("Requesting Gemini food photo scan language={}", languageTag);
            GeminiResponse response = geminiClient.generateContent(
                    geminiProperties.getApiKey(), request);
            logUsage("food-photo", startedAt, response);
            return parseFoodPhotoResponse(response);
        } catch (FeignException e) {
            log.warn("Gemini food photo scan failed after {}ms with status={} response={}",
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    e.status(), upstreamErrorSummary(e));
            if (isTemporaryGeminiFailure(e)) {
                throw new GeminiTemporaryUnavailableException(retryAfterSeconds(e), e);
            }
            throw new ExternalServiceException(
                    CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Gemini food photo scan failed",
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

    private GeminiRequest createFoodPhotoScanRequest(MultipartFile image,
                                                     String languageTag) {
        GeminiProperties.FoodPhotoScan scanProperties = geminiProperties.getFoodPhotoScan();
        byte[] imageBytes = imageService.resizeImageToJpegBytes(
                image,
                scanProperties.getMaxImageWidth(),
                scanProperties.getMaxImageHeight(),
                scanProperties.getImageQuality()
        );
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        GeminiRequest request = new GeminiRequest(List.of(
                new GeminiRequest.Content(List.of(
                        new GeminiRequest.Part(localizedFoodPhotoPrompt(languageTag)),
                        new GeminiRequest.Part(new GeminiRequest.InlineData(
                                GEMINI_IMAGE_MIME_TYPE, base64Image))
                ))
        ));
        request.setGenerationConfig(new GeminiRequest.GenerationConfig(
                scanProperties.getTemperature(),
                scanProperties.getMaxOutputTokens(),
                scanProperties.getResponseMimeType(),
                new GeminiRequest.ThinkingConfig(scanProperties.getThinkingBudget()),
                foodPhotoResponseSchema()
        ));
        return request;
    }

    private String localizedFoodPhotoPrompt(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String languageName = locale.getDisplayLanguage(Locale.ENGLISH);
        return geminiProperties.getFoodPhotoPrompt()
                + "\n\nReturn every items[].name in " + languageName
                + " (BCP 47 language tag: " + languageTag + ") using its native script. "
                + "Keep the name concise, standardized, and suitable for food search. "
                + "Return items[].search_name as the corresponding concise canonical English "
                + "food name for database matching. "
                + "Do not translate JSON property names or enum values.";
    }

    private String resolveFoodNameLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT_FOOD_NAME_LANGUAGE;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                if ("*".equals(range.getRange())) {
                    continue;
                }
                Locale locale = Locale.forLanguageTag(range.getRange()).stripExtensions();
                if (!locale.getLanguage().isBlank()) {
                    return locale.toLanguageTag();
                }
            }
        } catch (IllegalArgumentException exception) {
            log.debug("Ignoring invalid Accept-Language value for food photo scan");
        }
        return DEFAULT_FOOD_NAME_LANGUAGE;
    }

    private Map<String, Object> foodPhotoResponseSchema() {
        Map<String, Object> nutrition = Map.of(
                "type", "OBJECT",
                "required", List.of("calories", "protein_g", "fat_g", "carbs_g"),
                "properties", Map.of(
                        "calories", nonNegativeNumberSchema(),
                        "protein_g", nonNegativeNumberSchema(),
                        "fat_g", nonNegativeNumberSchema(),
                        "carbs_g", nonNegativeNumberSchema()
                )
        );

        Map<String, Object> item = Map.of(
                "type", "OBJECT",
                "required", List.of("name", "search_name", "estimated_weight_grams",
                        "confidence_score", "fallback_nutrition"),
                "properties", Map.of(
                        "name", Map.of(
                                "type", "STRING",
                                "description", "The specific, exact name of the food item"
                                               + " and preparation method, provided in the "
                                               + "requested language. STRICTLY avoid general"
                                               + " categories like 'fish', 'bread', or 'rice' "
                                               + "(e.g., use 'Grilled Atlantic Salmon' or"
                                               + " 'Fried Basmati Rice')."
                        ),
                        "search_name", Map.of(
                                "type", "STRING",
                                "description", "STRICTLY base ingredient name in the"
                                               + " requested language. NO cooking methods,"
                                               + " NO states, NO parentheses)."
                        ),
                        "estimation_rationale", Map.of(
                                "type", "STRING",
                                "description", "Brief estimation math: For discrete items,"
                                               + " estimate total count and multiply by single"
                                               + " piece weight. For continuous items, estimate "
                                               + "surface area in cm2, thickness in cm,"
                                               + " and multiply by density."
                        ),
                        "estimated_weight_grams", Map.of(
                                "type", "NUMBER",
                                "minimum", 0,
                                "description", "Actual edible as-served weight in grams."
                        ),
                        "confidence_score", Map.of(
                                "type", "NUMBER",
                                "minimum", 0,
                                "maximum", 1
                        ),
                        "fallback_nutrition", nutrition
                )
        );

        return Map.of(
                "type", "OBJECT",
                "required", List.of("scan_type", "image_quality", "items"),
                "properties", Map.of(
                        "scan_type", Map.of(
                                "type", "STRING",
                                "enum", List.of("food", "barcode", "not_food")
                        ),
                        "image_quality", Map.of(
                                "type", "STRING",
                                "enum", List.of("usable", "blurred")
                        ),
                        "items", Map.of(
                                "type", "ARRAY",
                                "description", "Return empty array [] if scan_type is"
                                               + " barcode/not_food or image_quality is blurred.",
                                "items", item
                        )
                )
        );
    }

    private Map<String, Object> nonNegativeNumberSchema() {
        return Map.of("type", "NUMBER", "minimum", 0);
    }

    private String upstreamErrorSummary(FeignException exception) {
        String response = exception.contentUTF8();
        if (response == null || response.isBlank()) {
            return "<empty>";
        }
        String singleLine = response.replaceAll("[\\r\\n\\t]+", " ").trim();
        return singleLine.length() <= MAX_UPSTREAM_ERROR_LOG_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_UPSTREAM_ERROR_LOG_LENGTH) + "...";
    }

    private GeminiFoodPhotoScanDto parseFoodPhotoResponse(GeminiResponse response) {
        String text = extractFirstText(response).trim();
        try {
            GeminiFoodPhotoScanDto parsed = objectMapper.readValue(
                    text, GeminiFoodPhotoScanDto.class);
            validateFoodPhotoResponse(parsed);
            return parsed;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.error("Failed to parse Gemini food photo response", exception);
            throw new ExternalServiceException(
                    CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Gemini returned an invalid food photo response",
                    exception
            );
        }
    }

    private void validateFoodPhotoResponse(GeminiFoodPhotoScanDto response) {
        if (response == null || !List.of("food", "barcode", "not_food")
                .contains(response.getScanType())) {
            throw new IllegalArgumentException("Invalid scan_type");
        }
        if (!List.of("usable", "blurred").contains(response.getImageQuality())) {
            throw new IllegalArgumentException("Invalid image_quality");
        }
        List<GeminiFoodPhotoScanDto.Item> items = response.getItems();
        if ("blurred".equals(response.getImageQuality())) {
            if (items == null || !items.isEmpty()) {
                throw new IllegalArgumentException(
                        "Blurred scans must contain an empty items array");
            }
            return;
        }
        if (items == null || (!"food".equals(response.getScanType()) && !items.isEmpty())) {
            throw new IllegalArgumentException("Non-food scans must contain an empty items array");
        }
        if (!"food".equals(response.getScanType())) {
            return;
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Food scan must contain at least one item");
        }
        for (GeminiFoodPhotoScanDto.Item item : items) {
            if (item == null || item.getName() == null || item.getName().isBlank()
                    || item.getSearchName() == null || item.getSearchName().isBlank()
                    || !inRange(item.getEstimatedWeightGrams(), BigDecimal.ZERO, null)
                    || !inRange(item.getConfidenceScore(), BigDecimal.ZERO, BigDecimal.ONE)
                    || item.getFallbackNutrition() == null
                    || !validNutrition(item.getFallbackNutrition())) {
                throw new IllegalArgumentException("Invalid food item in Gemini response");
            }
        }
    }

    private boolean validNutrition(GeminiFoodPhotoScanDto.FallbackNutrition nutrition) {
        return inRange(nutrition.getCalories(), BigDecimal.ZERO, null)
                && inRange(nutrition.getProteinG(), BigDecimal.ZERO, null)
                && inRange(nutrition.getFatG(), BigDecimal.ZERO, null)
                && inRange(nutrition.getCarbsG(), BigDecimal.ZERO, null);
    }

    private boolean inRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value != null && value.compareTo(minimum) >= 0
                && (maximum == null || value.compareTo(maximum) <= 0);
    }

    private void logUsage(String operation, Instant startedAt, GeminiResponse response) {
        GeminiResponse.UsageMetadata usage = response == null
                ? null : response.getUsageMetadata();
        log.info("Gemini operation={} latency_ms={} prompt_tokens={} output_tokens={} "
                        + "total_tokens={}",
                operation,
                Duration.between(startedAt, Instant.now()).toMillis(),
                usage == null ? null : usage.getPromptTokenCount(),
                usage == null ? null : usage.getCandidatesTokenCount(),
                usage == null ? null : usage.getTotalTokenCount());
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
