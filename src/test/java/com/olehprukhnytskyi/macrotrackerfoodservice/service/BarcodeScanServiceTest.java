package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerfoodservice.client.EntitlementClient;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BarcodeScanServiceTest {
    private static final long USER_ID = 42L;
    private static final String BARCODE = "0123456789012";

    @Mock
    private FoodService foodService;
    @Mock
    private EntitlementClient entitlementClient;

    private BarcodeScanService barcodeScanService;

    @BeforeEach
    void setUp() {
        barcodeScanService = new BarcodeScanService(foodService, entitlementClient);
    }

    @Test
    void allowedReservationReturnsFood() {
        BarcodeScanQuotaDto quota = quota(true, 4);
        FoodResponseDto food = new FoodResponseDto();
        when(entitlementClient.reserveBarcodeScan(USER_ID, BARCODE)).thenReturn(quota);
        when(foodService.findPersonalizedById(BARCODE, USER_ID)).thenReturn(food);

        BarcodeScanService.ScanResult result = barcodeScanService.scan(USER_ID, BARCODE);

        assertThat(result.food()).isSameAs(food);
        assertThat(result.quota()).isSameAs(quota);
    }

    @Test
    void deniedReservationStopsBeforeFoodLookup() {
        BarcodeScanQuotaDto quota = quota(false, 0);
        when(entitlementClient.reserveBarcodeScan(USER_ID, BARCODE)).thenReturn(quota);

        assertThatThrownBy(() -> barcodeScanService.scan(USER_ID, BARCODE))
                .isInstanceOf(BarcodeScanService.LimitExceededException.class);
        verify(foodService, never()).findPersonalizedById(BARCODE, USER_ID);
    }

    private BarcodeScanQuotaDto quota(boolean allowed, int remaining) {
        BarcodeScanQuotaDto quota = new BarcodeScanQuotaDto();
        quota.setAllowed(allowed);
        quota.setLimit(5);
        quota.setRemaining(remaining);
        return quota;
    }
}
