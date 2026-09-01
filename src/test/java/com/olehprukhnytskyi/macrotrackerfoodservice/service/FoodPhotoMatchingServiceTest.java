package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.dao.FoodSearchDao;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FoodPhotoMatchingServiceTest {
    private FoodPhotoHistoryLoader historyLoader;
    private FoodSearchDao searchDao;
    private FoodPhotoMatchingService service;

    @BeforeEach
    void setUp() {
        historyLoader = mock(FoodPhotoHistoryLoader.class);
        searchDao = mock(FoodSearchDao.class);
        GeminiProperties properties = new GeminiProperties();
        properties.getFoodPhotoScan().setMatchThreshold(0.85);
        service = new FoodPhotoMatchingService(historyLoader, searchDao, properties);
    }

    @Test
    void exactHistoryMatchUsesScaledDatabaseNutrition() {
        Food food = Food.builder()
                .id("food-1")
                .productName("Chicken Breast")
                .nutriments(Nutriments.builder()
                        .caloriesPer100(new BigDecimal("165"))
                        .proteinPer100(new BigDecimal("31"))
                        .fatPer100(new BigDecimal("3.6"))
                        .carbohydratesPer100(BigDecimal.ZERO)
                        .build())
                .build();
        GeminiFoodPhotoScanDto.Item visionItem = item("Куряча грудка", "150");
        visionItem.setSearchName("chicken breast");
        when(historyLoader.load(7L)).thenReturn(List.of(food));

        FoodPhotoScanResponseDto.Item result = service.process(7L, List.of(visionItem)).getFirst();

        assertThat(result.getSource()).isEqualTo("db_matched");
        assertThat(result.getId()).isEqualTo("food-1");
        assertThat(result.getSearchQuery()).isEqualTo("Куряча грудка");
        assertThat(result.getCalories()).isEqualByComparingTo("247.50");
        assertThat(result.getProteinG()).isEqualByComparingTo("46.50");
        assertThat(result.getMatchScore()).isEqualByComparingTo("1.0000");
        verify(searchDao, never()).searchPhotoCandidates(List.of("chicken breast"), 7L, 10);
    }

    @Test
    void noStrongMatchUsesGeminiFallbackNutrition() {
        GeminiFoodPhotoScanDto.Item visionItem = item("dragon fruit bowl", "220");
        when(historyLoader.load(7L)).thenReturn(List.of());
        when(searchDao.searchPhotoCandidates(List.of("dragon fruit bowl"), 7L, 10))
                .thenReturn(List.of());

        FoodPhotoScanResponseDto.Item result = service.process(7L, List.of(visionItem)).getFirst();

        assertThat(result.getSource()).isEqualTo("ai_fallback");
        assertThat(result.getId()).isNull();
        assertThat(result.getName()).isEqualTo("dragon fruit bowl");
        assertThat(result.getCalories()).isEqualByComparingTo("230.00");
    }

    private GeminiFoodPhotoScanDto.Item item(String name, String weight) {
        return GeminiFoodPhotoScanDto.Item.builder()
                .name(name)
                .estimatedWeightGrams(new BigDecimal(weight))
                .confidenceScore(new BigDecimal("0.93"))
                .fallbackNutrition(GeminiFoodPhotoScanDto.FallbackNutrition.builder()
                        .calories(new BigDecimal("230"))
                        .proteinG(new BigDecimal("8"))
                        .fatG(new BigDecimal("4"))
                        .carbsG(new BigDecimal("40"))
                        .build())
                .build();
    }
}
