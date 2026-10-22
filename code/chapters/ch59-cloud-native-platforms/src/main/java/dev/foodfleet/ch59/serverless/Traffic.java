package dev.foodfleet.ch59.serverless;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A synthetic day of FoodFleet traffic: a quiet night, a lunch peak and a dinner peak, as Poisson arrivals whose rate
 * changes every minute. {@code peakPerSecond} sets the dinner peak; the shape is the same for every service.
 */
public final class Traffic {

    private Traffic() {
    }

    /** Requests per second at minute {@code m} of the day (0-1439), relative to the dinner peak (1.0). */
    public static double shape(int m) {
        double h = m / 60.0;
        double night = 0.02;
        double lunch = 0.75 * Math.exp(-Math.pow((h - 13.0) / 1.0, 2));
        double dinner = 1.0 * Math.exp(-Math.pow((h - 20.5) / 1.3, 2));
        double day = 0.15 * Math.exp(-Math.pow((h - 16.0) / 4.0, 2));
        return night + lunch + dinner + day;
    }

    public static double[] day(double peakPerSecond, long seed) {
        Random r = new Random(seed);
        List<Double> times = new ArrayList<>();
        for (int m = 0; m < 1440; m++) {
            double rate = peakPerSecond * shape(m) / 1.02;
            double t = m * 60.0;
            while (true) {
                t += -Math.log(1 - r.nextDouble()) / rate;
                if (t >= (m + 1) * 60.0) {
                    break;
                }
                times.add(t);
            }
        }
        return times.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
