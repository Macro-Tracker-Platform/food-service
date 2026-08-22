package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutrimentsLabelResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.NutritionLabelScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.GeminiTemporaryUnavailableException;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.NutritionLabelRateLimitExceededException;
import java.math.BigDecimal;
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
        scanService = new NutritionLabelScanService(
                imageService, geminiService, rateLimitService, entitlementClient);
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("FREE");
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
    }

    @Test
    void scan_whenGeminiSucceedsForFreeUser_shouldConsumeMonthlySuccessfulQuota() {
        NutritionLabelRateLimitService.SuccessfulScanQuota availableQuota =
                quota("monthly", 3, 0);
        NutritionLabelRateLimitService.SuccessfulScanQuota consumedQuota =
                quota("monthly", 3, 1);
        NutritionLabelScanResponseDto expected = responseWithNutriments();

        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, false))
                .thenReturn(availableQuota);
        when(rateLimitService.recordSuccessfulScan(USER_ID, false))
                .thenReturn(consumedQuota);
        when(geminiService.scanNutritionLabel(image)).thenReturn(expected);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getQuota().getLimit()).isEqualTo(3);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(2);
        verify(imageService).validateImage(image);
        verify(rateLimitService).reserveRequest(USER_ID);
    }

    @Test
    void scan_forProUser_shouldApplyPremiumDailySuccessfulQuota() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("PRO");
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
        NutritionLabelRateLimitService.SuccessfulScanQuota availableQuota =
                quota("premium-daily", 30, 0);
        NutritionLabelRateLimitService.SuccessfulScanQuota consumedQuota =
                quota("premium-daily", 30, 1);
        NutritionLabelScanResponseDto response = responseWithNutriments();

        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, true))
                .thenReturn(availableQuota);
        when(rateLimitService.recordSuccessfulScan(USER_ID, true))
                .thenReturn(consumedQuota);
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(30);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(29);
    }

    @Test
    void scan_forLegacyFreeUser_shouldApplyPremiumDailySuccessfulQuota() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("LEGACY_FREE");
        entitlement.setLegacyAccess(true);
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
        NutritionLabelRateLimitService.SuccessfulScanQuota availableQuota =
                quota("premium-daily", 30, 0);
        NutritionLabelRateLimitService.SuccessfulScanQuota consumedQuota =
                quota("premium-daily", 30, 1);
        NutritionLabelScanResponseDto response = responseWithNutriments();

        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, true))
                .thenReturn(availableQuota);
        when(rateLimitService.recordSuccessfulScan(USER_ID, true))
                .thenReturn(consumedQuota);
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(30);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(29);
    }

    @Test
    void scan_whenGeminiIsTemporaryUnavailable_shouldKeepRequestCountAndNotConsumeSuccessQuota() {
        NutritionLabelRateLimitService.SuccessfulScanQuota availableQuota =
                quota("monthly", 3, 0);

        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, false))
                .thenReturn(availableQuota);
        when(geminiService.scanNutritionLabel(image))
                .thenThrow(new GeminiTemporaryUnavailableException(60, null));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(GeminiTemporaryUnavailableException.class);
        verify(rateLimitService).reserveRequest(USER_ID);
        verify(rateLimitService, never()).recordSuccessfulScan(USER_ID, false);
    }

    @Test
    void scan_whenNoNutrientsAreParsed_shouldNotConsumeSuccessQuota() {
        NutritionLabelRateLimitService.SuccessfulScanQuota availableQuota =
                quota("monthly", 3, 0);
        NutritionLabelScanResponseDto response = new NutritionLabelScanResponseDto();

        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, false))
                .thenReturn(availableQuota);
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(3);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(3);
        verify(rateLimitService, never()).recordSuccessfulScan(USER_ID, false);
    }

    @Test
    void scan_whenRequestDailyLimitExceeded_shouldNotCallGemini() {
        when(rateLimitService.reserveRequest(USER_ID))
                .thenThrow(new NutritionLabelRateLimitExceededException(
                        "daily", 120, 50, Instant.now().plusSeconds(120)));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(NutritionLabelRateLimitExceededException.class);
        verify(rateLimitService, never()).ensureSuccessfulScanQuotaAvailable(USER_ID, false);
        verify(geminiService, times(0)).scanNutritionLabel(image);
    }

    @Test
    void scan_whenSuccessfulMonthlyQuotaExceeded_shouldNotCallGemini() {
        when(rateLimitService.ensureSuccessfulScanQuotaAvailable(USER_ID, false))
                .thenThrow(new NutritionLabelRateLimitExceededException(
                        "monthly", 120, 3, Instant.now().plusSeconds(120)));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(NutritionLabelRateLimitExceededException.class);
        verify(rateLimitService).reserveRequest(USER_ID);
        verify(geminiService, times(0)).scanNutritionLabel(image);
    }

    private NutritionLabelScanResponseDto responseWithNutriments() {
        return new NutritionLabelScanResponseDto(
                NutrimentsLabelResponseDto.builder()
                        .caloriesPer100(BigDecimal.valueOf(100))
                        .build());
    }

    private NutritionLabelRateLimitService.SuccessfulScanQuota quota(String scope,
                                                                     int limit,
                                                                     int used) {
        return new NutritionLabelRateLimitService.SuccessfulScanQuota(
                scope, limit, used, Instant.now().plusSeconds(120));
    }
}
