package dev.foodfleet.ch51.slo;

import java.time.Duration;
import java.util.List;

/**
 * One condition of a multi-window, multi-burn-rate alert (the Google SRE workbook's scheme): fire when the error ratio
 * over both a long and a short window exceeds {@code burnRate} times the error budget. The long window makes the alert
 * significant (a real share of the budget is gone); the short window makes it stop soon after the problem does.
 *
 * @param longWindow  the window the budget spend is measured over
 * @param shortWindow a twelfth of it, by convention: the alert resets within about this long after recovery
 * @param burnRate    the multiple of the budget's sustainable rate
 * @param severity    page (wake someone) or ticket (next working day)
 */
public record BurnRateAlert(Duration longWindow, Duration shortWindow, double burnRate, Severity severity) {

    public enum Severity { PAGE, TICKET }

    /**
     * The burn rate at which {@code budgetFraction} of the budget is spent in {@code alertWindow}: for 2% of a 30-day
     * budget in one hour, 0.02 × 720 h / 1 h = 14.4.
     */
    public static double burnRateFor(double budgetFraction, Duration alertWindow, Duration sloWindow) {
        return budgetFraction * sloWindow.toSeconds() / alertWindow.toSeconds();
    }

    /** The error ratio that triggers this condition for {@code slo}. */
    public double errorRatioThreshold(Slo slo) {
        return burnRate * slo.errorBudget();
    }

    /** The share of the budget spent by the time the long window alone would trigger, at exactly this burn rate. */
    public double budgetSpentAtThreshold(Slo slo) {
        return burnRate * longWindow.toSeconds() / slo.window().toSeconds();
    }

    /**
     * The workbook's recommendation, scaled to the SLO's window: page when 2% of the budget is spent in 1 hour or 5% in
     * 6 hours; open a ticket when 10% is spent in 1 day or in 3 days. For a 30-day window these are burn rates of 14.4,
     * 6, 3 and 1.
     */
    public static List<BurnRateAlert> defaults(Slo slo) {
        Duration w = slo.window();
        return List.of(
                of(0.02, Duration.ofHours(1), w, Severity.PAGE),
                of(0.05, Duration.ofHours(6), w, Severity.PAGE),
                of(0.10, Duration.ofDays(1), w, Severity.TICKET),
                of(0.10, Duration.ofDays(3), w, Severity.TICKET));
    }

    private static BurnRateAlert of(double budgetFraction, Duration longWindow, Duration sloWindow, Severity severity) {
        return new BurnRateAlert(longWindow, longWindow.dividedBy(12), burnRateFor(budgetFraction, longWindow, sloWindow), severity);
    }
}
