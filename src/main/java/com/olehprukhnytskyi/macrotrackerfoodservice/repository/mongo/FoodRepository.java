package com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import com.olehprukhnytskyi.util.ModerationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends MongoRepository<Food, String> {
    void deleteByIdAndUserId(String id, Long userId);

    Page<Food> findAllByUserId(Long userId, Pageable pageable);

    Optional<Food> findByIdAndUserId(String id, Long userId);

    Page<Food> findAllByModerationStatus(ModerationStatus status, Pageable pageable);

    @Query("SELECT f.originalFoodId FROM Food f "
            + "WHERE f.userId = :userId AND f.originalFoodId IS NOT NULL")
    List<String> findOverriddenOriginalIdsByUserId(@Param("userId") Long userId);
}
