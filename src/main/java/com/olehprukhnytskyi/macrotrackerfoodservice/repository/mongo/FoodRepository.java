package com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo;

import com.olehprukhnytskyi.macrotrackerfoodservice.dto.OriginalIdOnly;
import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.util.ModerationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends MongoRepository<Food, String> {
    void deleteByIdAndUserId(String id, Long userId);

    Page<Food> findAllByUserId(Long userId, Pageable pageable);

    Page<Food> findAllByUserIdAndIdNotIn(Long userId, List<String> excludedIds, Pageable pageable);

    Optional<Food> findByIdAndUserId(String id, Long userId);

    Page<Food> findAllByModerationStatus(ModerationStatus status, Pageable pageable);

    @Query(value = "{ 'user_id': ?0, 'original_food_id': { $ne: null } }",
            fields = "{ 'original_food_id': 1, '_id': 0 }")
    List<OriginalIdOnly> findOriginalIdsByUserId(Long userId);

    Optional<Food> findByOriginalFoodIdAndUserIdAndModerationStatus(
            String originalFoodId,
            Long userId,
            ModerationStatus status
    );
}
