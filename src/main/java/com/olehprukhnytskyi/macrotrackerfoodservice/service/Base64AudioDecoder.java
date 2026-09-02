package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodVoiceBase64RequestDto;
import com.olehprukhnytskyi.macrotrackerfoodservice.exception.FoodPhotoScanLimitException;
import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class Base64AudioDecoder {
    private static final String DATA_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String DEFAULT_MEDIA_TYPE = "audio/3gpp";

    private final GeminiProperties properties;

    public MultipartFile decode(FoodVoiceBase64RequestDto request) {
        validateDuration(request.getDurationMs());
        String encoded = request.getAudioBase64().trim();
        String mediaType = request.getMediaType();
        if (encoded.startsWith(DATA_PREFIX)) {
            int marker = encoded.indexOf(BASE64_MARKER);
            if (marker < DATA_PREFIX.length()) {
                throw invalidAudio();
            }
            mediaType = encoded.substring(DATA_PREFIX.length(), marker);
            encoded = encoded.substring(marker + BASE64_MARKER.length());
        }
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = DEFAULT_MEDIA_TYPE;
        }
        if (encoded.length() > maxEncodedLength()) {
            throw audioTooLarge();
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0) {
                throw invalidAudio();
            }
            if (bytes.length > maxAudioBytes()) {
                throw audioTooLarge();
            }
            return new InMemoryMultipartFile(bytes, mediaType);
        } catch (IllegalArgumentException exception) {
            throw invalidAudio();
        }
    }

    private void validateDuration(Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            throw invalidAudio();
        }
        if (durationMs > properties.getFoodVoiceScan().getMaxDurationMs()) {
            throw new FoodPhotoScanLimitException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "AUDIO_TOO_LONG");
        }
    }

    private long maxAudioBytes() {
        return properties.getFoodVoiceScan().getMaxAudioBytes();
    }

    private long maxEncodedLength() {
        return ((maxAudioBytes() + 2) / 3) * 4;
    }

    private FoodPhotoScanLimitException invalidAudio() {
        return new FoodPhotoScanLimitException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO");
    }

    private FoodPhotoScanLimitException audioTooLarge() {
        return new FoodPhotoScanLimitException(HttpStatus.PAYLOAD_TOO_LARGE,
                "AUDIO_TOO_LARGE");
    }

    private static class InMemoryMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String contentType;

        InMemoryMultipartFile(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "audio";
        }

        @Override
        public String getOriginalFilename() {
            return "food-voice";
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(destination.toPath(), bytes);
        }
    }
}
