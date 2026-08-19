package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.NutritionLabelRateLimitExceededException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.time.Instant;
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
    @Mock
    private EntitlementClient entitlementClient;

    private NutritionLabelScanService scanService;
    private NutritionLabelRateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = mock(NutritionLabelRateLimitService.class);
        GeminiProperties properties = new GeminiProperties();
        scanService = new NutritionLabelScanService(
                imageService, geminiService, rateLimitService,
                entitlementClient, properties);
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("FREE");
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
    }

    @Test
    void scan_whenGeminiSucceeds_shouldReturnResponse() {
        NutritionLabelRateLimitService.Reservation reservation =
                reservation();
        NutritionLabelScanResponseDto expected = new NutritionLabelScanResponseDto();

        when(rateLimitService.reserve(USER_ID, 3)).thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image)).thenReturn(expected);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getQuota().getLimit()).isEqualTo(3);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(2);
        verify(imageService).validateImage(image);
    }

    @Test
    void scan_forProUser_shouldApplyProMonthlyLimit() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("PRO");
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
        NutritionLabelRateLimitService.Reservation reservation =
                new NutritionLabelRateLimitService.Reservation(
                        "monthly-key", "daily-key", 60, 1,
                        Instant.now().plusSeconds(120));
        NutritionLabelScanResponseDto response = new NutritionLabelScanResponseDto();
        when(rateLimitService.reserve(USER_ID, 60)).thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(60);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(59);
    }

    @Test
    void scan_forLegacyFreeUser_shouldApplyProMonthlyLimit() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("LEGACY_FREE");
        entitlement.setLegacyAccess(true);
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
        NutritionLabelRateLimitService.Reservation reservation =
                new NutritionLabelRateLimitService.Reservation(
                        "monthly-key", "daily-key", 60, 1,
                        Instant.now().plusSeconds(120));
        NutritionLabelScanResponseDto response = new NutritionLabelScanResponseDto();
        when(rateLimitService.reserve(USER_ID, 60)).thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(60);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(59);
    }

    @Test
    void scan_whenGeminiIsTemporaryUnavailable_shouldReleaseReservation() {
        NutritionLabelRateLimitService.Reservation reservation =
                reservation();

        when(rateLimitService.reserve(USER_ID, 3)).thenReturn(reservation);
        doThrow(new GeminiTemporaryUnavailableException(60, null))
                .when(geminiService).scanNutritionLabel(image);

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(GeminiTemporaryUnavailableException.class);
        verify(rateLimitService).release(reservation);
    }

    @Test
    void scan_whenDailyLimitExceeded_shouldNotCallGemini() {
        when(rateLimitService.reserve(USER_ID, 3))
                .thenThrow(new NutritionLabelRateLimitExceededException(
                        "daily", 120, 15, Instant.now().plusSeconds(120)));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(NutritionLabelRateLimitExceededException.class);
        verify(geminiService, times(0)).scanNutritionLabel(image);
    }

    private NutritionLabelRateLimitService.Reservation reservation() {
        return new NutritionLabelRateLimitService.Reservation(
                "monthly-key", "daily-key", 3, 1, Instant.now().plusSeconds(120));
    }
}
