package com.olehprukhnytskyi.macrotrackerfoodservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerfoodservice.model.FoodReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodReportRepository extends JpaRepository<FoodReport, Long> {
    boolean existsByUserIdAndFoodId(Long userId, String foodId);

    @Query("select report.foodId from FoodReport report where report.userId = :userId")
    List<String> findFoodIdsByUserId(@Param("userId") Long userId);
}
