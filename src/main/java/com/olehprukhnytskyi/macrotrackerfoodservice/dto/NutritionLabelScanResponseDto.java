package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NutritionLabelScanResponseDto {
    private NutrimentsLabelResponseDto nutriments;
}
