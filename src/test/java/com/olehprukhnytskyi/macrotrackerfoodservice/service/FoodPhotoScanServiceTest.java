package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.EntitlementDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoScanResponseDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiFoodPhotoScanDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.FoodPhotoScanLimitException;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.web.multipart.MultipartFile;

class FoodPhotoScanServiceTest {
    private ImageService imageService;
    private FoodPhotoEntitlementService entitlementService;
    private FoodPhotoQuotaReservationService quotaService;
    private GeminiService geminiService;
    private FoodPhotoMatchingService matchingService;
    private FoodPhotoScanService service;
    private MultipartFile image;

    @BeforeEach
    void setUp() {
        imageService = mock(ImageService.class);
        entitlementService = mock(FoodPhotoEntitlementService.class);
        quotaService = mock(FoodPhotoQuotaReservationService.class);
        geminiService = mock(GeminiService.class);
        matchingService = mock(FoodPhotoMatchingService.class);
        FoodPhotoCapacityGuard capacityGuard = mock(FoodPhotoCapacityGuard.class);
        FoodPhotoCapacityGuard.Permit permit = mock(FoodPhotoCapacityGuard.Permit.class);
        when(capacityGuard.acquire()).thenReturn(permit);
        FoodPhotoIdempotencyService idempotencyService = mock(FoodPhotoIdempotencyService.class);
        when(idempotencyService.execute(
                ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.<Function<String, FoodPhotoScanResponseDto>>any()))
                .thenAnswer(invocation -> {
                    Function<String, FoodPhotoScanResponseDto> action = invocation.getArgument(2);
                    return action.apply("operation-1");
                });
        service = new FoodPhotoScanService(
                imageService,
                mock(Base64ImageDecoder.class),
                entitlementService,
                quotaService,
                geminiService,
                matchingService,
                capacityGuard,
                idempotencyService
        );
        image = mock(MultipartFile.class);
    }

    @Test
    void scanChecksQuotaBeforeCallingGemini() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("FREE");
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, false, "operation-1"))
                .thenThrow(new FoodPhotoScanLimitException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "FREE_LIMIT_REACHED"));

        assertThatThrownBy(() -> service.scan(42L, null, image))
                .isInstanceOf(FoodPhotoScanLimitException.class);

        verify(geminiService, never()).scanFoodPhoto(image, null);
    }

    @Test
    void nonFoodDoesNotConsumeQuota() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("PRO");
        FoodPhotoQuotaReservationService.Reservation reservation =
                new FoodPhotoQuotaReservationService.Reservation(
                        42L, "operation-1", true, 18);
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, true, "operation-1"))
                .thenReturn(reservation);
        when(geminiService.scanFoodPhoto(image, null)).thenReturn(
                GeminiFoodPhotoScanDto.builder()
                        .scanType("not_food")
                        .imageQuality("usable")
                        .items(Collections.emptyList())
                        .build());

        FoodPhotoScanResponseDto response = service.scan(42L, null, image);

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
        GeminiFoodPhotoScanDto.Item visionItem = GeminiFoodPhotoScanDto.Item.builder()
                .name("chicken breast")
                .build();
        FoodPhotoScanResponseDto.Item processed = FoodPhotoScanResponseDto.Item.builder()
                .name("Chicken Breast")
                .build();
        FoodPhotoQuotaReservationService.Reservation reservation =
                new FoodPhotoQuotaReservationService.Reservation(
                        42L, "operation-1", false, 5);
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, false, "operation-1"))
                .thenReturn(reservation);
        when(geminiService.scanFoodPhoto(image, null)).thenReturn(
                GeminiFoodPhotoScanDto.builder()
                        .scanType("food")
                        .imageQuality("usable")
                        .items(List.of(visionItem))
                        .build());
        when(matchingService.process(42L, List.of(visionItem)))
                .thenReturn(List.of(processed));
        when(quotaService.commitSuccessfulFoodScan(reservation))
                .thenReturn(new FoodPhotoQuotaReservationService.QuotaSnapshot(false, 4));

        FoodPhotoScanResponseDto response = service.scan(42L, null, image);

        assertThat(response.getRemainingScans()).isEqualTo(4);
        assertThat(response.getItems()).containsExactly(processed);
        verify(matchingService).process(42L, List.of(visionItem));
        verify(quotaService).commitSuccessfulFoodScan(reservation);
    }

    @Test
    void blurredFoodPhotoReleasesReservationWithoutCharging() {
        EntitlementDto entitlement = new EntitlementDto();
        entitlement.setPlan("FREE");
        FoodPhotoQuotaReservationService.Reservation reservation =
                new FoodPhotoQuotaReservationService.Reservation(
                        42L, "operation-1", false, 5);
        when(entitlementService.get(42L, null)).thenReturn(entitlement);
        when(quotaService.reserve(42L, false, "operation-1"))
                .thenReturn(reservation);
        when(geminiService.scanFoodPhoto(image, null)).thenReturn(
                GeminiFoodPhotoScanDto.builder()
                        .scanType("food")
                        .imageQuality("blurred")
                        .items(Collections.emptyList())
                        .build());

        FoodPhotoScanResponseDto response = service.scan(42L, null, image);

        assertThat(response.getScanType()).isEqualTo("not_food");
        assertThat(response.getRemainingScans()).isEqualTo(5);
        verify(quotaService, never()).commitSuccessfulFoodScan(reservation);
        verify(quotaService).release(reservation);
    }
}
