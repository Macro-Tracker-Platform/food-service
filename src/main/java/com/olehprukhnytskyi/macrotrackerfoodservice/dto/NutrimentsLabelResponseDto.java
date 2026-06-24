package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.olehprukhnytskyi.macrotrackerfoodservice.validation.ValidUnitGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidUnitGroups
@Schema(description = "Nutrition information for food product")
public class NutrimentsLabelResponseDto {
    @Schema(description = "Calories per 100g", example = "165.0", minimum = "0.0")
    private BigDecimal caloriesPer100;

    @Schema(description = "Carbohydrates per 100g (g)", example = "0.0", minimum = "0.0")
    private BigDecimal carbohydratesPer100;

    @Schema(description = "Fat per 100g (g)", example = "3.6", minimum = "0.0")
    private BigDecimal fatPer100;

    @Schema(description = "Protein per 100g (g)", example = "31.0", minimum = "0.0")
    private BigDecimal proteinPer100;
}
