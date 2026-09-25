package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Reason for reporting a food product")
public class FoodReportRequestDto {
    @NotNull
    private FoodReportReason reason;
}
