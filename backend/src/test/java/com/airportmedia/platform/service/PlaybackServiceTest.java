package com.airportmedia.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.FlightDirection;
import com.airportmedia.platform.common.enums.FlightStatus;
import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Flight;
import com.airportmedia.platform.domain.entity.GateAssignment;
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

    @Test
    void aScreenAlwaysListsItsOwnGatesDeparturesEvenInABusyZone() {
        clock.jumpTo(GATE_42_TIME);
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        for (int i = 0; i < 9; i++) {
            departure("TS" + (100 + i), "D90", pierD, GATE_42_TIME.plusSeconds((10 + i) * 60L));
        }
        departure("TS999", "D99", pierD, GATE_42_TIME.plusSeconds(200 * 60L));
        Screen screen = gate42Screen();
        screen.nearestGates = "D99";
        em.flush();

        PlaybackView view = playback.playback(screen.id);

        assertThat(view.nearbyFlights()).hasSizeLessThanOrEqualTo(8)
                .anyMatch(f -> f.flightNumber().equals("TS999"))
                .anyMatch(f -> f.flightNumber().equals("TS100"));
        assertThat(view.nearbyFlights()).extracting(f -> f.minutesToDeparture()).isSorted();
    }

    @Test
    void aDelayedDepartureAtTheScreensGatesStaysOnItsBoard() {
        clock.jumpTo(GATE_42_TIME);
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        for (int i = 0; i < 9; i++) {
            departure("TS" + (100 + i), "D90", pierD, GATE_42_TIME.plusSeconds((10 + i) * 60L));
        }
        Flight delayed = departure("TS999", "D99", pierD, GATE_42_TIME.plusSeconds(20 * 60L));
        delayed.status = FlightStatus.DELAYED;
        delayed.delayMinutes = 150;
        delayed.estimatedTime = delayed.scheduledTime.plusSeconds(150 * 60L);
        departure("TS998", "D99", pierD, GATE_42_TIME.plusSeconds(170 * 60L));
        Screen screen = gate42Screen();
        screen.nearestGates = "D90,D99";
        em.flush();

        PlaybackView view = playback.playback(screen.id);

        assertThat(view.nearbyFlights()).hasSize(8)
                .anyMatch(f -> f.flightNumber().equals("TS999") && f.status().equals("DELAYED"))
                .noneMatch(f -> f.flightNumber().equals("TS998"));
    }

    private Flight departure(String number, String gate, Zone zone, Instant scheduled) {
        Flight template = em.createQuery("from Flight where flightNumber = 'XY501'", Flight.class).getSingleResult();
        Flight f = new Flight();
        f.flightNumber = number;
        f.airline = template.airline;
        f.direction = FlightDirection.DEPARTURE;
        f.origin = template.origin;
        f.destination = template.destination;
        f.haul = template.haul;
        f.aircraftType = template.aircraftType;
        f.paxCount = 100;
        f.status = FlightStatus.SCHEDULED;
        f.scheduledTime = scheduled;
        em.persist(f);
        GateAssignment ga = new GateAssignment();
        ga.flight = f;
        ga.gate = gate;
        ga.zone = zone;
        em.persist(ga);
        return f;
    }

    @Test
    void playbackRendersTheAdInTheRequestedLanguage() {
        clock.jumpTo(GATE_42_TIME);
        Screen screen = gate42Screen();

        PlaybackView english = playback.playback(screen.id, "en");
        PlaybackView spanish = playback.playback(screen.id, "es");

        assertThat(english.creative().language()).isEqualTo("en");
        assertThat(english.creative().offerText()).isEqualTo("15% off perfume");
        assertThat(spanish.creative().language()).isEqualTo("es");
        assertThat(spanish.creative().offerText()).isEqualTo("15% de descuento en perfumes");
        assertThat(spanish.creative().body()).doesNotContain("Limited-time");
        assertThat(spanish.decision().winnerCampaignId()).isEqualTo(english.decision().winnerCampaignId());
    }
}
