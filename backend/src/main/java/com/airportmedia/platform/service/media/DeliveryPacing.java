package com.airportmedia.platform.service.media;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Whether a campaign is delivering its budget on schedule:
 *
 * <pre>
 *   delivered = spent ÷ budget        expected = elapsed ÷ scheduled duration        pace = delivered ÷ expected
 * </pre>
 *
 * A live campaign is <em>under-delivering</em> when its pace drops below {@value #UNDER_DELIVERY_PACE}, judged
 * only once it has run at least {@value #MIN_HOURS_LIVE} hours (earlier is too noisy) and before it ends.
 */
public final class DeliveryPacing {

    static final double UNDER_DELIVERY_PACE = 0.5;
    static final long MIN_HOURS_LIVE = 2;

    private DeliveryPacing() {}

    public record Pace(double deliveredFraction, double expectedFraction, double pace, boolean underDelivering) {}

    public static Pace evaluate(BigDecimal spent, BigDecimal budget, Instant start, Instant end, Instant now) {
        double scheduled = Math.max(1, Duration.between(start, end).toSeconds());
        double elapsed = Duration.between(start, now).toSeconds();
        double expected = Math.max(0, Math.min(1, elapsed / scheduled));
        double delivered = budget.signum() > 0 ? spent.doubleValue() / budget.doubleValue() : 0.0;
        double pace = expected > 0 ? delivered / expected : 1.0;

        boolean live = now.isBefore(end) && Duration.between(start, now).toHours() >= MIN_HOURS_LIVE;
        return new Pace(delivered, expected, pace, live && pace < UNDER_DELIVERY_PACE);
    }
}
