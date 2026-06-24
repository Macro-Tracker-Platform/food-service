package com.olehprukhnytskyi.macrotrackerfoodservice.client;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiRequest;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.GeminiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "gemini-api",
        url = "${gemini.base-url:https://generativelanguage.googleapis.com}"
)
public interface GeminiClient {
    @PostMapping(
            value = "${gemini.generate-content-path:"
                    + "/gemini/models/gemini-2.5-flash:generateContent}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    GeminiResponse generateContent(
            @RequestHeader("x-goog-api-key") String apiKey,
            @RequestBody GeminiRequest requestBody
    );
}
