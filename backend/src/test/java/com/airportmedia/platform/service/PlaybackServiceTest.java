package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.PlaybackView;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The passenger-screen read model assembles the decision, creative, promo and FIDS for a screen. */
@QuarkusTest
@TestTransaction
class PlaybackServiceTest {

    private static final Instant GATE_42_TIME = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    PlaybackService playback;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    private Screen gate42Screen() {
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        Screen screen = new Screen();
        screen.code = "SCR-PLAY42";
        screen.name = "Gate 42 playback screen";
        screen.zone = pierD;
        screen.nearestGates = "40,41,42,43";
        screen.orientation = ScreenOrientation.LANDSCAPE;
        screen.widthPx = 1920;
        screen.heightPx = 1080;
        screen.status = ScreenStatus.ONLINE;
        screen.latencyMs = 25;
        em.persist(screen);
        em.flush();
        return screen;
    }

    @Test
    void playbackRendersTheWinningAdWithCreativePromoAndFids() {
        clock.jumpTo(GATE_42_TIME);
        Screen screen = gate42Screen();

        PlaybackView view = playback.playback(screen.id);

        assertThat(view.decision().winnerName()).contains("Duty-Free");
        assertThat(view.creative()).isNotNull();
        assertThat(view.creative().offerText()).isNotBlank();
        assertThat(view.promo()).isNotNull();
        assertThat(view.promo().claimPath()).isEqualTo("/claim/AMP-PERFUME15");
        assertThat(view.promo().secondsRemaining()).isPositive();
        assertThat(view.nearbyFlights()).anyMatch(f -> f.flightNumber().equals("XY501"));
        assertThat(view.screen().orientation()).isEqualTo("LANDSCAPE");
    }

    @Test
    void screenChooserListsAndFiltersByGate() {
        clock.jumpTo(GATE_42_TIME);
        Screen screen = gate42Screen();

        assertThat(playback.listScreens(null, null)).isNotEmpty();
        assertThat(playback.listScreens("42", null))
                .anyMatch(s -> s.id() == screen.id);
    }
}
