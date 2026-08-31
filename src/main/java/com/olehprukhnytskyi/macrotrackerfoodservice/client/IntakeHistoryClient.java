package com.olehprukhnytskyi.macrotrackerfoodservice.client;

import com.olehprukhnytskyi.util.CustomHeaders;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "intake-history", url = "${feign.intake-service:http://localhost:8081}")
public interface IntakeHistoryClient {
    @GetMapping("/api/intake/history/food-ids")
    List<String> getFrequentRecentFoodIds(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam int limit);
}
