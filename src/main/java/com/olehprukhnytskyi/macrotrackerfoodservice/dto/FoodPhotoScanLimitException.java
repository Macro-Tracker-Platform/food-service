package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class FoodPhotoScanLimitException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public FoodPhotoScanLimitException(HttpStatus status, String error) {
        super(error);
        this.status = status;
        this.error = error;
    }
}
