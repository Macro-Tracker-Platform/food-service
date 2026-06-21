package com.olehprukhnytskyi.macrotrackerfoodservice.repository.mongo;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.UserFoodFavorite;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFoodFavoriteRepository extends MongoRepository<UserFoodFavorite, String> {
    boolean existsByUserIdAndFoodId(Long userId, String foodId);

    List<UserFoodFavorite> findAllByUserIdAndFoodIdIn(Long userId, Collection<String> foodIds);

    void deleteByUserIdAndFoodId(Long userId, String foodId);

    void deleteByFoodId(String foodId);
}
