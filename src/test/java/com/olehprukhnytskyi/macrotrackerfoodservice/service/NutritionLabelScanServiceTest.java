package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.NutritionLabelRateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class NutritionLabelScanServiceTest {
    private static final Long USER_ID = 42L;

    @Mock
    private ImageService imageService;
    @Mock
    private GeminiService geminiService;
    @Mock
    private MultipartFile image;

    private NutritionLabelScanService scanService;
    private NutritionLabelRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(NutritionLabelRateLimitService.class);
        scanService = new NutritionLabelScanService(imageService, geminiService, rateLimitService);
    }

    @Test
    void scan_whenGeminiSucceeds_shouldReturnResponse() {
        NutritionLabelRateLimitService.Reservation reservation =
                new NutritionLabelRateLimitService.Reservation("key");
        NutritionLabelScanResponseDto expected = new NutritionLabelScanResponseDto();

        when(rateLimitService.reserve(USER_ID)).thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image)).thenReturn(expected);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, image);

        assertThat(actual).isSameAs(expected);
        verify(imageService).validateImage(image);
    }

    @Test
    void scan_whenGeminiIsTemporaryUnavailable_shouldReleaseReservation() {
        NutritionLabelRateLimitService.Reservation reservation =
                new NutritionLabelRateLimitService.Reservation("key");

        when(rateLimitService.reserve(USER_ID)).thenReturn(reservation);
        doThrow(new GeminiTemporaryUnavailableException(60, null))
                .when(geminiService).scanNutritionLabel(image);

        assertThatThrownBy(() -> scanService.scan(USER_ID, image))
                .isInstanceOf(GeminiTemporaryUnavailableException.class);
        verify(rateLimitService).release(reservation);
    }

    @Test
    void scan_whenDailyLimitExceeded_shouldNotCallGemini() {
        when(rateLimitService.reserve(USER_ID))
                .thenThrow(new NutritionLabelRateLimitExceededException(120));

        assertThatThrownBy(() -> scanService.scan(USER_ID, image))
                .isInstanceOf(NutritionLabelRateLimitExceededException.class);
        verify(geminiService, times(0)).scanNutritionLabel(image);
    }
}
