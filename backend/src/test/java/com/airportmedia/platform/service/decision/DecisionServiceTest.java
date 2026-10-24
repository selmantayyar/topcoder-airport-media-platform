package com.airportmedia.platform.service.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.airportmedia.platform.common.enums.ScreenOrientation;
import com.airportmedia.platform.common.enums.ScreenStatus;
import com.airportmedia.platform.domain.entity.Screen;
import com.airportmedia.platform.domain.entity.Zone;
import com.airportmedia.platform.domain.model.output.DecisionResult;
import com.airportmedia.platform.repository.DecisionLogRepository;
import com.airportmedia.platform.repository.ImpressionRepository;
import com.airportmedia.platform.service.SimulationClock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Drives the full pipeline over the seeded airport: the Gate 42 worked example reproduces with the
 * duty-free campaign winning, and executing a decision persists a queryable evidence trail.
 */
@QuarkusTest
@TestTransaction
class DecisionServiceTest {

    private static final Instant GATE_42_TIME = Instant.parse("2026-10-23T14:30:00Z");

    @Inject
    DecisionService decisions;

    @Inject
    SimulationClock clock;

    @Inject
    EntityManager em;

    @Inject
    DecisionLogRepository decisionLogs;

    @Inject
    ImpressionRepository impressions;

    private Screen gate42Screen() {
        Zone pierD = em.createQuery("from Zone where name = 'Pier D'", Zone.class).getSingleResult();
        Screen screen = new Screen();
        screen.code = "SCR-GATE42-TEST";
        screen.name = "Gate 42 test screen";
        screen.zone = pierD;
        screen.nearestGates = "40,41,42,43";
        screen.orientation = ScreenOrientation.LANDSCAPE;
        screen.widthPx = 1920;
        screen.heightPx = 1080;
        screen.status = ScreenStatus.ONLINE;
        screen.latencyMs = 30;
        em.persist(screen);
        em.flush();
        return screen;
    }

    @Test
    void gate42ScreenPlaysTheDutyFreeCampaign() {
        clock.jumpTo(GATE_42_TIME);
        Screen screen = gate42Screen();

        DecisionResult result = decisions.preview(screen.id);

        assertThat(result.fallback()).isFalse();
        assertThat(result.hasWinner()).isTrue();
        assertThat(result.winnerName()).contains("Duty-Free");
        assertThat(result.headcount()).isPositive();
        assertThat(result.candidates()).anyMatch(c -> c.campaignName().contains("Luxe Watches"));
    }

    @Test
    void executingADecisionPersistsTheEvidenceTrailAndAnImpression() {
        clock.jumpTo(GATE_42_TIME);
        Screen screen = gate42Screen();
        long impressionsBefore = impressions.count("screen.id", screen.id);

        DecisionResult result = decisions.decideAndExecute(screen.id);
        em.flush();

        assertThat(decisionLogs.recentForScreen(screen.id, 10)).isNotEmpty();
        var log = decisionLogs.recentForScreen(screen.id, 1).get(0);
        assertThat(log.candidatesJson).isNotBlank();
        assertThat(log.profileJson).isNotBlank();
        assertThat(log.winnerName).isEqualTo(result.winnerName());
        assertThat(log.reason).isNotBlank();

        assertThat(impressions.count("screen.id", screen.id)).isEqualTo(impressionsBefore + 1);
    }

    @Test
    void runningDueScreensDecidesOnlineScreens() {
        clock.jumpTo(GATE_42_TIME);
        gate42Screen();

        int decided = decisions.runDueScreens();

        assertThat(decided).isPositive();
        assertThat(decisionLogs.recent(5)).isNotEmpty();
    }
}
