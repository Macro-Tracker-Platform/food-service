package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
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
    private NutritionLabelRateLimitService rateLimitService;
    @Mock
    private AiCreditReservationService aiCreditReservationService;
    @Mock
    private EntitlementClient entitlementClient;
    @Mock
    private MultipartFile image;

    private NutritionLabelScanService scanService;

    @BeforeEach
    void setUp() {
        scanService = new NutritionLabelScanService(
                imageService,
                geminiService,
                rateLimitService,
                aiCreditReservationService,
                entitlementClient);
    }

    @Test
    void successfulFreeScanConsumesOneSharedDailyCredit() {
        givenPlan("FREE", false);
        AiCreditReservationService.Reservation reservation = freeReservation();
        when(aiCreditReservationService.reserve(
                org.mockito.ArgumentMatchers.eq(USER_ID), startsWith("nutrition-label:")))
                .thenReturn(reservation);
        when(aiCreditReservationService.commit(reservation))
                .thenReturn(new AiCreditReservationService.QuotaSnapshot(
                        3, 2, reservation.resetAt()));
        NutritionLabelScanResponseDto response = responseWithNutriments();
        when(geminiService.scanNutritionLabel(image)).thenReturn(response);

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(3);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(2);
        verify(aiCreditReservationService).commit(reservation);
        verify(rateLimitService, never()).ensurePremiumSuccessfulScanQuotaAvailable(USER_ID);
    }

    @Test
    void completedFreeScanConsumesCreditEvenWithoutParsedNutrients() {
        givenPlan("FREE", false);
        AiCreditReservationService.Reservation reservation = freeReservation();
        when(aiCreditReservationService.reserve(
                org.mockito.ArgumentMatchers.eq(USER_ID), startsWith("nutrition-label:")))
                .thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image))
                .thenReturn(new NutritionLabelScanResponseDto());
        when(aiCreditReservationService.commit(reservation))
                .thenReturn(new AiCreditReservationService.QuotaSnapshot(
                        3, 2, reservation.resetAt()));

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getRemaining()).isEqualTo(2);
        verify(aiCreditReservationService).commit(reservation);
        verify(aiCreditReservationService, never()).release(reservation);
    }

    @Test
    void failedFreeScanReleasesReservation() {
        givenPlan("FREE", false);
        AiCreditReservationService.Reservation reservation = freeReservation();
        when(aiCreditReservationService.reserve(
                org.mockito.ArgumentMatchers.eq(USER_ID), startsWith("nutrition-label:")))
                .thenReturn(reservation);
        when(geminiService.scanNutritionLabel(image))
                .thenThrow(new GeminiTemporaryUnavailableException(60, null));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(GeminiTemporaryUnavailableException.class);

        verify(aiCreditReservationService).release(reservation);
        verify(aiCreditReservationService, never()).commit(reservation);
    }

    @Test
    void premiumScanKeepsExistingDailyAntiAbuseQuota() {
        givenPlan("PRO", false);
        NutritionLabelRateLimitService.SuccessfulScanQuota available =
                premiumQuota(0);
        NutritionLabelRateLimitService.SuccessfulScanQuota consumed =
                premiumQuota(1);
        when(rateLimitService.ensurePremiumSuccessfulScanQuotaAvailable(USER_ID))
                .thenReturn(available);
        when(rateLimitService.recordPremiumSuccessfulScan(USER_ID)).thenReturn(consumed);
        when(geminiService.scanNutritionLabel(image)).thenReturn(responseWithNutriments());

        NutritionLabelScanResponseDto actual = scanService.scan(USER_ID, null, image);

        assertThat(actual.getQuota().getLimit()).isEqualTo(30);
        assertThat(actual.getQuota().getRemaining()).isEqualTo(29);
        verify(aiCreditReservationService, never()).reserve(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void requestAntiAbuseLimitStillStopsBeforeAiCreditReservation() {
        givenPlan("FREE", false);
        when(rateLimitService.reserveRequest(USER_ID))
                .thenThrow(new NutritionLabelRateLimitExceededException(
                        "daily", 120, 50, Instant.now().plusSeconds(120)));

        assertThatThrownBy(() -> scanService.scan(USER_ID, null, image))
                .isInstanceOf(NutritionLabelRateLimitExceededException.class);

        verify(aiCreditReservationService, never()).reserve(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(geminiService, never()).scanNutritionLabel(image);
    }

    private void givenPlan(String plan, boolean legacyAccess) {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan(plan);
        entitlement.setLegacyAccess(legacyAccess);
        when(entitlementClient.getEntitlement(USER_ID, null)).thenReturn(entitlement);
    }

    private AiCreditReservationService.Reservation freeReservation() {
        return new AiCreditReservationService.Reservation(
                USER_ID,
                "nutrition-label:test",
                3,
                3,
                Instant.now().plusSeconds(3600));
    }

    private NutritionLabelScanResponseDto responseWithNutriments() {
        return new NutritionLabelScanResponseDto(
                NutrimentsLabelResponseDto.builder()
                        .caloriesPer100(BigDecimal.valueOf(100))
                        .build());
    }

    private NutritionLabelRateLimitService.SuccessfulScanQuota premiumQuota(int used) {
        return new NutritionLabelRateLimitService.SuccessfulScanQuota(
                "premium-daily", 30, used, Instant.now().plusSeconds(3600));
    }
}
