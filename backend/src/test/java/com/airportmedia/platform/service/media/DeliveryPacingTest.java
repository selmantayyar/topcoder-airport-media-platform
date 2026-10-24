package com.airportmedia.platform.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryPacingTest {

    private static final Instant START = Instant.parse("2026-10-23T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-25T00:00:00Z");   // 48h flight
    private static final Instant HALFWAY = Instant.parse("2026-10-24T00:00:00Z");
    private static final BigDecimal BUDGET = new BigDecimal("1000");

    @Test
    void onPaceDeliveryIsNotFlagged() {
        var pace = DeliveryPacing.evaluate(new BigDecimal("400"), BUDGET, START, END, HALFWAY);

        assertThat(pace.expectedFraction()).isEqualTo(0.5, within(1e-9));
        assertThat(pace.deliveredFraction()).isEqualTo(0.4, within(1e-9));
        assertThat(pace.pace()).isEqualTo(0.8, within(1e-9));
        assertThat(pace.underDelivering()).isFalse();
    }

    @Test
    void deliveringUnderHalfOfExpectedIsFlagged() {
        assertThat(DeliveryPacing.evaluate(new BigDecimal("100"), BUDGET, START, END, HALFWAY).underDelivering())
                .isTrue();
    }

    @Test
    void tooEarlyToJudgeInTheFirstTwoHours() {
        assertThat(DeliveryPacing.evaluate(BigDecimal.ZERO, BUDGET, START, END, START.plusSeconds(3600))
                .underDelivering()).isFalse();
    }

    @Test
    void aFinishedCampaignIsNotFlagged() {
        assertThat(DeliveryPacing.evaluate(BigDecimal.ZERO, BUDGET, START, END, END.plusSeconds(60))
                .underDelivering()).isFalse();
    }
}
