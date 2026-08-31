package com.olehprukhnytskyi.macrotrackerfoodservice.service;

import com.olehprukhnytskyi.macrotrackerfoodservice.properties.GeminiProperties;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FoodPhotoCapacityGuard {
    private final Semaphore permits;

    public FoodPhotoCapacityGuard(GeminiProperties properties) {
        permits = new Semaphore(properties.getFoodPhotoScan().getMaxConcurrentScans());
    }

    public Permit acquire() {
        if (!permits.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Food photo scanning is at capacity; retry shortly");
        }
        return new Permit(permits);
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (!released) {
                released = true;
                semaphore.release();
            }
        }
    }
}
