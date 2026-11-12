package com.mudassir.framework.utils;

public final class PriceUtils {

    private PriceUtils() {
    }

    /** "Rs. 1,500" -> 1500 */
    public static int parseRupees(String text) {
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("No price found in: '" + text + "'");
        }
        return Integer.parseInt(digits);
    }
}
