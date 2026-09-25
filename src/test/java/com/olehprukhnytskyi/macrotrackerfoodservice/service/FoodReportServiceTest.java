package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReport;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReportReason;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.jpa.FoodReportRepository;
import com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo.FoodRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FoodReportServiceTest {
    @Mock
    private FoodRepository foodRepository;
    @Mock
    private FoodReportRepository foodReportRepository;
    private FoodReportService service;

    @BeforeEach
    void setUp() {
        service = new FoodReportService(foodRepository, foodReportRepository);
    }

    @Test
    void report_savesOnePendingReport() {
        Food food = Food.builder().id("food-1").visible(true).build();
        when(foodRepository.findById("food-1")).thenReturn(Optional.of(food));

        service.report("food-1", 42L, FoodReportReason.INCORRECT_NUTRITION);

        ArgumentCaptor<FoodReport> report = ArgumentCaptor.forClass(FoodReport.class);
        verify(foodReportRepository).save(report.capture());
        assertEquals(42L, report.getValue().getUserId());
        assertEquals("food-1", report.getValue().getFoodId());
        assertEquals(
                FoodReportReason.INCORRECT_NUTRITION, report.getValue().getReason());
    }

    @Test
    void report_isIdempotentForSameUserAndFood() {
        Food food = Food.builder().id("food-1").visible(true).build();
        when(foodRepository.findById("food-1")).thenReturn(Optional.of(food));
        when(foodReportRepository.existsByUserIdAndFoodId(42L, "food-1"))
                .thenReturn(true);

        service.report("food-1", 42L, FoodReportReason.OTHER);

        verify(foodReportRepository, never()).save(any());
    }
}
