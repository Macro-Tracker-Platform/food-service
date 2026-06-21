package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food favorite toggle request")
public class FoodFavoriteRequestDto {
    @NotNull
    @Schema(description = "Whether the product should be marked as favorite", example = "true")
    private Boolean favorite;
}
