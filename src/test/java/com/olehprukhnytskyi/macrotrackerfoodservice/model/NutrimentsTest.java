package com.olehprukhnytskyi.macrotrackerfoodservice.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NutrimentsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCachedJsonWithComputedFields() throws Exception {
        String json = """
                {
                  "caloriesPer100": 120,
                  "fatPer100": 4,
                  "proteinPer100": 6,
                  "carbohydratesPer100": 15,
                  "availableUnits": ["GRAMS"],
                  "gramsDataComplete": true,
                  "piecesDataComplete": false
                }
                """;

        Nutriments nutriments = objectMapper.readValue(json, Nutriments.class);

        assertEquals(new BigDecimal("120"), nutriments.getCaloriesPer100());
        assertTrue(nutriments.isGramsDataComplete());
    }

    @Test
    void doesNotSerializeComputedFields() {
        Nutriments nutriments = Nutriments.builder()
                .caloriesPer100(new BigDecimal("120"))
                .fatPer100(new BigDecimal("4"))
                .proteinPer100(new BigDecimal("6"))
                .carbohydratesPer100(new BigDecimal("15"))
                .build();

        JsonNode json = objectMapper.valueToTree(nutriments);

        assertFalse(json.has("availableUnits"));
        assertFalse(json.has("gramsDataComplete"));
        assertFalse(json.has("piecesDataComplete"));
    }
}
