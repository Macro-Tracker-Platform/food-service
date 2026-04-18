package com.olehprukhnytskyi.macrotrackerfoodservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.olehprukhnytskyi.util.ModerationStatus;
import jakarta.persistence.Version;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "foods")
public class Food {
    @MongoId
    @Field(name = "_id")
    @JsonProperty("_id")
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    private String code;

    @Field(name = "product_name")
    @JsonProperty("product_name")
    private String productName;

    @Field(name = "generic_name")
    @JsonProperty("generic_name")
    private String genericName;

    @Field(name = "image_url")
    @JsonProperty("image_url")
    private String imageUrl;

    private String brands;

    @Field(name = "_keywords")
    @JsonProperty("_keywords")
    private List<String> keywords;

    private Nutriments nutriments;

    @Indexed
    @Field(name = "user_id")
    @JsonProperty("user_id")
    private Long userId;

    @Field(name = "original_food_id")
    @JsonProperty("original_food_id")
    private String originalFoodId;

    @Field(name = "moderation_status")
    @JsonProperty("moderation_status")
    @Builder.Default
    private ModerationStatus moderationStatus = ModerationStatus.NONE;

    @Field(name = "verified_by_admin")
    @JsonProperty("verified_by_admin")
    @Builder.Default
    private boolean verifiedByAdmin = false;
}
