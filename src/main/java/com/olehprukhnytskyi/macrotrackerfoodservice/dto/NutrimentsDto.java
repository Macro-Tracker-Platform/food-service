package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.olehprukhnytskyi.macrotrackerfoodservice.validation.ValidUnitGroups;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.Data;

@Data
@ValidUnitGroups
@Schema(description = "Nutrition information for food product")
public class NutrimentsDto {
    @Schema(description = "Calories per 100g", example = "165.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal calories;

    @Schema(description = "Fat per 100g (g)", example = "3.6", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal fat;

    @Schema(description = "Protein per 100g (g)", example = "31.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal protein;

    @Schema(description = "Carbohydrates per 100g (g)", example = "0.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal carbohydrates;

    @Schema(description = "Calories per piece", example = "120.5", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal caloriesPerPiece;

    @Schema(description = "Fat per piece (g)", example = "2.1", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal fatPerPiece;

    @Schema(description = "Protein per piece (g)", example = "22.5", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal proteinPerPiece;

    @Schema(description = "Carbohydrates per piece (g)", example = "0.0", minimum = "0.0")
    @DecimalMin(value = "0.0")
    private BigDecimal carbohydratesPerPiece;
}
