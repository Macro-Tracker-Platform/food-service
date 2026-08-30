package com.olehprukhnytskyi.macrotrackerfoodservice.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class BarcodeScanQuotaDto {
    private boolean allowed;
    private boolean unlimited;
    private Integer limit;
    private Integer remaining;
    private Instant resetAt;
}
