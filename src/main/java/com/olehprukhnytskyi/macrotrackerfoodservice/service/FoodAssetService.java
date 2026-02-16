package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodAssetService {
    private static final int FOOD_IMAGE_SIZE = 400;
    private final S3StorageService s3StorageService;
    private final ImageService imageService;

    public String uploadToTemp(MultipartFile image) {
        log.debug("Starting temp upload for image: {}", image.getOriginalFilename());
        try {
            imageService.validateImage(image);
            ByteArrayInputStream resizedStream = imageService
                    .resizeImage(image, FOOD_IMAGE_SIZE);
            String format = imageService.detectImageFormat(image);
            String tempKey = "tmp/" + UUID.randomUUID() + "." + format;
            s3StorageService.uploadFile(resizedStream,
                    resizedStream.available(), tempKey, image.getContentType());
            log.debug("Image uploaded to temp: {}", tempKey);
            return tempKey;
        } catch (Exception e) {
            log.error("Failed to upload temp image", e);
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Error processing image upload", e);
        }
    }

    public String confirmImage(String tempKey, String foodId) {
        log.debug("Confirming image {} for foodId={}", tempKey, foodId);
        String extension = getExtension(tempKey);
        String finalKey = imageService.buildImageKey(foodId, FOOD_IMAGE_SIZE, extension);
        return s3StorageService.moveObject(tempKey, finalKey);
    }

    private String getExtension(String filename) {
        if (StringUtils.hasText(filename) && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1);
        }
        return "jpg";
    }
}
