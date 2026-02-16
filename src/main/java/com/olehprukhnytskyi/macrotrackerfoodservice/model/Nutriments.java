package com.olehprukhnytskyi.macrotrackerfoodservice.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.olehprukhnytskyi.model.NutrientAware;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Nutriments implements NutrientAware {
    @JsonAlias({"energy-kcal", "kcal", "calories"})
    @Field(name = "energy-kcal", targetType = FieldType.DOUBLE)
    private BigDecimal calories;

    @Field(targetType = FieldType.DOUBLE)
    private BigDecimal fat;

    @JsonAlias({"protein", "proteins"})
    @Field(name = "proteins", targetType = FieldType.DOUBLE)
    private BigDecimal protein;

    @Field(targetType = FieldType.DOUBLE)
    private BigDecimal carbohydrates;

    @JsonAlias({"energy-kcal_piece", "caloriesPerPiece"})
    @Field(name = "energy-kcal_piece", targetType = FieldType.DOUBLE)
    @DecimalMin(value = "0.0")
    private BigDecimal caloriesPerPiece;

    @JsonAlias({"fat_piece", "fatPerPiece"})
    @Field(name = "fat_piece", targetType = FieldType.DOUBLE)
    private BigDecimal fatPerPiece;

    @JsonAlias({"proteins_piece", "proteinPerPiece"})
    @Field(name = "proteins_piece", targetType = FieldType.DOUBLE)
    private BigDecimal proteinPerPiece;

    @JsonAlias({"carbohydrates_piece", "carbohydratesPerPiece"})
    @Field(name = "carbohydrates_piece", targetType = FieldType.DOUBLE)
    private BigDecimal carbohydratesPerPiece;
}
