package com.olehprukhnytskyi.macrotrackerfoodservice.exception;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FoodPhotoScanExceptionHandler {
    @ExceptionHandler(FoodPhotoScanLimitException.class)
    public ResponseEntity<Map<String, Object>> handleLimit(
            FoodPhotoScanLimitException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Map.of(
                "error", exception.getError(),
                "remaining_scans", 0
        ));
    }
}
