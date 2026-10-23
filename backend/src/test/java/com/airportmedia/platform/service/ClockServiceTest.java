package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.domain.model.output.ClockTick;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClockServiceTest {

    private final ClockService service = new ClockService();

    @Test
    void emitsMonotonicTicksWithAnInstant() {
        AssertSubscriber<ClockTick> subscriber = service
                .ticks(Duration.ofMillis(20))
                .subscribe()
                .withSubscriber(AssertSubscriber.create(3));

        List<ClockTick> items = subscriber.awaitItems(3).getItems();

        assertThat(items).extracting(ClockTick::tickNumber).containsExactly(0L, 1L, 2L);
        assertThat(items).allSatisfy(tick -> assertThat(tick.serverTime()).isNotNull());
    }

    @Test
    void defaultStreamTicksAtLeastOncePerSecond() {
        AssertSubscriber<ClockTick> subscriber = service
                .stream()
                .subscribe()
                .withSubscriber(AssertSubscriber.create(2));

        List<ClockTick> items = subscriber.awaitItems(2, Duration.ofSeconds(3)).getItems();

        assertThat(items).hasSize(2);
        assertThat(items.get(1).tickNumber()).isEqualTo(1L);
    }
}
