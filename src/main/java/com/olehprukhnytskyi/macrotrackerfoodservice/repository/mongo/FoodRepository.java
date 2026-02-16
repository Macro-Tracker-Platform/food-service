package com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.Food;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends MongoRepository<Food, String> {
    void deleteByIdAndUserId(String id, Long userId);

    Page<Food> findAllByUserId(Long userId, Pageable pageable);

    Optional<Food> findByIdAndUserId(String id, Long userId);
}
