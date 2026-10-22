package dev.foodfleet.ch76.functions;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A light function: a delivery fee from distance, basket value and surge, as JSON in and out. */
public final class FeeQuote implements Function<String, String> {

    private static final Pattern NUMBER = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?[0-9.]+)");

    @Override
    public String apply(String json) {
        double km = 0;
        double basket = 0;
        double surge = 1;
        Matcher m = NUMBER.matcher(json);
        while (m.find()) {
            switch (m.group(1)) {
                case "km" -> km = Double.parseDouble(m.group(2));
                case "basket" -> basket = Double.parseDouble(m.group(2));
                case "surge" -> surge = Double.parseDouble(m.group(2));
                default -> { }
            }
        }
        long fee = Math.round((2_500 + 900 * Math.max(0, km - 2)) * surge);
        if (basket >= 49_900) {
            fee = Math.max(0, fee - 2_500);
        }
        return "{\"feeInPaise\":" + fee + "}";
    }
}
