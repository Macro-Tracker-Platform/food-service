package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerfoodservice.dto.FoodPhotoBase64RequestDto;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class Base64ImageDecoder {
    private static final String DATA_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final int MAX_ENCODED_LENGTH = 7_000_000;

    public MultipartFile decode(FoodPhotoBase64RequestDto request) {
        String encoded = request.getImageBase64().trim();
        String mediaType = request.getMediaType();
        if (encoded.startsWith(DATA_PREFIX)) {
            int marker = encoded.indexOf(BASE64_MARKER);
            if (marker < DATA_PREFIX.length()) {
                throw invalidBase64();
            }
            mediaType = encoded.substring(DATA_PREFIX.length(), marker);
            encoded = encoded.substring(marker + BASE64_MARKER.length());
        }
        if (mediaType == null || mediaType.isBlank()) {
            mediaType = "image/jpeg";
        }
        if (encoded.length() > MAX_ENCODED_LENGTH) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Base64 image must not exceed 5 MB decoded");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return new InMemoryMultipartFile(bytes, mediaType);
        } catch (IllegalArgumentException exception) {
            throw invalidBase64();
        }
    }

    private BadRequestException invalidBase64() {
        return new BadRequestException(CommonErrorCode.BAD_REQUEST,
                "Image must contain valid base64 data");
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
            return "image";
        }

        @Override
        public String getOriginalFilename() {
            return "food-photo";
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
