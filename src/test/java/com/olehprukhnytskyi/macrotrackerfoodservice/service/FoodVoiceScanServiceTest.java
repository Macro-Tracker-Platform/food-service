package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodVoiceBase64RequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.web.multipart.MultipartFile;

class FoodVoiceScanServiceTest {
    private Base64AudioDecoder audioDecoder;
    private FoodPhotoEntitlementService entitlementService;
    private FoodVoiceQuotaReservationService quotaService;
    private GeminiService geminiService;
    private FoodPhotoMatchingService matchingService;
    private FoodVoiceScanService service;
    private MultipartFile audio;
    private FoodVoiceBase64RequestDto request;

    @BeforeEach
    void setUp() {
        audioDecoder = mock(Base64AudioDecoder.class);
        entitlementService = mock(FoodPhotoEntitlementService.class);
        quotaService = mock(FoodVoiceQuotaReservationService.class);
        geminiService = mock(GeminiService.class);
        matchingService = mock(FoodPhotoMatchingService.class);
        FoodPhotoCapacityGuard capacityGuard = mock(FoodPhotoCapacityGuard.class);
        FoodPhotoCapacityGuard.Permit permit = mock(FoodPhotoCapacityGuard.Permit.class);
        when(capacityGuard.acquire()).thenReturn(permit);
        FoodVoiceIdempotencyService idempotencyService = mock(FoodVoiceIdempotencyService.class);
        when(idempotencyService.execute(
                ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.<Function<String, FoodPhotoScanResponseDto>>any()))
                .thenAnswer(invocation -> {
                    Function<String, FoodPhotoScanResponseDto> action = invocation.getArgument(2);
                    return action.apply("operation-1");
                });
        service = new FoodVoiceScanService(
                audioDecoder,
                entitlementService,
                quotaService,
                geminiService,
                matchingService,
                capacityGuard,
                idempotencyService
        );
        audio = mock(MultipartFile.class);
        request = new FoodVoiceBase64RequestDto();
        when(audioDecoder.decode(request)).thenReturn(audio);
    }

    @Test
    void nonFoodDoesNotConsumeQuota() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("PRO");
        FoodVoiceQuotaReservationService.Reservation reservation =
                new FoodVoiceQuotaReservationService.Reservation(
                        42L, "operation-1", true, 18);
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, true, "operation-1"))
                .thenReturn(reservation);
        when(geminiService.scanFoodVoice(audio, null)).thenReturn(
                GeminiFoodPhotoScanDto.builder()
                        .scanType("not_food")
                        .imageQuality("usable")
                        .items(Collections.emptyList())
                        .build());

        FoodPhotoScanResponseDto response = service.scan(42L, null, null, null, request);

        assertThat(response.getScanType()).isEqualTo("not_food");
        assertThat(response.getRemainingScans()).isEqualTo(18);
        assertThat(response.getItems()).isEmpty();
        verify(quotaService, never()).commitSuccessfulFoodScan(reservation);
        verify(quotaService).release(reservation);
    }

    @Test
    void foodConsumesQuotaOnlyAfterMatchingSucceeds() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("FREE");
        GeminiFoodPhotoScanDto.Item voiceItem = GeminiFoodPhotoScanDto.Item.builder()
                .name("chicken breast")
                .build();
        FoodPhotoScanResponseDto.Item processed = FoodPhotoScanResponseDto.Item.builder()
                .name("Chicken Breast")
                .build();
        FoodVoiceQuotaReservationService.Reservation reservation =
                new FoodVoiceQuotaReservationService.Reservation(
                        42L, "operation-1", false, 5);
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, false, "operation-1"))
                .thenReturn(reservation);
        when(geminiService.scanFoodVoice(audio, null)).thenReturn(
                GeminiFoodPhotoScanDto.builder()
                        .scanType("food")
                        .imageQuality("usable")
                        .items(List.of(voiceItem))
                        .build());
        when(matchingService.process(42L, List.of(voiceItem)))
                .thenReturn(List.of(processed));
        when(quotaService.commitSuccessfulFoodScan(reservation))
                .thenReturn(new FoodVoiceQuotaReservationService.QuotaSnapshot(false, 4));

        FoodPhotoScanResponseDto response = service.scan(42L, null, null, null, request);

        assertThat(response.getRemainingScans()).isEqualTo(4);
        assertThat(response.getItems()).containsExactly(processed);
        verify(matchingService).process(42L, List.of(voiceItem));
        verify(quotaService).commitSuccessfulFoodScan(reservation);
    }
}
