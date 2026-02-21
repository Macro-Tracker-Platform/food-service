package com.olehprukhnytskyi.macrotrackerfoodservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.GeminiClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiResponse;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import com.olehprukhnytskyi.macrotrackerfoodservice.service.GeminiService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {
    @Mock
    private GeminiClient geminiClient;
    @Mock
    private GeminiProperties geminiProperties;

    @InjectMocks
    private GeminiService geminiService;

    private Food food;

    @BeforeEach
    void setup() {
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
}
