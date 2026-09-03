package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
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
    private static final String BARCODE = "5901234123457";

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

    @Test
    void ean13UpcAliasUsesCanonicalQuotaKeyButLooksUpRawBarcodeFirst() {
        String rawEan13 = "0036000291452";
        String canonicalUpc = "036000291452";
        BarcodeScanQuotaDto quota = quota(true, 4);
        FoodResponseDto food = new FoodResponseDto();
        when(entitlementClient.reserveBarcodeScan(USER_ID, canonicalUpc)).thenReturn(quota);
        when(foodService.findPersonalizedById(rawEan13, USER_ID)).thenReturn(food);

        BarcodeScanService.ScanResult result = barcodeScanService.scan(USER_ID, rawEan13);

        assertThat(result.food()).isSameAs(food);
        verify(foodService).findPersonalizedById(rawEan13, USER_ID);
        verify(foodService, never()).findPersonalizedById(canonicalUpc, USER_ID);
    }

    @Test
    void upcLookupFallsBackToEan13Alias() {
        String rawUpc = "036000291452";
        String ean13Alias = "0036000291452";
        BarcodeScanQuotaDto quota = quota(true, 4);
        FoodResponseDto food = new FoodResponseDto();
        when(entitlementClient.reserveBarcodeScan(USER_ID, rawUpc)).thenReturn(quota);
        when(foodService.findPersonalizedById(rawUpc, USER_ID))
                .thenThrow(new NotFoundException(
                        FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id or code: " + rawUpc));
        when(foodService.findPersonalizedById(ean13Alias, USER_ID)).thenReturn(food);

        BarcodeScanService.ScanResult result = barcodeScanService.scan(USER_ID, rawUpc);

        assertThat(result.food()).isSameAs(food);
        verify(foodService).findPersonalizedById(rawUpc, USER_ID);
        verify(foodService).findPersonalizedById(ean13Alias, USER_ID);
    }

    private BarcodeScanQuotaDto quota(boolean allowed, int remaining) {
        BarcodeScanQuotaDto quota = new BarcodeScanQuotaDto();
        quota.setAllowed(allowed);
        quota.setLimit(5);
        quota.setRemaining(remaining);
        return quota;
    }
}
