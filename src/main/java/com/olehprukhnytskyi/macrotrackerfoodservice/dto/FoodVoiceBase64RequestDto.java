package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FoodVoiceBase64RequestDto {
    @NotBlank
    @JsonProperty("audio_base64")
    @JsonAlias("base64")
    private String audioBase64;

    @JsonProperty("media_type")
    private String mediaType;

    @NotNull
    @Positive
    @JsonProperty("duration_ms")
    private Long durationMs;
}
