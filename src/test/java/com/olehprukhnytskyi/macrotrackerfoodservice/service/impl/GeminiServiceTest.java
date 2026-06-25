package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackerfoodservice.client.GeminiClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiRequest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiResponse;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.GeminiService;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.ImageService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {
    @Mock
    private GeminiClient geminiClient;
    @Mock
    private GeminiProperties geminiProperties;
    @Mock
    private ImageService imageService;

    private GeminiService geminiService;

    private Food food;

    @BeforeEach
    void setup() {
        geminiService = new GeminiService(
                geminiClient,
                geminiProperties,
                imageService,
                new ObjectMapper()
        );
        Nutriments nutriments = new Nutriments();
        nutriments.setCaloriesPer100(BigDecimal.valueOf(200.0));
        nutriments.setFatPer100(BigDecimal.valueOf(8.0));
        nutriments.setProteinPer100(BigDecimal.valueOf(10.0));
        nutriments.setCarbohydratesPer100(BigDecimal.valueOf(15.0));

        food = new Food();
        food.setProductName("Protein Bar");
        food.setGenericName("bar");
        food.setBrands("TestBrand");
        food.setNutriments(nutriments);
    }

    @Test
    @DisplayName("When response is valid, should return a list")
    void generateKeywords_whenResponseIsValid_shouldReturnList() {
        // Given
        GeminiResponse response = new GeminiResponse(List.of(
                new GeminiResponse.Candidate(
                        new GeminiResponse.Content(List.of(
                                new GeminiResponse.Part("protein bar, chocolate, peanuts")
                        ))
                )
        ));

        when(geminiClient.generateContent(any(), any()))
                .thenReturn(response);

        // When
        List<String> result = geminiService.generateKeywords(food);

        // Then
        assertEquals(List.of("protein bar", "chocolate", "peanuts"), result);
    }

    @Test
    @DisplayName("When content is unknown, should return an empty list")
    void generateKeywords_whenContentIsUnknown_shouldReturnEmptyList() {
        // Given
        GeminiResponse response = new GeminiResponse(List.of(
                new GeminiResponse.Candidate(
                        new GeminiResponse.Content(List.of(
                                new GeminiResponse.Part("unknown")
                        ))
                )
        ));

        when(geminiClient.generateContent(any(), any()))
                .thenReturn(response);

        // When
        List<String> result = geminiService.generateKeywords(food);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("When response is not 2xx, should return an empty list")
    void generateKeywords_whenResponseIsNot2xx_shouldReturnEmptyList() {
        // Given
        when(geminiClient.generateContent(any(), any()))
                .thenReturn(null);

        // When
        List<String> result = geminiService.generateKeywords(food);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("When food is null, should return an empty list")
    void generateKeywords_whenFoodIsNull_shouldReturnEmptyList() {
        assertTrue(geminiService.generateKeywords(null).isEmpty());
    }

    @Test
    @DisplayName("When scanning nutrition label, should disable Gemini thinking")
    void scanNutritionLabel_whenCreatingRequest_shouldDisableThinking() {
        // Given
        MultipartFile image = new MockMultipartFile(
                "image",
                "label.jpg",
                "image/jpeg",
                new byte[] {9}
        );
        mockNutritionLabelScanRequest();
        when(geminiClient.generateContent(eq("test-key"), any()))
                .thenReturn(nutritionLabelResponse(
                        "{\"nutriments\":{\"caloriesPer100\":97,"
                                + "\"carbohydratesPer100\":3.8,"
                                + "\"fatPer100\":5.0,\"proteinPer100\":8.6}}"
                ));

        // When
        geminiService.scanNutritionLabel(image);

        // Then
        ArgumentCaptor<GeminiRequest> requestCaptor =
                ArgumentCaptor.forClass(GeminiRequest.class);
        verify(geminiClient).generateContent(eq("test-key"), requestCaptor.capture());

        GeminiRequest.GenerationConfig generationConfig =
                requestCaptor.getValue().getGenerationConfig();
        assertNotNull(generationConfig.getThinkingConfig());
        assertEquals(0, generationConfig.getThinkingConfig().getThinkingBudget());
    }

    @Test
    @DisplayName("When scan response wraps JSON in text, should parse nutriments")
    void scanNutritionLabel_whenResponseContainsWrappedJson_shouldParseNutriments() {
        // Given
        MultipartFile image = new MockMultipartFile(
                "image",
                "label.jpg",
                "image/jpeg",
                new byte[] {9}
        );
        mockNutritionLabelScanRequest();
        when(geminiClient.generateContent(eq("test-key"), any()))
                .thenReturn(nutritionLabelResponse("""
                        Here is the JSON requested:
                        ```json
                        {"nutriments":{"caloriesPer100":97.0,
                        "carbohydratesPer100":3.8,"fatPer100":5.0,
                        "proteinPer100":8.6}}
                        ```
                        """));

        // When
        NutritionLabelScanResponseDto result = geminiService.scanNutritionLabel(image);

        // Then
        assertEquals(BigDecimal.valueOf(97.0),
                result.getNutriments().getCaloriesPer100());
        assertEquals(BigDecimal.valueOf(3.8),
                result.getNutriments().getCarbohydratesPer100());
        assertEquals(BigDecimal.valueOf(5.0),
                result.getNutriments().getFatPer100());
        assertEquals(BigDecimal.valueOf(8.6),
                result.getNutriments().getProteinPer100());
    }

    private void mockNutritionLabelScanRequest() {
        GeminiProperties.NutritionLabelScan scanProperties =
                new GeminiProperties.NutritionLabelScan();
        when(geminiProperties.getApiKey()).thenReturn("test-key");
        when(geminiProperties.getNutritionLabelPrompt())
                .thenReturn("Extract nutrition facts from the image.");
        when(geminiProperties.getNutritionLabelScan()).thenReturn(scanProperties);
        when(imageService.resizeImageToJpegBytes(
                any(),
                eq(scanProperties.getMaxImageWidth()),
                eq(scanProperties.getMaxImageHeight()),
                eq(scanProperties.getImageQuality())
        )).thenReturn(new byte[] {1, 2, 3});
    }

    private GeminiResponse nutritionLabelResponse(String text) {
        return new GeminiResponse(List.of(
                new GeminiResponse.Candidate(
                        new GeminiResponse.Content(List.of(
                                new GeminiResponse.Part(text)
                        ))
                )
        ));
    }
}
