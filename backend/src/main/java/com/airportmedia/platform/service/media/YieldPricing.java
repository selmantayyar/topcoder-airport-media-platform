package com.airportmedia.platform.service.media;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Suggested price for one screen-hour, driven by predicted footfall:
 *
 * <pre>
 *   price = basePricePerHour × clamp(footfall / medianFootfall, {@value #FLOOR}, {@value #CAP})
 * </pre>
 *
 * A typical (median) slot sells at the base price; an empty slot at the floor; the busiest at the cap.
 * A slot is <em>premium</em> when its footfall reaches the 75th percentile of the busy (non-empty) slots.
 */
public final class YieldPricing {

    static final double FLOOR = 0.5;
    static final double CAP = 2.5;
    private static final double PREMIUM_PERCENTILE = 0.75;

    private YieldPricing() {}

    public static BigDecimal suggestedPrice(int footfall, double medianFootfall, BigDecimal basePricePerHour) {
        double ratio = medianFootfall > 0 ? footfall / medianFootfall : 0.0;
        double factor = Math.max(FLOOR, Math.min(CAP, ratio));
        return basePricePerHour.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Nearest-rank 75th percentile of the non-zero footfalls; {@code Double.MAX_VALUE} when there are none. */
    public static double premiumThreshold(List<Integer> footfalls) {
        List<Integer> busy = footfalls.stream().filter(f -> f > 0).sorted().toList();
        if (busy.isEmpty()) {
            return Double.MAX_VALUE;
        }
        int rank = (int) Math.ceil(PREMIUM_PERCENTILE * busy.size());
        return busy.get(rank - 1);
    }

    /** The median of the non-zero footfalls (0 when none), the reference point for pricing. */
    public static double medianBusyFootfall(List<Integer> footfalls) {
        List<Integer> busy = footfalls.stream().filter(f -> f > 0).sorted().toList();
        if (busy.isEmpty()) {
            return 0;
        }
        int mid = busy.size() / 2;
        return busy.size() % 2 == 1 ? busy.get(mid) : (busy.get(mid - 1) + busy.get(mid)) / 2.0;
    }
}
