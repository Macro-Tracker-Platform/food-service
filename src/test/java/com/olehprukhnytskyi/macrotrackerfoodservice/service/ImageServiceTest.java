package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageServiceTest {
    private final ImageService imageService = new ImageService();

    @Test
    @DisplayName("When JPEG is within max dimensions, should return original bytes")
    void resizeImageToJpegBytes_whenJpegFits_shouldReturnOriginalBytes() throws IOException {
        // Given
        byte[] originalBytes = createJpegBytes(8, 8);
        MockMultipartFile file = new MockMultipartFile(
                "image",
                "label.jpg",
                "image/jpeg",
                originalBytes
        );

        // When
        byte[] result = imageService.resizeImageToJpegBytes(file, 1280, 1280, 0.75);

        // Then
        assertArrayEquals(originalBytes, result);
    }

    private byte[] createJpegBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        }
    }
}
