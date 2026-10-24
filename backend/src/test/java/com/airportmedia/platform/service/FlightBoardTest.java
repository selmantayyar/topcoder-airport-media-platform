package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.domain.entity.Flight;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FlightBoardTest {

    private static final Instant NOW = Instant.parse("2026-10-23T14:30:00Z");

    private static Flight departing(int minutesFromNow, FlightStatus status) {
        Flight f = new Flight();
        f.status = status;
        f.scheduledTime = NOW.plusSeconds(minutesFromNow * 60L);
        return f;
    }

    @Test
    void readsLikeAFlightBoard() {
        assertThat(FlightBoard.status(departing(90, FlightStatus.SCHEDULED), NOW)).isEqualTo("ON TIME");
        assertThat(FlightBoard.status(departing(30, FlightStatus.SCHEDULED), NOW)).isEqualTo("BOARDING");
        assertThat(FlightBoard.status(departing(30, FlightStatus.DELAYED), NOW)).isEqualTo("DELAYED");
        assertThat(FlightBoard.status(departing(-5, FlightStatus.SCHEDULED), NOW)).isEqualTo("DEPARTED");
    }

    @Test
    void aDelayedFlightThatHasLeftReadsDeparted() {
        assertThat(FlightBoard.status(departing(-240, FlightStatus.DELAYED), NOW)).isEqualTo("DEPARTED");
    }
}
