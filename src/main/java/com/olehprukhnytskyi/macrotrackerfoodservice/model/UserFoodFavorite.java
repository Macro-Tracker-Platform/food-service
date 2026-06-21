package com.olehprukhnytskyi.macrotrackerfoodservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_food_favorites")
@CompoundIndex(
        name = "user_food_favorite_unique",
        def = "{'user_id': 1, 'food_id': 1}",
        unique = true
)
public class UserFoodFavorite {
    @MongoId
    @Field(name = "_id")
    @JsonProperty("_id")
    private String id;

    @Indexed
    @Field(name = "user_id")
    @JsonProperty("user_id")
    private Long userId;

    @Indexed
    @Field(name = "food_id")
    @JsonProperty("food_id")
    private String foodId;
}
