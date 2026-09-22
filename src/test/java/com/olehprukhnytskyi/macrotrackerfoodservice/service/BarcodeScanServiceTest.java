package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
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
    private BarcodeScanService barcodeScanService;

    @BeforeEach
    void setUp() {
        barcodeScanService = new BarcodeScanService(foodService);
    }

    @Test
    void scanReturnsFood() {
        FoodResponseDto food = new FoodResponseDto();
        when(foodService.findPersonalizedById(BARCODE, USER_ID)).thenReturn(food);

        FoodResponseDto result = barcodeScanService.scan(USER_ID, BARCODE);

        assertThat(result).isSameAs(food);
    }

    @Test
    void ean13UpcAliasLooksUpRawBarcodeFirst() {
        String rawEan13 = "0036000291452";
        String canonicalUpc = "036000291452";
        FoodResponseDto food = new FoodResponseDto();
        when(foodService.findPersonalizedById(rawEan13, USER_ID)).thenReturn(food);

        FoodResponseDto result = barcodeScanService.scan(USER_ID, rawEan13);

        assertThat(result).isSameAs(food);
        verify(foodService).findPersonalizedById(rawEan13, USER_ID);
        verify(foodService, never()).findPersonalizedById(canonicalUpc, USER_ID);
    }

    @Test
    void upcLookupFallsBackToEan13Alias() {
        String rawUpc = "036000291452";
        String ean13Alias = "0036000291452";
        FoodResponseDto food = new FoodResponseDto();
        when(foodService.findPersonalizedById(rawUpc, USER_ID))
                .thenThrow(new NotFoundException(
                        FoodErrorCode.FOOD_NOT_FOUND,
                        "Food not found with id or code: " + rawUpc));
        when(foodService.findPersonalizedById(ean13Alias, USER_ID)).thenReturn(food);

        FoodResponseDto result = barcodeScanService.scan(USER_ID, rawUpc);

        assertThat(result).isSameAs(food);
        verify(foodService).findPersonalizedById(rawUpc, USER_ID);
        verify(foodService).findPersonalizedById(ean13Alias, USER_ID);
    }
}
