package com.airportmedia.platform.service.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class YieldPricingTest {

    private static final BigDecimal BASE = new BigDecimal("120");

    @Test
    void aMedianSlotIsPricedAtBase() {
        assertThat(YieldPricing.suggestedPrice(400, 400, BASE)).isEqualByComparingTo("120.00");
    }

    @Test
    void priceScalesWithFootfallRelativeToTheMedian() {
        assertThat(YieldPricing.suggestedPrice(600, 400, BASE)).isEqualByComparingTo("180.00");
    }

    @Test
    void priceIsFlooredForEmptySlotsAndCappedForTheBusiest() {
        assertThat(YieldPricing.suggestedPrice(0, 400, BASE)).isEqualByComparingTo("60.00");
        assertThat(YieldPricing.suggestedPrice(4000, 400, BASE)).isEqualByComparingTo("300.00");
    }

    @Test
    void withNoFootfallDataEverySlotSitsAtTheFloor() {
        assertThat(YieldPricing.suggestedPrice(0, 0, BASE)).isEqualByComparingTo("60.00");
    }

    @Test
    void premiumStartsAtTheSeventyFifthPercentileOfBusySlots() {
        assertThat(YieldPricing.premiumThreshold(List.of(0, 0, 10, 20, 30, 40))).isEqualTo(30.0);
    }

    @Test
    void withNoBusySlotsNothingIsPremium() {
        assertThat(YieldPricing.premiumThreshold(List.of(0, 0))).isEqualTo(Double.MAX_VALUE);
    }
}
