package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.olehprukhnytskyi.macrotrackerfoodservice.validation.ValidUnitGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Nutrition information partial update")
public class NutrimentsPatchDto {
    @Schema(description = "Calories per 100g", example = "170.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal caloriesPer100;

    @Schema(description = "Fat per 100g (g)", example = "4.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal fatPer100;

    @Schema(description = "Protein per 100g (g)", example = "32.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal proteinPer100;

    @Schema(description = "Carbohydrates per 100g (g)", example = "1.5", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal carbohydratesPer100;

    @Schema(description = "Calories per piece", example = "125.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal caloriesPerPiece;

    @Schema(description = "Fat per piece (g)", example = "2.5", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal fatPerPiece;

    @Schema(description = "Protein per piece (g)", example = "23.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal proteinPerPiece;

    @Schema(description = "Carbohydrates per piece (g)", example = "1.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal carbohydratesPerPiece;
}
