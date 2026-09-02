package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodVoiceBase64RequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.FoodPhotoScanLimitException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

class Base64AudioDecoderTest {
    private Base64AudioDecoder decoder;

    @BeforeEach
    void setUp() {
        GeminiProperties properties = new GeminiProperties();
        properties.getFoodVoiceScan().setMaxAudioBytes(5);
        properties.getFoodVoiceScan().setMaxDurationMs(60_000);
        decoder = new Base64AudioDecoder(properties);
    }

    @Test
    void decodesSupportedAudioDataUri() throws Exception {
        FoodVoiceBase64RequestDto request = request(
                "data:audio/3gpp;base64," + Base64.getEncoder()
                        .encodeToString(new byte[] {1, 2, 3}),
                null,
                1_000L
        );

        MultipartFile audio = decoder.decode(request);

        assertThat(audio.getContentType()).isEqualTo("audio/3gpp");
        assertThat(audio.getBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsUnsupportedMediaType() {
        FoodVoiceBase64RequestDto request = request(
                Base64.getEncoder().encodeToString(new byte[] {1}),
                "text/plain",
                1_000L
        );

        assertThatThrownBy(() -> decoder.decode(request))
                .isInstanceOf(FoodPhotoScanLimitException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.BAD_REQUEST, "INVALID_AUDIO");
    }

    @Test
    void rejectsAudioLongerThanConfiguredLimit() {
        FoodVoiceBase64RequestDto request = request(
                Base64.getEncoder().encodeToString(new byte[] {1}),
                "audio/3gpp",
                60_001L
        );

        assertThatThrownBy(() -> decoder.decode(request))
                .isInstanceOf(FoodPhotoScanLimitException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LONG");
    }

    @Test
    void rejectsDecodedAudioOverConfiguredSize() {
        FoodVoiceBase64RequestDto request = request(
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4, 5, 6}),
                "audio/3gpp",
                1_000L
        );

        assertThatThrownBy(() -> decoder.decode(request))
                .isInstanceOf(FoodPhotoScanLimitException.class)
                .extracting("status", "error")
                .containsExactly(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE");
    }

    private FoodVoiceBase64RequestDto request(String audioBase64,
                                              String mediaType,
                                              Long durationMs) {
        FoodVoiceBase64RequestDto request = new FoodVoiceBase64RequestDto();
        request.setAudioBase64(audioBase64);
        request.setMediaType(mediaType);
        request.setDurationMs(durationMs);
        return request;
    }
}
