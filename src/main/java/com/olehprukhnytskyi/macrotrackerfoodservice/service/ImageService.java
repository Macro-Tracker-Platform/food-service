package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ImageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public void validateImage(MultipartFile image) {
        if (image.isEmpty()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST, "Empty file uploaded");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(image.getContentType())) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Invalid file type: only JPG, PNG, and WEBP are allowed");
        }
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "File size must not exceed 5 MB");
        }
    }

    public ByteArrayInputStream resizeImage(MultipartFile file, int size) {
        try {
            log.debug("Resizing image to {}px", size);
            BufferedImage resizedImage = Thumbnails.of(ImageIO.read(file.getInputStream()))
                    .width(size)
                    .keepAspectRatio(true)
                    .asBufferedImage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resizedImage, detectImageFormat(file), baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            log.error("Image resize failed", e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to resize image", e);
        }
    }

    public byte[] resizeImageToJpegBytes(MultipartFile file,
                                         int maxWidth,
                                         int maxHeight,
                                         double quality) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            BufferedImage source = ImageIO.read(file.getInputStream());
            if (source == null) {
                throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                        "Unsupported image format");
            }
            if (canUseOriginalJpeg(file, source, maxWidth, maxHeight)) {
                log.debug("Using original JPEG for Gemini");
                return file.getBytes();
            }
            log.debug("Resizing image for Gemini to max {}x{}px", maxWidth, maxHeight);
            BufferedImage resized = Thumbnails.of(source)
                    .size(maxWidth, maxHeight)
                    .keepAspectRatio(true)
                    .asBufferedImage();
            BufferedImage rgb = new BufferedImage(
                    resized.getWidth(),
                    resized.getHeight(),
                    BufferedImage.TYPE_INT_RGB
            );
            rgb.getGraphics().drawImage(resized, 0, 0, java.awt.Color.WHITE, null);
            Thumbnails.of(rgb)
                    .scale(1)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Image resize failed", e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to resize image", e);
        }
    }

    private boolean canUseOriginalJpeg(MultipartFile file,
                                       BufferedImage source,
                                       int maxWidth,
                                       int maxHeight) {
        return "image/jpeg".equals(file.getContentType())
                && source.getWidth() <= maxWidth
                && source.getHeight() <= maxHeight;
    }

    public String detectImageFormat(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            ImageInputStream iis = ImageIO.createImageInputStream(is);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                String format = readers.next().getFormatName().toLowerCase();
                log.trace("Detected image format: {}", format);
                return format;
            } else {
                throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                        "Unsupported image format");
            }
        } catch (IOException e) {
            log.error("Failed to detect image format", e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to detect image format", e);
        }
    }

    public String generateImageKey(MultipartFile file, String foodId, int imageWidth) {
        String format = detectImageFormat(file);
        return buildImageKey(foodId, imageWidth, format);
    }

    public String buildImageKey(String foodId, int width, String format) {
        String key = "images/products/" + foodId + "/" + width + "." + format;
        log.trace("Generated image key={}", key);
        return key;
    }
}
