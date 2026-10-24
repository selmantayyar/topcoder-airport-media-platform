package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Campaign;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.SlotRecommendation;
import com.airportmedia.platform.domain.model.output.SlotRecommendations;
import com.airportmedia.platform.service.SimulationClock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

/** "Best 3 slots tomorrow" over the seeded airport, through the real pipeline. */
@QuarkusTest
@TestTransaction
class SlotRecommendationServiceTest {

    private static final Instant GATE_42_TIME = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    SlotRecommendationService recommendations;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    @Test
    void recommendsThreeRankedSlotsTomorrowWithPredictedImpressions() {
        clock.jumpTo(GATE_42_TIME);
        gate42Screen();
        Campaign dutyFree = em.createQuery("from Campaign where name like 'Azure Duty-Free%'", Campaign.class)
                .setMaxResults(1).getSingleResult();

        SlotRecommendations result = recommendations.recommend(dutyFree.id);

        assertThat(result.day()).isEqualTo(LocalDate.parse("2026-10-24"));
        assertThat(result.slots()).hasSize(3);
        assertThat(result.slots()).extracting(SlotRecommendation::rank).containsExactly(1, 2, 3);
        assertThat(result.slots())
                .allMatch(s -> !s.start().isBefore(Instant.parse("2026-10-24T00:00:00Z"))
                        && s.end().isBefore(Instant.parse("2026-10-25T00:00:01Z")))
                .isSortedAccordingTo(Comparator.comparingLong(SlotRecommendation::predictedImpressions).reversed());
        assertThat(result.slots().get(0).predictedImpressions()).isPositive();
        // a one-hour slot can't exceed the per-screen frequency cap over its 60-minute window
        assertThat(dutyFree.frequencyCapPerScreen).isPositive();
        assertThat(result.slots()).allMatch(
                s -> s.predictedImpressions() <= (long) s.winningScreens() * dutyFree.frequencyCapPerScreen);
        assertThat(result.method()).contains("projected");
    }

    @Test
    void theSameInputsGiveTheSameRecommendation() {
        clock.jumpTo(GATE_42_TIME);
        Campaign any = em.createQuery("from Campaign order by id", Campaign.class).setMaxResults(1).getSingleResult();

        assertThat(recommendations.recommend(any.id)).isEqualTo(recommendations.recommend(any.id));
    }

    private void gate42Screen() {
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        Screen screen = new Screen();
        screen.code = "SCR-GATE42-SLOTS";
        screen.name = "Gate 42 slot test screen";
        screen.zone = pierD;
        screen.nearestGates = "40,41,42,43";
        screen.orientation = ScreenOrientation.LANDSCAPE;
        screen.widthPx = 1920;
        screen.heightPx = 1080;
        screen.status = ScreenStatus.ONLINE;
        screen.latencyMs = 30;
        em.persist(screen);
        em.flush();
    }
}
