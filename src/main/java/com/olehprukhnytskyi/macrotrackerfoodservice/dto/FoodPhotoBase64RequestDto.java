package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FoodPhotoBase64RequestDto {
    @NotBlank
    @JsonProperty("image_base64")
    @JsonAlias("base64")
    private String imageBase64;

    @JsonProperty("media_type")
    private String mediaType;
}
