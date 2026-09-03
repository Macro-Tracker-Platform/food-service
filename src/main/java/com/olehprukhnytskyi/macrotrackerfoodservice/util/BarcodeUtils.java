package com.olehprukhnytskyi.macrotrackerfoodservice.util;

import java.util.ArrayList;
import java.util.List;

public final class BarcodeUtils {
    private BarcodeUtils() {
    }

    public static String normalizeForQuota(String rawBarcode) {
        String barcode = requireBarcode(rawBarcode);
        if (isNumeric(barcode) && barcode.length() == 13 && barcode.startsWith("0")) {
            return barcode.substring(1);
        }
        return barcode;
    }

    public static List<String> lookupCandidates(String rawBarcode) {
        List<String> candidates = new ArrayList<>();
        String barcode = requireBarcode(rawBarcode);
        addIfPresent(candidates, barcode);
        String normalized = normalizeForQuota(barcode);
        addIfPresent(candidates, normalized);
        if (isNumeric(normalized) && normalized.length() == 12) {
            addIfPresent(candidates, "0" + normalized);
        }
        return candidates;
    }

    private static String requireBarcode(String rawBarcode) {
        if (rawBarcode == null || rawBarcode.isBlank()) {
            throw new IllegalArgumentException("Barcode must not be blank");
        }
        return rawBarcode.trim();
    }

    private static void addIfPresent(List<String> values, String value) {
        if (value == null || value.isEmpty() || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
